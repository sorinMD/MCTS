package mcts.tree.node;

import java.util.Arrays;

import mcts.game.GameFactory;

/**
 * A tree node that only acts as a container for the statistics; i.e. the number
 * of wins/number of visits/prior value etc.
 * 
 * @author sorinMD
 *
 */
public abstract class TreeNode {

	private final int[] state;
	private final int[] belief;
	
	int nVisits;
    double[] wins;
	int vLoss;
	private final int currentPlayer;
	private boolean leaf = true;
	private final boolean terminal;
	/**
	 * Field required for ISMCTS way of computing parentVisits when this node was legal
	 */
	private int parentVisits;
	
	/**
	 * Flag set when an external evaluation metric is used for seeding.
	 */
	private boolean evaluated = false;
	
	public TreeNode(int[] state, int[] belief, boolean terminal, int cpn) {
	    this.state = state;
	    this.belief = belief;
		this.terminal = terminal;
		currentPlayer = cpn;
		vLoss = 0;
		nVisits = 0;
		parentVisits = 0;
	    wins = new double[GameFactory.nMaxPlayers()]; //TODO: find a way to set this value depending on the current game
	}
	
	public int getCurrentPlayer(){
		return currentPlayer;
	}
	
	/**
	 * @return a clone of the state
	 */
	public int[] getState(){
		return state.clone();
	}
	
	public boolean isLeaf(){
		return leaf;
	}
	
	public void setLeaf(boolean leaf){
		this.leaf = leaf;
	}

	public boolean isTerminal(){
		return terminal;
	}
	
	public Key getKey(){
		return new Key(state, belief);
	}
	
	/**
	 * Update this node and remove the virtual loss.
	 * 
	 * TODO: it remains to be seen if synchronising makes this too slow...
	 * initial tests indicate this is a negligible increase, while it is
	 * beneficial to synchronise the updates so that no results are lost
	 */
	public synchronized void update(double[] reward, int nRollouts) {
		for(int i = 0; i < wins.length; i++)	
			wins[i]+=reward[i];
		nVisits+=nRollouts;
		vLoss = 0;
	}
	
	public int getParentVisits() {
		return parentVisits;
	}
	
	public void incrementParentVisits() {
		parentVisits++;
	}
	
	public double getWins(int pn) {
		return wins[pn];
	}
	
	public int getnVisits() {
		return nVisits;
	}
	
	public int getvLoss() {
		return vLoss;
	}
	
	public void setEvaluated(boolean evaluated){
		this.evaluated = evaluated;
	}
	
	public boolean isEvaluated(){
		return evaluated;
	}
	
	public void incrementVLoss(){
		this.vLoss++;
	}
	
	/**
	 * Can this node be expanded?
	 * 
	 * @return
	 */
	public abstract boolean canExpand();
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof TreeNode){
			return Arrays.equals(state, ((TreeNode) obj).state);
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return Arrays.hashCode(state);
	}
	
}
