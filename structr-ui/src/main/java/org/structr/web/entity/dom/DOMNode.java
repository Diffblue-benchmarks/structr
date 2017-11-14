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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.structr.api.Predicate;
import org.structr.api.util.Iterables;
import org.structr.common.CaseHelper;
import org.structr.common.Filter;
import org.structr.common.Permission;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.common.error.SemanticErrorToken;
import org.structr.common.error.UnlicensedException;
import org.structr.core.GraphObject;
import org.structr.core.GraphObjectMap;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.LinkedTreeNode;
import org.structr.core.entity.Principal;
import org.structr.core.entity.Security;
import org.structr.core.graph.ModificationQueue;
import org.structr.core.graph.NodeInterface;
import org.structr.core.notion.PropertyNotion;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.CollectionIdProperty;
import org.structr.core.property.ConstantBooleanProperty;
import org.structr.core.property.EndNode;
import org.structr.core.property.EndNodes;
import org.structr.core.property.EntityIdProperty;
import org.structr.core.property.GenericProperty;
import org.structr.core.property.IntProperty;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.StartNode;
import org.structr.core.property.StringProperty;
import org.structr.core.script.Scripting;
import org.structr.web.common.AsyncBuffer;
import org.structr.web.common.GraphDataSource;
import org.structr.web.common.RenderContext;
import org.structr.web.common.RenderContext.EditMode;
import org.structr.web.common.StringRenderBuffer;
import org.structr.web.datasource.CypherGraphDataSource;
import org.structr.web.datasource.FunctionDataSource;
import org.structr.web.datasource.IdRequestParameterGraphDataSource;
import org.structr.web.datasource.NodeGraphDataSource;
import org.structr.web.datasource.RestDataSource;
import org.structr.web.datasource.XPathGraphDataSource;
import org.structr.web.entity.LinkSource;
import org.structr.web.entity.Linkable;
import org.structr.web.entity.Renderable;
import org.structr.web.entity.dom.relationship.DOMChildren;
import org.structr.web.entity.dom.relationship.DOMSiblings;
import org.structr.web.entity.relation.PageLink;
import org.structr.web.entity.relation.RenderNode;
import org.structr.web.entity.relation.Sync;
import org.structr.web.property.CustomHtmlAttributeProperty;
import org.structr.websocket.command.CreateComponentCommand;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.UserDataHandler;

/**
 * Combines AbstractNode and org.w3c.dom.Node.
 */
public interface DOMNode extends LinkedTreeNode<DOMChildren, DOMSiblings, DOMNode>, Node, Renderable, DOMAdoptable, DOMImportable {

	// ----- error messages for DOMExceptions -----
	public static final String NO_MODIFICATION_ALLOWED_MESSAGE         = "Permission denied.";
	public static final String INVALID_ACCESS_ERR_MESSAGE              = "Permission denied.";
	public static final String INDEX_SIZE_ERR_MESSAGE                  = "Index out of range.";
	public static final String CANNOT_SPLIT_TEXT_WITHOUT_PARENT        = "Cannot split text element without parent and/or owner document.";
	public static final String WRONG_DOCUMENT_ERR_MESSAGE              = "Node does not belong to this document.";
	public static final String HIERARCHY_REQUEST_ERR_MESSAGE_SAME_NODE = "A node cannot accept itself as a child.";
	public static final String HIERARCHY_REQUEST_ERR_MESSAGE_ANCESTOR  = "A node cannot accept its own ancestor as child.";
	public static final String HIERARCHY_REQUEST_ERR_MESSAGE_DOCUMENT  = "A document may only have one html element.";
	public static final String HIERARCHY_REQUEST_ERR_MESSAGE_ELEMENT   = "A document may only accept an html element as its document element.";
	public static final String NOT_SUPPORTED_ERR_MESSAGE               = "Node type not supported.";
	public static final String NOT_FOUND_ERR_MESSAGE                   = "Node is not a child.";
	public static final String NOT_SUPPORTED_ERR_MESSAGE_IMPORT_DOC    = "Document nodes cannot be imported into another document.";
	public static final String NOT_SUPPORTED_ERR_MESSAGE_ADOPT_DOC     = "Document nodes cannot be adopted by another document.";
	public static final String NOT_SUPPORTED_ERR_MESSAGE_RENAME        = "Renaming of nodes is not supported by this implementation.";

	public static final Property<String> dataKey                      = new StringProperty("dataKey").indexed().category(QUERY_CATEGORY);
	public static final Property<String> cypherQuery                  = new StringProperty("cypherQuery").category(QUERY_CATEGORY);
	public static final Property<String> xpathQuery                   = new StringProperty("xpathQuery").category(QUERY_CATEGORY);
	public static final Property<String> restQuery                    = new StringProperty("restQuery").category(QUERY_CATEGORY);
	public static final Property<String> functionQuery                = new StringProperty("functionQuery").category(QUERY_CATEGORY);
	public static final Property<Boolean> renderDetails               = new BooleanProperty("renderDetails").category(QUERY_CATEGORY);

	public static final Property<List<DOMNode>> syncedNodes           = new EndNodes("syncedNodes", Sync.class, new PropertyNotion(id)).category(PAGE_CATEGORY);
	public static final Property<DOMNode> sharedComponent             = new StartNode("sharedComponent", Sync.class, new PropertyNotion(id)).category(PAGE_CATEGORY);
	public static final Property<String> sharedComponentConfiguration = new StringProperty("sharedComponentConfiguration").format("multi-line").hint("The contents of this field will be evaluated before rendering this component. This is usually used to customize shared components to make them more flexible.<br><br>This is an 'auto-script' environment, meaning that the text is automatically surrounded with ${}").category(PAGE_CATEGORY);

	public static final Property<Boolean> hideOnIndex                 = new BooleanProperty("hideOnIndex").indexed().category(QUERY_CATEGORY);
	public static final Property<Boolean> hideOnDetail                = new BooleanProperty("hideOnDetail").indexed().category(QUERY_CATEGORY);
	public static final Property<String> showForLocales               = new StringProperty("showForLocales").indexed().category(VISIBILITY_CATEGORY);
	public static final Property<String> hideForLocales               = new StringProperty("hideForLocales").indexed().category(VISIBILITY_CATEGORY);
	public static final Property<String> showConditions               = new StringProperty("showConditions").indexed().category(VISIBILITY_CATEGORY).hint("Conditions which have to be met in order for the element to be shown.<br><br>This is an 'auto-script' environment, meaning that the text is automatically surrounded with ${}");
	public static final Property<String> hideConditions               = new StringProperty("hideConditions").indexed().category(VISIBILITY_CATEGORY).hint("Conditions which have to be met in order for the element to be hidden.<br><br>This is an 'auto-script' environment, meaning that the text is automatically surrounded with ${}");

