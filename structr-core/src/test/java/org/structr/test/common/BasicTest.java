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
package org.structr.test.common;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.testng.annotations.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.api.NotFoundException;
import org.structr.api.NotInTransactionException;
import org.structr.api.util.Iterables;
import org.structr.common.AccessMode;
import org.structr.common.GraphObjectComparator;
import org.structr.common.RelType;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.entity.GenericNode;
import org.structr.core.entity.Group;
import org.structr.test.core.entity.OneThreeOneToOne;
import org.structr.test.core.entity.OneTwoOneToOne;
import org.structr.core.entity.Principal;
import org.structr.core.entity.Relation;
import org.structr.core.entity.SchemaNode;
import org.structr.core.entity.SchemaRelationshipNode;
import org.structr.core.entity.Security;
import org.structr.test.core.entity.SixOneManyToMany;
import org.structr.test.core.entity.SixOneOneToOne;
import org.structr.test.core.entity.SixThreeOneToMany;
import org.structr.test.core.entity.TestFour;
import org.structr.test.core.entity.TestOne;
import org.structr.test.core.entity.TestSix;
import org.structr.test.core.entity.TestTen;
import org.structr.test.core.entity.TestThirteen;
import org.structr.test.core.entity.TestThree;
import org.structr.test.core.entity.TestTwo;
import org.structr.core.entity.relationship.NodeHasLocation;
import org.structr.core.entity.relationship.PrincipalOwnsNode;
import org.structr.core.graph.NodeAttribute;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.NodeServiceCommand;
import org.structr.core.graph.RelationshipInterface;
import org.structr.core.graph.Tx;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.StringProperty;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

/**
 *
 */
public class BasicTest extends StructrTest {

	private static final Logger logger = LoggerFactory.getLogger(BasicTest.class);

	@Test
	public void test00SimpleCreateOperation() {

		try (final Tx tx = app.tx()) {

			final PropertyMap properties = new PropertyMap();

			properties.put(TestSix.name, "name");

			// test null value for a 1:1 related property
			properties.put(TestSix.oneToOneTestThree, null);

			app.create(TestSix.class, properties);

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception");
		}

		try (final Tx tx = app.tx()) {

			final TestSix test = app.nodeQuery(TestSix.class).getFirst();

			assertNotNull("Invalid simple object creation result", test);
			assertEquals("Invalid simple object creation result", "name", test.getProperty(AbstractNode.name));
			assertEquals("Invalid simple object creation result", null,   test.getProperty(TestSix.oneToOneTestThree));

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception");
		}
	}

	/**
	 * Test successful deletion of a node.
	 *
	 * The node shouldn't be found afterwards.
	 * Creation and deletion are executed in two different transactions.
	 *
	 */
	@Test
	public void test01DeleteNode() {

		try {

			final PropertyMap props = new PropertyMap();
			final String type       = "GenericNode";
			final String name       = "GenericNode-name";
			NodeInterface node      = null;
			String uuid             = null;

			props.put(AbstractNode.type, type);
			props.put(AbstractNode.name, name);

			try (final Tx tx = app.tx()) {

				node = app.create(GenericNode.class, props);
				tx.success();
			}

			assertTrue(node != null);

			try (final Tx tx = app.tx()) {
				uuid = node.getUuid();
			}

			try (final Tx tx = app.tx()) {

				app.delete(node);
				tx.success();
			}

			try (final Tx tx = app.tx()) {

				List result = app.nodeQuery().uuid(uuid).getAsList();

				assertEquals("Node should have been deleted", 0, result.size());

			} catch (FrameworkException fe) {}

		} catch (FrameworkException ex) {

			logger.warn("", ex);

			logger.error(ex.toString());
			fail("Unexpected exception");

		}

	}

	@Test
	public void test01DeleteRelationship() {

		try {

			final TestOne testOne  = createTestNode(TestOne.class);
			final TestSix testSix  = createTestNode(TestSix.class);
			SixOneOneToOne rel     = null;

			assertNotNull(testOne);
			assertNotNull(testSix);

			try (final Tx tx = app.tx()) {

				rel = app.create(testSix, testOne, SixOneOneToOne.class);
				tx.success();
			}

			assertNotNull(rel);

			try {
				// try to delete relationship
				rel.getRelationship().delete(true);

				fail("Should have raised an org.neo4j.graphdb.NotInTransactionException");
			} catch (NotInTransactionException e) {}

			// Relationship still there
			assertNotNull(rel);

			try (final Tx tx = app.tx()) {

				app.delete(rel);
				tx.success();
			}

			try (final Tx tx = app.tx()) {

				String uuid = rel.getUuid();
				fail("Deleted entity should have thrown an exception on access.");

			} catch (NotFoundException iex) {
			}


		} catch (FrameworkException ex) {

			logger.warn("", ex);

			logger.error(ex.toString());
			fail("Unexpected exception");

		}


	}

	/**
	 * DELETE_NONE should not trigger any delete cascade
	 */
	@Test
	public void test03CascadeDeleteNone() {

		/* this test is flawed in that it expects the cascading
		 * not to take place but expects to run without an
		 * exception, but deleting one node of the two will leave
		 * the other (TestTwo) in an invalid state according
		 * to its isValid() method!
		try {

			// Create a relationship with DELETE_NONE
			AbstractRelationship rel = cascadeRel(TestOne.class, TestTwo.class, Relation.DELETE_NONE);
			AbstractNode startNode   = rel.getStartNode();
			AbstractNode endNode     = rel.getEndNode();
			final String startNodeId = startNode.getUuid();
			final String endNodeId   = endNode.getUuid();
			boolean exception        = false;

			deleteCascade(startNode);
			assertNodeNotFound(startNodeId);
			assertNodeExists(endNodeId);

			// Create another relationship with DELETE_NONE
			rel = cascadeRel(TestOne.class, TestTwo.class, Relation.DELETE_NONE);

			try {
				deleteCascade(rel.getEndNode());

			} catch (FrameworkException fex) {

				assertEquals(422, fex.getStatus());
				exception = true;
			}

			assertTrue("Exception should be raised", exception);

		} catch (FrameworkException ex) {

			logger.warn("", ex);

			logger.error(ex.toString());
			fail("Unexpected exception");

		}
		 */
	}

