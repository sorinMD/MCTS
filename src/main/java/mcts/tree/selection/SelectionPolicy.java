package mcts.tree.selection;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import mcts.game.GameFactory;
import mcts.tree.Tree;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;
import mcts.utils.BooleanNodePair;

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
	 * Used in the tree level of the algorithm to select the next action
	 * @param tn
	 * @param tree
	 * @return a pair, where the node is the selected child node and the boolean
	 *         represents if all the siblings have been visited at least
	 *         {@link #TreePolicy#MINVISITS} number of times
	 */
	public abstract BooleanNodePair selectAction(TreeNode tn, Tree tree, GameFactory gameFactory);
    
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
	
}
