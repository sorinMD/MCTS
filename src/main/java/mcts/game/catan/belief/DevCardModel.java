package mcts.game.catan.belief;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

import mcts.game.catan.GameStateConstants;

/**
 * Model for tracking the chances of each dev card type
 * @author sorinMD
 */
public class DevCardModel implements Serializable, GameStateConstants{
	/**
	 * Number of remaining (hidden) development cards, could be in the deck or in a player's hand
	 */
	private int totalCards;
	/** Cards that have not been played or revealed yet	 */
	private int[] remaining = new int[N_DEVCARDTYPES];
	/**	Models to keep track of specific information */
	private PlayerDevCardModel[] playerDevModels;
	
	/**
	 * Constructor.
	 * @param maxPlayers is it a 4 or a 6 player game?
	 */
	public DevCardModel(int maxPlayers){
		if(maxPlayers == 4){
			totalCards = 25;
			remaining[CARD_KNIGHT] = 14;
			remaining[CARD_ONEPOINT] = 5;
			remaining[CARD_MONOPOLY] = 2; 
			remaining[CARD_FREEROAD] =  2;
			remaining[CARD_FREERESOURCE] = 2;
		}else{ //six player game
			totalCards = 34;
			remaining[CARD_KNIGHT] = 20;
			remaining[CARD_ONEPOINT] = 5;
			remaining[CARD_MONOPOLY] = 3; 
			remaining[CARD_FREEROAD] =  3;
			remaining[CARD_FREERESOURCE] = 3;
		}
		playerDevModels = new PlayerDevCardModel[maxPlayers];
		for(int i = 0; i < maxPlayers; i++) {
			playerDevModels[i] = new PlayerDevCardModel();
		}
		
	}
	
	/**
	 * Copy constructor.
	 * @param old the model we are creating this object from
	 */
	public DevCardModel(DevCardModel old){
		this.totalCards = old.totalCards;
		this.remaining = old.remaining.clone();
		playerDevModels = new PlayerDevCardModel[old.playerDevModels.length];
		for(int i = 0; i < old.playerDevModels.length; i++) {
			playerDevModels[i] = old.playerDevModels[i].copy();
		}
	}

	/**
	 * In case a memory is absent when loading in JSettlers;
	 * TODO: should we do smth about the details of each player, nonVP, total etc??
	 * @param totalUnplayedCards the sum of the remaining cards in the deck and the unplayed cards in each of the player's hand
	 * @param playedSoldiers the sum of played knights from everyone's hand
	 * @param playedMon ?
	 * @param playedRB ?
	 * @param playedYOP ?
	 */
	public void reinitialise(int totalUnplayedCards, int playedSoldiers, int playedRBs, int playedYOPs, int playedMonos, int revealedVPs){
		totalCards = totalUnplayedCards;
		remaining[CARD_KNIGHT] -= playedSoldiers;
		remaining[CARD_MONOPOLY] -= playedMonos;
		remaining[CARD_FREEROAD] -= playedRBs;
		remaining[CARD_FREERESOURCE] -= playedYOPs;
		//human players may reveal their hand or if these are in our hand
		//(The game doesn't handle the revealing scenario yet and game rules specify that VP cards are only played at the end of the game)
		remaining[CARD_ONEPOINT] -= revealedVPs; 
	}
	
	/**
	 * What are the current chances of a hidden card being of a specific type given the cards that have been played until now.
	 * @return an array containing the chances on the position corresponding to the dev card type
	 */
	public double[] getCurrentChances(){
		double[] chances = new double[5];
		if(totalCards == 0)
			return chances;//avoid NaNs
		
		for(int i = 0; i < N_DEVCARDTYPES; i++) {
			chances[i] = ((double)remaining[i])/totalCards;
		}
		
		return chances;
	}
	
