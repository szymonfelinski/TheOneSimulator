package routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import core.Message;
import core.Settings;
import core.Connection;
import core.DTNHost;
import core.SimClock;
import util.Tuple;

/**
 * AODV router which uses an implementation of AODV protocol.
 */
public class AODVRouter extends ActiveRouter {
	private class RREQ {
		public DTNHost sourceID;
		public DTNHost destinationID;
		public int sequenceNumber;
		public String broadcastID;
		public int hopCount;

		public RREQ(DTNHost sourceID, DTNHost destinationID) {
			this.sourceID = sourceID;
			this.destinationID = destinationID;
			this.sequenceNumber = SimClock.getIntTime();
			this.broadcastID = UUID.randomUUID().toString();
			this.hopCount = 0;
		}

		public RREQ(RREQ rreq) {
			this.sourceID = rreq.sourceID;
			this.destinationID = rreq.destinationID;
			this.sequenceNumber = rreq.sequenceNumber;
			this.broadcastID = rreq.broadcastID;
			this.hopCount = rreq.hopCount;
		}
	}

	private class RREP {
		public DTNHost sourceID;
		public DTNHost destinationID;
		public int sequenceNumber;
		public int hopCount;
		public int TTL;

		public RREP(DTNHost sourceID, DTNHost destinationID) {
			this.sourceID = sourceID;
			this.destinationID = destinationID;
			this.sequenceNumber = SimClock.getIntTime();
			this.hopCount = 0;
			this.TTL = 10000;
		}

		public RREP(RREP rrep) {
			this.sourceID = rrep.sourceID;
			this.destinationID = rrep.destinationID;
			this.sequenceNumber = rrep.sequenceNumber;
			this.hopCount = rrep.hopCount;
			this.TTL = rrep.TTL;
		}
	}

	private class RoutingTableEntry {
		public DTNHost destinationID;
		public DTNHost nextHop;
		public int hopNumber;
		public int sequenceNumber;

		public RoutingTableEntry(DTNHost destinationID, DTNHost nextHop, int hopNumber) {
			this.destinationID = destinationID;
			this.nextHop = nextHop;
			this.hopNumber = hopNumber;
			this.sequenceNumber = SimClock.getIntTime();
		}
	}

	private class RoutingTable {

		private HashMap<DTNHost, RoutingTableEntry> routingTable;
		public int DEFAULT_TIMEOUT = 10000;

		public RoutingTable() {
			this.routingTable = new HashMap<>();
		}

		public void addRoute(DTNHost destinationID, DTNHost nextHop, int hopNumber) {
			routingTable.put(destinationID, new RoutingTableEntry(destinationID, nextHop, hopNumber));
		}

		public RoutingTableEntry getRoute(DTNHost destinationID) {
			return routingTable.get(destinationID);
		}

		public void removeExpiredRoutes() {
			ArrayList<DTNHost> keys = new ArrayList<>();

			routingTable.entrySet().stream().forEach(e -> {
				if (SimClock.getIntTime() - e.getValue().sequenceNumber >= DEFAULT_TIMEOUT) {
					keys.add(e.getKey());
				}
			});

			keys.forEach(key -> routingTable.remove(key));
		}
	}

	public RoutingTable routingTable = new RoutingTable();
	public ArrayList<RREQ> rreqToRelay = new ArrayList<RREQ>();
	public ArrayList<RREP> rrepToRelay = new ArrayList<RREP>();

	public AODVRouter(Settings s) {
		super(s);
	}

	protected AODVRouter(AODVRouter r) {
		super(r);
	}

