package mcts.game.catan;

import mcts.MCTSConfig;
import mcts.game.GameConfig;
import mcts.game.GameFactory;
import mcts.game.catan.typepdf.ActionTypePdf;
import mcts.game.catan.typepdf.UniformActionTypePdf;

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
    /** The maximum number of resources to list the discard options for. Anything above this we will never run enough rollouts to even evaluate. */
    public int N_MAX_DISCARD = 8;
    /** Maximum number of resources in the victim's hand for which we consider all possible outcomes. Anything above this the system becomes slow, and also we will never run enough rollouts to consider all possible outcome states. */
    public int N_MAX_RSS_STEAL = 15;
    /** Should make the discard action observable in the rollouts? */
    public boolean OBS_DISCARDS_IN_ROLLOUTS = false;
	/** Sample uniformly over the action types before sampling specifics in rollouts */
    public boolean SAMPLE_FROM_DISTRIBUTION_OVER_TYPES_IN_ROLLOUTS = true;
    /** Maximum number of offers per turn, i.e. the total number of offers made*/
    public int OFFERS_LIMIT = Integer.MAX_VALUE;
    /** If an even dist over types is not used, is sampling of a single trade option allowed? This is the option in between the two extremes. TODO: this should be removed as there is no benefit to it */
    public boolean ALLOW_SAMPLING_IN_NORMAL_STATE = false;
    /** Use the abstract belief representation to enumerate the possible actions TODO: fix it!!! this breaks the current version of MCTS as we may end up executing illegal actions! */
    public boolean ABSTRACT_BELIEF = false;
    /** Make all new belief chance events sample from a uniform distribution to speed up the belief game model.*/
    public boolean UNIFORM_BELIEF_CHANCE_EVENTS = false;
    /** Special option to list the partially-observable actions of the opponents when the game is not fully-observable. It is needed if either the algorithm is POMCP or we want to make the POMs' effects observable.*/
    public boolean LIST_POMS = false;
    /** Special option that makes the POM effects observable. It allows planning in the game starting from a belief and in time this game may become fully-observable. */
    public boolean OBSERVABLE_POM_EFFECTS = false;
	/** Makes all actions equally likely to be legal and it also ignores the type distribution if there is any during belief rollouts. */
    public boolean ENFORCE_UNIFORM_TYPE_DIST_IN_BELIEF_ROLLOUTS = false;
    /** What distribution over action types should be used in rollouts.*/
    public ActionTypePdf rolloutTypeDist = new UniformActionTypePdf();
    /** Drawing a vp card becomes an observable move*/
    public boolean OBSERVABLE_VPS = false;
    
    public CatanConfig() {
    	id = GameFactory.CATAN;
	}
    
    @Override
    protected CatanConfig copy() {
    	CatanConfig newConfig = new CatanConfig();
    	newConfig.TRADES = this.TRADES;
    	newConfig.NEGOTIATIONS = this.NEGOTIATIONS;
    	newConfig.ALLOW_COUNTEROFFERS = this.ALLOW_COUNTEROFFERS;
    	newConfig.N_MAX_DISCARD = this.N_MAX_DISCARD;
    	newConfig.N_MAX_RSS_STEAL = this.N_MAX_RSS_STEAL;
    	newConfig.OBS_DISCARDS_IN_ROLLOUTS = this.OBS_DISCARDS_IN_ROLLOUTS;
    	newConfig.SAMPLE_FROM_DISTRIBUTION_OVER_TYPES_IN_ROLLOUTS = this.SAMPLE_FROM_DISTRIBUTION_OVER_TYPES_IN_ROLLOUTS;
    	newConfig.OFFERS_LIMIT = this.OFFERS_LIMIT;
    	newConfig.ALLOW_SAMPLING_IN_NORMAL_STATE = this.ALLOW_SAMPLING_IN_NORMAL_STATE;
    	newConfig.ABSTRACT_BELIEF = this.ABSTRACT_BELIEF;
    	newConfig.UNIFORM_BELIEF_CHANCE_EVENTS = this.UNIFORM_BELIEF_CHANCE_EVENTS;
    	newConfig.LIST_POMS = this.LIST_POMS;
    	newConfig.OBSERVABLE_POM_EFFECTS = this.OBSERVABLE_POM_EFFECTS;
    	newConfig.ENFORCE_UNIFORM_TYPE_DIST_IN_BELIEF_ROLLOUTS = this.ENFORCE_UNIFORM_TYPE_DIST_IN_BELIEF_ROLLOUTS;
    	newConfig.rolloutTypeDist = this.rolloutTypeDist;
    	newConfig.OBSERVABLE_VPS = this.OBSERVABLE_VPS;
    	return newConfig;
    }
	
    public void selfCheck(MCTSConfig config){
    	if(ABSTRACT_BELIEF) {
    		System.err.println("WARNING: listing and performing actions using the abstract representation is not stable in the current MCTS implementation");
    	}
    	if(OBSERVABLE_POM_EFFECTS && !LIST_POMS) {
    		System.err.println("WARNING: OBSERVABLE_POM_EFFECTS doesn't have any effect without LIST_POMS. Adding LIST_POMS");
    		LIST_POMS = true;
    	}
    	if(config.pomcp) {
    		if(!LIST_POMS) {
	    		System.err.println("WARNING: POMCP needs the partially-observable moves to be listed. Adding LIST_POMS to game configuration");
	    		LIST_POMS = true;
    		}
    		if(OBS_DISCARDS_IN_ROLLOUTS) {
    			System.err.println("WARNING: POMCP already performs observable rollouts. Removing OBS_DISCARDS_IN_ROLLOUTS from game configuration");
    			OBS_DISCARDS_IN_ROLLOUTS = false;
    		}
    	}
    }
    
}