	public static final Property<DOMNode> parent                      = new StartNode<>("parent", DOMChildren.class).category(PAGE_CATEGORY);
	public static final Property<String> parentId                     = new EntityIdProperty("parentId", parent).category(PAGE_CATEGORY);
	public static final Property<List<DOMNode>> children              = new EndNodes<>("children", DOMChildren.class).category(PAGE_CATEGORY);
	public static final Property<List<String>> childrenIds            = new CollectionIdProperty("childrenIds", children).category(PAGE_CATEGORY);
	public static final Property<DOMNode> previousSibling             = new StartNode<>("previousSibling", DOMSiblings.class).category(PAGE_CATEGORY);
	public static final Property<DOMNode> nextSibling                 = new EndNode<>("nextSibling", DOMSiblings.class).category(PAGE_CATEGORY);
	public static final Property<String> nextSiblingId                = new EntityIdProperty("nextSiblingId", nextSibling).category(PAGE_CATEGORY);

	public static final Property<Page> ownerDocument                  = new EndNode<>("ownerDocument", PageLink.class).category(PAGE_CATEGORY);
	public static final Property<String> pageId                       = new EntityIdProperty("pageId", ownerDocument).category(PAGE_CATEGORY);
	public static final Property<Boolean> isDOMNode                   = new ConstantBooleanProperty("isDOMNode", true).category(PAGE_CATEGORY);

	public static final Property<String> dataStructrIdProperty        = new StringProperty("data-structr-id").hint("Set to ${current.id} most of the time").category(PAGE_CATEGORY);
	public static final Property<String> dataHashProperty             = new StringProperty("data-structr-hash").category(PAGE_CATEGORY);

	public static final Property<Integer> domSortPosition             = new IntProperty("domSortPosition").category(PAGE_CATEGORY);

	public static final Property[] rawProps = new Property[] {
		dataKey, restQuery, cypherQuery, xpathQuery, functionQuery, hideOnIndex, hideOnDetail, showForLocales, hideForLocales, showConditions, hideConditions
	};

	public static final Set<PropertyKey> cloneBlacklist = new LinkedHashSet<>(Arrays.asList(new Property[] {
		GraphObject.id, GraphObject.type, DOMNode.ownerDocument, DOMNode.pageId, DOMNode.parent, DOMNode.parentId, DOMElement.syncedNodes,
		DOMNode.children, DOMNode.childrenIds, LinkSource.linkable, LinkSource.linkableId, Page.path
	}));

	public static final List<GraphDataSource> LIST_SOURCES = new LinkedList<>(Arrays.asList(new GraphDataSource[] {

		// register data sources
		new IdRequestParameterGraphDataSource("nodeId"),
		new RestDataSource(),
		new NodeGraphDataSource(),
		new FunctionDataSource(),
		new CypherGraphDataSource(),
		new XPathGraphDataSource()

	}));


	boolean isSynced();
	boolean contentEquals(final DOMNode otherNode);
	String getContextName();
	void updateFromNode(final DOMNode otherNode) throws FrameworkException;

	// ---- static methods -----
	public static String indent(final int depth, final RenderContext renderContext) {

		if (!renderContext.shouldIndentHtml()) {
			return "";
		}

		StringBuilder indent = new StringBuilder("\n");

		for (int d = 0; d < depth; d++) {

			indent.append("	");

		}

		return indent.toString();
	}

	public static String escapeForHtml(final String raw) {
		return StringUtils.replaceEach(raw, new String[]{"&", "<", ">"}, new String[]{"&amp;", "&lt;", "&gt;"});

	}

	public static String escapeForHtmlAttributes(final String raw) {
		//return StringUtils.replaceEach(raw, new String[]{"&", "<", ">", "\"", "'"}, new String[]{"&amp;", "&lt;", "&gt;", "&quot;", "&#39;"});
		return StringUtils.replaceEach(raw, new String[]{"&", "<", ">", "\""}, new String[]{"&amp;", "&lt;", "&gt;", "&quot;"});
	}

	public static String unescapeForHtmlAttributes(final String raw) {
		//return StringUtils.replaceEach(raw, new String[]{"&", "<", ">", "\"", "'"}, new String[]{"&amp;", "&lt;", "&gt;", "&quot;", "&#39;"});
		return StringUtils.replaceEach(raw, new String[]{"&amp;", "&lt;", "&gt;", "&quot;"}, new String[]{"&", "<", ">", "\""});
	}

	public static GraphObjectMap extractHeaders(final Header[] headers) {

		final GraphObjectMap map = new GraphObjectMap();

		for (final Header header : headers) {

			map.put(new StringProperty(header.getName()), header.getValue());
		}

		return map;
	}

	public static Set<DOMNode> getAllChildNodes(final DOMNode node) {

		Set<DOMNode> allChildNodes = new HashSet();

		getAllChildNodes(node, allChildNodes);

		return allChildNodes;
	}

	public static void getAllChildNodes(final DOMNode node, final Set<DOMNode> allChildNodes) {

		Node n = node.getFirstChild();

		while (n != null) {

			if (n instanceof DOMNode) {

				DOMNode domNode = (DOMNode)n;

				if (!allChildNodes.contains(domNode)) {

					allChildNodes.add(domNode);
					allChildNodes.addAll(getAllChildNodes(domNode));

				} else {

					// break loop!
					break;
				}
			}

			n = n.getNextSibling();
		}
	}

	/**
	 * Recursively clone given node, all its direct children and connect the cloned child nodes to the clone parent node.
	 *
	 * @param securityContext
	 * @param nodeToClone
	 * @return
	 */
	public static DOMNode cloneAndAppendChildren(final SecurityContext securityContext, final DOMNode nodeToClone) {

		final DOMNode newNode = (DOMNode)nodeToClone.cloneNode(false);

		final List<DOMNode> childrenToClone = (List<DOMNode>)nodeToClone.getChildNodes();

		for (final DOMNode childNodeToClone : childrenToClone) {

			final DOMNode newChildNode = (DOMNode)cloneAndAppendChildren(securityContext, childNodeToClone);
			newNode.appendChild(newChildNode);

		}

		return newNode;
	}

	public static String objectToString(final Object source) {

		if (source != null) {
			return source.toString();
		}

		return null;
	}

