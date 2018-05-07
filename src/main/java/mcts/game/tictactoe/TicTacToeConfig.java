package mcts.game.tictactoe;

import mcts.MCTSConfig;
import mcts.game.GameConfig;
import mcts.game.GameFactory;

/**
 * TicTacToe specific configuration
 * 
 * @author sorinMD
 *
 */
public class TicTacToeConfig extends GameConfig{

	public TicTacToeConfig() {
		id = GameFactory.TICTACTOE;
	}

	@Override
	protected GameConfig copy() {
		return new TicTacToeConfig();
	}

	@Override
	public void selfCheck(MCTSConfig config) {}
}
