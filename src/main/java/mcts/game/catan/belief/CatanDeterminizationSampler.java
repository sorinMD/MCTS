package mcts.game.catan.belief;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

import mcts.game.DeterminizationSampler;
import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.game.catan.Catan;
import mcts.game.catan.CatanConfig;
import mcts.game.catan.CatanWithBelief;
import mcts.game.catan.GameStateConstants;
import mcts.game.catan.ResourceSet;
import mcts.utils.GameSample;

/**
 * Simple sampler that can return one or more fully-observable states given the current belief and game state.
 * 
 * @author sorinMD
 *
 */
public class CatanDeterminizationSampler implements GameStateConstants,DeterminizationSampler{

	/**
	 * Samples one observable state from the current belief.
	 * @param factory the game factory that contains the current belief and the game configuration.
	 * @param the current game description to provide access to the state array
	 * @return a sampled game and the attached probability
	 */
	public GameSample sampleObservableState(Game currentGame, GameFactory factory) {
		//sample resource hands	
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		CatanFactoredBelief belief = ((CatanWithBelief)currentGame).getBelief();
		ResourcesModel rssModel = belief.getResourceModel();
		ResourceSet[] playerHands = new ResourceSet[NPLAYERS];
		double[] prob = new double[NPLAYERS];
		Arrays.fill(prob, 0.0);
		for(int pl = 0; pl < NPLAYERS; pl++) {
			PlayerResourceModel phm = rssModel.getPlayerHandModel(pl);
			if(phm.isFullyObservable()) {
				playerHands[pl] = phm.getHand();
				continue;
			}
			double total = 0.0;
			for(Entry<ResourceSet, Double> entry : phm.possibleResSets.entrySet() ) {
				total+= entry.getValue().doubleValue();
			}
			double choice = rnd.nextDouble(total);
			for(Entry<ResourceSet, Double> entry : phm.possibleResSets.entrySet() ) {
				choice -= entry.getValue().doubleValue();
				if(choice <= 0.0d) {
					playerHands[pl] = entry.getKey();
					prob[pl] += Math.log(entry.getValue()); 
					break;
				}
			}
		}
		
		//sample development cards by creating a random order of the remaining ones and "revealing" them
		DevCardModel devModel = belief.getDevCardModel().copy();
		int totalUnk = devModel.getTotalUnknownCards();
		if(totalUnk > 0) {
			int totalNonVp = devModel.getTotalNonVPCards();
			ArrayList<Integer> deck = new ArrayList<>();
			//first reveal the nonvp cards so create the deck with the nonvp cards only
			for(int i = 0; i < N_DEVCARDTYPES; i++ ) {
				if(i == CARD_ONEPOINT)
					continue;
				for(int j = 0; j < devModel.getRemaining(i); j++){
					deck.add(i);
				}
			}
			if(totalNonVp > 0) {
				Collections.shuffle(deck);
				int idx = deck.size() - 1; //start from the back to avoid shifting the array
				for(int pl = 0; pl < NPLAYERS; pl++) {
					if(devModel.getNonVPCards(pl) > 0) {
						int nvpCards = devModel.getNonVPCards(pl);
						for(int i = 0; i < nvpCards; i++) {
							int type = deck.get(idx);
							prob[pl] += Math.log(((double)devModel.getRemaining(type))/(idx + 1));
							devModel.reveal(pl, 1, type);
							deck.remove(idx);
							idx--;							
						}
					}
				}
			}
			totalUnk -= totalNonVp;
			//now reveal the remaining if any
			if(totalUnk > 0) {
				for(int j = 0; j < devModel.getRemaining(CARD_ONEPOINT); j++){
					deck.add(CARD_ONEPOINT);
				}
				Collections.shuffle(deck);
				int idx = deck.size() - 1; //start from the back to avoid shifting the array
				for(int pl = 0; pl < NPLAYERS; pl++) {
					int cards = devModel.getTotalUnknownCards(pl);
					if(cards > 0) {
						for(int i = 0; i < cards; i++) {
							int type = deck.get(idx);
							prob[pl] += Math.log(((double)devModel.getRemaining(type))/(idx + 1));
							devModel.reveal(pl, 1, type);
							deck.remove(idx);
							idx--;
						}
					}
				}
			}
		}
		
		//finally create the game state using the sampled rss sets and the revealed dev cards;
		int[] state = currentGame.getState();
		int fsmlevel = state[OFS_FSMLEVEL];
		int fsmstate = state[OFS_FSMSTATE+fsmlevel];
		if(fsmstate == S_NEGOTIATIONS || fsmstate == S_PAYTAX)
			fsmlevel-=1;
		int cpn = state[OFS_FSMPLAYER + fsmlevel];
		for(int pl = 0; pl < CatanWithBelief.NPLAYERS; pl++) {
			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] = playerHands[pl].getAmount(RES_WOOD);
			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] = playerHands[pl].getAmount(RES_CLAY);
			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] = playerHands[pl].getAmount(RES_WHEAT);
			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] = playerHands[pl].getAmount(RES_SHEEP);
			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] = playerHands[pl].getAmount(RES_STONE);
			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES] = 0;
			//avoid doing the below if it is the player whose belief we are tracking because we already have access to their exact hand description
			if(pl == state[OFS_OUR_PLAYER])
				continue;
			//clear the array for dev cards to avoid duplicating anything
			for(int i = 0; i < N_DEVCARDTYPES; i++) {
				state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + i] = 0;
				state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + i] = 0;
			}
			
			int[] revealed = devModel.getRevealedSet(pl).clone();
			if(pl == cpn) {
				//if there are any new cards, we can sample at random from the set to reveal them first
				if(state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + N_DEVCARDTYPES] > 0) {
					ArrayList<Integer> hand = new ArrayList<>();
					int type = 0;
					for(int i : revealed) {
						for(int j = 0; j < i; j++)
							hand.add(new Integer(type));
						type++;
					}
					Collections.shuffle(hand);
					int idx = hand.size() - 1;
					for(int k = 0; k < state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + N_DEVCARDTYPES]; k++) {
						int card = hand.remove(idx);
						idx--;
						state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + card] +=1;
						revealed[card]-=1;
					}
				}
			}
			for(int i = 0; i < N_DEVCARDTYPES; i++)
				state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + i] = revealed[i];
			//Observable version of Catan always makes vp cards old cards, otherwise these won't be recognised until the end of the turn
			if(state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_ONEPOINT] > 0) {
				state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_ONEPOINT] += state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_ONEPOINT];
				state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_ONEPOINT] = 0;
			}
			
		}
		
		double finalProb = 0.0;
		for(int pl = 0; pl < NPLAYERS; pl++ ) {
			finalProb += prob[pl];
		}
		return new GameSample(new Catan(state, (CatanConfig)factory.getConfig()), Math.exp(finalProb));
	}

}
