/**
 * Copyright (C) 2010-2018 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.rest.service;

import org.eclipse.jetty.server.session.AbstractSessionDataStore;
import org.eclipse.jetty.server.session.SessionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.config.Settings;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Services;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.SessionDataNode;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.Tx;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.PropertyMap;

import java.util.*;
import org.structr.api.util.Iterables;

/**
 */
public class StructrSessionDataStore extends AbstractSessionDataStore {

	private static final Logger logger = LoggerFactory.getLogger(StructrSessionDataStore.class.getName());
	private static final SecurityContext ctx = SecurityContext.getSuperUserInstance();
	private static final App app = StructrApp.getInstance(ctx);
	private static final Services services = Services.getInstance();

	private static final Map<String, SessionData> anonymousSessionCache = new HashMap<>();

	@Override
	public void doStore(final String id, final SessionData data, final long lastSaveTime) throws Exception {

		assertInitialized();

		try (final Tx tx = app.tx(true, false, false)) {

			Map<String, Object> params = new HashMap<>();
			params.put("id", id);
			List<GraphObject> results = Iterables.toList(app.cypher("MATCH (n:User) WHERE {id} IN n.sessionIds RETURN count(n) > 0 as result", params));
			boolean isAuthenticated = results.get(0).getProperty(new BooleanProperty("result"));

			if (isAuthenticated) {

				final SessionDataNode node = getOrCreateSessionDataNode(app, id);
				if (node != null) {

					final PropertyMap properties = new PropertyMap();

					properties.put(SessionDataNode.lastAccessed, new Date(data.getLastAccessed()));
					properties.put(SessionDataNode.contextPath, data.getContextPath());
					properties.put(SessionDataNode.vhost, data.getVhost());

					node.setProperties(ctx, properties);
				}

				tx.success();

			} else {
				anonymousSessionCache.put(id, data);
			}

		} catch (FrameworkException ex) {

			logger.info("Unable to store session data for session id " + id + ".", ex);
		}
	}

	@Override
	public Set<String> doGetExpired(final Set<String> candidates) {
		final long sessionTimeout = Settings.SessionTimeout.getValue(1800) * 1000;
		final Date timeoutDate    = new Date(System.currentTimeMillis() - sessionTimeout);

		assertInitialized();

		for (Map.Entry<String,SessionData> entry : anonymousSessionCache.entrySet()) {
			SessionData data = entry.getValue();
			if ( (new Date().getTime() - data.getLastAccessed()) > sessionTimeout) {
				candidates.add(entry.getKey());
			}
		}

		try (final Tx tx = app.tx(true, false, false)) {

			for (final SessionDataNode node : app.nodeQuery(SessionDataNode.class).andRange(SessionDataNode.lastAccessed, new Date(0), timeoutDate).getAsList()) {

				candidates.add(node.getProperty(SessionDataNode.sessionId));
			}

			tx.success();

		} catch (FrameworkException ex) {

			logger.info("Unable to determine list of expired session candidates.");
		}

		return candidates;
	}

	@Override
	public boolean isPassivating() {
		return true;
	}

	@Override
	public boolean exists(final String id) throws Exception {

		if (anonymousSessionCache.containsKey(id)) {
			return true;
		}

		assertInitialized();

		try (final Tx tx = app.tx(true, false, false)) {

			final SessionDataNode node = app.nodeQuery(SessionDataNode.class).and(SessionDataNode.sessionId, id).getFirst();

			tx.success();

			return node != null;

		} catch (FrameworkException ex) {

			logger.info("Unable to determine if session data for " + id + " exists.", ex);
		}

		return false;
	}

	@Override
	public SessionData load(final String id) throws Exception {

		if (anonymousSessionCache.containsKey(id)) {
			return anonymousSessionCache.get(id);
		}

		assertInitialized();

		SessionData result        = null;

		try (final Tx tx = app.tx(true, false, false)) {

			final SessionDataNode node = app.nodeQuery(SessionDataNode.class).and(SessionDataNode.sessionId, id).getFirst();
			if (node != null) {

				result = new SessionData(
					id,
					node.getProperty(SessionDataNode.contextPath),
					node.getProperty(SessionDataNode.vhost),
					node.getCreatedDate().getTime(),
					node.getLastModifiedDate().getTime(),
					node.getLastModifiedDate().getTime(),
					-1
				);
			}

			tx.success();

		} catch (FrameworkException ex) {

			logger.info("Unable to load session data for session id " + id + ".", ex);
		}

		return result;
	}

	@Override
	public boolean delete(final String id) throws Exception {

		if (anonymousSessionCache.containsKey(id)) {
			anonymousSessionCache.remove(id);
			return true;
		}

		assertInitialized();

		try (final Tx tx = app.tx(true, false, false)) {

			// delete nodes
			for (final SessionDataNode node : app.nodeQuery(SessionDataNode.class).and(SessionDataNode.sessionId, id).getAsList()) {

				app.delete(node);
			}

			tx.success();

			return true;

		} catch (FrameworkException ex) {

			logger.info("Unable to load session data for session id " + id + ".", ex);
		}

		return false;
	}


	// ----- private methods -----
	private void assertInitialized() {

		if (!services.isShuttingDown() && !services.isShutdownDone()) {

			// wait for service layer to be initialized
			while (!services.isInitialized()) {

				try { Thread.sleep(1000); } catch (Throwable t) {}
			}
		}
	}

	private SessionDataNode getOrCreateSessionDataNode(final App app, final String id) throws FrameworkException {

		SessionDataNode node = app.nodeQuery(SessionDataNode.class).and(SessionDataNode.sessionId, id).getFirst();
		if (node == null) {

			node= app.create(SessionDataNode.class, new NodeAttribute<>(SessionDataNode.sessionId, id));
		}

		return node;
	}

	@Override
	public SessionData doLoad(String id) throws Exception {
		return load(id);
	}
}
