package decoy;

import topo.AS;

public class DecoyAS extends AS{

	private boolean hostsDecoyRouter;
	
	public DecoyAS(int myASN) {
		super(myASN);
		this.hostsDecoyRouter = false;
	}
	
	public void toggleDecoyRouter(){
		this.hostsDecoyRouter = true;
	}
	
	public void resetDecoyRouter(){
		this.hostsDecoyRouter = false;
	}
	
	public boolean isDecoy(){
		return this.hostsDecoyRouter;
	}
	
	
	
	

}
