package decoy;

import java.util.*;

import topo.AS;

public class DecoySeeder implements Seeder{

	private int decoyCount;
	private HashMap<Integer, DecoyAS> liveAS;
	private HashMap<Integer, DecoyAS> purgedAS;
	private boolean onlyTransit;
	private boolean saneChina;

	public DecoySeeder(int count, HashMap<Integer, DecoyAS> liveAS,
			HashMap<Integer, DecoyAS> purgedAS, boolean onlyTranist,
			boolean saneChina) {
		this.decoyCount = count;
		this.liveAS = liveAS;
		this.purgedAS = purgedAS;
		this.onlyTransit = onlyTransit;
		this.saneChina = saneChina;
	}

	public Set<Integer> seedDecoys() {
		Random rng = new Random();

		/*
		 * Sanity check that ASes are set to false
		 */
		for (DecoyAS tAS : liveAS.values()) {
			tAS.resetDecoyRouter();
		}
		for (DecoyAS tAS : purgedAS.values()) {
			tAS.resetDecoyRouter();
		}

		/*
		 * Step through seeding decoy routing ASes
		 */
		HashSet<Integer> markedSet = new HashSet<Integer>();
		while (markedSet.size() < this.decoyCount) {
			/*
			 * Get a random ASN, we'll see if it hasn't been picked, exists, and
			 * meets our critera (yes there are better ways to do this (read:
			 * more efficient), but meh, this will get us to a valid deploy,
			 * just a little slowly)
			 */
			int test = rng.nextInt(40000);
			if (markedSet.contains(test)) {
				continue;
			}

			/*
			 * Hunt in transit ASes for our ASN, if not there, check the
			 * customer ASNs
			 */
			DecoyAS focus = null;
			focus = liveAS.get(test);
			if (focus == null) {
				focus = purgedAS.get(test);
			}

			/*
			 * We picked an ASN randomly that doesn't exist...
			 */
			if (focus == null) {
				continue;
			}

			/*
			 * China doesn't deploy DRs....
			 */
			if (focus.isChinaAS()) {
				continue;
			}

			/*
			 * Clause to prevent us for selecting non-transit ASes as deployment
			 * points for deflecting routers
			 */
			if (onlyTransit && !liveAS.containsKey(test)) {
				continue;
			}

			/*
			 * Clause to prevent us from selecting a provider that directly
			 * abutts China as a decoy, as they have a STRONG economic incentive
			 * to not do such
			 */
			if (saneChina) {
				boolean adjChina = false;
				for (AS tAS : focus.getProviders()) {
					if (tAS.isChinaAS()) {
						adjChina = true;
					}
				}
				for (AS tAS : focus.getPeers()) {
					if (tAS.isChinaAS()) {
						adjChina = true;
					}
				}
				for (AS tAS : focus.getCustomers()) {
					if (tAS.isChinaAS()) {
						adjChina = true;
					}
				}

				if (adjChina) {
					continue;
				}
			}

			/*
			 * Yay! It is valid, mark it as such and record that fact
			 */
			focus.toggleDecoyRouter();
			markedSet.add(test);
		}

		return markedSet;
	}

}
