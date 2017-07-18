package mcts.game.catan;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class specific to the game of Catan, required for listing the discard
 * actions by listing all subsets of a certain size.
 * 
 * @author sorinMD
 *
 */
public class ResourceSet implements GameStateConstants{
	int[] resources;
	
	/**
	 * Default constructor. Creates an empty resource set.
	 */
	public ResourceSet(){
		resources = new int[NRESOURCES];
	}
	
	public ResourceSet(int[] rset){
		resources = rset;
	}
	
	public int getTotal() {
		int sum = 0;
        for (int i = 0; i < NRESOURCES; i++){
            sum += resources[i];
        }
        return sum;
	}
	
	/**
	 * @param size
	 *            the size of the subsets
	 * @return a list containing all the possible subsets of this resource set
	 */
	public List<ResourceSet> getSubsets(int size){
        List<ResourceSet> ret = new ArrayList<ResourceSet>();
        getSubsets(breakDown(), size, 0, new ResourceSet(), ret);
        return ret;
	}
	
	/**
	 * A recursive approach at creating all possible subsets (very slow when
	 * dealing with large sets: e.g. subsets of 14 or over from Power sets of 28
	 * or over)
	 * 
	 * @param superSet
	 * @param k
	 *            the max size of the subsets
	 * @param idx
	 *            the current index in the superset
	 * @param current
	 *            the current subset the method is computing/generating
	 * @param solution
	 *            the list of all solutions found so far
	 */
	private void getSubsets(List<ResourceSet> superSet, int k, int idx, ResourceSet current, List<ResourceSet> solution) {
	    //successful stop clause
	    if (current.getTotal() == k) {
	    	if(!solution.contains(current))//avoid including duplicates
	    		solution.add(new ResourceSet(current.getResourceArrayClone()));
	        return;
	    }
	    //unsuccessful stop clause
	    if (idx == superSet.size()) return;
	    ResourceSet x = superSet.get(idx);
	    current.add(x);
	    //"guess" x is in the subset
	    getSubsets(superSet, k, idx+1, current, solution);
	    current.subtract(x);
	    //"guess" x is not in the subset
	    getSubsets(superSet, k, idx+1, current, solution);
	}
	
	/**
	 * @return a list of 1 resource sets which add up to this resource set
	 */
	private List<ResourceSet> breakDown(){
        int n;
        List<ResourceSet> list = new ArrayList<ResourceSet>();
		int[] set;
        for (int i = 0; i < NRESOURCES; i++){
            n = resources[i];
            set = new int[NRESOURCES];
            set[i]++;
            for(int j = 0; j < n; j++){
            	list.add(new ResourceSet(set));
            }
        }
        return list;
	}
	
	public void subtract(ResourceSet rs) {
		resources[RES_CLAY] -= rs.resources[RES_CLAY];
		resources[RES_STONE] -= rs.resources[RES_STONE];
		resources[RES_SHEEP] -= rs.resources[RES_SHEEP];
		resources[RES_WHEAT] -= rs.resources[RES_WHEAT];
		resources[RES_WOOD] -= rs.resources[RES_WOOD];
	}
	
	public void add(ResourceSet rs) {
		resources[RES_CLAY] += rs.resources[RES_CLAY];
		resources[RES_STONE] += rs.resources[RES_STONE];
		resources[RES_SHEEP] += rs.resources[RES_SHEEP];
		resources[RES_WHEAT] += rs.resources[RES_WHEAT];
		resources[RES_WOOD] += rs.resources[RES_WOOD];
	}
	
    /**
     * @return a clone of the resource array
     */
	public int[] getResourceArrayClone(){
		return resources.clone();
	}
	
	public boolean equals(Object o) {
		if ((o instanceof ResourceSet) && (((ResourceSet) o).resources[RES_CLAY] == this.resources[RES_CLAY])
				&& (((ResourceSet) o).resources[RES_STONE] == this.resources[RES_STONE])
				&& (((ResourceSet) o).resources[RES_SHEEP] == this.resources[RES_SHEEP])
				&& (((ResourceSet) o).resources[RES_WHEAT] == this.resources[RES_WHEAT])
				&& (((ResourceSet) o).resources[RES_WOOD] == this.resources[RES_WOOD])) {
			return true;
		} else {
			return false;
		}
	}

}
