/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.HashMap;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;

/**
 * First contact router which uses only a single copy of the message 
 * (or fragments) and forwards it to the first available contact.
 */
public class AODVRouter extends ActiveRouter {
	private class RREQ {
		public DTNHost sourceID;
		public DTNHost destinationID;
		public int sequenceNumber;
		public String broadcastID;
		public int hopCount;
		
		public RREQ() {
			return;
		}
	}
	private class RREP {
		public DTNHost sourceID;
		public DTNHost destinationID;
		public int sequenceNumber;
		public int hopCount;
		public int TTL;
		
		public RREP() {
			return;
		}
	}
	
	private class RoutingTableEntry {
		public DTNHost destinationID;
		public DTNHost nextHop;
		public int hops;
		public int sequenceNumber;
		
		public RoutingTableEntry() {
			return;
		}
	}
	
	protected HashMap<DTNHost, RoutingTableEntry> routingTable;
	
	/**
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */
	public AODVRouter(Settings s) {
		super(s);
	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected AODVRouter(AODVRouter r) {
		super(r);
	}
	
	@Override
	protected int checkReceiving(Message m, DTNHost from) {
		int recvCheck = super.checkReceiving(m, from); 
		
		if (recvCheck == RCV_OK) {
			/* don't accept a message that has already traversed this node */
			if (m.getHops().contains(getHost())) {
				recvCheck = DENIED_OLD;
			}
		}
		
		return recvCheck;
	}
			
	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; 
		}
		
		if (exchangeDeliverableMessages() != null) {
			return; 
		}
		
		tryAllMessagesToAllConnections();
	}
	
	@Override
	protected void transferDone(Connection con) {
		/* don't leave a copy for the sender */
		this.deleteMessage(con.getMessage().getId(), false);
	}
	
	protected boolean isInToPass(RREQ) {
	}
		
	@Override
	public AODVRouter replicate() {
		return new AODVRouter(this);
	}

}