package routing.customRouter;

import java.util.UUID;

import core.DTNHost;
import core.SimClock;

public class RREQ {
	
	public DTNHost sourceID;
	public DTNHost destinationID;
	public int sequenceNumber;
	public String broadcastID;
	public int hopCount = 0;
	
	

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
