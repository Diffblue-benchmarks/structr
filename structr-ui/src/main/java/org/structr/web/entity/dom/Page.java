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

import org.structr.web.entity.html.Html5DocumentType;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.structr.api.Predicate;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.Export;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.graph.CreateNodeCommand;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.Tx;
import org.structr.core.property.BooleanProperty;
import org.structr.core.property.ConstantBooleanProperty;
import org.structr.core.property.IntProperty;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.RelationProperty;
import org.structr.core.property.StartNode;
import org.structr.core.property.StartNodes;
import org.structr.core.property.StringProperty;
import org.structr.web.common.RenderContext;
import org.structr.web.common.StringRenderBuffer;
import org.structr.web.diff.InvertibleModificationOperation;
import org.structr.web.entity.Linkable;
import org.structr.web.entity.Site;
import org.structr.web.entity.html.Html;
import org.structr.web.entity.relation.PageLink;
import org.structr.web.entity.relation.Pages;
import org.structr.web.importer.Importer;
import org.structr.web.property.UiNotion;
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

/**
 * Represents a page resource.
 */
public interface Page extends DOMNode, Linkable, Document, DOMImplementation {

	public static final Set<String> nonBodyTags = new HashSet<>(Arrays.asList(new String[] { "html", "head", "body", "meta", "link" } ));

	public static final Property<Integer> version = new IntProperty("version").indexed().readOnly();
	public static final Property<Integer> position = new IntProperty("position").indexed();
	public static final Property<String> contentType = new StringProperty("contentType").indexed();
	public static final Property<Integer> cacheForSeconds = new IntProperty("cacheForSeconds");
	public static final Property<String> showOnErrorCodes = new StringProperty("showOnErrorCodes").indexed();
	public static final Property<List<DOMNode>> elements = new StartNodes<>("elements", PageLink.class);
	public static final Property<Boolean> isPage = new ConstantBooleanProperty("isPage", true);
	public static final Property<Boolean> dontCache = new BooleanProperty("dontCache").defaultValue(false);
	public static final Property<String> category = new StringProperty("category").indexed();

	// if enabled, prevents asynchronous page rendering; enable this flag when using the stream() builtin method
	public static final Property<Boolean> pageCreatesRawData = new BooleanProperty("pageCreatesRawData").defaultValue(false);


	public static final Property<String> path = new StringProperty("path").indexed();
	public static final Property<Site> site  = new StartNode<>("site", Pages.class, new UiNotion()).indexedWhenEmpty();

	public static final org.structr.common.View publicView = new org.structr.common.View(Page.class, PropertyView.Public,
		name, path, children, linkingElements, contentType, owner, cacheForSeconds, version, position, showOnErrorCodes, isPage, site, dontCache, pageCreatesRawData, enableBasicAuth, basicAuthRealm, category
	);

	public static final org.structr.common.View uiView = new org.structr.common.View(Page.class, PropertyView.Ui,
		name, path, children, linkingElements, contentType, owner, cacheForSeconds, version, position, showOnErrorCodes, isPage, site, dontCache, pageCreatesRawData, enableBasicAuth, basicAuthRealm, category
	);

	public static final org.structr.common.View categoryView = new org.structr.common.View(Page.class, "category",
		category
	);

	/**
	 * Creates a new Page entity with the given name in the database.
	 *
	 * @param securityContext the security context to use
	 * @param name the name of the new ownerDocument, defaults to
	 * "ownerDocument" if not set
	 *
	 * @return the new ownerDocument
	 * @throws FrameworkException
	 */
	public static Page createNewPage(SecurityContext securityContext, String name) throws FrameworkException {
		return createNewPage(securityContext, null, name);
	}

	/**
	 * Creates a new Page entity with the given name in the database.
	 *
	 * @param securityContext the security context to use
	 * @param uuid the UUID of the new page or null
	 * @param name the name of the new page, defaults to "page"
	 * "ownerDocument" if not set
	 *
	 * @return the new ownerDocument
	 * @throws FrameworkException
	 */
	public static Page createNewPage(final SecurityContext securityContext, final String uuid, final String name) throws FrameworkException {

		final App app                = StructrApp.getInstance(securityContext);
		final PropertyMap properties = new PropertyMap();

		// set default values for properties on creation to avoid them
		// being set separately when indexing later
		properties.put(AbstractNode.name, name != null ? name : "page");
		properties.put(AbstractNode.type, Page.class.getSimpleName());
		properties.put(Page.contentType, "text/html");
		properties.put(Page.hideOnDetail, false);
		properties.put(Page.hideOnIndex, false);
		properties.put(Page.enableBasicAuth, false);

		if (uuid != null) {
			properties.put(Page.id, uuid);
		}

		return app.create(Page.class, properties);
	}

