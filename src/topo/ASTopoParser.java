package topo;

import java.util.*;
import java.io.*;

public class ASTopoParser {

	public static void main(String args[]) throws IOException {
		HashMap<Integer, AS> asMap = ASTopoParser.parseFile("as-rel.txt");

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
			if (stepCounter % 10000 == 0) {
				System.out.println("" + (stepCounter % 10000) + " (10k msgs)");
			}
		}

		System.out.println("all done here, holding to measure mem");
		while (true)
			;
	}

	public static HashMap<Integer, AS> parseFile(String asRelFile) throws IOException {

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
}
