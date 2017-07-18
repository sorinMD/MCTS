package mcts.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import mcts.game.Game;
import mcts.game.catan.GameStateConstants;

/**
 * A utility for playing the game following a policy until max depth or a
 * terminal node is reached
 * TODO: set the maxDepth field via the configuration file
 * @author sorinMD
 *
 */
public class SimulationPolicy {
	private static int maxDepth = 100000;
	public static void simulate(Game g){
		int depth = 0;
		while(!g.isTerminal()){
			ThreadLocalRandom r = ThreadLocalRandom.current();
			ArrayList<int[]> actions = g.listPossiblities(true);
			depth++;
			g.performAction(actions.get(r.nextInt(actions.size())));
			if(depth > maxDepth){
				System.err.println("WARNING: rollout reached max depth!!!");
				break;
			}
		}
	}
}
