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
	//stats on actions required if afterstates are not used
	public int[] childVisits;
	public int[][] wins;
	public int[] vloss;
	
	/**
	 * The P value (probability of selecting each action given a policy) of each
	 * action in the same order as the children are set. At the moment this is
	 * also used as value initialisation
	 */
	public AtomicDoubleArray  pValue;
	
	public StandardNode(int[] state, boolean terminal, int cpn) {
		super(state, terminal, cpn);
	}
	
	/**
	 * Adds the edges to the node.
	 * @param children
	 */
	public synchronized void addChildren(ArrayList<Key> children){
		if(!isLeaf())
			return;
		this.children = children;
		//initialise the pValue with a uniform distribution
		double[] dist = new double[children.size()];
		Arrays.fill(dist, 1.0/children.size());
		pValue = new AtomicDoubleArray(dist);
		//stats on actions required only if afterstates are not used
		childVisits = new int[children.size()];
		wins = new int[GameFactory.nMaxPlayers()][];
		for(int i = 0; i < GameFactory.nMaxPlayers(); i++){
			wins[i] = new int[children.size()];
		}
		vloss = new int[children.size()];
		setLeaf(false);
	}
	
	public ArrayList<Key> getChildren(){
		return this.children;
	}
	
	public boolean canExpand(){
		return true;
	}
	
	/**
	 * Used only if afterstates are not used, otherwise use the standard state update
	 * @param winner
	 * @param k
	 */
	public synchronized void update(int winner, Key k) {
		if(k != null)
		for(int idx = 0; idx < children.size(); idx++){
			if(children.get(idx).equals(k)){
				if (winner != -1) {
					wins[winner][idx]++;
				}else {
		 			/*
		 			 * this means everyone lost or the game hasn't finished yet (it
		 			 * could happen in Catan if roll-outs are too long, probably caused
		 			 * by no more legal building locations available on the board)
		 			 */
		 			System.err.println("WARNING: Possibly updating with results from a game that was not finished");
				}
				vloss[idx] = 0;
				childVisits[idx]++;
				break;
			}
		}
		nVisits++;
	}
	
}
