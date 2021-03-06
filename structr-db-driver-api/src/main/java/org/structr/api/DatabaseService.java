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
package org.structr.api;

import java.util.Map;
import java.util.Set;
import org.structr.api.graph.GraphProperties;
import org.structr.api.graph.Node;
import org.structr.api.graph.Relationship;
import org.structr.api.index.Index;

/**
 *
 */
public interface DatabaseService {

	// ----- lifecycle -----
	/**
	 * Initializes the service, returns true if the
	 * service was initialized successfully.
	 *
	 * @return whether the service was initialized successfully
	 */
	boolean initialize();
	void shutdown();

	<T> T forName(final Class<T> type, final String name);

	Transaction beginTx();

	Node createNode(final Set<String> labels, final Map<String, Object> properties);

	Node getNodeById(final long id);
	Relationship getRelationshipById(final long id);

	Iterable<Node> getAllNodes();
	Iterable<Node> getNodesByLabel(final String label);
	Iterable<Node> getNodesByTypeProperty(final String type);

	Iterable<Relationship> getAllRelationships();
	Iterable<Relationship> getRelationshipsByType(final String type);

	GraphProperties getGlobalProperties();

	String getTenantIdentifier();
	String getInternalTimestamp();

	// ----- index -----
	Index<Node> nodeIndex();
	Index<Relationship> relationshipIndex();
	void updateIndexConfiguration(final Map<String, Map<String, Boolean>> schemaIndexConfig, final Map<String, Map<String, Boolean>> removedClasses);

	Iterable<Map<String, Object>> execute(final String nativeQuery, final Map<String, Object> parameters);
	Iterable<Map<String, Object>> execute(final String nativeQuery);
}
