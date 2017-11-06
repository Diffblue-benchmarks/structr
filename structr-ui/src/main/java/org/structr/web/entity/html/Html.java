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
package org.structr.web.entity.html;

import org.apache.commons.lang3.ArrayUtils;
import org.structr.common.PropertyView;
import org.structr.common.View;
import org.structr.common.error.FrameworkException;
import org.structr.core.property.Property;
import org.structr.core.property.StringProperty;
import org.structr.schema.SchemaService;
import org.structr.web.common.AsyncBuffer;
import org.structr.web.common.HtmlProperty;
import org.structr.web.common.RenderContext;
import org.structr.web.entity.dom.DOMElement;

/**
 *
 */
public interface Html extends DOMElement {

	static class Impl { static { SchemaService.registerMixinType(Html.class); }}

	public static final Property<String> _manifest         = new HtmlProperty("manifest");
	public static final Property<String> _customOpeningTag = new StringProperty("customOpeningTag");

	public static final View htmlView = new View(Html.class, PropertyView.Html,
		_manifest
	);

	public static final View uiView = new View(Html.class, PropertyView.Ui,
		_manifest, _customOpeningTag
	);

	@Override
	default void openingTag(final AsyncBuffer out, final String tag, final RenderContext.EditMode editMode, final RenderContext renderContext, final int depth) throws FrameworkException {

		String custTag = getProperty(_customOpeningTag);
		if (custTag != null) {

			out.append(custTag);

		} else {

			DOMElement.super.openingTag(out, tag, editMode, renderContext, depth);
		}
	}

	@Override
	default Property[] getHtmlAttributes() {
		return (Property[]) ArrayUtils.addAll(DOMElement.super.getHtmlAttributes(), htmlView.properties());
	}
}
