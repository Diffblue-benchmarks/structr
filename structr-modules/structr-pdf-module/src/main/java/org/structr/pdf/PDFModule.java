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
package org.structr.pdf;

import org.structr.api.service.LicenseManager;
import org.structr.core.entity.AbstractSchemaNode;
import org.structr.core.function.Functions;
import org.structr.module.StructrModule;
import org.structr.pdf.function.PDFFunction;
import org.structr.schema.action.Actions;

import java.util.Set;

public class PDFModule implements StructrModule {
	@Override
	public void onLoad(LicenseManager licenseManager) {

		// final boolean basicEdition         = licenseManager == null || licenseManager.isEdition(LicenseManager.Basic);
		// final boolean smallBusinessEdition = licenseManager == null || licenseManager.isEdition(LicenseManager.SmallBusiness);
		final boolean enterpriseEdition    = licenseManager == null || licenseManager.isEdition(LicenseManager.Enterprise);

		Functions.put(enterpriseEdition, LicenseManager.Enterprise, "pdf", new PDFFunction());

	}

	@Override
	public String getName() {
		return "pdf";
	}

	@Override
	public Set<String> getDependencies() {
		return null;
	}

	@Override
	public Set<String> getFeatures() {
		return null;
	}

	@Override
	public void insertImportStatements(AbstractSchemaNode schemaNode, StringBuilder buf) {

	}

	@Override
	public void insertSourceCode(AbstractSchemaNode schemaNode, StringBuilder buf) {

	}

	@Override
	public void insertSaveAction(AbstractSchemaNode schemaNode, StringBuilder buf, Actions.Type type) {

	}

	@Override
	public Set<String> getInterfacesForType(AbstractSchemaNode schemaNode) {
		return null;
	}
}
