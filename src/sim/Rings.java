package sim;

import java.util.*;
import java.io.*;

import decoy.DecoyAS;
import decoy.Seeder;
import topo.AS;
import topo.BGPPath;

public class Rings implements Seeder{

	private static final int MAX_DEPTH = 20;

	private HashMap<Integer, DecoyAS> activeMap;
	private HashMap<Integer, DecoyAS> prunedMap;
	private HashSet<AS> chinaASes;

	private HashMap<Integer, DecoyAS> seederSet = null;
	private int size;

	public Rings(HashMap<Integer, DecoyAS> activeMap,
			HashMap<Integer, DecoyAS> prunedMap) {
		super();
		this.activeMap = activeMap;
		this.prunedMap = prunedMap;
		this.chinaASes = new HashSet<AS>();

		for (AS tAS : this.activeMap.values()) {
			if (tAS.isChinaAS()) {
				this.chinaASes.add(tAS);
			}
		}
	}

	public void runTests() throws IOException {
		HashMap<Integer, HashSet<AS>> rings = this.computeRingSize();
		this.computeOutOfShadowSizes(rings);
	}

	public void setupSeeder(int depth) {
		HashSet<AS> previous = new HashSet<AS>();
		HashSet<AS> visited = new HashSet<AS>();
		previous.addAll(this.chinaASes);

		/*
		 * Populates every ring
		 */
		for (int currDepth = 1; currDepth < depth; currDepth++) {
			visited.addAll(previous);
			previous = this.computeRingMembers(previous, visited);
		}

		this.seederSet = new HashMap<Integer, DecoyAS>();
		for (AS tAS : previous) {
			this.seederSet.put(tAS.getASN(), (DecoyAS) tAS);
		}
	}
	
	public void setDecoySeedSize(int decoySize){
		this.size = decoySize;
	}

	public Set<Integer> seedDecoys() {
		if (this.seederSet == null) {
			return null;
		}
		
		if(size > this.seederSet.size()){
			return null;
		}

		for (DecoyAS tAS : this.seederSet.values()) {
			tAS.resetDecoyRouter();
		}

		Set<Integer> retSet = new HashSet<Integer>();
		Random rng = new Random();
		while (retSet.size() < size) {
			int attempt = rng.nextInt(40000);
			if (this.seederSet.containsKey(attempt)
					&& !retSet.contains(attempt)) {
				retSet.add(attempt);
				this.seederSet.get(attempt).toggleDecoyRouter();
			}
		}
		
		return retSet;
	}

	private HashMap<Integer, HashSet<AS>> computeRingSize() throws IOException {

		long startTime = System.currentTimeMillis();
		System.out.println("starting ring compuatation.");

		HashSet<AS> visited = new HashSet<AS>();
		HashMap<Integer, HashSet<AS>> rings = new HashMap<Integer, HashSet<AS>>();

		/*
		 * Start with ring "0" the china ASes
		 */
		HashSet<AS> previous = new HashSet<AS>();
		previous.addAll(this.chinaASes);

		/*
		 * Populates every ring
		 */
		for (int depth = 1; depth < MAX_DEPTH; depth++) {
			visited.addAll(previous);
			previous = this.computeRingMembers(previous, visited);
			rings.put(depth, previous);
		}

		BufferedWriter outBuff = new BufferedWriter(new FileWriter(
				"logs/ringSize.csv"));
		for (int depth = 1; depth < 20; depth++) {
			outBuff.write("" + depth + "," + rings.get(depth).size() + "\n");
		}
		outBuff.close();

		startTime = System.currentTimeMillis() - startTime;
		System.out.println("Rings computed in: " + startTime / 1000
				+ " seconds.");

		return rings;
	}

	private HashSet<AS> computeRingMembers(HashSet<AS> previousRing,
			HashSet<AS> visited) {
		HashSet<AS> nextRing = new HashSet<AS>();

		for (AS tAS : previousRing) {
			HashSet<AS> considerSet = new HashSet<AS>();
			considerSet.addAll(tAS.getCustomers());
			considerSet.addAll(tAS.getPeers());
			considerSet.addAll(tAS.getProviders());

			considerSet.removeAll(visited);
			HashSet<AS> nonTransitSet = new HashSet<AS>();
			for (AS tConsider : considerSet) {
				if (this.prunedMap.containsKey(tConsider.getASN())) {
					nonTransitSet.add(tConsider);
				}
			}
			considerSet.removeAll(nonTransitSet);

			nextRing.addAll(considerSet);
		}

		return nextRing;
	}

	private void computeOutOfShadowSizes(HashMap<Integer, HashSet<AS>> rings)
			throws IOException {
		HashMap<Integer, Double> ooShadowSizes = new HashMap<Integer, Double>();

		for (int counter = 1; counter < MAX_DEPTH; counter++) {
			long startTime = System.currentTimeMillis();
			System.out.println("computing shadow for ring: " + counter);
			ooShadowSizes.put(counter, this.evaluateShadow(rings.get(counter)));
			startTime = System.currentTimeMillis() - startTime;
			System.out.println("shadow comuation took " + startTime / 1000
					+ " seconds");
		}

		BufferedWriter outBuff = new BufferedWriter(new FileWriter(
				"logs/ooShadow.csv"));
		for (int counter = 1; counter < MAX_DEPTH; counter++) {
			outBuff.write("" + counter + "," + ooShadowSizes.get(counter)
					+ "\n");
		}
		outBuff.close();
	}

	private double evaluateShadow(HashSet<AS> ring) {

		HashSet<Integer> ringASNs = AS.buildASNSet(ring);

		int consideredCount = 0;
		int outOfShadowCount = 0;
		for (AS tDest : this.activeMap.values()) {
			/*
			 * don't consider china ASes and those in the ring
			 */
			if (tDest.isChinaAS() || ring.contains(tDest)) {
				continue;
			}

			consideredCount++;
			HashSet<BGPPath> allPaths = new HashSet<BGPPath>();
			for (AS tChina : this.chinaASes) {
				allPaths.addAll(tChina.getAllPathsTo(tDest.getASN()));
			}

			for (BGPPath tPath : allPaths) {
				if (!tPath.containsAnyOf(ringASNs)) {
					outOfShadowCount++;
					break;
				}
			}
		}

		return (double) outOfShadowCount / (double) consideredCount;
	}

}
