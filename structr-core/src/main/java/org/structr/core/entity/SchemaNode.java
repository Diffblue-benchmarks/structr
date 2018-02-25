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
package org.structr.core.entity;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.util.Iterables;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.ValidationHelper;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.Export;
import org.structr.core.Services;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.relationship.SchemaRelationshipSourceNode;
import org.structr.core.entity.relationship.SchemaRelationshipTargetNode;
import org.structr.core.graph.ModificationQueue;
import org.structr.core.graph.NodeInterface;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.EndNode;
import org.structr.core.property.EndNodes;
import org.structr.core.property.IntProperty;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.StartNode;
import org.structr.core.property.StartNodes;
import org.structr.core.property.StringProperty;
import org.structr.schema.SchemaHelper;

/**
 *
 *
 */
public class SchemaNode extends AbstractSchemaNode {

	private static final Logger logger                   = LoggerFactory.getLogger(SchemaNode.class.getName());

	private static final Set<String> EntityNameBlacklist = new LinkedHashSet<>(Arrays.asList(new String[] {
		"Relation"
	}));

	public static final Property<List<SchemaRelationshipNode>> relatedTo            = new EndNodes<>("relatedTo", SchemaRelationshipSourceNode.class);
	public static final Property<List<SchemaRelationshipNode>> relatedFrom          = new StartNodes<>("relatedFrom", SchemaRelationshipTargetNode.class);
	public static final Property<String>                       extendsClass         = new StringProperty("extendsClass").indexed();
	public static final Property<String>                       implementsInterfaces = new StringProperty("implementsInterfaces").indexed();
	public static final Property<String>                       defaultSortKey       = new StringProperty("defaultSortKey");
	public static final Property<String>                       defaultSortOrder     = new StringProperty("defaultSortOrder");
	public static final Property<Boolean>                      isBuiltinType        = new BooleanProperty("isBuiltinType").readOnly().indexed();
	public static final Property<Integer>                      hierarchyLevel       = new IntProperty("hierarchyLevel").indexed();
	public static final Property<Integer>                      relCount             = new IntProperty("relCount").indexed();
	public static final Property<Boolean>                      shared               = new BooleanProperty("shared").indexed();
	public static final Property<Boolean>                      isInterface          = new BooleanProperty("isInterface").indexed();
	public static final Property<Boolean>                      isAbstract           = new BooleanProperty("isAbstract").indexed();
	public static final Property<String>                       category             = new StringProperty("category").indexed();

	public static final View defaultView = new View(SchemaNode.class, PropertyView.Public,
		extendsClass, implementsInterfaces, relatedTo, relatedFrom, defaultSortKey, defaultSortOrder, isBuiltinType, hierarchyLevel, relCount, isInterface, isAbstract
	);

	public static final View uiView = new View(SchemaNode.class, PropertyView.Ui,
		name, extendsClass, implementsInterfaces, relatedTo, relatedFrom, defaultSortKey, defaultSortOrder, isBuiltinType, hierarchyLevel, relCount, isInterface, isAbstract, category
	);

	public static final View schemaView = new View(SchemaNode.class, "schema",
		name, extendsClass, implementsInterfaces, relatedTo, relatedFrom, defaultSortKey, defaultSortOrder, isBuiltinType, hierarchyLevel, relCount, isInterface, isAbstract
	);

	public static final View exportView = new View(SchemaNode.class, "export",
		extendsClass, implementsInterfaces, defaultSortKey, defaultSortOrder, isBuiltinType, hierarchyLevel, relCount, isInterface, isAbstract
	);

	@Override
	public void onCreation(SecurityContext securityContext, ErrorBuffer errorBuffer) throws FrameworkException {

		super.onCreation(securityContext, errorBuffer);

		throwExceptionIfTypeAlreadyExists();
	}

	@Override
	public void onModification(SecurityContext securityContext, ErrorBuffer errorBuffer, final ModificationQueue modificationQueue) throws FrameworkException {

		super.onModification(securityContext, errorBuffer, modificationQueue);

		if (modificationQueue.isPropertyModified(this, name)) {
			throwExceptionIfTypeAlreadyExists();
		}
	}

