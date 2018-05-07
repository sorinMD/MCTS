package mcts.game.catan.belief;

import java.io.Serializable;
import java.util.HashMap;

import mcts.game.catan.ResourceSet;

/**
 * A structure defining an action for updating the belief model over resources.
 * @author sorinMD
 */
public class Action implements Serializable{
	//action types same as the SOCPlayerElement actions
	public static final int GAIN = 101;
	public static final int LOSE = 102;
	//special action types to handle the monopoly action which provides the information that the person cannot have more or fewer resources of that type.
	public static final int GAINMONO = 103;
	public static final int LOSEMONO = 104;
	/**this action's type*/
	public int type;
	/**the set of resources describing the modification and the probability of this action being the one executed in the real game*/
	public HashMap<ResourceSet,Double> resSets;
	
	public Action(int t, HashMap<ResourceSet,Double> rSets){
		type = t;
		resSets = rSets;
	}
	
	public Action(int t, ResourceSet set){
		type = t;
		resSets = new HashMap<>();
		resSets.put(set, 1.0);
	}
}