	/**
	 * DELETE_INCOMING should not trigger delete cascade from start to end node,
	 * but from end to start node
	 */
	@Test
	public void test04CascadeDeleteIncoming() {

		/* this test is flawed in that it expects the cascading
		 * not to take place but expectes to run without an
		 * exception, but deleting one node of the two will leave
		 * the other (TestTwo) in an invalid state according
		 * to its isValid() method!
		try {

			// Create a relationship with DELETE_INCOMING
			AbstractRelationship rel = cascadeRel(TestOne.class, TestTwo.class, Relation.DELETE_INCOMING);
			final String startNodeId = rel.getStartNode().getUuid();
			final String endNodeId   = rel.getEndNode().getUuid();
			boolean exception        = false;

			deleteCascade(rel.getStartNode());

			// Start node should not be found after deletion
			assertNodeNotFound(startNodeId);

			// End node should be found after deletion of start node
			assertNodeExists(endNodeId);

			// Create another relationship with DELETE_INCOMING
			rel = cascadeRel(TestOne.class, TestTwo.class, Relation.DELETE_INCOMING);

			try {
				deleteCascade(rel.getEndNode());

			} catch (FrameworkException fex) {

				assertEquals(422, fex.getStatus());
				exception = true;
			}

			assertTrue("Exception should be raised", exception);

		} catch (FrameworkException ex) {

			logger.warn("", ex);

			logger.error(ex.toString());
			fail("Unexpected exception");

		}
		*/
	}

	/**
	 * DELETE_OUTGOING should trigger delete cascade from start to end node,
	 * but not from end to start node.
	 */
	@Test
	public void test05CascadeDeleteOutgoing() {

		try {

			// Create a relationship with DELETE_OUTGOING
			AbstractRelationship rel = cascadeRel(TestOne.class, TestTwo.class, Relation.SOURCE_TO_TARGET);
			NodeInterface sourceNode;
			NodeInterface targetNode;
			String startNodeId;
			String endNodeId;

			try (final Tx tx = app.tx()) {

				startNodeId = rel.getSourceNode().getUuid();
				endNodeId   = rel.getTargetNode().getUuid();
				sourceNode  = rel.getSourceNode();
			}

			deleteCascade(sourceNode);

			try (final Tx tx = app.tx()) {

				// Start node should not be found after deletion
				assertNodeNotFound(startNodeId);

				// End node should not be found after deletion
				assertNodeNotFound(endNodeId);
			}

			// Create another relationship with DELETE_OUTGOING
			rel = cascadeRel(TestOne.class, TestTwo.class, Relation.SOURCE_TO_TARGET);

			try (final Tx tx = app.tx()) {

				startNodeId = rel.getSourceNode().getUuid();
				endNodeId   = rel.getTargetNode().getUuid();
				targetNode  = rel.getTargetNode();
			}

			deleteCascade(targetNode);

			try (final Tx tx = app.tx()) {

				// End node should not be found after deletion
				assertNodeNotFound(endNodeId);

				// Start node should still exist deletion of end node
				assertNodeExists(startNodeId);
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);

			logger.error(ex.toString());
			fail("Unexpected exception");

		}

	}

	/**
	 * DELETE_INCOMING + DELETE_OUTGOING should trigger delete cascade from start to end node
	 * and from end node to start node
	 */
	@Test
	public void test06CascadeDeleteBidirectional() {

		try {

			// Create a relationship with DELETE_INCOMING
			AbstractRelationship rel = cascadeRel(TestOne.class, TestTwo.class, Relation.TARGET_TO_SOURCE | Relation.SOURCE_TO_TARGET);
			NodeInterface sourceNode;
			NodeInterface targetNode;
			String startNodeId;
			String endNodeId;

			try (final Tx tx = app.tx()) {

				startNodeId = rel.getSourceNode().getUuid();
				endNodeId   = rel.getTargetNode().getUuid();
				sourceNode  = rel.getSourceNode();
			}

			deleteCascade(sourceNode);

			try (final Tx tx = app.tx()) {

				// Start node should not be found after deletion
				assertNodeNotFound(startNodeId);

				// End node should not be found after deletion of start node
				assertNodeNotFound(endNodeId);
			}

			// Create a relationship with DELETE_INCOMING
			rel = cascadeRel(TestOne.class, TestTwo.class, Relation.TARGET_TO_SOURCE | Relation.SOURCE_TO_TARGET);

			try (final Tx tx = app.tx()) {

				startNodeId = rel.getSourceNode().getUuid();
				endNodeId   = rel.getTargetNode().getUuid();
				targetNode  = rel.getTargetNode();
			}

			deleteCascade(targetNode);

			try (final Tx tx = app.tx()) {

				// End node should not be found after deletion
				assertNodeNotFound(endNodeId);

				// Start node should not be found after deletion of end node
				assertNodeNotFound(startNodeId);
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);

			logger.error(ex.toString());
			fail("Unexpected exception");

		}

	}

	/**
	 * DELETE_IF_CONSTRAINT_WOULD_BE_VIOLATED should
	 * trigger delete cascade from start to end node only
	 * if the remote node would not be valid afterwards
	 */
	@Test
	public void test07CascadeDeleteConditional() {

		try {

			AbstractRelationship rel = cascadeRel(TestOne.class, TestTwo.class, Relation.CONSTRAINT_BASED);
			NodeInterface sourceNode;
			String startNodeId;
			String endNodeId;

			try (final Tx tx = app.tx()) {

				startNodeId = rel.getSourceNode().getUuid();
				endNodeId   = rel.getTargetNode().getUuid();
				sourceNode  = rel.getSourceNode();
			}

			deleteCascade(sourceNode);

			try (final Tx tx = app.tx()) {

				// Start node should be deleted
				assertNodeNotFound(startNodeId);

				// End node should be deleted
				assertNodeNotFound(endNodeId);
			}

			rel = cascadeRel(TestOne.class, TestThree.class, Relation.CONSTRAINT_BASED);

			try (final Tx tx = app.tx()) {

				startNodeId = rel.getSourceNode().getUuid();
				endNodeId   = rel.getTargetNode().getUuid();
				sourceNode   = rel.getSourceNode();
			}

			deleteCascade(sourceNode);

			try (final Tx tx = app.tx()) {

				// Start node should be deleted
				assertNodeNotFound(startNodeId);

				// End node should still be there
				assertNodeExists(endNodeId);
			}

			rel = cascadeRel(TestOne.class, TestFour.class, Relation.CONSTRAINT_BASED);

			try (final Tx tx = app.tx()) {

				startNodeId = rel.getSourceNode().getUuid();
				endNodeId   = rel.getTargetNode().getUuid();
				sourceNode   = rel.getSourceNode();
			}

			deleteCascade(sourceNode);

			try (final Tx tx = app.tx()) {

				// Start node should be deleted
				assertNodeNotFound(startNodeId);

				// End node should still be there
				assertNodeExists(endNodeId);
			}

		} catch (FrameworkException ex) {

			logger.warn("", ex);

			logger.error(ex.toString());
			fail("Unexpected exception");

		}

	}

