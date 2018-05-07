package mcts.tree.selection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.tree.Tree;
import mcts.tree.node.ChanceNode;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;
import mcts.utils.Selection;

/**
 * A utility for selecting the action in the tree part of the algorithm.
 * 
 * @author sorinMD
 *
 */
@JsonTypeInfo(use = Id.CLASS,
include = JsonTypeInfo.As.PROPERTY,
property = "type")
@JsonSubTypes({
	@Type(value = UCT.class),
	@Type(value = UCTAction.class),
	@Type(value = PUCT.class),
	@Type(value = RAVE.class),
})
public abstract class SelectionPolicy {
	/**
	 * Flag for deciding if we should use the action probabilities to weight the selection.
	 * Should be used with MCTS with belief. It has no effect on POMCP or observable MCTS.
	 */
	public boolean weightedSelection = true;
	protected final double eps = 1e-6;
	/**
	 * The number of visits before the statistics in the nodes are used for
	 * computing the node value. Equal to saying how many visits before adding
	 * this node to the tree.
	 */
    public int MINVISITS = 1;
	/**
	 * The exploration constant
	 */
    public double C0 = 1.0;
    /**
     * Flag to decide if the parent visits should take into account if the node was legal
     */
    public boolean ismcts = false; 
    
	/**
	 * Used in the tree level of the algorithm to select the next action either only according to the game's
	 * transition model if it is a chance node or according to the selection policy if it is a {@link StandardNode}
	 * 
	 * @param tn
	 * @param tree
	 * @param gameFactory factory that can be used to generate a game and provide access to the belief
	 * @param obsGame the game that provides observations if the algorithm is POMCP, null otherwise
	 * @return a pair, where the node is the selected child node and the boolean
	 *         represents if all the siblings have been visited at least
	 *         {@link #TreePolicy#MINVISITS} number of times
	 */
	public Selection selectChild(TreeNode tn, Tree tree, GameFactory gameFactory, Game obsGame) {
		if(tn instanceof StandardNode){
			return selectChild((StandardNode)tn, tree, gameFactory, obsGame);
		}else {//chance nodes
			if(obsGame != null) {//the POMCP case
				//two cases when the chance exists in the fully-observable game, or when this is a belief specific chance node 
				TreeNode temp = obsGame.generateNode();
				if(temp instanceof ChanceNode) {
					//perform the same action in both observable state and belief, but sample from the observable state
					int[] action = obsGame.sampleNextAction();
					obsGame.performAction(action, true);					
					Game game = gameFactory.getGame(tn.getState());
					game.performAction(action,true);
					TreeNode child = game.generateNode();
					tree.putNodeIfAbsent(child);
					Selection pair = new Selection(true, tree.getNode(child.getKey()),1.0);
					return pair;
				}else {
					/* 
					 * The special case where the chance node decides whether the game is won or not in the belief space.
					 * This decision is already made in the observable game, so choose the action that leads us to the same situation as in the observable game.
					*/
					Game game = gameFactory.getGame(tn.getState());
					ArrayList<int[]> actions = game.listPossiblities(false).getOptions();
					int[] chosen = null;
					for(int[] act : actions) {
						Game t = game.copy();
						t.performAction(act, false);
						if(t.isTerminal() == obsGame.isTerminal()) {
							chosen = act;
							break;
						}
					}
					game.performAction(chosen, false);
					TreeNode child = game.generateNode();
					tree.putNodeIfAbsent(child);
					Selection pair = new Selection(true, tree.getNode(child.getKey()),1.0);
					return pair; 
				}
				
			}else {
				//sample from the possible outcomes and get a possible child node
				Game game = gameFactory.getGame(tn.getState());
				game.gameTick();
				TreeNode child = game.generateNode();
				//also add the new node to the tree as we want to build the tree past this step
				tree.putNodeIfAbsent(child);
				//it is a chance node, so MINVISITS doesn't apply here
				Selection pair = new Selection(true, tree.getNode(child.getKey()),1.0);
				return pair; 
			}
		}
	}
    