	@Override
	public Set<PropertyKey> getPropertyKeys(final String propertyView) {

		final List<PropertyKey> propertyKeys = new LinkedList<>(Iterables.toList(super.getPropertyKeys(propertyView)));

		// add "custom" property keys as String properties
		for (final String key : SchemaHelper.getProperties(getNode())) {

			final PropertyKey newKey = new StringProperty(key);
			newKey.setDeclaringClass(getClass());

			propertyKeys.add(newKey);
		}

		Collections.sort(propertyKeys, new Comparator<PropertyKey>() {

			@Override
			public int compare(PropertyKey o1, PropertyKey o2) {
				return o1.jsonName().compareTo(o2.jsonName());
			}
		});

		return new LinkedHashSet<>(propertyKeys);
	}

	@Override
	public boolean isValid(final ErrorBuffer errorBuffer) {

		boolean valid = super.isValid(errorBuffer);

		valid &= ValidationHelper.isValidUniqueProperty(this, name, errorBuffer);
		valid &= ValidationHelper.isValidStringMatchingRegex(this, name, "[A-Z][a-zA-Z0-9_]*", errorBuffer);

		return valid;
	}

	@Override
	public String getMultiplicity(final String propertyNameToCheck) {

		String multiplicity = getMultiplicity(this, propertyNameToCheck);

		if (multiplicity == null) {

			try {
				final String parentClass = getProperty(SchemaNode.extendsClass);

				if (parentClass != null) {

					// check if property is defined in parent class
					final SchemaNode parentSchemaNode = StructrApp.getInstance().nodeQuery(SchemaNode.class).andName(StringUtils.substringAfterLast(parentClass, ".")).getFirst();

					if (parentSchemaNode != null) {

						multiplicity = getMultiplicity(parentSchemaNode, propertyNameToCheck);

					}

				}


			} catch (FrameworkException ex) {
				logger.warn("Can't find schema node for parent class!", ex);
			}

		}

		if (multiplicity != null) {
			return multiplicity;
		}

		// fallback, search NodeInterface (this allows the owner relationship to be used in Notions!)
		final PropertyKey key = StructrApp.getConfiguration().getPropertyKeyForJSONName(NodeInterface.class, propertyNameToCheck, false);
		if (key != null) {


			// return "extended" multiplicity when the falling back to a NodeInterface property
			// to signal the code generator that it must not append "Property" to the name of
			// the generated NotionProperty parameter, i.e. NotionProperty(owner, ...) instead
			// of NotionProperty(ownerProperty, ...)..

			if (key instanceof StartNode || key instanceof EndNode) {
				return "1X";
			}

			if (key instanceof StartNodes || key instanceof EndNodes) {
				return "*X";
			}
		}

		return null;
	}

	private String getMultiplicity(final SchemaNode schemaNode, final String propertyNameToCheck) {

		final Set<String> existingPropertyNames = new LinkedHashSet<>();
		final String _className                 = schemaNode.getProperty(name);

		for (final SchemaRelationshipNode outRel : schemaNode.getProperty(SchemaNode.relatedTo)) {

			if (propertyNameToCheck.equals(outRel.getPropertyName(_className, existingPropertyNames, true))) {
				return outRel.getMultiplicity(true);
			}
		}

		// output related node definitions, collect property views
		for (final SchemaRelationshipNode inRel : schemaNode.getProperty(SchemaNode.relatedFrom)) {

			if (propertyNameToCheck.equals(inRel.getPropertyName(_className, existingPropertyNames, false))) {
				return inRel.getMultiplicity(false);
			}
		}

		return null;
	}

	@Override
	public String getRelatedType(final String propertyNameToCheck) {

		String relatedType = getRelatedType(this, propertyNameToCheck);

		if (relatedType == null) {

			try {

				final String parentClass = getProperty(SchemaNode.extendsClass);

				if (parentClass != null) {

					// check if property is defined in parent class
					final SchemaNode parentSchemaNode = StructrApp.getInstance().nodeQuery(SchemaNode.class).andName(StringUtils.substringAfterLast(parentClass, ".")).getFirst();

					if (parentSchemaNode != null) {

						relatedType = getRelatedType(parentSchemaNode, propertyNameToCheck);

					}
				}

			} catch (FrameworkException ex) {
				logger.warn("Can't find schema node for parent class!", ex);
			}

		}

		if (relatedType != null) {
			return relatedType;
		}

		// fallback, search NodeInterface (this allows the owner relationship to be used in Notions!)
		final PropertyKey key = StructrApp.getConfiguration().getPropertyKeyForJSONName(NodeInterface.class, propertyNameToCheck, false);
		if (key != null) {

			final Class relatedTypeClass = key.relatedType();
			if (relatedTypeClass != null) {

				return relatedTypeClass.getSimpleName();
			}
		}

		return null;
	}

