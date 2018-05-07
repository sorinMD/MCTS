package mcts.game;

import mcts.game.catan.Catan;
import mcts.game.catan.CatanConfig;
import mcts.game.catan.CatanWithBelief;
import mcts.game.catan.belief.CatanDeterminizationSampler;
import mcts.game.catan.belief.CatanFactoredBelief;
import mcts.game.tictactoe.TicTacToe;

/**
 * Simple factory that generates game instances based on a configuration.
 * It also provides access to the correct determinization sampler, given the current game configuration and belief.
 * @author sorinMD
 *
 */
public class GameFactory {
	public static final int TICTACTOE = 0;
	public static final int CATAN = 1;
	
	private GameConfig gameConfig;
	private Belief belief;
	
	public GameFactory(GameConfig gameConfig, Belief belief) {
		this.gameConfig = gameConfig;
		this.belief = belief;
	}
	
	public Game getNewGame(){
		if(gameConfig.id == CATAN){
			if(belief != null) {
				if(!CatanWithBelief.board.init)
					 CatanWithBelief.initBoard();//we can safely create a new board here as this means that we don't need to use a specific board.
				return new CatanWithBelief(((CatanConfig) gameConfig), (CatanFactoredBelief) belief);
			}else {
				if(!Catan.board.init)
					 Catan.initBoard();
				return new Catan(((CatanConfig) gameConfig));
			}
		}else
			return new TicTacToe();
	} 
	
	public Game getGame(int[] state){
		if(gameConfig.id == CATAN){
			if(belief != null)
				return new CatanWithBelief(state, ((CatanConfig) gameConfig), (CatanFactoredBelief) belief);
			else
				return new Catan(state, ((CatanConfig) gameConfig));
		}else
			return new TicTacToe(state);
	} 

	public GameConfig getConfig(){
		return gameConfig;
	}
	
	public Belief getBelief() {
		return belief;
	}
	
	public DeterminizationSampler getDeterminizationSampler() {
		if(gameConfig.id == CATAN && belief != null)
			return new CatanDeterminizationSampler();
		return new NullDeterminizationSampler();
	}
	
	/**
	 * @return
	 */
	public static int nMaxPlayers(){
		return 4;
	}
	
	public GameFactory copy() {
		Belief clone = null;
		if(belief != null)
			 clone = belief.copy();
		GameFactory factory = new GameFactory(gameConfig.copy(), clone);
		return factory;
	}
	
}
