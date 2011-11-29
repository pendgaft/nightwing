package decoy;

import java.util.*;

public class DecoySeeder {
	
	private int decoyCount;
	
	public DecoySeeder(int count){
		this.decoyCount = count;
	}
	
	public Set<Integer> seed(HashMap<Integer, DecoyAS> liveAS, HashMap<Integer, DecoyAS> purgedAS, boolean onlyTransit){
		Random rng = new Random();
		
		/*
		 * Sanity check that ASes are set to false
		 */
		for(DecoyAS tAS: liveAS.values()){
			tAS.resetDecoyRouter();
		}
		for(DecoyAS tAS: purgedAS.values()){
			tAS.resetDecoyRouter();
		}
		
		/*
		 * Step through seeding decoy routing ASes
		 */
		HashSet<Integer> markedSet = new HashSet<Integer>();
		while(markedSet.size() < this.decoyCount){
			int test = rng.nextInt(40000);
			if(markedSet.contains(test)){
				continue;
			}
			
			DecoyAS focus = null;
			focus = liveAS.get(test);
			if(focus == null){
				focus = purgedAS.get(test);
			}
			if(focus == null){
				continue;
			}
			if(focus.isChinaAS()){
				continue;
			}
			
			if(onlyTransit && !liveAS.containsKey(test)){
				continue;
			}
			
			focus.toggleDecoyRouter();
			markedSet.add(test);
		}
		
		return markedSet;
	}
	
	

}
