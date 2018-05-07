package mcts.game;

import mcts.utils.GameSample;

public class NullDeterminizationSampler implements DeterminizationSampler{

	@Override
	public GameSample sampleObservableState(Game currentGame, GameFactory factory) {
		return null;
	}

}
