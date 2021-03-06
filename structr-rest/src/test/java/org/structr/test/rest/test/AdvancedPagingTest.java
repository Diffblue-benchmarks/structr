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
package org.structr.test.rest.test;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.filter.log.ResponseLoggingFilter;
import java.util.LinkedList;
import java.util.List;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import org.testng.annotations.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.error.FrameworkException;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyMap;
import org.structr.test.rest.common.StructrRestTestBase;
import org.structr.test.rest.entity.TestFive;
import org.structr.test.rest.entity.TestOne;
import org.structr.test.rest.entity.TestThree;
import org.testng.Assert;

/**
 *
 *
 */
public class AdvancedPagingTest extends StructrRestTestBase {

	private static final Logger logger = LoggerFactory.getLogger(AdvancedPagingTest.class.getName());

	@Test
	public void test01Paging() {

		// create a root object
		String resource = "/test_twos";

		String location = RestAssured.given().contentType("application/json; charset=UTF-8")
			.body(" { 'name' : 'TestTwo-0', 'anInt' : 0, 'aLong' : 0, 'aDate' : '2012-09-18T00:33:12+0200' } ")
			.expect().statusCode(201).when().post(resource).getHeader("Location");

		String baseId = getUuidFromLocation(location);

		resource = resource.concat("/").concat(baseId).concat("/test_ones");

		// create sub objects
		for (int i=0; i<10; i++) {

			location = RestAssured.given().contentType("application/json; charset=UTF-8")
			.body(" { 'name' : 'TestOne-" + i + "', 'anInt' : " + i + ", 'aLong' : " + i + ", 'aDate' : '2012-09-18T0" + i + ":33:12+0200' } ")
			.expect().statusCode(201).when().post(resource).getHeader("Location");

			String id = getUuidFromLocation(location);

			System.out.println("Object created: " + id);

		}

		resource = "/test_ones";

		for (int page=1; page<5; page++) {

			String url = resource + "?sort=name&pageSize=2&page=" + page;

			System.out.println("Testing page " + page + " with URL " + url);

			RestAssured

				.given()
					.contentType("application/json; charset=UTF-8")
					.filter(ResponseLoggingFilter.logResponseTo(System.out))
				.expect()
					.statusCode(200)
					.body("result",			hasSize(2))
					.body("result_count",		equalTo(10))

					.body("result[0]",		isEntity(TestOne.class))
					.body("result[0].name ",	equalTo("TestOne-" + ((2*page)-2)))

					.body("result[1]",		isEntity(TestOne.class))
					.body("result[1].name ",	equalTo("TestOne-" + ((2*page)-1)))

				.when()
					.get(url);

		}

	}


	/**
	 * Paging of subresources
	 */
	@Test
	public void test02PagingOfSubresources() {

		// create a root object

		String resource = "/test_twos";

		String location = RestAssured.given().contentType("application/json; charset=UTF-8")
			.body(" { 'name' : 'TestTwo-0', 'anInt' : 0, 'aLong' : 0, 'aDate' : '2012-09-18T00:33:12+0200' } ")
			.expect().statusCode(201).when().post(resource).getHeader("Location");

		String baseId = getUuidFromLocation(location);

		resource = resource.concat("/").concat(baseId).concat("/test_ones");

		// create sub objects
		for (int i=0; i<10; i++) {

			location = RestAssured.given().contentType("application/json; charset=UTF-8")
			.body(" { 'name' : 'TestOne-" + i + "', 'anInt' : " + i + ", 'aLong' : " + i + ", 'aDate' : '2012-09-18T0" + i + ":33:12+0200' } ")
			.expect().statusCode(201).when().post(resource).getHeader("Location");

			String id = getUuidFromLocation(location);

		}

		resource = "/test_twos/" + baseId + "/test_ones";

		for (int page=1; page<5; page++) {

			String url = resource + "?sort=name&pageSize=2&page=" + page;

			RestAssured

				.given()
					.contentType("application/json; charset=UTF-8")
				.expect()
					.statusCode(200)
					.body("result",			hasSize(2))
					.body("result_count",		equalTo(10))

					.body("result[0]",		isEntity(TestOne.class))
					.body("result[0].name ",	equalTo("TestOne-" + ((2*page)-2)))

					.body("result[1]",		isEntity(TestOne.class))
					.body("result[1].name ",	equalTo("TestOne-" + ((2*page)-1)))

				.when()
					.get(url);

		}

	}

