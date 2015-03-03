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

import java.io.Serializable;

import mpc.MessageBase;


/**
 * message used to exchange data among (privacy) peers in the bftsu protocol
 *
 * @author Dilip Many, Manuel Widmer
 *
 */
public class BftsuMessage extends MessageBase implements Serializable {
	private static final long serialVersionUID = 3461683923455914692L;

	/**
	 * Message Type Flags
	 */
	/** indicates if the message contains the initial shares */
	private boolean isInitialSharesMessage = false;
	/** indicates if the message contains the final results */
	private boolean isFinalResultMessage = false;

	/** contains the initial shares */
	private long[] initialShares = null;
	/** contains the final results */
	private long[] finalResults = null;


	/**
	 * creates a new bftsu protocol message with the specified sender id and index
	 *
	 * @param senderID		the senders id
	 * @param senderIndex	the senders index
	 */
	public BftsuMessage(String senderID, int senderIndex) {
		super(senderID, senderIndex);
	}

	public boolean isInitialSharesMessage() {
		return isInitialSharesMessage;
	}

	public void setIsInitialSharesMessage(boolean isInitialSharesMessage) {
		this.isInitialSharesMessage = isInitialSharesMessage;
	}


	public boolean isFinalResultMessage() {
		return isFinalResultMessage;
	}

	public void setIsFinalResultMessage(boolean isFinalResultMessage) {
		this.isFinalResultMessage = isFinalResultMessage;
	}



	/**
	 * @return the initial shares
	 */
	public long[] getInitialShares() {
		return initialShares;
	}


	/**
	 * sets the initial shares
	 *
	 * @param shares	the initial shares to set
	 */
	public void setShares(long[] shares) {
		this.initialShares = shares;
	}



	/**
	 * @return the final results
	 */
	public long[] getResults() {
		return finalResults;
	}


	/**
	 * @param finalResults the finalResults to set
	 */
	public void setResults(long[] finalResults) {
		this.finalResults = finalResults;
	}
}