	@Test
	public void test08OverlappingDeleteCascades() {

		/*
		 * This test creates a ternary tree of depth 2 (39 nodes)
		 * linked with relationship type "TEN", with two additional
		 * links throughout the tree. It creates a situation where
		 * two delete cascades overlap when a node is deleted and
		 * tests the correct handling of such a situation.
		 *
		 *           1-------+       1->2, 1->3, 1->4, 1->D
		 *         / | \     |
		 *        /  |  \    |
		 *       2   3   4  /
		 *      /|\ /|\ /|\/
		 *      567 89A BCD
		 */

		try {

			final List<TestTen> rootNodes        = new LinkedList<>();
			final List<TestTen> allChildren      = new LinkedList<>();
			final List<TestTen> allGrandChildren = new LinkedList<>();

			try (final Tx tx = app.tx()) {

				// create some nodes..
				rootNodes.addAll(createTestNodes(TestTen.class, 3));

				for (final TestTen node : rootNodes) {

					final List<TestTen> children = createTestNodes(TestTen.class, 3);
					node.setProperty(TestTen.tenTenChildren, children);

					for (final TestTen child : children) {

						final List<TestTen> grandChildren = createTestNodes(TestTen.class, 3);
						child.setProperty(TestTen.tenTenChildren, grandChildren);

						allGrandChildren.addAll(grandChildren);
					}

					allChildren.addAll(children);
				}

				// create some additional links off a different type but with cascading delete
				rootNodes.get(0).setProperty(TestTen.testChild,   allGrandChildren.get(0));
				allChildren.get(0).setProperty(TestTen.testChild, allGrandChildren.get(1));

				tx.success();
			}

			// check preconditions: exactly 39 nodes should exist
			try (final Tx tx = app.tx()) {

				assertEquals("Wrong number of nodes", 39, app.nodeQuery(TestTen.class).getAsList().size());
				tx.success();
			}

			// delete one root node
			try (final Tx tx = app.tx()) {

				app.delete(rootNodes.get(0));
				tx.success();
			}

			// check conditions after deletion, 26 nodes shoud exist
			try (final Tx tx = app.tx()) {

				assertEquals("Wrong number of nodes", 26, app.nodeQuery(TestTen.class).getAsList().size());
				tx.success();
			}


		} catch (FrameworkException ex) {

			logger.warn("", ex);

			logger.error(ex.toString());
			fail("Unexpected exception");

		}

	}

	@Test
	public void test01CreateNode() {

		try {

			try {

				// Create node out of transaction => should give a NotInTransactionException
				app.create(TestOne.class);
				fail("Should have raised a NotInTransactionException");
			} catch (NotInTransactionException e) {
			}

			try {

				// Try to create node without parameters => should fail
				app.create(TestOne.class);
				fail("Should have raised a NotInTransactionException");
			} catch (NotInTransactionException e) {}

			AbstractNode node = null;

			try (final Tx tx = app.tx()) {

				node = app.create(TestOne.class);
				tx.success();
			}

			assertTrue(node != null);
			assertTrue(node instanceof TestOne);

		} catch (FrameworkException ex) {

			logger.error("", ex);
			fail("Unexpected exception");

		}

	}

	@Test
	public void test02CreateNodeWithExistingUuid() {

		try {

			final PropertyMap props = new PropertyMap();
			TestOne node            = null;

			final String uuid = StringUtils.replace(UUID.randomUUID().toString(), "-", "");

			props.put(GraphObject.id, uuid);

			try (final Tx tx = app.tx()) {

				node = app.create(TestOne.class, props);
				tx.success();
			}

			try (final Tx tx = app.tx()) {

				assertTrue(node != null);
				assertTrue(node instanceof TestOne);
				assertEquals(node.getUuid(), uuid);
			}

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");

		}
	}

	@Test
	public void test02CreateTwoNodesWithSameUuidInSameTx() {

		try {

			final PropertyMap props = new PropertyMap();
			TestOne node            = null;

			final String uuid = StringUtils.replace(UUID.randomUUID().toString(), "-", "");

			props.put(GraphObject.id, uuid);

			try (final Tx tx = app.tx()) {

				node = app.create(TestOne.class, props);

				assertTrue(node != null);
				assertTrue(node instanceof TestOne);
				assertEquals(node.getUuid(), uuid);

				node = app.create(TestOne.class, props);

				tx.success();

				fail("Validation failed!");
			}

		} catch (FrameworkException ex) {
		}

	}

	@Test
	public void test02CreateTwoNodesWithSameUuidInTwoTx() {

		try {

			final PropertyMap props = new PropertyMap();
			TestOne node            = null;

			final String uuid = StringUtils.replace(UUID.randomUUID().toString(), "-", "");

			props.put(GraphObject.id, uuid);

			try (final Tx tx = app.tx()) {

				node = app.create(TestOne.class, props);

				assertTrue(node != null);
				assertTrue(node instanceof TestOne);
				assertEquals(node.getUuid(), uuid);

				tx.success();

			} catch (FrameworkException ex) {
				logger.error(ex.toString());
				fail("Unexpected exception");
			}

			try (final Tx tx = app.tx()) {

				node = app.create(TestOne.class, props);

				tx.success();

				fail("Validation failed!");
			}

		} catch (FrameworkException ex) {

			// validate exception
			ex.printStackTrace();
		}

	}

	@Test
	public void test03CreateRelationship() {

		try {

			final List<GenericNode> nodes = createTestNodes(GenericNode.class, 2);
			final NodeInterface startNode = nodes.get(0);
			final NodeInterface endNode   = nodes.get(1);
			NodeHasLocation rel           = null;

			assertTrue(startNode != null);
			assertTrue(endNode != null);

			try (final Tx tx = app.tx()) {

				rel = app.create(startNode, endNode, NodeHasLocation.class);
				tx.success();
			}

			try (final Tx tx = app.tx()) {

				assertEquals(startNode.getUuid(), rel.getSourceNodeId());
				assertEquals(endNode.getUuid(), rel.getTargetNodeId());
				assertEquals(RelType.IS_AT.name(), rel.getType());
				assertEquals(NodeHasLocation.class, rel.getClass());
			}

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");

		}
	}

