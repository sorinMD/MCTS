package mcts.game.catan.belief;

import java.io.Serializable;

import mcts.game.catan.ResourceSet;

/**
 * A structure defining one possible situation of a player's resource hand.
 * The fluents are 'contained' in the two fields.
 * @author sorinMD
 */
public class Situation implements Serializable{
	/**the probability of this being the real hand*/
	public double probability;
	/**the set describing the player's hand in this situation*/
	public ResourceSet resSet;
	
	public Situation(double prob, ResourceSet r){
		probability = prob;
		resSet = r;
	}
}