	/**
	 * What is the probability of drawing a specific card type given our knowledge of the remaining unknown cards and how many nonvp cards have been drawn.
	 */
	public double[] getCurrentDrawChances(){
		int totalPlayerNonVps = 0;
		for(int i = 0; i < playerDevModels.length; i ++) {
			totalPlayerNonVps += playerDevModels[i].getNonVP();
		}
		double[] chances = new double[5];
		if(totalCards == 0)
			return chances;//avoid NaNs
		if(totalPlayerNonVps == getTotalNonVpRemaining()) {
			chances[CARD_ONEPOINT] = 1.0;
		}else {
			if(totalPlayerNonVps != 0) {
				chances[CARD_ONEPOINT] = (double)remaining[CARD_ONEPOINT]/(totalCards - totalPlayerNonVps);
				//remaining probability is split over the other types depending on how likely these are
				int totalNVPUnk = remaining[CARD_KNIGHT] + remaining[CARD_FREEROAD] + remaining[CARD_FREERESOURCE] + remaining[CARD_MONOPOLY];
				double remProb = 1.0 - chances[CARD_ONEPOINT];
				chances[CARD_KNIGHT] = remProb * ((double)remaining[CARD_KNIGHT]/totalNVPUnk);
				chances[CARD_FREEROAD] = remProb * ((double)remaining[CARD_FREEROAD]/totalNVPUnk);
				chances[CARD_FREERESOURCE] = remProb * ((double)remaining[CARD_FREERESOURCE]/totalNVPUnk);
				chances[CARD_MONOPOLY] = remProb * ((double)remaining[CARD_MONOPOLY]/totalNVPUnk);
			}else {
				chances = getCurrentChances();
			}
		}
		
		return chances;
	}
	
	/**
	 * What are the current chances of a player having at least one nonVp development card of a specific type.
	 * @param type the type of the card
	 * @param pn the player number.
	 * @return a double value representing the probability
	 */
	public double computeProbOfNonVp(int type, int pn){
		//if we are certain the player has at least one of that type then we can safely return a 1.0
		if(playerDevModels[pn].getRevealed(type) >= 1)
			return 1.0;
		
		int totalUnkForPlayer = getTotalUnknownCards(pn);
		if(totalUnkForPlayer == 0)
			return 0.0;
		
		//prob of not being the type we are looking for over the nonvp cards for this player
		double notNVPProb = 1.0;
		//need to reason over the current nonVps of this player if there are any
		int totalNVPForPlayer = getNonVPCards(pn);
		if(totalNVPForPlayer > 0) {
			double total = getTotalNonVpRemaining();
			double remaining = getRemaining(type);
			for(int i = 0; i < totalNVPForPlayer; i++) {
				notNVPProb *= (total - remaining)/total;
				total --;
			}
		}
		
		int totalOthers = totalUnkForPlayer - totalNVPForPlayer;
		//prob of not being the type we are looking for over the remaining cards that can be of any type, while considering the oppNonvps
		double notOthersProb = 1.0;
		if(totalOthers > 0 && notNVPProb > 0.0) {
			int oppNonVp = 0;
			for(int i = 0; i < playerDevModels.length; i ++) {
				if(i == pn)
					continue;
				oppNonVp += playerDevModels[i].getNonVP();
			}
			
			//next if opponents have any nonvp we also need to take those into considerations (if there are any other possible non-vp types of course);
			double remaining = getRemaining(type);
			double total = getTotalNonVpRemaining() - totalNVPForPlayer;
			if(oppNonVp > 0) {
				notOthersProb = 0.0;
				for(int j = 0; j <= remaining; j++) {
					if(j > oppNonVp)
						break;
					double oppProb = 0.0;
					//compute the probability of every possible order
					ArrayList<int[]> possibilities = new ArrayList<>();
					getSubsets(possibilities, 0, 0, j, new int[oppNonVp]);
					for(int[] poss : possibilities) {
						total = getTotalNonVpRemaining() - totalNVPForPlayer;
						double remOfType = getRemaining(type);
						double handProb = 1.0;
						for(int k = 0; k < poss.length; k++) {
							if(poss[k]==1) {
								handProb *= remOfType/total;
								remOfType--;
								total--;
							}else {
								handProb *= (total - remOfType)/total;
								total--;
							}
						}
						oppProb += handProb;
					}
					
					double totalRem = getTotalRemainingCards() - totalNVPForPlayer - oppNonVp;
					double rem = getRemaining(type) - j;
					double ourProb = 1.0;
					if(rem != 0) {
						for(int i = 0; i < totalOthers; i++) {
							ourProb *= (totalRem - rem)/totalRem;
							totalRem --;
						}
					}//else we can't have it as it would be over max so prob of not having it is 1.0
					notOthersProb += oppProb * ourProb;
				}
				
			}else {
				double totalRem = getTotalRemainingCards() - totalNVPForPlayer;
 				for(int i = 0; i < totalOthers; i++) {
					notOthersProb *= (totalRem - remaining)/totalRem;
					totalRem --;
				}
			}
		}
		return 1 - notNVPProb*notOthersProb;
	}
	
