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
package org.structr.schema;

import graphql.Scalars;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import static graphql.schema.GraphQLTypeReference.typeRef;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import javatools.parsers.PlingStemmer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.graph.PropertyContainer;
import org.structr.api.service.LicenseManager;
import org.structr.api.util.Iterables;
import org.structr.common.CaseHelper;
import org.structr.common.GraphObjectComparator;
import org.structr.common.PermissionPropagation;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.ValidationHelper;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.common.error.InvalidPropertySchemaToken;
import org.structr.common.error.UnlicensedTypeException;
import org.structr.core.Export;
import org.structr.core.GraphObject;
import org.structr.core.GraphObjectMap;
import org.structr.core.Services;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.entity.AbstractSchemaNode;
import org.structr.core.entity.DynamicResourceAccess;
import org.structr.core.entity.Relation;
import org.structr.core.entity.ResourceAccess;
import org.structr.core.entity.SchemaMethod;
import static org.structr.core.entity.SchemaMethod.source;
import org.structr.core.entity.SchemaNode;
import static org.structr.core.entity.SchemaNode.defaultSortKey;
import static org.structr.core.entity.SchemaNode.defaultSortOrder;
import org.structr.core.entity.SchemaProperty;
import org.structr.core.entity.SchemaRelationshipNode;
import org.structr.core.entity.SchemaView;
import org.structr.core.graph.ModificationQueue;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.RelationshipInterface;
import org.structr.core.graph.TransactionCommand;
import org.structr.core.graphql.GraphQLListType;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.GenericProperty;
import org.structr.core.property.LongProperty;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.RelationProperty;
import org.structr.core.property.StringProperty;
import org.structr.module.StructrModule;
import org.structr.schema.action.ActionEntry;
import org.structr.schema.action.Actions;
import org.structr.schema.parser.BooleanArrayPropertyParser;
import org.structr.schema.parser.BooleanPropertyParser;
import org.structr.schema.parser.CountPropertyParser;
import org.structr.schema.parser.CustomPropertyParser;
import org.structr.schema.parser.CypherPropertyParser;
import org.structr.schema.parser.DateArrayPropertyParser;
import org.structr.schema.parser.DatePropertyParser;
import org.structr.schema.parser.DoubleArrayPropertyParser;
import org.structr.schema.parser.DoublePropertyParser;
import org.structr.schema.parser.EnumPropertyParser;
import org.structr.schema.parser.FunctionPropertyParser;
import org.structr.schema.parser.IdNotionPropertyParser;
import org.structr.schema.parser.IntPropertyParser;
import org.structr.schema.parser.IntegerArrayPropertyParser;
import org.structr.schema.parser.JoinPropertyParser;
import org.structr.schema.parser.LongArrayPropertyParser;
import org.structr.schema.parser.LongPropertyParser;
import org.structr.schema.parser.NotionPropertyParser;
import org.structr.schema.parser.PasswordPropertySourceGenerator;
import org.structr.schema.parser.PropertyDefinition;
import org.structr.schema.parser.PropertySourceGenerator;
import org.structr.schema.parser.StringArrayPropertyParser;
import org.structr.schema.parser.StringBasedPropertyDefinition;
import org.structr.schema.parser.StringPropertySourceGenerator;
import org.structr.schema.parser.Validator;

/**
 *
 *
 */
public class SchemaHelper {

	private static final Logger logger                   = LoggerFactory.getLogger(SchemaHelper.class.getName());
	private static final String WORD_SEPARATOR           = "_";

	public enum Type {

		String, StringArray, DateArray, LongArray, DoubleArray, IntegerArray, BooleanArray, Integer, Long, Double, Boolean, Enum, Date, Count, Function, Notion, IdNotion, Cypher, Join, Thumbnail, Password, Custom;
	}

	public static final Map<Type, Class<? extends PropertySourceGenerator>> parserMap = new TreeMap<>(new ReverseTypeComparator());
	public static final Map<Type, GraphQLScalarType> graphQLTypeMap                   = new LinkedHashMap<>();
	public static final Map<Type, Integer> sortIndexMap                               = new LinkedHashMap<>();
	private static final Map<String, String> normalizedEntityNameCache                = new LinkedHashMap<>();
	private static final Set<String> basePropertyNames                                = new LinkedHashSet<>(Arrays.asList(
		"base", "type", "id", "createdDate", "createdBy", "lastModifiedDate", "lastModifiedBy", "visibleToPublicUsers", "visibleToAuthenticatedUsers",		// from GraphObject
		"relType", "sourceNode", "targetNode", "sourceId", "targetId", "sourceNodeProperty", "targetNodeProperty",				// from AbstractRelationship
		"name", "hidden", "owner", "ownerId", "grantees"													// from NodeInterface
	));

	static {

		// IMPORTANT: parser map must be sorted by type name length
		//            because we look up the parsers using "startsWith"!
		parserMap.put(Type.BooleanArray, BooleanArrayPropertyParser.class);
		parserMap.put(Type.IntegerArray, IntegerArrayPropertyParser.class);
		parserMap.put(Type.DoubleArray, DoubleArrayPropertyParser.class);
		parserMap.put(Type.StringArray, StringArrayPropertyParser.class);
		parserMap.put(Type.DateArray, DateArrayPropertyParser.class);
		parserMap.put(Type.LongArray, LongArrayPropertyParser.class);
		parserMap.put(Type.Function, FunctionPropertyParser.class);
		parserMap.put(Type.Password, PasswordPropertySourceGenerator.class);
		parserMap.put(Type.IdNotion, IdNotionPropertyParser.class);
		parserMap.put(Type.Boolean, BooleanPropertyParser.class);
		parserMap.put(Type.Integer, IntPropertyParser.class);
		parserMap.put(Type.String, StringPropertySourceGenerator.class);
		parserMap.put(Type.Double, DoublePropertyParser.class);
		parserMap.put(Type.Custom, CustomPropertyParser.class);
		parserMap.put(Type.Notion, NotionPropertyParser.class);
		parserMap.put(Type.Cypher, CypherPropertyParser.class);
		parserMap.put(Type.Long, LongPropertyParser.class);
		parserMap.put(Type.Enum, EnumPropertyParser.class);
		parserMap.put(Type.Date, DatePropertyParser.class);
		parserMap.put(Type.Count, CountPropertyParser.class);
		parserMap.put(Type.Join, JoinPropertyParser.class);

		// IMPORTANT: parser map must be sorted by type name length
		//            because we look up the parsers using "startsWith"!
		sortIndexMap.put(Type.BooleanArray, 0);
		sortIndexMap.put(Type.IntegerArray, 1);
		sortIndexMap.put(Type.DoubleArray,  2);
		sortIndexMap.put(Type.StringArray,  3);
		sortIndexMap.put(Type.DateArray,    4);
		sortIndexMap.put(Type.LongArray,    5);
		sortIndexMap.put(Type.Password,     6);
		sortIndexMap.put(Type.Boolean,      7);
		sortIndexMap.put(Type.Integer,      8);
		sortIndexMap.put(Type.String,       9);
		sortIndexMap.put(Type.Double,      10);
		sortIndexMap.put(Type.Long,        11);
		sortIndexMap.put(Type.Enum,        12);
		sortIndexMap.put(Type.Date,        13);
		sortIndexMap.put(Type.Function,    14);
		sortIndexMap.put(Type.Cypher,      15);
		sortIndexMap.put(Type.Count,       16);
		sortIndexMap.put(Type.Custom,      17);
		sortIndexMap.put(Type.Join,        18);
		sortIndexMap.put(Type.IdNotion,    19);
		sortIndexMap.put(Type.Notion,      20);

		graphQLTypeMap.put(Type.Password, Scalars.GraphQLString);
		graphQLTypeMap.put(Type.Boolean, Scalars.GraphQLBoolean);
		graphQLTypeMap.put(Type.Integer, Scalars.GraphQLInt);
		graphQLTypeMap.put(Type.String, Scalars.GraphQLString);
		graphQLTypeMap.put(Type.Double, Scalars.GraphQLFloat);
		graphQLTypeMap.put(Type.Count, Scalars.GraphQLInt);
		graphQLTypeMap.put(Type.Long, Scalars.GraphQLLong);
		graphQLTypeMap.put(Type.Enum, Scalars.GraphQLString);
		graphQLTypeMap.put(Type.Date, Scalars.GraphQLString);
	}

