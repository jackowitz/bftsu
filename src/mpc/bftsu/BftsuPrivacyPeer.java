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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Vector;
import java.util.logging.Level;

import mpc.CountingBarrier;
import mpc.VectorData;
import mpc.protocolPrimitives.Primitives;
import mpc.protocolPrimitives.PrimitivesEnabledProtocol;
import services.Services;
import services.Stopper;
import connections.ConnectionManager;
import events.FinalResultEvent;

/**
 * A MPC privacy peer with the computation capabilities for the bftsu protocol
 * 
 * @author Dilip Many, Manuel Widmer
 *
 */
public class BftsuPrivacyPeer extends BftsuBase {

	/** vector of protocols (between this privacy peer and the peers) */
	private Vector<BftsuProtocolPrivacyPeerToPeer> peerProtocolThreads = null;
	/** vector of protocols (between this privacy peer and other privacy peers) */
	private Vector<BftsuProtocolPrivacyPeerToPP> ppToPPProtocolThreads = null;
	/** vector of information objects for the connected peers */
	protected Vector<BftsuPeerInfo> peerInfos = null;
	/** vector of information objects for the connected privacy peers */
	protected Vector<BftsuPeerInfo> privacyPeerInfos = null;
	/** barrier to synchronize the peerProtocolThreads threads */
	private CountingBarrier peerProtocolBarrier = null;
	/** barrier to synchronize the ppToPPProtocolThreads threads */
	private CountingBarrier ppProtocolBarrier = null;

	/** number of input peers connected to this one */
	protected int numberOfInputPeers = 0;
	/** number of initial shares that the privacy peer yet has to receive */
	private int initialSharesToReceive = 0;

	/**
	 * creates a new MPC bftsu privacy peer
	 *
	 * @param myPeerIndex	This peer's number/index
	 * @param stopper		Stopper (can be used to stop this thread)
	 * @param cm 			the connection manager
	 * @throws Exception
	 */
	public BftsuPrivacyPeer(int myPeerIndex, ConnectionManager cm, Stopper stopper) throws Exception {
		super(myPeerIndex, cm, stopper);

		peerInfos = new Vector<BftsuPeerInfo>();
		privacyPeerInfos = new Vector<BftsuPeerInfo>();
		peerProtocolThreads = new Vector<BftsuProtocolPrivacyPeerToPeer>();
		ppToPPProtocolThreads = new Vector<BftsuProtocolPrivacyPeerToPP>();
	}

	/**
	 * Initializes the privacy peer
	 */
	public void initialize() throws Exception {
		initProperties();

		currentTimeSlot = 1;
	}


	/**
	 * Initializes a new round of computation.
	 */
	protected void initializeNewRound() {
		connectionManager.waitForConnections();
		connectionManager.activateTemporaryConnections();
		PrimitivesEnabledProtocol.newStatisticsRound();
		
		// Get all the active privacy peer IDs. Note that these are not necessarily all PPs configured in the config file.
		List<String> privacyPeerIDs = connectionManager.getActivePeers(true);
		List<String> inputPeerIDs = connectionManager.getActivePeers(false);
		Map<String, Integer> ppIndexMap = getIndexMap(privacyPeerIDs);
		myAlphaIndex = ppIndexMap.get(myPeerID);
		
		numberOfPrivacyPeers = privacyPeerIDs.size()+1; // Count myself
		numberOfInputPeers = inputPeerIDs.size();
		peerProtocolBarrier = new CountingBarrier(numberOfInputPeers);
		ppProtocolBarrier = new CountingBarrier(numberOfPrivacyPeers-1);
		clearPP2PPBarrier();
		
		// init counters
		initialSharesToReceive = numberOfInputPeers;
		finalResultsToDo = numberOfInputPeers;
		finalResults = null;
		
		primitives = new Primitives(randomAlgorithm, shamirSharesFieldOrder, degreeT, numberOfPrivacyPeers, myAlphaIndex, numberOfPrivacyPeers-1);
		createProtocolThreadsForInputPeers(inputPeerIDs);
		createProtocolThreadsForPrivacyPeers(privacyPeerIDs, ppIndexMap);
	}

