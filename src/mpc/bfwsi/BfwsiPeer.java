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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Vector;
import java.util.logging.Level;

import mpc.ShamirSharing;
import mpc.VectorData;
import mpc.protocolPrimitives.PrimitivesEnabledProtocol;
import services.BloomFilter;
import services.DirectoryPoller;
import services.Services;
import services.Stopper;
import connections.ConnectionManager;
import events.FinalResultEvent;

/**
 * A MPC peer providing the private input data for the bfwsi protocol
 *
 * @author Dilip Many, Manuel Widmer
 *
 */
public class BfwsiPeer extends BfwsiBase {

	/** vector of protocols (between this peer and the privacy peers) */
	private Vector<BfwsiProtocolPeer> peerProtocolThreads = null;
	/** MpcShamirSharing instance to use basic operations on Shamir shares */
	protected ShamirSharing mpcShamirSharing = null;

	/** indicates if the initial shares were generated yet */
	private boolean initialSharesGenerated = false;
	/** array containing the input data of this peer for all time slots; format: [inputIndex] */
	protected long[] inputData = null;
	/** array containing my initial shares; dimensions: [numberOfPrivacyPeers][numberOfItems] */
	private long[][] initialShares = null;
	
	private DirectoryPoller poller;
	/** keeps track of the input file belonging to the current round */
	private File currentInputFile;

	/**
	 * constructs a new bfwsi peer object
	 *
	 * @param myPeerIndex	This peer's number/index
	 * @param stopper		Stopper (can be used to stop this thread)
	 * @param cm the connection manager
	 * @throws Exception
	 */
	public BfwsiPeer(int myPeerIndex, ConnectionManager cm, Stopper stopper) throws Exception {
		super(myPeerIndex, cm, stopper);
		peerProtocolThreads = new Vector<BfwsiProtocolPeer>();
		mpcShamirSharing = new ShamirSharing();
	}

	/**
	 * Initializes the peer
	 */
	public void initialize() throws Exception {
		initProperties();

		mpcShamirSharing.setRandomAlgorithm(randomAlgorithm);
		mpcShamirSharing.setFieldSize(shamirSharesFieldOrder);
		if (degreeT>0) {
			mpcShamirSharing.setDegreeT(degreeT);
		}
			
		currentTimeSlot = 1;
		
		// additional initialization code
		
   		poller = new DirectoryPoller(stopper, new File(inputFolder));
   		poller.setTimeout(inputTimeout);
		
   		// Create output folder if it does not exist
        File folder = new File(outputFolder);
    	if (!folder.exists()) {
    		folder.mkdir();
    	}
	}

	/**
	 * Initializes and starts a new round of computation. It first (re-)established connections and
	 * then creates and runs the protocol threads for the new round. 
	 */
	protected void initializeNewRound() {
		connectionManager.waitForConnections();
		connectionManager.activateTemporaryConnections();
		PrimitivesEnabledProtocol.newStatisticsRound();
		
		List<String> privacyPeerIDs = connectionManager.getActivePeers(true);
		Collections.sort(privacyPeerIDs);
		numberOfPrivacyPeers = privacyPeerIDs.size();
		mpcShamirSharing.setNumberOfPrivacyPeers(numberOfPrivacyPeers);
		mpcShamirSharing.init();
		clearPP2PPBarrier();
		
		// Init state variables
		initialSharesGenerated = false;
		initialShares = null;
		finalResults = null;
		finalResultsToDo = numberOfPrivacyPeers;

		readDataFromFile(inputFolder);
		createProtocolThreadsForPrivacyPeers(privacyPeerIDs);
	}

	
	/**
	 * Create and start the threads. Attach one privacy peer id to each of them.
	 * 
	 * @param privacyPeerIDs the ids of the privacy peers
	 */
	private void createProtocolThreadsForPrivacyPeers(List<String> privacyPeerIDs)  { 
		peerProtocolThreads.clear();
		int currentID = 0;
		for(String ppId: privacyPeerIDs) {
			logger.log(Level.INFO, "Create a thread for privacy peer " +ppId );
			BfwsiProtocolPeer bfwsiProtocolPeer = new BfwsiProtocolPeer(currentID, this, ppId, currentID, stopper);
			bfwsiProtocolPeer.addObserver(this);
			Thread thread = new Thread(bfwsiProtocolPeer, "Bfwsi Peer protocol with user number " + currentID);
			peerProtocolThreads.add(bfwsiProtocolPeer);
			thread.start();
			currentID++;
		}
	}

