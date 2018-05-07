package mcts.game.catan.typepdf;

import java.util.ArrayList;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import mcts.game.catan.GameStateConstants;

/**
 * A utility for selecting the action in the tree part of the algorithm.
 * 
 * @author sorinMD
 *
 */
@JsonTypeInfo(use = Id.CLASS,
include = JsonTypeInfo.As.PROPERTY,
property = "type")
@JsonSubTypes({
	@Type(value = UniformActionTypePdf.class),
	@Type(value = HumanActionTypePdf.class),
})
public abstract class ActionTypePdf implements GameStateConstants{

	/**
	 * @return the distribution given a set of legal types
	 */
	public abstract Map<Integer,Double> getDist(ArrayList<Integer> legalTypes);
}
