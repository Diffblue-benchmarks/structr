/**
 * Copyright (C) 2010-2016 Structr GmbH
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
package org.structr.common.fulltext;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObjectMap;

/**
 *
 */
public class DummyFulltextIndexer implements FulltextIndexer {

	private static final Logger logger = Logger.getLogger(DummyFulltextIndexer.class.getName());

	@Override
	public void addToFulltextIndex(final Indexable indexable) throws FrameworkException {
		logger.log(Level.WARNING, "No fulltext indexer installed, this is a dummy implementation that does nothing.");
	}

	@Override
	public GraphObjectMap getContextObject(String searchTerm, String text, int contextLength) {

		logger.log(Level.WARNING, "No fulltext indexer installed, this is a dummy implementation that does nothing.");

		return new GraphObjectMap();
	}
}
