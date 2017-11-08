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
package org.structr.web.entity.dom;

import org.structr.common.SecurityContext;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.property.PropertyMap;
import org.structr.web.common.RenderContext;
import org.structr.schema.NonIndexed;
import org.structr.schema.SchemaService;

/**
 */
public interface Comment extends Content, org.w3c.dom.Comment, NonIndexed {

	static class Impl { static { SchemaService.registerMixinType(Comment.class); }}

	@Override
	default boolean onCreation(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		if (Content.super.isValid(errorBuffer)) {

			setProperties(securityContext, new PropertyMap(Comment.contentType, "text/html"));
			return true;
		}

		return false;
	}

	@Override
	default void render(RenderContext renderContext, int depth) throws FrameworkException {

		final String _content = getProperty(content);

		// Avoid rendering existing @structr comments since those comments are
		// created depending on the visiblity settings of individual nodes. If
		// those comments are rendered, there will be duplicates in a round-
		// trip export/import test.
		if (!_content.contains("@structr:")) {

			renderContext.getBuffer().append("<!--").append(_content).append("-->");
		}

	}
}
