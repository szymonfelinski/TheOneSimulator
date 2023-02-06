package routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
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
			this.hopCount = 0; // ???
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
		public int DEFAULT_TIMEOUT = 1000000;

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
	public ArrayList<RREQ> rreqToPass = new ArrayList<RREQ>();
	public ArrayList<RREP> rrepToPass = new ArrayList<RREP>();

	public AODVRouter(Settings s) {
		super(s);
	}

	protected AODVRouter(AODVRouter r) {
		super(r);
	}

	@Override
	public MessageRouter replicate() {
		return new CustomRouter(this);
	}

	public boolean isInToPass(RREQ rreq) {
		for (RREQ value : rreqToPass) {
			if (value.destinationID == rreq.destinationID) {
				return true;
			}
		}
		return false;
	}

	public boolean isInToPass(RREP rrep) {
		for (RREP value : rrepToPass) {
			if (value.destinationID == rrep.destinationID) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void update() {
		super.update();
		if (isTransferring() || !canStartTransfer()) {
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			System.out.println("I have delivered the message to final recipient");
			return; // started a transfer, don't try others (yet)
		}

		routingTable.removeExpiredRoutes();

		List<Message> messages = new ArrayList<Message>(this.getMessageCollection());
		List<Tuple<Message, Connection>> messagesToSend = new ArrayList<Tuple<Message, Connection>>();

		for (Message message : messages) {
			RoutingTableEntry route = routingTable.getRoute(message.getTo());
			if (route == null) {
				// If I do not know the route for this message, i need to create RREQ
				RREQ newRreq = new RREQ(message.getFrom(), message.getTo());
				if (isInToPass(newRreq) == false) {
					// Put RREQ on the broadcast list
					rreqToPass.add(newRreq);
				}

			} else {
				// I know a route for this message
				// System.out.println("I know a route for this message!!!!111");
				for (Connection con : getConnections()) {
					DTNHost peer = con.getOtherNode(getHost());

					if (peer == route.nextHop) {
						// I have next hop in range, i am going to start transfer
						// System.out.println("I have next hop in range!");
						messagesToSend.add(new Tuple<Message, Connection>(message, con));
//						if (c.isReadyForTransfer() && c.startTransfer(getHost(), message) == RCV_OK) {
//
//							System.out.println("I have managed to transfer, RCV_OK");
//						}
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

		ArrayList<RREQ> tempRreqToPass = new ArrayList<RREQ>(rreqToPass);
		for (RREQ rreq : tempRreqToPass) {
			for (Connection con : connections) {
				if (con.isUp()) {
					DTNHost peer = con.getOtherNode(getHost());
					RREQ toPass = new RREQ(rreq);
					toPass.hopCount++;
					((AODVRouter) peer.getRouter()).passRREQ(con, toPass);
				}
			}
		}

		ArrayList<RREP> toRemove = new ArrayList<RREP>();
		// Pass the RREP messages:
		// ArrayList<RREP> tempRreplToPass = new ArrayList<RREP>(rrepToPass);
		for (RREP rrep : rrepToPass) {
			RoutingTableEntry route = routingTable.getRoute(rrep.destinationID);
			if (route == null) {
				// I don't know where to pass the RREP?????
				System.out.println("I don't know where to pass the RREP?????");
			} else {
				for (Connection con : connections) {
					if (con.isUp()) {
						DTNHost peer = con.getOtherNode(getHost());
						if (peer == route.nextHop) {
							RREP toPass = new RREP(rrep);
							toPass.hopCount++;
							((AODVRouter) peer.getRouter()).passRREP(con, toPass);
							toRemove.add(rrep);
						}
					}

				}

			}
		}
		rrepToPass.removeAll(toRemove);

	}

	public void passRREQ(Connection con, RREQ rreq) {

		DTNHost peer = con.getOtherNode(getHost());

		routingTable.addRoute(rreq.sourceID, peer, rreq.hopCount);

		RoutingTableEntry route = routingTable.getRoute(rreq.destinationID);
		if (route == null) {
			// I do not know how to reach the peer
			if (isInToPass(rreq) == false) {
				rreq.hopCount++;
				rreqToPass.add(rreq);
			}
			return;
		} else {
			// I know the route to the peer
			RREP toPass = new RREP(route.destinationID, rreq.sourceID);
			toPass.hopCount = route.hopNumber + rreq.hopCount;
			((AODVRouter) peer.getRouter()).passRREP(con, toPass);
		}

	}

	public void passRREP(Connection con, RREP rrep) {

		if (SimClock.getIntTime() - rrep.sequenceNumber >= rrep.TTL) {
			// Lifetime exceeded, RREP discarded
			System.out.println("Lifetime exceeded, RREP discarded");
		}

		DTNHost peer = con.getOtherNode(getHost());

		routingTable.addRoute(rrep.sourceID, peer, rrep.hopCount);

		if (rrep.destinationID == getHost()) {
			// This is RREP for me. I do not need to search for it using RREQ

			ArrayList<RREQ> toRemove = new ArrayList<RREQ>();
			for (RREQ rreq : rreqToPass) {
				if (rreq.destinationID == rrep.sourceID) {
					toRemove.add(rreq);
				}
			}
			rreqToPass.removeAll(toRemove);

		} else {
			// I need to pass this RREP, it is for someone else
			if (isInToPass(rrep)) {
				// I already have is in my isInToPass.
				return;
			} else {
				// I need to put it in my isInToPass;
				RREP toAdd = new RREP(rrep);
				toAdd.hopCount++;
				rrepToPass.add(toAdd);
			}
		}
	}

	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
	}

	@Override
	public int receiveMessage(Message m, DTNHost from) {
		// List<Message> messages = new ArrayList<Message>(this.getMessageCollection());

		int retval = super.receiveMessage(m, from);
		if (retval == RCV_OK) {
			if (m.getTo() == getHost()) {
//				System.out.print(getHost());
//				System.out.print(": ");
//				System.out.print(m);
//				System.out.println(" The massage was for me!");
			} else {
				this.addToMessages(m, true);
			}

		}

		return retval;
	}

}