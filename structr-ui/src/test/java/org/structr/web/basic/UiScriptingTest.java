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
package org.structr.web.basic;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import org.apache.commons.lang.StringUtils;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.AccessMode;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Group;
import org.structr.core.entity.SchemaNode;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.StringProperty;
import org.structr.core.script.Scripting;
import org.structr.schema.export.StructrSchema;
import org.structr.schema.json.JsonSchema;
import org.structr.web.StructrUiTest;
import org.structr.web.auth.UiAuthenticator;
import org.structr.web.common.RenderContext;
import org.structr.web.entity.Folder;
import org.structr.web.entity.TestOne;
import org.structr.web.entity.User;
import org.structr.web.entity.dom.Content;
import org.structr.web.entity.dom.DOMElement;
import org.structr.web.entity.dom.DOMNode;
import org.structr.web.entity.dom.Page;
import org.structr.web.entity.dom.ShadowDocument;
import org.structr.web.entity.dom.Template;
import org.structr.web.entity.html.Div;
import org.structr.web.entity.html.Table;
import org.structr.websocket.command.CreateComponentCommand;

/**
 *
 *
 */
public class UiScriptingTest extends StructrUiTest {

	private static final Logger logger = LoggerFactory.getLogger(UiScriptingTest.class.getName());

	@Test
	public void testSingleRequestParameters() {

		try (final Tx tx = app.tx()) {

			Page page         = (Page) app.create(Page.class, new NodeAttribute(Page.name, "test"), new NodeAttribute(Page.visibleToPublicUsers, true));
			Template template = (Template) app.create(Template.class, new NodeAttribute(Page.visibleToPublicUsers, true));
			template.setProperty(Template.content, "${request.param}");

			page.appendChild(template);

			tx.success();

		} catch (FrameworkException fex) {

			fail("Unexpected exception");
		}


		try (final Tx tx = app.tx()) {

			RestAssured
			.given()
				//.headers("X-User", "admin" , "X-Password", "admin")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
				.statusCode(200)
				.body(equalTo("a"))
			.when()
				.get("http://localhost:8875/test?param=a");

			tx.success();

		} catch (FrameworkException fex) {

			fail("Unexpected exception");
		}
	}

	@Test
	public void testMultiRequestParameters() {

		try (final Tx tx = app.tx()) {

			Page page         = (Page) app.create(Page.class, new NodeAttribute(Page.name, "test"), new NodeAttribute(Page.visibleToPublicUsers, true));
			Template template = (Template) app.create(Template.class, new NodeAttribute(Page.visibleToPublicUsers, true));
			template.setProperty(Template.content, "${each(request.param, print(data))}");

			page.appendChild(template);

			tx.success();

		} catch (FrameworkException fex) {

			fail("Unexpected exception");
		}


		try (final Tx tx = app.tx()) {

			RestAssured
			.given()
				//.headers("X-User", "admin" , "X-Password", "admin")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
				.statusCode(200)
				.body(equalTo("abc"))
			.when()
				.get("http://localhost:8875/test?param=a&param=b&param=c");

			tx.success();

		} catch (FrameworkException fex) {

			fail("Unexpected exception");
		}
	}

