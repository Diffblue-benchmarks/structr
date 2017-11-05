/**
 * Copyright (C) 2010-2017 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.datasource;

import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.CypherQueryCommand;
import org.structr.core.graph.NodeInterface;
import org.structr.web.common.GraphDataSource;
import org.structr.web.common.RenderContext;
import org.structr.web.entity.dom.DOMNode;

/**
 *
 *
 */
public class CypherGraphDataSource implements GraphDataSource {

	@Override
	public Iterable<GraphObject> getData(final RenderContext renderContext, final NodeInterface referenceNode) throws FrameworkException {

		final String cypherQuery = ((DOMNode) referenceNode).getPropertyWithVariableReplacement(renderContext, DOMNode.cypherQuery);
		if (cypherQuery == null || cypherQuery.isEmpty()) {

			return null;
		}

		return StructrApp.getInstance(renderContext.getSecurityContext()).command(CypherQueryCommand.class).execute(cypherQuery);
	}
}