	public void handleMigration() throws FrameworkException {

		// we need to consider the following cases:
		//  - class extends other dynamic class => no change
		//  - class extends FileBase => make it extend AbstractNode and implement File
		//  - class extends built-in type => make it extend AbstractNode and implement dynamic interface

		final String tmp = getProperty(extendsClass);
		if (tmp != null) {


			final String interfaces = getProperty(implementsInterfaces);
			String _extendsClass = StringUtils.substringBefore(tmp, "<"); // remove optional generic parts from class name

			// migrate FileBase
			if (_extendsClass.equals("org.structr.web.entity.FileBase")) {

				removeProperty(extendsClass);
				setProperty(implementsInterfaces, addToList(interfaces, "org.structr.web.entity.File"));
				return;
			}

			// migrate Image
			if (_extendsClass.equals("org.structr.web.entity.Image")) {

				setProperty(extendsClass, "org.structr.dynamic.Image");
				return;
			}

			// migrate Person
			if (_extendsClass.equals("org.structr.core.entity.Person")) {

				setProperty(extendsClass, "org.structr.dynamic.Person");
				return;
			}

			// migrate XMPPClient
			if (_extendsClass.equals("org.structr.xmpp.XMPPClient")) {

				setProperty(implementsInterfaces, addToList(interfaces, "org.structr.xmpp.XMPPClient"));
				removeProperty(extendsClass);
				return;
			}

			// we need to migrate the feed package
			if (_extendsClass.startsWith("org.structr.web.entity.feed.")) {

				_extendsClass = _extendsClass.replace("org.structr.web.entity.feed.", "org.structr.feed.entity.");
				setProperty(extendsClass, _extendsClass);
			}

			// move most of the extendsClass to implementsInterfaces
			if (
				_extendsClass.startsWith("org.structr.knowledge.")
				||
				(
					!_extendsClass.endsWith("Impl") &&
					_extendsClass.contains(".entity.") &&
					_extendsClass.startsWith("org.structr.") &&
					!_extendsClass.startsWith("org.structr.dynamic.") &&
					!AbstractNode.class.getName().equals(_extendsClass)
				)
			) {

				setProperty(implementsInterfaces, addToList(interfaces, _extendsClass));
				removeProperty(extendsClass);
			}
		}
	}

	@Export
	public String getGeneratedSourceCode() throws FrameworkException {
		return SchemaHelper.getSource(this, new ErrorBuffer());
	}

	@Export
	public void applySourceCode(final Map<String, Object> parameters) throws FrameworkException {

		final String sourceCode               = (String)parameters.get("sourceCode");
		final CompilationUnit cu              = JavaParser.parse(sourceCode);
		final List<ImportDeclaration> imports = cu.getImports();
		final List<TypeDeclaration<?>> types  = cu.getTypes();
		final TypeDeclaration type            = types.get(0);

		if (this.getName().equals(type.getName().asString())) {

			applyImports(imports);

			final List<BodyDeclaration<?>> members = type.getMembers();
			for (final BodyDeclaration d : members) {

				if (d.isMethodDeclaration()) {

					applyMethodDeclaration(d.asMethodDeclaration());

				} else if (d.isFieldDeclaration()) {

					applyFieldDeclaration(d.asFieldDeclaration());
				}
			}

		} else {

			throw new FrameworkException(422, "Class name does not match, cannot apply source code.");
		}
	}

	// ----- private methods -----
	private String addToList(final String source, final String value) {

		final List<String> list = new LinkedList<>();

		if (source != null) {

			list.addAll(Arrays.asList(source.split(",")));
		}

		list.add(value);

		return StringUtils.join(list, ",");
	}


