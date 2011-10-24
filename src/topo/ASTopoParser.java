package topo;

import java.util.*;
import java.io.*;

public class ASTopoParser {

	public static void main(String args[]) throws IOException {
		
		/*
		 * Read the relationship file and then purge all stub ASes
		 */
		HashMap<Integer, AS> asMap = ASTopoParser.parseFile("as-rel.txt");
		System.out.println("Raw topo size is: " + asMap.size());
		ASTopoParser.pruneStubASNs(asMap);
		System.out.println("Topo size after stub purge: " + asMap.size());
		
		/*
		 * Give everyone their self network
		 */
		for (AS tAS : asMap.values()) {
			tAS.advPath(new BGPPath(tAS.getASN()));
		}

		int stepCounter = 0;
		boolean stuffToDo = true;
		while (stuffToDo) {
			stuffToDo = false;

			/*
			 * let everyone do one msg
			 */
			for (AS tAS : asMap.values()) {
				tAS.handleAdvertisement();
			}

			/*
			 * check if nodes still have stuff to do
			 */
			for (AS tAS : asMap.values()) {
				if (tAS.hasWorkToDo()) {
					stuffToDo = true;
				}
			}

			stepCounter++;
			if (stepCounter % 1000 == 0) {
				System.out.println("" + (stepCounter % 1000) + " (1k msgs)");
			}
		}

		System.out.println("all done here, holding to measure mem");
		while (true)
			;
	}

	public static HashMap<Integer, AS> parseFile(String asRelFile)
			throws IOException {

		HashMap<Integer, AS> retMap = new HashMap<Integer, AS>();

		String pollString;
		StringTokenizer pollToks;
		int lhsASN, rhsASN, rel;

		BufferedReader fBuff = new BufferedReader(new FileReader(asRelFile));
		while (fBuff.ready()) {
			pollString = fBuff.readLine().trim();

			/*
			 * ignore blanks
			 */
			if (pollString.length() == 0) {
				continue;
			}

			/*
			 * Ignore comments
			 */
			if (pollString.charAt(0) == '#') {
				continue;
			}

			pollToks = new StringTokenizer(pollString, "|");
			lhsASN = Integer.parseInt(pollToks.nextToken());
			rhsASN = Integer.parseInt(pollToks.nextToken());
			rel = Integer.parseInt(pollToks.nextToken());

			if (!retMap.containsKey(lhsASN)) {
				retMap.put(lhsASN, new AS(lhsASN));
			}
			if (!retMap.containsKey(rhsASN)) {
				retMap.put(rhsASN, new AS(rhsASN));
			}

			retMap.get(lhsASN).addRelation(retMap.get(rhsASN), rel);
		}
		fBuff.close();

		return retMap;
	}

	public static void validateRelexive(HashMap<Integer, AS> asMap) {
		for (AS tAS : asMap.values()) {
			for (AS tCust : tAS.getCustomers()) {
				if (!tCust.getProviders().contains(tAS)) {
					System.out.println("fail - cust");
				}
			}
			for (AS tProv : tAS.getProviders()) {
				if (!tProv.getCustomers().contains(tAS)) {
					System.out.println("fail - prov");
				}
			}
			for (AS tPeer : tAS.getPeers()) {
				if (!tPeer.getPeers().contains(tAS)) {
					System.out.println("fail - peer");
				}
			}
		}
	}

	public static void pruneStubASNs(HashMap<Integer, AS> asMap) {
		Set<AS> stubSet = new HashSet<AS>();

		/*
		 * Find the stubs (and any non-connected AS)!
		 */
		for (AS tAS : asMap.values()) {
			if (tAS.getDegree() <= 1) {
				stubSet.add(tAS);
			}
		}

		/*
		 * Remove these guys from the asn map and remove them from their peer's
		 * data structure
		 */
		for (AS tAS : stubSet) {
			asMap.remove(tAS.getASN());
			tAS.purgeRelations();
		}

	}
}