	@Test
	public void test03RangeHeader() {

		// create a root object

		final List<String> testOneIDs = new LinkedList<>();
		String resource               = "/test_twos";

		String location = RestAssured.given().contentType("application/json; charset=UTF-8")
			.body(" { 'name' : 'TestTwo-0', 'anInt' : 0, 'aLong' : 0, 'aDate' : '2012-09-18T00:33:12+0200' } ")
			.expect().statusCode(201).when().post(resource).getHeader("Location");

		String baseId = getUuidFromLocation(location);

		resource = resource.concat("/").concat(baseId).concat("/test_ones");

		// create sub objects
		for (int i=0; i<20; i++) {

			final String subLocation = RestAssured.given().contentType("application/json; charset=UTF-8")
				.body(" { 'name' : 'TestOne-" + i + "', 'anInt' : " + i + ", 'aLong' : " + i + ", 'aDate' : '2012-09-18T0" + i + ":33:12+0200' } ")
				.expect().statusCode(201).when().post(resource).getHeader("Location");

			testOneIDs.add(getUuidFromLocation(subLocation));
		}

		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.header("Range", "test_ones=0-3")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
			.expect()
				.statusCode(200)
				.body("result",                    hasSize(1))
				.body("result_count",              equalTo(1))

				.body("result[0]",                 isEntity(TestOne.class))
				.body("result[0].test_ones",       hasSize(3))
				.body("result[0].test_ones[0].id", equalTo(testOneIDs.get(0)))
				.body("result[0].test_ones[1].id", equalTo(testOneIDs.get(1)))
				.body("result[0].test_ones[2].id", equalTo(testOneIDs.get(2)))

			.when()
				.get("/test_twos");


		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.header("Range", "test_ones=3-6")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
			.expect()
				.statusCode(200)
				.body("result",                    hasSize(1))
				.body("result_count",              equalTo(1))

				.body("result[0]",                 isEntity(TestOne.class))
				.body("result[0].test_ones",       hasSize(3))
				.body("result[0].test_ones[0].id", equalTo(testOneIDs.get(3)))
				.body("result[0].test_ones[1].id", equalTo(testOneIDs.get(4)))
				.body("result[0].test_ones[2].id", equalTo(testOneIDs.get(5)))

			.when()
				.get("/test_twos");


		RestAssured

			.given()
				.contentType("application/json; charset=UTF-8")
				.header("Range", "test_ones=10-20")
				.filter(ResponseLoggingFilter.logResponseIfStatusCodeIs(200))
			.expect()
				.statusCode(200)
				.body("result",                    hasSize(1))
				.body("result_count",              equalTo(1))

				.body("result[0]",                 isEntity(TestOne.class))
				.body("result[0].test_ones",       hasSize(10))
				.body("result[0].test_ones[0].id", equalTo(testOneIDs.get(10)))
				.body("result[0].test_ones[1].id", equalTo(testOneIDs.get(11)))
				.body("result[0].test_ones[2].id", equalTo(testOneIDs.get(12)))
				.body("result[0].test_ones[3].id", equalTo(testOneIDs.get(13)))
				.body("result[0].test_ones[4].id", equalTo(testOneIDs.get(14)))
				.body("result[0].test_ones[5].id", equalTo(testOneIDs.get(15)))
				.body("result[0].test_ones[6].id", equalTo(testOneIDs.get(16)))
				.body("result[0].test_ones[7].id", equalTo(testOneIDs.get(17)))
				.body("result[0].test_ones[8].id", equalTo(testOneIDs.get(18)))
				.body("result[0].test_ones[9].id", equalTo(testOneIDs.get(19)))

			.when()
				.get("/test_twos");


	}

	/**
	 * This tests the scenario that the pagesize is taken into account **before** the filter has been taken into account
	 * which results in less (or no) results being returned.
	 */
	@Test
	public void testPagingWithPageSizeAndFilterOnRemoteObject() {

		/* Test Setup */
		final String connectedNodeName    = "Test3-First-Connected";
		final String notConnectedNodeName = "Test3-Second";
		TestThree t3_connected            = null;
		TestThree t3_not_connected        = null;
		TestFive  t5                      = null;

		try (final Tx tx = app.tx(true, false, false)) {

			t3_connected     = app.create(TestThree.class, connectedNodeName);
			t3_not_connected = app.create(TestThree.class, notConnectedNodeName);

			final PropertyMap t5Map = new PropertyMap(TestFive.oneToOneTestThree, t3_connected);
			t5 = app.create(TestFive.class, t5Map);

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
		}

		Assert.assertNotNull(t3_connected);
		Assert.assertNotNull(t3_not_connected);
		Assert.assertNotNull(t5);

		/* Test 1: Test that we created two TestThrees */
		RestAssured
			.given()
				.contentType("application/json; charset=UTF-8")
			.expect()
				.statusCode(200)
				.body("result",        hasSize(2))
				.body("result_count",  equalTo(2))
			.when()
				.get("/test_threes");

		/* Test 2: Test that we created one TestFive */
		RestAssured
			.given()
				.contentType("application/json; charset=UTF-8")
			.expect()
				.statusCode(200)
				.body("result",       hasSize(1))
				.body("result_count", equalTo(1))
			.when()
				.get("/test_fives");

		/* Test 3: Test that we can correctly search for objects **without** a connection to another node */
		RestAssured
			.given()
				.contentType("application/json; charset=UTF-8")
			.expect()
				.statusCode(200)
				.body("result",         hasSize(1))
				.body("result[0].name", equalTo(notConnectedNodeName))
				.body("result[0].id",   equalTo(t3_not_connected.getUuid()))
			.when()
				.get("/test_threes?oneToOneTestFive=null");


		/* Test 4: Test that we can correctly search for objects **without** a connection to another node WHILE also reducing pagesize to 1	*/
		RestAssured
			.given()
				.contentType("application/json; charset=UTF-8")
				.filter(ResponseLoggingFilter.logResponseTo(System.out))
			.expect()
				.statusCode(200)
				.body("result",         hasSize(1))
				.body("result[0].name", equalTo(notConnectedNodeName))
				.body("result[0].id",   equalTo(t3_not_connected.getUuid()))
			.when()
				.get("/test_threes?oneToOneTestFive=null&pageSize=1");

	}

}
