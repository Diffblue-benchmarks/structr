/**
 * Copyright (C) 2010-2018 Structr GmbH
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
package org.structr.ldap;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapInvalidAttributeValueException;
import org.structr.common.PropertyView;
import org.structr.common.error.FrameworkException;
import org.structr.core.Services;
import org.structr.core.app.StructrApp;
import org.structr.schema.SchemaService;
import org.structr.schema.json.JsonObjectType;
import org.structr.schema.json.JsonSchema;
import org.structr.web.entity.User;

/**
 *
 */
public interface LDAPUser extends User {

	static class Impl { static {

		final JsonSchema schema    = SchemaService.getDynamicSchema();
		final JsonObjectType type  = schema.addType("LDAPUser");

		type.setExtends(schema.getType("User"));
		type.setImplements(URI.create("https://structr.org/v1.1/definitions/LDAPUser"));

		type.addStringProperty("distinguishedName", PropertyView.Public, PropertyView.Ui).setUnique(true).setIndexed(true);

		type.addPropertyGetter("distinguishedName", String.class);
		type.addPropertySetter("distinguishedName", String.class);

		type.overrideMethod("initializeFrom",  false, LDAPUser.class.getName() + ".initializeFrom(this, arg0);");
		type.overrideMethod("isValidPassword", false, "return " + LDAPUser.class.getName() + ".isValidPassword(this, arg0);");
	}}

	String getDistinguishedName();

	void initializeFrom(final Entry entry) throws FrameworkException;
	void setDistinguishedName(final String distinguishedName) throws FrameworkException;

	static void initializeFrom(final LDAPUser thisUser, final Entry entry) throws FrameworkException {

		final LDAPService ldapService      = Services.getInstance().getService(LDAPService.class);
		final Map<String, String> mappings = new LinkedHashMap<>();

		if (ldapService != null) {

			mappings.putAll(ldapService.getPropertyMapping());
		}

		try {

			// apply mappings
			for (final String key : mappings.keySet()) {

				final String structrName = mappings.get(key);
				final String ldapName    = key;

				thisUser.setProperty(StructrApp.key(LDAPUser.class, structrName), LDAPUser.getString(entry, ldapName));
			}

		} catch (final LdapInvalidAttributeValueException ex) {
			ex.printStackTrace();
		}
	}

	static boolean isValidPassword(final LDAPUser thisUser, final String password) {

		final LDAPService ldapService = Services.getInstance().getService(LDAPService.class);
		final String dn               = thisUser.getDistinguishedName();

		if (ldapService != null) {

			return ldapService.canSuccessfullyBind(dn, password);

		} else {

			logger.warn("Unable to reach LDAP server for authentication of {}", dn);
		}

		return false;
	}

	static String getString(final Entry entry, final String key) throws LdapInvalidAttributeValueException {

		final Attribute attribute = entry.get(key);
		if (attribute != null) {

			return attribute.getString();
		}

		return null;
	}
}
