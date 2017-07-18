package mcts.tree.selection;

import java.util.concurrent.ThreadLocalRandom;

import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.tree.Tree;
import mcts.tree.node.ChanceNode;
import mcts.tree.node.Key;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;
import mcts.utils.BooleanNodePair;

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
    
    public BooleanNodePair selectAction(TreeNode tn, Tree tree, GameFactory gameFactory){
		if(tn instanceof ChanceNode){
			//sample from the possible outcomes and get the child node
			Game game = gameFactory.getGame(tn.getState());
			game.performAction(game.sampleNextAction());
			TreeNode child = game.generateNode();
			//also add the new node to the tree if missing
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
        Key chosen = null;
        boolean allSiblingsVisited = true;
        StandardNode parent = ((StandardNode) node);
        int idx = -1;
        
        int sumVisits = 0;
        for (int k=0; k< parent.getChildren().size(); k++){
        	sumVisits+=parent.childVisits[k];
        }
        
        for (int k=0; k< parent.getChildren().size(); k++){
            
        	if (parent.childVisits[k] < MINVISITS){
            	v = Double.MAX_VALUE - rnd.nextDouble();
                allSiblingsVisited = false;
            }
            else{
                v = ((double)parent.wins[parent.getCurrentPlayer()][k] - parent.vloss[k])/parent.childVisits[k];
                //include exploration factor
                v += C0*Math.sqrt(Math.log(sumVisits)/(parent.childVisits[k])) + rnd.nextDouble() * eps;
            }
            if (maxv <= v){
                maxv = v;
                chosen = parent.getChildren().get(k);
                idx = k;
            }
        }
        
		/*
		 * increment virtual loss to discourage other threads from selecting the
		 * same action. This also breaks cyclic behaviour with time
		 */
        parent.vloss[idx]++;
        TreeNode child = tree.getNode(chosen);
        
        BooleanNodePair pair = new BooleanNodePair(allSiblingsVisited, child);
        
        return pair;
        
    }
}