	/**
	 * Computes the probability of this player holding n victory point cards, by computing every possible combination.
	 * @param pn the player number
	 * @param required how many vp cards this player must have
	 * @return
	 */
	public double computeProbOfVP(int pn, int required) {
		//do not take into consideration the cards we know are not vp cards when computing these probabilities
		int totalRemaining = getTotalRemainingCards();
		for(int i = 0; i < playerDevModels.length; i ++) {
			totalRemaining -= playerDevModels[i].getNonVP();
		}
		
		int unk = getTotalUnknownCards(pn) - getNonVPCards(pn);
		if(unk == 0)//if all the unknown cards for this player are nonvp cards then why are we here? just return 0.0
			return 0.0;
		
		//if only vp cards are left unaccounted for than we could safely say this person has the required amount
		if(totalRemaining == remaining[CARD_ONEPOINT])
			return 1.0;
		
		ArrayList<int[]> possibilities = new ArrayList<>();
		getSubsets(possibilities, 0, 0, required, new int[unk]);
		double prob = 0.0;
		for(int[] poss : possibilities) {
			int total = totalRemaining;
			double remOfType = getRemaining(CARD_ONEPOINT);
			double handProb = 1.0;
			for(int k = 0; k < poss.length; k++) {
				if(poss[k]==1) {
					handProb *= remOfType/total;
					remOfType--;
					total--;
				}else {
					handProb *= (total - remOfType)/total;
					total--;
				}
			}
			prob += handProb;
		}
		
		return prob;
	}
	
	/**
	 * Return the number of remaining (not played or revealed) for each type
	 * @param type the type of development card as in {@link GameStateConstants}
	 * @return
	 */
	public int getRemaining(int type){
		return remaining[type];
	}
	
	
	/**
	 * A card has been played or bought by this player so we can update the chances for the remaining hidden ones
	 * @param cardType the type of card observed or played
	 */
	private void updateNumbers(int cardType){
		remaining[cardType]--;
		totalCards--;
	}
	
	/**
	 * Update the model after a specific card was drawn
	 * @param pn the player that drew the card
	 * @param type the type of card drew (if our player) or unknown = {@link GameStateConstants#N_DEVCARDTYPES} (if opponent drew the card)
	 */
	protected void updateDraw(int pn, int type) {
		playerDevModels[pn].updateDraw(type);
		if(type < N_DEVCARDTYPES)
			updateNumbers(type);
	}
	
	/**
	 * Update the model after a play of a specific card was observed
	 * @param pn the player that played the card
	 * @param type the type of card that was played
	 */
	protected void updatePlay(int pn, int type) {
		//if we didn't know about this card then update numbers
		if(playerDevModels[pn].getRevealed(type) == 0) 
			updateNumbers(type);
		playerDevModels[pn].updatePlay(type);
	}
	
	/**
	 * Note: this should only be called for opponents!!!
	 * @param pn the player number
	 * @param score number of current vp including the already revealed cards!!!
	 */
	protected void updateNonVP(int pn, int score) {
		PlayerDevCardModel model = playerDevModels[pn];
		//what we don't know and excluding what we already know to be a non-vp from the unknowns and adding the revealedVP since these are only tracked here
		int cards = getTotalUnknownCards(pn) - model.getNonVP();
		int amt = score + cards - 9;
		if(amt > 0) {
			model.addNonVp(amt);
		}
	}
	
	public void addNonVP(int pn, int amt) {
		PlayerDevCardModel model = playerDevModels[pn];
		model.addNonVp(amt);
	}
	