	@Test
	public void test06DuplicateRelationshipsOneToOne() {

		// Creating duplicate one-to-one relationships
		// is silently ignored, the relationship will
		// be replaced.

		try (final Tx tx = app.tx()) {

			final TestOne test1 = app.create(TestOne.class);
			final TestTwo test2 = app.create(TestTwo.class);

			// test duplicate prevention
			app.create(test1, test2, OneTwoOneToOne.class);
			app.create(test1, test2, OneTwoOneToOne.class);

			tx.success();

		} catch (FrameworkException ex) {

			fail("Creating duplicate relationships via app.create() should NOT throw an exception.");
		}

		try (final Tx tx = app.tx()) {

			final TestOne test1 = app.create(TestOne.class);
			final TestTwo test2 = app.create(TestTwo.class);

			test1.setProperty(TestOne.testTwo, test2);
			test1.setProperty(TestOne.testTwo, test2);

			tx.success();

		} catch (FrameworkException ex) {

			fail("Creating duplicate relationships via setProperty() should NOT throw an exception.");
		}
	}

	@Test
	public void test06DuplicateRelationshipsOneToMany() {

		try (final Tx tx = app.tx()) {

			final TestSix test1   = app.create(TestSix.class);
			final TestThree test2 = app.create(TestThree.class);

			// test duplicate prevention
			app.create(test1, test2, SixThreeOneToMany.class);
			app.create(test1, test2, SixThreeOneToMany.class);

			tx.success();

		} catch (FrameworkException ex) {

			fail("Creating duplicate relationships via app.create() should NOT throw an exception.");
		}

		// second test via setProperty will silently ignore
		// the duplicates in the list
		try (final Tx tx = app.tx()) {

			final TestSix test1   = app.create(TestSix.class);
			final TestThree test2 = app.create(TestThree.class);

			// test duplicate prevention
			final List<TestThree> list = new LinkedList<>();

			list.add(test2);
			list.add(test2);

			test1.setProperty(TestSix.oneToManyTestThrees, list);

			tx.success();

		} catch (FrameworkException ex) {

			fail("Creating duplicate relationships via setProperty() should NOT throw an exception.");
		}
	}

	@Test
	public void test06DuplicateRelationshipsManyToMany() {

		try (final Tx tx = app.tx()) {

			final TestSix test1 = app.create(TestSix.class);
			final TestOne test2 = app.create(TestOne.class);

			// test duplicate prevention
			app.create(test1, test2, SixOneManyToMany.class);
			app.create(test1, test2, SixOneManyToMany.class);

			fail("Creating duplicate relationships should throw an exception.");

			tx.success();

		} catch (FrameworkException ex) {
		}

		// second test via setProperty() should throw an exception
		// for manyToMany only.
		try (final Tx tx = app.tx()) {

			final TestSix test1 = app.create(TestSix.class);
			final TestOne test2 = app.create(TestOne.class);

			// test duplicate prevention
			final List<TestOne> list = new LinkedList<>();

			list.add(test2);
			list.add(test2);

			test1.setProperty(TestSix.manyToManyTestOnes, list);

			tx.success();

		} catch (FrameworkException ex) {

			fail("Creating duplicate relationships via setProperty() should NOT throw an exception.");
		}
	}

	@Test
	public void test01ModifyNode() {

		try {

			final PropertyMap props = new PropertyMap();
			final String type       = "UnknownTestType";
			final String name       = "GenericNode-name";

			NodeInterface node      = null;

			props.put(AbstractNode.type, type);
			props.put(AbstractNode.name, name);

			try (final Tx tx = app.tx()) {

				node = app.create(GenericNode.class, props);
				tx.success();
			}

			try (final Tx tx = app.tx()) {

				// Check defaults
				assertEquals(GenericNode.class.getSimpleName(), node.getProperty(AbstractNode.type));
				assertTrue(node.getProperty(AbstractNode.name).equals(name));
				assertTrue(!node.getProperty(AbstractNode.hidden));
				assertTrue(!node.getProperty(AbstractNode.visibleToAuthenticatedUsers));
				assertTrue(!node.getProperty(AbstractNode.visibleToPublicUsers));
			}

			final String name2 = "GenericNode-name-äöüß";

			try (final Tx tx = app.tx()) {

				// Modify values
				node.setProperty(AbstractNode.name, name2);
				node.setProperty(AbstractNode.hidden, true);
				node.setProperty(AbstractNode.visibleToAuthenticatedUsers, true);
				node.setProperty(AbstractNode.visibleToPublicUsers, true);

				tx.success();
			}

			try (final Tx tx = app.tx()) {

				assertTrue(node.getProperty(AbstractNode.name).equals(name2));
				assertTrue(node.getProperty(AbstractNode.hidden));
				assertTrue(node.getProperty(AbstractNode.visibleToAuthenticatedUsers));
				assertTrue(node.getProperty(AbstractNode.visibleToPublicUsers));
			}

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");

		}

	}

	 /**
	 * Test the results of setProperty and getProperty of a relationship
	 */
	@Test
	public void test02ModifyRelationship() {

		try {

			final NodeHasLocation rel = (createTestRelationships(NodeHasLocation.class, 1)).get(0);
			final PropertyKey key1         = new StringProperty("jghsdkhgshdhgsdjkfgh");
			final String val1              = "54354354546806849870";

			try (final Tx tx = app.tx()) {

				rel.setProperty(key1, val1);
				tx.success();
			}

			try (final Tx tx = app.tx()) {

				assertTrue("Expected relationship to have a value for key '" + key1.dbName() + "'", rel.getRelationship().hasProperty(key1.dbName()));

				assertEquals(val1, rel.getRelationship().getProperty(key1.dbName()));

				Object vrfy1 = rel.getProperty(key1);
				assertEquals(val1, vrfy1);
			}

			final String val2 = "öljkhöohü8osdfhoödhi";

			try (final Tx tx = app.tx()) {

				rel.setProperty(key1, val2);
				tx.success();
			}

			try (final Tx tx = app.tx()) {

				Object vrfy2 = rel.getProperty(key1);
				assertEquals(val2, vrfy2);
			}

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");
		}
	}

