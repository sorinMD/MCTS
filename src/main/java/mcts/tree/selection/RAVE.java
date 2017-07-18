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
			return selectActionUCTRAVE((StandardNode)tn, tree);
		}
		
		System.err.println("Unexpected random action selection performed in the tree policy");
		Game game = gameFactory.getGame(tn.getState());
		game.performAction(game.sampleNextAction());
		BooleanNodePair pair = new BooleanNodePair(true,tree.getNode(new Key(game.getState())));
		return pair;
	}
    
	/**
	 * Select node based on the best UCT-RAVE value.
	 * Ties are broken randomly.
	 * 
	 * @param node
	 * @param tree
	 * @return
	 */
    private BooleanNodePair selectActionUCTRAVE(TreeNode node, Tree tree){
    	ThreadLocalRandom rnd = ThreadLocalRandom.current();
    	double alpha; 
    	double q;
        double maxv = -Double.MAX_VALUE;
        int idx = 0;//the idx of the chosen node
        boolean allSiblingsVisited = true;
        int nChildren = ((StandardNode) node).getChildren().size();
        AtomicDoubleArray p = ((StandardNode)node).pValue;
        //minor optimisation.. if there is a single child no need to compute anything;
        if(nChildren > 1){
            //precompute sum and 'freeze' statistics
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
	        
	        for (int k=0; k<((StandardNode) node).getChildren().size(); k++){
	            if (visits[k] < MINVISITS){
	            	q = Double.MAX_VALUE - rnd.nextDouble();
	                allSiblingsVisited = false;
	            }
	            else{
	            	alpha = Math.max(0.0, (V - visits[k]) / V);
	                q = ((double)wins[k] - vLoss[k])/(visits[k]);
	                //include exploration factor
	                q += C0*Math.sqrt(Math.log(sumVisits)/(visits[k])) + rnd.nextDouble() * eps;
	                q = alpha * p.get(k) + (1-alpha)*q;
	            }
	            if (maxv <= q){
	                maxv = q;
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
