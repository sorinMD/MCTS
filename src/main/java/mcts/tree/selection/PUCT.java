package mcts.tree.selection;

import java.util.concurrent.ThreadLocalRandom;

import com.google.common.util.concurrent.AtomicDoubleArray;

import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.tree.Tree;
import mcts.tree.node.StandardNode;
import mcts.tree.node.ChanceNode;
import mcts.tree.node.Key;
import mcts.tree.node.TreeNode;
import mcts.utils.BooleanNodePair;

/**
 * PUCT selection policy for handling a prior probability of certain actions
 * being optimal (or for using an existing stochastic policy).
 * 
 * @author sorinMD
 *
 */
public class PUCT extends SelectionPolicy {
    public PUCT() {	}
    
    public BooleanNodePair selectAction(TreeNode tn, Tree tree, GameFactory gameFactory){
		if(tn instanceof ChanceNode){
			//sample from the possible outcomes and get a possible child node
			Game game = gameFactory.getGame(tn.getState());
			game.performAction(game.sampleNextAction());
			TreeNode child = game.generateNode();
			//also add the new node to the tree as we want to build the tree past this step
			tree.putNodeIfAbsent(child);
			//it is a chance node, so MINVISITS doesn't apply here
			BooleanNodePair pair = new BooleanNodePair(true, tree.getNode(child.getKey()));
			return pair; 
		}
		if(tn instanceof StandardNode){
			return selectActionPUCT((StandardNode)tn, tree);
		}
		
		System.err.println("Unexpected random action selection performed in the tree policy");
		Game game = gameFactory.getGame(tn.getState());
		game.performAction(game.sampleNextAction());
		BooleanNodePair pair = new BooleanNodePair(true,tree.getNode(new Key(game.getState())));
		return pair;
	}
	
	
	/**
	 * Select node based on the best PUCT value.
	 * Ties are broken randomly.
	 * @param node
	 * @param tree
	 * @return
	 */
    private BooleanNodePair selectActionPUCT(TreeNode node, Tree tree){
    	ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double v;
        double maxv = -Double.MAX_VALUE;
        int idx = 0;//the idx of the chosen node
        boolean allSiblingsVisited = true;
        AtomicDoubleArray p = ((StandardNode)node).pValue;
        int nChildren = ((StandardNode) node).getChildren().size();
        //minor optimisation.. if there is a single child no need to compute anything;
        if(nChildren > 1){
            //'freeze' values and precompute sum
            int sumVisits = 0; 
            int[] visits = new int[nChildren];
            int[] wins = new int[nChildren];
            int[] vLoss = new int[nChildren];
	        for (int k=0; k<nChildren; k++){
	        	TreeNode n = tree.getNode(((StandardNode) node).getChildren().get(k));
	        	visits[k] = n.getnVisits();
	        	wins[k] = n.getWins(node.getCurrentPlayer());
	        	vLoss[k] = n.getvLoss();
	        	sumVisits += visits[k];
	        }
	        for (int k=0; k<nChildren; k++){
	            if (visits[k] < MINVISITS){
	            	v = Double.MAX_VALUE - rnd.nextDouble();
	                allSiblingsVisited = false;
	            }
	            else{
	                v = ((double)wins[k] - vLoss[k])/(visits[k]);
	                //c*p(s,a)*sqrt(sum(n(s,b)))/1+n(s,a) + (small eps to break ties)
	                v += C0*p.get(k)*Math.sqrt(sumVisits-visits[k])/(1 + visits[k]) + rnd.nextDouble() * eps;
	            }
	            if (maxv <= v){
	                maxv = v;
	                idx = k;
	            }
	        }
        }
		/*
		 * increment virtual loss to discourage other threads from selecting the
		 * same action. This also breaks cyclic behaviour with time
		 */
        TreeNode chosen = tree.getNode(((StandardNode) node).getChildren().get(idx));
        chosen.incrementVLoss();
        BooleanNodePair pair = new BooleanNodePair(allSiblingsVisited, chosen);
        
        return pair;
        
    }

}