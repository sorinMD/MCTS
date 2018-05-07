package mcts.game;

import mcts.game.GameFactory;
import mcts.utils.GameSample;

/**
 * Simple sampler that can return one or more fully-observable states given the current belief and game.
 * 
 * @author sorinMD
 *
 */
public interface DeterminizationSampler{

	/**
	 * Samples one observable state from the current belief.
	 * @param factory the game factory that contains the current belief and the game configuration.
	 * @param the current game description to provide access to the state array
	 * @return a fully-observable game object
	 */
	public GameSample sampleObservableState(Game currentGame, GameFactory factory);
	
	/**
	 * Creates nSamples fully-observable games
	 * @param nSamples the number of samples
	 * @param belief current belief
	 * @param currentGame current game that contains the current state
	 * @param factory game factory to create new games
	 * @return
	 */
	public default GameSample[] sampleObservableStates(int nSamples, Game currentGame, GameFactory factory) {
		GameSample[] samples = new GameSample[nSamples];
		for(int i = 0; i < nSamples; i++) {
			samples[i] = sampleObservableState(currentGame, factory);
		}
		return samples;
	}

}
