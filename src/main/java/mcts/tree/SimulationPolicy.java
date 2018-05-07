package mcts.tree;

import mcts.game.Game;

/**
 * A utility for playing the game following a policy until max depth or a
 * terminal node is reached
 * TODO: set the maxDepth field via the configuration file
 * @author sorinMD
 *
 */
public class SimulationPolicy {
	private static int maxDepth = 100000;
	/**
	 * Run the rollout policy to the end of the game
	 * @param state
	 * @return the game in the final state
	 */
	public static Game simulate(Game game){
		int depth = 0;
		while(!game.isTerminal()){
			depth++;
			game.gameTick();
			if(depth > maxDepth){
				System.err.println("WARNING: rollout reached max depth!!!");
				break;
			}
		}
		return game;
	}
}
