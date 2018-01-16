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
package org.structr.xmpp.handler;

import org.jivesoftware.smack.packet.Message;
import org.structr.xmpp.XMPPClient;
import org.structr.xmpp.XMPPContext.StructrXMPPConnection;

/**
 *
 *
 */
public class MessageTypeHandler implements TypeHandler<Message> {

	@Override
	public void handle(final StructrXMPPConnection connection, final Message packet) {

		if (packet.getBody() != null) {

			// let client callback handle the message
			XMPPClient.onMessage(connection.getUuid(), packet);
		}
	}
}
