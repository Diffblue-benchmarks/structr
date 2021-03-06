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
package org.structr.core.graph;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.DatabaseService;
import org.structr.api.graph.Node;
import org.structr.api.graph.Relationship;
import org.structr.api.util.Iterables;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.GraphObjectMap;
import org.structr.core.property.GenericProperty;

/**
 * Executes the given Cypher query and tries to convert the result in a List
 * of {@link GraphObject}s.
 *
 *
 */
public class CypherQueryCommand extends NodeServiceCommand {

	private static final Logger logger = LoggerFactory.getLogger(CypherQueryCommand.class.getName());

	public Iterable<GraphObject> execute(String query) throws FrameworkException {
		return execute(query, null);
	}

	public Iterable<GraphObject> execute(String query, Map<String, Object> parameters) throws FrameworkException {
		return execute(query, parameters, true);
	}

	public Iterable<GraphObject> execute(String query, Map<String, Object> parameters, boolean includeHiddenAndDeleted) throws FrameworkException {
		return execute(query, parameters, includeHiddenAndDeleted, false);
	}

	public Iterable<GraphObject> execute(String query, Map<String, Object> parameters, boolean includeHiddenAndDeleted, boolean publicOnly) throws FrameworkException {

		DatabaseService graphDb = (DatabaseService) arguments.get("graphDb");
		if (graphDb != null) {

			final Iterable<Map<String, Object>> result = graphDb.execute(query, parameters != null ? parameters : Collections.emptyMap());
			final Iterable<Iterable<GraphObject>> test = extractRows(result, includeHiddenAndDeleted, publicOnly);

			if (query.matches("(?i)(?s)(?m).*\\s+(delete|set|remove)\\s+.*")) {
				logger.info("Clearing all caches due to DELETE, SET or REMOVE found in Cypher query: " + query);
				FlushCachesCommand.flushAll();
			}

			return Iterables.flatten(test);
		}

		return Collections.emptyList();
	}

	private Iterable<Iterable<GraphObject>> extractRows(final Iterable<Map<String, Object>> result, final boolean includeHiddenAndDeleted, final boolean publicOnly) {
		return Iterables.map(map -> { return extractColumns(map, includeHiddenAndDeleted, publicOnly); }, result);
	}

	private Iterable<GraphObject> extractColumns(final Map<String, Object> map, final boolean includeHiddenAndDeleted, final boolean publicOnly) {

		final RelationshipFactory relFactory  = new RelationshipFactory(securityContext);
		final NodeFactory nodeFactory         = new NodeFactory(securityContext);

		return Iterables.map(entry -> {

			final String key = entry.getKey();
			final Object val = entry.getValue();

			try {

				final Object obj = handleObject(nodeFactory, relFactory, key, val, includeHiddenAndDeleted, publicOnly, 0);
				if (obj != null) {

					if (obj instanceof GraphObject) {

						return (GraphObject)obj;

					} else if (obj instanceof Collection) {

						return (GraphObject)handleObject(nodeFactory, relFactory, key, obj, includeHiddenAndDeleted, publicOnly, 0);

					} else {

						logger.warn("Unable to handle Cypher query result object of type {}, ignoring.", obj.getClass().getName());
					}
				}

			} catch (FrameworkException fex) {

				fex.printStackTrace();
			}

			return null;

		}, map.entrySet());
	}

	final Object handleObject(final NodeFactory nodeFactory, final RelationshipFactory relFactory, final String key, final Object value, boolean includeHiddenAndDeleted, boolean publicOnly, int level) throws FrameworkException {

		GraphObject graphObject = null;

		if (value instanceof Node) {

			graphObject = nodeFactory.instantiate((Node) value, includeHiddenAndDeleted, publicOnly);

		} else if (value instanceof Relationship) {

			graphObject = relFactory.instantiate((Relationship) value);

		} else if (value instanceof Map) {

			final Map<String, Object> valueMap = (Map<String, Object>)value;
			graphObject = new GraphObjectMap();

			for (final Entry<String, Object> valueEntry : valueMap.entrySet()) {

				final String valueKey   = valueEntry.getKey();
				final Object valueValue = valueEntry.getValue();

				graphObject.setProperty(new GenericProperty(valueKey), handleObject(nodeFactory, relFactory, valueKey, valueValue, includeHiddenAndDeleted, publicOnly, level + 1));
			}

		} else if (value instanceof Collection) {

			final Collection<Object> valueCollection = (Collection<Object>)value;
			final List<Object> collection            = new LinkedList<>();

			for (final Object valueEntry : valueCollection) {

				collection.add(handleObject(nodeFactory, relFactory, null, valueEntry, includeHiddenAndDeleted, publicOnly, level + 1));
			}

			return collection;

		} else if (level == 0) {

			graphObject = new GraphObjectMap();
			graphObject.setProperty(new GenericProperty(key), value);

		} else {

			return value;
		}

		return graphObject;
	}

}
