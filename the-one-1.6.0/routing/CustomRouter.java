package routing;

import java.util.ArrayList;
import java.util.List;

import core.Message;
import core.Settings;
import core.Connection;
import core.DTNHost;
import core.Message;
import core.MessageListener;
import core.NetworkInterface;
import core.Settings;
import core.SimClock;

import routing.customRouter.*;
import util.Tuple;

public class CustomRouter extends ActiveRouter {

	public RoutingTable routingTable = new RoutingTable();
	public ArrayList<RREQ> rreqToPass = new ArrayList<RREQ>();
	public ArrayList<RREPL> rreplToPass = new ArrayList<RREPL>();

	public CustomRouter(Settings s) {
		super(s);
		routingTable.addRoute(getHost(), getHost(), 0);
		// TODO Auto-generated constructor stub
	}

	public CustomRouter(ActiveRouter r) {
		super(r);
		routingTable.addRoute(getHost(), getHost(), 0);
		// TODO Auto-generated constructor stub
	}

	@Override
	public MessageRouter replicate() {
		// TODO Auto-generated method stub
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

	public boolean isInToPass(RREPL rrepl) {
		for (RREPL value : rreplToPass) {
			if (value.destinationID == rrepl.destinationID) {
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
		///////////////////////////////////////////////////////////

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
					peer.getRouter().passRREQ(con, toPass);
				}
			}
		}

		ArrayList<RREPL> toRemove = new ArrayList<RREPL>();
		// Pass the RREPL messages:
		//ArrayList<RREPL> tempRreplToPass = new ArrayList<RREPL>(rreplToPass);
		for (RREPL rrepl : rreplToPass) {
			RoutingTableEntry route = routingTable.getRoute(rrepl.destinationID);
			if (route == null) {
				// I don't know where to pass the RREPL?????
				System.out.println("I don't know where to pass the RREPL?????");
			} else {
				for (Connection con : connections) {
					if (con.isUp()) {
						DTNHost peer = con.getOtherNode(getHost());
						if (peer == route.nextHop) {
							RREPL toPass = new RREPL(rrepl);
							toPass.hopCount++;
							peer.getRouter().passRREPL(con, toPass);
							toRemove.add(rrepl);
						}
					}

				}

			}
		}
		rreplToPass.removeAll(toRemove);

	}

	@Override
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
			RREPL toPass = new RREPL(route.destinationID, rreq.sourceID);
			toPass.hopCount = route.hops + rreq.hopCount;
			peer.getRouter().passRREPL(con, toPass);
		}

	}

	@Override
	public void passRREPL(Connection con, RREPL rrepl) {
		
		if (SimClock.getIntTime() - rrepl.sequenceNumber >= rrepl.lifetime) {
			// Lifetime exceeded, RREPL discarded
			System.out.println("Lifetime exceeded, RREPL discarded");
		}

		DTNHost peer = con.getOtherNode(getHost());

		routingTable.addRoute(rrepl.sourceID, peer, rrepl.hopCount);

		if (rrepl.destinationID == getHost()) {
			// This is RREPL for me. I do not need to search for it using RREQ

			ArrayList<RREQ> toRemove = new ArrayList<RREQ>();
			for (RREQ rreq : rreqToPass) {
				if (rreq.destinationID == rrepl.sourceID) {
					toRemove.add(rreq);
				}
			}
			rreqToPass.removeAll(toRemove);

		} else {
			// I need to pass this RREPL, it is for someone else
			if (isInToPass(rrepl)) {
				// I already have is in my isInToPass.
				return;
			} else {
				// I need to put it in my isInToPass;
				RREPL toAdd = new RREPL(rrepl);
				toAdd.hopCount++;
				rreplToPass.add(toAdd);
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
