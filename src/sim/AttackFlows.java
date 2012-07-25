package sim;


import decoy.DecoyAS;

import java.util.*;
import java.util.regex.*;
import java.io.*;

import topo.AS;
import util.Stats;

public class AttackFlows {
	
	private HashMap<Integer, DecoyAS> liveTopo;
	private HashMap<Integer, DecoyAS> pruenedTopo;
	private HashMap<Integer, Long> cidrs;
	
	public static void main(String[] args) throws IOException{
		AttackFlows tObj = new AttackFlows(null, null);
		tObj.parseRIBFile("asn2497.conf");
	}
	
	public AttackFlows(HashMap<Integer, DecoyAS> liveTopo, HashMap<Integer, DecoyAS> prunedTopo){
		this.liveTopo = liveTopo;
		this.pruenedTopo = prunedTopo;
	}
	
	public void runExperiment(String ribFileName, String outputFileName, int prfxCap) throws IOException{
		this.parseRIBFile(ribFileName);
		
		List<Long> t1Routes = new ArrayList<Long>();
		List<Long> t2Routes = new ArrayList<Long>();
		List<Long> allToAll = new ArrayList<Long>();
		
		List<Integer> t1Flows = new ArrayList<Integer>();
		List<Integer> t2Flows = new ArrayList<Integer>();
		List<Integer> allFlows = new ArrayList<Integer>();
		
		int top100Size = this.topKThresh(100);
		
		for(DecoyAS tAS: this.liveTopo.values()){
			List<Long> secondRoutes = null;
			List<Integer> secondFlows = null;
			
			if(tAS.getDegree() >= top100Size){
				secondRoutes = t1Routes;
				secondFlows = t1Flows;
			} else if(tAS.getCustomerCount() > 0){
				secondRoutes = t2Routes;
				secondFlows = t2Flows;
			}
			
			long sizeBase = this.cidrs.get(tAS.getASN());
			if(sizeBase == 0){
				System.out.println("wtf: " + tAS.getASN());
				continue;
			}
			if(prfxCap > 0){
				sizeBase = Math.min(sizeBase, prfxCap);
			}
			
			for(DecoyAS tTarget: this.liveTopo.values()){
				if(tTarget.getASN() == tAS.getASN()){
					continue;
				}
				
				int pathCount = this.pathsBetween(tAS.getASN(), tTarget.getASN());
				allToAll.add(pathCount * sizeBase);
				allFlows.add(pathCount);
				
				if(secondRoutes != null){
					secondRoutes.add(pathCount * sizeBase);
					secondFlows.add(pathCount);
				}
			}
		}
		
		this.dumpLong(t1Routes, outputFileName + "t1routes.csv");
		this.dumpLong(t2Routes, outputFileName + "t2routes.csv");
		this.dumpLong(allToAll, outputFileName + "allroutes.csv");
		
		this.dumpInt(t1Flows, outputFileName + "t1flows.csv");
		this.dumpInt(t2Flows, outputFileName + "t2flows.csv");
		this.dumpInt(allFlows, outputFileName + "allflows.csv");
	}
	
	private void dumpInt(List<Integer> vals, String name) throws IOException{
		List<Double> typeCastList = new ArrayList<Double>();
		for(int tVal: vals){
			typeCastList.add((double)tVal);
			Stats.printCDF(typeCastList, name);
		}
	}
	
	private void dumpLong(List<Long> vals, String name) throws IOException{
		List<Double> typeCastList = new ArrayList<Double>();
		for(long tVal: vals){
			typeCastList.add((double)tVal);
			Stats.printCDF(typeCastList, name);
		}
	}
	
	private int topKThresh(int k){
		List<Integer> degs = new ArrayList<Integer>();
		for(DecoyAS tAS: this.liveTopo.values()){
			degs.add(tAS.getDegree());
		}
		Collections.sort(degs);
		
		return degs.get(degs.size() - k);
	}
	
	private boolean inCC(int lhs, int rhs){
		HashSet<Integer> traversed = new HashSet<Integer>();
		HashSet<Integer> pending = new HashSet<Integer>();
		HashSet<Integer> middle = new HashSet<Integer>();
		
		pending.add(lhs);
		while(!pending.isEmpty()){
			middle.addAll(pending);
			pending.clear();
			
			for(int tASN: middle){
				if(traversed.contains(tASN)){
					continue;
				}
				DecoyAS asObj = this.liveTopo.get(tASN);
				for(AS tCust: asObj.getCustomers()){
					if(traversed.contains(tCust.getASN())){
						continue;
					}
					
					pending.add(tCust.getASN());
				}
				
				traversed.add(tASN);
			}
			
			middle.clear();
			
			if(traversed.contains(rhs)){
				return true;
			}
		}
		
		return traversed.contains(rhs);
	}
	
	private int pathsBetween(int lhs, int rhs){
		boolean rhsIsDownhill = this.inCC(lhs, rhs);
		int upperBound;
		if(rhsIsDownhill){
			upperBound = Math.min(this.liveTopo.get(lhs).getCustomers().size(), this.liveTopo.get(rhs).getProviders().size());
		} else{
			upperBound = Math.min(this.liveTopo.get(lhs).getProviders().size(), this.liveTopo.get(rhs).getCustomers().size());
		}
		
		return upperBound;
	}
	
	private void parseRIBFile(String file) throws IOException{
		this.cidrs = new HashMap<Integer, Long>();
		
		//first group is size, second is path
		Pattern cidrPattern = Pattern.compile("BGP4MP\\|.+?\\|.+?\\|.+?\\|.+?\\|.+?/(\\d+)\\|(.+?)\\|");
		
		BufferedReader fBuff = new BufferedReader(new FileReader(file));
		while(fBuff.ready()){
			String poll = fBuff.readLine();
			Matcher mat = cidrPattern.matcher(poll);
			if(mat.find()){
				long size = Long.parseLong(mat.group(1));
				String pathStr = mat.group(2);
				StringTokenizer pathToks = new StringTokenizer(pathStr, " ");
				String lastAS = null;
				while(pathToks.hasMoreElements()){
					lastAS = pathToks.nextToken();
				}
				
				if(lastAS.contains("{")){
					continue;
				}
				
				int owner = Integer.parseInt(lastAS);
				
				long sizeDelta = 24 - size;
				if(sizeDelta < 0){
					continue;
				}
				long endSize = (long)Math.pow(2, sizeDelta);
				
				if(!this.cidrs.containsKey(owner)){
					this.cidrs.put(owner, (long)0);
				}
				this.cidrs.put(owner, this.cidrs.get(owner) + endSize);
			}
		}
	}
}
