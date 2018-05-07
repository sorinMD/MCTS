package mcts.utils;

import mcts.game.Game;

/**
 * A fully observable game and the probability of this being the true state.
 * 
 * @author sorinMD
 */
public class GameSample {
	private Game game;
	private double prob;
	
	public GameSample(Game g, double p) {
		game = g;
		prob = p;
	}
	
	public Game getGame() {
		return game;
	}
	
	public double getProb() {
		return prob;
	}
}