	/**
	 * Generates a consistent mapping from active privacy peer IDs to privacy peer indices.
	 * @param connectedPrivacyPeerIDs all connected PPs, without myself.
	 * @return the index map
	 */
	private Map<String,Integer> getIndexMap(List<String> connectedPrivacyPeerIDs) {
		List<String> allPPsorted = new ArrayList<String>();
		allPPsorted.addAll(connectedPrivacyPeerIDs);
		allPPsorted.add(getMyPeerID());
		
		Collections.sort(allPPsorted);
		HashMap<String,Integer> indexMap = new HashMap<String, Integer>();
		for(int index=0; index<allPPsorted.size(); index++) {
			indexMap.put(allPPsorted.get(index), index);
		}
		return indexMap;
	}
	
	/**
	 * Create and start the threads. Attach one privacy peer id to each of them.
	 * 
	 * @param privacyPeerIDs
	 *            the ids of the privacy peers
	 * @param ppIndexMap
	 * 			  a map mapping privacy peer IDs to indices
	 */
	private void createProtocolThreadsForPrivacyPeers(List<String> privacyPeerIDs, Map<String, Integer> ppIndexMap) {
		ppToPPProtocolThreads.clear();
		privacyPeerInfos.clear();
		int currentID =0;
		for(String ppId: privacyPeerIDs) {
			logger.log(Level.INFO, "Create a thread for privacy peer " +ppId );
			int otherPPindex = ppIndexMap.get(ppId);
			BftsuProtocolPrivacyPeerToPP pp2pp = new BftsuProtocolPrivacyPeerToPP(currentID, this, ppId, otherPPindex, stopper);
			pp2pp.setMyPeerIndex(myAlphaIndex);
			pp2pp.addObserver(this);
			Thread thread = new Thread(pp2pp, "Bftsu PP-to-PP protocol connected with " + ppId);
			ppToPPProtocolThreads.add(pp2pp);
			privacyPeerInfos.add(currentID, new BftsuPeerInfo(ppId, otherPPindex));
			thread.start();
			currentID++;
		}
	}

	/**
	 * Create and start the threads. Attach one input peer id to each of them.
	 * 
	 * @param inputPeerIDs
	 *            the ids of the input peers
	 */
	private void createProtocolThreadsForInputPeers(List<String> inputPeerIDs) {
		peerProtocolThreads.clear();
		peerInfos.clear();
		int currentID = 0;
		for(String ipId: inputPeerIDs) {
			logger.log(Level.INFO, "Create a thread for input peer " +ipId );
			BftsuProtocolPrivacyPeerToPeer pp2p = new BftsuProtocolPrivacyPeerToPeer(currentID, this, ipId, currentID, stopper);
			pp2p.addObserver(this);
			Thread thread = new Thread(pp2p, "Bftsu Peer protocol connected with " + ipId);
			peerProtocolThreads.add(pp2p);
			peerInfos.add(currentID, new BftsuPeerInfo(ipId, currentID));
			thread.start();
			currentID++;
		}
	}

	/**
	 * Run the MPC protocol(s) over the given connection(s).
	 */
	public synchronized void runProtocol() {
		// All we need to do here is starting the first round
		initializeNewRound();
	}

	/**
	 * Process message received by an observable.
	 * 
	 * @param observable	Observable who sent the notification
	 * @param object		The object that was sent by the observable
	 */
	protected void notificationReceived(Observable observable, Object object) throws Exception {
		if (object instanceof BftsuMessage) {
			BftsuMessage msg = (BftsuMessage) object;
			// We are awaiting a message with initial shares 
			if (msg.isDummyMessage()) {
				// Counterpart is offline. Simulate an initial shares message.
				msg.setIsInitialSharesMessage(true);
			} 
			
			if (msg.isInitialSharesMessage()) {
				logger.log(Level.INFO, "Received shares from peer: " + msg.getSenderID());
				BftsuPeerInfo peerInfo = getPeerInfoByPeerID(msg.getSenderID());
				peerInfo.setInitialShares(msg.getInitialShares());

				initialSharesToReceive--;
				if (initialSharesToReceive <= 0) {
					logger.log(Level.INFO, "Received all initial shares from peers...");
					startNextPPProtocolStep();
				}

			} else {
				String errorMessage = "Didn't receive initial shares...";
				errorMessage += "\nisGoodBye: "+msg.isGoodbyeMessage();  
				errorMessage += "\nisHello: "+msg.isHelloMessage();
				errorMessage += "\nisInitialShares: "+msg.isInitialSharesMessage();
				errorMessage += "\nisFinalResult: "+msg.isFinalResultMessage();
				logger.log(Level.SEVERE, errorMessage);
				sendExceptionEvent(this, errorMessage);
			}
		} else {
			throw new Exception("Received unexpected message type (expected: " + BftsuMessage.class.getName() + ", received: " + object.getClass().getName());
		}
	}