	/**
	 * Tries to normalize (and singularize) the given string so that it
	 * resolves to an existing entity type.
	 *
	 * @param possibleEntityString
	 * @return the normalized entity name in its singular form
	 */
	public static String normalizeEntityName(String possibleEntityString) {

		if (possibleEntityString == null) {

			return null;

		}

		if ("/".equals(possibleEntityString)) {

			return "/";

		}

		final StringBuilder result = new StringBuilder();

		if (possibleEntityString.contains("/")) {

			final String[] names = StringUtils.split(possibleEntityString, "/");

			for (String possibleEntityName : names) {

				// CAUTION: this cache might grow to a very large size, as it
				// contains all normalized mappings for every possible
				// property key / entity name that is ever called.
				String normalizedType = normalizedEntityNameCache.get(possibleEntityName);

				if (normalizedType == null) {

					normalizedType = StringUtils.capitalize(CaseHelper.toUpperCamelCase(stem(possibleEntityName)));

				}

				result.append(normalizedType).append("/");

			}

			return StringUtils.removeEnd(result.toString(), "/");

		} else {

//                      CAUTION: this cache might grow to a very large size, as it
			// contains all normalized mappings for every possible
			// property key / entity name that is ever called.
			String normalizedType = normalizedEntityNameCache.get(possibleEntityString);

			if (normalizedType == null) {

				normalizedType = StringUtils.capitalize(CaseHelper.toUpperCamelCase(stem(possibleEntityString)));

			}

			return normalizedType;
		}
	}

	private static String stem(final String term) {


		String lastWord;
		String begin = "";

		if (StringUtils.contains(term, WORD_SEPARATOR)) {

			lastWord = StringUtils.substringAfterLast(term, WORD_SEPARATOR);
			begin = StringUtils.substringBeforeLast(term, WORD_SEPARATOR);

		} else {

			lastWord = term;

		}

		lastWord = PlingStemmer.stem(lastWord);

		return begin.concat(WORD_SEPARATOR).concat(lastWord);

	}

	public static Class getEntityClassForRawType(final String rawType) {

		// first try: raw name
		Class type = getEntityClassForRawType(rawType, false);
		if (type == null) {

			// second try: normalized name
			type = getEntityClassForRawType(rawType, true);
		}

		return type;
	}

	private static Class getEntityClassForRawType(final String rawType, final boolean normalize) {

		final String normalizedEntityName = normalize ? normalizeEntityName(rawType) : rawType;
		final ConfigurationProvider configuration = StructrApp.getConfiguration();

		// first try: node entity
		Class type = configuration.getNodeEntities().get(normalizedEntityName);

		// second try: relationship entity
		if (type == null) {
			type = configuration.getRelationshipEntities().get(normalizedEntityName);
		}

		// third try: interface
		if (type == null) {
			type = configuration.getInterfaces().get(normalizedEntityName);
		}

		// third try: interface with FQN
		if (type == null) {
			type = configuration.getInterfaces().get("org.structr.dynamic." + normalizedEntityName);
		}

		// store type but only if it exists!
		if (type != null) {
			normalizedEntityNameCache.put(rawType, type.getSimpleName());
		}

		// fallback to support generic queries on all types
		if (type == null) {

			if (AbstractNode.class.getSimpleName().equals(rawType)) {
				return AbstractNode.class;
			}

			if (NodeInterface.class.getSimpleName().equals(rawType)) {
				return NodeInterface.class;
			}

			if (AbstractRelationship.class.getSimpleName().equals(rawType)) {
				return AbstractRelationship.class;
			}

			if (RelationshipInterface.class.getSimpleName().equals(rawType)) {
				return RelationshipInterface.class;
			}
		}

		return type;
	}

	public static boolean reloadSchema(final ErrorBuffer errorBuffer, final String initiatedBySessionId) {

		try {

			final App app = StructrApp.getInstance();

			final List<SchemaNode> existingSchemaNodes = app.nodeQuery(SchemaNode.class).getAsList();

			// initialize cache
			app.nodeQuery(ResourceAccess.class).getAsList();

			cleanUnusedDynamicGrants(existingSchemaNodes);

			for (final SchemaNode schemaNode : existingSchemaNodes) {

				createDynamicGrants(schemaNode.getResourceSignature());

			}

			for (final SchemaRelationshipNode schemaRelationship : StructrApp.getInstance().nodeQuery(SchemaRelationshipNode.class).getAsList()) {

				createDynamicGrants(schemaRelationship.getResourceSignature());
				createDynamicGrants(schemaRelationship.getInverseResourceSignature());

			}

		} catch (Throwable t) {

			logger.warn("", t);
		}

		return SchemaService.reloadSchema(errorBuffer, initiatedBySessionId);

	}

	public static void cleanUnusedDynamicGrants(final List<SchemaNode> existingSchemaNodes) {

		try {

			final List<DynamicResourceAccess> existingDynamicGrants = StructrApp.getInstance().nodeQuery(DynamicResourceAccess.class).getAsList();
			final Set<String> existingSchemaNodeNames               = new HashSet<>();

			for (final SchemaNode schemaNode : existingSchemaNodes) {

				existingSchemaNodeNames.add(schemaNode.getResourceSignature());
			}

			for (final DynamicResourceAccess grant : existingDynamicGrants) {

				boolean foundAllParts = true;

				if (!TransactionCommand.isDeleted(grant.getNode())) {

					final String sig = grant.getResourceSignature();

					// Try to find schema nodes for all parts of the grant signature
					final String[] parts = StringUtils.split(sig, "/");

					if (parts != null) {


						for (final String sigPart : parts) {

							if ("/".equals(sigPart) || sigPart.startsWith("_")) {
								continue;
							}

							// If one of the signature parts doesn't have an equivalent existing schema node, remove it
							foundAllParts &= existingSchemaNodeNames.contains(sigPart);
						}
					}

					if (!foundAllParts) {

						logger.info("Did not find all parts of signature, will be removed: {} ", sig);

						removeDynamicGrants(sig);
					}
				}
			}

		} catch (Throwable t) {

			logger.warn("", t);
		}
	}

	public static List<DynamicResourceAccess> createDynamicGrants(final String signature) {

		final List<DynamicResourceAccess> grants = new LinkedList<>();
		final long initialFlagsValue = 0;

		final App app = StructrApp.getInstance();
		try {

			ResourceAccess grant = app.nodeQuery(ResourceAccess.class).and(ResourceAccess.signature, signature).getFirst();

			if (grant == null) {

				// create new grant
				grants.add(app.create(DynamicResourceAccess.class,
					new NodeAttribute(DynamicResourceAccess.signature, signature),
					new NodeAttribute(DynamicResourceAccess.flags, initialFlagsValue)
				));

				logger.debug("New signature created: {}", new Object[]{ (signature) });
			}

			final String schemaSig = schemaResourceSignature(signature);

			ResourceAccess schemaGrant = app.nodeQuery(ResourceAccess.class).and(ResourceAccess.signature, schemaSig).getFirst();
			if (schemaGrant == null) {
				// create additional grant for the _schema resource
				grants.add(app.create(DynamicResourceAccess.class,
					new NodeAttribute(DynamicResourceAccess.signature, schemaSig),
					new NodeAttribute(DynamicResourceAccess.flags, initialFlagsValue)
				));

				logger.debug("New signature created: {}", new Object[]{ schemaSig });
			}

			final String uiSig = uiViewResourceSignature(signature);

			ResourceAccess uiViewGrant = app.nodeQuery(ResourceAccess.class).and(ResourceAccess.signature, uiSig).getFirst();
			if (uiViewGrant == null) {

				// create additional grant for the Ui view
				grants.add(app.create(DynamicResourceAccess.class,
					new NodeAttribute(DynamicResourceAccess.signature, uiSig),
					new NodeAttribute(DynamicResourceAccess.flags, initialFlagsValue)
				));

				logger.debug("New signature created: {}", new Object[]{ uiSig });
			}

		} catch (Throwable t) {

			logger.warn("", t);
		}

		return grants;

	}

	public static void removeAllDynamicGrants() {

		final App app = StructrApp.getInstance();
		try {

			// delete grants
			for (DynamicResourceAccess grant : app.nodeQuery(DynamicResourceAccess.class).getAsList()) {
				app.delete(grant);
			}

		} catch (Throwable t) {

			logger.warn("", t);
		}
	}

	public static void removeDynamicGrants(final String signature) {

		final App app = StructrApp.getInstance();
		try {

			// delete grant
			DynamicResourceAccess grant = app.nodeQuery(DynamicResourceAccess.class).and(DynamicResourceAccess.signature, signature).getFirst();
			if (grant != null) {

				app.delete(grant);
			}

			// delete grant
			DynamicResourceAccess schemaGrant = app.nodeQuery(DynamicResourceAccess.class).and(DynamicResourceAccess.signature, "_schema/" + signature).getFirst();
			if (schemaGrant != null) {

				app.delete(schemaGrant);
			}
			// delete grant
			DynamicResourceAccess viewGrant = app.nodeQuery(DynamicResourceAccess.class).and(DynamicResourceAccess.signature, signature + "/_Ui").getFirst();
			if (viewGrant != null) {

				app.delete(viewGrant);
			}

		} catch (Throwable t) {

			logger.warn("", t);
		}

	}

