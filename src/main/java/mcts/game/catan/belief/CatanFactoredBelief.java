package mcts.game.catan.belief;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import mcts.game.Belief;
import mcts.game.catan.GameStateConstants;
import mcts.game.catan.ResourceSet;
import mcts.utils.Timer;

/**
 * This class implements a simple symbolic reasoning over the hidden resources and development cards.
 * Hidden resources are tracked via a method inspired from situation calculus, while unknown development cards are tracked using the total for each
 * type in the deck and given the observed history.
 * The model should work for both 4 or 6 player games.
 * 
 * NOTE: contains 2 constructors: 
 * 1. when starting a new game; 
 * 2. when loading a game;
 * 
 * At the moment, this implementation assumes the agent has a perfect memory and no revision is performed;
 * @author sorinMD
 */
public class CatanFactoredBelief implements Serializable, Belief, GameStateConstants{
	/**
	 * The model that tracks the chances of the hidden development cards from this player's perspective.
	 */
	private DevCardModel devCardModel;
	
	/**
	 * Contains the player hand models and keeps track of the total resources of a specific type in a game.
	 */
	private ResourcesModel rssModel;
	
	public PlayerResourceModel[] getPlayerHandsModel(){
		return rssModel.getPlayerHandModel();
	}
	
	public DevCardModel getDevCardModel(){
		return devCardModel;
	}
	/**
	 * Default constructor to be called at the start of the game
	 * @param maxPlayers the number of players in the game
	 */
	public CatanFactoredBelief(int maxPlayers) {
		rssModel = new ResourcesModel(maxPlayers);
		devCardModel = new DevCardModel(maxPlayers);
	}
	
	/**
	 * Constructor cloning the old model
	 * @param maxPlayers the number of players in the game
	 */
	public CatanFactoredBelief(CatanFactoredBelief old) {
		rssModel = new ResourcesModel(old.rssModel);
		devCardModel = old.devCardModel.copy();
	}
	
	/**
	 * When loading without a memory; A pesimistic approach in the sense that it assumes the unknowns can be any resource type
	 * @param maxPlayers the number of players in the game
	 * @param possHands the sets representing the actual player hands. 
	 */
	public CatanFactoredBelief(int maxPlayers, HashMap<ResourceSet,Double>[] possHands){
		rssModel = new ResourcesModel(maxPlayers);
		ResourceSet totals = new ResourceSet();
		for(int i = 0; i < possHands.length; i++){
			rssModel.getPlayerHandModel(i).update(possHands[i]);
			rssModel.getPlayerHandModel(i).resetAbstractRepresentation();
			for(int j = 0; j < ResourceSet.NRESOURCES; j ++)
				totals.add(rssModel.getPlayerHandModel(i).rssAbs[PlayerResourceModel.MAX + j], j);
		}
		devCardModel = new DevCardModel(maxPlayers);//these are set when reinitialiseDevCardChances method is called
	}
	
	/**
	 * Reinitialise the dev card chances when a memory is not available; 
	 * NOTE: the required information is stored in this player's game object if a memory is not available
	 * @param totalUnplayedCards the sum of the remaining cards in the deck and the unplayed cards in each of the player's hand
	 * @param playedSoldiers the sum of played knights from everyone's hand
	 * @param playedMon the total of monopoly cards played
     * @param playedRB the total of road building cards played
	 * @param playedYOP the total of year of plenty cards played
	 * @param revealedVPs the total of victory cards observed
	 */
	public void reinitialiseDevCardChances(int totalUnplayedCards, int playedSoldiers, int playedMonos, int playedRBs, int playedYOPs, int revealedVPs){
		devCardModel.reinitialise(totalUnplayedCards, playedSoldiers, playedMonos, playedRBs, playedYOPs, revealedVPs);
	}
	
	/**
	 * A card has been played or bought by this player so we can update the chances for the remaining hidden ones
	 * @param cardType the type of card observed or played
	 * @param actType gain or lose, draw or play
	 * @param pn the player that executed the action
	 */
	public void updateDevCardModel(int cardType, int actType, int pn){
		if(actType == Action.GAIN) {
			devCardModel.updateDraw(pn, cardType);
		}else if(actType == Action.LOSE) {
			devCardModel.updatePlay(pn, cardType);
		}else
			System.err.println("Unknwon dev card action.");
	}
	
