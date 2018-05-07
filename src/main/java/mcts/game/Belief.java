package mcts.game;

/**
 * Simple interface for belief models.
 * 
 * @author sorinMD
 *
 */
public interface Belief {

	Belief copy();
	
	int[] getRepresentation();
}