	public static String getSource(final AbstractSchemaNode schemaNode, final Map<String, SchemaNode> schemaNodes, final Set<String> blacklist, final ErrorBuffer errorBuffer) throws FrameworkException, UnlicensedTypeException {

		final Collection<StructrModule> modules                = StructrApp.getConfiguration().getModules().values();
		final Map<String, List<ActionEntry>> methods           = new LinkedHashMap<>();
		final Map<String, Set<String>> viewProperties          = new LinkedHashMap<>();
		final List<String> propertyValidators                  = new LinkedList<>();
		final Set<String> existingPropertyNames                = new LinkedHashSet<>();
		final Set<String> compoundIndexKeys                    = new LinkedHashSet<>();
		final Set<String> propertyNames                        = new LinkedHashSet<>();
		final Set<String> relationshipPropertyNames            = new LinkedHashSet<>();
		final Set<Validator> validators                        = new LinkedHashSet<>();
		final Set<String> implementedInterfaces                = new LinkedHashSet<>();
		final List<String> importStatements                    = new LinkedList<>();
		final Set<String> enums                                = new LinkedHashSet<>();
		final StringBuilder src                                = new StringBuilder();
		final StringBuilder mixinCodeBuffer                    = new StringBuilder();
		final Class baseType                                   = AbstractNode.class;
		final String _className                                = schemaNode.getProperty(SchemaNode.name);
		final String _extendsClass                             = schemaNode.getProperty(SchemaNode.extendsClass);
		final String superClass                                = _extendsClass != null ? _extendsClass : baseType.getSimpleName();
		final boolean extendsAbstractNode                      = _extendsClass == null;

		// check superclass
		if (!extendsAbstractNode && !superClass.startsWith("org.structr.dynamic.") && !SchemaHelper.hasType(superClass)) {

			// we can only detect if a type is missing that is usually provided by a module; we
			// can not detect whether a dynamic type is missing because those are only available
			// after compiling the whole set of schema nodes
			logger.warn("Dynamic type {} cannot be used, superclass {} not defined.", schemaNode.getName(), superClass);
			return null;
		}

		// import mixins, check that all types exist and return null otherwise (causing this class to be ignored)
		SchemaHelper.collectInterfaces(schemaNode, implementedInterfaces);

		// package name
		src.append("package org.structr.dynamic;\n\n");

		// include import statements from mixins
		SchemaHelper.formatImportStatements(schemaNode, src, baseType, importStatements);

		if (schemaNode.getProperty(SchemaNode.isInterface)) {

			// create interface
			src.append("public interface ");
			src.append(_className);

			// output implemented interfaces
			if (!implementedInterfaces.isEmpty()) {

				src.append(" extends ");
				src.append(StringUtils.join(implementedInterfaces, ", "));
			}

		} else {

			// create class
			src.append("public ");

			if (schemaNode.getProperty(SchemaNode.isAbstract)) {
				src.append("abstract ");
			}

			src.append("class ");
			src.append(_className);
			src.append(" extends ");
			src.append(superClass);

			// output implemented interfaces
			if (!implementedInterfaces.isEmpty()) {

				src.append(" implements ");
				src.append(StringUtils.join(implementedInterfaces, ", "));
			}
		}

		src.append(" {\n\n");

		// output related node definitions, collect property views
		for (final SchemaRelationshipNode outRel : schemaNode.getProperty(SchemaNode.relatedTo)) {

			// skip relationship properties whose endpoint types are blacklisted
			if (blacklist.contains(outRel.getSchemaNodeTargetType())) {
				continue;
			}

			final String propertyName = outRel.getPropertyName(_className, existingPropertyNames, true);

			propertyNames.add(propertyName);

			src.append(outRel.getPropertySource(propertyName, true));

			// built-in schema views are controlled manually, but all user-generated
			// schema changes are expected to be added to "custom" view.
			if (!outRel.getProperty(SchemaRelationshipNode.isPartOfBuiltInSchema)) {
				addPropertyToView(PropertyView.Custom, propertyName, viewProperties);
			}

			relationshipPropertyNames.add(propertyName);
		}

		// output related node definitions, collect property views
		for (final SchemaRelationshipNode inRel : schemaNode.getProperty(SchemaNode.relatedFrom)) {

			// skip relationship properties whose endpoint types are blacklisted
			if (blacklist.contains(inRel.getSchemaNodeSourceType())) {
				continue;
			}

			final String propertyName = inRel.getPropertyName(_className, existingPropertyNames, false);

			propertyNames.add(propertyName);

			src.append(inRel.getPropertySource(propertyName, false));

			// built-in schema views are controlled manually, but all user-generated
			// schema changes are expected to be added to "custom" view.
			if (!inRel.getProperty(SchemaRelationshipNode.isPartOfBuiltInSchema)) {
				SchemaHelper.addPropertyToView(PropertyView.Custom, propertyName, viewProperties);
			}

			relationshipPropertyNames.add(propertyName);
		}

		src.append(SchemaHelper.extractProperties(schemaNodes, schemaNode, propertyNames, validators, compoundIndexKeys, enums, viewProperties, propertyValidators, errorBuffer));

		SchemaHelper.extractViews(schemaNodes, schemaNode, viewProperties, relationshipPropertyNames, errorBuffer);
		SchemaHelper.extractMethods(schemaNodes, schemaNode, methods);

		// output possible enum definitions
		for (final String enumDefition : enums) {
			src.append(enumDefition);
		}

		for (Entry<String, Set<String>> entry : viewProperties.entrySet()) {

			final String viewName  = entry.getKey();
			final Set<String> view = entry.getValue();

			if (!view.isEmpty()) {

				schemaNode.addDynamicView(viewName);

				SchemaHelper.formatView(src, _className, viewName, viewName, view);
			}
		}

		if (schemaNode.getProperty(defaultSortKey) != null) {

			String order = schemaNode.getProperty(defaultSortOrder);
			if (order == null || "desc".equals(order)) {
				order = "GraphObjectComparator.DESCENDING";
			} else {
				order = "GraphObjectComparator.ASCENDING";
			}

			src.append("\n\t@Override\n");
			src.append("\tpublic PropertyKey getDefaultSortKey() {\n");
			src.append("\t\treturn ").append(schemaNode.getProperty(defaultSortKey)).append("Property;\n");
			src.append("\t}\n");

			src.append("\n\t@Override\n");
			src.append("\tpublic String getDefaultSortOrder() {\n");
			src.append("\t\treturn ").append(order).append(";\n");
			src.append("\t}\n");
		}

		SchemaHelper.formatValidators(src, validators, compoundIndexKeys, extendsAbstractNode, propertyValidators);
		SchemaHelper.formatMethods(schemaNode, src, methods, implementedInterfaces);

		// insert dynamic code here
		src.append(mixinCodeBuffer);

		// insert source code from module
		for (final StructrModule module : modules) {
			module.insertSourceCode(schemaNode, src);
		}

		src.append("}\n");

		return src.toString();
	}

	public static Set<String> getUnlicensedTypes(final SchemaNode schemaNode) throws FrameworkException {

		final String _extendsClass              = schemaNode.getProperty(SchemaNode.extendsClass);
		final String superClass                 = _extendsClass != null ? _extendsClass : AbstractNode.class.getSimpleName();
		final Set<String> implementedInterfaces = new LinkedHashSet<>();

		// import mixins, check that all types exist and return null otherwise (causing this class to be ignored)
		SchemaHelper.collectInterfaces(schemaNode, implementedInterfaces);

		// check if base types and interfaces are part of the licensed package
		return checkLicense(Services.getInstance().getLicenseManager(), superClass, implementedInterfaces);
	}

