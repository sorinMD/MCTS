package mcts.tree.selection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.tree.Tree;
import mcts.tree.node.Key;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;
import mcts.utils.Selection;
import mcts.utils.Utils;

/**
 * Similar to the UCT class, but selecting based on the statistics stored in the
 * links, i.e. actions, rather than the resulting states. Used when afterstates
 * are not allowed.
 * 
 * @author sorinMD
 *
 */
public class UCTAction extends SelectionPolicy{
    public UCTAction() {}
    
	/**
	 * Select node based on the best UCT value.
	 * Ties are broken randomly.
	 * 
	 * @param node
	 * @param tree
	 * @return
	 */
    protected Selection selectChild(StandardNode node, Tree tree, GameFactory factory, Game obsGame){
    	ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double v;
        double maxv = -Double.MAX_VALUE;
        Key chosen = null;
        boolean allSiblingsVisited = true;
        ArrayList<Key> children = ((StandardNode) node).getChildren();
        ArrayList<Double> actLegalProb = ((StandardNode) node).getActionProbs();
        ArrayList<int[]> actions = node.getActions();
        if(weightedSelection)
        	actLegalProb = node.getActionProbs();
        else
        	actLegalProb = new ArrayList<Double>(Collections.nCopies(actions.size(), 1.0));
        double actProb = 1.0;
        if(obsGame != null) {
        	ArrayList<int[]> stateActions = obsGame.listPossiblities(false).getOptions();
        	actLegalProb = Utils.createActMask(stateActions, actions);
        }
        int nChildren = children.size();
        StandardNode parent = ((StandardNode) node);
        int idx = -1;
        
        //precompute sum and 'freeze' values
        int sumVisits = 0;
        int[] visits = new int[nChildren];
        double[] wins = new double[nChildren];
        int[] vLoss = new int[nChildren];
        for (int k=0; k< nChildren; k++){
        	sumVisits+=parent.childVisits[k];
        }
        synchronized (parent) {
        	wins = parent.wins[parent.getCurrentPlayer()].clone();
        	visits = parent.childVisits.clone();
        	vLoss = parent.vloss.clone();
        }
        
        for (int k=0; k< nChildren; k++){
            
        	if (visits[k] < MINVISITS){
            	v = Double.MAX_VALUE - rnd.nextDouble();
                allSiblingsVisited = false;
                if(actLegalProb != null) {
                	if(actLegalProb.get(k).doubleValue() == 0.0)
                		v = -Double.MAX_VALUE;
                	else
                		v *= actLegalProb.get(k).doubleValue();
                }
            }
            else{
                v = (wins[k] - vLoss[k])/visits[k];
                //include exploration factor
                v += C0*Math.sqrt(Math.log(sumVisits)/(visits[k])) + rnd.nextDouble() * eps;
                //weight with the probability that represents the likelihood of the action being legal
                if(actLegalProb != null) {
                	if(actLegalProb.get(k).doubleValue() == 0.0)
                		v= -Double.MAX_VALUE; // we need to do this due to virtual loss, in which case we might get the value for other legal actions under 0
                	else
                		v *= actLegalProb.get(k).doubleValue();
                }
            }
            if (maxv <= v){
                maxv = v;
                chosen = children.get(k);
                idx = k;
                if(actLegalProb != null) 
                	actProb = actLegalProb.get(k);
            }
        }
        
        if(factory.getBelief() != null) {
        	int[] action = actions.get(idx);
        	Game game = factory.getGame(node.getState());
        	game.performAction(action, false);
        	if(obsGame != null)
        		obsGame.performAction(action, false);
        }
        
		/*
		 * increment virtual loss to discourage other threads from selecting the
		 * same action. This also breaks cyclic behaviour with time
		 */
        parent.vloss[idx]++;
        TreeNode child = tree.getNode(chosen);
        
        Selection pair = new Selection(allSiblingsVisited, child, actProb);
        
        return pair;
        
    }
}
