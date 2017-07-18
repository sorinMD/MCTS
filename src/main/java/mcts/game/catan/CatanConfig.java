package mcts.game.catan;

import mcts.game.GameConfig;
import mcts.game.GameFactory;

/**
 * Settlers of Catan specific configuration
 * 
 * @author sorinMD
 *
 */
public class CatanConfig extends GameConfig{
	//default values//
	/** Handle resource exchanges between players */
	public boolean TRADES = true;
    /** Allow full negotiations in the tree, i.e. offer, reject, accept */
    public boolean NEGOTIATIONS = true;
    /** Allow counter-offers in the tree */
    public boolean ALLOW_COUNTEROFFERS = false;
    /** The maximum number of resources to list the discard options for */
    public int N_MAX_DISCARD = 10;
	/** Sample uniformly over the action types before sampling specifics */
    public boolean EVEN_DISTRIBUTION_OVER_TYPES = true;
    /** Maximum number of offers per turn, i.e. the total number of offers made*/
    public int OFFERS_LIMIT = Integer.MAX_VALUE;
    /** If an even dist over types is not used, is sampling of a single trade option allowed? This is the option in between the two extremes. */
    public boolean ALLOW_SAMPLING_IN_NORMAL_STATE = false;
    
    public CatanConfig() {
    	id = GameFactory.CATAN;
	}
	
}
