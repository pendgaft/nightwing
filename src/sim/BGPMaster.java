package sim;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import topo.AS;
import topo.ASTopoParser;
import topo.BGPPath;

public class BGPMaster {

	private int blockCount;
	private Semaphore workSem;
	private Semaphore completeSem;
	private Queue<Set<AS>> workQueue;

	private static final int NUM_THREADS = 8;
	private static final int WORK_BLOCK_SIZE = 40;

	public static void main(String argv[]) throws IOException {

		/*
		 * Build AS map
		 */
		HashMap<Integer, AS> asMap = ASTopoParser.doNetworkBuild();

		/*
		 * Give everyone their self network
		 */
		for (AS tAS : asMap.values()) {
			tAS.advPath(new BGPPath(tAS.getASN()));
		}

		/*
		 * dole out ases into blocks
		 */
		List<Set<AS>> asBlocks = new LinkedList<Set<AS>>();
		int currentBlockSize = 0;
		Set<AS> currentSet = new HashSet<AS>();
		for (AS tAS : asMap.values()) {
			currentSet.add(tAS);
			currentBlockSize++;

			/*
			 * if it's a full block, send it to the list
			 */
			if (currentBlockSize >= BGPMaster.WORK_BLOCK_SIZE) {
				asBlocks.add(currentSet);
				currentSet = new HashSet<AS>();
				currentBlockSize = 0;
			}
		}
		/*
		 * add the partial set at the end if it isn't empty
		 */
		if (currentSet.size() > 0) {
			asBlocks.add(currentSet);
		}

		/*
		 * build the master and slaves, spin the slaves up
		 */
		BGPMaster self = new BGPMaster(asBlocks.size());
		List<Thread> slaveThreads = new LinkedList<Thread>();
		for (int counter = 0; counter < BGPMaster.NUM_THREADS; counter++) {
			slaveThreads.add(new Thread(new BGPSlave(self)));
		}
		for (Thread tThread : slaveThreads) {
			tThread.start();
		}

		int stepCounter = 0;
		boolean stuffToDo = true;
		boolean skipToMRAI = false;
		while (stuffToDo) {
			stuffToDo = false;

			/*
			 * dole out work to slaves
			 */
			for (Set<AS> tempBlock : asBlocks) {
				self.addWork(tempBlock);
			}

			/*
			 * Wait till this round is done
			 */
			try {
				self.wall();
			} catch (InterruptedException e) {
				e.printStackTrace();
				System.exit(-2);
			}

			/*
			 * check if nodes still have stuff to do
			 */
			for (AS tAS : asMap.values()) {
				if (tAS.hasWorkToDo()) {
					stuffToDo = true;
				}
				if (tAS.hasDirtyPrefixes()) {
					skipToMRAI = true;
				}
			}

			/*
			 * If we have no pending BGP messages, release all pending updates,
			 * this is slightly different from a normal MRAI, but it gets the
			 * point
			 */
			if (!stuffToDo && skipToMRAI) {
				for(AS tAS: asMap.values()){
					tAS.mraiExpire();
				}
				skipToMRAI = false;
				stuffToDo = true;
			}

			/*
			 * A tiny bit of logging
			 */
			stepCounter++;
			if (stepCounter % 1000 == 0) {
				System.out.println("" + (stepCounter / 1000) + " (1k msgs)");
			}
		}

		//self.tellDone();
		System.out.println("all done here, holding to measure mem");
		while (true)
			;
	}

	public BGPMaster(int blockCount) {
		this.blockCount = blockCount;
		this.workSem = new Semaphore(0);
		this.completeSem = new Semaphore(0);
		this.workQueue = new LinkedBlockingQueue<Set<AS>>();
	}

	public void addWork(Set<AS> workSet) {
		this.workQueue.add(workSet);
		this.workSem.release();
	}

	public Set<AS> getWork() throws InterruptedException {

		this.workSem.acquire();
		return this.workQueue.poll();
	}

	public void reportWorkDone() {
		this.completeSem.release();
	}

	public void wall() throws InterruptedException {
		for (int counter = 0; counter < this.blockCount; counter++) {
			this.completeSem.acquire();
		}
	}

	private void tellDone() {
		this.workSem.notifyAll();
	}

}
