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

import java.util.Collections;
import java.util.Observable;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;

import mpc.protocolPrimitives.PrimitivesEnabledPeer;
import services.BloomFilter;
import services.Stopper;
import services.Utils;
import startup.ConfigFile;
import connections.ConnectionManager;
import events.ExceptionEvent;


/**
 * This abstract class contains the functionality common to peers and
 * privacy peers of the MPC Bfwsi protocol.
 *
 * @author Dilip Many, Manuel Widmer
 *
 */
public abstract class BfwsiBase extends PrimitivesEnabledPeer {

	protected String inputFolder;
	protected String outputFolder;
	protected int inputTimeout;

	/** number of time slots */
	protected int timeSlotCount = 1;
	/** number of items per time slot */
	protected int numberOfItems = 0;
	/** the alpha index of this (privacy) peer */
	protected int myAlphaIndex = 0;
	/** the size of the field to use for the Shamir shares computations */
	protected long shamirSharesFieldOrder = 0;
	/** the degree of the polynomials to use*/
	protected int degreeT = -1;
	/** contains the final results */
	protected long[] finalResults = null;
	/** the size of the Bloom filter */
	protected int bloomFilterSize = 0;
	/** number of hash functions used for the Bloom filter */
	protected int numberOfHashFunctions = 1;
	/** counting or non-counting Bloom filter */
	//protected boolean bfIsCounting = false; not used in this protocol
	/** number of Items to Read from the input File */
	protected int itemsToRead = 0;
	
	/** Thresholds for the weighted set intersection */
	protected long keyThreshold = 0;
	protected boolean learnWeights = false;

	
	/** prefix of all bfwsi protocol properties */
	public static final String PROP_BLOOM_PREFIX = "bloomfilter.";
	public static final String PROP_BLOOMF_NUM_OF_HASHES = PROP_BLOOM_PREFIX + "hashcount";
	public static final String PROP_BLOOMF_SIZE = PROP_BLOOM_PREFIX + "size";
	//public static final String PROP_BLOOMF_COUNTING = PROP_BLOOM_PREFIX + "iscounting";
	
	public static final String PROP_BFWSI_KEYTHRESHOLD = "mpc.bfwsi.keythreshold";
	public static final String PROP_BFWSI_LEARNWEIGHTS = "mpc.bfwsi.learnweights";
	
	
	/**
	 * Creates a new MPC bfwsi peer instance
	 * 
	 * @param myPeerIndex	This peer's number/index
	 * @param stopper		Stopper (can be used to stop this thread)
	 * @param cm the connection manager
	 */
	public BfwsiBase(int myPeerIndex, ConnectionManager cm, Stopper stopper) {
		super(myPeerIndex, cm, stopper);
		protocolStopper = new Stopper();
	}