	// ----- actual methods -----
	@Override
	default boolean onCreation(final SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		if (LinkedTreeNode.super.isValid(errorBuffer)) {
			return checkName(errorBuffer);
		}

		return false;
	}

	@Override
	default boolean onModification(SecurityContext securityContext, ErrorBuffer errorBuffer, final ModificationQueue modificationQueue) throws FrameworkException {

		if (LinkedTreeNode.super.onModification(securityContext, errorBuffer, modificationQueue)) {

			try {

				increasePageVersion();

			} catch (FrameworkException ex) {

				logger.warn("Updating page version failed", ex);

			}

			return checkName(errorBuffer);
		}

		return false;
	}

	@Override
	default boolean isValid(final ErrorBuffer errorBuffer) {
		return LinkedTreeNode.super.isValid(errorBuffer);
	}

	default String getIdHash() {
		return getUuid();
	}

	default String getIdHashOrProperty() {

		String idHash = getProperty(DOMNode.dataHashProperty);
		if (idHash == null) {

			idHash = getIdHash();
		}

		return idHash;
	}

	@Override
	default Class<DOMChildren> getChildLinkType() {
		return DOMChildren.class;
	}

	@Override
	default Class<DOMSiblings> getSiblingLinkType() {
		return DOMSiblings.class;
	}

	default boolean isSharedComponent() {

		final Document _ownerDocument = getOwnerDocumentAsSuperUser();
		if (_ownerDocument != null) {

			try {

				return _ownerDocument.equals(CreateComponentCommand.getOrCreateHiddenDocument());

			} catch (FrameworkException fex) {

				logger.warn("Unable fetch ShadowDocument node: {}", fex.getMessage());
			}
		}

		return false;
	}

	// ----- public methods -----
	default List<DOMChildren> getChildRelationships() {
		return treeGetChildRelationships();
	}

	default String getPositionPath() {

		String path = "";

		DOMNode currentNode = this;
		while (currentNode.getParentNode() != null) {

			DOMNode parentNode = (DOMNode)currentNode.getParentNode();

			path = "/" + parentNode.treeGetChildPosition(currentNode) + path;

			currentNode = parentNode;

		}

		return path;

	}

	default Document getOwnerDocumentAsSuperUser() {

		Page cachedOwnerDocument = null;

		if (cachedOwnerDocument == null) {

			final PageLink ownership = getOutgoingRelationshipAsSuperUser(PageLink.class);
			if (ownership != null) {

				Page page = ownership.getTargetNode();
				cachedOwnerDocument = page;
			}
		}

		return cachedOwnerDocument;
	}

	default Set<PropertyKey> getDataPropertyKeys() {

		final Set<PropertyKey> customProperties = new TreeSet<>();
		final org.structr.api.graph.Node dbNode = getNode();
		final Iterable<String> props            = dbNode.getPropertyKeys();

		for (final String key : props) {

			if (key.startsWith("data-")) {

				final PropertyKey propertyKey = StructrApp.getConfiguration().getPropertyKeyForJSONName(getClass(), key);
				if (propertyKey instanceof BooleanProperty && dbNode.hasProperty(key)) {

					final Object defaultValue = propertyKey.defaultValue();
					final Object nodeValue    = dbNode.getProperty(key);

					// don't export boolean false values (which is the default)
					if (nodeValue != null && Boolean.FALSE.equals(nodeValue) && (defaultValue == null || nodeValue.equals(defaultValue))) {

						continue;
					}
				}

				customProperties.add(propertyKey);

			} else if (key.startsWith(CustomHtmlAttributeProperty.CUSTOM_HTML_ATTRIBUTE_PREFIX)) {

				final CustomHtmlAttributeProperty customProp = new CustomHtmlAttributeProperty(StructrApp.getConfiguration().getPropertyKeyForJSONName(getClass(), key));

				customProperties.add(customProp);
			}
		}

		return customProperties;
	}

	default String getPagePath() {

		final StringBuilder buf = new StringBuilder();
		DOMNode current         = this;

		while (current != null) {

			buf.insert(0, "/" + current.getContextName());
			current = current.getProperty(DOMNode.parent);
		}

		return buf.toString();
	}