	/**
	 * Note: this should only be called for opponents!!!
	 * Updates the number of nonvp cards based on the player's score
	 * @param pn the player number
	 * @param score number of current vp including the already revealed cards!!!
	 */
	public void updateNonVPDevCards(int score, int pn) {
		devCardModel.updateNonVP(pn, score);
	}
	
	/**
	 * Performs a revision over all possible dev card hands and reveals any cards that can be revealed.
	 */
	public void reviseDevCardModel() {
		devCardModel.revise(); 
	}
	
	public int getRevealedVP(int pn) {
		return devCardModel.getRevealedVP(pn);
	}
	
	public int getTotalRemainingDevCards() {
		return devCardModel.getTotalRemainingCards();
	}
	
	/**
	 * @return true if all hands are known (observable or can be inferred), false otherwise
	 */
	public boolean isRssModelObs() {
		boolean observable = true;
		for(PlayerResourceModel phm : rssModel.getPlayerHandModel()) {
			if(!phm.isFullyObservable()) {
				observable = false;
				break;
			}
		}
		return observable;
	}
	
	public ResourcesModel getResourceModel() {
		return rssModel;
	}
	
	/**
	 * @param pn the player number for whom to get the hand
	 * @return a stac resource set if there is only one possibility, or null if multiple
	 */
	public ResourceSet getHandIfOnlyOnePossible(int pn){
		if(rssModel.getPlayerHandModel(pn).isFullyObservable())
			return rssModel.getPlayerHandModel(pn).getHand();
		return null;
	}
	
	/**
	 * NB: this doesn't return a possible hand for the specific player
	 * @param pn the player number for whom we get the information
	 * @return a stac resource set which contains the max amount for each resource type this player may have in his hand. 
	 */
	public ResourceSet getMaxAmountForEachType(int pn){
		return rssModel.getPlayerHandModel(pn).getMaxAmountForEachType();
	}
	
	@Override
	public int[] getRepresentation() {
		int players = rssModel.getPlayerHandModel().length;
		//rss for each player based on the abstract rep, number of hands
		//remaining dev cards, for each player have the total, nonvp and revealed cards.
		int size = rssModel.getPlayerHandModel(0).rssAbs.length * players + 1 + N_DEVCARDTYPES + (2 + N_DEVCARDTYPES)  * players;
		int[] representation = new int[size];
		int idx = 0;
		for(int i = 0; i < players; i++) {
			int[] rssAbs = rssModel.getPlayerHandModel(i).rssAbs;
			for(int j = 0; j < rssAbs.length; j++) {
				representation[idx] = rssAbs[j];
				idx++;
			}
		}
		DevCardModel devModel = getDevCardModel();
		representation[idx] = devModel.getTotalRemainingCards();
		idx++;
		for(int i = 0; i < 5; i++) {
			representation[idx] = devModel.getRemaining(i);
			idx++;
		}
		for(int i = 0; i < players; i++) {
			representation[idx] = devModel.getNonVPCards(i);
			idx++;
			representation[idx] = devModel.getTotalCards(i);
			idx++;
			int[] revealed = devModel.getRevealedSet(i);
			for(int j = 0; j < revealed.length; j++) {
				representation[idx] = revealed[j];
				idx++;
			}
		}
		return representation;
	}
	
	
	/**
	 * The possibility axioms
	 * @param a
	 * @param s
	 * @return
	 */
	private boolean poss(Action a, Situation s){
		switch (a.type) {
		case Action.GAIN:
		case Action.GAINMONO:
			//it is always possible to gain more resources
			return true;
		case Action.LOSEMONO:
			if(a.resSets.size() == 1) {
				ResourceSet set = a.resSets.keySet().iterator().next();
				for(int i = 0; i < ResourceSet.NRESOURCES; i++){
					if(set.getAmount(i) == 1 && s.resSet.getAmount(i) == set.getTotal())
						return true;
				}
			}
			return false;
		case Action.LOSE:
			if(a.resSets.size() == 1) {
				ResourceSet set = a.resSets.keySet().iterator().next();
				if(set.isKnown() && s.resSet.contains(set))
					return true;
				else if((!set.isKnown()) && s.resSet.getTotal() >= set.getTotal())
					return true;
			}
			return false;
		default:
			System.err.println("Unknown action type encountered in possibility axiom");
			return true;
		}
		
	}
	