	@Test
	public void testScripting() {

		NodeInterface detailsDataObject = null;
		Page page                       = null;
		DOMNode html                    = null;
		DOMNode head                    = null;
		DOMNode body                    = null;
		DOMNode title                   = null;
		DOMNode div                    = null;
		DOMNode p                      = null;
		DOMNode text                    = null;

		try (final Tx tx = app.tx()) {

			detailsDataObject = app.create(TestOne.class, "TestOne");
			page              = Page.createNewPage(securityContext, "testpage");

			page.setProperties(page.getSecurityContext(), new PropertyMap(Page.visibleToPublicUsers, true));

			assertTrue(page != null);
			assertTrue(page instanceof Page);

			html  = (DOMNode) page.createElement("html");
			head  = (DOMNode) page.createElement("head");
			body  = (DOMNode) page.createElement("body");
			title = (DOMNode) page.createElement("title");
			div   = (DOMNode) page.createElement("div");
			p     = (DOMNode) page.createElement("p");
			text  = (DOMNode) page.createTextNode("x");

			// add HTML element to page
			page.appendChild(html);

			// add HEAD and BODY elements to HTML
			html.appendChild(head);
			html.appendChild(body);

			// add TITLE element to HEAD
			head.appendChild(title);

			body.appendChild(div);
			div.appendChild(p);

			final PropertyMap changedProperties = new PropertyMap();
			changedProperties.put(DOMElement.restQuery, "/divs");
			changedProperties.put(DOMElement.dataKey, "div");
			p.setProperties(p.getSecurityContext(), changedProperties);

			p.appendChild(text);

			tx.success();

		} catch (FrameworkException fex) {

			fail("Unexpected exception");
		}

		try (final Tx tx = app.tx()) {

			final RenderContext ctx = new RenderContext(securityContext, new RequestMockUp(), new ResponseMockUp(), RenderContext.EditMode.NONE);
			ctx.setDetailsDataObject(detailsDataObject);
			ctx.setPage(page);

			test(p, text, "${{ return Structr.get('div').id}}", "<p>" + div.getUuid() + "</p>", ctx);
			test(p, text, "${{ return Structr.get('page').id}}", "<p>" + page.getUuid() + "</p>", ctx);
			test(p, text, "${{ return Structr.get('parent').id}}", "<p>" + p.getUuid() + "</p>", ctx);


			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testCharset() {

		System.out.println("######### Charset settings ##############");
		System.out.println("Default Charset=" + Charset.defaultCharset());
		System.out.println("file.encoding=" + System.getProperty("file.encoding"));
		System.out.println("Default Charset=" + Charset.defaultCharset());
		System.out.println("Default Charset in Use=" + getEncodingInUse());
		System.out.println("This should look like the umlauts of 'a', 'o', 'u' and 'ss': äöüß");
		System.out.println("#########################################");

	}

	@Test
	public void testSpecialHeaders() {

		String uuid = null;

		// schema setup
		try (final Tx tx = app.tx()) {

			// create list of 100 folders
			final List<Folder> folders = new LinkedList<>();
			for (int i=0; i<100; i++) {

				folders.add(createTestNode(Folder.class, new NodeAttribute<>(AbstractNode.name, "Folder" + i)));
			}

			// create parent folder
			final Folder parent = createTestNode(Folder.class,
				new NodeAttribute<>(AbstractNode.name, "Parent"),
				new NodeAttribute<>(Folder.folders, folders)
			);

			uuid = parent.getUuid();

			// create function property that returns folder children
			final SchemaNode schemaNode = app.nodeQuery(SchemaNode.class).andName("Folder").getFirst();
			schemaNode.setProperty(new StringProperty("_testFunction"), "Function(this.folders)");

			// create admin user
			createTestNode(User.class,
				new NodeAttribute<>(User.name, "admin"),
				new NodeAttribute<>(User.password, "admin"),
				new NodeAttribute<>(User.isAdmin, true)
			);

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception");
		}

		RestAssured.basePath = "/structr/rest";
		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.accept("application/json; properties=id,type,name,folders,testFunction")
				.header("Range", "folders=0-10;testFunction=0-10")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(201))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
				.headers("X-User", "admin" , "X-Password", "admin")
			.expect()
				.statusCode(200)
				.body("result.folders",      Matchers.hasSize(10))
				.body("result.testFunction", Matchers.hasSize(10))
			.when()
				.get("/Folder/" + uuid + "/ui");
	}

	@Test
	public void testFunctionQueryWithJavaScriptAndRepeater() {

		try (final Tx tx = app.tx()) {

			final Page page       = Page.createSimplePage(securityContext, "test");
			final Div div         = (Div)page.getElementsByTagName("div").item(0);
			final Content content = (Content)div.getFirstChild();

			// setup repeater
			content.setProperty(DOMNode.functionQuery, "{ var arr = []; for (var i=0; i<10; i++) { arr.push({ name: 'test' + i }); }; return arr; }");
			content.setProperty(DOMNode.dataKey, "test");
			content.setProperty(Content.content, "${test.name}");

			// create admin user
			createTestNode(User.class,
				new NodeAttribute<>(User.name, "admin"),
				new NodeAttribute<>(User.password, "admin"),
				new NodeAttribute<>(User.isAdmin, true)
			);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		RestAssured.basePath = "/";

		// test successful basic auth
		RestAssured
			.given()
				.headers("X-User", "admin" , "X-Password", "admin")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
				.statusCode(200)
				.body("html.head.title", Matchers.equalTo("Test"))
				.body("html.body.h1", Matchers.equalTo("Test"))
				.body("html.body.div", Matchers.equalTo("test0test1test2test3test4test5test6test7test8test9"))
			.when()
			.get("/html/test");
	}

	@Test
	public void testIncludeWithRepeaterInJavaScript() {

		try (final Tx tx = app.tx()) {

			final Page page       = Page.createSimplePage(securityContext, "test");
			final Div div         = (Div)page.getElementsByTagName("div").item(0);
			final Content content = (Content)div.getFirstChild();

			// setup scripting repeater
			content.setProperty(Content.content, "${{ var arr = []; for (var i=0; i<10; i++) { arr.push({name: 'test' + i}); } Structr.include('item', arr, 'test'); }}");
			content.setProperty(Content.contentType, "text/html");

			// setup shared component with name "table" to include
			final ShadowDocument shadowDoc = CreateComponentCommand.getOrCreateHiddenDocument();

			final Div item    = (Div)shadowDoc.createElement("div");
			final Content txt = (Content)shadowDoc.createTextNode("${test.name}");

			item.setProperty(Table.name, "item");
			item.appendChild(txt);

			// create admin user
			createTestNode(User.class,
				new NodeAttribute<>(User.name, "admin"),
				new NodeAttribute<>(User.password, "admin"),
				new NodeAttribute<>(User.isAdmin, true)
			);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		RestAssured.basePath = "/";

		// test successful basic auth
		RestAssured
			.given()
				.headers("X-User", "admin" , "X-Password", "admin")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
				.statusCode(200)
				.body("html.head.title",      Matchers.equalTo("Test"))
				.body("html.body.h1",         Matchers.equalTo("Test"))
				.body("html.body.div.div[0]", Matchers.equalTo("test0"))
				.body("html.body.div.div[1]", Matchers.equalTo("test1"))
				.body("html.body.div.div[2]", Matchers.equalTo("test2"))
				.body("html.body.div.div[3]", Matchers.equalTo("test3"))
				.body("html.body.div.div[4]", Matchers.equalTo("test4"))
				.body("html.body.div.div[5]", Matchers.equalTo("test5"))
				.body("html.body.div.div[6]", Matchers.equalTo("test6"))
				.body("html.body.div.div[7]", Matchers.equalTo("test7"))
				.body("html.body.div.div[8]", Matchers.equalTo("test8"))
				.body("html.body.div.div[9]", Matchers.equalTo("test9"))
			.when()
			.get("/html/test");
	}

	@Test
	public void testRestQueryRepeater() {

		String uuid = null;

		try (final Tx tx = app.tx()) {

			final Page page       = Page.createSimplePage(securityContext, "test");
			final Div div         = (Div) page.getElementsByTagName("div").item(0);
			final Content content = (Content) div.getFirstChild();

			// setup scripting repeater
			content.setProperty(Content.restQuery, "/Page/${current.id}");
			content.setProperty(Content.dataKey, "test");
			content.setProperty(Content.content, "${test.id}");

			// store UUID for later use
			uuid = page.getUuid();

			// create admin user
			createTestNode(User.class,
				new NodeAttribute<>(User.name, "admin"),
				new NodeAttribute<>(User.password, "admin"),
				new NodeAttribute<>(User.isAdmin, true)
			);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		RestAssured.basePath = "/";

		// test successful basic auth
		RestAssured
			.given()
				.headers("X-User", "admin" , "X-Password", "admin")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
				.statusCode(200)
				.body("html.head.title", Matchers.equalTo("Test"))
				.body("html.body.h1",    Matchers.equalTo("Test"))
				.body("html.body.div",   Matchers.equalTo(uuid))
			.when()
			.get("/html/test/" + uuid);
	}


	@Test
	public void testRestQueryWithRemoteAttributeRepeater() {

		String uuid = null;

		try (final Tx tx = app.tx()) {

			final Page page       = Page.createSimplePage(securityContext, "test");
			final Div div         = (Div) page.getElementsByTagName("div").item(0);
			final Content content = (Content) div.getFirstChild();

			// Create second div without children
			Div div2 = (Div) div.cloneNode(false);
			div.getUuid();

			// setup scripting repeater to repeat over (non-existing) children of second div
			content.setProperty(Content.restQuery, "/Div/" + div2.getUuid()+ "/children");
			content.setProperty(Content.dataKey, "test");
			content.setProperty(Content.content, "foo${test}");

			// store UUID for later use
			uuid = page.getUuid();

			// create admin user
			createTestNode(User.class,
				new NodeAttribute<>(User.name, "admin"),
				new NodeAttribute<>(User.password, "admin"),
				new NodeAttribute<>(User.isAdmin, true)
			);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		RestAssured.basePath = "/";

		// test successful basic auth
		RestAssured
			.given()
				.headers("X-User", "admin" , "X-Password", "admin")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
				.statusCode(200)
				.body("html.head.title", Matchers.equalTo("Test"))
				.body("html.body.h1",    Matchers.equalTo("Test"))
				.body("html.body.div",   Matchers.equalTo(""))
			.when()
			.get("/html/test/" + uuid);
	}

	@Test
	public void testHttpGetFunction() {

		try (final Tx tx = app.tx()) {

			JsonSchema schema = StructrSchema.createFromDatabase(app);

			schema.addType("Test").addMethod("doTest", "${GET('http://localhost:8875/structr/rest/Test')}", "");

			StructrSchema.extendDatabaseSchema(app, schema);

			// create admin user
			createTestNode(User.class,
				new NodeAttribute<>(User.name, "admin"),
				new NodeAttribute<>(User.password, "admin"),
				new NodeAttribute<>(User.isAdmin, true)
			);

			tx.success();

		} catch (Exception ex) {
			logger.error("", ex);
		}

		RestAssured.basePath = "/structr/rest";

		final String uuid = createEntityAsSuperUser("/Test", "{name: test}");

		grant("Test", UiAuthenticator.NON_AUTH_USER_GET, true);

		// test successful basic auth
		RestAssured
			.given()
				.headers("X-User", "admin" , "X-Password", "admin")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(400))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(401))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(403))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(404))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(422))
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(500))
			.expect()
				.statusCode(200)
				//.body("html.head.title", Matchers.equalTo("Test"))
				//.body("html.body.h1",    Matchers.equalTo("Test"))
				//.body("html.body.div",   Matchers.equalTo(uuid))
			.when()
			.post("/Test/" + uuid + "/doTest");

	}

	@Test
	public void testDoPrivileged() {

		User tester = null;

		try (final Tx tx = app.tx()) {

			// create admin user
			createTestNode(User.class,
				new NodeAttribute<>(User.name, "admin"),
				new NodeAttribute<>(User.password, "admin"),
				new NodeAttribute<>(User.isAdmin, true)
			);

			// create test user
			tester = createTestNode(User.class,
				new NodeAttribute<>(User.name, "tester"),
				new NodeAttribute<>(User.password, "test")
			);

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		final String script1              =  "${{ return Structr.find('User', 'name', 'admin'); }}\n";
		final String script2              =  "${{ return Structr.doPrivileged(function() { return Structr.find('User', 'name', 'admin'); }); }}\n";
		final SecurityContext userContext = SecurityContext.getInstance(tester, AccessMode.Backend);
		final App app                     = StructrApp.getInstance(userContext);
		final RenderContext renderContext = new RenderContext(userContext, new RequestMockUp(), new ResponseMockUp(), RenderContext.EditMode.NONE);

		try (final Tx tx = app.tx()) {

			// unprivileged call
			final Object result = Scripting.evaluate(renderContext, null, script1, "test");

			assertEquals("Result is of invalid type",                   ArrayList.class, result.getClass());
			assertEquals("Script in user context should not see admin", 0, ((List)result).size());


			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			// doPrivileged call
			final Object result = Scripting.evaluate(renderContext, null, script2, "test");

			assertEquals("Result is of invalid type",              ArrayList.class, result.getClass());
			assertEquals("Privileged script should not see admin", 1, ((List)result).size());

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testGroupFunctions() {

		Group group = null;
		User tester = null;

		try (final Tx tx = app.tx()) {

			// create test user
			tester = createTestNode(User.class,
				new NodeAttribute<>(User.name, "tester"),
				new NodeAttribute<>(User.password, "test")
			);

			// create test group
			group = createTestNode(Group.class, new NodeAttribute<>(Group.name, "test"));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		final RenderContext renderContext = new RenderContext(securityContext, new RequestMockUp(), new ResponseMockUp(), RenderContext.EditMode.NONE);

		try (final Tx tx = app.tx()) {

			// check that the user is not in the group at first
			assertFalse("User should not be in the test group before testing", group.getProperty(Group.members).contains(tester));

			// check that is_in_group returns the correct result
			assertEquals("Function is_in_group should return false.", false, Scripting.evaluate(renderContext, null, "${is_in_group(first(find('Group')), first(find('User')))}", "test"));

			// add user to group
			Scripting.evaluate(renderContext, null, "${add_to_group(first(find('Group')), first(find('User')))}", "test");

			// check that the user is in the group after the call to add_to_group
			assertTrue("User should be in the test group now", group.getProperty(Group.members).contains(tester));

			// check that is_in_group returns the correct result
			assertEquals("Function is_in_group should return true.", true, Scripting.evaluate(renderContext, null, "${is_in_group(first(find('Group')), first(find('User')))}", "test"));

			// remove user from group
			Scripting.evaluate(renderContext, null, "${remove_from_group(first(find('Group')), first(find('User')))}", "test");

			// check that the user is not in the group any more after the call to remove_from_group
			assertFalse("User should not be in the test group before testing", group.getProperty(Group.members).contains(tester));

			// check that is_in_group returns the correct result
			assertEquals("Function is_in_group should return false.", false, Scripting.evaluate(renderContext, null, "${is_in_group(first(find('Group')), first(find('User')))}", "test"));

			tx.success();

		} catch (FrameworkException fex) {

			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	// ----- private methods -----
	private String getEncodingInUse() {
		OutputStreamWriter writer = new OutputStreamWriter(new ByteArrayOutputStream());
		return writer.getEncoding();
	}

	private void test(final DOMNode p, final DOMNode text, final String content, final String expected, final RenderContext context) throws FrameworkException {

		text.setTextContent(content);

		// clear queue
		context.getBuffer().getQueue().clear();
		p.render(context, 0);

		assertEquals("Invalid JavaScript evaluation result", expected, concat(context.getBuffer().getQueue()));
	}

	private String concat(final Queue<String> queue) {

		StringBuilder buf = new StringBuilder();

		for (final String str : queue) {

			final String trimmed = str.trim();
			if (StringUtils.isNotBlank(trimmed)) {

				buf.append(trimmed);
			}
		}

		return buf.toString();
	}

	public class RequestMockUp implements HttpServletRequest {

		@Override
		public String getAuthType() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Cookie[] getCookies() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public long getDateHeader(String name) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getHeader(String name) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public int getIntHeader(String name) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getMethod() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getPathInfo() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getPathTranslated() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getContextPath() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getQueryString() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getRemoteUser() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public boolean isUserInRole(String role) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public java.security.Principal getUserPrincipal() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getRequestedSessionId() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getRequestURI() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public StringBuffer getRequestURL() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getServletPath() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public HttpSession getSession(boolean create) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public HttpSession getSession() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String changeSessionId() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public boolean isRequestedSessionIdValid() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public boolean isRequestedSessionIdFromCookie() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public boolean isRequestedSessionIdFromURL() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public boolean isRequestedSessionIdFromUrl() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void login(String username, String password) throws ServletException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void logout() throws ServletException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Collection<Part> getParts() throws IOException, ServletException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Part getPart(String name) throws IOException, ServletException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Object getAttribute(String name) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Enumeration<String> getAttributeNames() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getCharacterEncoding() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public int getContentLength() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public long getContentLengthLong() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getContentType() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getParameter(String name) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Enumeration<String> getParameterNames() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String[] getParameterValues(String name) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Map<String, String[]> getParameterMap() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getProtocol() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getScheme() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getServerName() {
			return "localhost";
		}

		@Override
		public int getServerPort() {
			return 12345;
		}

		@Override
		public BufferedReader getReader() throws IOException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getRemoteAddr() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getRemoteHost() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setAttribute(String name, Object o) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void removeAttribute(String name) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Locale getLocale() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Enumeration<Locale> getLocales() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public boolean isSecure() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public RequestDispatcher getRequestDispatcher(String path) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getRealPath(String path) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public int getRemotePort() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getLocalName() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getLocalAddr() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public int getLocalPort() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public ServletContext getServletContext() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public AsyncContext startAsync() throws IllegalStateException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public boolean isAsyncStarted() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public boolean isAsyncSupported() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public AsyncContext getAsyncContext() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public DispatcherType getDispatcherType() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}
	}

	public class ResponseMockUp implements HttpServletResponse {

		@Override
		public void addCookie(Cookie cookie) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public boolean containsHeader(String name) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String encodeURL(String url) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String encodeRedirectURL(String url) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String encodeUrl(String url) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String encodeRedirectUrl(String url) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void sendError(int sc, String msg) throws IOException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void sendError(int sc) throws IOException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void sendRedirect(String location) throws IOException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setDateHeader(String name, long date) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void addDateHeader(String name, long date) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setHeader(String name, String value) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void addHeader(String name, String value) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setIntHeader(String name, int value) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void addIntHeader(String name, int value) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setStatus(int sc) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setStatus(int sc, String sm) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public int getStatus() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getHeader(String name) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Collection<String> getHeaders(String name) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Collection<String> getHeaderNames() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getCharacterEncoding() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getContentType() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public ServletOutputStream getOutputStream() throws IOException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public PrintWriter getWriter() throws IOException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setCharacterEncoding(String charset) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setContentLength(int len) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setContentLengthLong(long len) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setContentType(String type) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setBufferSize(int size) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public int getBufferSize() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void flushBuffer() throws IOException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void resetBuffer() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public boolean isCommitted() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void reset() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setLocale(Locale loc) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Locale getLocale() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}
	}
}
