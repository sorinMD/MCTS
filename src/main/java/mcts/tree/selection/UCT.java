package mcts.tree.selection;

import java.util.concurrent.ThreadLocalRandom;

import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.tree.Tree;
import mcts.tree.node.StandardNode;
import mcts.tree.node.ChanceNode;
import mcts.tree.node.Key;
import mcts.tree.node.TreeNode;
import mcts.utils.BooleanNodePair;

/**
 * UCT selection policy with afterstates 
 * 
 * @author sorinMD
 *
 */
public class UCT extends SelectionPolicy{
    public UCT() {}
    
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
			return selectActionUCT((StandardNode)tn, tree);
		}
		
		System.err.println("Unexpected random action selection performed in the tree policy");
		Game game = gameFactory.getGame(tn.getState());
		game.performAction(game.sampleNextAction());
		BooleanNodePair pair = new BooleanNodePair(true,tree.getNode(new Key(game.getState())));
		return pair;
	}
    
	/**
	 * Select node based on the best UCT value.
	 * Ties are broken randomly.
	 * 
	 * @param node
	 * @param tree
	 * @return
	 */
    private BooleanNodePair selectActionUCT(TreeNode node, Tree tree){
    	ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double v;
        double maxv = -Double.MAX_VALUE;
        TreeNode chosen = null;
        boolean allSiblingsVisited = true;
        int nChildren = ((StandardNode) node).getChildren().size();
        //precompute sum and 'freeze' values
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
        	TreeNode n = tree.getNode(((StandardNode) node).getChildren().get(k));
            if (visits[k] < MINVISITS){
            	v = Double.MAX_VALUE - rnd.nextDouble();
                allSiblingsVisited = false;
            }
            else{
                v = ((double)wins[k] - vLoss[k])/(visits[k]);
                //include exploration factor
                v += C0*Math.sqrt(Math.log(sumVisits)/(visits[k])) + rnd.nextDouble() * eps;
            }
            if (maxv <= v){
                maxv = v;
                chosen = n;
            }
        }
        
		/*
		 * increment virtual loss to discourage other threads from selecting the
		 * same action. This also breaks cyclic behaviour with time
		 */
        chosen.incrementVLoss();
        BooleanNodePair pair = new BooleanNodePair(allSiblingsVisited, chosen);
        
        return pair;
        
    }
}
