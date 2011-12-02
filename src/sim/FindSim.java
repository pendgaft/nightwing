package sim;

import java.io.*;
import java.util.*;

import decoy.*;
import topo.AS;
import topo.BGPPath;
import util.Stats;

public class FindSim {

	private HashMap<Integer, DecoyAS> activeMap;
	private HashMap<Integer, DecoyAS> purgedMap;
	private HashSet<DecoyAS> chinaAS;

	private HashMap<Integer, List<Integer>> dirtyResultMap;
	private HashMap<Integer, List<Integer>> cleanResultMap;
	private HashMap<Integer, List<Integer>> falseResultMap;

	private static final int RUN_COUNT = 25;
	private static final boolean ONLY_TRANSIT = true;
	
	private static final String LOG_DIR = "logs/";

	public FindSim(HashMap<Integer, DecoyAS> activeMap, HashMap<Integer, DecoyAS> purgedMap) {
		super();
		this.activeMap = activeMap;
		this.purgedMap = purgedMap;
		this.dirtyResultMap = new HashMap<Integer, List<Integer>>();
		this.cleanResultMap = new HashMap<Integer, List<Integer>>();
		this.falseResultMap = new HashMap<Integer, List<Integer>>();
		this.chinaAS = new HashSet<DecoyAS>();
		for (DecoyAS tAS : activeMap.values()) {
			if (tAS.isChinaAS()) {
				this.chinaAS.add(tAS);
			}
		}
	}

	public void run() {
		long fullTimeStart = System.currentTimeMillis();
		System.out.println("Starting decoy hunting sim.");

		//for (int expo = 0; expo < 11; expo++) {
		//	int decoyCount = (int) Math.round(Math.pow(2, expo));
		//	this.runOneDeployLevel(decoyCount, FindSim.ONLY_TRANSIT);
		//}

		for(int decoyCount = 1500; decoyCount < 4100; decoyCount = decoyCount + 250){
		    this.runOneDeployLevel(decoyCount, FindSim.ONLY_TRANSIT);
		}

		fullTimeStart = (System.currentTimeMillis() - fullTimeStart) / 60000;
		System.out.println("Full run took: " + fullTimeStart + " mins ");
	}

	public void printResults() throws IOException {
		BufferedWriter outBuff = new BufferedWriter(new FileWriter(FindSim.LOG_DIR + "decoy-hunt.csv"));
		int totalASN = this.activeMap.size() + this.purgedMap.size();
		outBuff.write("Decoy hunting sim - full size is," + totalASN + "\n");
		outBuff.write("deploy size,mean dirty,std dev dirty,median dirty,mean clean,std dev clean,median clean,mean false, std dev false, median false\n");
		for (int expo = 0; expo < 11; expo++) {
			int decoyCount = (int) Math.round(Math.pow(2, expo));
			List<Integer> vals = this.dirtyResultMap.get(decoyCount);
			double meanD = Stats.mean(vals);
			double stdD = Stats.stdDev(vals);
			double medD = Stats.median(vals);
			vals = this.cleanResultMap.get(decoyCount);
			double meanC = Stats.mean(vals);
			double stdC = Stats.stdDev(vals);
			double medC = Stats.median(vals);
			vals = this.falseResultMap.get(decoyCount);
			double meanF = Stats.mean(vals);
			double stdF = Stats.stdDev(vals);
			double medF = Stats.median(vals);
			outBuff.write("" + decoyCount + "," + meanD + "," + stdD + "," + medD + "," + meanC + "," + stdC + ","
					+ medC + "," + meanF + "," + stdF + "," + medF + "\n");
		}
		outBuff.close();
	}

	private void runOneDeployLevel(int size, boolean onlyTransit) {
		System.out.println("starting probe of deployment size: " + size);
		long deploySizeStart = System.currentTimeMillis();
		DecoySeeder placer = new DecoySeeder(size);
		this.dirtyResultMap.put(size, new LinkedList<Integer>());
		this.cleanResultMap.put(size, new LinkedList<Integer>());
		this.falseResultMap.put(size, new LinkedList<Integer>());

		for (int runs = 0; runs < FindSim.RUN_COUNT; runs++) {
			long runStart = -1;
			/*
			 * run timing if we're low number run (to give us a feel)
			 */
			if (runs < 5) {
				System.out.println("Starting run number: " + runs);
				runStart = System.currentTimeMillis();
			}
			Set<Integer> correctSet = placer.seed(this.activeMap, this.purgedMap, onlyTransit);
			this.probe(size, correctSet);

			/*
			 * Run timing reporting
			 */
			if (runs < 5) {
				runStart = (System.currentTimeMillis() - runStart) / 1000;
				System.out.println("Run took: " + runStart);
			}
		}

		/*
		 * Deployment timing info
		 */
		deploySizeStart = (System.currentTimeMillis() - deploySizeStart) / 1000;
		System.out.println("Probe of deployment size: " + size + " took " + deploySizeStart + " seconds.");
	}