	/**
	 * Test the results of setProperty and getProperty of a node
	 */
	@Test
	public void test03ModifyConstantBooleanProperty() {

		try {

			final Class groupType          = StructrApp.getConfiguration().getNodeEntityClass("Group");
			final PropertyKey<Boolean> key = StructrApp.key(groupType, "isGroup");
			final PropertyMap props        = new PropertyMap();
			final String type              = "Group";
			final String name              = "TestGroup-1";

			NodeInterface node      = null;

			props.put(AbstractNode.type, type);
			props.put(AbstractNode.name, name);

			try (final Tx tx = app.tx()) {

				node = app.create(Group.class, props);
				tx.success();
			}

			try (final Tx tx = app.tx()) {

				// Check defaults
				assertEquals(Group.class.getSimpleName(), node.getProperty(AbstractNode.type));
				assertTrue(node.getProperty(AbstractNode.name).equals(name));
				assertTrue(node.getProperty(key));
			}

			final String name2 = "TestGroup-2";

			try (final Tx tx = app.tx()) {

				// Modify values
				node.setProperty(AbstractNode.name, name2);
				node.setProperty(key, false);

				fail("Should have failed with an exception: Group.isGroup is_read_only_property");

				tx.success();

			} catch (FrameworkException expected) {}


		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");

		}

	}

	@Test
	public void test01CompareAscending() {

		try {

			TestOne a = createTestNode(TestOne.class);
			TestOne b = createTestNode(TestOne.class);

			try (final Tx tx = app.tx()) {

				GraphObjectComparator comp = new GraphObjectComparator(TestOne.anInt, GraphObjectComparator.ASCENDING);

				try {
					comp.compare(null, null);
					fail("Should have raised an NullPointerException");

				} catch (NullPointerException e) {}

				try {
					comp.compare(a, null);
					fail("Should have raised an NullPointerException");

				} catch (NullPointerException e) {}

				try {
					comp.compare(null, b);
					fail("Should have raised an NullPointerException");

				} catch (NullPointerException e) {}

				try {
					comp.compare(null, b);
					fail("Should have raised an NullPointerException");

				} catch (NullPointerException e) {}

				// a: null
				// b: null
				// a == b => 0
				assertEquals(0, comp.compare(a, b));

				// a: 0
				// b: null
				// a < b => -1
				setPropertyTx(a, TestOne.anInt, 0);
				assertEquals(-1, comp.compare(a, b));

				// a: null
				// b: 0
				// a > b => 1
				setPropertyTx(a, TestOne.anInt, null);
				setPropertyTx(b, TestOne.anInt, 0);
				assertEquals(1, comp.compare(a, b));

				// a: 1
				// b: 2
				// a < b => -1
				setPropertyTx(a, TestOne.anInt, 1);
				setPropertyTx(b, TestOne.anInt, 2);
				assertEquals(-1, comp.compare(a, b));

				// a: 2
				// b: 1
				// a > b => 1
				setPropertyTx(a, TestOne.anInt, 2);
				setPropertyTx(b, TestOne.anInt, 1);
				assertEquals(1, comp.compare(a, b));
			}

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");

		}

	}

	@Test
	public void test01CompareDescending() {

		try {

			TestOne a = createTestNode(TestOne.class);
			TestOne b = createTestNode(TestOne.class);

			GraphObjectComparator comp = new GraphObjectComparator(TestOne.anInt, GraphObjectComparator.DESCENDING);

			try {
				comp.compare(null, null);
				fail("Should have raised an NullPointerException");

			} catch (NullPointerException e) {}

			try {
				comp.compare(a, null);
				fail("Should have raised an NullPointerException");

			} catch (NullPointerException e) {}

			try {
				comp.compare(null, b);
				fail("Should have raised an NullPointerException");

			} catch (NullPointerException e) {}

			try {
				comp.compare(null, b);
				fail("Should have raised an NullPointerException");

			} catch (NullPointerException e) {}

			try (final Tx tx = app.tx()) {

				// a: null
				// b: null
				// a == b => 0
				assertEquals(0, comp.compare(a, b));
			}

			// a: 0
			// b: null
			// a > b => 1
			setPropertyTx(a, TestOne.anInt, 0);

			try (final Tx tx = app.tx()) {

				assertEquals(1, comp.compare(a, b));
			}

			// a: null
			// b: 0
			// a < b => -1
			setPropertyTx(a, TestOne.anInt, null);
			setPropertyTx(b, TestOne.anInt, 0);

			try (final Tx tx = app.tx()) {

				assertEquals(-1, comp.compare(a, b));
			}

			// a: 1
			// b: 2
			// a > b => 1
			setPropertyTx(a, TestOne.anInt, 1);
			setPropertyTx(b, TestOne.anInt, 2);

			try (final Tx tx = app.tx()) {

				assertEquals(1, comp.compare(a, b));
			}

			// a: 2
			// b: 1
			// a < b => -1
			setPropertyTx(a, TestOne.anInt, 2);
			setPropertyTx(b, TestOne.anInt, 1);

			try (final Tx tx = app.tx()) {

				assertEquals(-1, comp.compare(a, b));
			}

		} catch (FrameworkException ex) {

			logger.error(ex.toString());
			fail("Unexpected exception");

		}
	}