	/**
	 * Performs a revision over all possible hands and reveals any cards that can be revealed.
	 */
	protected void revise() {
		boolean[] flags = new boolean[4];
		
		//first revision: if a single type of nonvp left
		int totalNVRemaining = getTotalNonVpRemaining();
		int remainingType = -1;
		if(totalNVRemaining > 0) {
			if(totalNVRemaining == remaining[CARD_MONOPOLY])
				remainingType = CARD_MONOPOLY;
			else if(totalNVRemaining == remaining[CARD_FREEROAD])
				remainingType = CARD_FREEROAD;
			else if(totalNVRemaining == remaining[CARD_KNIGHT])
				remainingType = CARD_KNIGHT;
			else if(totalNVRemaining == remaining[CARD_FREERESOURCE]) 
				remainingType = CARD_FREERESOURCE;
			if(remainingType > -1) {
				flags[0] = true;
//				System.out.println("#Revealing a single type of non-vp if we know who has them of type " + remainingType);
				for(PlayerDevCardModel plm : playerDevModels) {
					int nonvp = plm.getNonVP();
					plm.reveal(remainingType, nonvp);
					totalCards -= nonvp;
					if(remainingType == CARD_MONOPOLY)
						remaining[CARD_MONOPOLY] -= nonvp;
					else if(remainingType == CARD_FREEROAD)
						remaining[CARD_FREEROAD] -= nonvp;
					else if(remainingType == CARD_FREERESOURCE)
						remaining[CARD_FREERESOURCE] -= nonvp;
					else if(remainingType == CARD_KNIGHT)
						remaining[CARD_KNIGHT] -= nonvp;
				}
			}
		}
		//second revision: one player has all the remaining unknown cards ... very unlikely but worth checking to avoid computing prob later
		for(int i = 0; i < playerDevModels.length ; i++) {
			if(getTotalUnknownCards(i) == getTotalRemainingCards()) {
//				System.out.println("#Revealing all cards for one player");
				flags[1] = true;
				PlayerDevCardModel plm = playerDevModels[i];
				for(int j = 0; j < N_DEVCARDTYPES; j++)
					plm.reveal(j, remaining[j]);
				totalCards = 0;
				Arrays.fill(remaining, 0);
				return;
			}
		}
		
		//third revision: one player has all the nonvps
		totalNVRemaining = getTotalNonVpRemaining();
		if(totalNVRemaining > 0) {
			for(PlayerDevCardModel plm : playerDevModels) {
				if(plm.getNonVP() == totalNVRemaining) {
//					System.out.println("#Revealing all non-vp cards for player " + pn);
					flags[2] = true;
					for(int j = 0; j < N_DEVCARDTYPES; j++) {
						if(j == CARD_ONEPOINT)
							continue;
						if(remaining[j] > 0) {
							plm.reveal(j, remaining[j]);
							totalCards -= remaining[j];
							remaining[j] = 0;
						}
					}
				}
			}
		}
		
		//fourth revision: check if we can reveal any vp cards
		totalNVRemaining = getTotalNonVpRemaining();
		int nonvps = 0;
		for(PlayerDevCardModel plm : playerDevModels) {
			nonvps += plm.getNonVP();
		}
		int unkn = totalNVRemaining - nonvps; //this means we have accounted for all the nonvp cards
		if(unkn == 0) {
			flags[3] = true;
//			System.out.println("#Revealing all vp cards in hands");
			for(PlayerDevCardModel plm : playerDevModels) {
				int alreadyRevealed = plm.getTotalRevealed();
				int nonvp = plm.getNonVP();
				int rev = plm.getTotal() - alreadyRevealed - nonvp;
				if(rev > 0) {
					plm.reveal(CARD_ONEPOINT, rev);
					totalCards -= rev;
					remaining[CARD_ONEPOINT] -= rev;
				}
			}
		}
	}
	
	public void reveal(int pn, int amt, int type) {
		playerDevModels[pn].reveal(type, amt);
		totalCards -= amt;
		remaining[type] -=amt;
	}
	
	public int getRevealedVP(int pn) {
		return playerDevModels[pn].getRevealed(CARD_ONEPOINT); 
	}
	
	public int getTotalRemainingCards() {
		return totalCards;
	}
	
