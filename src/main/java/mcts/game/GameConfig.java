package mcts.game;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

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

}
