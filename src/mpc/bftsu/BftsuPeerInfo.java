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


/**
 * stores information about a bftsu (privacy) peer
 * 
 * @author Dilip Many
 *
 */
public class BftsuPeerInfo {

	private String ID;
	private int index;
	
	/** indicates if the initial shares were received */
	private boolean isInitialSharesReceived = false;

	/** contains the initial shares */
	private long[] initialShares = null;

	/**
	 * Creates a new bftsu info object
	 */
	public BftsuPeerInfo(String ID, int index) {
		this.ID = ID;
		this.index = index;
	}

	
	public boolean isInitialSharesReceived() {
		return isInitialSharesReceived;
	}

	public void setIsInitialSharesReceived(boolean isInitialSharesReceived) {
		this.isInitialSharesReceived = isInitialSharesReceived;
	}

	public String getID() {
		return ID;
	}


	public void setID(String iD) {
		ID = iD;
	}


	public int getIndex() {
		return index;
	}


	public void setIndex(int index) {
		this.index = index;
	}


	/**
	 * Returns the initial shares.
	 * @note This can be <code>null</code> if an input peer is offline.
	 *
	 * @return	the initial shares
	 */
	public long[] getInitialShares() {
		return initialShares;
	}


	/**
	 * sets the initial shares
	 *
	 * @param initialShares	the initial shares to set
	 */
	public void setInitialShares(long[] initialShares) {
		this.initialShares = initialShares;
	}
}