	/**
	 * Creates a default simple page for the Structr backend "add page" button.
	 *
	 * @param securityContext
	 * @param name
	 * @return
	 * @throws FrameworkException
	 */
	public static Page createSimplePage(final SecurityContext securityContext, final String name) throws FrameworkException {

		final Page page = Page.createNewPage(securityContext, name);
		if (page != null) {

			Element html  = page.createElement("html");
			Element head  = page.createElement("head");
			Element body  = page.createElement("body");
			Element title = page.createElement("title");
			Element h1    = page.createElement("h1");
			Element div   = page.createElement("div");

			try {
				// add HTML element to page
				page.appendChild(html);

				// add HEAD and BODY elements to HTML
				html.appendChild(head);
				html.appendChild(body);

				// add TITLE element to HEAD
				head.appendChild(title);

				// add H1 element to BODY
				body.appendChild(h1);

				// add DIV element to BODY
				body.appendChild(div);

				// add text nodes
				title.appendChild(page.createTextNode("${capitalize(page.name)}"));
				h1.appendChild(page.createTextNode("${capitalize(page.name)}"));
				div.appendChild(page.createTextNode("Initial body text"));

			} catch (DOMException dex) {

				logger.warn("", dex);

				throw new FrameworkException(422, dex.getMessage());
			}
		}

		return page;
	}

	@Override
	default boolean contentEquals(DOMNode otherNode) {
		return false;
	}

	@Override
	default String getContextName() {
		return getProperty(Page.name);
	}

	@Override
	default void updateFromNode(final DOMNode newNode) throws FrameworkException {
		// do nothing
	}

	@Override
	default Object getFeature(final String version, final String feature) {
		return null;
	}

	@Override
	default boolean hasFeature(final String version, final String feature){
		return false;
	}

	@Override
	default void checkHierarchy(Node otherNode) throws DOMException {

		// verify that this document has only one document element
		if (getDocumentElement() != null) {
			throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, HIERARCHY_REQUEST_ERR_MESSAGE_DOCUMENT);
		}