	/**
	 * returns the number of peers connected to this one
	 */
	public int getNumberOfInputPeers() {
		return numberOfInputPeers;
	}


	/**
	 * returns the number of time slots
	 */
	public int getTimeSlotCount() {
		return timeSlotCount;
	}


	/**
	 * @return the numberOfItems per time slot
	 */
	public int getNumberOfItems() {
		return numberOfItems;
	}


	/**
	 * Returns the peer info for the PRIVACY PEER with the given user number, 
	 * which corresponds to the index of this privacy peer's elements in the list
	 * (null if user not in list)
	 *
	 * @param privacyPeerNumber	The privacy peer's number in the list
	 *
	 * @return The privacy peers info instance (null if not found)
	 */
	protected synchronized BftsuPeerInfo getPrivacyPeerInfoByIndex(int privacyPeerNumber) {
		return privacyPeerInfos.elementAt(privacyPeerNumber);
	}


	/**
	 * Returns the peer info for the INPUT PEER with the given user number, which
	 * corresponds to the index of this privacy peer's elements in the list
	 * (null if user not in list)
	 *
	 * @param peerNumber	the peer's number in the list
	 *
	 * @return The input peers info instance (null if not found)
	 */
	protected synchronized BftsuPeerInfo getPeerInfoByIndex(int peerNumber) {
		return peerInfos.elementAt(peerNumber);
	}


	/**
	 * Returns the peer info for the PEER with the given peer ID.
	 * 
	 * @param peerID	The peer's ID
	 * 
	 * @return The peers info instance (null if not found)
	 */
	protected synchronized BftsuPeerInfo getPeerInfoByPeerID(String peerID) {
		for (BftsuPeerInfo peerInfo : peerInfos) {
			if (peerInfo.getID() == null) {
				logger.log(Level.WARNING, "There is a peerInfo without a peerID! " + peerInfo.getIndex());
			}
			else if (peerInfo.getID().equals(peerID)) {
				return peerInfo;
			}
		}
		return null;
	}


	/**
	 * Wait until the privacy peer is ready for the next PeerProtocol step. 
	 * @throws InterruptedException
	 */
	public void waitForNextPeerProtocolStep() {
		logger.log(Level.INFO, "PeerProtocol Barrier: Thread nr. "+(peerProtocolBarrier.getNumberOfWaitingThreads()+1)+" arrived.");
		try {
			peerProtocolBarrier.block();
		} catch (InterruptedException e) {
			// ignore
		}
	}


	/**
	 * Starts the next PeerProtocol step. 
	 * @throws InterruptedException
	 */
	protected void startNextPeerProtocolStep() {
		logger.log(Level.INFO, "PeerProtocol Opening the barrier. PeerProtocol Threads can start the next step.");
		try {
			peerProtocolBarrier.openBarrier();
		} catch (InterruptedException e) {
			// ignore
		}
	}


	/**
	 * Wait until the privacy peer is ready for the next PPProtocol step. 
	 * @throws InterruptedException
	 */
	public void waitForNextPPProtocolStep() {
		logger.log(Level.INFO, "PPProtocol Barrier: Thread nr. "+(ppProtocolBarrier.getNumberOfWaitingThreads()+1)+" arrived.");
		try {
			ppProtocolBarrier.block();
		} catch (InterruptedException e) {
			// ignore
		}
	}


	/**
	 * Starts the next PPProtocol step. 
	 * @throws InterruptedException
	 */
	protected void startNextPPProtocolStep() throws InterruptedException {
		logger.log(Level.INFO, "PPProtocol Opening the barrier. PPProtocol Threads can start the next step.");
		ppProtocolBarrier.openBarrier();
	}


	/**
	 * starts the product computations
	 */
	public void startProductComputations() {
		logger.log(Level.INFO, Services.getFilterPassingLogPrefix()+ "STARTING Bftsu Protocol round...");
		int activeInputPeers = connectionManager.getNumberOfConnectedPeers(false, true);
		// create product operation set
		initializeNewOperationSet(numberOfItems);
		operationIDs = new int[numberOfItems];
		long[] data = null;
		for(int operationIndex = 0; operationIndex < numberOfItems; operationIndex++) {
			operationIDs[operationIndex] = operationIndex;
			data = new long[activeInputPeers];
			int dataIndex=0;
			for(int peerIndex = 0; peerIndex < numberOfInputPeers; peerIndex++) {
				long[] initialShares = getPeerInfoByIndex(peerIndex).getInitialShares();
				if(initialShares!=null) { // only consider active input peers
					data[dataIndex++] = initialShares[operationIndex];
				}
			}
			primitives.product(operationIndex, data);
		}
		logger.log(Level.INFO, "thread " + Thread.currentThread().getId() + " started the "+operationIDs.length+" product operations...");
	}