	/**
	 * The effect axioms
	 * @param a 
	 * @param s
	 * @return a list of new situation(s)
	 */
	private HashMap<ResourceSet,Double> effect(Action a, Situation s){
		//a list because the partially observable actions generate multiple situations
		HashMap<ResourceSet, Double> ret = new HashMap<ResourceSet, Double>();
		ResourceSet rs;//we are creating new set objects so we won't modify the existing situation;
		switch (a.type) {
		case Action.GAIN:
		case Action.GAINMONO:
			if(a.resSets.size() == 1){
				rs = new ResourceSet();
				rs.add(s.resSet);
				rs.add(a.resSets.keySet().iterator().next());
				ret.put(rs,s.probability);
				return ret;
			}else{
				//robber case, when gaining one rss, but there are multiple possibilities
				//double logProb = Math.log(s.probability);
				for(ResourceSet set : a.resSets.keySet()) {
	            	rs = new ResourceSet();
	            	rs.add(s.resSet);
	            	rs.add(set);
	            	//ret.put(rs, Math.exp(logProb + Math.log(a.resSets.get(set).doubleValue()))); //TODO: why???
	            	ret.put(rs, s.probability * a.resSets.get(set).doubleValue());
				}
				return ret;
			}
		case Action.LOSE:
			if(a.resSets.size() == 1) {
				ResourceSet actSet = a.resSets.keySet().iterator().next();
				if(actSet.isKnown()){
					rs = new ResourceSet();
					rs.add(s.resSet);
					rs.subtract(actSet);
					ret.put(rs,s.probability);
					return ret;
				}else{
					//the discards or victim cases
					List<ResourceSet> outcomes = s.resSet.getSubsets(s.resSet.getTotal() - actSet.getTotal(), true);
					//double prob = Math.exp(Math.log(s.probability) - Math.log(outcomes.size())); //TODO: why???
					double prob = s.probability/outcomes.size();
					for(ResourceSet set : outcomes) {
						if(ret.containsKey(set))
							ret.put(set, ret.get(set).doubleValue() + prob);
						else
							ret.put(set, prob);
					}
					return ret;
				}
			}
		case Action.LOSEMONO:
			if(a.resSets.size() == 1) {
				ResourceSet actSet = a.resSets.keySet().iterator().next();
				rs = new ResourceSet();
				rs.add(s.resSet);
				for(int i = 0; i < ResourceSet.NRESOURCES; i++){
					if(actSet.getAmount(i) == 1) {
						rs.subtract(actSet.getTotal(),i);
						break;
					}
				}
				ret.put(rs,s.probability);
				return ret;
			}
		default:
			System.err.println("Unknown action type encountered in effect axiom");
			return ret;
		}
	}
	
	/**
	 * Update our beliefs based on the observation (or action if fully observable)
	 * @param set the modification to the player's resource set
	 * @param pn the player executing the action
	 * @param at the action type observed
	 * @param fromPn (if its a gain from robbery), which player has the resource been stolen from
	 */
	public void updateResourceBelief(ResourceSet set, int pn, int at, int... fromPn){
		try {
			Action a; 
			if(fromPn.length > 0 && at == Action.GAIN) //robber case
				a = new Action(at, rssModel.getPlayerHandModel(fromPn[0]).getPossibleResourceTypes());
			else  //all other cases
				a = new Action(at, set);			
			HashMap<ResourceSet,Double> newPossHands = new HashMap<ResourceSet,Double>();
			Situation s;
			double totalProb = 0.0;
			
			//update each of this player's possible hand
			for(Entry<ResourceSet, Double> entry : rssModel.getPlayerHandModel(pn).possibleResSets.entrySet()){
				ResourceSet rssSet = new ResourceSet(entry.getKey().getResourceArrayClone());
				s = new Situation(entry.getValue().doubleValue(), rssSet);
				if(poss(a, s)) {
					HashMap<ResourceSet, Double> outcomes = effect(a, s);
					for(ResourceSet rs : outcomes.keySet()) {
						double val = outcomes.get(rs);
						totalProb += val;
						if(newPossHands.containsKey(rs))
							newPossHands.put(rs,newPossHands.get(rs).doubleValue() + val);
						else
							newPossHands.put(rs,val);
					}
				}
			}
			// normalise if any hand was dropped due to the possibility axiom or if needed due to some precision loss
			if(!doubleEquals(1.0, totalProb)) {
				for(ResourceSet rs : newPossHands.keySet()) {
					newPossHands.put(rs, newPossHands.get(rs).doubleValue()/totalProb);
				}
			}
			rssModel.getPlayerHandModel(pn).update(newPossHands,a);
			
		} catch (Exception e) {
			System.err.println("Exception when updating belief model");
			e.printStackTrace();
		} finally {
		}
	}
	
