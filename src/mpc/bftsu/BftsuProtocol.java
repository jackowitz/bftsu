// Copyright 2010-2012 Martin Burkhart (martibur@ethz.ch)
//
// This file is part of SEPIA. SEPIA is free software: you can redistribute 
// it and/or modify it under the terms of the GNU Lesser General Public 
// License as published by the Free Software Foundation, either version 3 
// of the License, or (at your option) any later version.
//
// SEPIA is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with SEPIA.  If not, see <http://www.gnu.org/licenses/>.

package mpc.bftsu;

import java.util.logging.Level;

import mpc.protocolPrimitives.PrimitivesEnabledProtocol;
import services.Stopper;
import connections.PrivacyViolationException;

/**
 * common functionalities for all bftsu protocol classes
 *
 * @author Dilip Many
 *
 */
public abstract class BftsuProtocol extends PrimitivesEnabledProtocol implements Runnable {

	/** holds the message to be sent over the connection */
	protected BftsuMessage messageToSend;
	/** hold the last received message */
	protected BftsuMessage messageReceived;
	/** defines the string which precedes a bftsu protocol message */
	protected static String BFTSU_MESSAGE = "BFTSU_MESSAGE";


	/**
	 * creates a new protocol instance
	 *
	 * @param threadNumber	This peer's thread number (for identification when notifying observers)
	 * @param bftsuPeer	(Privacy) Peer who started the protocol
	 * @param otherPeerID   the other peer's ID
	 * @param stopper		Can be used to stop a running protocol thread
	 */
	public BftsuProtocol(int threadNumber, BftsuBase bftsuPeer, String otherPeerID, int otherPeerIndex, Stopper stopper) {
		super(threadNumber, bftsuPeer.getConnectionManager(), bftsuPeer.getMyPeerID(), otherPeerID, bftsuPeer.getMyPeerIndex(), otherPeerIndex, stopper);

		initializeProtocolPrimitives(bftsuPeer);
	}


	/**
	 * Sends a bftsu message over the connection.
	 * @throws PrivacyViolationException 
	 */
	protected void sendMessage() throws PrivacyViolationException {
		logger.log(Level.INFO, "Sending bftsu message (to " + otherPeerID + ")...");
		connectionManager.sendMessage(otherPeerID, BFTSU_MESSAGE);
		connectionManager.sendMessage(otherPeerID, messageToSend);
	}


	/**
	 * Receives a bftsu message over the connection.
	 * (the received message is stored in the messageReceived variable)
	 * @throws PrivacyViolationException 
	 */
	protected void receiveMessage() throws PrivacyViolationException {
		logger.log(Level.INFO, "Waiting for bftsu message to arrive ( from " + otherPeerID + ")...");
		String messageType = (String) connectionManager.receiveMessage(otherPeerID);
		messageReceived = (BftsuMessage) connectionManager.receiveMessage(otherPeerID);
		
		// If the input peer has disconnected, null is returned
		if(messageType==null || messageReceived==null) {
			/*
			 * Even though the input peer has left, we need to notify our observers in order
			 * not to block protocol execution. Use a dummy message. 
			 */
			messageReceived = new BftsuMessage(otherPeerID, otherPeerIndex);
			messageReceived.setIsDummyMessage(true);			
			
			logger.info("No connection to "+otherPeerID+". Notifying Observers with DUMMY message... ");
			notify(messageReceived);
		} else if (BFTSU_MESSAGE.equals(messageType)) {
			logger.info("Received " + messageType + " message type from "+otherPeerID+". Notifying Observers... ");
			notify(messageReceived);
		} else {
			logger.log(Level.WARNING, "Received unexpected message type (expected: " + BFTSU_MESSAGE + ", received: " + messageType);
		}
	}

	/**
	 * Checks whether the protocol was stopped.
	 * @return true if the protocol was stopped, false otherwise.
	 */
	protected boolean wasIStopped() {
		// Leave if someone stopped you
		if (stopper.isStopped()) {
			logger.log(Level.INFO, "Protocol thread handling "+otherPeerID+" was stopped, returning...");
			return true;
		}
		return false;
	}
}
