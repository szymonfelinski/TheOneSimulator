package routing.customRouter;

import core.DTNHost;
import core.SimClock;

public class RREPL {
	
	private int DEFAULT_LIFETIME = 10000;

	public DTNHost sourceID;
	public DTNHost destinationID;
	public int sequenceNumber;
	public int hopCount = 0;
	public int lifetime = DEFAULT_LIFETIME;
	
	public RREPL(DTNHost sourceID, DTNHost destinationID) {
		this.sourceID = sourceID;
		this.destinationID = destinationID;
		this.sequenceNumber = SimClock.getIntTime();
		this.hopCount = 0; //???
		this.lifetime = this.DEFAULT_LIFETIME;
	}

	public RREPL(RREPL rrepl) {
		this.sourceID = rrepl.sourceID;
		this.destinationID = rrepl.destinationID;
		this.sequenceNumber = rrepl.sequenceNumber;
		this.hopCount = rrepl.hopCount;
		this.lifetime = rrepl.lifetime;
	}

}
