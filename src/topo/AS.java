package topo;

import java.util.*;

public class AS {

	private int asn;
	private Set<AS> customers;
	private Set<AS> peers;
	private Set<AS> providers;

	private HashMap<Integer, List<BGPPath>> adjInRib;
	private HashMap<Integer, List<BGPPath>> inRib;
	private HashMap<Integer, Set<AS>> adjOutRib;
	private HashMap<Integer, BGPPath> locRib;

	private Queue<BGPUpdate> incUpdateQueue;

	public static final int PROVIDER = -1;
	public static final int PEER = 0;
	public static final int CUSTOMER = 1;

	public AS(int myASN) {
		this.asn = myASN;
		this.customers = new HashSet<AS>();
		this.peers = new HashSet<AS>();
		this.providers = new HashSet<AS>();

		this.adjInRib = new HashMap<Integer, List<BGPPath>>();
		this.inRib = new HashMap<Integer, List<BGPPath>>();
		this.adjOutRib = new HashMap<Integer, Set<AS>>();
		this.locRib = new HashMap<Integer, BGPPath>();

		this.incUpdateQueue = new LinkedList<BGPUpdate>();
	}

	public void addRelation(AS otherAS, int myRelationToThem) {
		if (myRelationToThem == AS.PROVIDER) {
			this.customers.add(otherAS);
			otherAS.providers.add(this);
		} else if (myRelationToThem == AS.PEER) {
			this.peers.add(otherAS);
			otherAS.peers.add(this);
		} else if (myRelationToThem == AS.CUSTOMER) {
			this.providers.add(otherAS);
			otherAS.customers.add(this);
		} else if (myRelationToThem == 3) {
			//ignore
		} else {
			System.err.println("WTF bad relation: " + myRelationToThem);
			System.exit(-1);
		}
	}

	//FIXME needs to pull from queue
	public void handleAdvertisement() {
		BGPUpdate nextUpdate = this.incUpdateQueue.poll();
		if (nextUpdate == null) {
			return;
		}

		/*
		 * Fetch some fields in the correct form
		 */
		int advPeer, dest;
		if (nextUpdate.isWithdrawl()) {
			advPeer = nextUpdate.getWithdrawer().asn;
			dest = nextUpdate.getWithdrawnDest();
		} else {
			advPeer = nextUpdate.getPath().getNextHop();
			dest = nextUpdate.getPath().getDest();
		}

		/*
		 * Setup some objects if this the first time seeing a peer/dest
		 */
		if (this.adjInRib.get(advPeer) == null) {
			this.adjInRib.put(advPeer, new ArrayList<BGPPath>());
		}
		if (this.inRib.get(dest) == null) {
			this.inRib.put(dest, new ArrayList<BGPPath>());
		}

		/*
		 * Hunt for an existing route in the adjInRib. If it's a withdrawl we
		 * want to remove it, and if it is an adv and a route already exists we
		 * then have an implicit withdrawl
		 */
		boolean routeRemoved = false;
		List<BGPPath> advRibList = this.adjInRib.get(advPeer);
		for (int counter = 0; counter < advRibList.size(); counter++) {
			if (advRibList.get(counter).getDest() == dest) {
				advRibList.remove(counter);
				routeRemoved = true;
				break;
			}
		}

		/*
		 * If there was a rotue to remove from the adjInRib, clean up the inRib
		 * as well
		 */
		List<BGPPath> destRibList = this.inRib.get(dest);
		if (routeRemoved) {
			for (int counter = 0; counter < destRibList.size(); counter++) {
				if (destRibList.get(counter).getNextHop() == advPeer) {
					destRibList.remove(counter);
					break;
				}
			}
		}

		/*
		 * If it is a loop don't add it to ribs
		 */
		if ((!nextUpdate.isWithdrawl()) && (!nextUpdate.getPath().containsLoop(this.asn))) {
			advRibList.add(nextUpdate.getPath());
			destRibList.add(nextUpdate.getPath());
		}

		recalcBestPath(dest);
	}

	public void advPath(BGPPath incPath) {
		this.incUpdateQueue.add(new BGPUpdate(incPath));
	}

	public void withdrawPath(AS peer, int dest) {
		this.incUpdateQueue.add(new BGPUpdate(dest, peer));
	}
	
	public boolean hasWorkToDo(){
		return !this.incUpdateQueue.isEmpty();
	}

	private void recalcBestPath(int dest) {
		boolean changed;

		List<BGPPath> possList = this.inRib.get(dest);

		BGPPath currentBest = null;
		int currentRel = -4;
		for (BGPPath tPath : possList) {
			if (currentBest == null) {
				currentBest = tPath;
				currentRel = this.getRel(currentBest.getNextHop());
				continue;
			}

			int newRel = this.getRel(tPath.getNextHop());
			if (newRel > currentRel) {
				currentBest = tPath;
				currentRel = newRel;
				continue;
			}

			if (newRel == currentRel) {
				if (currentBest.getPathLength() > tPath.getPathLength()
						|| (currentBest.getPathLength() == tPath.getPathLength() && tPath.getNextHop() < currentBest
								.getNextHop())) {
					currentBest = tPath;
					currentRel = newRel;
				}
			}
		}

		BGPPath currentInstall = this.locRib.get(dest);
		changed = (currentInstall == null || !currentBest.equals(currentInstall));
		this.locRib.put(dest, currentBest);

		/*
		 * If we have a new path send updates
		 */
		if (changed) {
			Set<AS> prevAdvedTo = this.adjOutRib.get(dest);
			Set<AS> newAdvTo = new HashSet<AS>();
			BGPPath pathOfMerit = this.locRib.get(dest);

			if (pathOfMerit != null) {
				BGPPath pathToAdv = pathOfMerit.deepCopy();
				pathToAdv.appendASToPath(this.asn);
				for (AS tCust : this.customers) {
					tCust.advPath(pathToAdv);
					newAdvTo.add(tCust);
				}
				if (pathOfMerit.getDest() == this.asn || this.getRel(pathOfMerit.getNextHop()) == 1) {
					for (AS tPeer : this.peers) {
						tPeer.advPath(pathToAdv);
						newAdvTo.add(tPeer);
					}
					for (AS tProv : this.providers) {
						tProv.advPath(pathToAdv);
						newAdvTo.add(tProv);
					}
				}
			}

			if (prevAdvedTo != null) {
				prevAdvedTo.removeAll(newAdvTo);
				for (AS tAS : prevAdvedTo) {
					tAS.withdrawPath(this, dest);
				}
			}
		}
	}

	private int getRel(int asn) {
		if (this.providers.contains(asn)) {
			return -1;
		} else if (this.peers.contains(asn)) {
			return 0;
		} else if (this.customers.contains(asn)) {
			return 1;
		}
		
		if(asn == this.asn){
			return 2;
		}

		return -4;
	}

	public Set<AS> getCustomers() {
		return customers;
	}

	public Set<AS> getPeers() {
		return peers;
	}

	public Set<AS> getProviders() {
		return providers;
	}

	public int hashCode() {
		return this.asn;
	}

	public int getASN() {
		return this.asn;
	}

}