	/**
	 * Render the node including data binding (outer rendering).
	 *
	 * @param renderContext
	 * @param depth
	 * @throws FrameworkException
	 */
	@Override
	default void render(final RenderContext renderContext, final int depth) throws FrameworkException {

		final SecurityContext securityContext = getSecurityContext();

		if (!securityContext.isVisible(this)) {
			return;
		}

		final GraphObject details = renderContext.getDetailsDataObject();
		final boolean detailMode = details != null;

		if (detailMode && getProperty(hideOnDetail)) {
			return;
		}

		if (!detailMode && getProperty(hideOnIndex)) {
			return;
		}

		final EditMode editMode = renderContext.getEditMode(securityContext.getUser(false));

		if (EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode) || EditMode.DEPLOYMENT.equals(editMode)) {

			renderContent(renderContext, depth);

		} else {

			final String subKey = getProperty(dataKey);

			if (StringUtils.isNotBlank(subKey)) {

				setDataRoot(renderContext, this, subKey);

				final GraphObject currentDataNode = renderContext.getDataObject();

				// fetch (optional) list of external data elements
				final Iterable<GraphObject> listData = checkListSources(securityContext, renderContext);

				final PropertyKey propertyKey;

				if (getProperty(renderDetails) && detailMode) {

					renderContext.setDataObject(details);
					renderContext.putDataObject(subKey, details);
					renderContent(renderContext, depth);

				} else {

					if (Iterables.isEmpty(listData) && currentDataNode != null) {

						// There are two alternative ways of retrieving sub elements:
						// First try to get generic properties,
						// if that fails, try to create a propertyKey for the subKey
						final Object elements = currentDataNode.getProperty(new GenericProperty(subKey));
						renderContext.setRelatedProperty(new GenericProperty(subKey));
						renderContext.setSourceDataObject(currentDataNode);

						if (elements != null) {

							if (elements instanceof Iterable) {

								for (Object o : (Iterable)elements) {

									if (o instanceof GraphObject) {

										GraphObject graphObject = (GraphObject)o;
										renderContext.putDataObject(subKey, graphObject);
										renderContent(renderContext, depth);

									}
								}

							}

						} else {

							propertyKey = StructrApp.getConfiguration().getPropertyKeyForJSONName(currentDataNode.getClass(), subKey, false);
							renderContext.setRelatedProperty(propertyKey);

							if (propertyKey != null) {

								final Object value = currentDataNode.getProperty(propertyKey);
								if (value != null) {

									if (value instanceof Iterable) {

										for (final Object o : ((Iterable)value)) {

											if (o instanceof GraphObject) {

												renderContext.putDataObject(subKey, (GraphObject)o);
												renderContent(renderContext, depth);

											}
										}
									}
								}
							}

						}

						// reset data node in render context
						renderContext.setDataObject(currentDataNode);
						renderContext.setRelatedProperty(null);

					} else {

						renderContext.setListSource(listData);
						renderNodeList(securityContext, renderContext, depth, subKey);

					}

				}

			} else {

				renderContent(renderContext, depth);
			}
		}

	}

	/**
	 * Return the content of this node depending on edit mode
	 *
	 * @param editMode
	 * @return content
	 * @throws FrameworkException
	 */
	default String getContent(final RenderContext.EditMode editMode) throws FrameworkException {

		final RenderContext ctx         = new RenderContext(getSecurityContext(), null, null, editMode);
		final StringRenderBuffer buffer = new StringRenderBuffer();
		ctx.setBuffer(buffer);
		render(ctx, 0);

		// extract source
		return buffer.getBuffer().toString();
	}

	default Template getClosestTemplate(final Page page) {

		DOMNode node = this;

		while (node != null) {

			if (node instanceof Template) {

				final Template template = (Template)node;

				Document doc = template.getOwnerDocument();

				if (doc == null) {

					doc = node.getClosestPage();
				}

				if (doc != null && (page == null || doc.equals(page))) {

					return template;

				}

				final List<DOMNode> _syncedNodes = template.getProperty(DOMNode.syncedNodes);

				for (final DOMNode syncedNode : _syncedNodes) {

					doc = syncedNode.getOwnerDocument();

					if (doc != null && (page == null || doc.equals(page))) {

						return (Template)syncedNode;

					}

				}

			}

			node = (DOMNode)node.getParentNode();

		}

		return null;

	}

	default Page getClosestPage() {

		DOMNode node = this;

		while (node != null) {

			if (node instanceof Page) {

				return (Page)node;
			}

			node = (DOMNode)node.getParentNode();

		}

		return null;
	}

	default boolean inTrash() {
		return (getProperty(DOMNode.parent) == null && getOwnerDocumentAsSuperUser() == null);
	}

	default Iterable<GraphObject> checkListSources(final SecurityContext securityContext, final RenderContext renderContext) {

		// try registered data sources first
		for (GraphDataSource source : LIST_SOURCES) {

			try {

				Iterable<GraphObject> graphData = source.getData(renderContext, this);
				if (graphData != null && !Iterables.isEmpty(graphData)) {
					return graphData;
				}

			} catch (FrameworkException fex) {

				logger.warn("", fex);

				logger.warn("Could not retrieve data from graph data source {}: {}", new Object[]{source, fex});
			}
		}

		return Collections.EMPTY_LIST;
	}
	// ----- private methods -----
	/**
	 * Get all ancestors of this node
	 *
	 * @return list of ancestors
	 */
	default List<Node> getAncestors() {

		List<Node> ancestors = new ArrayList();

		Node _parent = getParentNode();
		while (_parent != null) {

			ancestors.add(_parent);
			_parent = _parent.getParentNode();
		}

		return ancestors;

	}

	// ----- protected methods -----
	/**
	 * This method will be called by the DOM logic when this node gets a new child. Override this method if you need to set properties on the child depending on its type etc.
	 *
	 * @param newChild
	 */
	default void handleNewChild(Node newChild) {

		final Page page = (Page)getOwnerDocument();

		for (final DOMNode child : getAllChildNodes()) {

			try {

				child.setProperties(child.getSecurityContext(), new PropertyMap(ownerDocument, page));

			} catch (FrameworkException ex) {
				logger.warn("", ex);
			}

		}

	}

	default boolean renderDeploymentExportComments(final AsyncBuffer out, final boolean isContentNode) {

		final Set<String> instructions = new LinkedHashSet<>();

		getVisibilityInstructions(instructions);
		getLinkableInstructions(instructions);
		getSecurityInstructions(instructions);

		if (isContentNode) {

			// special rules apply for content nodes: since we can not store
			// structr-specific properties in the attributes of the element,
			// we need to encode those attributes in instructions.
			getContentInstructions(instructions);
		}

		if (!instructions.isEmpty()) {

			out.append("<!-- ");

			for (final Iterator<String> it = instructions.iterator(); it.hasNext();) {

				final String instruction = it.next();

				out.append(instruction);

				if (it.hasNext()) {
					out.append(", ");
				}
			}

			out.append(" -->");

			return true;

		} else {

			return false;
		}
	}

	default void renderSharedComponentConfiguration(final AsyncBuffer out, final EditMode editMode) {

		if (EditMode.DEPLOYMENT.equals(editMode)) {

			final String configuration = getProperty(DOMNode.sharedComponentConfiguration);
			if (StringUtils.isNotBlank(configuration)) {

				out.append(" data-structr-meta-shared-component-configuration=\"");
				out.append(escapeForHtmlAttributes(configuration));
				out.append("\"");
			}
		}
	}

	default void getContentInstructions(final Set<String> instructions) {

		final String _contentType = getProperty(Content.contentType);
		if (_contentType != null) {

			instructions.add("@structr:content(" + escapeForHtmlAttributes(_contentType) + ")");
		}

		final String _showConditions = getProperty(DOMNode.showConditions);
		if (StringUtils.isNotEmpty(_showConditions)) {

			instructions.add("@structr:show(" + escapeForHtmlAttributes(_showConditions) + ")");
		}

		final String _hideConditions = getProperty(DOMNode.hideConditions);
		if (StringUtils.isNotEmpty(_hideConditions)) {

			instructions.add("@structr:hide(" + escapeForHtmlAttributes(_hideConditions) + ")");
		}
	}

	default void getLinkableInstructions(final Set<String> instructions) {

		if (this instanceof LinkSource) {

			final LinkSource linkSourceElement = (LinkSource)this;
			final Linkable linkable            = linkSourceElement.getProperty(LinkSource.linkable);

			if (linkable != null) {

				final String path = linkable.getPath();
				if (path != null) {

					instructions.add("@structr:link(" + path + ")");

				} else {

					logger.warn("Cannot export linkable relationship, no path.");
				}
			}
		}
	}

	default void getVisibilityInstructions(final Set<String> instructions) {

		final Page _ownerDocument       = (Page)getOwnerDocument();

		if(_ownerDocument == null) {

			logger.warn("DOMNode {} has no owner document!", getUuid());
		}

		final boolean pagePublic        = _ownerDocument != null ? _ownerDocument.isVisibleToPublicUsers() : false;
		final boolean pageProtected     = _ownerDocument != null ? _ownerDocument.isVisibleToAuthenticatedUsers() : false;
		final boolean pagePrivate       = !pagePublic && !pageProtected;
		final boolean pagePublicOnly    = pagePublic && !pageProtected;
		final boolean elementPublic     = isVisibleToPublicUsers();
		final boolean elementProtected  = isVisibleToAuthenticatedUsers();
		final boolean elementPrivate    = !elementPublic && !elementProtected;
		final boolean elementPublicOnly = elementPublic && !elementProtected;

		if (pagePrivate && !elementPrivate) {

			if (elementPublicOnly) {
				instructions.add("@structr:public-only");
				return;
			}

			if (elementPublic && elementProtected) {
				instructions.add("@structr:public");
				return;
			}

			if (elementProtected) {
				instructions.add("@structr:protected");
				return;
			}
		}

		if (pageProtected && !elementProtected) {

			if (elementPublicOnly) {
				instructions.add("@structr:public-only");
				return;
			}

			if (elementPublic && elementProtected) {
				instructions.add("@structr:public");
				return;
			}

			if (elementPrivate) {
				instructions.add("@structr:private");
				return;
			}
		}

		if (pagePublic && !elementPublic) {

			if (elementPublicOnly) {
				instructions.add("@structr:public-only");
				return;
			}

			if (elementProtected) {
				instructions.add("@structr:protected");
				return;
			}

			if (elementPrivate) {
				instructions.add("@structr:private");
				return;
			}
		}

		if (pagePublicOnly && !elementPublicOnly) {

			if (elementPublic && elementProtected) {
				instructions.add("@structr:public");
				return;
			}

			if (elementProtected) {
				instructions.add("@structr:protected");
				return;
			}

			if (elementPrivate) {
				instructions.add("@structr:private");
				return;
			}

			return;
		}
	}

	default void getSecurityInstructions(final Set<String> instructions) {

		final Principal _owner = getOwnerNode();
		if (_owner != null) {

			instructions.add("@structr:owner(" + _owner.getProperty(AbstractNode.name) + ")");
		}

		for (final Security security : getSecurityRelationships()) {

			if (security != null) {

				final Principal grantee = security.getSourceNode();
				final Set<String> perms = security.getPermissions();
				final StringBuilder shortPerms = new StringBuilder();

				// first character only
				for (final String perm : perms) {
					if (perm.length() > 0) {
						shortPerms.append(perm.substring(0, 1));
					}
				}

				if (shortPerms.length() > 0) {
					// ignore SECURITY-relationships without permissions
					instructions.add("@structr:grant(" + grantee.getProperty(AbstractNode.name) + "," + shortPerms.toString() + ")");
				}
			}
		}
	}

	default void renderCustomAttributes(final AsyncBuffer out, final SecurityContext securityContext, final RenderContext renderContext) throws FrameworkException {

		final org.structr.api.graph.Node dbNode = getNode();
		EditMode editMode                       = renderContext.getEditMode(securityContext.getUser(false));

		for (PropertyKey key : getDataPropertyKeys()) {

			String value = "";

			if (EditMode.DEPLOYMENT.equals(editMode)) {

				final Object obj = getProperty(key);
				if (obj != null) {

					value = obj.toString();
				}

			} else {

				value = getPropertyWithVariableReplacement(renderContext, key);
				if (value != null) {

					value = value.trim();
				}
			}

			if (!(EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode))) {

				value = escapeForHtmlAttributes(value);
			}

			if (StringUtils.isNotBlank(value)) {

				if (key instanceof CustomHtmlAttributeProperty) {
					out.append(" ").append(((CustomHtmlAttributeProperty)key).cleanName()).append("=\"").append(value).append("\"");
				} else {
					out.append(" ").append(key.dbName()).append("=\"").append(value).append("\"");
				}
			}
		}

		if (EditMode.DEPLOYMENT.equals(editMode) || EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode)) {

			if (EditMode.DEPLOYMENT.equals(editMode)) {

				// export name property if set
				final String name = getProperty(AbstractNode.name);
				if (name != null) {

					out.append(" data-structr-meta-name=\"").append(escapeForHtmlAttributes(name)).append("\"");
				}
			}

			for (final Property p : rawProps) {

				String htmlName = "data-structr-meta-" + CaseHelper.toUnderscore(p.jsonName(), false).replaceAll("_", "-");
				Object value = getProperty(p);

				if (value != null) {

					final boolean isBoolean  = p instanceof BooleanProperty;
					final String stringValue = value.toString();

					if ((isBoolean && "true".equals(stringValue)) || (!isBoolean && StringUtils.isNotBlank(stringValue))) {
						out.append(" ").append(htmlName).append("=\"").append(escapeForHtmlAttributes(stringValue)).append("\"");
					}
				}
			}
		}
	}

	default void setDataRoot(final RenderContext renderContext, final NodeInterface node, final String dataKey) {

		// an outgoing RENDER_NODE relationship points to the data node where rendering starts
		for (RenderNode rel : node.getOutgoingRelationships(RenderNode.class)) {

			NodeInterface dataRoot = rel.getTargetNode();

			// set start node of this rendering to the data root node
			renderContext.putDataObject(dataKey, dataRoot);

			// allow only one data tree to be rendered for now
			break;
		}
	}

	default void renderNodeList(final SecurityContext securityContext, final RenderContext renderContext, final int depth, final String dataKey) throws FrameworkException {

		final Iterable<GraphObject> listSource = renderContext.getListSource();
		if (listSource != null) {

			for (final GraphObject dataObject : listSource) {

				// make current data object available in renderContext
				renderContext.putDataObject(dataKey, dataObject);
				renderContent(renderContext, depth + 1);

			}

			renderContext.clearDataObject(dataKey);
		}
	}

	/**
	 * Increase version of the page.
	 *
	 * A {@link Page} is a {@link DOMNode} as well, so we have to check 'this' as well.
	 *
	 * @throws FrameworkException
	 */
	default void increasePageVersion() throws FrameworkException {

		Page page = null;

		if (this instanceof Page) {

			page = (Page)this;

		} else {

			// ignore page-less nodes
			if (getProperty(DOMNode.parent) == null) {
				return;
			}
		}

		if (page == null) {

			final List<Node> ancestors = getAncestors();
			if (!ancestors.isEmpty()) {

				final DOMNode rootNode = (DOMNode)ancestors.get(ancestors.size() - 1);
				if (rootNode instanceof Page) {
					page = (Page)rootNode;
				} else {
					rootNode.increasePageVersion();
				}

			} else {

				final List<DOMNode> _syncedNodes = getProperty(DOMNode.syncedNodes);
				for (final DOMNode syncedNode : _syncedNodes) {

					syncedNode.increasePageVersion();
				}
			}

		}

		if (page != null) {

			page.increaseVersion();

		}

	}

	default boolean avoidWhitespace() {

		return false;

	}

	default void checkIsChild(Node otherNode) throws DOMException {

		if (otherNode instanceof DOMNode) {

			Node _parent = otherNode.getParentNode();

			if (!isSameNode(_parent)) {

				throw new DOMException(DOMException.NOT_FOUND_ERR, NOT_FOUND_ERR_MESSAGE);
			}

			// validation successful
			return;
		}

		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, NOT_SUPPORTED_ERR_MESSAGE);
	}

	default void checkHierarchy(Node otherNode) throws DOMException {

		// we can only check DOMNodes
		if (otherNode instanceof DOMNode) {

			// verify that the other node is not this node
			if (isSameNode(otherNode)) {
				throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, HIERARCHY_REQUEST_ERR_MESSAGE_SAME_NODE);
			}

			// verify that otherNode is not one of the
			// the ancestors of this node
			// (prevent circular relationships)
			Node _parent = getParentNode();
			while (_parent != null) {

				if (_parent.isSameNode(otherNode)) {
					throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, HIERARCHY_REQUEST_ERR_MESSAGE_ANCESTOR);
				}

				_parent = _parent.getParentNode();
			}

			// TODO: check hierarchy constraints imposed by the schema
			// validation successful
			return;
		}

		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, NOT_SUPPORTED_ERR_MESSAGE);
	}

	default void checkSameDocument(Node otherNode) throws DOMException {

		Document doc = getOwnerDocument();

		if (doc != null) {

			Document otherDoc = otherNode.getOwnerDocument();

			// Shadow doc is neutral
			if (otherDoc != null && !doc.equals(otherDoc) && !(doc instanceof ShadowDocument)) {

				logger.warn("{} node with UUID {} has owner document {} with UUID {} whereas this node has owner document {} with UUID {}",
					otherNode.getClass().getSimpleName(),
					((NodeInterface)otherNode).getUuid(),
					otherDoc.getClass().getSimpleName(),
					((NodeInterface)otherDoc).getUuid(),
					doc.getClass().getSimpleName(),
					((NodeInterface)doc).getUuid()
				);

				throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, WRONG_DOCUMENT_ERR_MESSAGE);
			}

			if (otherDoc == null) {

				((DOMNode)otherNode).doAdopt((Page)doc);

			}
		}
	}

	default void checkWriteAccess() throws DOMException {

		final SecurityContext securityContext = getSecurityContext();

		if (!isGranted(Permission.write, securityContext)) {

			logger.warn("User {} has no write access to {} node with UUID {}", securityContext.getUser(false), this.getClass().getSimpleName(), getUuid());

			throw new DOMException(DOMException.NO_MODIFICATION_ALLOWED_ERR, NO_MODIFICATION_ALLOWED_MESSAGE);
		}
	}

	default void checkReadAccess() throws DOMException {

		final SecurityContext securityContext = getSecurityContext();

		if (securityContext.isVisible(this) || isGranted(Permission.read, securityContext)) {
			return;
		}

		logger.warn("User {} has no read access to {} node with UUID {}", securityContext.getUser(false), this.getClass().getSimpleName(), getUuid());

		throw new DOMException(DOMException.INVALID_ACCESS_ERR, INVALID_ACCESS_ERR_MESSAGE);
	}

	/**
	 * Decide whether this node should be displayed for the given conditions string.
	 *
	 * @param renderContext
	 * @return true if node should be displayed
	 */
	default boolean displayForConditions(final RenderContext renderContext) {

		final SecurityContext securityContext = getSecurityContext();

		// In raw or widget mode, render everything
		EditMode editMode = renderContext.getEditMode(securityContext.getUser(false));
		if (EditMode.DEPLOYMENT.equals(editMode) || EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode)) {
			return true;
		}

		String _showConditions = getProperty(DOMNode.showConditions);
		String _hideConditions = getProperty(DOMNode.hideConditions);

		// If both fields are empty, render node
		if (StringUtils.isBlank(_hideConditions) && StringUtils.isBlank(_showConditions)) {
			return true;
		}
		try {
			// If hide conditions evaluate to "true", don't render
			if (StringUtils.isNotBlank(_hideConditions) && Boolean.TRUE.equals(Scripting.evaluate(renderContext, this, "${".concat(_hideConditions).concat("}"), "hide condition"))) {
				return false;
			}

		} catch (UnlicensedException|FrameworkException ex) {
			logger.error("Hide conditions " + _hideConditions + " could not be evaluated.", ex);
		}
		try {
			// If show conditions evaluate to "false", don't render
			if (StringUtils.isNotBlank(_showConditions) && Boolean.FALSE.equals(Scripting.evaluate(renderContext, this, "${".concat(_showConditions).concat("}"), "show condition"))) {
				return false;
			}

		} catch (UnlicensedException|FrameworkException ex) {
			logger.error("Show conditions " + _showConditions + " could not be evaluated.", ex);
		}

		return true;

	}

	/**
	 * Decide whether this node should be displayed for the given locale settings.
	 *
	 * @param renderContext
	 * @return true if node should be displayed
	 */
	default boolean displayForLocale(final RenderContext renderContext) {

		final SecurityContext securityContext = getSecurityContext();

		// In raw or widget mode, render everything
		EditMode editMode = renderContext.getEditMode(securityContext.getUser(false));
		if (EditMode.DEPLOYMENT.equals(editMode) || EditMode.RAW.equals(editMode) || EditMode.WIDGET.equals(editMode)) {
			return true;
		}

		String localeString = renderContext.getLocale().toString();

		String show = getProperty(DOMNode.showForLocales);
		String hide = getProperty(DOMNode.hideForLocales);

		// If both fields are empty, render node
		if (StringUtils.isBlank(hide) && StringUtils.isBlank(show)) {
			return true;
		}

		// If locale string is found in hide, don't render
		if (StringUtils.contains(hide, localeString)) {
			return false;
		}

		// If locale string is found in hide, don't render
		if (StringUtils.isNotBlank(show) && !StringUtils.contains(show, localeString)) {
			return false;
		}

		return true;

	}

	default void collectNodesByPredicate(Node startNode, DOMNodeList results, Predicate<Node> predicate, int depth, boolean stopOnFirstHit) {

		final SecurityContext securityContext = getSecurityContext();

		if (predicate instanceof Filter) {
			((Filter)predicate).setSecurityContext(securityContext);
		}

		if (predicate.accept(startNode)) {

			results.add(startNode);

			if (stopOnFirstHit) {

				return;
			}
		}

		NodeList _children = startNode.getChildNodes();
		if (_children != null) {

			int len = _children.getLength();
			for (int i = 0; i < len; i++) {

				Node child = _children.item(i);

				collectNodesByPredicate(child, results, predicate, depth + 1, stopOnFirstHit);
			}
		}
	}

	// ----- interface org.w3c.dom.Node -----
	@Override
	default String getTextContent() throws DOMException {

		final DOMNodeList results = new DOMNodeList();
		final TextCollector textCollector = new TextCollector();

		collectNodesByPredicate(this, results, textCollector, 0, false);

		return textCollector.getText();
	}

	@Override
	default void setTextContent(String textContent) throws DOMException {
		// TODO: implement?
	}

	@Override
	default Node getParentNode() {
		// FIXME: type cast correct here?
		return (Node)getProperty(parent);
	}

	@Override
	default NodeList getChildNodes() {
		checkReadAccess();
		return new DOMNodeList(treeGetChildren());
	}

	@Override
	default Node getFirstChild() {
		checkReadAccess();
		return treeGetFirstChild();
	}

	@Override
	default Node getLastChild() {
		return treeGetLastChild();
	}

	@Override
	default Node getPreviousSibling() {
		return listGetPrevious(this);
	}

	@Override
	default Node getNextSibling() {
		return listGetNext(this);
	}

	@Override
	default Document getOwnerDocument() {
		return getProperty(ownerDocument);
	}

	@Override
	default Node insertBefore(final Node newChild, final Node refChild) throws DOMException {

		// according to DOM spec, insertBefore with null refChild equals appendChild
		if (refChild == null) {

			return appendChild(newChild);
		}

		checkWriteAccess();

		checkSameDocument(newChild);
		checkSameDocument(refChild);

		checkHierarchy(newChild);
		checkHierarchy(refChild);

		if (newChild instanceof DocumentFragment) {

			// When inserting document fragments, we must take
			// care of the special case that the nodes already
			// have a NEXT_LIST_ENTRY relationship coming from
			// the document fragment, so we must first remove
			// the node from the document fragment and then
			// add it to the new parent.
			final DocumentFragment fragment = (DocumentFragment)newChild;
			Node currentChild = fragment.getFirstChild();

			while (currentChild != null) {

				// save next child in fragment list for later use
				Node savedNextChild = currentChild.getNextSibling();

				// remove child from document fragment
				fragment.removeChild(currentChild);

				// insert child into new parent
				insertBefore(currentChild, refChild);

				// next
				currentChild = savedNextChild;
			}

		} else {

			final Node _parent = newChild.getParentNode();
			if (_parent != null) {

				_parent.removeChild(newChild);
			}

			try {

				// do actual tree insertion here
				treeInsertBefore((DOMNode)newChild, (DOMNode)refChild);

			} catch (FrameworkException frex) {

				if (frex.getStatus() == 404) {

					throw new DOMException(DOMException.NOT_FOUND_ERR, frex.getMessage());

				} else {

					throw new DOMException(DOMException.INVALID_STATE_ERR, frex.getMessage());
				}
			}

			// allow parent to set properties in new child
			handleNewChild(newChild);
		}

		return refChild;
	}

	@Override
	default Node replaceChild(final Node newChild, final Node oldChild) throws DOMException {

		checkWriteAccess();

		checkSameDocument(newChild);
		checkSameDocument(oldChild);

		checkHierarchy(newChild);
		checkHierarchy(oldChild);

		if (newChild instanceof DocumentFragment) {

			// When inserting document fragments, we must take
			// care of the special case that the nodes already
			// have a NEXT_LIST_ENTRY relationship coming from
			// the document fragment, so we must first remove
			// the node from the document fragment and then
			// add it to the new parent.
			// replace indirectly using insertBefore and remove
			final DocumentFragment fragment = (DocumentFragment)newChild;
			Node currentChild = fragment.getFirstChild();

			while (currentChild != null) {

				// save next child in fragment list for later use
				final Node savedNextChild = currentChild.getNextSibling();

				// remove child from document fragment
				fragment.removeChild(currentChild);

				// add child to new parent
				insertBefore(currentChild, oldChild);

				// next
				currentChild = savedNextChild;
			}

			// finally, remove reference element
			removeChild(oldChild);

		} else {

			Node _parent = newChild.getParentNode();
			if (_parent != null && _parent instanceof DOMNode) {

				_parent.removeChild(newChild);
			}

			try {
				// replace directly
				treeReplaceChild((DOMNode)newChild, (DOMNode)oldChild);

			} catch (FrameworkException frex) {

				if (frex.getStatus() == 404) {

					throw new DOMException(DOMException.NOT_FOUND_ERR, frex.getMessage());

				} else {

					throw new DOMException(DOMException.INVALID_STATE_ERR, frex.getMessage());
				}
			}

			// allow parent to set properties in new child
			handleNewChild(newChild);
		}

		return oldChild;
	}

	@Override
	default Node removeChild(final Node node) throws DOMException {

		checkWriteAccess();
		checkSameDocument(node);
		checkIsChild(node);

		try {

			treeRemoveChild((DOMNode)node);

		} catch (FrameworkException fex) {

			throw new DOMException(DOMException.INVALID_STATE_ERR, fex.toString());
		}

		return node;
	}

	@Override
	default Node appendChild(final Node newChild) throws DOMException {

		checkWriteAccess();
		checkSameDocument(newChild);
		checkHierarchy(newChild);

		try {

			if (newChild instanceof DocumentFragment) {

				// When inserting document fragments, we must take
				// care of the special case that the nodes already
				// have a NEXT_LIST_ENTRY relationship coming from
				// the document fragment, so we must first remove
				// the node from the document fragment and then
				// add it to the new parent.
				// replace indirectly using insertBefore and remove
				final DocumentFragment fragment = (DocumentFragment)newChild;
				Node currentChild = fragment.getFirstChild();

				while (currentChild != null) {

					// save next child in fragment list for later use
					final Node savedNextChild = currentChild.getNextSibling();

					// remove child from document fragment
					fragment.removeChild(currentChild);

					// append child to new parent
					appendChild(currentChild);

					// next
					currentChild = savedNextChild;
				}

			} else {

				final Node _parent = newChild.getParentNode();

				if (_parent != null && _parent instanceof DOMNode) {
					_parent.removeChild(newChild);
				}

				treeAppendChild((DOMNode)newChild);

				// allow parent to set properties in new child
				handleNewChild(newChild);
			}

		} catch (FrameworkException fex) {

			throw new DOMException(DOMException.INVALID_STATE_ERR, fex.toString());
		}

		return newChild;
	}

	@Override
	default boolean hasChildNodes() {
		return !getProperty(children).isEmpty();
	}

	@Override
	default Node cloneNode(boolean deep) {

		final SecurityContext securityContext = getSecurityContext();

		if (deep) {

			return cloneAndAppendChildren(securityContext, this);

		} else {

			final PropertyMap properties = new PropertyMap();

			for (Iterator<PropertyKey> it = getPropertyKeys(PropertyView.Ui).iterator(); it.hasNext();) {

				final PropertyKey key = it.next();

				// skip blacklisted properties
				if (cloneBlacklist.contains(key)) {
					continue;
				}


				if (!key.isUnvalidated()) {
					properties.put(key, getProperty(key));
				}
			}

			// htmlView is necessary for the cloning of DOM nodes - otherwise some properties won't be cloned
			for (Iterator<PropertyKey> it = getPropertyKeys(DOMElement.htmlView.name()).iterator(); it.hasNext();) {

				final PropertyKey key = it.next();

				// skip blacklisted properties
				if (cloneBlacklist.contains(key)) {
					continue;
				}

				if (!key.isUnvalidated()) {
					properties.put(key, getProperty(key));
				}
			}

			if (this instanceof LinkSource) {

				final LinkSource linkSourceElement = (LinkSource)this;

				properties.put(LinkSource.linkable, linkSourceElement.getProperty(LinkSource.linkable));

			}

			final App app = StructrApp.getInstance(securityContext);

			try {
				final DOMNode node = app.create(getClass(), properties);

				return node;

			} catch (FrameworkException ex) {

				ex.printStackTrace();

				throw new DOMException(DOMException.INVALID_STATE_ERR, ex.toString());

			}

		}
	}

	@Override
	default boolean isSupported(String string, String string1) {
		return false;
	}

	@Override
	default String getNamespaceURI() {
		return null; //return "http://www.w3.org/1999/xhtml";
	}

	@Override
	default String getPrefix() {
		return null;
	}

	@Override
	default void setPrefix(String prefix) throws DOMException {
	}

	@Override
	default String getBaseURI() {
		return null;
	}

	@Override
	default short compareDocumentPosition(Node node) throws DOMException {
		return 0;
	}

	@Override
	default boolean isSameNode(Node node) {

		if (node != null && node instanceof DOMNode) {

			String otherId = ((DOMNode)node).getProperty(GraphObject.id);
			String ourId = getProperty(GraphObject.id);

			if (ourId != null && otherId != null && ourId.equals(otherId)) {
				return true;
			}
		}

		return false;
	}

	@Override
	default String lookupPrefix(String string) {
		return null;
	}

	@Override
	default boolean isDefaultNamespace(String string) {
		return true;
	}

	@Override
	default String lookupNamespaceURI(String string) {
		return null;
	}

	@Override
	default boolean isEqualNode(Node node) {
		return equals(node);
	}

	@Override
	default Object setUserData(String string, Object o, UserDataHandler udh) {
		return null;
	}

	@Override
	default Object getUserData(String string) {
		return null;
	}

	@Override
	default void normalize() {

		Document document = getOwnerDocument();
		if (document != null) {

			// merge adjacent text nodes until there is only one left
			Node child = getFirstChild();
			while (child != null) {

				if (child instanceof Text) {

					Node next = child.getNextSibling();
					if (next != null && next instanceof Text) {

						String text1 = child.getNodeValue();
						String text2 = next.getNodeValue();

						// create new text node
						Text newText = document.createTextNode(text1.concat(text2));

						removeChild(child);
						insertBefore(newText, next);
						removeChild(next);

						child = newText;

					} else {

						// advance to next node
						child = next;
					}

				} else {

					// advance to next node
					child = child.getNextSibling();

				}
			}

			// recursively normalize child nodes
			if (hasChildNodes()) {

				Node currentChild = getFirstChild();
				while (currentChild != null) {

					currentChild.normalize();
					currentChild = currentChild.getNextSibling();
				}
			}
		}

	}

	default void setVisibility(final boolean publicUsers, final boolean authenticatedUsers) throws FrameworkException {

		final PropertyMap map = new PropertyMap();

		map.put(NodeInterface.visibleToPublicUsers, publicUsers);
		map.put(NodeInterface.visibleToAuthenticatedUsers, authenticatedUsers);

		setProperties(getSecurityContext(), map);
	}

	default boolean checkName(final ErrorBuffer errorBuffer) {

		final String _name = getProperty(AbstractNode.name);
		if (_name != null && _name.contains("/")) {

			errorBuffer.add(new SemanticErrorToken(getType(), AbstractNode.name, "may_not_contain_slashes", _name));

			return false;
		}

		return true;
	}

	// ----- interface DOMAdoptable -----
	@Override
	default Node doAdopt(final Page _page) throws DOMException {

		if (_page != null) {

			try {

				setProperties(getSecurityContext(), new PropertyMap(ownerDocument, _page));

			} catch (FrameworkException fex) {

				throw new DOMException(DOMException.INVALID_STATE_ERR, fex.getMessage());

			}
		}

		return this;
	}

	// ----- nested classes -----
	static class TextCollector implements Predicate<Node> {

		private final StringBuilder textBuffer = new StringBuilder(200);

		@Override
		public boolean accept(final Node obj) {

			if (obj instanceof Text) {
				textBuffer.append(((Text)obj).getTextContent());
			}

			return false;
		}

		public String getText() {
			return textBuffer.toString();
		}
	}

	static class TagPredicate implements Predicate<Node> {

		private String tagName = null;

		public TagPredicate(String tagName) {
			this.tagName = tagName;
		}

		@Override
		public boolean accept(Node obj) {

			if (obj instanceof DOMElement) {

				DOMElement elem = (DOMElement)obj;

				if (tagName.equals(elem.getProperty(DOMElement.tag))) {
					return true;
				}
			}

			return false;
		}
	}
}