	/**
	 * Implements the specific policy
	 * @param tn
	 * @param tree
	 * @param gameFactory factory that can be used to generate a game and provide access to the belief
	 * @param obsGame the game that provides observations if the algorithm is POMCP, null otherwise
	 * @return a pair, where the node is the selected child node and the boolean
	 *         represents if all the siblings have been visited at least
	 *         {@link #TreePolicy#MINVISITS} number of times
	 */
	protected abstract Selection selectChild(StandardNode tn, Tree tree, GameFactory gameFactory, Game obsGame);
	
	/**
	 * To be called after the search has finished to get the best action based
	 * on value. Ties are broken randomly.
	 * 
	 * @param tree
	 * @return the action with the highest value
	 */
    public int selectBestAction(Tree tree){
    	ThreadLocalRandom rnd = ThreadLocalRandom.current();
    	StandardNode root = (StandardNode)tree.getRoot();
        double v, maxv;
        int maxind=0;
        maxv = -Double.MAX_VALUE;
    	for (int k=0; k<root.getChildren().size(); k++){
    		if(this instanceof UCTAction)
    			v = ((double)root.wins[root.getCurrentPlayer()][k])/root.childVisits[k] + rnd.nextDouble() * eps;
    		else{
	        	TreeNode n = tree.getNode(root.getChildren().get(k));
	            v = ((double)n.getWins(root.getCurrentPlayer()))/(n.getnVisits()) + rnd.nextDouble() * eps;
    		}
            
            if (maxv<=v){
                maxv = v;
                maxind = k;
            }
        }
        return maxind;
    }
	
	/**
	 * To be called after the search has finished to get the best action based
	 * on number of visits. Ties are broken randomly.
	 * 
	 * @param tree
	 * @return the action that has the highest number of visits
	 */
    public int selectMostExploredAction(Tree tree){
    	ThreadLocalRandom rnd = ThreadLocalRandom.current();
    	StandardNode root = (StandardNode)tree.getRoot();
    	double maxVisits = 0;
    	double current = 0;
        int maxind=0;
    	for (int k=0; k<root.getChildren().size(); k++){
            if(this instanceof UCTAction)
            	current = ((double)root.childVisits[k]) + rnd.nextDouble() * eps;
            else{
	        	TreeNode n = tree.getNode(root.getChildren().get(k));
	            current = ((double)n.getnVisits()) + rnd.nextDouble() * eps;
            }
        	if (maxVisits <= current){
                maxVisits = current;
                maxind = k;
            }
        }
        return maxind;
    }
    
	/**
	 * To be called after the search has finished. Adds a small random value to
	 * each such that ties will be broken randomly when the list is ordered.
	 * 
	 * @param tree
	 * @return the values of the children which is of the same size and has the
	 *         same order as the set of legal actions actions
	 */
    public double[] getChildrenValues(Tree tree){
    	ThreadLocalRandom rnd = ThreadLocalRandom.current();
    	StandardNode root = (StandardNode)tree.getRoot();
    	double[] values = new double[root.getChildren().size()];
    	for (int k=0; k<root.getChildren().size(); k++){
    		if(this instanceof UCTAction)
    		values[k] = ((double)root.wins[root.getCurrentPlayer()][k])/root.childVisits[k] + rnd.nextDouble() * eps;
    		else{
    			TreeNode n = tree.getNode(root.getChildren().get(k));
    			values[k] = ((double)n.getWins(root.getCurrentPlayer()))/(n.getnVisits()) + rnd.nextDouble() * eps;
    		}
        }
    	return values;
    }
    
	@Override
	public String toString() {
		return "[name-" + this.getClass().getName() + "; C-" + C0 + "; MINVISITS-" + MINVISITS + "]";
	}

    
}
