/**
 * Copyright (C) 2010-2017 Structr GmbH
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
package org.structr.bolt.index;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.neo4j.driver.v1.types.Relationship;
import org.structr.api.QueryResult;
import org.structr.bolt.SessionTransaction;

/**
 */
public class RelationshipResultStream implements QueryResult<Relationship> {

	private final Map<String, Object> metadata = new LinkedHashMap<>();
	private Iterator<Relationship> current     = null;
	private SessionTransaction tx              = null;
	private PageableQuery query                = null;

	public RelationshipResultStream(final SessionTransaction tx, final PageableQuery query) {
		this.query = query;
		this.tx    = tx;
	}

	@Override
	public void close() {
	}

	@Override
	public void setMetaData(String key, Object value) {
		this.metadata.put(key, value);
	}

	@Override
	public Object getMetaData(String key) {
		return this.metadata.get(key);
	}

	@Override
	public Iterator<Relationship> iterator() {

		return new Iterator<Relationship>() {

			@Override
			public boolean hasNext() {

				if (current == null || !current.hasNext()) {

					final String statement                 = query.getStatement(false);
					final Map<String, Object> params       = query.getParameters();
					final QueryResult<Relationship> result = tx.getRelationships(statement, params);

					if (result != null) {

						current = result.iterator();

						// advance page
						query.nextPage();

						// does the next result have elements?
						if (!current.hasNext()) {

							// no more elements
							return false;
						}
					}
				}

				return current != null && current.hasNext();
			}

			@Override
			public Relationship next() {
				return current.next();
			}
		};
	}
}