	public static String extractProperties(final Map<String, SchemaNode> schemaNodes, final Schema entity, final Set<String> propertyNames, final Set<Validator> validators, final Set<String> compoundIndexKeys, final Set<String> enums, final Map<String, Set<String>> views, final List<String> propertyValidators, final ErrorBuffer errorBuffer) throws FrameworkException {

		final PropertyContainer propertyContainer = entity.getPropertyContainer();
		final StringBuilder src                   = new StringBuilder();

		// output property source code and collect views
		for (String propertyName : SchemaHelper.getProperties(propertyContainer)) {

			if (!propertyName.startsWith("__") && propertyContainer.hasProperty(propertyName)) {

				String rawType = propertyContainer.getProperty(propertyName).toString();

				PropertySourceGenerator parser = SchemaHelper.getSourceGenerator(errorBuffer, entity.getClassName(), new StringBasedPropertyDefinition(propertyName, rawType));
				if (parser != null) {

					// migrate properties
					if (entity instanceof AbstractSchemaNode) {
						parser.createSchemaPropertyNode((AbstractSchemaNode)entity, propertyName);
					}
				}
			}
		}

		final List<SchemaProperty> schemaProperties = Iterables.toList(entity.getSchemaProperties());
		if (schemaProperties != null) {

			// sort properties to avoid initialization issues with notion properties
			Collections.sort(schemaProperties, new PropertyTypeComparator());

			for (final SchemaProperty schemaProperty : schemaProperties) {

				String propertyName = schemaProperty.getPropertyName();
				String oldName      = propertyName;
				int count           = 1;

				if (propertyNames.contains(propertyName)) {

					while (propertyNames.contains(propertyName)) {
						propertyName = propertyName + count++;
					}

					logger.warn("Property name {} already present in type {}, renaming to {}", oldName, entity.getClassName(), propertyName);
					logger.warn("Offending property is {} with ID {}, name {}, type {}", schemaProperty.getClass().getSimpleName(), schemaProperty.getUuid(), schemaProperty.getName(), schemaProperty.getPropertyType());

					schemaProperty.setProperty(SchemaProperty.name, propertyName);
				}

				propertyNames.add(propertyName);

				if (!schemaProperty.getProperty(SchemaProperty.isBuiltinProperty)) {

					// migrate property source
					if (Type.Function.equals(schemaProperty.getPropertyType())) {

						// Note: This is a temporary migration from the old format to the new readFunction property
						final String format = schemaProperty.getFormat();
						if (format != null) {

							try {
								schemaProperty.setProperty(SchemaProperty.readFunction, format);
								schemaProperty.setProperty(SchemaProperty.format, null);

							} catch (FrameworkException ex) {

								logger.warn("", ex);
							}
						}

					}

					final PropertySourceGenerator parser = SchemaHelper.getSourceGenerator(errorBuffer, entity.getClassName(), schemaProperty);
					if (parser != null) {

						// add property name to set for later use
						propertyNames.add(schemaProperty.getPropertyName());

						// append created source from parser
						parser.getPropertySource(schemaNodes, src, entity);

						// register global elements created by parser
						validators.addAll(parser.getGlobalValidators());
						compoundIndexKeys.addAll(parser.getCompoundIndexKeys());
						enums.addAll(parser.getEnumDefinitions());

						// built-in schema properties are configured manually
						if (!schemaProperty.isPartOfBuiltInSchema()) {

							// register property in default view
							addPropertyToView(PropertyView.Custom, propertyName, views);
						}
					}
				}

				final String[] propertyValidatorsArray = schemaProperty.getProperty(SchemaProperty.validators);
				if (propertyValidatorsArray != null) {

					propertyValidators.addAll(Arrays.asList(propertyValidatorsArray));
				}
			}
		}

		return src.toString();
	}

	public static void extractViews(final Map<String, SchemaNode> schemaNodes, final Schema entity, final Map<String, Set<String>> views, final Set<String> relPropertyNames, final ErrorBuffer errorBuffer) throws FrameworkException {

		final PropertyContainer propertyContainer = entity.getPropertyContainer();
		final ConfigurationProvider config        = StructrApp.getConfiguration();

		Class superClass = config.getNodeEntityClass(entity.getSuperclassName());
		if (superClass == null) {

			superClass = config.getRelationshipEntityClass(entity.getSuperclassName());
		}

		if (superClass == null) {
			superClass = AbstractNode.class;
		}


		for (final String rawViewName : getViews(propertyContainer)) {

			if (!rawViewName.startsWith("___") && propertyContainer.hasProperty(rawViewName)) {

				final String value = propertyContainer.getProperty(rawViewName).toString();
				final String[] parts = value.split("[,\\s]+");
				final String viewName = rawViewName.substring(2);

				if (entity instanceof AbstractSchemaNode) {

					final List<String> nonGraphProperties = new LinkedList<>();
					final List<SchemaProperty> properties = new LinkedList<>();
					final AbstractSchemaNode schemaNode   = (AbstractSchemaNode)entity;
					final App app                         = StructrApp.getInstance();

					if (app.nodeQuery(SchemaView.class).and(SchemaView.schemaNode, schemaNode).and(AbstractNode.name, viewName).getFirst() == null) {

						// add parts to view, overrides defaults (because of clear() above)
						for (int i = 0; i < parts.length; i++) {

							String propertyName = parts[i].trim();

							while (propertyName.startsWith("_")) {
								propertyName = propertyName.substring(1);
							}

							// remove "Property" suffix in views where people needed to
							// append this as a workaround to include remote properties
							if (propertyName.endsWith("Property")) {
								propertyName = propertyName.substring(0, propertyName.length() - "Property".length());
							}

							final SchemaProperty propertyNode = app.nodeQuery(SchemaProperty.class).and(SchemaProperty.schemaNode, schemaNode).andName(propertyName).getFirst();
							if (propertyNode != null) {

								properties.add(propertyNode);

							} else {

								nonGraphProperties.add(propertyName);
							}
						}

						app.create(SchemaView.class,
							new NodeAttribute<>(SchemaView.schemaNode, schemaNode),
							new NodeAttribute<>(SchemaView.schemaProperties, properties),
							new NodeAttribute<>(SchemaView.name, viewName),
							new NodeAttribute<>(SchemaView.nonGraphProperties, StringUtils.join(nonGraphProperties, ","))
						);

						schemaNode.removeProperty(new StringProperty(rawViewName));
					}
				}
			}
		}

		final Iterable<SchemaView> schemaViews = entity.getSchemaViews();
		if (schemaViews != null) {

			for (final SchemaView schemaView : schemaViews) {

				final String nonGraphProperties = schemaView.getProperty(SchemaView.nonGraphProperties);
				final String viewName           = schemaView.getName();

				// clear view before filling it again
				Set<String> view = views.get(viewName);
				if (view == null) {

					view = new LinkedHashSet<>();
					views.put(viewName, view);
				}

				final Iterable<SchemaProperty> schemaProperties = schemaView.getProperty(SchemaView.schemaProperties);

				for (final SchemaProperty property : schemaProperties) {

					if (property.getProperty(SchemaProperty.isBuiltinProperty) && !property.getProperty(SchemaProperty.isDynamic)) {

						view.add(SchemaHelper.cleanPropertyName(property.getPropertyName()));

					} else {

						view.add(SchemaHelper.cleanPropertyName(property.getPropertyName() + "Property"));
					}
				}

				// add properties that are not part of the graph
				if (StringUtils.isNotBlank(nonGraphProperties)) {

					for (final String propertyName : nonGraphProperties.split("[, ]+")) {

						if (SchemaHelper.isDynamic(schemaNodes, entity.getClassName(), propertyName)) {

							view.add(SchemaHelper.cleanPropertyName(propertyName + "Property"));

						} else if (relPropertyNames.contains(propertyName)) {

							view.add(SchemaHelper.cleanPropertyName(propertyName) + "Property");

						} else if (basePropertyNames.contains(propertyName)) {

							view.add(SchemaHelper.cleanPropertyName(propertyName));

						} else {

							logger.warn("Unknown property {} in non-graph properties, ignoring.", propertyName);
							SchemaHelper.isDynamic(schemaNodes, entity.getClassName(), propertyName);
						}
					}
				}

				final String order = schemaView.getProperty(SchemaView.sortOrder);
				if (order != null) {

					applySortOrder(view, order);
				}
			}
		}
	}

	public static void extractMethods(final Map<String, SchemaNode> schemaNodes, final AbstractSchemaNode entity, final Map<String, List<ActionEntry>> actions) throws FrameworkException {

		final PropertyContainer propertyContainer = entity.getPropertyContainer();

		for (final String rawActionName : getActions(propertyContainer)) {

			if (propertyContainer.hasProperty(rawActionName)) {

				final String value = propertyContainer.getProperty(rawActionName).toString();

				if (entity instanceof AbstractSchemaNode) {

					final AbstractSchemaNode schemaNode = (AbstractSchemaNode)entity;
					final App app                       = StructrApp.getInstance();
					final String methodName             = rawActionName.substring(3);

					if (app.nodeQuery(SchemaMethod.class).and(SchemaMethod.schemaNode, schemaNode).and(AbstractNode.name, methodName).getFirst() == null) {

						app.create(SchemaMethod.class,
							new NodeAttribute<>(SchemaMethod.schemaNode, schemaNode),
							new NodeAttribute<>(SchemaMethod.name, methodName),
							new NodeAttribute<>(SchemaMethod.source, value)
						);

						schemaNode.removeProperty(new StringProperty(rawActionName));
					}
				}
			}
		}

		final Iterable<SchemaMethod> schemaMethods = entity.getSchemaMethods();
		if (schemaMethods != null) {

			for (final SchemaMethod schemaMethod : schemaMethods) {

				final ActionEntry entry      = schemaMethod.getActionEntry(schemaNodes, entity);
				final String name            = entry.getName();
				List<ActionEntry> actionList = actions.get(name);

				if (actionList == null) {

					actionList = new LinkedList<>();
					actions.put(name, actionList);
				}

				actionList.add(entry);

				Collections.sort(actionList);
			}
		}
	}

