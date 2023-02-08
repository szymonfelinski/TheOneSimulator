package routing.customRouter;

import core.DTNHost;
import core.SimClock;

public class RoutingTableEntry {

	public DTNHost destinationID;
	public DTNHost nextHop;
	public int hops;
	public int sequenceNumber;

	public RoutingTableEntry(DTNHost destinationID, DTNHost nextHop, int hops) {
		this.destinationID = destinationID;
		this.nextHop = nextHop;
		this.hops = hops;
		this.sequenceNumber = SimClock.getIntTime();
	}

}
