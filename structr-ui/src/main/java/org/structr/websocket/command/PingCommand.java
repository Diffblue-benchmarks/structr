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
package org.structr.websocket.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.structr.core.Services;
import org.structr.core.entity.AbstractNode;
import org.structr.rest.auth.AuthHelper;
import org.structr.rest.service.HttpService;
import org.structr.websocket.StructrWebSocket;
import org.structr.websocket.message.MessageBuilder;
import org.structr.websocket.message.WebSocketMessage;
import org.structr.core.entity.Principal;

//~--- classes ----------------------------------------------------------------

/**
 * Websocket heartbeat command, keeps the websocket connection open.
 *
 * Checks validity of session id.
 *
 *
 */
public class PingCommand extends AbstractCommand {

	private static final Logger logger = LoggerFactory.getLogger(PingCommand.class.getName());

	static {

		StructrWebSocket.addCommand(PingCommand.class);

	}

	@Override
	public void processMessage(final WebSocketMessage webSocketData) {
		
		final String sessionId = webSocketData.getSessionId();
		logger.debug("PING received from session {}", sessionId);

		final Principal currentUser = AuthHelper.getPrincipalForSessionId(Services.getInstance().getService(HttpService.class).getSessionCache().getSessionHandler().getSessionIdManager().getId(sessionId));

		if (currentUser != null) {

			getWebSocket().send(MessageBuilder.status()
				.callback(webSocketData.getCallback())
				.data("username", currentUser.getProperty(AbstractNode.name))
				.data("isAdmin", currentUser.getProperty(Principal.isAdmin))
				.code(100).build(), true);

		} else {

			logger.debug("Invalid session id");
			getWebSocket().send(MessageBuilder.status().code(401).build(), true);

		}
	}

	//~--- get methods ----------------------------------------------------

	@Override
	public String getCommand() {
		return "PING";
	}
}