	public static void addPropertyToView(final String viewName, final String propertyName, final Map<String, Set<String>> views) {

		Set<String> view = views.get(viewName);
		if (view == null) {

			view = new LinkedHashSet<>();
			views.put(viewName, view);
		}

		view.add(SchemaHelper.cleanPropertyName(propertyName) + "Property");
	}

	public static Iterable<String> getProperties(final PropertyContainer propertyContainer) {

		final List<String> keys = new LinkedList<>();

		for (final String key : propertyContainer.getPropertyKeys()) {

			if (propertyContainer.hasProperty(key) && key.startsWith("_")) {

				keys.add(key);
			}
		}

		return keys;
	}

	public static Iterable<String> getViews(final PropertyContainer propertyContainer) {

		final List<String> keys = new LinkedList<>();

		for (final String key : propertyContainer.getPropertyKeys()) {

			if (propertyContainer.hasProperty(key) && key.startsWith("__")) {

				keys.add(key);
			}
		}

		return keys;
	}

	public static Iterable<String> getActions(final PropertyContainer propertyContainer) {

		final List<String> keys = new LinkedList<>();

		for (final String key : propertyContainer.getPropertyKeys()) {

			if (propertyContainer.hasProperty(key) && key.startsWith("___")) {

				keys.add(key);
			}
		}

		return keys;
	}

	public static void formatView(final StringBuilder src, final String _className, final String viewName, final String view, final Set<String> viewProperties) {

		// output default view
		src.append("\n\tpublic static final View ").append(viewName).append("View = new View(").append(_className).append(".class, \"").append(view).append("\",\n");
		src.append("\t\t");

		for (final Iterator<String> it = viewProperties.iterator(); it.hasNext();) {

			src.append(it.next());

			if (it.hasNext()) {
				src.append(", ");
			}
		}

		src.append("\n\t);\n");

	}

	public static void formatImportStatements(final AbstractSchemaNode schemaNode, final StringBuilder src, final Class baseType, final List<String> importStatements) {

		src.append("import ").append(baseType.getName()).append(";\n");
		src.append("import ").append(ConfigurationProvider.class.getName()).append(";\n");
		src.append("import ").append(GraphObjectComparator.class.getName()).append(";\n");
		src.append("import ").append(PermissionPropagation.class.getName()).append(";\n");
		src.append("import ").append(FrameworkException.class.getName()).append(";\n");
		src.append("import ").append(DatePropertyParser.class.getName()).append(";\n");
		src.append("import ").append(DateArrayPropertyParser.class.getName()).append(";\n");
		src.append("import ").append(ModificationQueue.class.getName()).append(";\n");
		src.append("import ").append(PropertyConverter.class.getName()).append(";\n");
		src.append("import ").append(ValidationHelper.class.getName()).append(";\n");
		src.append("import ").append(SecurityContext.class.getName()).append(";\n");
		src.append("import ").append(LinkedHashSet.class.getName()).append(";\n");
		src.append("import ").append(PropertyView.class.getName()).append(";\n");
		src.append("import ").append(GraphObject.class.getName()).append(";\n");
		src.append("import ").append(ErrorBuffer.class.getName()).append(";\n");
		src.append("import ").append(StringUtils.class.getName()).append(";\n");
		src.append("import ").append(Collections.class.getName()).append(";\n");
		src.append("import ").append(StructrApp.class.getName()).append(";\n");
		src.append("import ").append(LinkedList.class.getName()).append(";\n");
		src.append("import ").append(Collection.class.getName()).append(";\n");
		src.append("import ").append(Iterables.class.getName()).append(";\n");
		src.append("import ").append(Services.class.getName()).append(";\n");
		src.append("import ").append(Actions.class.getName()).append(";\n");
		src.append("import ").append(HashMap.class.getName()).append(";\n");
		src.append("import ").append(Export.class.getName()).append(";\n");
		src.append("import ").append(View.class.getName()).append(";\n");
		src.append("import ").append(List.class.getName()).append(";\n");
		src.append("import ").append(Set.class.getName()).append(";\n");
                src.append("import ").append(Date.class.getName()).append(";\n");

		if (hasRestClasses()) {
			src.append("import org.structr.rest.RestMethodResult;\n");
		}

		src.append("import org.structr.core.property.*;\n");

		if (hasUiClasses()) {
			src.append("import org.structr.web.property.*;\n");
		}

		for (final StructrModule module : StructrApp.getConfiguration().getModules().values()) {
			module.insertImportStatements(schemaNode, src);
		}

		src.append("import org.structr.core.notion.*;\n");
		src.append("import org.structr.core.entity.*;\n");

		// include imports from mixins
		for (final String importLine : importStatements) {
			src.append(importLine);
			src.append("\n");
		}

		src.append("\n");

	}

	public static void collectInterfaces(final AbstractSchemaNode schemaInfo, final Set<String> interfaces) throws FrameworkException {

		final String _implementsInterfaces = schemaInfo.getProperty(SchemaNode.implementsInterfaces);
		if (StringUtils.isNotBlank(_implementsInterfaces)) {

			interfaces.addAll(collectInterfaces(_implementsInterfaces));
		}
	}

	public static Set<String> collectInterfaces(final String src) {

		final Set<String> interfaces = new LinkedHashSet<>();
		final String[] parts         = src.split("[, ]+");

		for (final String part : parts) {

			final String trimmed = part.trim();
			if (StringUtils.isNotBlank(trimmed)) {

				interfaces.add(trimmed);
			}
		}

		return interfaces;
	}

	public static String cleanPropertyName(final String propertyName) {
		return propertyName.replaceAll("[^\\w]+", "");
	}

	public static void formatValidators(final StringBuilder src, final Set<Validator> validators, final Set<String> compoundIndexKeys, final boolean extendsAbstractNode, final List<String> propertyValidators) {

		if (!validators.isEmpty() || !compoundIndexKeys.isEmpty()) {

			src.append("\n\t@Override\n");
			src.append("\tpublic boolean isValid(final ErrorBuffer errorBuffer) {\n\n");
			src.append("\t\tboolean valid = super.isValid(errorBuffer);\n\n");

			for (final Validator validator : validators) {
				src.append("\t\tvalid &= ").append(validator.getSource("this", true)).append(";\n");
			}

			if (!compoundIndexKeys.isEmpty()) {

				src.append("\t\tvalid &= ValidationHelper.areValidCompoundUniqueProperties(this, errorBuffer, ").append(StringUtils.join(compoundIndexKeys, ", ")).append(");\n");
			}

			for (final String propertyValidator : propertyValidators) {

				src.append("\t\tvalid &= new ");
				src.append(propertyValidator);
				src.append("().isValid(this, errorBuffer);\n");
			}

			src.append("\n\t\treturn valid;\n");
			src.append("\t}\n");
		}
	}

	public static void formatMethods(final AbstractSchemaNode schemaNode, final StringBuilder src, final Map<String, List<ActionEntry>> saveActions, final Set<String> implementedInterfaces) {

		/*
		Methods are collected and grouped by name. There can be multiple methods with the same
		name, which must be combined into a single method.
		*/

		for (final Map.Entry<String, List<ActionEntry>> entry : saveActions.entrySet()) {

			final List<ActionEntry> actionList = entry.getValue();
			final String name                  = entry.getKey();

			switch (name) {

				case "onCreation":
					formatCreationCallback(schemaNode, src, name, actionList);
					break;

				case "afterCreation":
					formatAfterCreationCallback(schemaNode, src, name, actionList);
					break;

				case "onModification":
					formatModificationCallback(schemaNode, src, name, actionList);
					break;

				case "afterDeletion":
					formatDeletionCallback(schemaNode, src, name, actionList);
					break;

				default:
					formatCustomMethods(src, actionList);
					break;
			}
		}

	}

	public static void formatCreationCallback(final AbstractSchemaNode schemaNode, final StringBuilder src, final String name, final List<ActionEntry> actionList) {

		src.append("\n\t@Override\n");
		src.append("\tpublic void ");
		src.append(name);
		src.append("(final SecurityContext arg0, final ErrorBuffer arg1) throws FrameworkException {\n\n");
		src.append("\t\tsuper.");
		src.append(name);
		src.append("(arg0, arg1);\n\n");

		for (final ActionEntry action : actionList) {

			src.append("\t\t").append(action.getSource("this", "arg0", false)).append(";\n");
		}

		src.append("\t}\n");

	}

