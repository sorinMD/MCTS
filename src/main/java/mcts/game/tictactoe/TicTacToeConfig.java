package mcts.game.tictactoe;

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
}