	@Override
	public MessageRouter replicate() {
		return new AODVRouter(this);
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
		
		routingTable.removeExpiredRoutes();

		List<Message> messages = new ArrayList<Message>(this.getMessageCollection());
		List<Tuple<Message, Connection>> messagesToSend = new ArrayList<Tuple<Message, Connection>>();

		for (Message message : messages) {
			RoutingTableEntry route = routingTable.getRoute(message.getTo());
			if (route == null) {
				// No route is known, create RREQ
				RREQ newRreq = new RREQ(message.getFrom(), message.getTo());
				if (isInToRelay(newRreq) == false) {
					// Put RREQ on the broadcast list
					rreqToRelay.add(newRreq);
				}

			} else {
				for (Connection con : getConnections()) {
					DTNHost peer = con.getOtherNode(getHost());

					if (peer == route.nextHop) {
						 //System.out.println("Next hop is in range. Starting transfer.");
						messagesToSend.add(new Tuple<Message, Connection>(message, con));
						if (con.isReadyForTransfer() && con.startTransfer(getHost(), message) == RCV_OK) {
							
						}
					}
				}
				Tuple<Message, Connection> ret = tryMessagesForConnected(messagesToSend);

				if (ret != null) {
					System.out.println(ret);
				}

			}
		}

		// Broadcast all RREQ messages:
		List<Connection> connections = getConnections();

		ArrayList<RREQ> temprreqToRelay = new ArrayList<RREQ>(rreqToRelay);
		for (RREQ rreq : temprreqToRelay) {
			for (Connection con : connections) {
				if (con.isUp()) {
					if (((AODVRouter) con.getOtherNode(getHost()).getRouter()).isTransferring())
					{
						continue;
					}
					DTNHost peer = con.getOtherNode(getHost());
					RREQ toRelay = new RREQ(rreq);
					toRelay.hopCount++;
					((AODVRouter) peer.getRouter()).relayRREQ(con, toRelay);
				}
			}
		}

		ArrayList<RREP> toRemove = new ArrayList<RREP>();
		for (RREP rrep : rrepToRelay) {
			RoutingTableEntry route = routingTable.getRoute(rrep.destinationID);
			if (route == null) {
				System.out.println("No info about where to pass the RREP.");
			} else {
				for (Connection con : connections) {
					if (con.isUp()) {
						DTNHost peer = con.getOtherNode(getHost());
						if (peer == route.nextHop) {
							RREP toRelay = new RREP(rrep);
							toRelay.hopCount++;
							((AODVRouter) peer.getRouter()).relayRREP(con, toRelay);
							toRemove.add(rrep);
						}
					}

				}

			}
		}
		rrepToRelay.removeAll(toRemove);

		//tryAllMessagesToAllConnections();

	}
	
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
	}

	@Override
	public int receiveMessage(Message m, DTNHost from) {
		int retval = super.receiveMessage(m, from);
		if (retval == RCV_OK) {
			if (m.getTo() == getHost()) {
//				System.out.println("Received message addressed for this host.");
//				super.messageTransferred(m.getId(), m.getFrom());
//				this.messageTransferred(m.getId(), from);
			} else {
				this.addToMessages(m, true);
				//sendMessageToConnected(m);
			}
		}
		return retval;
	}
	
	public void relayRREQ(Connection con, RREQ rreq) {
		DTNHost peer = con.getOtherNode(getHost());

		routingTable.addRoute(rreq.sourceID, peer, rreq.hopCount);

		RoutingTableEntry route = routingTable.getRoute(rreq.destinationID);
		if (route == null) {
			// I do not know how to reach the peer
			if (isInToRelay(rreq) == false) {
				rreq.hopCount++;
				rreqToRelay.add(rreq);
			}
			return;
		} else {
			// I know the route to the peer
			RREP toRelay = new RREP(route.destinationID, rreq.sourceID);
			toRelay.hopCount = route.hopNumber + rreq.hopCount;
			((AODVRouter) peer.getRouter()).relayRREP(con, toRelay);
		}

	}

	public void relayRREP(Connection con, RREP rrep) {

		if (SimClock.getIntTime() - rrep.sequenceNumber >= rrep.TTL) {
			// Lifetime exceeded, RREP id to be discarded
			System.out.println("Lifetime exceeded");
		}

		DTNHost peer = con.getOtherNode(getHost());

		routingTable.addRoute(rrep.sourceID, peer, rrep.hopCount);

		if (rrep.destinationID == getHost()) {
			// RREP reached its target.

			ArrayList<RREQ> toRemove = new ArrayList<RREQ>();
			for (RREQ rreq : rreqToRelay) {
				if (rreq.destinationID == rrep.sourceID) {
					toRemove.add(rreq);
				}
			}
			rreqToRelay.removeAll(toRemove);

		} else {
			// RREP is for another node.
			
			//System.out.println("Passing RREP.");
			if (isInToRelay(rrep)) {
				return;
			} else {
				RREP toAdd = new RREP(rrep);
				toAdd.hopCount++;
				rrepToRelay.add(toAdd);
			}
		}
	}
	
	public boolean isInToRelay(RREQ rreq) {
		for (RREQ value : rreqToRelay) {
			if (value.destinationID == rreq.destinationID) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isInToRelay(RREP rrep) {
		for (RREP value : rrepToRelay) {
			if (value.destinationID == rrep.destinationID) {
				return true;
			}
		}
		return false;
	}

}