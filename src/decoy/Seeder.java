package decoy;

import java.util.Set;

public interface Seeder {

	/**
	 * Seeds decoy routers.  Assumes that all information has been
	 * pre-propogated to the object doing this (e.g. refs to AS objects
	 * and how many ASes we're looking for).  This function MUST have the
	 * side effect of throwing the decoy flags.
	 * 
	 * @return
	 */
	public Set<Integer> seedDecoys();
}