	public static void formatAfterCreationCallback(final AbstractSchemaNode schemaNode, final StringBuilder src, final String name, final List<ActionEntry> actionList) {

		src.append("\n\t@Override\n");
		src.append("\tpublic void ");
		src.append(name);
		src.append("(final SecurityContext arg0) throws FrameworkException {\n\n");
		src.append("\t\tsuper.");
		src.append(name);
		src.append("(arg0);\n\n");

		for (final ActionEntry action : actionList) {

			src.append("\t\t").append(action.getSource("this", "arg0", false)).append(";\n");
		}

		src.append("\t}\n");

	}

	public static void formatModificationCallback(final AbstractSchemaNode schemaNode, final StringBuilder src, final String name, final List<ActionEntry> actionList) {

		src.append("\n\t@Override\n");
		src.append("\tpublic void ");
		src.append(name);
		src.append("(final SecurityContext arg0, final ErrorBuffer arg1, final ModificationQueue arg2) throws FrameworkException {\n\n");
		src.append("\t\tsuper.");
		src.append(name);
		src.append("(arg0, arg1, arg2);\n\n");

		for (final ActionEntry action : actionList) {

			src.append("\t\t").append(action.getSource("this", "arg0", true)).append(";\n");
		}

		src.append("\t}\n");

	}

	public static void formatDeletionCallback(final AbstractSchemaNode schemaNode, final StringBuilder src, final String name, final List<ActionEntry> actionList) {

		src.append("\n\t@Override\n");
		src.append("\tpublic void ");
		src.append(name);
		src.append("(final SecurityContext arg0, final PropertyMap arg1) {\n\n");
		src.append("\t\tsuper.");
		src.append(name);
		src.append("(arg0, arg1);\n\n");

		src.append("\t\ttry {\n\n");

		for (final ActionEntry action : actionList) {

			src.append("\t\t\t").append(action.getSource("this", "arg0", false)).append(";\n");
		}

		src.append("\t\t} catch (FrameworkException fex) {\n");
		src.append("\t\t\tfex.printStackTrace();\n");
		src.append("\t\t}\n");
		src.append("\t}\n");

	}

	public static void formatCustomMethods(final StringBuilder src, final List<ActionEntry> actionList) {

		for (final ActionEntry action : actionList) {

			if (Actions.Type.Custom.equals(action.getType())) {

				formatActiveAction(src, action);

			} else {

				final String source                  = action.getSource("this", true, false);
				final String returnType              = action.getReturnType();
				final Map<String, String> parameters = action.getParameters();

				if (returnType != null && parameters != null) {

					src.append("\n");

					if (action.overrides()) {
						src.append("\t@Override\n");
					}

					if (action.doExport()) {
						src.append("\t@Export\n");
					}

					src.append("\tpublic ");
					src.append(returnType);
					src.append(" ");
					src.append(action.getName());
					src.append("(");

					// output parameters
					for (final Iterator<Entry<String, String>> it = parameters.entrySet().iterator(); it.hasNext();) {

						final Entry<String, String> entry = it.next();

						src.append("final ");
						src.append(entry.getValue());
						src.append(" ");
						src.append(entry.getKey());

						if (it.hasNext()) {
							src.append(", ");
						}
					}

					src.append(")");

					final List<String> exceptions = action.getExceptions();
					if (!exceptions.isEmpty()) {

						src.append(" throws ");
						src.append(StringUtils.join(exceptions, ", "));
					}

					src.append(" {\n");

				} else {

					src.append("\n\t@Export\n");
					src.append("\tpublic java.lang.Object ");
					src.append(action.getName());
					src.append("(final java.util.Map<java.lang.String, java.lang.Object> parameters) throws FrameworkException {\n\n");
				}

				if (action.callSuper()) {

					src.append("\n\t\t// call super\n");
					src.append("\t\tsuper.");
					src.append(action.getName());
					src.append("(");
					src.append(StringUtils.join(parameters.keySet(), ", "));
					src.append(");\n\n");
				}

				if (StringUtils.isNotBlank(source)) {

					src.append("\t\t");
					src.append(source);

					final String trimmed = source.trim();
					if (!trimmed.endsWith(";") &&  !trimmed.endsWith("}")) {

						src.append(";");
					}

					src.append("\n");
				}

				if (!"void".equals(returnType) && (StringUtils.isBlank(source) || Actions.Type.Custom.equals(action.getType()))) {

					src.append("\t\treturn null;\n");
				}

				src.append("\t}\n");
			}
		}
	}

	public static void formatActiveAction(final StringBuilder src, final ActionEntry action) {

		src.append("\n\t@Export\n");
		src.append("\tpublic java.lang.Object ");
		src.append(action.getName());
		src.append("(final java.util.Map<java.lang.String, java.lang.Object> parameters) throws FrameworkException {\n\n");

		src.append("\t\treturn ");
		src.append(action.getSource("this", true, false));
		src.append(";\n\n");
		src.append("\t}\n");
	}

	private static Map<String, Object> getPropertiesForView(final SecurityContext securityContext, final Class type, final String propertyView) throws FrameworkException {

		final Set<PropertyKey> properties = new LinkedHashSet<>(StructrApp.getConfiguration().getPropertySet(type, propertyView));
		final Map<String, Object> propertyConverterMap = new LinkedHashMap<>();

		for (PropertyKey property : properties) {

			propertyConverterMap.put(property.jsonName(), getPropertyInfo(securityContext, property));
		}

		return propertyConverterMap;
	}

	// ----- public static methods -----
	public static List<GraphObjectMap> getSchemaTypeInfo(final SecurityContext securityContext, final String rawType, final Class type, final String propertyView) throws FrameworkException {

		List<GraphObjectMap> resultList = new LinkedList<>();

		// create & add schema information

		if (type != null) {

			if (propertyView != null) {

				for (final Map.Entry<String, Object> entry : getPropertiesForView(securityContext, type, propertyView).entrySet()) {

					final GraphObjectMap property = new GraphObjectMap();

					for (final Map.Entry<String, Object> prop : ((Map<String, Object>) entry.getValue()).entrySet()) {

						property.setProperty(new GenericProperty(prop.getKey()), prop.getValue());
					}

					resultList.add(property);
				}

			} else {

				final GraphObjectMap schema = new GraphObjectMap();

				resultList.add(schema);

				String url = "/".concat(rawType);

				schema.setProperty(new StringProperty("url"), url);
				schema.setProperty(new StringProperty("type"), type.getSimpleName());
				schema.setProperty(new StringProperty("className"), type.getName());
				schema.setProperty(new StringProperty("extendsClass"), type.getSuperclass().getName());
				schema.setProperty(new BooleanProperty("isRel"), AbstractRelationship.class.isAssignableFrom(type));
				schema.setProperty(new LongProperty("flags"), SecurityContext.getResourceFlags(rawType));

				Set<String> propertyViews = new LinkedHashSet<>(StructrApp.getConfiguration().getPropertyViewsForType(type));

				// list property sets for all views
				Map<String, Map<String, Object>> views = new TreeMap();
				schema.setProperty(new GenericProperty("views"), views);

				for (final String view : propertyViews) {

					if (!View.INTERNAL_GRAPH_VIEW.equals(view)) {

						views.put(view, getPropertiesForView(securityContext, type, view));
					}
				}
			}
		}

		return resultList;
	}


	public static Map<String, Object> getPropertyInfo(final SecurityContext securityContext, final PropertyKey property) {

		final Map<String, Object> map = new LinkedHashMap();

		map.put("dbName", property.dbName());
		map.put("jsonName", property.jsonName());
		map.put("className", property.getClass().getName());

		final Class declaringClass = property.getDeclaringClass();
		if (declaringClass != null) {

			map.put("declaringClass", declaringClass.getSimpleName());
		}

		map.put("defaultValue", property.defaultValue());

		if (property instanceof StringProperty) {
			map.put("contentType", ((StringProperty) property).contentType());
		}

		map.put("format", property.format());
		map.put("readOnly", property.isReadOnly());
		map.put("system", property.isSystemInternal());
		map.put("indexed", property.isIndexed());
		map.put("indexedWhenEmpty", property.isIndexedWhenEmpty());
		map.put("compound", property.isCompound());
		map.put("unique", property.isUnique());
		map.put("notNull", property.isNotNull());
		map.put("dynamic", property.isDynamic());
		map.put("hint", property.hint());
		map.put("category", property.category());
		map.put("builtin", property.isPartOfBuiltInSchema());

		final Class<? extends GraphObject> relatedType = property.relatedType();
		if (relatedType != null) {

			map.put("relatedType", relatedType.getName());
			map.put("type", relatedType.getSimpleName());
			map.put("uiType", relatedType.getSimpleName() + (property.isCollection() ? "[]" : ""));

		} else {

			map.put("type", property.typeName());
			map.put("uiType", property.typeName() + (property.isCollection() ? "[]" : ""));
		}

		map.put("isCollection", property.isCollection());

		final PropertyConverter databaseConverter = property.databaseConverter(securityContext, null);
		final PropertyConverter inputConverter = property.inputConverter(securityContext);

		if (databaseConverter != null) {

			map.put("databaseConverter", databaseConverter.getClass().getName());
		}

		if (inputConverter != null) {

			map.put("inputConverter", inputConverter.getClass().getName());
		}

		//if (declaringClass != null && ("org.structr.dynamic".equals(declaringClass.getPackage().getName()))) {
		if (declaringClass != null && property instanceof RelationProperty) {

			Relation relation = ((RelationProperty) property).getRelation();
			if (relation != null) {

				map.put("relationshipType", relation.name());
			}

		}

		return map;
	}