	/**
	 * A quick method to output errors before these throw exceptions
	 * to be removed after debugging
	 */
	public void modelChecking(){
		for(PlayerResourceModel phm : rssModel.getPlayerHandModel()){
			if(phm.possibleResSets.size() == 0)
				System.err.println("Smth went wrong we have 0 models in the world");
			ResourceSet max = new ResourceSet();
			int[] min = new int[5];
			Arrays.fill(min, Integer.MAX_VALUE);
			for(ResourceSet rs: phm.possibleResSets.keySet()){
				if(phm.getTotalResources() != rs.getTotal())
					System.err.println("Total doesn't match the resource set");
				for (int i = 0; i < ResourceSet.NRESOURCES; i++){
					if(phm.rssAbs[PlayerResourceModel.MIN + i] > rs.getAmount(i))
						System.err.println("Resource set outside of min bound");
					if(phm.rssAbs[PlayerResourceModel.MAX + i] < rs.getAmount(i))
						System.err.println("Resource set outside of max bound");
				}
				for(int i = 0; i < ResourceSet.NRESOURCES; i++){
					if(max.getAmount(i) < rs.getAmount(i))
						max.setAmount(rs.getAmount(i), i);
					if(min[i] > rs.getAmount(i))
						min[i] = rs.getAmount(i);
				}
			}
		}
		double[] chances = devCardModel.getCurrentChances();
		for(double d : chances)
			if(d > 1 || d < 0)
				System.err.println("Error in dev card model: " + d);
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder("Belief model: \n");
		sb.append("Dev card model: \n");
		sb.append(devCardModel.toString());
		sb.append("\n\n");
		sb.append("Resource model: \n");
		//for each player output the possibilities and the results from the methods that will aid later on;
		int n = 0;
		for(PlayerResourceModel phm :rssModel.getPlayerHandModel()){
			sb.append("Player " + n + ": \n");
			sb.append("Abstract representation: " + Arrays.toString(phm.rssAbs) + "\n");
			ResourceSet rs = getHandIfOnlyOnePossible(n);
			if(rs!=null)
				sb.append("Exact hand: " + rs.toShortString() + "; \n");
			sb.append("Possible hands and probabilities:  \n");
			for(ResourceSet s : phm.possibleResSets.keySet()){
				sb.append(s.toShortString() + " prob: " + phm.possibleResSets.get(s).doubleValue());
			}
			n++;
			sb.append("\n");
		}
		return sb.toString();
	}
	
	private boolean doubleEquals(double a, double b) {
		double eps = 1e-6;
		if (eps < Math.abs(a - b))
			return false;
		return true;
	}
	
	/**
	 * @return a deep copy of the object
	 */
	public CatanFactoredBelief copy(){
		return new CatanFactoredBelief(this);
	}
	
	public void destroy(){
		rssModel.destroy();
		rssModel = null;
		devCardModel = null;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof CatanFactoredBelief) {
			if (devCardModel.equals(((CatanFactoredBelief)obj).devCardModel)) {
				if (rssModel.equals(((CatanFactoredBelief)obj).rssModel)) {
					return true;
				}
			}
		}
		return false;
	}

}