	private String getRelatedType(final SchemaNode schemaNode, final String propertyNameToCheck) {

		final Set<String> existingPropertyNames = new LinkedHashSet<>();
		final String _className                 = schemaNode.getProperty(name);

		for (final SchemaRelationshipNode outRel : schemaNode.getProperty(SchemaNode.relatedTo)) {

			if (propertyNameToCheck.equals(outRel.getPropertyName(_className, existingPropertyNames, true))) {
				return outRel.getSchemaNodeTargetType();
			}
		}

		// output related node definitions, collect property views
		for (final SchemaRelationshipNode inRel : schemaNode.getProperty(SchemaNode.relatedFrom)) {

			if (propertyNameToCheck.equals(inRel.getPropertyName(_className, existingPropertyNames, false))) {
				return inRel.getSchemaNodeSourceType();
			}
		}

		return null;
	}

	/**
	* If the system is fully initialized (and no schema replacement is currently active), we disallow overriding (known) existing types so we can prevent unwanted behavior.
	* If a user were to create a type 'Html', he could cripple Structrs Page rendering completely.
	* This is a fix for all types in the Structr context - this does not help if the user creates a type named 'String' or 'Object'.
	* That could still lead to unexpected behavior.
	*
	* @throws FrameworkException if a pre-existing type is encountered
	*/
	private void throwExceptionIfTypeAlreadyExists() throws FrameworkException {

		if (Services.getInstance().isInitialized() && ! Services.getInstance().isOverridingSchemaTypesAllowed()) {

			final String typeName = getProperty(name);

			// add type names to list of forbidden entity names
			if (EntityNameBlacklist.contains(typeName)) {
				throw new FrameworkException(422, "Type '" + typeName + "' already exists. To prevent unwanted/unexpected behavior this is forbidden.");
			}

			/*
			// add type names to list of forbidden entity names
			if (StructrApp.getConfiguration().getNodeEntities().containsKey(typeName)) {
				throw new FrameworkException(422, "Type '" + typeName + "' already exists. To prevent unwanted/unexpected behavior this is forbidden.");
			}

			// add interfaces to list of forbidden entity names
			if (StructrApp.getConfiguration().getInterfaces().containsKey(typeName)) {
				throw new FrameworkException(422, "Type '" + typeName + "' already exists. To prevent unwanted/unexpected behavior this is forbidden.");
			}
			*/
		}
	}

	private void applyImports(final List<ImportDeclaration> imports) throws FrameworkException {

	}

	private void applyMethodDeclaration(final MethodDeclaration method) throws FrameworkException {

		final String name                   = method.getNameAsString();
		final List<SchemaMethod> candidates = StructrApp.getInstance().nodeQuery(SchemaMethod.class).and(SchemaMethod.schemaNode, this).and(SchemaMethod.name, name).getAsList();

		if (candidates.isEmpty()) {

			logger.info("Detected new or renamed method {}", name);

		} else {

			logger.info("Detected {} candidates for method {}", candidates.size(), name);
		}
	}

	private void applyFieldDeclaration(final FieldDeclaration field) throws FrameworkException {

		final List<VariableDeclarator> variables = field.getVariables();

		for (final VariableDeclarator var : variables) {

			final String name              = var.getNameAsString();
			final Type type                = var.getType();

			if (type.isClassOrInterfaceType()) {

				final ObjectCreationExpr expr  = getCreationExpression(var.getInitializer());
				if (expr != null) {

					final ClassOrInterfaceType cit = type.asClassOrInterfaceType();
					final String typeName          = cit.getNameAsString();

					logger.info("Detected field of type {} with name {}, creation of {}", typeName, name, expr.getType().getNameAsString());

				}

			}
		}

	}

	private ObjectCreationExpr getCreationExpression(final Optional<Expression> optional) {

		if (optional.isPresent()) {

			final Expression expression = optional.get();

			if (expression.isMethodCallExpr()) {

				// recurse to remove trailing method calls of builder pattern
				// (e.g. new StringProperty(..).indexed().dynamic().readOnly() etc.
				return getCreationExpression(expression.asMethodCallExpr().getScope());
			}

			if (expression.isObjectCreationExpr()) {

				return expression.asObjectCreationExpr();
			}

			System.out.println("Unhandled expression type " + expression.getClass().getName());
		}

		return null;
	}
}