	public static void applySortOrder(final Set<String> view, final String orderString) {

		final List<String> list = new LinkedList<>();

		if ("alphabetic".equals(orderString)) {

			// copy elements to list for sorting
			list.addAll(view);

			// sort alphabetically
			Collections.sort(list);

		} else {

			// sort according to comma-separated list of property names
			final String[] order = orderString.split("[, ]+");
			for (final String property : order) {

				if (StringUtils.isNotEmpty(property.trim())) {

					// SchemaProperty instances are suffixed with "Property"
					final String suffixedProperty = property + "Property";

					if (view.contains(property)) {

						// move property from view to list
						list.add(property);
						view.remove(property);

					} else if (view.contains(suffixedProperty)) {

						// move property from view to list
						list.add(suffixedProperty);
						view.remove(suffixedProperty);
					}

				}
			}

			// append the rest
			list.addAll(view);
		}

		// clear source view, add sorted list contents
		view.clear();
		view.addAll(list);
	}

	public static boolean isDynamic(final Map<String, SchemaNode> schemaNodes, final String typeName, final String propertyName) throws FrameworkException {

		final ConfigurationProvider config = StructrApp.getConfiguration();
		final Class type                   = config.getNodeEntityClass(typeName);

		if (type != null) {

			final PropertyKey property = StructrApp.getConfiguration().getPropertyKeyForJSONName(type, propertyName, false);
			if (property != null && property.isDynamic()) {

				return true;
			}

		} else if (hasSchemaProperty(schemaNodes, typeName, propertyName)) {

			return true;
		}

		return false;
	}

	public static boolean hasPeerToPeerService() {

		try {

			Class.forName("org.structr.net.SharedNodeInterface");

			// success
			return true;

		} catch (Throwable t) {
		}

		return false;
	}

	public static Class classForName(final String fqcn) {

		try {

			return Class.forName(cleanTypeName(fqcn));

		} catch (Throwable t) {}

		return null;
	}

	public static GraphQLOutputType getGraphQLOutputTypeForProperty(final SchemaProperty property) {

		final Type propertyType            = property.getPropertyType();
		final GraphQLOutputType outputType = graphQLTypeMap.get(propertyType);
		if (outputType != null) {

			return outputType;
		}

		switch (propertyType) {

			case Function:
			case Custom:
			case Cypher:
				final String typeHint = property.getTypeHint();
				if (typeHint != null) {

					final String lowerCaseTypeHint = typeHint.toLowerCase();
					switch (lowerCaseTypeHint) {

						case "boolean": return graphQLTypeMap.get(Type.Boolean);
						case "string":  return graphQLTypeMap.get(Type.String);
						case "int":     return graphQLTypeMap.get(Type.Integer);
						case "long":    return graphQLTypeMap.get(Type.Long);
						case "double":  return graphQLTypeMap.get(Type.Double);
						case "date":    return graphQLTypeMap.get(Type.Date);
					}

					// object array type?
					if (typeHint.endsWith("[]")) {

						// list type
						return new GraphQLListType(typeRef(StringUtils.substringBefore(typeHint, "[]")));

					} else {

						// object type
						return typeRef(typeHint);
					}

				} else {

					logger.warn("Unable to register property {} with GraphQL, no type hint present. Please set typeHint property of {} to use it with GraphQL", property.getFullName(), property.getUuid());
				}
				break;
		}


		return null;
	}

	public static GraphQLInputType getGraphQLInputTypeForProperty(final SchemaProperty property) {

		final Type propertyType = property.getPropertyType();
		switch (propertyType) {

			case Function:
			case Custom:
			case Cypher:
				final String typeHint = property.getTypeHint();
				if (typeHint != null) {

					final String lowerCaseTypeHint = typeHint.toLowerCase();
					switch (lowerCaseTypeHint) {

						case "boolean": return graphQLTypeMap.get(Type.Boolean);
						case "string":  return graphQLTypeMap.get(Type.String);
						case "int":     return graphQLTypeMap.get(Type.Integer);
						case "long":    return graphQLTypeMap.get(Type.Long);
						case "double":  return graphQLTypeMap.get(Type.Double);
						case "date":    return graphQLTypeMap.get(Type.Date);
					}
				}
				break;
		}

		final GraphQLScalarType type = graphQLTypeMap.get(propertyType);
		if (type != null) {

			return type;
		}

		// default / fallback
		return Scalars.GraphQLString;
	}

	public static List<GraphQLArgument> getGraphQLQueryArgumentsForType(final Map<String, SchemaNode> schemaNodes, final Map<String, GraphQLInputObjectType> selectionTypes, final Set<String> queryTypeNames, final String type) throws FrameworkException {

		final List<GraphQLArgument> arguments = new LinkedList<>();
		final SchemaNode schemaNode           = schemaNodes.get(type);

		if (schemaNode != null) {

			// register parent type arguments as well!
			final SchemaNode parentSchemaNode = schemaNode.getParentSchemaNode(schemaNodes);
			if (parentSchemaNode != null && !parentSchemaNode.equals(schemaNode)) {

				arguments.addAll(getGraphQLQueryArgumentsForType(schemaNodes, selectionTypes, queryTypeNames, parentSchemaNode.getName()));
			}

			// outgoing relationships
			for (final SchemaRelationshipNode outNode : schemaNode.getProperty(SchemaNode.relatedTo)) {

				final SchemaNode targetNode = outNode.getTargetNode();
				final String targetTypeName = targetNode.getClassName();
				final String propertyName   = outNode.getPropertyName(targetTypeName, new LinkedHashSet<>(), true);
				final String queryTypeName  = type + propertyName + targetTypeName + "InInput";

				if (!queryTypeNames.contains(queryTypeName)) {

					arguments.add(GraphQLArgument.newArgument().name(propertyName).type(GraphQLInputObjectType.newInputObject()
						.name(queryTypeName)
						.fields(getGraphQLInputFieldsForType(selectionTypes, targetNode))
						.build()
					).build());

					queryTypeNames.add(queryTypeName);
				}
			}

			// incoming relationships
			for (final SchemaRelationshipNode inNode : schemaNode.getProperty(SchemaNode.relatedFrom)) {

				final SchemaNode sourceNode = inNode.getSourceNode();
				final String sourceTypeName = sourceNode.getClassName();
				final String propertyName   = inNode.getPropertyName(sourceTypeName, new LinkedHashSet<>(), false);
				final String queryTypeName  = type + propertyName + sourceTypeName + "OutInput";

				if (!queryTypeNames.contains(queryTypeName)) {

					arguments.add(GraphQLArgument.newArgument().name(propertyName).type(GraphQLInputObjectType.newInputObject()
						.name(queryTypeName)
						.fields(getGraphQLInputFieldsForType(selectionTypes, sourceNode))
						.build()
					).build());

					queryTypeNames.add(queryTypeName);
				}
			}

			// properties
			for (final SchemaProperty property : schemaNode.getSchemaProperties()) {

				if (property.isIndexed() || property.isCompound()) {

					final String name          = property.getName();
					final String selectionName = type + name + "Selection";

					GraphQLInputObjectType selectionType = selectionTypes.get(selectionName);
					if (selectionType == null) {

						selectionType = GraphQLInputObjectType.newInputObject()
							.name(selectionName)
							.field(GraphQLInputObjectField.newInputObjectField().name("_contains").type(Scalars.GraphQLString).build())
							.field(GraphQLInputObjectField.newInputObjectField().name("_equals").type(getGraphQLInputTypeForProperty(property)).build())
							.field(GraphQLInputObjectField.newInputObjectField().name("_conj").type(Scalars.GraphQLString).build())
							.build();

						selectionTypes.put(selectionName, selectionType);
					}

					arguments.add(GraphQLArgument.newArgument()
						.name(name)
						.type(selectionType)
						.build()
					);
				}
			}

			final String ownerTypeName = type + "ownerInput";

			if (!queryTypeNames.contains(ownerTypeName)) {

				// manual registration for built-in relationships that are not dynamic
				arguments.add(GraphQLArgument.newArgument().name("owner").type(GraphQLInputObjectType.newInputObject()
					.name(ownerTypeName)
					.fields(getGraphQLInputFieldsForType(selectionTypes, schemaNodes.get("Principal")))
					.build()
				).build());

				queryTypeNames.add(ownerTypeName);
			}
		}

		return arguments;
	}

