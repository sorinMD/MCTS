package mcts.tree.update;

import java.util.ArrayList;
import java.util.HashSet;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;
import mcts.utils.HashMapList;
import mcts.utils.Selection;

/**
 * Update rule when afterstates are not allowed.
 * 
 * @author sorinMD
 *
 */
public class ActionUpdater extends UpdatePolicy{
    public ActionUpdater(boolean ex, boolean ev) {
    	expectedReturn = ex;
    	everyVisit = ev;
    }
    
    public ActionUpdater() {
		// dummy constructor required for json....
	}
	@Override
	public void update(ArrayList<Selection> visited, double[] reward, int nRollouts) {
		if(expectedReturn) {
			//Use log probabilities for precision...this works for now as reward is between 0-1 TODO: separate probs and rewards
			for (int k =0; k < reward.length; k++) {
				reward[k] = Math.log(reward[k] );
			}
			if(everyVisit) {
				double[] r = new double[reward.length];
				for (int k =0; k < reward.length; k++) {
					r[k] = Math.exp(reward[k]);
				}
				//update the terminal/leaf node first
				if(visited.get(visited.size()-1).getNode() instanceof StandardNode)
					((StandardNode)visited.get(visited.size()-1).getNode()).update(r, null, nRollouts);
				else
					visited.get(visited.size()-1).getNode().update(r, nRollouts);
				
				double logActProb = Math.log(visited.get(visited.size()-1).getActProb());
				for (int k =0; k < reward.length; k++) {
					reward[k] += logActProb;
				}
				//update the others now			
				for(int i = visited.size()-2; i >= 0; i--) {
					r = new double[reward.length];
					for (int k =0; k < reward.length; k++) {
						r[k] = Math.exp(reward[k]);
					}
					
					if(visited.get(i).getNode() instanceof StandardNode)
						((StandardNode)visited.get(i).getNode()).update(r, visited.get(i+1).getNode().getKey(), nRollouts);
					else
						visited.get(i).getNode().update(r, nRollouts);
					logActProb = Math.log(visited.get(i).getActProb());
					for (int k =0; k < reward.length; k++) {
						reward[k] += logActProb;
					}
				}
			}else {
				HashMapList<TreeNode, double[]> updateValues = new HashMapList<TreeNode, double[]>();
				HashMapList<TreeNode, TreeNode> updateAction = new HashMapList<TreeNode, TreeNode>();
				//update the terminal/leaf node first
				double[] r = new double[reward.length];
				for (int k =0; k < reward.length; k++) {
					r[k] = Math.exp(reward[k]);
				}
				
				if(visited.get(visited.size()-1).getNode() instanceof StandardNode)
					((StandardNode)visited.get(visited.size()-1).getNode()).update(r, null, nRollouts);
				else
					visited.get(visited.size()-1).getNode().update(r, nRollouts);
				
				double logActProb = Math.log(visited.get(visited.size()-1).getActProb());
				for (int k =0; k < reward.length; k++) {
					reward[k] += logActProb;
				}

				for(int i = visited.size()-2; i >= 0; i--) {
					r = new double[reward.length];
					for (int k =0; k < reward.length; k++) {
						r[k] = Math.exp(reward[k]);
					}
					updateValues.put(visited.get(i).getNode(), r);
					updateAction.put(visited.get(i).getNode(),visited.get(i+1).getNode());
					logActProb = Math.log(visited.get(i).getActProb());
					for (int k =0; k < reward.length; k++) {
						reward[k] += logActProb;
					}
				}
								
				for(TreeNode state : updateValues.keySet()) {
					HashSet<TreeNode> updated = new HashSet<TreeNode>();
					ArrayList<TreeNode> actList = updateAction.get(state);
					ArrayList<double[]> retList = updateValues.get(state);
					for(int i=0; i < actList.size(); i++) {
						if(updated.contains(actList.get(i)))
							continue;
						if(state instanceof StandardNode)
							((StandardNode)state).update(retList.get(i), actList.get(i).getKey(), nRollouts);
						else
							state.update(retList.get(i), nRollouts);
						updated.add(actList.get(i));
					}
				}
			}
		}else {
			//NOTE: since we don't apply any expectation or discount the nodes are updated from start to finish here
			HashMapList<TreeNode, TreeNode> pairs = new HashMapList<>();
			//update all visited nodes but avoid duplicates
			for(int i = 0; i < visited.size()-1; i++){
				if(pairs.containsKeyValue(visited.get(i).getNode(),visited.get(i+1).getNode())){
					continue;
				}
				if(visited.get(i).getNode() instanceof StandardNode)
					((StandardNode)visited.get(i).getNode()).update(reward, visited.get(i+1).getNode().getKey(), nRollouts);
				else
					visited.get(i).getNode().update(reward, nRollouts);
				if(!everyVisit)
					pairs.put(visited.get(i).getNode(), visited.get(i+1).getNode());
			}
			//update the terminal/leaf node
			if(visited.get(visited.size()-1).getNode() instanceof StandardNode)
				((StandardNode)visited.get(visited.size()-1).getNode()).update(reward, null, nRollouts);
			else
				visited.get(visited.size()-1).getNode().update(reward, nRollouts);
		}
		
	}
	
	@Override
	public String toString() {
		return "[name-" + this.getClass().getName() + "; expectedReturn-" + expectedReturn + "; everyVisit-" + everyVisit + "]";
	}


}
