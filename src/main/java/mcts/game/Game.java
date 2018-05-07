package mcts.game;

import mcts.tree.node.TreeNode;
import mcts.utils.Options;

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
	 * @param sample
	 *            if this action was sampled according to an efficient game specific
	 *            method or not. A simple rule of thumb is if the options were
	 *            listed with sample true this should also be true and vice-versa.
	 */
	public void performAction(int[] a, boolean sample);

	/**
	 * @param sample
	 *            flag for deciding if should include all actions or just sample
	 *            from the legal ones. (e.g. usage: set to true if this is called
	 *            while performing rollouts)
	 * @return a list of all the legal actions given the current state
	 */
	public Options listPossiblities(boolean sample);

	/**
	 * @return a clone of the game
	 */
	public Game copy();

	/**
	 * Method for sampling the next action at random or based on the chances built
	 * into the game logic;
	 * 
	 * @return the next action description
	 */
	public int[] sampleNextAction();

	/**
	 * Method for sampling the next action at random or based on the chances built
	 * into the game logic;
	 * 
	 * @return the index of the next legal action
	 */
	public int sampleNextActionIndex();

	public TreeNode generateNode();

	/**
	 * Executes one random action.
	 * 
	 * @param belief
	 */
	public void gameTick();
}