	/**
	 * Init the properties.
	 */
	protected synchronized void initProperties() throws Exception {
		Properties properties = ConfigFile.getInstance().getProperties();
		
        inputFolder = properties.getProperty(ConfigFile.PROP_INPUT_DIR, ConfigFile.DEFAULT_INPUT_DIR);
        outputFolder = properties.getProperty(ConfigFile.PROP_OUTPUT_DIR, ConfigFile.DEFAULT_OUTPUT_DIR);
    	inputTimeout = Integer.valueOf(properties.getProperty(ConfigFile.PROP_INPUT_TIMEOUT, ConfigFile.DEFAULT_INPUT_TIMEOUT));
		
		randomAlgorithm = properties.getProperty(ConfigFile.PROP_PRG, ConfigFile.DEFAULT_PRG);
		random = new Random();

		timeSlotCount = Integer.valueOf(properties.getProperty(ConfigFile.PROP_NUMBER_OF_TIME_SLOTS));
		// number of Items is exactly the bloomfiltersize
		// IMPORTANT: we have to make sure "numberOfItems" is the exact Bloom filter length
    	// or we will get errors later because only the "numberOfItems" first entries of
    	// the Bloom filter will be reconstructed.
		// E.g PROP_BLOOMF_SIZE = 25 -> actual length will be 32 since always the next 
		// larger power of 2 is used.
		numberOfItems = BloomFilter.getNextPowerOfTwo(
				Integer.valueOf(properties.getProperty(PROP_BLOOMF_SIZE)));
		minInputPeers = Integer.valueOf(properties.getProperty(ConfigFile.PROP_MIN_INPUTPEERS));
		minPrivacyPeers = Integer.valueOf(properties.getProperty(ConfigFile.PROP_MIN_PRIVACYPEERS));
		setMyPeerID(properties.getProperty(ConfigFile.PROP_MY_PEER_ID));
		shamirSharesFieldOrder = Long.valueOf(properties.getProperty(ConfigFile.PROP_FIELD, ConfigFile.DEFAULT_FIELD));
		degreeT = Integer.valueOf(properties.getProperty(ConfigFile.PROP_DEGREE, "-1"));

        myAlphaIndex = Collections.binarySearch(connectionManager.getConfiguredPrivacyPeerIDs(), getMyPeerID());
        
		// Protocol specific
		itemsToRead = Integer.valueOf(properties.getProperty(ConfigFile.PROP_NUMBER_OF_ITEMS));
		numberOfHashFunctions = Integer.valueOf(properties.getProperty(PROP_BLOOMF_NUM_OF_HASHES));
        bloomFilterSize = Integer.valueOf(properties.getProperty(PROP_BLOOMF_SIZE));
//        bfIsCounting = Boolean.valueOf(properties.getProperty(PROP_BLOOMF_COUNTING));
        
        keyThreshold = Long.valueOf(properties.getProperty(PROP_BFWSI_KEYTHRESHOLD));
        learnWeights = Boolean.valueOf(properties.getProperty(PROP_BFWSI_LEARNWEIGHTS));
      
		
		// output properties to log
		logger.log(Level.INFO, "The following properties were set:");
		logger.log(Level.INFO, "time slot count: " + timeSlotCount);
		logger.log(Level.INFO, "number of items per time slot: " + numberOfItems);
		logger.log(Level.INFO, "random algorithm: " + randomAlgorithm);
		logger.log(Level.INFO, "minInputPeers: " + minInputPeers);
		logger.log(Level.INFO, "minPrivacyPeers: " + minPrivacyPeers);
		logger.log(Level.INFO, "Shamir shares field order: " + shamirSharesFieldOrder);
		logger.log(Level.INFO, "Shamir shares polynomial degree: " + degreeT);
		logger.log(Level.INFO, "myID: " + getMyPeerID());
		logger.log(Level.INFO, "my alpha index: " + myAlphaIndex);
		logger.log(Level.INFO, "Items to read: " + itemsToRead);
		logger.log(Level.INFO, "Number of hashfunctions: " + numberOfHashFunctions);
		logger.log(Level.INFO, "Bloom filter size: " + bloomFilterSize);
//		logger.log(Level.INFO, "Bloom filter is counting: " + bfIsCounting);
		
	}


	/**
	 * Process message received by an observable.
	 * 
	 * @param observable	Observable who sent the notification
	 * @param object		The object that was sent by the observable
	 */
	protected abstract void notificationReceived(Observable observable, Object object) throws Exception;


	/**
	 * Invoked when an observable that we're observing is notifying its
	 * observers
	 * 
	 * @param observable	Observable who sent the notification
	 * @param object		The object that was sent by the observable
	 */
	public synchronized void update(Observable observable, Object object) {
		ExceptionEvent exceptionEvent;
		String errorMessage;
		
		if(object==null) {
			logger.severe("Received a null message from observable: " + observable.getClass().getName());
			return;
		}
		
		logger.log(Level.INFO, "Received notification from observable: " + observable.getClass().getName() + " (object is of type: " + object.getClass().getName() + ")");

		try {
			/* !!! WATCH OUT FOR ORDER (e.g. BfwsiMessage will go for
			 * !!! MpcMessage, too, since it is subclassing it!) -> always check
			 * !!! subclasses first
			 */
			if (object instanceof BfwsiMessage) {
				notificationReceived(observable, object);

			} else if (object instanceof ExceptionEvent) {
				exceptionEvent = (ExceptionEvent) object;
				logger.log(Level.SEVERE, "Received Exception Event..." + exceptionEvent.getMessage());
				sendExceptionEvent(exceptionEvent);

			} else {
				errorMessage = "Unexpected message type: " + object.getClass().getName();
				logger.log(Level.SEVERE, errorMessage);
				sendExceptionEvent(this, errorMessage);
			}

		} catch (Exception e) {
			errorMessage = "Error when processing event: " + Utils.getStackTrace(e);
			logger.log(Level.SEVERE, errorMessage);
			sendExceptionEvent(this, e, errorMessage);
		}
	}

	/**
	 * Does some cleaning up.
	 */
	protected synchronized void cleanUp() throws Exception {
		// Stop all started threads
		stopProcessing();
	}
}