	@Test
	public void testNodeCacheInvalidationInRelationshipWrapper() {

		try (final Tx tx = app.tx()) {

			final TestOne testOne    = createTestNode(TestOne.class);
			final TestTwo testTwo1   = createTestNode(TestTwo.class, new NodeAttribute<>(TestTwo.testOne, testOne));
			final OneTwoOneToOne rel = testOne.getOutgoingRelationship(OneTwoOneToOne.class);
			final TestTwo testTwo2   = rel.getTargetNode();

			testTwo1.setProperty(AbstractNode.name, "test");

			assertEquals("Cache invalidation failure!", "test", testTwo2.getProperty(AbstractNode.name));

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// fill cache with other nodes
		try {

			createTestNodes(TestSix.class, 1000);

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final TestOne testOne    = app.nodeQuery(TestOne.class).getFirst();
			final TestTwo testTwo1   = app.nodeQuery(TestTwo.class).getFirst();
			final OneTwoOneToOne rel = testOne.getOutgoingRelationship(OneTwoOneToOne.class);
			final TestTwo testTwo2   = rel.getTargetNode();

			testTwo1.setProperty(AbstractNode.name, "test2");

			assertEquals("Cache invalidation failure!", "test2", testTwo2.getProperty(AbstractNode.name));

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testNodeCacheInvalidationWithLongLivedReferences() {

		TestOne longLivedReference = null;

		try (final Tx tx = app.tx()) {

			longLivedReference = createTestNode(TestOne.class, "test1");

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// fill cache with other nodes
		try {

			createTestNodes(TestSix.class, 1000);

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final TestOne freshReference = app.nodeQuery(TestOne.class).getFirst();

			freshReference.setProperty(AbstractNode.name, "test2");

			assertEquals("Cache invalidation failure!", "test2", longLivedReference.getProperty(AbstractNode.name));

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testRelationshipEndNodeTypeRestriction() {

		// this test makes sure that relationships with identical relationship
		// types are filtered according to the types of their end nodes
		try (final Tx tx = app.tx()) {

			// create two OWNS relationships with different end node types
			final TestOne testOne     = app.create(TestOne.class, "testone");
			final TestThree testThree = app.create(TestThree.class, "testthree");
			final Principal testUser  = app.create(Principal.class, "testuser");

			testOne.setProperty(TestOne.testThree, testThree);
			testThree.setProperty(TestThree.owner, testUser);

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final List<OneThreeOneToOne> rels = app.relationshipQuery(OneThreeOneToOne.class).getAsList();

			assertEquals("Relationship query returns wrong number of results", 1, rels.size());

			for (final OneThreeOneToOne rel : rels) {
				assertEquals("Relationship query returns wrong type", OneThreeOneToOne.class, rel.getClass());
			}

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testRelationshipsOnNodeCreation() {

		Principal user = null;
		TestOne test  = null;

		// create user
		try (final Tx tx = app.tx()) {

			user = app.create(Principal.class, "tester");

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		final SecurityContext ctx = SecurityContext.getInstance(user, AccessMode.Backend);
		final App app             = StructrApp.getInstance(ctx);

		// create object with user context
		try (final Tx tx = app.tx()) {

			test = app.create(TestOne.class);

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// query for relationships
		try (final Tx tx = app.tx()) {

			final List<? extends RelationshipInterface> rels1 = app.relationshipQuery().and(AbstractRelationship.sourceId, user.getUuid()).getAsList();
			final List<Class> classes1                        = rels1.stream().map(r -> r.getClass()).collect(Collectors.toList());
			assertEquals("Invalid number of relationships after object creation", 2, rels1.size());
			assertTrue("Invalid relationship type after object creation", classes1.contains(Security.class));
			assertTrue("Invalid relationship type after object creation", classes1.contains(PrincipalOwnsNode.class));

			final List<? extends RelationshipInterface> rels2 = app.relationshipQuery().and(AbstractRelationship.targetId, test.getUuid()).getAsList();
			final List<Class> classes2                        = rels2.stream().map(r -> r.getClass()).collect(Collectors.toList());
			assertEquals("Invalid number of relationships after object creation", 2, rels2.size());
			assertTrue("Invalid relationship type after object creation", classes2.contains(Security.class));
			assertTrue("Invalid relationship type after object creation", classes2.contains(PrincipalOwnsNode.class));

			final List<? extends RelationshipInterface> rels3 = Iterables.toList(test.getIncomingRelationships());
			final List<Class> classes3                        = rels3.stream().map(r -> r.getClass()).collect(Collectors.toList());
			assertEquals("Invalid number of relationships after object creation", 2, rels3.size());
			assertTrue("Invalid relationship type after object creation", classes3.contains(Security.class));
			assertTrue("Invalid relationship type after object creation", classes3.contains(PrincipalOwnsNode.class));

			final Security sec = app.relationshipQuery(Security.class).getFirst();
			assertNotNull("Relationship caching on node creation is broken", sec);

			final PrincipalOwnsNode owns = app.relationshipQuery(PrincipalOwnsNode.class).getFirst();
			assertNotNull("Relationship caching on node creation is broken", owns);

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	/**
	 * This test makes sure that no graph objects that were created in a transaction which is rolled back
	 * are visible/accessible in other tx.
	 *
	 * Before a bug fix this test was created for, we saw NotFoundExceptions with wrapped NoSuchRecordException
	 * when trying to access stale nodes through relationships from the cache.
	 */

	@Test
	public void testRelationshipsOnNodeCreationAfterRollback() {

		Principal user = null;
		TestThirteen test  = null;

		// create user
		try (final Tx tx = app.tx()) {

			user = app.create(Principal.class, "tester");

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		final SecurityContext ctx = SecurityContext.getInstance(user, AccessMode.Backend);
		final App app             = StructrApp.getInstance(ctx);

		String uuid = null;

		List<? extends RelationshipInterface> rels1 = Collections.EMPTY_LIST;
		List<? extends RelationshipInterface> rels2 = Collections.EMPTY_LIST;
		List<? extends RelationshipInterface> rels3 = Collections.EMPTY_LIST;
		List<? extends RelationshipInterface> rels4 = Collections.EMPTY_LIST;

		// create object with user context
		try (final Tx tx = app.tx()) {

			test = app.create(TestThirteen.class);
			uuid = test.getUuid();

			rels1 = app.relationshipQuery().and(AbstractRelationship.sourceId, user.getUuid()).getAsList();
			rels2 = app.relationshipQuery().and(AbstractRelationship.targetId, test.getUuid()).getAsList();
			rels3 = Iterables.toList(test.getIncomingRelationships());
			rels4 = Iterables.toList(user.getOutgoingRelationships());

			System.out.println("rels1: " + rels1.size());
			System.out.println("rels2: " + rels2.size());
			System.out.println("rels3: " + rels3.size());
			System.out.println("rels4: " + rels4.size());

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			//fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			// We shouldn't find a not-created node by its UUID
			final TestThirteen test2 = app.get(TestThirteen.class, uuid);
			assertNull(test2);

			rels1 = app.relationshipQuery().and(AbstractRelationship.sourceId, user.getUuid()).getAsList();
			rels4 = Iterables.toList(user.getOutgoingRelationships());

			System.out.println("rels1: " + rels1.size());
			System.out.println("rels4: " + rels4.size());

			for (final RelationshipInterface rel : rels1) {
				System.out.println("Source node: " + rel.getSourceNode() + ", target node: " + rel.getTargetNode());
			}

			for (final RelationshipInterface rel : rels4) {
				System.out.println("Source node: " + rel.getSourceNode() + ", target node: " + rel.getTargetNode());
			}

		} catch (Exception fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		// query for relationships
		try (final Tx tx = app.tx()) {

			rels1 = app.relationshipQuery().and(AbstractRelationship.sourceId, user.getUuid()).getAsList();
			final List<Class> classes1                        = rels1.stream().map(r -> r.getClass()).collect(Collectors.toList());
			assertEquals("Invalid number of relationships after object creation", 0, rels1.size());

			rels4 = Iterables.toList(user.getOutgoingRelationships());
			final List<Class> classes4                        = rels4.stream().map(r -> r.getClass()).collect(Collectors.toList());
			assertEquals("Invalid number of relationships after object creation", 0, rels4.size());

			final Security sec = app.relationshipQuery(Security.class).getFirst();
			assertNull("Relationship caching on node creation is broken", sec);

			final PrincipalOwnsNode owns = app.relationshipQuery(PrincipalOwnsNode.class).getFirst();
			assertNull("Relationship caching on node creation is broken", owns);

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testRelationshipsToListWithNullTimestamp() {

		String id = null;

		try (final Tx tx = app.tx()) {

			final TestSix testSix = createTestNode(TestSix.class);
			id = testSix.getUuid();

			// Create 1000 rels
			for (int i=0; i<10; i++) {

				final TestThree testThree = createTestNode(TestThree.class, new NodeAttribute<>(TestThree.oneToManyTestSix, testSix));
				final RelationshipInterface rel = testThree.getRelationships(SixThreeOneToMany.class).iterator().next();

				if (i%2 == 0) {
					rel.getRelationship().setProperty("internalTimestamp", null);
				}
			}

			tx.success();

		} catch (FrameworkException fex) {
			fex.printStackTrace();
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final TestSix testSix = app.get(TestSix.class, id);

			// This calls NodeWrapper#toList internally
			List<OneTwoOneToOne> rels = Iterables.toList(testSix.getRelationships());

			assertEquals("Wrong number of relationships", 10, rels.size());

			for (RelationshipInterface rel : rels) {
				System.out.println(rel.getProperty(StructrApp.getConfiguration().getPropertyKeyForJSONName(RelationshipInterface.class, "internalTimestamp")));
			}


			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testNodeCreationWithForcedUuid() {

		final String uuid = NodeServiceCommand.getNextUuid();
		TestOne test      = null;

		// create object with user context
		try (final Tx tx = app.tx()) {

			test = app.create(TestOne.class,
				new NodeAttribute<>(AbstractNode.name, "test"),
				new NodeAttribute<>(GraphObject.id, uuid)
			);

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final String uuid1 = test.getProperty(GraphObject.id);
			final String uuid2 = test.getUuid();

			assertEquals("Object creation does not accept provided UUID", uuid, uuid1);
			assertEquals("UUID mismatch in getProperty() and getUuid()", uuid1, uuid2);

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testNodeCreationWithForcedInvalidUuid() {

		TestOne test = null;

		// create object with user context
		try (final Tx tx = app.tx()) {

			test = app.create(TestOne.class,
				new NodeAttribute<>(AbstractNode.name, "test"),
				new NodeAttribute<>(GraphObject.id, null)
			);

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		try (final Tx tx = app.tx()) {

			final String uuid1 = test.getProperty(GraphObject.id);
			final String uuid2 = test.getUuid();

			assertEquals("UUID mismatch in getProperty() and getUuid()", uuid1, uuid2);

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testCreateNodeWithAdditionalProperties() {

		TestOne test = null;

		// create object with user context
		try (final Tx tx = app.tx()) {

			test = app.create(TestOne.class,
				new NodeAttribute<>(AbstractNode.name, "test"),
				new NodeAttribute<>(GraphObject.visibleToPublicUsers, true),
				new NodeAttribute<>(GraphObject.visibleToAuthenticatedUsers, true),
				new NodeAttribute<>(AbstractNode.hidden, true)
			);

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		// create object with user context
		try (final Tx tx = app.tx()) {

			assertEquals("Invalid create node result",       "test", test.getProperty(AbstractNode.name));
			assertEquals("Invalid create node result", Boolean.TRUE, test.getProperty(GraphObject.visibleToPublicUsers));
			assertEquals("Invalid create node result", Boolean.TRUE, test.getProperty(GraphObject.visibleToAuthenticatedUsers));
			assertEquals("Invalid create node result", Boolean.TRUE, test.getProperty(AbstractNode.hidden));

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}
	}

	@Test
	public void testObjectCreationAfterRollback() {

		/**
		 * 1. Test Setup:
		 * - Clean Database
		 * - Create "bad" type with error in onCreate
		 * - Create "good" type
		 * - Create a test user
		 */

		cleanDatabaseAndSchema();

		try {
			Thread.sleep(1000L);
		} catch (InterruptedException ex) {
			logger.error("Thread sleep was interrupted", ex);
		}

		// setup: create dynamic type with onCreate() method
		try (final Tx tx = app.tx()) {

			SchemaNode badNodeType = createTestNode(SchemaNode.class, "BadNode");
			badNodeType.setProperty(new StringProperty("___onCreate"), "{ return Structr.error('name', 'no node of this type can not be created!'); }");

			SchemaNode goodNodeType = createTestNode(SchemaNode.class, "GoodNode");

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}


		Principal user = null;

		// create user
		try (final Tx tx = app.tx()) {

			user = app.create(Principal.class, "tester");

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		final SecurityContext ctx = SecurityContext.getInstance(user, AccessMode.Backend);
		final App userApp             = StructrApp.getInstance(ctx);


		/**
		 * 2. Test Execution:
		 * - Try to create one node of the type that fails ==> rollback
		 * - Create HALF as many nodes of a type that can be created ==> the fail probability is quite high (fail meaning that a previously deleted relationship is still present in the cache and not marked as stale)
		 */

		long maxId = 0;

		try (final Tx tx = userApp.tx()) {

			Class failingType = StructrApp.getConfiguration().getNodeEntityClass("BadNode");

			final NodeInterface x = userApp.create(failingType, "should fail");
			for (RelationshipInterface r : x.getIncomingRelationships()) {
				System.out.println(r.getRelType().name() + ": " + r.getId());
				maxId = Long.max(maxId, r.getId());
			}

			tx.success();

		} catch (FrameworkException fex) {

			// expected error
			assertEquals("BadNode.name no node of this type can not be created!", fex.toString());

		}

		maxId = Long.min(maxId, 100);

		/**
		 * Sleep for some time - in the hopes that the database will re-use the previously deleted relationship ids
		 */
		try {
			logger.info("Waiting for 20 seconds...");
			Thread.sleep(20000L);
		} catch (InterruptedException ex) {
			logger.error("Thread sleep was interrupted", ex);
		}

		try (final Tx tx = userApp.tx()) {

			Class goodType = StructrApp.getConfiguration().getNodeEntityClass("GoodNode");

			for (int cnt = 0; cnt < (maxId / 2); cnt++) {
				userApp.create(goodType, "Good node " + cnt);
			}

			tx.success();

		} catch (ClassCastException cce) {

			logger.warn("", cce);
			fail("ClassCastException occurred - this happens because a relationship cache entry was not set to stale after a rollback!");

		} catch (FrameworkException fex) {

			logger.warn("", fex);

			if (fex.getStatus() == 403) {
				fail("Previously existing relationship (which was deleted) but not set stale has been re-used in the cache... which is why the user is not allowed to modify his own node");
			}

			fail("Unexpected exception.");
		}

	}

	@Test
	public void testRelationshipCreationAfterRollback() {

		cleanDatabaseAndSchema();

		try {
			Thread.sleep(1000L);
		} catch (InterruptedException ex) {
			logger.error("Thread sleep was interrupted", ex);
		}

		// setup: create dynamic type with onCreate() method
		try (final Tx tx = app.tx()) {

			final SchemaNode source = app.create(SchemaNode.class, "Source");
			final SchemaNode target = app.create(SchemaNode.class, "Target");

			final SchemaRelationshipNode rel = app.create(SchemaRelationshipNode.class,
				new NodeAttribute(SchemaRelationshipNode.relationshipType, "LINK"),
				new NodeAttribute(SchemaRelationshipNode.sourceNode, source),
				new NodeAttribute(SchemaRelationshipNode.targetNode, target),
				new NodeAttribute(SchemaRelationshipNode.sourceMultiplicity, "1"),
				new NodeAttribute(SchemaRelationshipNode.targetMultiplicity, "1")
			);

			rel.setProperty(new StringProperty("___onCreate"), "{ return Structr.error('name', 'relationship can not be created!'); }");

			tx.success();

		} catch (FrameworkException fex) {

			logger.warn("", fex);
			fail("Unexpected exception.");
		}


		Principal user = null;

		// create user
		try (final Tx tx = app.tx()) {

			user = app.create(Principal.class, "tester");

			tx.success();

		} catch (FrameworkException fex) {
			logger.warn("", fex);
			fail("Unexpected exception.");
		}

		final SecurityContext ctx = SecurityContext.getInstance(user, AccessMode.Backend);
		final App userApp         = StructrApp.getInstance(ctx);


		NodeInterface sourceNode = null;
		NodeInterface targetNode = null;

		try (final Tx tx = userApp.tx()) {

			Class sourceType = StructrApp.getConfiguration().getNodeEntityClass("Source");
			Class targetType = StructrApp.getConfiguration().getNodeEntityClass("Target");

			sourceNode = userApp.create(sourceType, "source node");
			targetNode = userApp.create(targetType, "target node");

			tx.success();

		} catch (FrameworkException fex) {

			fail("Unexpected exception.");

		}

		assertNotNull(sourceNode);
		assertNotNull(targetNode);

		long maxId = 0;

		try (final Tx tx = userApp.tx()) {

			final Class relClass = StructrApp.getConfiguration().getRelationClassForCombinedType(sourceNode.getType(), "LINK", targetNode.getType());

			userApp.create(sourceNode, targetNode, relClass);

			for (RelationshipInterface r : targetNode.getIncomingRelationships()) {
				System.out.println(r.getRelType().name() + ": " + r.getId());
				maxId = Long.max(maxId, r.getId());
			}

			tx.success();

		} catch (FrameworkException fex) {

			// expected error
			assertEquals("LINK.name relationship can not be created!", fex.toString());

		}

		maxId = Long.min(maxId, 100);

		/**
		 * Sleep for some time - in the hopes that the database will re-use the previously deleted relationship ids
		 */
		try {
			logger.info("Waiting for 20 seconds...");
			Thread.sleep(20000L);
		} catch (InterruptedException ex) {
			logger.error("Thread sleep was interrupted", ex);
		}

		try (final Tx tx = userApp.tx()) {

			Class goodType = StructrApp.getConfiguration().getNodeEntityClass("Source");

			for (int cnt = 0; cnt < (maxId); cnt++) {
				userApp.create(goodType, "Surce node " + cnt);
			}

			tx.success();

		} catch (ClassCastException cce) {

			logger.warn("", cce);
			fail("ClassCastException occurred - this happens because a relationship cache entry was not set to stale after a rollback!");

		} catch (FrameworkException fex) {

			logger.warn("", fex);

			if (fex.getStatus() == 403) {
				fail("Previously existing relationship (which was deleted) but not set stale has been re-used in the cache... which is why the user is not allowed to modify his own node");
			}

			fail("Unexpected exception.");
		}
	}

	// ----- private methods -----
	private AbstractRelationship cascadeRel(final Class type1, final Class type2, final int cascadeDeleteFlag) throws FrameworkException {

		try (final Tx tx = app.tx()) {

			NodeInterface start      = createTestNode(type1);
			NodeInterface end        = createTestNode(type2);
			AbstractRelationship rel = createTestRelationship(start, end, NodeHasLocation.class);

			rel.setProperty(AbstractRelationship.cascadeDelete, cascadeDeleteFlag);

			tx.success();

			return rel;
		}
	}

	private void deleteCascade(final NodeInterface node) throws FrameworkException {

		try (final Tx tx = app.tx()) {

			app.delete(node);
			tx.success();
		}
	}

	private void setPropertyTx(final GraphObject obj, final PropertyKey key, final Object value) {

		try (final Tx tx = app.tx()) {

			obj.setProperty(key, value);
			tx.success();

		} catch (FrameworkException ex) {
		}
	}
}