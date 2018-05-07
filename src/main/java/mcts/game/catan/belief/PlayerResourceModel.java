package mcts.game.catan.belief;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import mcts.game.catan.ResourceSet;

/**
 * A definition to represent all possible combinations of resources a player may have in a given state.
 * @author sorinMD
 */
public class PlayerResourceModel implements Serializable{
	/**
	 * the abstract representation of this player's hand: minimum for each type,
	 * maximum for each type, total and number of possible hands.
	 */
	public int[] rssAbs;
	/**
	 * offset start for minimum for each resource type
	 */
	public static final int MIN = 0;
	/**
	 * offset start for maximum for each resource type
	 */
	public static final int MAX = 5;
	
	/**the set containing all the combinations describing the possible hands
	 * NOTE: it should always have at least one set*/
	public HashMap<ResourceSet,Double> possibleResSets = new HashMap<ResourceSet,Double>();
	
	/**
	 * Default constructor, to be called at the start of the game
	 */
	public PlayerResourceModel(){
		rssAbs = new int[12];
		possibleResSets.put(new ResourceSet(), new Double(1));
	}
	
	/**
	 * When we load and have no memory so we start from fully observable world.
	 * @param set the current resource set in the player's hand
	 */
	public PlayerResourceModel(ResourceSet set){
		rssAbs = new int[12];
		for (int i = 0; i < ResourceSet.NRESOURCES; i++){
			rssAbs[MIN + i] = rssAbs[MAX + i] = set.getAmount(i);
		}
		rssAbs[10] = set.getTotal();
		possibleResSets.put(set, new Double(1));
	}
	
	/**
	 * Copy constructor
	 * @param old the old model we are building this object from
	 */
	public PlayerResourceModel(PlayerResourceModel old){
		rssAbs = old.rssAbs.clone();
		for(ResourceSet set : old.possibleResSets.keySet())
			possibleResSets.put(new ResourceSet(set), old.possibleResSets.get(set).doubleValue());
	}
	
	/**
	 * Do we know the exact player's hand?
	 * @return
	 */
	public boolean isFullyObservable(){
		return possibleResSets.size() == 1;
	}

	/**
	 * This should be used as checks for rejection sampling methods.
	 * @return
	 */
	public boolean isEmpty() {
		return possibleResSets.size() == 0;
	}
	
	/**
	 * NB: to be called only if {@link #isFullyObservable()} returns true, otherwise it would just return one of the possible hands
	 * @return
	 */
	public ResourceSet getHand(){
		return possibleResSets.keySet().iterator().next();
	}
	
	/**
	 * Updates the model
	 * @param newModel
	 */
	protected void update(HashMap<ResourceSet,Double> newModel){
		possibleResSets = newModel;
		rssAbs[11] = possibleResSets.size();
	}
	
	/**
	 * @return the total number of resources in this player's hand
	 */
	public int getTotalResources(){
		return possibleResSets.keySet().iterator().next().getTotal();
	}
	
	/**
	 * @return the number of possible hands for this player
	 */
	public int getPossHandCount(){
		return rssAbs[11];
	}
	
	/**
	 * Updates the model and the abstract representation of the model
	 * @param newModel
	 * @param a the action that was executed
	 */
	protected void update(HashMap<ResourceSet,Double> newModel, Action a){
		update(newModel);
		
		switch (a.type) {
			case Action.GAIN:
			case Action.GAINMONO:
				if(a.resSets.size() == 1){
					ResourceSet actSet = a.resSets.keySet().iterator().next();
					rssAbs[10] += actSet.getTotal();
					for (int i = 0; i < ResourceSet.NRESOURCES; i++){
						rssAbs[MIN + i] += actSet.getAmount(i);
						rssAbs[MAX + i] += actSet.getAmount(i);
			        }
				}else{
					//robber case, when gaining one rss from a list of possiblities
					rssAbs[10]++;
					HashSet<ResourceSet> allSets = new HashSet<>(a.resSets.keySet());
					for(ResourceSet s : allSets) {
						for (int i = 0; i < ResourceSet.NRESOURCES; i++){
							if(s.getAmount(i)==1){
								rssAbs[MAX + i]++;
							}
						}
					}
				}
				break;
			case Action.LOSE:
				if(a.resSets.size() == 1) {
					ResourceSet actSet = a.resSets.keySet().iterator().next();
					rssAbs[10] -= actSet.getTotal();
					if(actSet.isKnown()){
						for (int i = 0; i < ResourceSet.NRESOURCES; i++){
							rssAbs[MIN + i] -= actSet.getAmount(i);
							if(rssAbs[MIN + i] < 0)
								rssAbs[MIN + i] = 0;
							rssAbs[MAX + i] -= actSet.getAmount(i);
							if(rssAbs[MAX + i] > rssAbs[10])
								rssAbs[MAX + i] = rssAbs[10];
				        }
					}else{
						//the discards or victim cases
						for (int i = 0; i < ResourceSet.NRESOURCES; i++){
							rssAbs[MIN + i] -= actSet.getTotal();
							if(rssAbs[MIN + i] < 0)
								rssAbs[MIN + i] = 0;
							if(rssAbs[MAX + i] > rssAbs[10])
								rssAbs[MAX + i] = rssAbs[10];
						}
					}
				}
				break;
			case Action.LOSEMONO:
				if(a.resSets.size() == 1) {
					ResourceSet actSet = a.resSets.keySet().iterator().next();
					rssAbs[10] -= actSet.getTotal();
					for (int i = 0; i < ResourceSet.NRESOURCES; i++){
						if(actSet.getAmount(i) == 1) {
							rssAbs[MIN + i] = 0;
							rssAbs[MAX + i] = 0;
							break;
						}
			        }
					
					//we can reset the min and the max amount after this action type as certain hands were dropped... TODO: consider removing this
					int[] min = new int[5];
					Arrays.fill(min, Integer.MAX_VALUE);
					int[] max = new int[5];
					for(ResourceSet set : newModel.keySet()) {
						for (int i = 0; i < ResourceSet.NRESOURCES; i++){
							if(min[i] > set.getAmount(i))
								min[i] = set.getAmount(i);
							if(max[i] < set.getAmount(i))
								max[i] = set.getAmount(i);
						}
					}
					for (int i = 0; i < ResourceSet.NRESOURCES; i++){
							rssAbs[MIN + i] = min[i];
							rssAbs[MAX + i] = max[i];
					}
				
				}
				break;
			default:
				System.err.println("Unknown action type encountered in updating the abstract representation of the player hand model");
		}
		
	}
	
