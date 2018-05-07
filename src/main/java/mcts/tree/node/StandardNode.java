package mcts.tree.node;

import java.util.ArrayList;
import java.util.Arrays;

import com.google.common.util.concurrent.AtomicDoubleArray;

import mcts.game.GameFactory;

/**
 * Standard non-chance node that can be expanded normally and the tree policy can be deterministic.
 * TODO: separate the P (exploration) and the Q (value for exploitation)
 * @author sorinMD
 *
 */
public class StandardNode extends TreeNode{
	
	private ArrayList<Key> children;
	/**
	 * The possible actions.
	 */
	private ArrayList<int[]> actions;
	
	/**
	 * The probabilities of actions to be legal.
	 */
	private ArrayList<Double> actionProbs;
	
	//stats on actions required if afterstates are not used
	public int[] childVisits;
	public double[][] wins;
	public int[] vloss;
	
	/**
	 * The P value (probability of selecting each action given a policy) of each
	 * action in the same order as the children are set. At the moment this is
	 * also used as value initialisation
	 */
	public AtomicDoubleArray  pValue;
	
	public StandardNode(int[] state, int[] belief, boolean terminal, int cpn) {
		super(state, belief, terminal, cpn);
	}
	
	/**
	 * Adds the edges to the node.
	 * @param children
	 */
	public synchronized void addChildren(ArrayList<Key> children, ArrayList<int[]> acts, ArrayList<Double> actionProbs){
		if(!isLeaf())
			return;
		this.actions = acts;
		this.children = children;
		this.actionProbs = actionProbs;
		//initialise the pValue with a uniform distribution
		double[] dist = new double[children.size()];
		Arrays.fill(dist, 1.0/children.size());
		pValue = new AtomicDoubleArray(dist);
		//stats on actions required only if afterstates are not used
		childVisits = new int[children.size()];
		wins = new double[GameFactory.nMaxPlayers()][];
		for(int i = 0; i < GameFactory.nMaxPlayers(); i++){
			wins[i] = new double[children.size()];
		}
		vloss = new int[children.size()];
		setLeaf(false);
	}
	
	public ArrayList<Key> getChildren(){
		return this.children;
	}
	
	public ArrayList<int[]> getActions(){
		return this.actions;
	}
	
	public ArrayList<Double> getActionProbs(){
		return this.actionProbs;
	}
	
	public boolean canExpand(){
		return true;
	}
	
	/**
	 * Used only if afterstates are not used, otherwise use the standard state update
	 * @param reward
	 * @param k
	 * @param nRollouts
	 */
	public synchronized void update(double[] reward, Key k, int nRollouts) {
		if(k != null)
		for(int idx = 0; idx < children.size(); idx++){
			if(children.get(idx).equals(k)){
				for(int i =0; i < wins.length; i++)
					wins[i][idx]+=reward[i];
				vloss[idx] = 0;
				childVisits[idx]+=nRollouts;
				break;
			}
		}
		nVisits+=nRollouts;
	}
	
}
