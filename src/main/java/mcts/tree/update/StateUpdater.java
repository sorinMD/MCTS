package mcts.tree.update;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import mcts.tree.node.TreeNode;
import mcts.utils.Selection;

/**
 * Update rule with afterstates.
 * 
 * @author sorinMD
 *
 */
public class StateUpdater extends UpdatePolicy{
    public StateUpdater(boolean ex, boolean ev) {
    	expectedReturn = ex;
    	everyVisit = ev;
    }
    
    public StateUpdater() {
		// dummy constructor required for json....
	}
    
	@Override
	public void update(ArrayList<Selection> visited, double[] reward, int nRollouts) {
		if(expectedReturn) {
			//Use log probabilities for precision...this works for now as reward is between 0-1 TODO: separate probs and rewards
			for (int k =0; k < reward.length; k++) {
				reward[k] = Math.log(reward[k]);
			}
			
			if(everyVisit) {
				for(int i = visited.size()-1; i >= 0; i--) {
					double[] r = new double[reward.length];
					for (int k =0; k < reward.length; k++) {
						r[k] = Math.exp(reward[k]);
					}
					visited.get(i).getNode().update(r, nRollouts);
					double logActProb = Math.log(visited.get(i).getActProb());
					for (int k =0; k < reward.length; k++) {
						reward[k] += logActProb;
					}
				}
			}else {
				HashMap<TreeNode, double[]> updateValues = new HashMap<>();
				for(int i = visited.size()-1; i >= 0; i--) {
					double[] r = new double[reward.length];
					for (int k =0; k < reward.length; k++) {
						r[k] = Math.exp(reward[k]) ;
					}
					updateValues.put(visited.get(i).getNode(), r);
					double logActProb = Math.log(visited.get(i).getActProb());
					for (int k =0; k < reward.length; k++) {
						reward[k] += logActProb;
					}
				}
				for(Entry<TreeNode, double[]> entry : updateValues.entrySet()) {
					entry.getKey().update(entry.getValue(), nRollouts);
				}
			}
			
		}else {
			//NOTE: since we don't apply any expectation or discount the nodes are updated from start to finish here
			if(everyVisit) {
				visited.forEach(s -> s.getNode().update(reward, nRollouts));
			}else {
				//hashset to avoid updating nodes multiple times
				HashSet<TreeNode> v = new HashSet<TreeNode>(visited.size());
				for(Selection s : visited)
					v.add(s.getNode());
				//update all visited nodes
				v.forEach(n -> n.update(reward, nRollouts));
			}
		}
	}
    
	@Override
	public String toString() {
		return "[name-" + this.getClass().getName() + "; expectedReturn-" + expectedReturn + "; everyVisit-" + everyVisit + "]";
	}

}
