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

package mpc.bfwsi;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.Level;

import mpc.protocolPrimitives.PrimitivesException;
import services.Stopper;
import services.Utils;
import connections.PrivacyViolationException;

/**
 * Protocol between a privacy peer and another privacy peer for the bfwsi protocol.
 *
 * @author Dilip Many, Manuel Widmer
 *
 */
public class BfwsiProtocolPrivacyPeerToPP extends BfwsiProtocol {

	/** reference to bfwsi privacy peer object that started this protocol instance */
	protected BfwsiPrivacyPeer privacyPeer;



	/**
	 * Creates a new instance of the bfwsi protocol between two privacy peers.
	 *
	 * @param threadNumber				Protocol's thread number
	 * @param privacyPeer		the privacy peer instantiating this thread
	 * @param privacyPeerID				the counterpart privacy peer
	 * @param privacyPeerIndex			the counterpart privacy peer's index
	 * @param stopper					Stopper to stop protocol thread
	 * @throws Exception
	 */
	
	public BfwsiProtocolPrivacyPeerToPP(int threadNumber, BfwsiPrivacyPeer privacyPeer, String privacyPeerID, int privacyPeerIndex, Stopper stopper) {
		super(threadNumber, privacyPeer, privacyPeerID, privacyPeerIndex, stopper);
		this.privacyPeer = privacyPeer;
	}


	/**
	 * Run the MPC bfwsi protocol for the privacy peer
	 */
	public synchronized void run() {
		initialize(privacyPeer.getTimeSlotCount(), privacyPeer.getNumberOfItems(), privacyPeer.getNumberOfInputPeers());

		// wait for all shares
		logger.log(Level.INFO, "thread " + Thread.currentThread().getId() + " waits for all shares to arrive...");
		privacyPeer.waitForNextPPProtocolStep();
		if(wasIStopped()) {
			return;
		}

		CyclicBarrier ppThreadsBarrier = privacyPeer.getBarrierPP2PPProtocolThreads();
		try {
			/*
			 * One thread always prepares the data for the next step and then all threads
			 * enter doOperations() and process the operations in parallel.
			 */
			if (ppThreadsBarrier.await()==0) {
				// compute bfwsi
				privacyPeer.startBfwsi();
			}
			
			ppThreadsBarrier.await();
			if(!doOperations()) {
				logger.severe("Computing bfwsi failed; returning...");
				return;
			}
			
			if (ppThreadsBarrier.await()==0) {
				// reconstruct
				privacyPeer.startFinalResultReconstruction();
			}
			
			ppThreadsBarrier.await();
			if(!doOperations()) {
				logger.severe("Final result reconstruction failed; returning...");
				return;
			}
			
			if (ppThreadsBarrier.await()==0) {
				privacyPeer.setFinalResult();
				logger.log(Level.INFO, "Bfwsi protocol round completed");
			}
		} catch (PrimitivesException e) {
			logger.severe(Utils.getStackTrace(e));
		} catch (InterruptedException e) {
			logger.severe(Utils.getStackTrace(e));
		} catch (BrokenBarrierException e) {
			logger.severe(Utils.getStackTrace(e));
		} catch (PrivacyViolationException e) {
			logger.severe(Utils.getStackTrace(e));
		}
	}
}