	public static List<GraphQLInputObjectField> getGraphQLInputFieldsForType(final Map<String, GraphQLInputObjectType> selectionTypes, final SchemaNode targetNode) {

		final Map<String, GraphQLInputObjectField> fields = new LinkedHashMap<>();

		for (final SchemaProperty property : targetNode.getSchemaProperties()) {

			if (property.isIndexed() || property.isCompound()) {

				final String name          = property.getName();
				final String selectionName = name + "Selection";

				GraphQLInputObjectType selectionType = selectionTypes.get(selectionName);
				if (selectionType == null) {

					selectionType = GraphQLInputObjectType.newInputObject()
						.name(selectionName)
						.field(GraphQLInputObjectField.newInputObjectField().name("_contains").type(Scalars.GraphQLString).build())
						.field(GraphQLInputObjectField.newInputObjectField().name("_equals").type(getGraphQLInputTypeForProperty(property)).build())
						.field(GraphQLInputObjectField.newInputObjectField().name("_conj").type(Scalars.GraphQLString).build())
						.build();

					selectionTypes.put(selectionName, selectionType);
				}

				fields.put(name, GraphQLInputObjectField.newInputObjectField().name(name).type(selectionType).build());
			}
		}

		if (!fields.containsKey("name")) {

			GraphQLInputObjectType selectionType = selectionTypes.get("nameSelection");
			if (selectionType == null) {

				selectionType = GraphQLInputObjectType.newInputObject()
					.name("nameSelection")
					.field(GraphQLInputObjectField.newInputObjectField().name("_contains").type(Scalars.GraphQLString).build())
					.field(GraphQLInputObjectField.newInputObjectField().name("_equals").type(Scalars.GraphQLString).build())
					.field(GraphQLInputObjectField.newInputObjectField().name("_conj").type(Scalars.GraphQLString).build())
					.build();

				selectionTypes.put("nameSelection", selectionType);
			}

			fields.put("name", GraphQLInputObjectField.newInputObjectField().name("name").type(selectionType).build());
		}

		return new LinkedList<>(fields.values());
	}

	// ----- private methods -----
	private static PropertySourceGenerator getSourceGenerator(final ErrorBuffer errorBuffer, final String className, final PropertyDefinition propertyDefinition) throws FrameworkException {

		final String propertyName                                  = propertyDefinition.getPropertyName();
		final Type propertyType                                    = propertyDefinition.getPropertyType();
		final Class<? extends PropertySourceGenerator> parserClass = parserMap.get(propertyType);

		try {

			return parserClass.getConstructor(ErrorBuffer.class, String.class, PropertyDefinition.class).newInstance(errorBuffer, className, propertyDefinition);

		} catch (Throwable t) {
			logger.warn("", t);
		}

		errorBuffer.add(new InvalidPropertySchemaToken(SchemaProperty.class.getSimpleName(), propertyName, propertyName, "invalid_property_definition", "Unknow value type " + source + ", options are " + Arrays.asList(Type.values()) + "."));
		throw new FrameworkException(422, "Invalid property definition for property " + propertyDefinition.getPropertyName(), errorBuffer);
	}

	private static boolean hasRestClasses() {

		try {

			Class.forName("org.structr.rest.RestMethodResult");

			// success
			return true;

		} catch (Throwable t) {
		}

		return false;
	}

	private static boolean hasUiClasses() {

		try {

			Class.forName("org.structr.web.property.ThumbnailProperty");

			// success
			return true;

		} catch (Throwable t) {
		}

		return false;
	}

	private static class ReverseTypeComparator implements Comparator<Type> {

		@Override
		public int compare(final Type o1, final Type o2) {
			return o2.name().compareTo(o1.name());
		}
	}

	private static String schemaResourceSignature(final String signature) {
		return "_schema/" + signature;
	}

	private static String uiViewResourceSignature(final String signature) {
		return signature + "/_Ui";
	}

	private static boolean hasRelationshipNode(final SchemaNode schemaNode, final String propertyName) throws FrameworkException {

		if (StructrApp.getInstance().nodeQuery(SchemaRelationshipNode.class)
			.and(SchemaRelationshipNode.sourceNode, schemaNode)
			.and()
				.or(SchemaRelationshipNode.targetJsonName, propertyName)
				.or(SchemaRelationshipNode.previousTargetJsonName, propertyName)

			.getFirst() != null) {

			return true;
		}

		if (StructrApp.getInstance().nodeQuery(SchemaRelationshipNode.class)
			.and(SchemaRelationshipNode.targetNode, schemaNode)
			.and()
				.or(SchemaRelationshipNode.sourceJsonName, propertyName)
				.or(SchemaRelationshipNode.previousSourceJsonName, propertyName)

			.getFirst() != null) {

			return true;
		}

		return false;
	}

	private static boolean hasSchemaProperty(final Map<String, SchemaNode> schemaNodes, final String typeName, final String propertyName) throws FrameworkException {

		final Set<String> visited = new LinkedHashSet<>();
		final Queue<String> types = new LinkedList<>();
		final App app             = StructrApp.getInstance();

		types.add(typeName);

		while (!types.isEmpty()) {

			final String type = types.poll();

			if (!visited.contains(type)) {

				visited.add(type);

				final SchemaNode schemaNode = schemaNodes.get(type);
				if (schemaNode != null) {

					final SchemaProperty schemaProperty = app.nodeQuery(SchemaProperty.class).and(SchemaProperty.schemaNode, schemaNode).andName(propertyName).getFirst();
					if (schemaProperty != null || hasRelationshipNode(schemaNode, propertyName)) {

						return true;

					} else {

						// add superclass AND interfaces
						String localTypeName = schemaNode.getProperty(SchemaNode.extendsClass);
						if (localTypeName != null) {

							localTypeName = cleanTypeName(localTypeName);
							localTypeName = localTypeName.substring(localTypeName.lastIndexOf(".") + 1);

							types.add(localTypeName);
						}

						final String interfaces = schemaNode.getProperty(SchemaNode.implementsInterfaces);
						if (StringUtils.isNotBlank(interfaces)) {

							for (final String iface : collectInterfaces(interfaces)) {

								String cleaned = cleanTypeName(iface);
								cleaned        = cleaned.substring(cleaned.lastIndexOf(".") + 1);

								types.add(cleaned);
							}
						}
					}

				} else {

					break;
				}
			}
		}

		return false;
	}

	private static boolean hasType(final String fqcn) {
		return SchemaHelper.classForName(fqcn) != null;
	}

	private static String cleanTypeName(final String src) {
		return StringUtils.substringBefore(src, "<");
	}

	private static Set<String> checkLicense(final LicenseManager licenseManager, final String superClass, final Set<String> implementedInterfaces) {

		final Set<String> types = new LinkedHashSet<>();
		final String cleaned    = cleanTypeName(superClass);

		if (!checkLicense(licenseManager, cleaned)) {
			types.add(StringUtils.substringAfterLast(cleaned, "."));
		}

		for (final String iface : implementedInterfaces) {

			final String cleanedInterfaceName = cleanTypeName(iface);

			if (!checkLicense(licenseManager, cleanedInterfaceName)) {
				types.add(StringUtils.substringAfterLast(cleanedInterfaceName, "."));
			}
		}

		return types;
	}

	private static boolean checkLicense(final LicenseManager licenseManager, final String fqcn) {

		if (licenseManager == null) {
			return true;
		}

		if (fqcn == null) {
			return true;
		}

		if (AbstractNode.class.getSimpleName().equals(fqcn)) {
			return true;
		}

		if (fqcn.startsWith("org.structr.dynamic.")) {
			return true;
		}

		if (licenseManager.isClassLicensed(fqcn)) {
			return true;
		}

		return false;
	}

	// ----- nested classes -----
	private static class PropertyTypeComparator implements Comparator<SchemaProperty> {

		@Override
		public int compare(final SchemaProperty o1, final SchemaProperty o2) {

			final Type type1     = o1.getPropertyType();
			final Type type2     = o2.getPropertyType();
			final Integer index1 = sortIndexMap.get(type1);
			final Integer index2 = sortIndexMap.get(type2);

			if (index1 != null && index2 != null) {
				return index1.compareTo(index2);
			}

			return 0;
		}

	}
}