	/**
	 * @return the remaining (unknown cards) that are not victory point cards. These can be in a player's hand or in the deck
	 */
	public int getTotalNonVpRemaining() {
		return remaining[CARD_KNIGHT] + remaining[CARD_MONOPOLY] + remaining[CARD_FREEROAD] + remaining[CARD_FREERESOURCE];
	}
	
	/**
	 * @param pn the player number
	 * @return the total unknown cards that have been already drawn for player with player number pn
	 */
	public int getTotalUnknownCards(int pn) {
		return playerDevModels[pn].getTotal() - playerDevModels[pn].getTotalRevealed();
	}
	
	/**
	 * Total cards (both known and unknown) for a player
	 * @param pn the player number
	 * @return
	 */
	public int getTotalCards(int pn) {
		return playerDevModels[pn].getTotal();
	}
	
	/**
	 * @return the total unknown cards that have been already drawn
	 */
	public int getTotalUnknownCards() {
		int ret = 0;
		for(PlayerDevCardModel plm : playerDevModels) {
			ret += plm.getTotal() - plm.getTotalRevealed();
		}
		return ret;
	}
	
	/**
	 * @param pn the player number
	 * @return total unknown cards for player with pn that we know are not victory point cards
	 */
	public int getNonVPCards(int pn) {
		return playerDevModels[pn].getNonVP();
	}
	
	/**
	 * @return total unknown cards for all players that we know are not victory point cards
	 */
	public int getTotalNonVPCards() {
		int ret = 0;
		for(PlayerDevCardModel plm : playerDevModels)
			ret+=plm.getNonVP();
		return ret;
	}
	
	/**
	 * @param pn the player number
	 * @return an array that contains all the revealed development cards for player pn
	 */
	public int[] getRevealedSet(int pn) {
		return playerDevModels[pn].getRevealedSet();
	}
	
	
	/**
	 * @return a deep copy of the object
	 */
	protected DevCardModel copy(){
		return new DevCardModel(this);
	}
	
