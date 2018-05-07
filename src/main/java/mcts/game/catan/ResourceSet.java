package mcts.game.catan;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Definition of a data type to contain the resource combination for a possible hand in the belief model.
 * This is used both as a fully observable (known) set and as a set containing only the total for stealing/discarding actions.
 * Some methods are adapted from SOCResourceSet, however this version cannot contain both known and unknown resources.
 * It contains methods for generating possible subsets of a given size from the original set required for discard/stealing actions.
 * NOTE: There is no direct translation to and from SOCResourceSet due to these changes!!
 * @author sorinMD
 */
public class ResourceSet implements Serializable,HexTypeConstants{
	/**
	 * This flag highlights if it is fully specified set or just contains the total.
	 */
	private boolean known = true;
	int[] resources;
	
	/**
	 * Default constructor. Creates an empty resource set.
	 */
	public ResourceSet(){
		resources = new int[NRESOURCES + 1];
	}
	
    /**
     * Make a resource set from an array, where the array size should be equal {@link HexTypeConstants#NRESOURCES} + 1 and the last position is the total.
     */
	public ResourceSet(int[] rset){
		resources = rset.clone();
	}
	
    /**
     * Make a resource set from an array, where the array size should be equal {@link HexTypeConstants#NRESOURCES} + 1 and the last position is the total.
     */
	public ResourceSet(ResourceSet set){
		resources = set.getResourceArrayClone();
		known = set.isKnown();
	}
	
	/**
	 * Constructor for creating a set that only specifies the total for the victim or discard cases, 
	 * where we don't know anything about the resources types.
	 * @param total
	 */
	public ResourceSet(int total){
		resources = new int[NRESOURCES + 1];
		resources[NRESOURCES] = total;
		known = false;
	}	
	
	/**
	 * Constructor for creating a set of a specific format for dealing with monopoly actions
	 * where the total could be 0.
	 * @param total
	 */
	public ResourceSet(int total, int type){
		resources = new int[NRESOURCES + 1];
		resources[type] = 1;//placeholder to indicate what type of rss was monopolised
		resources[NRESOURCES] = total;
	}
	
	/**
	 * if total is specified return it, else it is equal to the sum of the rest.
	 */
	public int getTotal() {
        return resources[NRESOURCES];
	}
	
	/**
	 * @return true if the total is not specified, else false.
	 */
	public boolean isKnown(){
		 return known;
	}
			
	public boolean equals(Object o){
		if ((o instanceof ResourceSet)
				&& (((ResourceSet) o).getAmount(NRESOURCES)  == resources[NRESOURCES])
                && (((ResourceSet) o).getAmount(RES_CLAY)    == resources[RES_CLAY])
                && (((ResourceSet) o).getAmount(RES_STONE)   == resources[RES_STONE])
                && (((ResourceSet) o).getAmount(RES_SHEEP)   == resources[RES_SHEEP])
                && (((ResourceSet) o).getAmount(RES_WHEAT)   == resources[RES_WHEAT])
                && (((ResourceSet) o).getAmount(RES_WOOD)    == resources[RES_WOOD]))
        {
            return true;
        }
        else
        {
            return false;
        }
	}
	
    /**
     * Note: it doesn't take isknown parameter into account
     * @return a hashcode for this resource data
     */
    @Override
    public int hashCode()
    {
        return Arrays.hashCode(resources);
    }
	
    /**
     * Human-readable form of the set, with format "clay=5|ore=1|sheep=0|wheat=0|wood=3|total=0"
     * @return a human readable longer form of the set
     */
    @Override
    public String toString()
    {
        String s = "clay=" + resources[RES_CLAY]
            + "|ore=" + resources[RES_STONE]
            + "|sheep=" + resources[RES_SHEEP]
            + "|wheat=" + resources[RES_WHEAT]
            + "|wood=" + resources[RES_WOOD]
            + "|total=" + resources[NRESOURCES];

        return s;
    }
    
	/**
	 * @param size the size of the subsets
	 * @param includeDuplicates flag to decide whether to keep duplicates or not
	 * @return a list containing all the possible subsets of this resource set
	 */
	public List<ResourceSet> getSubsets(int size, boolean includeDuplicates){
		Collection ret;
		if(!includeDuplicates) {
			ret = new HashSet<ResourceSet>();
		}else
			ret = new ArrayList<ResourceSet>();
        getSubsets(breakDown(), size, 0, new ResourceSet(), ret);
        return new ArrayList<>(ret);
	}
	
