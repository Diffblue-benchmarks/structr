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
package org.structr.core.entity.relationship;

import java.util.LinkedHashSet;
import java.util.Set;
import org.structr.api.util.Iterables;
import org.structr.common.PropertyView;
import org.structr.common.ValidationHelper;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.core.entity.ManyToMany;
import org.structr.core.entity.Relation;
import org.structr.core.entity.SchemaNode;
import org.structr.core.property.LongProperty;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.StringProperty;
import org.structr.schema.SchemaHelper;

/**
 *
 *
 */
public class SchemaRelationship extends ManyToMany<SchemaNode, SchemaNode> {

	public static final Property<String>  name                = new StringProperty("name").indexed();
	public static final Property<String>  relationshipType    = new StringProperty("relationshipType");
	public static final Property<String>  sourceMultiplicity  = new StringProperty("sourceMultiplicity");
	public static final Property<String>  targetMultiplicity  = new StringProperty("targetMultiplicity");
	public static final Property<String>  sourceNotion        = new StringProperty("sourceNotion");
	public static final Property<String>  targetNotion        = new StringProperty("targetNotion");
	public static final Property<String>  sourceJsonName      = new StringProperty("sourceJsonName");
	public static final Property<String>  targetJsonName      = new StringProperty("targetJsonName");
	public static final Property<String>  extendsClass        = new StringProperty("extendsClass").indexed();
	public static final Property<Long>    cascadingDeleteFlag = new LongProperty("cascadingDeleteFlag");
	public static final Property<Long>    autocreationFlag    = new LongProperty("autocreationFlag");

	// internal, do not use externally
	public static final Property<String>  sourceTypeName      = new StringProperty("__internal_Structr_sourceTypeName");


	public static final View defaultView = new View(SchemaRelationship.class, PropertyView.Public,
		name, sourceId, targetId, sourceMultiplicity, targetMultiplicity, sourceNotion, targetNotion, relationshipType,
		sourceJsonName, targetJsonName, extendsClass, cascadingDeleteFlag, autocreationFlag
	);

	public static final View uiView = new View(SchemaRelationship.class, PropertyView.Ui,
		name, sourceId, targetId, sourceMultiplicity, targetMultiplicity, sourceNotion, targetNotion, relationshipType,
		sourceJsonName, targetJsonName, extendsClass, cascadingDeleteFlag, autocreationFlag
	);

	@Override
	public Class<SchemaNode> getSourceType() {
		return SchemaNode.class;
	}

	@Override
	public Class<SchemaNode> getTargetType() {
		return SchemaNode.class;
	}

	@Override
	public Property<String> getSourceIdProperty() {
		return sourceId;
	}

	@Override
	public Property<String> getTargetIdProperty() {
		return targetId;
	}

	@Override
	public String name() {
		return "IS_RELATED_TO";
	}

	@Override
	public int getCascadingDeleteFlag() {
		return Relation.NONE;
	}

	@Override
	public int getAutocreationFlag() {
		return Relation.SOURCE_TO_TARGET;
	}

	@Override
	public Set<PropertyKey> getPropertyKeys(final String propertyView) {

		final Set<PropertyKey> propertyKeys = new LinkedHashSet<>(Iterables.toList(super.getPropertyKeys(propertyView)));

		// add "custom" property keys as String properties
		for (final String key : SchemaHelper.getProperties(getRelationship())) {

			final PropertyKey newKey = new StringProperty(key);
			newKey.setDeclaringClass(getClass());

			propertyKeys.add(newKey);
		}

		return propertyKeys;
	}

	@Override
	public boolean isValid(final ErrorBuffer errorBuffer) {

		boolean valid = super.isValid(errorBuffer);

		valid &= ValidationHelper.isValidStringNotBlank(this, relationshipType, errorBuffer);

		return valid;
	}

	@Override
	public boolean isInternal() {
		return true;
	}
}
