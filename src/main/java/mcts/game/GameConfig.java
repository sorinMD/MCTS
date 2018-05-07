package mcts.game;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import mcts.MCTSConfig;
import mcts.game.catan.CatanConfig;
import mcts.game.tictactoe.TicTacToeConfig;

/**
 * Game configuration containing game specific options (e.g. allowing trades in
 * Catan)
 * 
 * @author sorinMD
 *
 */
@JsonTypeInfo(use = Id.CLASS,
include = JsonTypeInfo.As.PROPERTY,
property = "type")
@JsonSubTypes({
	@Type(value = CatanConfig.class),
	@Type(value = TicTacToeConfig.class)
})
public abstract class GameConfig {
	public int id;
	
	/**
	 * @return a deep copy of the object
	 */
	protected abstract GameConfig copy();
	
	/**
	 * Checks the current configuration for known mismatches between parameters.
	 * Also checks against the MCTS configuration and updates any of the missing parameters.
	 * @param config the configuration of the MCTS algorithm that will use the game model with this configuration.
	 */
	public abstract void selfCheck(MCTSConfig config);
	
}