	/**
	 * computes the function on the received shares
	 */
	public void startBftsu() {
		logger.log(Level.INFO, Services.getFilterPassingLogPrefix()+ "STARTING Bftsu Protocol round...");
		int activeInputPeers = connectionManager.getNumberOfConnectedPeers(false, true);
		
		//create bftsu operation set
		initializeNewOperationSet(1);
		operationIDs = new int[1];
		operationIDs[0] = 0;
		int dataIndex = 0;
		// data[filter x][position i]
		long[][] data = new long[activeInputPeers][numberOfItems];
		for(int peerIndex = 0; peerIndex < numberOfInputPeers; peerIndex++) {
		// collect all Bloom filter shares
			long [] initialShares = getPeerInfoByIndex(peerIndex).getInitialShares();
			if(initialShares!=null) { // only consider active input peers
				System.arraycopy(initialShares, 0, data[dataIndex], 0, numberOfItems);
				dataIndex++;
			}
		}
		if(!primitives.bfThresholdUnion(operationIDs[0], data, threshold, learnWeights)) {
			Services.printVector("SEVERE: bfBftsu operation arguments are invalid: id=0; data: ", data[0], logger);
		}
		logger.log(Level.INFO, "thread " + Thread.currentThread().getId() + " started the "+operationIDs.length+" bftsu operations...");

	}
	
	/**
	 * starts the reconstruction of the final result
	 */
	public void startFinalResultReconstruction() {
		// get bftsu operation result
		long[] result = primitives.getResult(operationIDs[0]);

		initializeNewOperationSet(result.length);
		operationIDs = new int[result.length];
		long[] data = null;
		for(int i = 0; i < result.length; i++) {
			// create reconstruction operation for result of product operation
			operationIDs[i] = i;
			data = new long[1];
			data[0] = result[i];
			if(!primitives.reconstruct(operationIDs[i], data)) {
				logger.log(Level.SEVERE, "reconstruct operation arguments are invalid: id="+operationIDs[i]+", data="+data[0]);
			}
		}
		logger.log(Level.INFO, "thread " + Thread.currentThread().getId() + " started the final result reconstruction; (" + operationIDs.length + " reconstruction operations are in progress)");
	}


	/**
	 * retrieves and stores the final result
	 */
	public void setFinalResult() {
		logger.info("Thread " + Thread.currentThread().getId() + " called setFinalResult");
		finalResults = new long[operationIDs.length];
		for(int i = 0; i < operationIDs.length; i++) {
			finalResults[i] = primitives.getResult(operationIDs[i])[0];
		}
		logger.info("Thread " + Thread.currentThread().getId() + " starts next pp-peer protocol step...");
		startNextPeerProtocolStep();
	}

	/**
	 * @return the final result
	 */
	public long[] getFinalResult() {
		return finalResults;
	}

	/**
	 * lets protocol thread report to privacy peer that it sent the final result and
	 * starts new round if there are more time slots (data) to process
	 */
	protected synchronized void finalResultIsSent() {
		finalResultsToDo--;
		logger.log(Level.INFO, "thread " + Thread.currentThread().getId() + " called finalResultIsSent; finalResultsToDo="+finalResultsToDo);
		if(finalResultsToDo <= 0) {
			// report final result to observers
			logger.log(Level.INFO, Services.getFilterPassingLogPrefix()+ "Sent all final results. Notifying observers...");
			VectorData dummy = new VectorData(); // dummy data to avoid null pointer exception in Peers::processMpcEvent
			FinalResultEvent finalResultEvent;
			finalResultEvent = new FinalResultEvent(this, myAlphaIndex, getMyPeerID(), getMyPeerID(), dummy);
			finalResultEvent.setVerificationSuccessful(true);
			sendNotification(finalResultEvent);
			// check if there are more time slots to process
			if(currentTimeSlot < timeSlotCount) {
				currentTimeSlot++;
				logger.log(Level.INFO, "thread " + Thread.currentThread().getId() + " increased currentTimeSlot to "+currentTimeSlot+", will init new round now...");
				initializeNewRound();
			} else {
				logger.log(Level.INFO, "No more data available... Stopping protocol threads...");
				protocolStopper.setIsStopped(true);
			}
		}
	}
}
