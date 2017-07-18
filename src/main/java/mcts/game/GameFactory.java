package mcts.game;

import mcts.game.catan.Catan;
import mcts.game.catan.CatanConfig;
import mcts.game.tictactoe.TicTacToe;

/**
 * Simple factory that generates game instances based on a configuration.
 * @author sorinMD
 *
 */
public class GameFactory {
	public static final int TICTACTOE = 0;
	public static final int CATAN = 1;
	
	private GameConfig gameConfig;
	
	public GameFactory(GameConfig gameConfig) {
		this.gameConfig = gameConfig;
	}
	
	public Game getNewGame(){
		if(gameConfig.id == CATAN){
			if(!Catan.board.init)
				 Catan.initBoard();
			return new Catan(((CatanConfig) gameConfig));
		}else
			return new TicTacToe();
	} 
	
	public Game getGame(int[] state){
		if(gameConfig.id == CATAN){
			if(!Catan.board.init)
				 Catan.initBoard();
			return new Catan(state, ((CatanConfig) gameConfig));
		}else
			return new TicTacToe(state);
	} 

	public GameConfig getConfig(){
		return gameConfig;
	}
	
	/**
	 * The max number of players across all games. 
	 * TODO: make it game specific
	 * @return
	 */
	public static int nMaxPlayers(){
		return 4;
	}
	
}