	/**
	 * A recursive approach at creating all possible subsets (very slow when dealing with large sets: e.g. subsets of 14 or over from 28 or over)
	 * @param superSet
	 * @param k the max size of the subsets
	 * @param idx the current index in the superset
	 * @param current the current subset the method is computing/generating
	 * @param solution the list of all solutions found so far
	 */
	private void getSubsets(List<ResourceSet> superSet, int k, int idx, ResourceSet current, Collection<ResourceSet> solution) {
	    //successful stop clause
	    if (current.getTotal() == k) {
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
	 * Add every quantity of every resource type from the set to this one
	 * @param rs the set to add to this one
	 */
	public void add(ResourceSet rs) {
        resources[RES_CLAY]    += rs.getAmount(RES_CLAY);
        resources[RES_STONE]   += rs.getAmount(RES_STONE);
        resources[RES_SHEEP]   += rs.getAmount(RES_SHEEP);
        resources[RES_WHEAT]   += rs.getAmount(RES_WHEAT);
        resources[RES_WOOD]    += rs.getAmount(RES_WOOD);
        resources[NRESOURCES]  += rs.getAmount(NRESOURCES);	    		
	}
	
	/**
	 * Add the amount of a type to this set
	 * @param amt the amount to add
	 * @param rtype the type of resource
	 */
    public void add(int amt, int rtype)
    {
        resources[rtype] += amt;
        resources[NRESOURCES] += amt;
    }

	/**
	 * @return a list of 1 resource sets which add up to this resource set
	 */
	private List<ResourceSet> breakDown(){
        int n;
        List<ResourceSet> list = new ArrayList<ResourceSet>();
		int[] set = {0,0,0,0,0,1};
        for (int i = 0; i < NRESOURCES; i++){
            n = resources[i];
            set[i]++;
            for(int j = 0; j < n; j++){
            	list.add(new ResourceSet(set));
            }
            set[i]--;
        }
        return list;
	}
	
	/**
	 * Subtracts the amount of a specific type from this set
	 * Note: it doesn't check if the amount is contained or not beforehand
	 * @param rtype the type of resource, like {@link HexTypeConstants#RES_CLAY}
     * @param amt the amount;
	 */
	public void subtract(int amt, int rtype)
    {
		resources[rtype] -= amt;
		resources[NRESOURCES] -= amt;
    }
	
	/**
	 * Subtracts an entire set from this set
	 * Note: it doesn't check if the set is contained or not beforehand
     * @param rs  the resource set
     */
    public void subtract(ResourceSet rs){
        resources[RES_CLAY] -= rs.getAmount(RES_CLAY);
        resources[RES_STONE] -= rs.getAmount(RES_STONE);
        resources[RES_SHEEP] -= rs.getAmount(RES_SHEEP);
        resources[RES_WHEAT] -= rs.getAmount(RES_WHEAT);
        resources[RES_WOOD] -= rs.getAmount(RES_WOOD);
        resources[NRESOURCES] -= rs.getAmount(NRESOURCES);
    }
	
    /**
     * @return the number of a kind of resource
     *
     * @param rtype the type of resource
     */
    public int getAmount(int rtype)
    {
        return resources[rtype];
    }
    
    /**
     * @return a clone of the resource array
     */
	public int[] getResourceArrayClone(){
		return resources.clone();
	}
	
    /**
     * set the amount of a resource and update the total
     *
     * @param rtype the type of resource, like {@link HexTypeConstants#RES_CLAY}
     * @param amt   the amount
     */
    public void setAmount(int amt, int rtype){
    	resources[NRESOURCES] = resources[NRESOURCES] - (resources[rtype] - amt);
        resources[rtype] = amt;
    }
    
    /**
     * @return true if sub is in this set
     *
     * @param sub  the sub set
     */
    public boolean contains(ResourceSet sub)
    {
        return gte(this, sub);
    }	
	/**
	 * Is set a greater or equal than set b
	 * @param a
	 * @param b
	 * @return true if each resource type in set A is >= each resource type in set B
	 */
    static public boolean gte(ResourceSet a, ResourceSet b){
        return (   (a.getAmount(NRESOURCES) >= b.getAmount(NRESOURCES))
        		&& (a.getAmount(RES_CLAY)    >= b.getAmount(RES_CLAY))
                && (a.getAmount(RES_STONE)   >= b.getAmount(RES_STONE))
                && (a.getAmount(RES_SHEEP)   >= b.getAmount(RES_SHEEP))
                && (a.getAmount(RES_WHEAT)   >= b.getAmount(RES_WHEAT))
                && (a.getAmount(RES_WOOD)    >= b.getAmount(RES_WOOD)));
    }
	
	/**
	 * Is the specified rss type in set a greater or equal than the one in set b
	 * @param a
	 * @param b
	 * @return true if it is greater or equal
	 */
    static public boolean gte(ResourceSet a, ResourceSet b, int rtype){
    	return (a.getAmount(rtype) >= b.getAmount(rtype));
    }
    
    /**
     * Human-readable form of the set, with format "Resources: 5 1 0 0 3 0".
     * Order of types is Clay, ore, sheep, wheat, wood, unknown.
     * @return a human readable short form of the set
     * @see #toFriendlyString()
     */
    public String toShortString()
    {
        String s = "Resources: " + resources[RES_SHEEP] + " "
            + resources[RES_WOOD] + " "
            + resources[RES_CLAY] + " "
            + resources[RES_WHEAT] + " "
            + resources[RES_STONE] + " "
            + resources[NRESOURCES];

        return s;
    }
    
    //quick testing of the subset method
	public static void main(String[] args) {
		int count = 5;
		int[] rss = new int[6];
		Arrays.fill(rss, count);
		rss[ResourceSet.NRESOURCES] = count * 5;
		System.out.println(Arrays.toString(rss));
		ResourceSet set = new ResourceSet(rss);
		List<ResourceSet> subsets = set.getSubsets(5, false);
		System.out.println(subsets.size());
		for(ResourceSet s : subsets){
			System.out.println(Arrays.toString(s.resources));
		}
	}
	
}
