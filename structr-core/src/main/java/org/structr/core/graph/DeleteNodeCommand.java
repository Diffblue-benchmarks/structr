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
package org.structr.core.graph;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.common.error.ErrorBuffer;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.entity.Relation;

/**
 * Deletes a node.
 */
public class DeleteNodeCommand extends NodeServiceCommand {

	private static final Logger logger = LoggerFactory.getLogger(DeleteNodeCommand.class.getName());

	private final Set<NodeInterface> deletedNodes = new HashSet<>();

	public void execute(NodeInterface node) {

		if (securityContext.doCascadingDelete()) {

			doDeleteNode(node);

			for (final NodeInterface deleteMe : deletedNodes) {

				// mark node as deleted in transaction
				TransactionCommand.nodeDeleted(securityContext.getCachedUser(), deleteMe);

				// delete node in database
				deleteMe.getNode().delete(false);
			}

		} else {

			node.onNodeDeletion();
			node.getNode().delete(true);
		}
	}

	private void doDeleteNode(final NodeInterface node) {

		if (node == null || TransactionCommand.isDeleted(node.getNode())) {
			return;
		}

		try {
			if (!deletedNodes.contains(node) && node.getUuid() == null) {

				logger.warn("Will not delete node which has no UUID, dumping stack.");
				Thread.dumpStack();

				return;
			}

		} catch (java.lang.IllegalStateException ise) {
			logger.warn("Trying to delete a node which is already deleted", ise.getMessage());
			return;
		} catch (org.structr.api.NotFoundException nfex) {
			// exception can be ignored, node is already deleted
		}

		deletedNodes.add(node);

		App app = StructrApp.getInstance(securityContext);

		try {

			List<NodeInterface> nodesToCheckAfterDeletion = new LinkedList<>();

			// Delete all end nodes of outgoing relationships which are connected
			// by relationships which are marked with DELETE_OUTGOING
			for (AbstractRelationship rel : node.getOutgoingRelationships()) {

				// deleted rels can be null..
				if (rel != null) {

					int cascadeDelete = rel.getCascadingDeleteFlag();
					NodeInterface endNode = rel.getTargetNode();

					if ((cascadeDelete & Relation.CONSTRAINT_BASED) == Relation.CONSTRAINT_BASED) {

						nodesToCheckAfterDeletion.add(endNode);
					}

					if (!deletedNodes.contains(endNode) && ((cascadeDelete & Relation.SOURCE_TO_TARGET) == Relation.SOURCE_TO_TARGET)) {

						// remove end node from index
						doDeleteNode(endNode);
					}
				}
			}

			// Delete all start nodes of incoming relationships which are connected
			// by relationships which are marked with DELETE_INCOMING
			for (AbstractRelationship rel : node.getIncomingRelationships()) {

				// deleted rels can be null
				if (rel != null) {

					final int cascadeDelete       = rel.getCascadingDeleteFlag();
					final NodeInterface startNode = rel.getSourceNode();

					if ((cascadeDelete & Relation.CONSTRAINT_BASED) == Relation.CONSTRAINT_BASED) {

						nodesToCheckAfterDeletion.add(startNode);
					}

					if (!deletedNodes.contains(startNode) && ((cascadeDelete & Relation.TARGET_TO_SOURCE) == Relation.TARGET_TO_SOURCE)) {

						// remove start node from index
						doDeleteNode(startNode);
					}
				}
			}

			// deletion callback, must not prevent node deletion!
			node.onNodeDeletion();

			// Delete any relationship (this is PASSIVE DELETION)
			for (AbstractRelationship r : node.getRelationships()) {

				if (r != null) {

					app.delete(r);
				}
			}

			// now check again the deletion cascade for violated constraints
			// Check all end nodes of outgoing relationships which are connected if they are
			// still valid after node deletion
			for (NodeInterface nodeToCheck : nodesToCheckAfterDeletion) {

				ErrorBuffer errorBuffer = new ErrorBuffer();

				if (!deletedNodes.contains(nodeToCheck) && !nodeToCheck.isValid(errorBuffer)) {

					// remove end node from index
					doDeleteNode(nodeToCheck);
				}
			}

		} catch (Throwable t) {

			t.printStackTrace();

			logger.warn("Exception while deleting node {}: {}", node, t.getMessage());
		}

		return;
	}
}