	/**
	 * Utility method that generates arrays of possible hands as a vector of binary
	 * features e.g. [1,0,0,1], where a 1 means that this card is the type we are
	 * looking for and 0 means it is a different card. The array must have n 1s.
	 * @param solutions the list of solutions
	 * @param current the current possible solution
	 * @param idx current index in current
	 * @param count number of 1s in current
	 * @param target the target number of 1s
	 */
	private void getSubsets(ArrayList<int[]> solutions, int idx, int count, int target, int[] current) {
		if(count == target) {
			solutions.add(current.clone());
			return;
		}
		if(idx == current.length)//reached the end
			return;
		current[idx] = 1;
		getSubsets(solutions, idx + 1, count + 1, target, current);
		current[idx] = 0;
		getSubsets(solutions, idx + 1, count, target, current);
		
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Development card Model");
		sb.append(Arrays.toString(getCurrentChances()));
		sb.append("\nTotal: " + totalCards);
		sb.append("; cards: " + Arrays.toString(remaining));
		sb.append("\n");
		for(int i = 0; i<playerDevModels.length; i++) {
			sb.append("Model for player " + i + " " + playerDevModels[i].toString());
			sb.append("\n");
		}
		
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof DevCardModel) {
			DevCardModel objRef = (DevCardModel) obj;
			if(totalCards == objRef.totalCards && Arrays.equals(remaining, objRef.remaining)) {
				if(playerDevModels.length == objRef.playerDevModels.length) {
					boolean equal = true;
					for(int i = 0; i < playerDevModels.length; i++) {
						if(!playerDevModels[i].equals(objRef.playerDevModels[i]))
							equal = false;
					}
					if(equal)
						return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * Quick tests :p. It is convenient to write here so we can access the private fields and quickly create the required scenario
	 * @param args
	 */
	public static void main(String[] args) {
		//TODO: move these in a test class and use reflection to modify the private methods?
		/*
		 * Expected output:
		 * Draw chances at start: [0.56, 0.2, 0.08, 0.08, 0.08]
		 * Draw chances at end: [0.0, 0.0, 0.0, 0.0, 0.0]
		 * Draw chances in the middle: [0.4, 0.3, 0.1, 0.2, 0.0]
		 * Draw chances when one nvp is accounted for: [0.22222222222222224, 0.6666666666666666, 0.0, 0.11111111111111112, 0.0]
		 * Draw chances when all nvp are accounted for: [0.0, 1.0, 0.0, 0.0, 0.0]
		 * Prob of VP cards when all unk for this player are nvp : 0.0
		 * Prob of VP cards when all nvps are accounted for : 1.0
		 * Prob of VP cards when all unk for this player must be vp (2 out of 2) : 0.1
		 * Prob of VP cards when 1 unk for this player must be vp (1 out of 3) : 0.6
		 * Prob of VP cards when 1 unk for this player must be vp (2 out of 3) : 0.3
		 * Prob of NVP when the player has no unknown cards 0.0
		 * Prob of NVP when the player has 2 unk of any type: 0.6666666666666667
		 * Prob of NVP when the player has 2 unk and one is known to be a nvp: 0.7619047619047619
		 * Prob of NVP when the player has 2 unk and both are known to be nvps: 0.8571428571428572
		 * Prob of NVP when the player has the card revealed 1.0
		 * Prob of NVP when the player has 2 unk of any type and the opponents have 2 nvps: 0.6122448979591837
		 * Prob of NVP when the player has 2 unk out of which 1 is a known nvp and the opponents have 2 nvps: 0.7346938775510203
		 * Edge cases where the number of remaining cards of the type is insufficient
		 * Prob of NVP when the player has 2 unk of any type and the opponents have 2 nvps: 0.4444444444444444
		 * Prob of NVP when the player has 2 unk out of which 1 is a known nvp and the opponents have 2 nvps: 0.6666666666666667
		 * Very edge case, where there are no cards of that type:
		 * Prob of NVP when the player has 2 unk of any type and the opponents have 2 nvps: 0.0
		 * Prob of NVP when the player has 2 unk out of which 1 is a known nvp and the opponents have 2 nvps: 0.0
		 * 
		 */
		DevCardModel model = new DevCardModel(4);
		System.out.println("Draw chances at start: " + Arrays.toString(model.getCurrentDrawChances()));
		model.remaining = new int[N_DEVCARDTYPES];
		model.totalCards = 0;
		System.out.println("Draw chances at end: " + Arrays.toString(model.getCurrentDrawChances()));
		int[] rem = {4,3,1,2,0};
		model.remaining = rem;
		model.totalCards = 10;
		System.out.println("Draw chances in the middle: " + Arrays.toString(model.getCurrentDrawChances()));
		int[] rem2 = {2,4,0,1,0};
		model.remaining = rem2;
		model.totalCards = 7;
		PlayerDevCardModel plm = model.playerDevModels[0];
		plm.updateDraw(N_DEVCARDTYPES);
		plm.addNonVp(1);
		System.out.println("Draw chances when one nvp is accounted for: " + Arrays.toString(model.getCurrentDrawChances()));
		plm.updateDraw(N_DEVCARDTYPES);
		plm.updateDraw(N_DEVCARDTYPES);
		plm.addNonVp(2);
		System.out.println("Draw chances when all nvp are accounted for: " + Arrays.toString(model.getCurrentDrawChances()));
		///test prob of having vp cards now///
		int[] rem3 = {2,0,0,1,0};
		model.remaining = rem3;
		model.totalCards = 3;
		System.out.println("Prob of VP cards when all unk for this player are nvp : " + model.computeProbOfVP(0, 1));
		int[] rem4 = {2,2,0,1,0};
		model.remaining = rem4;
		model.totalCards = 5;
		plm.updateDraw(N_DEVCARDTYPES);
		System.out.println("Prob of VP cards when all nvps are accounted for : " + model.computeProbOfVP(0, 1));
		int[] rem5 = {5,2,0,1,0};
		model.remaining = rem5;
		model.totalCards = 8;
		plm.updateDraw(N_DEVCARDTYPES);
		System.out.println("Prob of VP cards when all unk for this player must be vp (2 out of 2) : " + model.computeProbOfVP(0, 2));
		plm.updateDraw(N_DEVCARDTYPES);
		System.out.println("Prob of VP cards when 1 unk for this player must be vp (1 out of 3) : " + model.computeProbOfVP(0, 1));
		System.out.println("Prob of VP cards when 1 unk for this player must be vp (2 out of 3) : " + model.computeProbOfVP(0, 2));
		///test the prob of having non-vp cards now///
		//reset just so we can keep these tests seperate
		model = new DevCardModel(4);
		plm = model.playerDevModels[0];
		System.out.println("Prob of NVP when the player has no unknown cards: " + model.computeProbOfNonVp(CARD_KNIGHT, 0));
		model.remaining = rem;
		model.totalCards = 10;
		plm.updateDraw(N_DEVCARDTYPES);
		plm.updateDraw(N_DEVCARDTYPES);
		System.out.println("Prob of NVP when the player has 2 unk of any type: " + model.computeProbOfNonVp(CARD_KNIGHT, 0));
		plm.addNonVp(1);
		System.out.println("Prob of NVP when the player has 2 unk and one is known to be a nvp: " + model.computeProbOfNonVp(CARD_KNIGHT, 0));
		plm.addNonVp(1);
		System.out.println("Prob of NVP when the player has 2 unk and both are known to be nvps: " + model.computeProbOfNonVp(CARD_KNIGHT, 0));
		plm.updateDraw(CARD_KNIGHT);
		System.out.println("Prob of NVP when the player has the card revealed: " + model.computeProbOfNonVp(CARD_KNIGHT, 0));
		//reset again to create the most difficult case
		model = new DevCardModel(4);
		plm = model.playerDevModels[0];
		model.remaining = rem;
		model.totalCards = 10;
		plm.updateDraw(N_DEVCARDTYPES);
		plm.updateDraw(N_DEVCARDTYPES);
		PlayerDevCardModel opp = model.playerDevModels[1];
		opp.updateDraw(N_DEVCARDTYPES);
		opp.updateDraw(N_DEVCARDTYPES);
		opp.addNonVp(2);
		System.out.println("Prob of NVP when the player has 2 unk of any type and the opponents have 2 nvps: " + model.computeProbOfNonVp(CARD_KNIGHT, 0));
		plm.addNonVp(1);
		System.out.println("Prob of NVP when the player has 2 unk out of which 1 is a known nvp and the opponents have 2 nvps: " + model.computeProbOfNonVp(CARD_KNIGHT, 0));
		///Some edge cases worth checking///
		System.out.println("Edge cases where the number of remaining cards of the type is insufficient ");
		//reset again
		model = new DevCardModel(4);
		plm = model.playerDevModels[0];
		model.remaining = rem4;
		model.totalCards = 5;
		plm.updateDraw(N_DEVCARDTYPES);
		plm.updateDraw(N_DEVCARDTYPES);
		opp = model.playerDevModels[1];
		opp.updateDraw(N_DEVCARDTYPES);
		opp.updateDraw(N_DEVCARDTYPES);
		opp.addNonVp(2);
		System.out.println("Prob of NVP when the player has 2 unk of any type and the opponents have 2 nvps: " + model.computeProbOfNonVp(CARD_KNIGHT, 0));
		plm.addNonVp(1);
		System.out.println("Prob of NVP when the player has 2 unk out of which 1 is a known nvp and the opponents have 2 nvps: " + model.computeProbOfNonVp(CARD_KNIGHT, 0));
		
		System.out.println("Very edge cases where there are no cards of that type: ");
		model = new DevCardModel(4);
		plm = model.playerDevModels[0];
		model.remaining = rem4;
		model.totalCards = 5;
		plm.updateDraw(N_DEVCARDTYPES);
		plm.updateDraw(N_DEVCARDTYPES);
		opp = model.playerDevModels[1];
		opp.updateDraw(N_DEVCARDTYPES);
		opp.updateDraw(N_DEVCARDTYPES);
		opp.addNonVp(2);
		System.out.println("Prob of NVP when the player has 2 unk of any type and the opponents have 2 nvps: " + model.computeProbOfNonVp(CARD_MONOPOLY, 0));
		plm.addNonVp(1);
		System.out.println("Prob of NVP when the player has 2 unk out of which 1 is a known nvp and the opponents have 2 nvps: " + model.computeProbOfNonVp(CARD_MONOPOLY, 0));
		
	}
	
}