		if (!(otherNode instanceof Html || otherNode instanceof Comment || otherNode instanceof Template)) {

			throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR, HIERARCHY_REQUEST_ERR_MESSAGE_ELEMENT);
		}

		DOMNode.super.checkHierarchy(otherNode);
	}

	@Override
	default boolean hasChildNodes() {
		return true;
	}

	@Override
	default NodeList getChildNodes() {

		DOMNodeList _children = new DOMNodeList();

		_children.add(getFirstChild());
		_children.addAll(DOMNode.super.getChildNodes());

		return _children;
	}

	default void increaseVersion() throws FrameworkException {

		final Integer _version = getProperty(Page.version);

		unlockReadOnlyPropertiesOnce();
		if (_version == null) {

			setProperties(getSecurityContext(), new PropertyMap(Page.version, 1));

		} else {

			setProperties(getSecurityContext(), new PropertyMap(Page.version, _version + 1));
		}
	}

	@Override
	default Element createElement(final String tag) throws DOMException {
		return createElement(tag, false);
	}

	default Element createElement (final String tag, final boolean suppressException) {

		final String elementType = StringUtils.capitalize(tag);
		final App app            = StructrApp.getInstance(getSecurityContext());
		String c                 = Content.class.getSimpleName();

		// Avoid creating an (invalid) 'Content' DOMElement
		if (elementType == null || c.equals(elementType)) {

			logger.warn("Blocked attempt to create a DOMElement of type {}", c);

			return null;

		}

		final Page _page = this;

		// create new content element
		DOMElement element;

		try {

			final Class entityClass = Class.forName("org.structr.web.entity.html." + elementType);
			if (entityClass != null) {

				element = (DOMElement) app.create(entityClass,
					new NodeAttribute(DOMElement.tag, tag),
					new NodeAttribute(DOMElement.hideOnDetail, false),
					new NodeAttribute(DOMElement.hideOnIndex, false)
				);
				element.doAdopt(_page);

				return element;
			}

		} catch (Throwable t) {
			if (!suppressException) {
				logger.error("Unable to instantiate element of type " + elementType, t);
			}
		}

		return null;

	}

	@Override
	default void handleNewChild(Node newChild) {

		for (final DOMNode child : getAllChildNodes()) {

			try {

				child.setProperties(getSecurityContext(), new PropertyMap(ownerDocument, this));

			} catch (FrameworkException ex) {
				logger.warn("", ex);
			}

		}

	}

	@Override
	default DocumentFragment createDocumentFragment() {

		final SecurityContext securityContext = getSecurityContext();
		final App app                         = StructrApp.getInstance(securityContext);

		try {

			// create new content element
			org.structr.web.entity.dom.DocumentFragment fragment = app.create(org.structr.web.entity.dom.DocumentFragment.class);

			// create relationship from ownerDocument to new text element
			((RelationProperty<DOMNode>) Page.elements).addSingleElement(securityContext, Page.this, fragment);
			// Page.elements.createRelationship(securityContext, Page.this, fragment);

			return fragment;

		} catch (FrameworkException fex) {

			// FIXME: what to do with the exception here?
			logger.warn("", fex);
		}

		return null;

	}

	@Override
	default Text createTextNode(final String text) {

		final SecurityContext securityContext = getSecurityContext();

		try {

			// create new content element
			Content content = (Content) StructrApp.getInstance(securityContext).command(CreateNodeCommand.class).execute(
				new NodeAttribute(AbstractNode.type, Content.class.getSimpleName()),
				new NodeAttribute(Content.hideOnDetail, false),
				new NodeAttribute(Content.hideOnIndex, false),
				new NodeAttribute(Content.content, text)
			);

			// create relationship from ownerDocument to new text element
			((RelationProperty<DOMNode>) Page.elements).addSingleElement(securityContext, Page.this, content);
			//Page.elements.createRelationship(securityContext, Page.this, content);

			return content;

		} catch (FrameworkException fex) {

			// FIXME: what to do with the exception here?
			logger.warn("", fex);
		}

		return null;
	}

	@Override
	default Comment createComment(String comment) {

		final SecurityContext securityContext = getSecurityContext();

		try {

			// create new content element
			org.structr.web.entity.dom.Comment commentNode = (org.structr.web.entity.dom.Comment) StructrApp.getInstance(securityContext).command(CreateNodeCommand.class).execute(
				new NodeAttribute(AbstractNode.type, org.structr.web.entity.dom.Comment.class.getSimpleName()),
				new NodeAttribute(Content.content, comment)
			);

			// create relationship from ownerDocument to new text element
			((RelationProperty<DOMNode>) Page.elements).addSingleElement(securityContext, Page.this, commentNode);
			//Page.elements.createRelationship(securityContext, Page.this, content);

			return commentNode;

		} catch (FrameworkException fex) {

			// FIXME: what to do with the exception here?
			logger.warn("", fex);
		}

		return null;
	}

	@Override
	default CDATASection createCDATASection(String string) throws DOMException {

		final SecurityContext securityContext = getSecurityContext();

		try {

			// create new content element
			Cdata content = (Cdata) StructrApp.getInstance(securityContext).command(CreateNodeCommand.class).execute(
				new NodeAttribute(AbstractNode.type, Cdata.class.getSimpleName())
			);

			// create relationship from ownerDocument to new text element
			((RelationProperty<DOMNode>) Page.elements).addSingleElement(securityContext, Page.this, content);
			//Page.elements.createRelationship(securityContext, Page.this, content);

			return content;

		} catch (FrameworkException fex) {

			// FIXME: what to do with the exception here?
			logger.warn("", fex);
		}

		return null;
	}

	@Override
	default ProcessingInstruction createProcessingInstruction(String string, String string1) throws DOMException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	default Attr createAttribute(String name) throws DOMException {
		return new DOMAttribute(this, null, name, null);
	}

	@Override
	default EntityReference createEntityReference(String string) throws DOMException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	default Element createElementNS(String string, String string1) throws DOMException {
		throw new UnsupportedOperationException("Namespaces not supported");
	}

	@Override
	default Attr createAttributeNS(String string, String string1) throws DOMException {
		throw new UnsupportedOperationException("Namespaces not supported");
	}

	@Override
	default Node importNode(final Node node, final boolean deep) throws DOMException {
		return importNode(node, deep, true);
	}

	@Override
	default Node adoptNode(Node node) throws DOMException {
		return adoptNode(node, true);
	}

	default Node adoptNode(final Node node, final boolean removeParentFromSourceNode) throws DOMException {

		if (node instanceof DOMNode) {

			final DOMNode domNode = (DOMNode) node;

			// step 2: do recursive import?
			if (domNode.hasChildNodes()) {

				Node child = domNode.getFirstChild();
				while (child != null) {

					// do not remove parent for child nodes
					adoptNode(child, false);
					child = child.getNextSibling();
				}

			}

			// step 3: remove node from its current parent
			// (Note that this step needs to be done last in
			// (order for the child to be able to find its
			// siblings.)
			if (removeParentFromSourceNode) {

				// only do this for the actual source node, do not remove
				// child nodes from its parents
				Node _parent = domNode.getParentNode();
				if (_parent != null) {
					_parent.removeChild(domNode);
				}
			}

			// step 1: use type-specific adopt impl.
			Node adoptedNode = domNode.doAdopt(Page.this);

			return adoptedNode;

		}

		return null;
	}

	@Override
	default void normalizeDocument() {
		normalize();
	}

	@Override
	default Node renameNode(Node node, String string, String string1) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, NOT_SUPPORTED_ERR_MESSAGE_RENAME);
	}

	@Override
	default DocumentType createDocumentType(String string, String string1, String string2) throws DOMException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	default Document createDocument(String string, String string1, DocumentType dt) throws DOMException {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	/**
	 * Return the content of this page depending on edit mode
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

	@Override
	default short getNodeType() {

		return Element.DOCUMENT_NODE;
	}

	@Override
	default DOMImplementation getImplementation() {

		return this;
	}

	@Override
	default Element getDocumentElement() {

		Node node = DOMNode.super.getFirstChild();

		if (node instanceof Element) {

			return (Element) node;

		} else {

			return null;
		}
	}

	@Override
	default Element getElementById(final String id) {

		DOMNodeList results = new DOMNodeList();

		collectNodesByPredicate(this, results, new Predicate<Node>() {

			@Override
			public boolean accept(Node obj) {

				if (obj instanceof DOMElement) {

					DOMElement elem = (DOMElement) obj;

					if (id.equals(elem.getProperty(DOMElement._id))) {
						return true;
					}
				}

				return false;
			}

		}, 0, true);

		// return first result
		if (results.getLength() == 1) {
			return (DOMElement) results.item(0);
		}

		return null;
	}

	@Override
	default String getInputEncoding() {
		return null;
	}

	@Override
	default String getXmlEncoding() {
		return "UTF-8";
	}

	@Override
	default boolean getXmlStandalone() {
		return true;
	}

	@Override
	default String getXmlVersion() {
		return "1.0";
	}

	@Override
	default boolean getStrictErrorChecking() {
		return true;
	}

	@Override
	default String getDocumentURI() {
		return null;
	}

	@Override
	default DOMConfiguration getDomConfig() {
		return null;
	}

	@Override
	default DocumentType getDoctype() {
		return new Html5DocumentType(this);
	}

	@Override
	default void render(RenderContext renderContext, int depth) throws FrameworkException {

		renderContext.setPage(this);

		// Skip DOCTYPE node
		DOMNode subNode = (DOMNode) this.getFirstChild().getNextSibling();

		if (subNode == null) {

			subNode = (DOMNode) DOMNode.super.getFirstChild();


		} else {

			renderContext.getBuffer().append("<!DOCTYPE html>\n");

		}

		while (subNode != null) {

			if (subNode.isNotDeleted() && getSecurityContext().isVisible(subNode)) {

				subNode.render(renderContext, depth);
			}

			subNode = (DOMNode) subNode.getNextSibling();

		}

	}

	@Override
	default void renderContent(final RenderContext renderContext, final int depth) throws FrameworkException {
	}

	@Override
	default boolean isSynced() {
		return false;
	}

	@Override
	default void setXmlStandalone(boolean bln) throws DOMException {
	}

	@Override
	default void setXmlVersion(String string) throws DOMException {
	}

	@Override
	default void setStrictErrorChecking(boolean bln) {
	}

	@Override
	default void setDocumentURI(String string) {
	}

	// ----- interface org.w3c.dom.Node -----
	@Override
	default String getLocalName() {
		return null;
	}

	@Override
	default String getNodeName() {
		return "#document";
	}

	@Override
	default String getNodeValue() throws DOMException {
		return null;
	}

	@Override
	default void setNodeValue(String string) throws DOMException {
	}

	@Override
	default NamedNodeMap getAttributes() {
		return null;
	}

	@Override
	default boolean hasAttributes() {
		return false;
	}

	@Override
	default NodeList getElementsByTagName(final String tagName) {

		DOMNodeList results = new DOMNodeList();

		collectNodesByPredicate(this, results, new Predicate<Node>() {

			@Override
			public boolean accept(Node obj) {

				if (obj instanceof DOMElement) {

					DOMElement elem = (DOMElement) obj;

					if (tagName.equals(elem.getProperty(DOMElement.tag))) {
						return true;
					}
				}

				return false;
			}

		}, 0, false);

		return results;
	}

	@Override
	default NodeList getElementsByTagNameNS(String string, String tagName) {
		throw new UnsupportedOperationException("Namespaces not supported.");
	}

	// ----- interface DOMAdoptable -----
	@Override
	default Node doAdopt(Page page) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, NOT_SUPPORTED_ERR_MESSAGE_ADOPT_DOC);
	}

	// ----- interface DOMImportable -----
	@Override
	default Node doImport(Page newPage) throws DOMException {
		throw new DOMException(DOMException.NOT_SUPPORTED_ERR, NOT_SUPPORTED_ERR_MESSAGE_IMPORT_DOC);
	}

	@Override
	default String getPath() {
		return getProperty(path);
	}

	// ----- diff methods -----
	@Export
	default void diff(final String file) throws FrameworkException {

		final SecurityContext securityContext = getSecurityContext();
		final App app                         = StructrApp.getInstance(securityContext);

		try (final Tx tx = app.tx()) {

			final FileInputStream fis                             = new FileInputStream(file);
			final String source                                   = IOUtils.toString(fis, "utf-8");
			final Page diffPage                                   = Importer.parsePageFromSource(securityContext, source, this.getProperty(Page.name) + "diff");
			final List<InvertibleModificationOperation> changeSet = new LinkedList<>();

			// close input stream after use
			fis.close();

			// build change set
			changeSet.addAll(Importer.diffNodes(this, diffPage));

			for (final InvertibleModificationOperation op : changeSet) {

				System.out.println(op);

				op.apply(app, this, diffPage);
			}

			// delete remaining children
			for (final DOMNode child : diffPage.getProperty(Page.elements)) {
				app.delete(child);
			}

			// delete imported page
			app.delete(diffPage);

			tx.success();

		} catch (Throwable t) {
			logger.warn("", t);
		}
	}

	// ----- private methods -----
	default Node importNode(final Node node, final boolean deep, final boolean removeParentFromSourceNode) throws DOMException {

		if (node instanceof DOMNode) {

			final DOMNode domNode = (DOMNode) node;

			// step 1: use type-specific import impl.
			Node importedNode = domNode.doImport(Page.this);

			// step 2: do recursive import?
			if (deep && domNode.hasChildNodes()) {

				// FIXME: is it really a good idea to do the
				// recursion inside of a transaction?
				Node child = domNode.getFirstChild();

				while (child != null) {

					// do not remove parent for child nodes
					importNode(child, deep, false);
					child = child.getNextSibling();

					logger.info("sibling is {}", child);
				}

			}

			// step 3: remove node from its current parent
			// (Note that this step needs to be done last in
			// (order for the child to be able to find its
			// siblings.)
			if (removeParentFromSourceNode) {

				// only do this for the actual source node, do not remove
				// child nodes from its parents
				Node _parent = domNode.getParentNode();
				if (_parent != null) {
					_parent.removeChild(domNode);
				}
			}

			return importedNode;

		}

		return null;
	}
}
