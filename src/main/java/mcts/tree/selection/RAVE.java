package mcts.tree.selection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import com.google.common.util.concurrent.AtomicDoubleArray;

import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.tree.Tree;
import mcts.tree.node.StandardNode;
import mcts.tree.node.Key;
import mcts.tree.node.TreeNode;
import mcts.utils.Selection;
import mcts.utils.Utils;

/**
 * UCT-RAVE selection policy. Used only for handling a prior value.
 * 
 * TODO: implement AMAF heuristic.
 * 
 * @author sorinMD
 *
 */
public class RAVE extends SelectionPolicy{
	public int V = 1;
    public RAVE(int V) {
    	this.V = V;
    }
    
	/**
	 * Select node based on the best UCT-RAVE value.
	 * Ties are broken randomly.
	 * 
	 * @param node
	 * @param tree
	 * @return
	 */
    protected Selection selectChild(StandardNode node, Tree tree, GameFactory factory, Game obsGame){
    	ThreadLocalRandom rnd = ThreadLocalRandom.current();
    	double alpha; 
    	double q;
        double maxv = -Double.MAX_VALUE;
        int idx = 0;//the idx of the chosen node
        boolean allSiblingsVisited = true;
        ArrayList<Key> children = node.getChildren();
        ArrayList<Double> actLegalProb = node.getActionProbs();
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
        AtomicDoubleArray p = node.pValue;
        //minor optimisation.. if there is a single child no need to compute anything;
        if(nChildren > 1){
            //precompute sum and 'freeze' statistics
            int sumVisits = 0; 
            int[] visits = new int[nChildren];
            double[] wins = new double[nChildren];
            int[] vLoss = new int[nChildren];
            int[] parentVisits = new int[nChildren];
	        for (int k=0; k<nChildren; k++){
	        	TreeNode n = tree.getNode(children.get(k));
	        	synchronized (n) {
		        	visits[k] = n.getnVisits();
		        	wins[k] = n.getWins(node.getCurrentPlayer());
		        	vLoss[k] = n.getvLoss();
		        	sumVisits += visits[k];
		        	parentVisits[k] = n.getParentVisits();
	        	}
	        }
	        
	        for (int k=0; k<nChildren; k++){
	            if (visits[k] < MINVISITS){
	            	q = Double.MAX_VALUE - rnd.nextDouble();
	                allSiblingsVisited = false;
	                if(actLegalProb != null) {
	                	if(actLegalProb.get(k).doubleValue() == 0.0)
	                		q = -Double.MAX_VALUE;
	                	else
	                		q *= actLegalProb.get(k).doubleValue();
	                }
	            }
	            else{
	            	alpha = Math.max(0.0, (V - visits[k]) / V);
	                q = ((double)wins[k] - vLoss[k])/(visits[k]);
	                //include exploration factor
	                int pVisits = sumVisits;
	                if(ismcts)
	                	pVisits = parentVisits[k];
	                q += C0*Math.sqrt(Math.log(pVisits)/(visits[k])) + rnd.nextDouble() * eps;
	                q = alpha * p.get(k) + (1-alpha)*q;
	                //weigh with the probability that represents the likelihood of the action being legal
	                if(actLegalProb != null) {
	                	if(actLegalProb.get(k).doubleValue() == 0.0)
	                		q= -Double.MAX_VALUE; // we need to do this due to virtual loss, in which case we might get the value for other legal actions under 0
	                	else
	                		q *= actLegalProb.get(k).doubleValue();
	                }
	            }
				if(actLegalProb != null && actLegalProb.get(k) == 1.0)
					tree.getNode(children.get(k)).incrementParentVisits();
	            if (maxv <= q){
	                maxv = q;
	                idx = k;
	                if(actLegalProb != null) 
	                	actProb = actLegalProb.get(k);
	            }
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
        TreeNode chosen = tree.getNode(children.get(idx));
        chosen.incrementVLoss();
        Selection pair = new Selection(allSiblingsVisited, chosen, actProb);
        
        return pair;
        
    }
    
	@Override
	public String toString() {
		return "[name-" + this.getClass().getName() + "; C-" + C0 + "; MINVISITS-" + MINVISITS +"; V-" + V + "]";
	}

}
