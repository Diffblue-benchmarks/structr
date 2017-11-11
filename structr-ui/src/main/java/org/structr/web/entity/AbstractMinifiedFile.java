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
package org.structr.web.entity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.structr.common.SecurityContext;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.Export;
import org.structr.core.graph.ModificationEvent;
import org.structr.core.graph.ModificationQueue;
import org.structr.core.property.EndNodes;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyMap;
import org.structr.web.entity.relation.MinificationSource;

/**
 * Base class for minifiable files in structr
 *
 */
public interface AbstractMinifiedFile extends File {

	public static final Property<List<File>> minificationSources = new EndNodes<>("minificationSources", MinificationSource.class);

	@Override
	default boolean onModification(final SecurityContext securityContext, final ErrorBuffer errorBuffer, final ModificationQueue modificationQueue) throws FrameworkException {

		boolean shouldMinify = false;
		final String myUUID = getUuid();

		for (ModificationEvent modState : modificationQueue.getModificationEvents()) {

			// only take changes on this exact file into account
			if (myUUID.equals(modState.getUuid())) {

				shouldMinify = shouldMinify || shouldModificationTriggerMinifcation(modState);

			}

		}

		if (shouldMinify) {

			try {
				this.minify();
			} catch (IOException ex) {
				logger.warn("Could not automatically minify file", ex);
			}

		}

		return File.super.onModification(securityContext, errorBuffer, modificationQueue);
	}

	@Export
	public void minify() throws FrameworkException, IOException;
	public boolean shouldModificationTriggerMinifcation(ModificationEvent modState);

	default public int getMaxPosition () {
		int max = -1;
		for (final MinificationSource neighbor : getOutgoingRelationships(MinificationSource.class)) {
			max = Math.max(max, neighbor.getProperty(MinificationSource.position));
		}
		return max;
	}

	default public String getConcatenatedSource () throws FrameworkException, IOException {

		final StringBuilder concatenatedSource = new StringBuilder();
		int cnt = 0;

		for (MinificationSource rel : getSortedRelationships()) {

			final File src = rel.getTargetNode();

			concatenatedSource.append(FileUtils.readFileToString(src.getFileOnDisk()));

			// compact the relationships (if necessary)
			if (rel.getProperty(MinificationSource.position) != cnt) {
				rel.setProperties(getSecurityContext(), new PropertyMap(MinificationSource.position, cnt));
			}

			cnt++;
		}

		return concatenatedSource.toString();
	}

	default public List<MinificationSource> getSortedRelationships() {
		final List<MinificationSource> rels = new ArrayList();
		getOutgoingRelationships(MinificationSource.class).forEach(rels::add);

		Collections.sort(rels, (MinificationSource arg0, MinificationSource arg1) -> (arg0.getProperty(MinificationSource.position).compareTo(arg1.getProperty(MinificationSource.position))));

		return rels;
	}

	/**
	 * Move a minification source to a new position.
	 * All minification sources between those positions have to be adjusted as well.
	 *
	 * @param from The position from where the minification source is moved
	 * @param to The position where to move the minification source
	 * @throws FrameworkException
	 */
	@Export
	default public void moveMinificationSource(final int from, final int to) throws FrameworkException {

		final SecurityContext securityContext = getSecurityContext();

		for (MinificationSource rel : getOutgoingRelationships(MinificationSource.class)) {

			int currentPosition = rel.getProperty(MinificationSource.position);

			int change = 0;
			if (from < to) {
				change = -1;
			} else if (from > to) {
				change = 1;
			}

			if (currentPosition > from && currentPosition <= to) {

				rel.setProperties(securityContext, new PropertyMap(MinificationSource.position, currentPosition + change));

			} else if (currentPosition >= to && currentPosition < from) {

				rel.setProperties(securityContext, new PropertyMap(MinificationSource.position, currentPosition + change));

			} else if (currentPosition == from) {

				rel.setProperties(securityContext, new PropertyMap(MinificationSource.position, to));

			}

		}

	}

}