	/**
	 * Iterates over the current set of possible hands (model) and creates the abstract representation.
	 * It should be used either if a single hand is possible or when loading without a memory.
	 */
	public void resetAbstractRepresentation(){
		for (int i = 0; i < ResourceSet.NRESOURCES; i++){
			rssAbs[MIN + i] = Integer.MAX_VALUE;
			rssAbs[MAX + i] = 0;
		}
		for(ResourceSet s: possibleResSets.keySet()){
			for (int i = 0; i < ResourceSet.NRESOURCES; i++){
				if(s.getAmount(i) < rssAbs[MIN + i])
					rssAbs[MIN + i] = s.getAmount(i);
				if(s.getAmount(i) > rssAbs[MAX + i])
					rssAbs[MAX + i] = s.getAmount(i);
			}
		}
		rssAbs[10] = possibleResSets.keySet().iterator().next().getTotal();
		rssAbs[11] = possibleResSets.size();
	}
	
	/**
	 * @return a hashmap of StacResourceSets representing what resource type could be stolen and the probability attached to it.
	 */
	protected HashMap<ResourceSet,Double> getPossibleResourceTypes(){
		HashMap<ResourceSet,Double> ret = new HashMap<ResourceSet,Double>();
		for(ResourceSet s : possibleResSets.keySet()){
			List<ResourceSet> possRss = s.getSubsets(1, true);
			double val = possibleResSets.get(s).doubleValue()/possRss.size();
			for(ResourceSet set : possRss) {
				if(ret.containsKey(set)) {
					ret.put(set, ret.get(set).doubleValue() + val);
				}else
					ret.put(set, val);
			}
		}
		return ret;
		
	}
	
	/**
	 * @return a stac resource set containing the maximum possible amount for each resource type in this player's hand
	 */
	protected ResourceSet getMaxAmountForEachType(){
		ResourceSet set = new ResourceSet();
		for (int i = 0; i < ResourceSet.NRESOURCES; i++){
			set.setAmount(rssAbs[MAX + i], i);
        }
		return set;
	}
	
	public void destroy(){
		possibleResSets.clear();
		possibleResSets = null;
	}
	
	/**
	 * @return a deep copy of the object
	 */
	protected PlayerResourceModel copy(){
		return new PlayerResourceModel(this);
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof PlayerResourceModel) {
			PlayerResourceModel objRef = (PlayerResourceModel) obj;
			if(Arrays.equals(rssAbs, objRef.rssAbs)) { 
				if(possibleResSets.size() == objRef.possibleResSets.size()) {
					boolean equals = true;
					for(Entry<ResourceSet, Double> entry : possibleResSets.entrySet()) {
						if(objRef.possibleResSets.containsKey(entry.getKey())) {
							if(!doubleEquals(entry.getValue().doubleValue(), objRef.possibleResSets.get(entry.getKey()).doubleValue())) {
								equals = false;
								break;
							}
						}else { 
							equals = false;
							break;
						}
					}
					if(equals)
						return true;
				}
			}
		}
		return false;
	}
	
	private boolean doubleEquals(double a, double b) {
		double eps = 1e-6;
		if (eps < Math.abs(a - b))
			return false;
		return true;
	}
	
}