	/**
	 * Opens file input stream and reads the data
	 *
	 * @param inputFolderName	The file to read from
	 * @return					true if successful
	 */
	public boolean readDataFromFile(String inputFolderName) {
		// read from input file, create a Bloom filter and set inputData
		BloomFilter bf = new BloomFilter(numberOfHashFunctions, bloomFilterSize, true);

		currentInputFile = poller.getNextFile();
		try{
			FileReader fr = new FileReader(currentInputFile);
			BufferedReader br = new BufferedReader(fr);

			String adr = null;
			while(null != (adr = br.readLine())){
				bf.insert(adr);
			}
			br.close();
			fr.close();
		}catch (Exception e){
			e.printStackTrace();
		}
		// inputData is of type long, and bf.getArray is int
		// we have to copy each element b/c of the typecast
		inputData = new long[bf.getArray().length];
		for(int i = 0; i < bf.getArray().length; i++){
			inputData[i] = bf.getArray()[i];
		}

		// debug, use for short filters only!!!
		//logger.log(Level.SEVERE,"initial: "+bf.toString());

		return true;
	}


	/**
	 * Generates shares for each secret input.
	 */
	public synchronized void generateInitialShares() {
		if(!initialSharesGenerated) {
			initialSharesGenerated = true;
			logger.log(Level.INFO, "Generating initial shares...");
			initialShares = mpcShamirSharing.generateShares(inputData);
			logger.log(Level.INFO, Services.getFilterPassingLogPrefix() + "DONE generating initial shares...");
		}
	}


	/**
	 * Returns the initial shares for the privacy peer.
	 *
	 * @param privacyPeerIndex	index of privacy peer for which to return the initial shares
	 */
	protected long[] getInitialSharesForPrivacyPeer(int privacyPeerIndex) {
		return initialShares[privacyPeerIndex];
	}


	/**
	 * Run the MPC protocol(s) over the given connection(s).
	 */
	public void runProtocol() throws Exception {
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
		if (object instanceof BfwsiMessage) {
			// We are awaiting a final results message				
			BfwsiMessage bfwsiMessage = (BfwsiMessage) object;

			if(bfwsiMessage.isDummyMessage()) {
				// Simulate a final results message in order not to stop protocol execution
				bfwsiMessage.setIsFinalResultMessage(true);
			}
			
			if(bfwsiMessage.isFinalResultMessage()) {
				logger.log(Level.INFO, "Received a final result message from a privacy peer");
				finalResultsToDo--;

				if (finalResults == null && bfwsiMessage.getResults() != null) {
					finalResults = bfwsiMessage.getResults();
				}
				
				if(finalResultsToDo <= 0) {
					// notify observers about final result
					logger.log(Level.INFO, Services.getFilterPassingLogPrefix()+ "Received all final results. Notifying observers...");
					VectorData dummy = new VectorData(); // dummy data to avoid null pointer exception in Peers::processMpcEvent
					FinalResultEvent finalResultEvent = new FinalResultEvent(this, myAlphaIndex, getMyPeerID(), bfwsiMessage.getSenderID(), dummy);
					finalResultEvent.setVerificationSuccessful(true);
					sendNotification(finalResultEvent);

					writeOutputToFile();
					
					// check if there are more time slots to process
					if(currentTimeSlot < timeSlotCount) {
						currentTimeSlot++;
						initializeNewRound();
					} else {
						logger.log(Level.INFO, "No more data available... Stopping protocol threads...");
						protocolStopper.setIsStopped(true);
					}
				}
			} else {
				String errorMessage = "Didn't receive final result...";
				errorMessage += "\nisGoodBye: "+bfwsiMessage.isGoodbyeMessage();  
				errorMessage += "\nisHello: "+bfwsiMessage.isHelloMessage();
				errorMessage += "\nisInitialShares: "+bfwsiMessage.isInitialSharesMessage();
				errorMessage += "\nisFinalResult: "+bfwsiMessage.isFinalResultMessage();
				logger.log(Level.SEVERE, errorMessage);
				sendExceptionEvent(this, errorMessage);
			}
		} else {
			throw new Exception("Received unexpected message type (expected: " + BfwsiMessage.class.getName() + ", received: " + object.getClass().getName());
		}
	}


    /**
     * Write the output to a file.
     * @throws Exception 
     */
	protected void writeOutputToFile() throws Exception {
		// store finalResult as a BloomFilter
		BloomFilter bf = new BloomFilter(numberOfHashFunctions, finalResults, learnWeights);

		// debug, use for short filters only!!!
		//logger.log(Level.SEVERE,"final: "+bf.toString());
		//logger.log(Level.SEVERE, "finalResults.length: "+finalResults.length);

		String fileName = outputFolder + "/bfwsi_" + String.valueOf(getMyPeerID()).replace(":", "_") + "_round" 
			+ currentTimeSlot + ".csv";
		//bf.writeToFile(fileName);

		FileWriter fw = new FileWriter(fileName);
		BufferedWriter bw = new BufferedWriter(fw);

		FileReader fr = new FileReader(currentInputFile);
		BufferedReader br = new BufferedReader(fr);
		
		String adr = null;
		while(null != (adr = br.readLine())){
			// for each element in the input set, check if it is in the result
			if(bf.check(adr)){
				bw.write(adr);
				bw.newLine();
			}
		}
		br.close();
		fr.close();
		bw.close();
		fw.close();
	}	
}
