package mcts.tree.selection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.tree.Tree;
import mcts.tree.node.StandardNode;
import mcts.tree.node.Key;
import mcts.tree.node.TreeNode;
import mcts.utils.Selection;
import mcts.utils.Utils;

/**
 * UCT selection policy with afterstates 
 * 
 * @author sorinMD
 *
 */
public class UCT extends SelectionPolicy{
    public UCT() {}
    
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
        TreeNode chosen = null;
        int chosenIdx = 0;
        boolean allSiblingsVisited = true;
        ArrayList<Key> children = node.getChildren();
        ArrayList<int[]> actions = node.getActions();
        ArrayList<Double> actLegalProb;
        if(weightedSelection) 
        	actLegalProb = node.getActionProbs();
        else
        	actLegalProb = new ArrayList<Double>(Collections.nCopies(actions.size(), 1.0));
        double actProb = 1.0;
        if(obsGame != null) {//POMCP case
        	ArrayList<int[]> stateActions = obsGame.listPossiblities(false).getOptions();
        	actLegalProb = Utils.createActMask(stateActions, actions);
        }
        int nChildren = children.size();
        //precompute sum and 'freeze' values
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
                v = (wins[k] - vLoss[k])/(visits[k]);
                //include exploration factor
                int pVisits = sumVisits;
                if(ismcts)
                	pVisits = parentVisits[k];
                v += C0*Math.sqrt(Math.log(pVisits)/(visits[k])) + rnd.nextDouble() * eps;
                //weigh with the probability that represents the likelihood of the action being legal
                if(actLegalProb != null) {
                	if(actLegalProb.get(k).doubleValue() == 0.0)
                		v= -Double.MAX_VALUE; // we need to do this due to virtual loss, in which case we might get the value for other legal actions under 0
                	else
                		v *= actLegalProb.get(k).doubleValue();
                }
            }
			if(actLegalProb != null && actLegalProb.get(k) == 1.0)
				tree.getNode(children.get(k)).incrementParentVisits();
            if (maxv <= v){
                maxv = v;
                chosen = tree.getNode(children.get(k));
                chosenIdx = k;
                if(actLegalProb != null) 
                	actProb = actLegalProb.get(k);
            }
        }
        
        //when game is not observable and we need to update belief; i.e. POMCP and other belief MCTS algorithms
        if(factory.getBelief() != null) { 
        	int[] action = actions.get(chosenIdx);
        	Game game = factory.getGame(node.getState());
        	game.performAction(action, false);
        	if(obsGame != null)
        		obsGame.performAction(action, false);
        }
        
		/*
		 * increment virtual loss to discourage other threads from selecting the
		 * same action. This also breaks cyclic behaviour with time
		 */
        chosen.incrementVLoss();
        Selection pair = new Selection(allSiblingsVisited, chosen, actProb);
        
        return pair;
        
    }
}
