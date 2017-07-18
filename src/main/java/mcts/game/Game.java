package mcts.game;

import java.util.ArrayList;

import mcts.tree.node.TreeNode;

/**
 * Game model interface. It must be implemented to use the current MCTS code.
 * 
 * @author sorinMD
 *
 */
public interface Game {

	/**
	 * @return a clone of the game state
	 */
	public int[] getState();

	public int getWinner();

	public boolean isTerminal();

	public int getCurrentPlayer();

	/**
	 * Updates the state description based on the chosen action
	 * 
	 * @param a
	 *            the chosen action
	 */
	public void performAction(int[] a);

	/**
	 * @param sample
	 *            flag for deciding if should include all actions or just sample
	 *            from the legal ones.(e.g. usage: if this is called while
	 *            performing random simulations, random sampling is good enough)
	 * @return a list of all the legal actions given the current state
	 */
	public ArrayList<int[]> listPossiblities(boolean sample);

	/**
	 * @return a clone of the game
	 */
	public Game copy();

	/**
	 * Method for sampling the next action at random or based on the chances
	 * built into the game logic;
	 * 
	 * @return the next action description
	 */
	public int[] sampleNextAction();

	/**
	 * Method for sampling the next action at random or based on the chances
	 * built into the game logic;
	 * 
	 * @return the index of the next legal action
	 */
	public int sampleNextActionIndex();

	public TreeNode generateNode();

}
