package decoy;

import java.util.*;

public class LargeASDecoyPlacer {
	
	private HashMap<Integer, DecoyAS> transitMap;
	
	public LargeASDecoyPlacer(HashMap<Integer, DecoyAS> activeMap){
		this.transitMap = activeMap;
	}
	
	public Set<Integer> seedSingleDecoyBySize(int currentStep){
		
		this.reset();
		
		HashSet<Integer> consideredSet = new HashSet<Integer>();
		while(consideredSet.size() < currentStep){
			int nextAS = this.findNextLargest(consideredSet);
			consideredSet.add(nextAS);
		}
		
		HashSet<Integer> seededSet = new HashSet<Integer>();
		int pickedASN = this.findNextLargest(consideredSet);
		this.transitMap.get(pickedASN).toggleDecoyRouter();
		seededSet.add(pickedASN);
		return seededSet;
	}
	
	public Set<Integer> seedNLargest(int deploySize){
		
		this.reset();
		
		HashSet<Integer> seededSet = new HashSet<Integer>();
		while(seededSet.size() < deploySize){
			int nextLargest = this.findNextLargest(seededSet);
			this.transitMap.get(nextLargest).toggleDecoyRouter();
			seededSet.add(nextLargest);
		}
		
		return seededSet;
	}
	
	private void reset(){
		/*
		 * clear out the old run
		 */
		for(DecoyAS tAS: this.transitMap.values()){
			tAS.resetDecoyRouter();
		}
	}
	
	private int findNextLargest(HashSet<Integer> considered){
		int currMax = 0;
		int currASN = -1;
		
		for(DecoyAS tAS: this.transitMap.values()){
			if(considered.contains(tAS.getASN())){
				continue;
			}
			if(tAS.isWardenAS()){
				continue;
			}
			
			if(tAS.getDegree() > currMax){
				currMax = tAS.getDegree();
				currASN = tAS.getASN();
			}
		}
		
		return currASN;
	}

}