	private void probe(int stopPoint, Set<Integer> groundTruth) {

		/*
		 * start building the clean set, we'll first look at all ASNs that lie
		 * on clean paths
		 */
		HashSet<Integer> cleanSet = new HashSet<Integer>();
		HashSet<Integer> dirtySet = new HashSet<Integer>();
		Set<BGPPath> tempPathSet = new HashSet<BGPPath>();

		int noDest = 0;
		for (int tASN : this.activeMap.keySet()) {
			tempPathSet.clear();
			for (DecoyAS tChina : this.chinaAS) {
				tempPathSet.addAll(tChina.getAllPathsTo(tASN));
			}
			if (tempPathSet.size() == 0) {
				noDest++;
			}
			for (BGPPath tempPath : tempPathSet) {
				if (!this.pathIsDirty(tempPath, tASN)) {
					cleanSet.addAll(tempPath.getPath());
				}
			}
		}
		System.out.println("No dest to transits: " + noDest);
		for (int tASN : this.purgedMap.keySet()) {
			tempPathSet.clear();
			for (DecoyAS tChina : this.chinaAS) {
				for (AS tHook : this.purgedMap.get(tASN).getProviders()) {
					tempPathSet.addAll(tChina.getAllPathsTo(tHook.getASN()));
				}
			}

			for (BGPPath tempPath : tempPathSet) {
				if (!this.pathIsDirty(tempPath, tASN)) {
					cleanSet.addAll(tempPath.getPath());
					cleanSet.add(tASN);
				}
			}
		}

		for (int tASN : this.activeMap.keySet()) {
			/*
			 * If we know you're clean, skip over you
			 */
			if (cleanSet.contains(tASN)) {
				continue;
			}

			tempPathSet.clear();

			/*
			 * Grab all paths to the possibly tainted destination
			 */
			for (DecoyAS tChina : this.chinaAS) {
				tempPathSet.addAll(tChina.getAllPathsTo(tASN));
			}

			for (BGPPath tPath : tempPathSet) {
				boolean only = true;
				for (int tHop : tPath.getPath()) {
					if ((tHop != tASN) && (!cleanSet.contains(tHop))) {
						only = false;
						break;
					}
				}

				/*
				 * If the dest asn is the only not known clean asn, then it must
				 * be dirty
				 */
				if (only) {
					dirtySet.add(tASN);
					break;
				}
			}

		}

		for (int tASN : this.purgedMap.keySet()) {
			/*
			 * If we know you're clean, skip over you
			 */
			if (cleanSet.contains(tASN)) {
				continue;
			}

			tempPathSet.clear();
			/*
			 * Grab all paths to the possibly tainted destination
			 */
			for (DecoyAS tChina : this.chinaAS) {
				for (AS tHook : this.purgedMap.get(tASN).getProviders()) {
					tempPathSet.addAll(tChina.getAllPathsTo(tHook.getASN()));
				}
			}

			for (BGPPath tPath : tempPathSet) {
				boolean only = true;
				for (int tHop : tPath.getPath()) {
					if ((tHop != tASN) && (!cleanSet.contains(tHop))) {
						only = false;
						break;
					}
				}

				/*
				 * If the dest asn is the only not known clean asn, then it must
				 * be dirty
				 */
				if (only) {
					dirtySet.add(tASN);
					break;
				}
			}
		}

		System.out.println("dirty size: " + dirtySet.size() + " clean size: " + cleanSet.size());
		/*
		 * base size is the number of ASNs we finger, we then remove the ground
		 * truth, leaving any false positives in dirtySet
		 */
		int baseSize = dirtySet.size();
		dirtySet.removeAll(groundTruth);
		this.dirtyResultMap.get(stopPoint).add(baseSize - dirtySet.size());
		this.cleanResultMap.get(stopPoint).add(cleanSet.size());
		this.falseResultMap.get(stopPoint).add(dirtySet.size());
	}

	private boolean pathIsDirty(BGPPath path, int dest) {
		for (int tHop : path.getPath()) {
			if (this.activeMap.get(tHop).isDecoy()) {
				return true;
			}
		}

		if (this.purgedMap.containsKey(dest)) {
			if (this.purgedMap.get(dest).isDecoy()) {
				return true;
			}
		}

		return false;
	}
}
