package routing.customRouter;

import java.util.ArrayList;
import java.util.HashMap;

import core.DTNHost;
import core.SimClock;

public class RoutingTable {

	private HashMap<DTNHost, RoutingTableEntry> routingTable;
	public int DEFAULT_TIMEOUT = 1000000;

	public RoutingTable() {
		this.routingTable = new HashMap<>();
	}

	public void addRoute(DTNHost destinationID, DTNHost nextHop, int hops) {
		routingTable.put(destinationID, new RoutingTableEntry(destinationID, nextHop, hops));
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