package mcts.game.catan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

import mcts.game.Game;
import mcts.game.catan.belief.Action;
import mcts.game.catan.belief.DevCardModel;
import mcts.game.catan.belief.PlayerResourceModel;
import mcts.game.catan.typepdf.HumanActionTypePdf;
import mcts.game.catan.belief.CatanFactoredBelief;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;
import mcts.utils.Options;
import mcts.utils.Timer;
import mcts.tree.node.ChanceNode;

/**
 * Partially observable version of Settlers of Catan game model implementation.
 * 
 * Part of the state representation and logic courtesy of Pieter Spronck:
 * http://www.spronck.net/research.html
 * 
 * Several improvements and additions have been made:
 * <ul>
 * <li>Added planning for the discard action instead of random decision (if the possible set is not too large)
 * </li>
 * <li>Added trades (with options for simultaneous actions or negotiations,
 * including counter-offer or not)</li>
 * <li>Added option to sample over action types first and then descriptions
 * (i.e. locations, resources etc)</li>
 * <li>Added handling the chances of the development card deck, instead of
 * assuming a specific order</li>
 * <li>Fixed various minor bugs, to make sure the game rules are respected (e.g.
 * only a limited number of pieces can be built, players are chosen randomly to
 * start the game and the order affects the initial placement,road building
 * development card can be played even if a single road can be built etc )</li>
 * <li>Added code to play the game in the belief space of a player.</li>
 * </ul>
 * 
 * Note: It duplicates a lot of code from {@link Catan}, since we want to keep
 * that version a fast implementation of the observable game. This version can
 * run the fully-observable version of the game also, but it is pretty slow in
 * comparison due to all the extra checks and the use of the {@link Options}
 * datastructure for storing the actions and their probability of being legal.
 * 
 * @author sorinMD
 *
 */
public class CatanWithBelief extends Catan implements GameStateConstants, Game {

	CatanFactoredBelief belief;
	//the below is used for gathering statistics when the code in MCTSBrain is uncommented
	public static long chanceCount = 0; 
	public static long chanceCount1 = 0; 
	/**
	 * The development cards. This sequence is used when belief is null.
	 */
	private int[] cardSequence = {
			// 14
			CARD_KNIGHT, CARD_KNIGHT, CARD_KNIGHT, CARD_KNIGHT, CARD_KNIGHT, CARD_KNIGHT, CARD_KNIGHT, CARD_KNIGHT,
			CARD_KNIGHT, CARD_KNIGHT, CARD_KNIGHT, CARD_KNIGHT, CARD_KNIGHT, CARD_KNIGHT,
			// 5
			CARD_ONEPOINT, CARD_ONEPOINT, CARD_ONEPOINT, CARD_ONEPOINT, CARD_ONEPOINT,
			// 2, 2, 2
			CARD_MONOPOLY, CARD_MONOPOLY, CARD_FREERESOURCE, CARD_FREERESOURCE, CARD_FREEROAD, CARD_FREEROAD };
	
	/**
	 * A new game object with the provided state, but assumes an existing board.
	 * To initialise the board call {@link #initBoard()}
	 * 
	 * @param state
	 *            the game state
	 */
	public CatanWithBelief(int[] state, CatanConfig config, CatanFactoredBelief belief) {
		super();
		this.config = config;
		this.state = state;
		this.belief = belief;
		if(belief == null) {
			/*
			 * NOTE: the following code is dealing with the imperfect knowledge of
			 * the order of the deck of remaining development cards. Could
			 * potentially shuffle the remaining cards only if the next action is
			 * dealing a card or if a new roll-out is started, but no significant
			 * performance gain was observed to make up for the extra complexity
			 */
			if(state[OFS_NCARDSGONE] > 0 && state[OFS_NCARDSGONE] < NCARDS){
		        int numvp, numkn, numdisc, nummono, numrb;//the drawn dev cards
		        numvp = numdisc = numkn = nummono = numrb = 0;
		        for (int pl=0; pl<NPLAYERS; pl++){
		        	numkn += state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_KNIGHT] + state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] + 
		        			state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_KNIGHT];
		        	numvp += state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_ONEPOINT] + state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_ONEPOINT] + 
		        			state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_ONEPOINT];
		        	numdisc += state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_FREERESOURCE] + state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE] + 
		        			state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_FREERESOURCE];
		        	nummono += state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_MONOPOLY] + state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY] + 
		        			state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_MONOPOLY];
		        	numrb += state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + CARD_FREEROAD] + state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD] + 
		        			state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_FREEROAD];
		        }
		        
		        int nDrawnCards = numvp + numkn + numdisc + nummono + numrb;
				state[OFS_DEVCARDS_LEFT + CARD_KNIGHT] = 14 - numkn;
				state[OFS_DEVCARDS_LEFT + CARD_ONEPOINT] = 5 - numvp;
				state[OFS_DEVCARDS_LEFT + CARD_FREEROAD] = 2 - numrb;
				state[OFS_DEVCARDS_LEFT + CARD_FREERESOURCE] = 2 - numdisc;
				state[OFS_DEVCARDS_LEFT + CARD_MONOPOLY] = 2 - nummono;
		        int[] remainingCards = new int[NCARDS - nDrawnCards];
		        int i;
		        int idx = 0;
		        for(i = 0; i < (14 - numkn); i++){
		        	remainingCards[idx] = CARD_KNIGHT;
		        	idx++;
		        }
		        for(i = 0; i < (5 - numvp); i++){
		        	remainingCards[idx] = CARD_ONEPOINT;
		        	idx++;
		        }
		        for(i = 0; i < (2 - numdisc); i++){
		        	remainingCards[idx] = CARD_FREERESOURCE;
		        	idx++;
		        }
		        for(i = 0; i < (2 - nummono); i++){
		        	remainingCards[idx] = CARD_MONOPOLY;
		        	idx++;
		        }
		        for(i = 0; i < (2 - numrb); i++){
		        	remainingCards[idx] = CARD_FREEROAD;
		        	idx++;
		        }
		        
		        //shuffle the remaining cards and add them to the sequence in the order that they will be dealt in
		        shuffleArray(remainingCards);
		        idx = state[OFS_NCARDSGONE];
		        for(i = 0; i < remainingCards.length; i++){
		        	cardSequence[idx] = remainingCards[i];
		        	idx++;
		        }
		        
			}else if(state[OFS_NCARDSGONE] == 0){
				shuffleArray(cardSequence);
				state[OFS_DEVCARDS_LEFT + CARD_KNIGHT] = 14;
				state[OFS_DEVCARDS_LEFT + CARD_ONEPOINT] = 5;
				state[OFS_DEVCARDS_LEFT + CARD_FREEROAD] = 2;
				state[OFS_DEVCARDS_LEFT + CARD_FREERESOURCE] = 2;
				state[OFS_DEVCARDS_LEFT + CARD_MONOPOLY] = 2;
			}
		}
		recalcScores();
	}

	/**
	 * A new game, but assumes an existing board. To initialise the board call {@link #initBoard()}
	 * @param belief the belief as a new object used only as a flag in this initial constructor
	 */
	public CatanWithBelief(CatanConfig config, CatanFactoredBelief belief) {
		super();
		this.config = config;
		this.belief = belief;
		state = new int[STATESIZE];
		if(belief == null) {
			shuffleArray(cardSequence);
			state[OFS_DEVCARDS_LEFT + CARD_KNIGHT] = 14;
			state[OFS_DEVCARDS_LEFT + CARD_ONEPOINT] = 5;
			state[OFS_DEVCARDS_LEFT + CARD_FREEROAD] = 2;
			state[OFS_DEVCARDS_LEFT + CARD_FREERESOURCE] = 2;
			state[OFS_DEVCARDS_LEFT + CARD_MONOPOLY] = 2;
		}else {
			state[OFS_OUR_PLAYER] = ThreadLocalRandom.current().nextInt(NPLAYERS);
		}
        state[OFS_LARGESTARMY_AT] = -1;
        state[OFS_LONGESTROAD_AT] = -1;
        state[OFS_DISCARD_FIRST_PL] = -1;
		if(board.init){
			stateTransition(null);
		}else
			throw new RuntimeException("Cannot create game; the board was not initialised");
	}
	
	/**
	 * Generates a new board with all the info that is constant throughout a game.
	 */
	public static void initBoard(){
		board.InitBoard();
	}
	
	/**
	 * @return a clone of the state to avoid any external modifications to it
	 */
	public int[] getState() {
		return state.clone();
	}

	public int getWinner() {
		//only current player can win
		int pl = getCurrentPlayer();
		if (state[OFS_PLAYERDATA[pl] + OFS_SCORE] >= 10)
				return pl;
		return -1;
	}
	
	/**
	 * Considers the current public vp and the cards in a player's hand to decide whether it is possible for the current player to win the game.
	 * @param belief
	 * @return
	 */
	private boolean winnerPossible(CatanFactoredBelief belief) {
		int pl = getCurrentPlayer();
		if(pl == state[OFS_OUR_PLAYER] || belief == null || config.OBSERVABLE_VPS)
			return false; //we only use the visible score to decide on the winner in these cases
		DevCardModel devModel = belief.getDevCardModel();
		int score = state[OFS_PLAYERDATA[pl] + OFS_SCORE];//the score was just updated
		int cards = devModel.getTotalUnknownCards(pl) - devModel.getNonVPCards(pl);
		if(cards >= 10 - score && devModel.getRemaining(CARD_ONEPOINT) >= 10 - score) 
			return true;
		return false;
	}

	public boolean isTerminal() {
		int fsmlevel = state[OFS_FSMLEVEL];
		return state[OFS_FSMSTATE + fsmlevel] == S_FINISHED;
	}

	public int getCurrentPlayer() {
		int fsmlevel = state[OFS_FSMLEVEL];
		return state[OFS_FSMPLAYER + fsmlevel];
	}

	public void performAction(int[] a, boolean sample) {
        int fsmlevel    = state[OFS_FSMLEVEL];
        int fsmstate    = state[OFS_FSMSTATE+fsmlevel];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        int i, j, ind, val, ind2, k;
        
        switch (a[0]){
        case A_BUILDSETTLEMENT: 
			state[OFS_VERTICES + a[1]] = VERTEX_HASSETTLEMENT + pl;
			state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS]++;
			//keep track of the free initial settlements in order to be able to plan the free initial roads
			if(fsmstate == S_SETTLEMENT1 || fsmstate == S_SETTLEMENT2)
				state[OFS_LASTVERTEX] = a[1];
			boolean[] hasOpponentRoad = new boolean[NPLAYERS];
			for (j = 0; j < 6; j++) {
				ind = board.neighborVertexVertex[a[1]][j];
				if (ind != -1) {
					state[OFS_VERTICES + ind] = VERTEX_TOOCLOSE;
				}
				ind = board.neighborVertexEdge[a[1]][j];
				if ((ind != -1) && (state[OFS_EDGES + ind] != EDGE_EMPTY)) {
					hasOpponentRoad[state[OFS_EDGES + ind] - EDGE_OCCUPIED] = true;
				}
			}
			hasOpponentRoad[pl] = false;
			for (j = 0; j < 6; j++) {
				ind = board.neighborVertexHex[a[1]][j];
				if ((ind != -1) && (board.hextiles[ind].type == TYPE_PORT)) {
					val = board.hextiles[ind].subtype - PORT_SHEEP;
					k = j - 2;
					if (k < 0)
						k += 6;
					if (k == board.hextiles[ind].orientation)
						state[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + val] = 1;
					k = j - 3;
					if (k < 0)
						k += 6;
					if (k == board.hextiles[ind].orientation)
						state[OFS_PLAYERDATA[pl] + OFS_ACCESSTOPORT + val] = 1;
				}
			}
			for (int pl2 = 0; pl2 < NPLAYERS; pl2++) {
				if (hasOpponentRoad[pl2])
					recalcLongestRoad(state, pl2);
			}
			if (fsmstate == S_SETTLEMENT2) {
				int resource;
				for (j = 0; j < 6; j++) {
					ind = board.neighborVertexHex[a[1]][j];
					if (ind != -1) {
						resource = board.hextiles[ind].yields();
						if (resource != -1) {
							state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + resource]++;
							state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]++;
							if(belief != null) {
								ResourceSet set = new ResourceSet();
								set.add(1, resource);
								belief.updateResourceBelief(set, pl, Action.GAIN);
							}
						}
					}
				}
			} else if (fsmstate == S_NORMAL) {
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD]--;
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY]--;
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT]--;
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP]--;
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]-=4;
				if(belief != null) {
					if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] < 0)
						state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] = 0;
					if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] < 0)
						state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] = 0;
					if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] < 0)
						state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] = 0;
					if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] < 0)
						state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] = 0;
					ResourceSet set = new ResourceSet();
					set.add(1, RES_CLAY);
					set.add(1, RES_SHEEP);
					set.add(1, RES_WHEAT);
					set.add(1, RES_WOOD);
					belief.updateResourceBelief(set, pl, Action.LOSE);
				}
			}

			break;
		case A_BUILDCITY:
			state[OFS_VERTICES + a[1]] = VERTEX_HASCITY + pl;
			state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS]--;
			state[OFS_PLAYERDATA[pl] + OFS_NCITIES]++;
			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] -= 3;
			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] -= 2;
			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]-= 5;
			
			if(belief != null) {
				if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] = 0;
				if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] = 0;
				ResourceSet set = new ResourceSet();
				set.add(3, RES_STONE);
				set.add(2, RES_WHEAT);
				belief.updateResourceBelief(set, pl, Action.LOSE);
			}
			
			break;
		case A_BUILDROAD:
			state[OFS_LASTVERTEX] = 0;//clear the last free settlement location;
			state[OFS_EDGES + a[1]] = EDGE_OCCUPIED + pl;
			state[OFS_PLAYERDATA[pl] + OFS_NROADS]++;
			if (fsmstate == S_NORMAL) {
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD]--;
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY]--;
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]-= 2;
				
				if(belief != null) {
					if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] < 0)
						state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] = 0;
					if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] < 0)
						state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] = 0;
					
					ResourceSet set = new ResourceSet();
					set.add(1, RES_CLAY);
					set.add(1, RES_WOOD);
					belief.updateResourceBelief(set, pl, Action.LOSE);
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES] = belief.getResourceModel().getPlayerHandModel(pl).getTotalResources();
				}
				
			}
			recalcLongestRoad(state, pl);
			break;
        case A_THROWDICE:
        	state[OFS_NATURE_MOVE] = 1;//next move is non-deterministic
        	break;
        case A_CHOOSE_DICE:
			val = a[1];
			for (ind = 0; ind < N_HEXES; ind++) {
				if ((val == board.hextiles[ind].productionNumber) && (state[OFS_ROBBERPLACE] != ind)) {
					for (j = 0; j < 6; j++) {
						ind2 = board.neighborHexVertex[ind][j];
						if (ind2 != -1) {
							k = state[OFS_VERTICES + ind2];
							// production for settlement
							if ((k >= VERTEX_HASSETTLEMENT) && (k < VERTEX_HASSETTLEMENT + NPLAYERS)) {
								pl = k - VERTEX_HASSETTLEMENT;
								int rssType = board.hextiles[ind].yields();
								state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + rssType]++;
								state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]++;
								if(belief != null) {
									ResourceSet set = new ResourceSet();
									set.add(1, rssType);
									belief.updateResourceBelief(set, pl, Action.GAIN);
								}
							}
							// production for city
							if ((k >= VERTEX_HASCITY) && (k < VERTEX_HASCITY + NPLAYERS)) {
								pl = k - VERTEX_HASCITY;
								int rssType = board.hextiles[ind].yields();
								state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + rssType] += 2;
								state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]+=2;
								if(belief != null) {
									ResourceSet set = new ResourceSet();
									set.add(2, rssType);
									belief.updateResourceBelief(set, pl, Action.GAIN);
								}
							}
						}
					}
				}
			}
			state[OFS_DICE] = val;
			state[OFS_NATURE_MOVE] = 0;
			break;
		case A_ENDTURN:
			// new cards become old cards
			for (ind = 0; ind < NCARDTYPES; ind++) {
				state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + ind] += state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + ind];
				state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + ind] = 0;
			}
			state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + N_DEVCARDTYPES] += state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + N_DEVCARDTYPES];
			state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + N_DEVCARDTYPES] = 0;
			state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = 0;
			state[OFS_DICE] = 0;
			state[OFS_NUMBER_OF_OFFERS] = 0;
			break;
        case A_PORTTRADE:
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[2]] -= a[1];
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[4]] += a[3];
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]-= a[1];
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]+= a[3];
            if(belief != null) {
            	if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[2]] < 0)
            		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[2]] = 0;
				ResourceSet set = new ResourceSet();
				set.add(a[1], a[2]);
				belief.updateResourceBelief(set, pl, Action.LOSE);
				set = new ResourceSet();
				set.add(a[3], a[4]);
				belief.updateResourceBelief(set, pl, Action.GAIN);
            }
            
            break;
        case A_BUYCARD:
        	state[OFS_NATURE_MOVE] = 1;//dealing a card is a non-deterministic action that always follows this one
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT]--;
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP]--;                    
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE]--;
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]-=3;
            if(belief != null) {
				if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] = 0;
				if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] = 0;
				if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] = 0;
				ResourceSet set = new ResourceSet();
				set.add(1, RES_STONE);
				set.add(1, RES_SHEEP);
				set.add(1, RES_WHEAT);
				belief.updateResourceBelief(set, pl, Action.LOSE);
            }
        	break;
        case A_DEAL_DEVCARD:
        	if(belief != null) {
        		val = a[1];
        		if(val == CARD_ONEPOINT && config.OBSERVABLE_VPS) {
        			//do not replace with unknown
        		}else if(state[OFS_OUR_PLAYER] != pl && !config.OBSERVABLE_POM_EFFECTS) {
        			val = N_DEVCARDTYPES;
        		}
        		belief.updateDevCardModel(val, Action.GAIN, pl);
        		if(config.OBSERVABLE_VPS && val != CARD_ONEPOINT && state[OFS_OUR_PLAYER] != pl)
        			belief.getDevCardModel().addNonVP(pl, 1);
        			
        		if(belief.getTotalRemainingDevCards() > 0)
					belief.reviseDevCardModel();
        		if(state[OFS_OUR_PLAYER] == pl) {
        			if (val==CARD_ONEPOINT)
    	                state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + val]++;
    	            else
    	                state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + val]++;
        		}
        	}else {
        		val = a[1];
 	            if (val==CARD_ONEPOINT)
 	                state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + val]++;
 	            else
 	                state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + val]++;
 	            state[OFS_DEVCARDS_LEFT + val]--;
        	}
        	state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + N_DEVCARDTYPES]++;
            state[OFS_NCARDSGONE] ++;
            state[OFS_NATURE_MOVE] = 0;
            break;
            
		case A_PLAYCARD_KNIGHT:
			state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = 1;
			state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT]--;
			state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + N_DEVCARDTYPES]--;
			state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_KNIGHT]++;
			if(belief != null) {
				if(state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] = 0;
				belief.updateDevCardModel(CARD_KNIGHT, Action.LOSE, pl);
				if(belief.getTotalRemainingDevCards() > 0)
					belief.reviseDevCardModel();
			}
			recalcLargestArmy();
			// flow to next case!!!
		case A_PLACEROBBER:
			state[OFS_ROBBERPLACE] = a[1];
			if((a[2] != -1)){
				//the stealing action is non-deterministic
				state[OFS_NATURE_MOVE] = 1;
				state[OFS_VICTIM] = a[2];
			}
			break;
		case A_CHOOSE_RESOURCE:
			if(a[1] != -1 && (config.OBSERVABLE_POM_EFFECTS || state[OFS_OUR_PLAYER] == pl || state[OFS_OUR_PLAYER] == state[OFS_VICTIM] || belief == null)) {
				state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + a[1]]--;
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[1]]++;
				if(belief != null) {
					if(state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + a[1]] < 0)
						state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + a[1]] = 0;
					ResourceSet set = new ResourceSet();
					set.add(1, a[1]);
					belief.updateResourceBelief(set, pl, Action.GAIN);
		            belief.updateResourceBelief(set, state[OFS_VICTIM], Action.LOSE); 
				}
			}else if(belief != null) {
				if(state[OFS_OUR_PLAYER] == pl) {
					double[] rss = new double[NRESOURCES];
					for(Entry<ResourceSet,Double> entry: belief.getPlayerHandsModel()[state[OFS_VICTIM]].possibleResSets.entrySet()) {
						for(i = 0; i < NRESOURCES; i++) {
							rss[i] += entry.getValue() * entry.getKey().getAmount(i);
						}
					}
					
					ind = selectRandomResourceFromSet(rss);
					state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + ind]--;
					if(state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + ind] < 0)
						state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + ind] = 0;
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + ind]++;
					ResourceSet set = new ResourceSet();
					set.add(1, ind);
					belief.updateResourceBelief(set, pl, Action.GAIN);
		            belief.updateResourceBelief(set, state[OFS_VICTIM], Action.LOSE); 
					
				} else if(state[OFS_OUR_PLAYER] == state[OFS_VICTIM]) {
					state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + a[1]]--;
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[1]]++;
					ResourceSet set = new ResourceSet();
					set.add(1, a[1]);
					belief.updateResourceBelief(set, pl, Action.GAIN); 
		            belief.updateResourceBelief(set, state[OFS_VICTIM], Action.LOSE); 
				} else {
					if(state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + NRESOURCES] >= config.N_MAX_RSS_STEAL) {
						//bad design, but we must not forget the case where the observation is already provided in the action description
						if(a[1] != -1) {
							state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + a[1]]--;
							state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[1]]++;
							if(belief != null) {
								if(state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + a[1]] < 0)
									state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + a[1]] = 0;
								ResourceSet set = new ResourceSet();
								set.add(1, a[1]);
								belief.updateResourceBelief(set, pl, Action.GAIN);
					            belief.updateResourceBelief(set, state[OFS_VICTIM], Action.LOSE); 
							}
						}else {
							//this is always a chance node so it is safe to sample here
							//random stealing of one resource and make the action observable
							double[] rss = new double[NRESOURCES];
							if(config.UNIFORM_BELIEF_CHANCE_EVENTS) {
								int[] abs = belief.getPlayerHandsModel()[state[OFS_VICTIM]].rssAbs;
								for (i = 0; i < NRESOURCES; i++) {
									rss[i] = abs[PlayerResourceModel.MAX + i];
								}
							}else {
								for(Entry<ResourceSet,Double> entry: belief.getPlayerHandsModel()[state[OFS_VICTIM]].possibleResSets.entrySet()) {
									for(i = 0; i < NRESOURCES; i++) {
										rss[i] += entry.getValue() * entry.getKey().getAmount(i);
									}
								}
							}
							
							//rejection sampling until we find a legal rss to steal given the relaxed max bounds
							ResourceSet set;
							while(true) {
								double[] rssTemp = rss.clone();
								CatanFactoredBelief temp = belief.copy();
								set = new ResourceSet();
								ind = selectRandomResourceFromSet(rssTemp);
								rssTemp[ind] --;
								if(rssTemp[ind] < 0)
									rssTemp[ind] = 0;
								set.add(1, ind);
								temp.updateResourceBelief(set, state[OFS_VICTIM], Action.LOSE);
								if(!temp.getPlayerHandsModel()[state[OFS_VICTIM]].isEmpty())
									break;
							}
							belief.updateResourceBelief(set, pl, Action.GAIN);
							belief.updateResourceBelief(set, state[OFS_VICTIM], Action.LOSE);
						}
						
					}else {
						ResourceSet set = new ResourceSet(1);
			            belief.updateResourceBelief(set, pl, Action.GAIN, state[OFS_VICTIM]); //gain first
			            belief.updateResourceBelief(set, state[OFS_VICTIM], Action.LOSE); //lose after so we know what we can gain
		           
					}
					 //make sure the representation matches the min from the abs rep for both participants
		            int[] abs = belief.getPlayerHandsModel()[pl].rssAbs;
					for (i = 0; i < NRESOURCES; i++) {
						state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i] = abs[PlayerResourceModel.MIN + i];
					}
					abs = belief.getPlayerHandsModel()[state[OFS_VICTIM]].rssAbs;
					for (i = 0; i < NRESOURCES; i++) {
						state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + i] = abs[PlayerResourceModel.MIN + i];
					}
				}
			}
			state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + NRESOURCES]--;
			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]++;
			state[OFS_NATURE_MOVE] = 0;
			state[OFS_VICTIM] = 0;
			break;
        case A_PLAYCARD_MONOPOLY:
            state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = 1;
            state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY]--;
            state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + N_DEVCARDTYPES]--;
            state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_MONOPOLY]++;
			state[OFS_MONO_RSS_CHOICE] = a[1];
			state[OFS_NATURE_MOVE] = 1;
            if(belief != null) {
				if(state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY] = 0;
				belief.updateDevCardModel(CARD_MONOPOLY, Action.LOSE, pl);
				if(belief.getTotalRemainingDevCards() > 0)
					belief.reviseDevCardModel();
            }
            break;
            
        case A_CHOOSE_MONO_TOTALS:
        	int choice = state[OFS_MONO_RSS_CHOICE];
        	if(belief != null) {
        		boolean observable = belief.isRssModelObs();
        		if(a[2] != -1) {
        			//in the case of POMCP we will receive the exact action description and effects from the fully-observable state
        			int total = 0;
					for (ind = 0; ind<NPLAYERS; ind++) {
    	                if (ind==pl) 
    	                	continue;
    	                else {
    	                	ResourceSet lost = new ResourceSet(a[1 + ind],choice);
    	                	belief.updateResourceBelief(lost, ind, Action.LOSEMONO);
	    	                state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice] = 0;
	    	                state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + NRESOURCES] -= a[1+ind];
	    	                if(state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice] < 0)
	    	                	state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice] = 0;
	    	                total += a[1 + ind];
    	                }
					}
    	            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + choice] += total;
    	            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES] += total;
                	ResourceSet gain = new ResourceSet();
					gain.add(total, choice);
                	belief.updateResourceBelief(gain, pl, Action.GAINMONO);
        			
        		}else if(observable) {
        			int total = 0;
    	            for (ind = 0; ind<NPLAYERS; ind++) {
    	                if (ind==pl)
    	                    continue;
    	                //must use the exact description to avoid any errors, as the value set in the state is only the lower bound
    	                int amt = belief.getHandIfOnlyOnePossible(ind).getAmount(choice);
    	                state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice] = 0;
    	                state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + NRESOURCES] -= amt;
	                	ResourceSet lost = new ResourceSet(amt,choice);
						belief.updateResourceBelief(lost, ind, Action.LOSEMONO);
						total += amt;
    	            }
    	            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + choice] += total;
    	            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES] += total;
                	ResourceSet gain = new ResourceSet();
					gain.add(total, choice);
                	belief.updateResourceBelief(gain, pl, Action.GAINMONO);
        		}else {
        			//rejection sampling... TODO: it probably shouldn't be uniform, but anything else would be expensive?
		        	int absTotal = 0;
		        	for (ind = 0; ind<NPLAYERS; ind++) {
		        		absTotal += belief.getPlayerHandsModel()[ind].rssAbs[PlayerResourceModel.MAX + choice];
		        	}
					int total = 0;
					int sum = 0;
					int[] discardAmt = new int[NPLAYERS]; 
					boolean conflict = true;
					ThreadLocalRandom rnd = ThreadLocalRandom.current();
					CatanFactoredBelief tempBelief = null;
					while(conflict) {
						conflict = false;
						
						while(true) {
							//first sample a total as the total in the belief model is only an upper bound
							if(pl == state[OFS_OUR_PLAYER])
								total = rnd.nextInt(absTotal - state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + choice] + 1);
							else {
								//we need to sample what this player has also...
								int poss = belief.getPlayerHandsModel()[pl].rssAbs[PlayerResourceModel.MIN + choice] + rnd.nextInt(belief.getPlayerHandsModel()[pl].rssAbs[PlayerResourceModel.MAX + choice] - belief.getPlayerHandsModel()[pl].rssAbs[PlayerResourceModel.MIN + choice] + 1);
								total = rnd.nextInt(absTotal - poss + 1);		
							}
							sum = 0;
							for (ind = 0; ind<NPLAYERS; ind++) {
				                if (ind==pl)
				                    continue;
								
								discardAmt[ind] = belief.getPlayerHandsModel()[ind].rssAbs[PlayerResourceModel.MIN + choice] + rnd.nextInt(belief.getPlayerHandsModel()[ind].rssAbs[PlayerResourceModel.MAX + choice] - belief.getPlayerHandsModel()[ind].rssAbs[PlayerResourceModel.MIN + choice] + 1);
								sum+= discardAmt[ind];
							}
							if(sum == total)
								break;
						}
						tempBelief = belief.copy();
						ResourceSet lost;
						for (ind = 0; ind<NPLAYERS; ind++) {
			                if (ind==pl)
			                    continue;
							lost = new ResourceSet(discardAmt[ind],choice);
							tempBelief.updateResourceBelief(lost, ind, Action.LOSEMONO);
						}
						
			        	ResourceSet gain = new ResourceSet();
						gain.add(total, choice);
						tempBelief.updateResourceBelief(gain, pl, Action.GAINMONO);
						
						for (ind = 0; ind<NPLAYERS; ind++) {
			                if (ind==pl)
			                	continue;
			                if(tempBelief.getPlayerHandsModel()[ind].isEmpty())
			                	conflict = true;//reject
						}
						
					}
					//now update the real belief
					for (ind = 0; ind<NPLAYERS; ind++) {
		                if (ind==pl) {
		    	        	ResourceSet gain = new ResourceSet();
		    				gain.add(total, choice);
		    				belief.updateResourceBelief(gain, pl, Action.GAINMONO);
		                	state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice] += total;
		    				state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + NRESOURCES]+=total;
		                }else {
		                	ResourceSet lost = new ResourceSet(discardAmt[ind],choice);
		                	belief.updateResourceBelief(lost, ind, Action.LOSEMONO);
		                	state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice] = 0;
		                	state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + NRESOURCES]-=discardAmt[ind];
		                }
					}
        		}
        	}else {
                for (ind = 0; ind<NPLAYERS; ind++)
                {
                    if (ind==pl)
                        continue;
                    state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + choice] += state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice];
                    state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES] += state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice];
                    state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + NRESOURCES] -= state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice];
                    state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice] = 0;
                }
        	}
			
			//resetting the choice. Even if default is equal to sheep, we use nature move offset and state transition to guide the state flow
			state[OFS_MONO_RSS_CHOICE] = 0;
			state[OFS_NATURE_MOVE] = 0;
			break;
        case A_PLAYCARD_FREEROAD:
            state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = 1;
            state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD]--;
            state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + N_DEVCARDTYPES]--;
            state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_FREEROAD]++;
			if(belief != null) {
				if(state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD] = 0;
				belief.updateDevCardModel(CARD_FREEROAD, Action.LOSE, pl);
				if(belief.getTotalRemainingDevCards() > 0)
					belief.reviseDevCardModel();
			}
            break;
        case A_PLAYCARD_FREERESOURCE:
            state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = 1;
            state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE]--;
            state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + N_DEVCARDTYPES]--;
            state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_FREERESOURCE]++;
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[1]] ++;
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[2]] ++;
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]+=2;
            
			if(belief != null) {
				if(state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE] = 0;
				belief.updateDevCardModel(CARD_FREERESOURCE, Action.LOSE, pl);
				if(belief.getTotalRemainingDevCards() > 0)
					belief.reviseDevCardModel();
				ResourceSet set = new ResourceSet();
				set.add(1, a[1]);
				set.add(1, a[2]);
				belief.updateResourceBelief(set, pl, Action.GAIN);
			}
            
            break;
		case A_PAYTAX:
			//the discard amounts and types are specified for our player or when the game is observable or when the action is random and observable
			val = state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]/2;
			if(a[1] != -1 && ((sample && config.OBS_DISCARDS_IN_ROLLOUTS) || state[OFS_OUR_PLAYER] == pl || 
					val > config.N_MAX_DISCARD || config.OBSERVABLE_POM_EFFECTS)) {
				for (i = 0; i < NRESOURCES; i++) {
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i] -= a[i + 1];
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]-= a[i + 1];
				}
				if(belief!=null) {
					ResourceSet discardSet = new ResourceSet();
					for (i = 0; i < NRESOURCES; i++) {
						discardSet.add(a[i+1], i);
						if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i] < 0)
							state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i] = 0;
					}
					belief.updateResourceBelief(discardSet, pl, Action.LOSE);
				}
				
			}else {
				int count = state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]/2;
				ResourceSet set = new ResourceSet(count);
				belief.updateResourceBelief(set, pl, Action.LOSE);
				int[] abs = belief.getPlayerHandsModel()[pl].rssAbs;
				//make sure the representation matches the min from the abs rep as this is the most we can do here
				for (i = 0; i < NRESOURCES; i++) {
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i] = abs[PlayerResourceModel.MIN + i];
				}
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]-= count;
			}
			
			break;
        case A_TRADE:
        	state[OFS_NUMBER_OF_OFFERS]++;
        	//execute the trade by swapping the resources;
        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[4]] -= a[3];
        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]-= a[3];
        	if(a[5] > -1) {
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[6]] -= a[5];
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]-= a[5];
        	}
        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[8]] += a[7];
        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]+= a[7];
        	if(a[9] > -1) {
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[10]] += a[9];
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES]+= a[9];
        	}
        	//for opponent 
        	state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[4]] += a[3];
        	state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + NRESOURCES]+= a[3];
        	if(a[5] > -1) {
        		state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[6]] += a[5];
        		state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + NRESOURCES]+= a[5];
        	}
        	state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[8]] -= a[7];
        	state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + NRESOURCES]-= a[7];
        	if(a[9] > -1) {
        		state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[10]] -= a[9];
        		state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + NRESOURCES]-= a[9];
        	}
        	
			if(belief != null) {
				ResourceSet set = new ResourceSet();
				set.add(a[3], a[4]);
				if(a[5] > -1) {
					set.add(a[5], a[6]);
				}
				belief.updateResourceBelief(set, pl, Action.LOSE);
				belief.updateResourceBelief(set, a[2], Action.GAIN);
				set = new ResourceSet();
				set.add(a[7], a[8]);
				if(a[9] > -1) {
					set.add(a[9], a[10]);
				}
				belief.updateResourceBelief(set, pl, Action.GAIN);
				belief.updateResourceBelief(set, a[2], Action.LOSE);

				if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[4]] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[4]] = 0;
				if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[6]] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[6]] = 0;
				if(state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[8]] < 0)
					state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[8]] = 0;
				if(state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[10]] < 0)
					state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[10]] = 0;
			}
        	
        	//check if any player has negative resources and report the problem;
        	if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[4]] < 0 || state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[6]] < 0 ||
        			state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[8]] < 0 || state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[10]] < 0)
        		System.err.println("negative rss");
        	break;
        case A_ACCEPT:
        	//same as above, only that we need to look into the currentOffer and the initiator field in bl when executing trade;
        	state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 4]] -= state[OFS_CURRENT_OFFER + 3];
        	state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + NRESOURCES] -= state[OFS_CURRENT_OFFER + 3];
        	if(state[OFS_CURRENT_OFFER + 5] > -1) {
        		state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 6]] -= state[OFS_CURRENT_OFFER + 5];
        		state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + NRESOURCES] -= state[OFS_CURRENT_OFFER + 5];
        	}
        	state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 8]] += state[OFS_CURRENT_OFFER + 7];
        	state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + NRESOURCES] += state[OFS_CURRENT_OFFER + 7];
        	if(state[OFS_CURRENT_OFFER + 9] > -1) {
        		state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 10]] += state[OFS_CURRENT_OFFER + 9];
        		state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + NRESOURCES] += state[OFS_CURRENT_OFFER + 9];
        	}
        	//for the accepting player
        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 4]] += state[OFS_CURRENT_OFFER + 3];
        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES] += state[OFS_CURRENT_OFFER + 3];
        	if(state[OFS_CURRENT_OFFER + 5] > -1) {
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 6]] += state[OFS_CURRENT_OFFER + 5];
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES] += state[OFS_CURRENT_OFFER + 5];
        	}
        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 8]] -= state[OFS_CURRENT_OFFER + 7];
        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES] -= state[OFS_CURRENT_OFFER + 7];
        	if(state[OFS_CURRENT_OFFER + 9] > -1) {
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 10]] -= state[OFS_CURRENT_OFFER + 9];
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES] -= state[OFS_CURRENT_OFFER + 9];
        	}
        	
			if(belief != null) {
				ResourceSet set = new ResourceSet();
				set.add(state[OFS_CURRENT_OFFER + 3], state[OFS_CURRENT_OFFER + 4]);
				if(state[OFS_CURRENT_OFFER + 5] > -1) {
					set.add(state[OFS_CURRENT_OFFER + 5], state[OFS_CURRENT_OFFER + 6]);
				}
				belief.updateResourceBelief(set, state[OFS_CURRENT_OFFER + 1], Action.LOSE);
				belief.updateResourceBelief(set, pl, Action.GAIN);
				set = new ResourceSet();
				set.add(state[OFS_CURRENT_OFFER + 7], state[OFS_CURRENT_OFFER + 8]);
				if(state[OFS_CURRENT_OFFER + 9] > -1) {
					set.add(state[OFS_CURRENT_OFFER + 9], state[OFS_CURRENT_OFFER + 10]);
				}
				belief.updateResourceBelief(set, state[OFS_CURRENT_OFFER + 1], Action.GAIN);
				belief.updateResourceBelief(set, pl, Action.LOSE);
				if(state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 4]] < 0)
					state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 4]] = 0;
				if(state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 6]] < 0)
					state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 6]] = 0;
				if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 8]] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 8]] = 0;
				if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 10]] < 0)
					state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 10]] = 0;
			}
        	
        	//check if any player has negative resources and report the problem;
        	if(state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 4]] < 0 || state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 6]] < 0 ||
        			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 8]] < 0 || state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 10]] < 0)
        		System.err.println("negative rss");
        	
        	for(i = 0; i < ACTIONSIZE; i++)
        		state[OFS_CURRENT_OFFER + i] = 0;
        	break;
        case A_REJECT:
        	for(i = 0; i < ACTIONSIZE; i++)
        		state[OFS_CURRENT_OFFER + i] = 0;
        	break;
        case A_OFFER:
        	state[OFS_NUMBER_OF_OFFERS]++;
        	for(i = 0; i < ACTIONSIZE; i++)
        		state[OFS_CURRENT_OFFER + i] = a[i];
        	break;
        case A_CONTINUE_GAME:
        	state[OFS_NATURE_MOVE] = 0;
        	belief.updateNonVPDevCards(state[OFS_PLAYERDATA[pl] + OFS_SCORE], pl);
        	if(belief.getTotalRemainingDevCards() > 0)
				belief.reviseDevCardModel();
        	break;
        case A_WIN_GAME:
        	//the below should suffice so no need to reveal in the observable state also.
        	belief.getDevCardModel().reveal(pl, a[1], CARD_ONEPOINT);
        	state[OFS_NATURE_MOVE] = 0;
        	break;
		}
        stateTransition(a);
	}

	public Options listPossiblities(boolean sample) {
		int fsmlevel = state[OFS_FSMLEVEL];
		int fsmstate = state[OFS_FSMSTATE + fsmlevel];
		int pl = state[OFS_FSMPLAYER + fsmlevel];
		Options options = new Options();
				
		switch (fsmstate) {
		case S_SETTLEMENT1:
		case S_SETTLEMENT2:
			listInitSettlementPossibilities(options);
			break;
		case S_ROAD1:
		case S_ROAD2:
			listInitRoadPossibilities(options);
			break;
		case S_BEFOREDICE:
			listBeforeRollPossibilities(options, pl);
			break;
		case S_DICE_RESULT:
			listDiceResultPossibilities(options);
			break;
		case S_FREEROAD1:
		case S_FREEROAD2:
			listRoadPossibilities(options,1.0);
			break;
		case S_PAYTAX:
			listDiscardPossiblities(options,sample);
			break;
		case S_ROBBERAT7:
			listRobberPossibilities(options, A_PLACEROBBER, 1.0);
			break;
		case S_STEALING:
			listStealingPossiblities(options, state[OFS_VICTIM]);
			break;
		case S_NORMAL:
			listNormalPossibilities(options,sample);
			break;
		case S_BUYCARD:
			listDealDevCardPossibilities(options);
			break;
		case S_NEGOTIATIONS:
			listTradeResponsePossiblities(options);
			break;
		case S_MONOPOLY_EFFECT:
			options.put(Actions.newAction(A_CHOOSE_MONO_TOTALS, -1, -1, -1, -1), 1.0);
			break;
		case S_WIN_POSSIBILITY:
			listWinGamePossibility(options);
			break;
		}
		return options;
	}
	
	public TreeNode generateNode() {
		int[] beliefRepresentation = null;
		if(belief != null)
			beliefRepresentation = belief.getRepresentation();
		
		int fsmlevel = state[OFS_FSMLEVEL];
		int fsmstate = state[OFS_FSMSTATE + fsmlevel];
		if(fsmstate == S_PAYTAX){
			int pl = state[OFS_FSMPLAYER + fsmlevel];
			int val = state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES];
			val = val / 2;
			if(val <= config.N_MAX_DISCARD)
				return new StandardNode(getState(), beliefRepresentation, isTerminal(), getCurrentPlayer());
			else
				return new ChanceNode(getState(), beliefRepresentation, isTerminal(), getCurrentPlayer());
		}
		if(state[OFS_NATURE_MOVE] == 0)
			return new StandardNode(getState(), beliefRepresentation, isTerminal(), getCurrentPlayer());
		else
			return new ChanceNode(getState(), beliefRepresentation, isTerminal(), getCurrentPlayer());
	}

	public int[] sampleNextAction() {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int fsmlevel    = state[OFS_FSMLEVEL];
        int fsmstate    = state[OFS_FSMSTATE+fsmlevel];
        Options options = listPossiblities(true);
        
        if(/*state[OFS_NATURE_MOVE] != 1 &&*/ options.size() >1){
        	depth ++;
        	breadth += options.size();
        }
        
        int val = -1;
        if(belief != null) {
        	//only because we didn't specify the distribution, we must treat these actions differently
        	if(fsmstate == S_DICE_RESULT){
				state[OFS_DICE] = rnd.nextInt(6) + rnd.nextInt(6) + 2;
				val = state[OFS_DICE];
        	}else if(fsmstate == S_STEALING && state[OFS_OUR_PLAYER] == state[OFS_VICTIM]) {
        		val = selectRandomResourceInHand(state[OFS_VICTIM]);
			}else {
				//simple weighted random
				double choice = rnd.nextDouble(options.getTotalMass());
				ArrayList<int[]> opts = options.getOptions();
				ArrayList<Double> probs = options.getProbabilities();
				for(int i=0; i < options.size(); i++){
					choice -= probs.get(i);
				    if (choice <= 0.0d){
				    	return opts.get(i);
				    }
				}
			}
        }else {
			if(state[OFS_NATURE_MOVE] == 1){
				if(fsmstate == S_DICE_RESULT){
					state[OFS_DICE] = rnd.nextInt(6) + rnd.nextInt(6) + 2;
					val = state[OFS_DICE];
				}else if(fsmstate == S_STEALING){
					val = selectRandomResourceInHand(state[OFS_VICTIM]);
				}else if(fsmstate == S_BUYCARD){
					val = cardSequence[state[OFS_NCARDSGONE]];
				}else if(fsmstate == S_MONOPOLY_EFFECT) {
					return options.getOptions().get(0);//fake chance node, there should be a single option
				}
				
			}else{
				return options.getOptions().get(rnd.nextInt(options.size()));
			}
        }
        
		//iterate and choose the corresponding one
		if(val != -1){
			ArrayList<int[]> opts = options.getOptions();
			for(int i=0; i < options.size(); i++){
				if(opts.get(i)[1] == val)
					return opts.get(i);
			}
		}

		System.err.println("Couldn't find option in the list returning a random action");
		return options.getOptions().get(rnd.nextInt(options.size()));
	}
	
	public int sampleNextActionIndex() {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int fsmlevel    = state[OFS_FSMLEVEL];
        int fsmstate    = state[OFS_FSMSTATE+fsmlevel];
        int val = -1;
        Options options = listPossiblities(true);
        
        if(belief != null) {
        	//only because we didn't specify the distribution, we must treat these actions differently
        	if(fsmstate == S_DICE_RESULT){
				val = rnd.nextInt(6) + rnd.nextInt(6) + 2;
			}else if(fsmstate == S_STEALING && state[OFS_OUR_PLAYER] == state[OFS_VICTIM]) {
        		val = selectRandomResourceInHand(state[OFS_VICTIM]);
			}else {
				//simple weighted random
				double choice = rnd.nextDouble(options.getTotalMass());
				ArrayList<Double> probs = options.getProbabilities();
				for(int i=0; i < options.size(); i++){
					choice -= probs.get(i);
				    if (choice <= 0.0d){
				    	return i;
				    }
				}
			}
        }else {
			if(state[OFS_NATURE_MOVE] == 1){
				if(fsmstate == S_DICE_RESULT){
					state[OFS_DICE] = rnd.nextInt(6) + rnd.nextInt(6) + 2;
					val = state[OFS_DICE];
				}else if(fsmstate == S_STEALING){
					val = selectRandomResourceInHand(state[OFS_VICTIM]);
				}else if(fsmstate == S_BUYCARD){
					val = cardSequence[state[OFS_NCARDSGONE]];
				}else if(fsmstate == S_MONOPOLY_EFFECT) {
					return 0;//fake chance node, there should be a single option
				}
			}else{
				return rnd.nextInt(options.size());
			}
        }
		//iterate and choose the corresponding one
		if(val != -1){
			ArrayList<int[]> opts = options.getOptions();
			for(int i=0; i < options.size(); i++){
				if(opts.get(i)[1] == val)
					return i;
			}
		}
		System.err.println("Couldn't find option in the list returning a random index");
		return rnd.nextInt(options.size());
	}

	/**
	 * Calculates score given the observable information and what our player believes to be true if a belief model is available.
	 */
	public void recalcScores() {
		int[] auxScoreArray = new int[4];
		int pl;
		for (pl = 0; pl < NPLAYERS; pl++) {
			auxScoreArray[pl] = 0;
			auxScoreArray[pl] += state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS];
			auxScoreArray[pl] += state[OFS_PLAYERDATA[pl] + OFS_NCITIES] * 2;
			if(belief != null) { //to avoid counting the vps twice for our player, we use the belief model as well
				auxScoreArray[pl] += belief.getDevCardModel().getRevealedVP(pl);
			}else {
				auxScoreArray[pl] += state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_ONEPOINT];
			}
		}
		for (pl = 0; pl < NPLAYERS; pl++) {
			state[OFS_PLAYERDATA[pl] + OFS_SCORE] = auxScoreArray[pl];
		}

		pl = state[OFS_LARGESTARMY_AT];
		if (pl != -1)
			state[OFS_PLAYERDATA[pl] + OFS_SCORE] += 2;

		pl = state[OFS_LONGESTROAD_AT];
		if (pl != -1)
			state[OFS_PLAYERDATA[pl] + OFS_SCORE] += 2;
	}

	/**
	 * Performs the state transition and updates the current state id and player
	 * based on the executed action and the current state
	 * 
	 * @param a
	 *            the executed action
	 */
	private void stateTransition(int[] a) {
		int fsmlevel = state[OFS_FSMLEVEL];
		int fsmstate = state[OFS_FSMSTATE + fsmlevel];
		int pl = state[OFS_FSMPLAYER + fsmlevel];
		int startingPl = state[OFS_STARTING_PLAYER];

		switch (fsmstate) {
		case S_GAME:
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			state[OFS_FSMSTATE + fsmlevel] = S_SETTLEMENT1;
			state[OFS_FSMPLAYER + fsmlevel] = rnd.nextInt(4);//randomly choose the starting player
			state[OFS_STARTING_PLAYER] = state[OFS_FSMPLAYER + fsmlevel];
	        //make sure the robber location is recorded
			for (int i=0; i<N_HEXES; i++)
	        {
	            if (board.hextiles[i].subtype == LAND_DESERT)
	            {
	            	state[OFS_ROBBERPLACE] = i;
	                break;
	            }
	        }
			break;
		case S_SETTLEMENT1:
			state[OFS_FSMSTATE + fsmlevel] = S_ROAD1;
			break;
		case S_ROAD1:
			pl++;
			if (pl > NPLAYERS - 1) {
				pl = 0;
			}
			if (pl == startingPl) {
				//the last player starts the next phase so don't modify the current player field in the state
				state[OFS_FSMSTATE + fsmlevel] = S_SETTLEMENT2;
			} else {
				state[OFS_FSMPLAYER + fsmlevel] = pl;
				state[OFS_FSMSTATE + fsmlevel] = S_SETTLEMENT1;
			}
			break;
		case S_SETTLEMENT2:
			state[OFS_FSMSTATE + fsmlevel] = S_ROAD2;
			break;
		case S_ROAD2:
			if (pl == startingPl) {
				// the starting player starts the next phase
				state[OFS_FSMSTATE + fsmlevel] = S_BEFOREDICE;
			} else {
				pl--; //go back anti-clockwise for the second free settlement
				if (pl < 0)
					pl = NPLAYERS - 1;
				state[OFS_FSMPLAYER + fsmlevel] = pl;
				state[OFS_FSMSTATE + fsmlevel] = S_SETTLEMENT2;
			}
			break;
		case S_BEFOREDICE:
			if (a[0] == A_THROWDICE)
				state[OFS_FSMSTATE + fsmlevel] = S_DICE_RESULT;
			else if((a[0] == A_PLAYCARD_KNIGHT) && a[2] != -1){
				fsmlevel++;
				state[OFS_FSMLEVEL] = fsmlevel;
				state[OFS_FSMSTATE + fsmlevel] = S_STEALING;
				state[OFS_FSMPLAYER + fsmlevel] = pl;
			}//if no victim is specified, the player still needs to roll
			break;
		case S_DICE_RESULT:
			if ((a[0] == A_CHOOSE_DICE) && (state[OFS_DICE] != 7)) {
				state[OFS_FSMSTATE + fsmlevel] = S_NORMAL;
			} else if ((a[0] == A_CHOOSE_DICE) && (state[OFS_DICE] == 7)) {
				//always start from the current player and finish at the current player;
				state[OFS_DISCARD_FIRST_PL] = pl;
				int val = state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES];
				if(val > 7){
					fsmlevel++;
					state[OFS_FSMLEVEL] = fsmlevel;
					state[OFS_FSMSTATE + fsmlevel] = S_PAYTAX;
					state[OFS_FSMPLAYER + fsmlevel] = pl; 
					break;
				}
				int j = pl+1;
				if(j>=NPLAYERS)
					j=0;
				while(j!=pl){
					val = state[OFS_PLAYERDATA[j] + OFS_RESOURCES + NRESOURCES];
					if(val > 7){
						fsmlevel++;
						state[OFS_FSMLEVEL] = fsmlevel;
						state[OFS_FSMSTATE + fsmlevel] = S_PAYTAX;
						state[OFS_FSMPLAYER + fsmlevel] = j; 
						break;
					}
					j++;
					if(j>=NPLAYERS)
						j=0;
				}
				//if no player has to discard, then update to move robber
				if(state[OFS_FSMSTATE + fsmlevel] != S_PAYTAX){
					fsmlevel++;
					state[OFS_FSMLEVEL] = fsmlevel;
					state[OFS_FSMSTATE + fsmlevel] = S_ROBBERAT7;
					state[OFS_FSMPLAYER + fsmlevel] = pl;
				}
			}
			break;
		case S_PAYTAX:
			//go round the board until all players discarded or we are back to the original player			
			boolean discardFinished = true;
			pl++;
			if(pl>=NPLAYERS)
				pl=0;
			while(pl!=state[OFS_DISCARD_FIRST_PL]){
				int val = state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES];
				if(val > 7){
					state[OFS_FSMSTATE + fsmlevel] = S_PAYTAX;
					state[OFS_FSMPLAYER + fsmlevel] = pl; 
					discardFinished = false;
					break;
				}
				pl++;
				if(pl>=NPLAYERS)
					pl=0;
			}
			if(discardFinished){
				state[OFS_DISCARD_FIRST_PL] = -1;
				state[OFS_FSMPLAYER + fsmlevel] = state[OFS_FSMPLAYER + fsmlevel - 1]; //the player that rolled moves the robber
				state[OFS_FSMSTATE + fsmlevel] = S_ROBBERAT7;
			}
			break;
		case S_ROBBERAT7:
			if(a[2] != -1)
				state[OFS_FSMSTATE + fsmlevel] = S_STEALING;
			else{
				fsmlevel--;
				state[OFS_FSMLEVEL] = fsmlevel;
				state[OFS_FSMSTATE + fsmlevel] = S_NORMAL;
			}
			break;
		case S_STEALING:
			fsmlevel--;
			state[OFS_FSMLEVEL] = fsmlevel;
			if(state[OFS_DICE] != 0) 
				state[OFS_FSMSTATE + fsmlevel] = S_NORMAL;
			else
				state[OFS_FSMSTATE + fsmlevel] = S_BEFOREDICE;
			break;
		case S_NORMAL:
			switch (a[0]) {
			case A_PLAYCARD_MONOPOLY:
				if(state[OFS_NATURE_MOVE] == 1) {
					state[OFS_FSMSTATE + fsmlevel] = S_MONOPOLY_EFFECT;
				}
				break;
			case A_PLAYCARD_KNIGHT:
				if(a[2] != -1){
					fsmlevel++;
					state[OFS_FSMLEVEL] = fsmlevel;
					state[OFS_FSMPLAYER + fsmlevel] = pl;
					state[OFS_FSMSTATE + fsmlevel] = S_STEALING;
				}
				break;
			case A_BUYCARD:
				state[OFS_FSMSTATE + fsmlevel] = S_BUYCARD;
				break;
			case A_OFFER:
				fsmlevel++;
				state[OFS_FSMLEVEL] = fsmlevel;
				state[OFS_FSMPLAYER + fsmlevel] = a[2];
				state[OFS_FSMSTATE + fsmlevel] = S_NEGOTIATIONS;
				break;
			case A_ENDTURN:
				state[OFS_TURN]++;
				pl++;
				if (pl >= NPLAYERS)
					pl = 0;
				state[OFS_FSMPLAYER + fsmlevel] = pl;
				state[OFS_FSMSTATE + fsmlevel] = S_BEFOREDICE;
				break;
			case A_PLAYCARD_FREEROAD:
				// free road card can be played even if you only have one road left to build
				if (state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 14)
					state[OFS_FSMSTATE + fsmlevel] = S_FREEROAD1;
				else
					state[OFS_FSMSTATE + fsmlevel] = S_FREEROAD2;
				break;
			}
			break;
		case S_BUYCARD:
			state[OFS_FSMSTATE + fsmlevel] = S_NORMAL;
			break;
		case S_FREEROAD1:
			//the player may have roads left but there is no free space
			Options roadOpt = new Options();
			listRoadPossibilities(roadOpt,1.0);
			if (roadOpt.size() > 0)
				state[OFS_FSMSTATE + fsmlevel] = S_FREEROAD2;
			else
				state[OFS_FSMSTATE + fsmlevel] = S_NORMAL;
			break;
		case S_FREEROAD2:
			state[OFS_FSMSTATE + fsmlevel] = S_NORMAL;
			break;
		case S_NEGOTIATIONS:
			switch (a[0]) {
			case A_OFFER:
				state[OFS_FSMPLAYER + fsmlevel] = a[2]; //counter-offer, change the current player only
				break;
			case A_REJECT:
			case A_ACCEPT:
				fsmlevel--;
				state[OFS_FSMLEVEL] = fsmlevel;
				state[OFS_FSMSTATE + fsmlevel] = S_NORMAL;
				break;
			}
			break;
		case S_MONOPOLY_EFFECT:
			state[OFS_FSMSTATE + fsmlevel] = S_NORMAL;
			break;
		case S_WIN_POSSIBILITY:
			switch (a[0]) {
			case A_WIN_GAME:
				//do nothing. recalcScores and getWinner will find the winner since perform action revealed the vp cards required to win
				state[OFS_FSMSTATE + fsmlevel] = S_FINISHED;
				break;
			case A_CONTINUE_GAME:
				//only revert to previous state
				fsmlevel--;
				state[OFS_FSMLEVEL] = fsmlevel;
				break;
			}
		}
		
		recalcScores();
		if (getWinner() != -1) {
			state[OFS_FSMSTATE + fsmlevel] = S_FINISHED;
		}else if( (state[OFS_FSMSTATE + fsmlevel] == S_BEFOREDICE || 
				state[OFS_FSMSTATE + fsmlevel] == S_NORMAL ||
				state[OFS_FSMSTATE + fsmlevel] == S_FREEROAD1 ||
				state[OFS_FSMSTATE + fsmlevel] == S_FREEROAD2)
				&& winnerPossible(belief)) {
			pl = getCurrentPlayer();//use the correct player number since the state may have been updated.
			fsmlevel++;//this transition should be the only one that can get us to the maximum of level 2 (i.e. from freeroad!)
			state[OFS_NATURE_MOVE] = 1;
			state[OFS_FSMLEVEL] = fsmlevel;
			state[OFS_FSMPLAYER + fsmlevel] = pl;
			state[OFS_FSMSTATE + fsmlevel] = S_WIN_POSSIBILITY;
		}
			
	}

	//private listing actions methods//
    
	private void listInitSettlementPossibilities(Options options) {
		int i;
		for (i = 0; i < N_VERTICES; i++) {
			if (state[OFS_VERTICES + i] == 0) {
				options.put(Actions.newAction(A_BUILDSETTLEMENT, i),1.0);
			}
		}
	}
	
	private void listInitRoadPossibilities(Options options) {
		int i, ind;
		int lastvertex = state[OFS_LASTVERTEX];
		for (i = 0; i < 6; i++) {
			ind = board.neighborVertexEdge[lastvertex][i];
			if ((ind != -1) && (state[OFS_EDGES + ind] == 0)) {
				options.put(Actions.newAction(A_BUILDROAD, ind),1.0);
			}
		}
	}
	private void listBeforeRollPossibilities(Options options, int pl) {
		options.put(Actions.newAction(A_THROWDICE),1.0);
		if(belief != null && state[OFS_OUR_PLAYER] != pl) {
			double prob = belief.getDevCardModel().computeProbOfNonVp(CARD_KNIGHT, pl);
			if ((state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + N_DEVCARDTYPES] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] == 0) && prob > 0.0) {
				listRobberPossibilities(options, A_PLAYCARD_KNIGHT, prob);
			}
		}else {
			if ((state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] == 0)) {
				listRobberPossibilities(options, A_PLAYCARD_KNIGHT, 1.0);
			}
		}
	}
	
	private void listDiceResultPossibilities(Options options){
		for(int i = 2; i <= 12; i++){
			options.put(Actions.newAction(A_CHOOSE_DICE, i),1.0);
		}
	}
	
	private void listNormalPossibilities(Options options, boolean sample){
		if(sample && config.SAMPLE_FROM_DISTRIBUTION_OVER_TYPES_IN_ROLLOUTS){
			listNormalPossAndSampleType(options);
			return;
		}
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
		
		//probabilities for buying road, settlement, city or dev card
		//if belief is null, then we check exact description and assign 1 or 0;
		double [] probs = new double[4]; 
		if(belief != null && state[OFS_OUR_PLAYER] != pl) {
			if(!config.ABSTRACT_BELIEF) {
				for(Entry<ResourceSet,Double> entry: belief.getPlayerHandsModel()[pl].possibleResSets.entrySet()) {
					int[] rss = entry.getKey().getResourceArrayClone();
					if ((state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15) && (rss[RES_WOOD] >= 1) && (rss[RES_CLAY] >= 1))
						probs[0]+=entry.getValue();
					if ((state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] < 5) && (rss[RES_WOOD] >= 1)
							&& (rss[RES_CLAY] >= 1)	&& (rss[RES_WHEAT] >= 1) && (rss[RES_SHEEP] >= 1))
						probs[1]+=entry.getValue();
					if ((state[OFS_PLAYERDATA[pl] + OFS_NCITIES] < 4) && (rss[RES_STONE] >= 3)
							&& (rss[RES_WHEAT] >= 2)) 
						probs[2]+=entry.getValue();
			        if (state[OFS_NCARDSGONE] < NCARDS && (rss[RES_STONE] >= 1)
							&& (rss[RES_WHEAT] >= 1) && (rss[RES_SHEEP] >= 1))
			        	probs[3]+=entry.getValue();
				}
			}else {
				int[] rss = Arrays.copyOfRange(belief.getPlayerHandsModel()[pl].rssAbs, PlayerResourceModel.MAX, PlayerResourceModel.MAX + NRESOURCES);
				if ((state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15) && (rss[RES_WOOD] >= 1) && (rss[RES_CLAY] >= 1))
					probs[0]+=1.0;
				if ((state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] < 5) && (rss[RES_WOOD] >= 1)
						&& (rss[RES_CLAY] >= 1)	&& (rss[RES_WHEAT] >= 1) && (rss[RES_SHEEP] >= 1))
					probs[1]+=1.0;
				if ((state[OFS_PLAYERDATA[pl] + OFS_NCITIES] < 4) && (rss[RES_STONE] >= 3)
						&& (rss[RES_WHEAT] >= 2)) 
					probs[2]+=1.0;
		        if (state[OFS_NCARDSGONE] < NCARDS && (rss[RES_STONE] >= 1)
						&& (rss[RES_WHEAT] >= 1) && (rss[RES_SHEEP] >= 1))
		        	probs[3]+=1.0;
			}
		}else { //use the exact state description
			if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15))
				probs[0] = 1.0;
			if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] < 5))
				probs[1] = 1.0;
			if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] >= 3)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] >= 2)
					&& (state[OFS_PLAYERDATA[pl] + OFS_NCITIES] < 4))
				probs[2] = 1.0;
	        if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] >= 1) 
					&& state[OFS_NCARDSGONE] < NCARDS)
	        	probs[3] = 1.0;
		} 
		if(probs[0] > 0.0)
			listRoadPossibilities(options, probs[0]);
		if(probs[1] > 0.0)
			listSettlementPossibilities(options, probs[1]);
		if(probs[2] > 0.0)
			listCityPossibilities(options, probs[2]);
		if(probs[3] > 0.0)
			listBuyDevCardPossibility(options, probs[3]);
        listBankTradePossibilities(options);
        listDevCardPossibilities(options);
        if(config.TRADES && state[OFS_NUMBER_OF_OFFERS] < config.OFFERS_LIMIT){
        	//NOTE: this was an attempt to sample a single trade to reduce the branching factor, but sampling from types makes more sense than this
        	if(config.ALLOW_SAMPLING_IN_NORMAL_STATE){ 
        		listTradePossibilities(options,sample);
        	}else{
        		listTradePossibilities(options,false);
        	}
        }
        options.put(Actions.newAction(A_ENDTURN),1.0);
	}
	
	/**
	 * Chooses uniformly at random the action type to execute next and only
	 * lists the normal possibilities of the chosen type.
	 * 
	 * TODO: add option for weights on action types such that some would be executed more
	 * often in the roll-outs (i.e. consider basic player types)
	 */
	private void listNormalPossAndSampleType(Options options){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
		int i,j;
        HashMap<Integer, Double> actionTypes = new HashMap<>();
		Options roadOptions = new Options();
		Options settlementOptions = new Options();
		Options cityOptions = new Options();
		Options buyCardOptions = new Options();
		Options portTradeOptions = new Options();
        
        //can always end turn in the normal state
		actionTypes.put(A_ENDTURN, 1.0);
		double [] probs = new double[4];
		if(belief != null && state[OFS_OUR_PLAYER] != pl) {
			if(belief.getPlayerHandsModel()[pl].getTotalResources() > 0) {
				if(!config.ABSTRACT_BELIEF) {
					for(Entry<ResourceSet,Double> entry: belief.getPlayerHandsModel()[pl].possibleResSets.entrySet()) {
						int[] rss = entry.getKey().getResourceArrayClone();
						if ((state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15) && (rss[RES_WOOD] >= 1) && (rss[RES_CLAY] >= 1))
							probs[0]+=entry.getValue();
						if ((state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] < 5) && (rss[RES_WOOD] >= 1)
								&& (rss[RES_CLAY] >= 1)	&& (rss[RES_WHEAT] >= 1) && (rss[RES_SHEEP] >= 1))
							probs[1]+=entry.getValue();
						if ((state[OFS_PLAYERDATA[pl] + OFS_NCITIES] < 4) && (rss[RES_STONE] >= 3)
								&& (rss[RES_WHEAT] >= 2)) 
							probs[2]+=entry.getValue();
				        if (state[OFS_NCARDSGONE] < NCARDS && (rss[RES_STONE] >= 1)
								&& (rss[RES_WHEAT] >= 1) && (rss[RES_SHEEP] >= 1))
				        	probs[3]+=entry.getValue();
					}
				}else {
					int[] rss = Arrays.copyOfRange(belief.getPlayerHandsModel()[pl].rssAbs, PlayerResourceModel.MAX, PlayerResourceModel.MAX + NRESOURCES);
					if ((state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15) && (rss[RES_WOOD] >= 1) && (rss[RES_CLAY] >= 1))
						probs[0]+=1.0;
					if ((state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] < 5) && (rss[RES_WOOD] >= 1)
							&& (rss[RES_CLAY] >= 1)	&& (rss[RES_WHEAT] >= 1) && (rss[RES_SHEEP] >= 1))
						probs[1]+=1.0;
					if ((state[OFS_PLAYERDATA[pl] + OFS_NCITIES] < 4) && (rss[RES_STONE] >= 3)
							&& (rss[RES_WHEAT] >= 2)) 
						probs[2]+=1.0;
			        if (state[OFS_NCARDSGONE] < NCARDS && (rss[RES_STONE] >= 1)
							&& (rss[RES_WHEAT] >= 1) && (rss[RES_SHEEP] >= 1))
			        	probs[3]+=1.0;
				}
				
				for (i = 0; i < NPLAYERS; i++) {
					if(i==pl)
						continue;
					else if (belief.getPlayerHandsModel()[i].getTotalResources() > 0) {
						actionTypes.put(A_TRADE,1.0);
						break;
					}
				}
			}
			
			if(state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + N_DEVCARDTYPES] > 0) {
				double prob = belief.getDevCardModel().computeProbOfNonVp(CARD_FREERESOURCE, pl);
				if(prob > 0.0)
					actionTypes.put(A_PLAYCARD_FREERESOURCE, prob);
				prob = belief.getDevCardModel().computeProbOfNonVp(CARD_MONOPOLY, pl);
				if(prob > 0.0)
					actionTypes.put(A_PLAYCARD_MONOPOLY, prob);
				prob = belief.getDevCardModel().computeProbOfNonVp(CARD_FREEROAD, pl);
				if (state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15 && prob > 0.0) {
					// need to check if the player can place roads first
					Options roadOpt = new Options();
					listRoadPossibilities(roadOpt,1.0);//it doesn't matter what prob is sent here
					if (roadOpt.size() > 0)
						actionTypes.put(A_PLAYCARD_FREEROAD, prob);
				}
				prob = belief.getDevCardModel().computeProbOfNonVp(CARD_KNIGHT, pl);
				if(prob > 0.0)
					actionTypes.put(A_PLAYCARD_KNIGHT, prob);
			}
			
		}else {//use the exact state description
			if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15))
				probs[0] = 1.0;
			if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] < 5))
				probs[1] = 1.0;
			if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] >= 3)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] >= 2)
					&& (state[OFS_PLAYERDATA[pl] + OFS_NCITIES] < 4))
				probs[2] = 1.0;
	        if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] >= 1) 
					&& state[OFS_NCARDSGONE] < NCARDS)
	        	probs[3] = 1.0;
	        
	        int[] playersWithResources = new int[NPLAYERS];
	        ArrayList<Integer> oppWithRss = new ArrayList<>();
			for (i = 0; i < NPLAYERS; i++) {
				for (j = 0; j < NRESOURCES; j++)
					playersWithResources[i] += state[OFS_PLAYERDATA[i] + OFS_RESOURCES + j];
				if(i==pl)
					continue;
				//are there any other players with rss
				if(playersWithResources[i] > 0)
					oppWithRss.add(i);
			}
			//can only do trades if any of the opponents have resources and if we have resources
			if(playersWithResources[pl] > 0){
				if(oppWithRss.size() != 0)
					actionTypes.put(A_TRADE,1.0);
			}
			
			if (state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] == 0){
				if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] >= 1)
					actionTypes.put(A_PLAYCARD_KNIGHT,1.0);
				if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY] >= 1)
					actionTypes.put(A_PLAYCARD_MONOPOLY,1.0);
				if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE] >= 1)
					actionTypes.put(A_PLAYCARD_FREERESOURCE,1.0);
				if ((state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD] >= 1)
						&& (state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15)) {
					// need to check if the player can place roads first
					Options roadOpt = new Options();
					listRoadPossibilities(roadOpt,1.0);
					if (roadOpt.size() > 0)
						actionTypes.put(A_PLAYCARD_FREEROAD,1.0);
				}
			}
		}
		
		if(probs[0] > 0.0)
			listRoadPossibilities(roadOptions, probs[0]);
		if(probs[1] > 0.0)
			listSettlementPossibilities(settlementOptions, probs[1]);
		if(probs[2] > 0.0)
			listCityPossibilities(cityOptions, probs[2]);
		if(probs[3] > 0.0)
			listBuyDevCardPossibility(buyCardOptions, probs[3]);
        listBankTradePossibilities(portTradeOptions);
        
        
		if(roadOptions.size() > 0){
			actionTypes.put(A_BUILDROAD,probs[0]);
		}
		if(settlementOptions.size() > 0){
			actionTypes.put(A_BUILDSETTLEMENT,probs[1]);
		}
		if(cityOptions.size() > 0){
			actionTypes.put(A_BUILDCITY,probs[2]);
		}
		if(buyCardOptions.size() > 0){
			actionTypes.put(A_BUYCARD,probs[3]);
		}
		if(portTradeOptions.size() > 0){
			double max = 0.0;
			for(Double prob : portTradeOptions.getProbabilities()) {
				if(prob > max)
					max = prob.doubleValue();
			}
			actionTypes.put(A_PORTTRADE, max);
		}
		
		int chosenType = A_ENDTURN;
		double totalWeight = 0.0;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		if(config.ENFORCE_UNIFORM_TYPE_DIST_IN_BELIEF_ROLLOUTS && belief != null && state[OFS_OUR_PLAYER] != pl) {
			totalWeight = actionTypes.size();
			double random = rnd.nextDouble() * totalWeight;
			for (Entry<Integer,Double> e : actionTypes.entrySet()){
				random -= 1.0;
			    if (random <= 0.0) {
			        chosenType = e.getKey();
			        break;
			    }
			}
		}else {
			if(config.rolloutTypeDist instanceof HumanActionTypePdf) {
				Map<Integer,Double> dist = config.rolloutTypeDist.getDist(new ArrayList(actionTypes.keySet()));
				double val = 0.0;
				for (Entry<Integer,Double> e : dist.entrySet()){
					val = e.getValue() * actionTypes.get(e.getKey());
				    totalWeight += val;
				    actionTypes.put(e.getKey(), val);
				}
				double random = rnd.nextDouble() * totalWeight;
				for (Entry<Integer,Double> e : actionTypes.entrySet()){
					random -= e.getValue();
				    if (random <= 0.0) {
				        chosenType = e.getKey();
				        break;
				    }
				}
			}else {
				//it must be uniform, so no need to get the distribution, just use the legality probabilities
				for (Entry<Integer,Double> e : actionTypes.entrySet()){
				    totalWeight += e.getValue();
				}
				double random = rnd.nextDouble() * totalWeight;
				for (Entry<Integer,Double> e : actionTypes.entrySet()){
					random -= e.getValue();
				    if (random <= 0.0) {
				        chosenType = e.getKey();
				        break;
				    }
				}
			}
		}
		
		switch (chosenType) {
		case A_ENDTURN:
			options.put(Actions.newAction(A_ENDTURN),1.0);
			break;
		case A_TRADE:
			listTradePossibilities(options, true);
			break;
		case A_BUILDROAD:
			options.putAll(roadOptions);
			break;
		case A_BUILDSETTLEMENT:
			options.putAll(settlementOptions);
			break;
		case A_BUILDCITY:
			options.putAll(cityOptions);
			break;
		case A_BUYCARD:
			options.putAll(buyCardOptions);
			break;
		case A_PORTTRADE:
			options.putAll(portTradeOptions);
			break;
		case A_PLAYCARD_KNIGHT:
			listRobberPossibilities(options, A_PLAYCARD_KNIGHT, actionTypes.get(chosenType));
			break;
		case A_PLAYCARD_FREEROAD:
			options.put(Actions.newAction(A_PLAYCARD_FREEROAD), actionTypes.get(chosenType));
			break;
		case A_PLAYCARD_MONOPOLY:
			listMonopolyPossibilities(options,actionTypes.get(chosenType));
			break;
		case A_PLAYCARD_FREERESOURCE:
			listFreeResourcePossibilities(options,actionTypes.get(chosenType));
			break;
		}
		
	}
	
	public ArrayList<Integer> listNormalActionTypes(){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
		int i,j;
		ArrayList<Integer> actionTypes = new ArrayList<>();
		Options roadOptions = new Options();
		Options settlementOptions = new Options();
		Options cityOptions = new Options();
		Options buyCardOptions = new Options();
		Options portTradeOptions = new Options();
        
        //can always end turn in the normal state
		actionTypes.add(A_ENDTURN);
		double [] probs = new double[4];
		if(belief != null && state[OFS_OUR_PLAYER] != pl) {
			if(belief.getPlayerHandsModel()[pl].getTotalResources() > 0) {
				if(!config.ABSTRACT_BELIEF) {
					for(Entry<ResourceSet,Double> entry: belief.getPlayerHandsModel()[pl].possibleResSets.entrySet()) {
						int[] rss = entry.getKey().getResourceArrayClone();
						if ((state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15) && (rss[RES_WOOD] >= 1) && (rss[RES_CLAY] >= 1))
							probs[0]+=entry.getValue();
						if ((state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] < 5) && (rss[RES_WOOD] >= 1)
								&& (rss[RES_CLAY] >= 1)	&& (rss[RES_WHEAT] >= 1) && (rss[RES_SHEEP] >= 1))
							probs[1]+=entry.getValue();
						if ((state[OFS_PLAYERDATA[pl] + OFS_NCITIES] < 4) && (rss[RES_STONE] >= 3)
								&& (rss[RES_WHEAT] >= 2)) 
							probs[2]+=entry.getValue();
				        if (state[OFS_NCARDSGONE] < NCARDS && (rss[RES_STONE] >= 1)
								&& (rss[RES_WHEAT] >= 1) && (rss[RES_SHEEP] >= 1))
				        	probs[3]+=entry.getValue();
					}
				}else {
					int[] rss = Arrays.copyOfRange(belief.getPlayerHandsModel()[pl].rssAbs, PlayerResourceModel.MAX, PlayerResourceModel.MAX + NRESOURCES);
					if ((state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15) && (rss[RES_WOOD] >= 1) && (rss[RES_CLAY] >= 1))
						probs[0]+=1.0;
					if ((state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] < 5) && (rss[RES_WOOD] >= 1)
							&& (rss[RES_CLAY] >= 1)	&& (rss[RES_WHEAT] >= 1) && (rss[RES_SHEEP] >= 1))
						probs[1]+=1.0;
					if ((state[OFS_PLAYERDATA[pl] + OFS_NCITIES] < 4) && (rss[RES_STONE] >= 3)
							&& (rss[RES_WHEAT] >= 2)) 
						probs[2]+=1.0;
			        if (state[OFS_NCARDSGONE] < NCARDS && (rss[RES_STONE] >= 1)
							&& (rss[RES_WHEAT] >= 1) && (rss[RES_SHEEP] >= 1))
			        	probs[3]+=1.0;
				}
				
				for (i = 0; i < NPLAYERS; i++) {
					if(i==pl)
						continue;
					else if (belief.getPlayerHandsModel()[i].getTotalResources() > 0) {
						actionTypes.add(A_TRADE);
						break;
					}
				}
			}
			
			if(state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + N_DEVCARDTYPES] > 0) {
				double prob = belief.getDevCardModel().computeProbOfNonVp(CARD_FREERESOURCE, pl);
				if(prob > 0.0)
					actionTypes.add(A_PLAYCARD_FREERESOURCE);
				prob = belief.getDevCardModel().computeProbOfNonVp(CARD_MONOPOLY, pl);
				if(prob > 0.0)
					actionTypes.add(A_PLAYCARD_MONOPOLY);
				prob = belief.getDevCardModel().computeProbOfNonVp(CARD_FREEROAD, pl);
				if (state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15 && prob > 0.0) {
					// need to check if the player can place roads first
					Options roadOpt = new Options();
					listRoadPossibilities(roadOpt,1.0);//it doesn't matter what prob is sent here
					if (roadOpt.size() > 0)
						actionTypes.add(A_PLAYCARD_FREEROAD);
				}
				prob = belief.getDevCardModel().computeProbOfNonVp(CARD_KNIGHT, pl);
				if(prob > 0.0)
					actionTypes.add(A_PLAYCARD_KNIGHT);
			}
			
		}else {//use the exact state description
			if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15))
				probs[0] = 1.0;
			if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] < 5))
				probs[1] = 1.0;
			if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] >= 3)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] >= 2)
					&& (state[OFS_PLAYERDATA[pl] + OFS_NCITIES] < 4))
				probs[2] = 1.0;
	        if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] >= 1) 
					&& state[OFS_NCARDSGONE] < NCARDS)
	        	probs[3] = 1.0;
	        
	        int[] playersWithResources = new int[NPLAYERS];
	        ArrayList<Integer> oppWithRss = new ArrayList<>();
			for (i = 0; i < NPLAYERS; i++) {
				for (j = 0; j < NRESOURCES; j++)
					playersWithResources[i] += state[OFS_PLAYERDATA[i] + OFS_RESOURCES + j];
				if(i==pl)
					continue;
				//are there any other players with rss
				if(playersWithResources[i] > 0)
					oppWithRss.add(i);
			}
			//can only do trades if any of the opponents have resources and if we have resources
			if(playersWithResources[pl] > 0){
				if(oppWithRss.size() != 0)
					actionTypes.add(A_TRADE);
			}
			
			if (state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] == 0){
				if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] >= 1)
					actionTypes.add(A_PLAYCARD_KNIGHT);
				if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY] >= 1)
					actionTypes.add(A_PLAYCARD_MONOPOLY);
				if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE] >= 1)
					actionTypes.add(A_PLAYCARD_FREERESOURCE);
				if ((state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD] >= 1)
						&& (state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15)) {
					// need to check if the player can place roads first
					Options roadOpt = new Options();
					listRoadPossibilities(roadOpt,1.0);
					if (roadOpt.size() > 0)
						actionTypes.add(A_PLAYCARD_FREEROAD);
				}
			}
		}
		
		if(probs[0] > 0.0)
			listRoadPossibilities(roadOptions, probs[0]);
		if(probs[1] > 0.0)
			listSettlementPossibilities(settlementOptions, probs[1]);
		if(probs[2] > 0.0)
			listCityPossibilities(cityOptions, probs[2]);
		if(probs[3] > 0.0)
			listBuyDevCardPossibility(buyCardOptions, probs[3]);
        listBankTradePossibilities(portTradeOptions);
        
        
		if(roadOptions.size() > 0){
			actionTypes.add(A_BUILDROAD);
		}
		if(settlementOptions.size() > 0){
			actionTypes.add(A_BUILDSETTLEMENT);
		}
		if(cityOptions.size() > 0){
			actionTypes.add(A_BUILDCITY);
		}
		if(buyCardOptions.size() > 0){
			actionTypes.add(A_BUYCARD);
		}
		if(portTradeOptions.size() > 0){
			double max = 0.0;
			for(Double prob : portTradeOptions.getProbabilities()) {
				if(prob > max)
					max = prob.doubleValue();
			}
			actionTypes.add(A_PORTTRADE);
		}
		
		return actionTypes;
	}
	
	
	private void listSettlementPossibilities(Options options, double prob){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        int ind;
        boolean hasneighbor;
        
		for (int i = 0; i < N_VERTICES; i++) {
			if (state[OFS_VERTICES + i] == VERTEX_EMPTY) {
				hasneighbor = false;
				for (int j = 0; j < 6; j++) {
					ind = board.neighborVertexEdge[i][j];
					if ((ind != -1) && (state[OFS_EDGES + ind] == EDGE_OCCUPIED + pl))
						hasneighbor = true;
				}
				if (hasneighbor)
					options.put(Actions.newAction(A_BUILDSETTLEMENT, i), prob);
			}
		}
		
	}
	
	private void listCityPossibilities(Options options, double prob){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];

		for (int i = 0; i < N_VERTICES; i++) {
			if (state[OFS_VERTICES + i] == VERTEX_HASSETTLEMENT + pl) {
				options.put(Actions.newAction(A_BUILDCITY, i), prob);
			}
		}
	}
	
	private void listBuyDevCardPossibility(Options options, double prob){
		options.put(Actions.newAction(A_BUYCARD), prob);
	}
	
	private void listWinGamePossibility(Options options){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        DevCardModel devModel = belief.getDevCardModel();
        int score = state[OFS_PLAYERDATA[pl] + OFS_SCORE];//the score was just updated
        int cards = devModel.getTotalUnknownCards(pl) - devModel.getNonVPCards(pl);
		int required = 10 - score;//this is the number of vp required for the player to win the game
		int numberOfNonVP = score + cards - 9;// we know this player must have less or equal to 9 vp in total for the game to continue, so we know for certain that this amount are not vp cards
		//first check that it is possible to win the game... this should be checked earlier no? we shouldn't get in here
		int totalRemainingVP = devModel.getRemaining(CARD_ONEPOINT);
		chanceCount1++;
		if(required > totalRemainingVP) {//TODO: I have not seen this happen. Remove this check.
			//continue game may cause an infinite loop!!!
			options.put(Actions.newAction(A_CONTINUE_GAME, numberOfNonVP), 1.0);
			return;
		}
		//Due to sequential characteristics of the game and the fact that a player may gain at most 3 points at once, there should only be 3 cases here:
		//1) all cards must be vp cards;
		//2) 1 card can be anything else (including vp cards) while the others must be vp cards;
		//3) 2 cards can be anything else (including vp cards) while the others must be vp cards; (edge case when a settlement is built that blocks an existing longest road)
		double totalProb = 0.0;
		if(cards - required <= 2){
			for(int n = 0; n <= cards - required; n++) {
				totalProb += devModel.computeProbOfVP(pl, cards-n);
			}
		}
		
		chanceCount++;
		if(totalProb > 0.0)
			options.put(Actions.newAction(A_WIN_GAME, required), totalProb);
		options.put(Actions.newAction(A_CONTINUE_GAME, numberOfNonVP), 1.0 - totalProb);
	}
	
	private void listBankTradePossibilities(Options options){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        if(belief != null && state[OFS_OUR_PLAYER] != pl) {
        	double[][] probs = new double[3][5];
        	for(int i = 0; i < 3; i++) {
        		probs[i] = new double[5];
        	}
        	if(!config.ABSTRACT_BELIEF) {
	        	for(Entry<ResourceSet, Double> entry : belief.getPlayerHandsModel()[pl].possibleResSets.entrySet()) {
	            	ResourceSet set = entry.getKey();
	            	for (int i=0; i<NRESOURCES; i++) {
		        		 if (set.getAmount(i) >= 2 &&
			                        (state[OFS_PLAYERDATA[pl]+OFS_ACCESSTOPORT+i] == 1) )
		        			 probs[0][i] += entry.getValue();
		                else if (set.getAmount(i) >= 3 &&
		                			state[OFS_PLAYERDATA[pl]+OFS_ACCESSTOPORT+NRESOURCES] == 1 )
		                	probs[1][i] += entry.getValue();
		                else if (  set.getAmount(i) >= 4) 
		                	probs[2][i] += entry.getValue();
	            	}
	            	
	        	}
        	}else {
        		int[] rss = Arrays.copyOfRange(belief.getPlayerHandsModel()[pl].rssAbs, PlayerResourceModel.MAX, PlayerResourceModel.MAX + NRESOURCES);
	        	for (int i=0; i<NRESOURCES; i++) {
	        		 if (rss[i] >= 2 &&
		                        (state[OFS_PLAYERDATA[pl]+OFS_ACCESSTOPORT+i] == 1) )
	        			 probs[0][i] += 1.0;
	                else if (rss[i] >= 3 &&
	                			state[OFS_PLAYERDATA[pl]+OFS_ACCESSTOPORT+NRESOURCES] == 1 )
	                	probs[1][i] += 1.0;
	                else if (rss[i] >= 4) 
	                	probs[2][i] += 1.0;
	        	}
        	}
        	
	        for (int i=0; i<NRESOURCES; i++){
	            for (int j = 0; j<NRESOURCES; j++) {
	                if(probs[0][i] > 0.0)
		            	options.put(Actions.newAction(A_PORTTRADE, 2, i, 1, j),probs[0][i]);
	                if(probs[1][i] > 0.0)
		            	options.put(Actions.newAction(A_PORTTRADE, 3, i, 1, j),probs[1][i]);
	                if(probs[2][i] > 0.0)
		            	options.put(Actions.newAction(A_PORTTRADE, 4, i, 1, j),probs[2][i]);
	            }
	        }
   
        }else {
	        for (int i=0; i<NRESOURCES; i++)
	        {
	            for (int j = 0; j<NRESOURCES; j++)
	            {
	                if (i==j) continue;
	                // specific port
	                if (    (state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+i] >= 2) &&
	                        (state[OFS_PLAYERDATA[pl]+OFS_ACCESSTOPORT+i] == 1) )
	                	options.put(Actions.newAction(A_PORTTRADE, 2, i, 1, j),1.0);
	                // misc port
	                else if (    (state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+i] >= 3) &&
	                        (state[OFS_PLAYERDATA[pl]+OFS_ACCESSTOPORT+NRESOURCES] == 1) )
	                	options.put(Actions.newAction(A_PORTTRADE, 3, i, 1, j),1.0);
	                // bank
	                else if (   (state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+i] >= 4) )
	                	options.put(Actions.newAction(A_PORTTRADE, 4, i, 1, j),1.0);
	            }
	        }
        }
	}
	
	private void listRoadPossibilities(Options options, double prob) {
		int fsmlevel = state[OFS_FSMLEVEL];
		int pl = state[OFS_FSMPLAYER + fsmlevel];
		int i, j, ind;
		boolean hasneighbor;

		for (i = 0; i < N_VERTICES; i++) {

			if ((state[OFS_VERTICES + i] == VERTEX_EMPTY) || (state[OFS_VERTICES + i] == VERTEX_TOOCLOSE)
					|| (state[OFS_VERTICES + i] == VERTEX_HASSETTLEMENT + pl)
					|| (state[OFS_VERTICES + i] == VERTEX_HASCITY + pl)) {
				hasneighbor = false;
				for (j = 0; j < 6; j++) {
					ind = board.neighborVertexEdge[i][j];
					if ((ind != -1) && (state[OFS_EDGES + ind] == EDGE_OCCUPIED + pl)) {
						hasneighbor = true;
					}
				}
				if (hasneighbor) {
					for (j = 0; j < 6; j++) {
						ind = board.neighborVertexEdge[i][j];
						if ((ind != -1) && (state[OFS_EDGES + ind] == EDGE_EMPTY))
							options.put(Actions.newAction(A_BUILDROAD, ind), prob);
					}
				}
			}
		}
	}
	
	/**
	 * Lists the options of where to move the robber and who is the victim, without the action of stealing
	 * @param options
	 * @param action is this robber at 7 or following a played knight?
	 * @param prob the probability of this action being legal
	 */
	private void listRobberPossibilities(Options options, int action, double prob) {
		int fsmlevel = state[OFS_FSMLEVEL];
		int pl = state[OFS_FSMPLAYER + fsmlevel];
		int i, j, ind, pl2, val;

		boolean[] adjacentPlayers = new boolean[NPLAYERS];
		boolean[] playersWithResources = new boolean[NPLAYERS];
		Arrays.fill(playersWithResources, false);// make sure it is false
		for (i = 0; i < NPLAYERS; i++) {
			if (state[OFS_PLAYERDATA[i] + OFS_RESOURCES + NRESOURCES] > 0) {
				playersWithResources[i] = true;
			}
		}

		for (i = 0; i < N_HEXES; i++) {
			if (board.hextiles[i].type != TYPE_LAND)
				continue;
			if (i == state[OFS_ROBBERPLACE])
				continue;
			Arrays.fill(adjacentPlayers, false);// clear adjacent array
			for (j = 0; j < 6; j++) {
				ind = board.neighborHexVertex[i][j];
				if (ind == -1)
					continue;
				val = state[OFS_VERTICES + ind];
				if ((val >= VERTEX_HASSETTLEMENT) && (val < VERTEX_HASSETTLEMENT + NPLAYERS))
					pl2 = val - VERTEX_HASSETTLEMENT;
				else if ((val >= VERTEX_HASCITY) && (val < VERTEX_HASCITY + NPLAYERS))
					pl2 = val - VERTEX_HASCITY;
				else
					pl2 = -1;

				if ((pl2 != -1) && (pl2 != pl)) {
					// in order to only add one action per adjacent player and not per adjacent piece
					adjacentPlayers[pl2] = true;
				}
			}
			//add the victims based on who is adjacent and who has resources
			int counter = 0;
			for(j = 0; j < NPLAYERS; j++){
				if(adjacentPlayers[j] && playersWithResources[j]){
					options.put(Actions.newAction(action, i, j),prob);
					counter++;
				}
			}
			if(counter == 0)
				options.put(Actions.newAction(action, i, -1),prob);//can still move the robber on the hex, even if there will be no victim
		}
	}
	
	private void listDiscardPossiblities(Options options, boolean sample){
		int fsmlevel = state[OFS_FSMLEVEL];
		int pl = state[OFS_FSMPLAYER + fsmlevel];
		int val = state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + NRESOURCES];
		val = val / 2;
		boolean random = false;
		if(sample && config.OBS_DISCARDS_IN_ROLLOUTS) {
			random = true;
		}else if(val <= config.N_MAX_DISCARD){
			if(belief != null && state[OFS_OUR_PLAYER] != pl) {
				if(config.LIST_POMS) {//expensive listing here for discards needed mainly for POMCP. TODO: add probabilities of actions being legal
					HashSet<ResourceSet> discards = new HashSet<>();
					for(Entry<ResourceSet,Double> entry : belief.getResourceModel().getPlayerHandModel(pl).possibleResSets.entrySet()) {
						ResourceSet current = new ResourceSet(entry.getKey());
						discards.addAll(current.getSubsets(val, false));
					}
					for(ResourceSet discard : discards) {
						options.put(Actions.newAction(A_PAYTAX, discard.getAmount(RES_SHEEP), discard.getAmount(RES_WOOD),discard.getAmount(RES_CLAY), discard.getAmount(RES_WHEAT), discard.getAmount(RES_STONE)), 1.0);
					}
					
				}else{//we don't know what was discarded so we leave the belief update take care of this, including probabilities
					options.put(Actions.newAction(A_PAYTAX, -1, -1, -1, -1, -1),1.0);
				}
			}else {
				//get the resources
				int[] resources = new int[NRESOURCES];
				ResourceSet set = new ResourceSet();
				for (int i = 0; i < NRESOURCES; i++)
					set.add(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i], i);
				List<ResourceSet> discardList = set.getSubsets(val,false);
				for(ResourceSet opt: discardList){
					resources = opt.getResourceArrayClone();
					options.put(Actions.newAction(A_PAYTAX, resources[0], resources[1],resources[2], resources[3], resources[4]), 1.0);
				}
			}
		}else
			random = true;
		
		if(random) {
			ResourceSet discardSet;
			int i, ind;
			if(belief != null && state[OFS_OUR_PLAYER] != pl) {
				double[] rss = new double[NRESOURCES];
				if(config.UNIFORM_BELIEF_CHANCE_EVENTS) {
					int[] abs = belief.getPlayerHandsModel()[pl].rssAbs;
					for (i = 0; i < NRESOURCES; i++) {
						rss[i] = abs[PlayerResourceModel.MAX + i];
					}
				}else {
					for(Entry<ResourceSet,Double> entry: belief.getPlayerHandsModel()[pl].possibleResSets.entrySet()) {
						for(i = 0; i < NRESOURCES; i++) {
							rss[i] += entry.getValue() * entry.getKey().getAmount(i);
						}
					}
				}
				
				//rejection sampling until we find a legal discard
				while(true) {
					double[] rssTemp = rss.clone();
					CatanFactoredBelief temp = belief.copy();
					discardSet = new ResourceSet();
					for (i = 0; i < val; i++) {
						ind = selectRandomResourceFromSet(rssTemp);
						rssTemp[ind] --;
						if(rssTemp[ind] < 0)
							rssTemp[ind] = 0;
						discardSet.add(1, ind);
					}
					temp.updateResourceBelief(discardSet, pl, Action.LOSE);
					if(!temp.getPlayerHandsModel()[pl].isEmpty())
						break;
				}
				options.put(Actions.newAction(A_PAYTAX, discardSet.getAmount(RES_SHEEP), discardSet.getAmount(RES_WOOD),discardSet.getAmount(RES_CLAY), discardSet.getAmount(RES_WHEAT), discardSet.getAmount(RES_STONE)), 1.0);
			}else {
				double[] rssSet = new double[NRESOURCES];
				for (i = 0; i < NRESOURCES; i++)
					rssSet[i] = state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i];
				int[] action = Actions.newAction(A_PAYTAX);
				for (i = 0; i < val; i++) {
					ind = selectRandomResourceFromSet(rssSet);
					rssSet[ind] --;
					action[ind + 1] +=1;
				}
				options.put(action,1);
			}
		}
	}
	
	private void listStealingPossiblities(Options options, int victim){
		if(belief != null && state[OFS_OUR_PLAYER] != victim) {
			if(config.LIST_POMS) {
				double total = 0.0;
				double val;
				double[] prob = new double[NRESOURCES];
				for(Entry<ResourceSet,Double> entry: belief.getPlayerHandsModel()[state[OFS_VICTIM]].possibleResSets.entrySet()) {
					for(int i = 0; i < NRESOURCES; i++) {
						val = entry.getValue() * entry.getKey().getAmount(i);
						prob[i] += val;
						total += val;
					}
				}
				//normalise and add to list of options
				for (int i = 0; i < NRESOURCES; i++) {
					options.put(Actions.newAction(A_CHOOSE_RESOURCE, i),prob[i]/total);
				}
			}else
				options.put(Actions.newAction(A_CHOOSE_RESOURCE, -1),1.0);
		}else {
			for (int i = 0; i < NRESOURCES; i++) {
				if(state[OFS_PLAYERDATA[victim] + OFS_RESOURCES + i] > 0)
					options.put(Actions.newAction(A_CHOOSE_RESOURCE, i),1.0);
			}
		}
	}
	
	private void listDealDevCardPossibilities(Options options){
		//if we got here it means there must be at least one card left as the check is performed before the buycard action
		int fsmlevel = state[OFS_FSMLEVEL];
		int pl = state[OFS_FSMPLAYER + fsmlevel];
		double[] chances = new double[N_DEVCARDTYPES];
		if(belief != null) {
			//it is important to differentiate here between our players and the others, as our player cannot see what the others draw
			if(pl == state[OFS_OUR_PLAYER] || config.LIST_POMS) {
				chances = belief.getDevCardModel().getCurrentDrawChances();
			}else {
				options.put(Actions.newAction(A_DEAL_DEVCARD, N_DEVCARDTYPES),1.0);
				return;
			}
		}
		for (int i = 0; i < NCARDTYPES; i++) {
			if(belief != null) {
				if(chances[i] > 0.0)
					options.put(Actions.newAction(A_DEAL_DEVCARD, i),chances[i]);
			}else if(state[OFS_DEVCARDS_LEFT + i] > 0)
				options.put(Actions.newAction(A_DEAL_DEVCARD, i),1.0);
		}
	}
	
	private void listDevCardPossibilities(Options options) {
		int fsmlevel = state[OFS_FSMLEVEL];
		int pl = state[OFS_FSMPLAYER + fsmlevel];
		
		if (state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] != 0) {
			return;
		}
		if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + N_DEVCARDTYPES] < 1) {
			return;
		}
		if(belief != null && state[OFS_OUR_PLAYER] != pl) {
			double prob =  belief.getDevCardModel().computeProbOfNonVp(CARD_FREERESOURCE, pl);
			if (prob > 0)
				listFreeResourcePossibilities(options,prob);
			prob = belief.getDevCardModel().computeProbOfNonVp(CARD_MONOPOLY, pl);
			if(prob > 0)
				listMonopolyPossibilities(options, prob);
			prob = belief.getDevCardModel().computeProbOfNonVp(CARD_FREEROAD, pl);
			if (state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15 && prob > 0) {
				// need to check if the player can place roads first
				Options roadOpt = new Options();
				listRoadPossibilities(roadOpt,prob);
				if (roadOpt.size() > 0)
					options.put(Actions.newAction(A_PLAYCARD_FREEROAD), prob);
			}
			prob = belief.getDevCardModel().computeProbOfNonVp(CARD_KNIGHT, pl);
			if (prob > 0)
				listRobberPossibilities(options, A_PLAYCARD_KNIGHT, prob);
		}else {
			if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE] >= 1) {
				listFreeResourcePossibilities(options, 1.0);
			}
			if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY] >= 1) {
				listMonopolyPossibilities(options, 1.0);
			}
			if ((state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD] >= 1)
					&& (state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15)) {
				// need to check if the player can place roads first
				Options roadOpt = new Options();
				listRoadPossibilities(roadOpt,1.0);
				if (roadOpt.size() > 0)
					options.put(Actions.newAction(A_PLAYCARD_FREEROAD), 1.0);
			}
			if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] >= 1) {
				listRobberPossibilities(options, A_PLAYCARD_KNIGHT, 1.0);
			}
		}
	}
	
	private void listMonopolyPossibilities(Options options, double prob){
		for (int i = 0; i < NRESOURCES; i++)
			options.put(Actions.newAction(A_PLAYCARD_MONOPOLY, i, -1,-1,-1,-1),prob);
	}
	
	private void listFreeResourcePossibilities(Options options, double prob){
		for (int i = 0; i < NRESOURCES; i++)
			for (int j = i; j < NRESOURCES; j++)
				options.put(Actions.newAction(A_PLAYCARD_FREERESOURCE, i, j), prob);
	}
	
    private void listTradePossibilities(Options options, boolean sample){
        int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        int pl2, action, i;
        
        if(sample){
        	//if sample, first step is to choose the opponent and the trade type at random if there are sufficient resources
        	int[] playersWithResources = new int[NPLAYERS];
    		for (i = 0; i < NPLAYERS; i++) {
    			playersWithResources[i] += state[OFS_PLAYERDATA[i] + OFS_RESOURCES + NRESOURCES];
    		}
    		//does the current player have any rss?
    		if(playersWithResources[pl] == 0)
    			return;
    		//are there any other players with rss
    		ArrayList<Integer> oppWithRss = new ArrayList<>();
    		for (i = 0; i < NPLAYERS; i++) {
    			if(i==pl)
    				continue;
    			if(playersWithResources[i] > 0)
    				oppWithRss.add(i);
    		}
    		if(oppWithRss.size() == 0)
    			return;
    		//choose an opponent at random that is not us and has resources in their hand
    		ThreadLocalRandom rnd = ThreadLocalRandom.current();
    		pl2 = oppWithRss.get(rnd.nextInt(oppWithRss.size()));
        	
    		//options are 0 is 1:1; 1 is 1:2; 2 is 2:1
    		ArrayList<Integer> quantityOptions = new ArrayList<>();
    		quantityOptions.add(0);//1:1 is always true
    		if(playersWithResources[pl2] >= 2)
    			quantityOptions.add(1);
    		if(playersWithResources[pl] >= 2)
    			quantityOptions.add(2);
    		
    		int typeOfTrade = quantityOptions.get(rnd.nextInt(quantityOptions.size()));
        	
        	if(belief == null) {
	        	//if sampling in a fully observable game, just select the resources at random from the observable hand
	    		int giveable;
	    		int receiveable;
	    		int[] rss;
	    		switch (typeOfTrade) {
				case 0:
		        	giveable = selectRandomResourceInHand(pl);
		        	receiveable = selectRandomResourceInHand(pl2);
		        	options.put(Actions.newAction(A_TRADE, pl, pl2, 1, giveable, -1, -1, 1, receiveable, -1, -1),1.0);
					break;
				case 1://pl2 gives 2 rss
		        	giveable = selectRandomResourceInHand(pl);
		        	rss = select2RandomResourcesInHand(pl2);
		        	if(rss[0] == rss[1]){
		        		options.put(Actions.newAction(A_TRADE, pl, pl2, 1, giveable, -1, -1, 2, rss[0], -1, -1),1.0);
		        	}else{
		        		options.put(Actions.newAction(A_TRADE, pl, pl2, 1, giveable, -1, -1, 1, rss[0], 1,  rss[1]),1.0);
		        	}
					break;
				case 2://current pl gives 2 rss
					rss = select2RandomResourcesInHand(pl);
		        	receiveable = selectRandomResourceInHand(pl2);
		        	if(rss[0] == rss[1]){
		        		options.put(Actions.newAction(A_TRADE, pl, pl2, 2, rss[0], -1, -1, 1, receiveable, -1, -1),1.0);
		        	}else{
		        		options.put(Actions.newAction(A_TRADE, pl, pl2, 1, rss[0], 1, rss[1], 1, receiveable, -1, -1),1.0);
		        	}
					break;
				}
        	}else {
        		//we can sample in the belief as well, but by choosing from a set that represents how likely it is for the player to have the resource is given the current belief
				double[] ourRss = new double[NRESOURCES];
				for(Entry<ResourceSet,Double> entry: belief.getPlayerHandsModel()[pl].possibleResSets.entrySet()) {
					for(i = 0; i < NRESOURCES; i++) {
						ourRss[i] += entry.getValue() * entry.getKey().getAmount(i);
					}
				}
				double[] oppRss = new double[NRESOURCES];
				for(Entry<ResourceSet,Double> entry: belief.getPlayerHandsModel()[pl2].possibleResSets.entrySet()) {
					for(i = 0; i < NRESOURCES; i++) {
						oppRss[i] += entry.getValue() * entry.getKey().getAmount(i);
					}
				}
	    		int giveable;
	    		int receiveable;
	    		double[] tempRss;
	    		int[] rss = new int[2];
	    		int[] act = null;
	    		ResourceSet set;
	    		boolean conflict = true;
				//rejection sampling as we can select 2 rss that are never present at the same time
				while(conflict) {
					switch (typeOfTrade) {
					case 0:
			        	giveable = selectRandomResourceFromSet(ourRss);
			        	receiveable = selectRandomResourceFromSet(oppRss);
			        	act = Actions.newAction(A_TRADE, pl, pl2, 1, giveable, -1, -1, 1, receiveable, -1, -1);
			        	conflict = false;
						break;
					case 1://pl2 gives 2 rss
						tempRss = oppRss.clone();
			        	giveable = selectRandomResourceFromSet(ourRss);
			        	rss[0] = selectRandomResourceFromSet(tempRss);
			        	tempRss[rss[0]]--;
			        	rss[1] = selectRandomResourceFromSet(tempRss);
			        	set = new ResourceSet();
			        	set.add(1, rss[0]);
			        	set.add(1, rss[1]);
			        	for(Entry<ResourceSet,Double> entry: belief.getPlayerHandsModel()[pl2].possibleResSets.entrySet()) {
			        		if(entry.getKey().contains(set)) {
			        			conflict = false;
			        			break;
			        		}
			        	}
			        	
			        	if(rss[0] == rss[1]){
			        		act = Actions.newAction(A_TRADE, pl, pl2, 1, giveable, -1, -1, 2, rss[0], -1, -1);
			        	}else{
			        		act = Actions.newAction(A_TRADE, pl, pl2, 1, giveable, -1, -1, 1, rss[0], 1,  rss[1]);
			        	}
						break;
					case 2://current pl gives 2 rss
						tempRss = ourRss.clone();
						rss[0] = selectRandomResourceFromSet(tempRss);
						tempRss[rss[0]]--;
						rss[1] = selectRandomResourceFromSet(tempRss);
			        	receiveable = selectRandomResourceFromSet(oppRss);
			        	set = new ResourceSet();
			        	set.add(1, rss[0]);
			        	set.add(1, rss[1]);
			        	for(Entry<ResourceSet,Double> entry: belief.getPlayerHandsModel()[pl].possibleResSets.entrySet()) {
			        		if(entry.getKey().contains(set)) {
			        			conflict = false;
			        			break;
			        		}
			        	}
			        	
			        	if(rss[0] == rss[1]){
			        		act = Actions.newAction(A_TRADE, pl, pl2, 2, rss[0], -1, -1, 1, receiveable, -1, -1);
			        	}else{
			        		act = Actions.newAction(A_TRADE, pl, pl2, 1, rss[0], 1, rss[1], 1, receiveable, -1, -1);
			        	}
						break;
					}
				}
				options.put(act, 1.0);//1.0 because we already sampled from the current belief
        	}
        }else{
	        if(config.NEGOTIATIONS){
	        	action = A_OFFER;
	        }else{
	        	action = A_TRADE;
	        }
	        if(belief != null) {
	        	for(int[] trade : Trades.legalTrades){
            		//check if we have the resources 
	        		double prob = 0.0;
	        		if(!config.ABSTRACT_BELIEF) {
	            		for(Entry<ResourceSet, Double> entry : belief.getPlayerHandsModel()[pl].possibleResSets.entrySet()) {
	            			ResourceSet set = entry.getKey();
	            			if(trade[0] > set.getAmount(trade[1]))
	            				continue;
	            			if(trade[2] > -1 && trade[2] > set.getAmount(trade[3]))
	            				continue;
			            	prob += entry.getValue();
			            	
	            		}
	            		
	            		if(prob > 0.0) {
		            		if(config.NEGOTIATIONS) {
		            			//this means that the opponent has a say in the trade so we only care that there is a possibility the player can do the trade
				        		for(pl2 = 0; pl2 < NPLAYERS; pl2++) {
				        			if(pl2==pl) continue;
				        			double oppProb = 0.0;
				        			for(Entry<ResourceSet, Double> entry : belief.getPlayerHandsModel()[pl2].possibleResSets.entrySet()) {
				        				ResourceSet set = entry.getKey();
					        			if( trade[4] > set.getAmount(trade[5]))
						            		continue;
						            	if (trade[6] > -1 && trade[6] > set.getAmount(trade[7]))
						            		continue;
						            	oppProb += entry.getValue();
						            	break;
				        			}
				        			if(oppProb > 0.0) {
				        				options.put(Actions.newAction(action, pl, pl2, trade[0], trade[1], trade[2], trade[3], trade[4], trade[5], trade[6], trade[7]),prob);
				            			// order: initiator, recipient, (number of res given, type of rss given) x2, (number of rss received, type of rss received) x2
				        			}
				        		}
		            		}else {
		            			//here we should take the exact probability of the player having the needed resources
				        		for(pl2 = 0; pl2 < NPLAYERS; pl2++) {
				        			if(pl2==pl) continue;
				        			double oppProb = 0.0;
				        			for(Entry<ResourceSet, Double> entry : belief.getPlayerHandsModel()[pl2].possibleResSets.entrySet()) {
				        				ResourceSet set = entry.getKey();
					        			if( trade[4] > set.getAmount(trade[5]))
						            		continue;
						            	if (trade[6] > -1 && trade[6] > set.getAmount(trade[7]))
						            		continue;
						            	oppProb += entry.getValue();
				        			}
				        			if(oppProb > 0.0) {
				        				options.put(Actions.newAction(action, pl, pl2, trade[0], trade[1], trade[2], trade[3], trade[4], trade[5], trade[6], trade[7]),prob*oppProb);
				        				// order: initiator, recipient, (number of res given, type of rss given) x2, (number of rss received, type of rss received) x2
				        			}
				        		}
		            		}
	            		}
	        		}else {
	        			int[] rss = Arrays.copyOfRange(belief.getPlayerHandsModel()[pl].rssAbs, PlayerResourceModel.MAX, PlayerResourceModel.MAX + NRESOURCES);
	        			if(trade[0] > rss[trade[1]])
	        				continue;
	        			if(trade[2] > -1 && trade[2] > rss[trade[3]])
	        				continue;
		            	prob += 1.0;
		            	
	            		if(prob > 0.0) {
			        		for(pl2 = 0; pl2 < NPLAYERS; pl2++) {
			        			if(pl2==pl) continue;
			        			if( trade[4] > belief.getPlayerHandsModel()[pl2].rssAbs[PlayerResourceModel.MAX + trade[5]])
				            		continue;
				            	if (trade[6] > -1 && trade[6] > belief.getPlayerHandsModel()[pl2].rssAbs[PlayerResourceModel.MAX + trade[7]])
				            		continue;
			            		options.put(Actions.newAction(action, pl, pl2, trade[0], trade[1], trade[2], trade[3], trade[4], trade[5], trade[6], trade[7]),prob);
			            		// order: initiator, recipient, (number of res given, type of rss given) x2, (number of rss received, type of rss received) x2
			        		}
	            		}
	        		}
	        	}
	        }else {
		        for(int[] trade : Trades.legalTrades){
		        	//do we have the resources //TODO: this relies on the fact that state description will not contain -1 at any earlier indices...
		        	if(trade[0] <= state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+trade[1]] && trade[2] <= state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+trade[3]]){
		        		//for each opponent
		        		for(pl2 = 0; pl2 < NPLAYERS; pl2++){
		        			if(pl2==pl) continue;
		        			if(trade[4] <= state[OFS_PLAYERDATA[pl2]+OFS_RESOURCES+trade[5]] && trade[6] <= state[OFS_PLAYERDATA[pl2]+OFS_RESOURCES+trade[7]]){
		        				options.put(Actions.newAction(action, pl, pl2, trade[0], trade[1], trade[2], trade[3], trade[4], trade[5], trade[6], trade[7]),1.0);
		        				// order: initiator, recipient, (number of res given, type of rss given) x2, (number of rss received, type of rss received) x2
		        			}
		        		}
		        	}
		        }
	        }
        }
    }
	
    private void listTradeResponsePossiblities(Options options){
        int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        int pl2 = state[OFS_CURRENT_OFFER + 1];
        
    	double prob = 0.0;
    	if(belief != null) {
    		if(!config.ABSTRACT_BELIEF) {
		    	for(Entry<ResourceSet,Double> entry: belief.getPlayerHandsModel()[pl].possibleResSets.entrySet()) {
					int[] rss = entry.getKey().getResourceArrayClone();
					if ((rss[state[OFS_CURRENT_OFFER + 8]] >= state[OFS_CURRENT_OFFER + 7])) {
						if(state[OFS_CURRENT_OFFER + 9] > -1) { 
							if(rss[state[OFS_CURRENT_OFFER + 10]] >= state[OFS_CURRENT_OFFER + 9])
								prob+=entry.getValue();
						}else
							prob+=entry.getValue();
					}
				}
    		}else {
    			int[] rss = Arrays.copyOfRange(belief.getPlayerHandsModel()[pl].rssAbs, PlayerResourceModel.MAX, PlayerResourceModel.MAX + NRESOURCES);
				if ((rss[state[OFS_CURRENT_OFFER + 8]] >= state[OFS_CURRENT_OFFER + 7])) {
					if(state[OFS_CURRENT_OFFER + 9] > -1) { 
						if(rss[state[OFS_CURRENT_OFFER + 10]] >= state[OFS_CURRENT_OFFER + 9])
							prob=1.0;
					}else
						prob=1.0;
				}
    		}
    	}else
    		prob = 1.0;
    	
    	if(prob > 0.0)
    		options.put(Actions.newAction(A_ACCEPT),prob);
        options.put(Actions.newAction(A_REJECT),1.0);
        if(config.ALLOW_COUNTEROFFERS){//TODO: the following was not tested yet...also it uses the abstract description of a player's hand
        	if(belief != null) {
	            for(int[] trade : Trades.legalTrades){
	            	//first check if the opponent has the resources in any possible world
	            	if( trade[4] > belief.getPlayerHandsModel()[pl2].rssAbs[PlayerResourceModel.MAX + trade[5]])
	            		continue;
	            	if (trade[6] > -1 && trade[6] > belief.getPlayerHandsModel()[pl2].rssAbs[PlayerResourceModel.MAX + trade[7]])
	            		continue;
	            	prob = 0.0;
            		//now check if we have the resources
	            	if(!config.ABSTRACT_BELIEF) {
	            		for(Entry<ResourceSet, Double> entry : belief.getPlayerHandsModel()[pl].possibleResSets.entrySet()) {
			            	ResourceSet set = entry.getKey();
	            			if(trade[0] > set.getAmount(trade[1]))
	            				continue;
	            			if(trade[2] > -1 && trade[2] > set.getAmount(trade[3]))
	            				continue;
			            	prob += entry.getValue();
			            	
	            		}
	            	}else {
	            		int[] rss = Arrays.copyOfRange(belief.getPlayerHandsModel()[pl].rssAbs, PlayerResourceModel.MAX, PlayerResourceModel.MAX + NRESOURCES);
            			if(trade[0] > rss[trade[1]])
            				continue;
            			if(trade[2] > -1 && trade[2] > rss[trade[3]])
            				continue;
		            	prob = 1.0;
			            	
	            	}
            		if(prob > 0.0)
            			options.put(Actions.newAction(A_OFFER, pl, pl2, trade[0], trade[1], trade[2], trade[3], trade[4], trade[5], trade[6], trade[7]),prob);
            			// order: initiator, recipient, (number of res given, type of rss given) x2, (number of rss received, type of rss received) x2
	            }
        	}else {
	            for(int[] trade : Trades.legalTrades){
	            	//do we have the resources //TODO: this relies on the fact that state description will not contain -1 at any earlier indices...
	            	if(trade[0] <= state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+trade[1]] && trade[2] <= state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+trade[3]]){
	            		//only to the initiator of the current offer
	        			if(trade[4] <= state[OFS_PLAYERDATA[pl2]+OFS_RESOURCES+trade[5]] && trade[6] <= state[OFS_PLAYERDATA[pl2]+OFS_RESOURCES+trade[7]]){
	        				options.put(Actions.newAction(A_OFFER, pl, pl2, trade[0], trade[1], trade[2], trade[3], trade[4], trade[5], trade[6], trade[7]),1.0);
	        				// order: initiator, recipient, (number of res given, type of rss given) x2, (number of rss received, type of rss received) x2
	        			}
	            	}
	            }
        	}
        }
    }
    
	// other utility methods //
    
	private int selectRandomResourceFromSet(double[] rss) {
		int i;
		double ind;
		double ncards = 0;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (i = 0; i < NRESOURCES; i++)
			ncards += rss[i];
		if (ncards <= 0) {
			System.err.println("Player has " + ncards + " cards");
			return -1;
		}
		ind = rnd.nextDouble(ncards);
		for (i = 0; i < NRESOURCES; i++) {
			ind -= rss[i];
			if (ind <= 0.0d)
				return i;
		}
		return Math.min(i, NRESOURCES - 1);
	}
	
	private int selectRandomResourceInHand(int pl) {
		int i, ind, j;
		int ncards = 0;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (i = 0; i < NRESOURCES; i++)
			ncards += state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i];
		if (ncards <= 0) {
			System.err.println("Player " + pl + " has " + ncards + " cards");
			return -1;
		}
		ind = rnd.nextInt(ncards) + 1;
		j = 0;
		for (i = 0; i < NRESOURCES; i++) {
			j += state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i];
			if (j >= ind)
				break;
		}
		return Math.min(i, NRESOURCES - 1);
	}
	
	/**
	 * It may select the same type of rss if the player has multiple.
	 * NOTE: it doesn't check if this is possible. This should have been done before!
	 * @param pl
	 * @return an array with 2 rss or null if smth went wrong so an exception will be thrown later
	 */
	private int[] select2RandomResourcesInHand(int pl) {
		int[] rss = new int[2];
		int i, ind, j;
		int ncards = 0;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		for (i = 0; i < NRESOURCES; i++)
			ncards += state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i];
		if (ncards <= 0) {
			System.err.println("Player " + pl + " has " + ncards + " cards");
			return null;
		}
		ind = rnd.nextInt(ncards) + 1;
		j = 0;
		for (i = 0; i < NRESOURCES; i++) {
			j += state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i];
			if (j >= ind)
				break;
		}
		rss[0] = i;//first rss;
		if (ncards < 2)
			return null;
		//make sure not to select the same rss
		int prevInd = ind;
		boolean ok = false;
		while(!ok){
			ind = rnd.nextInt(ncards) + 1;
			if(ind != prevInd)
				ok = true;
		}
		//find the type
		j = 0;
		for (i = 0; i < NRESOURCES; i++) {
			j += state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i];
			if (j >= ind)
				break;
		}
		rss[1] = i;//second rss;
		
		return rss;
	}
	
	
	private static final int LR_EMPTY = 0;
	private static final int LR_UNCHECKED = 1;
	private static final int LR_CHECKED1 = 2;
	private static final int LR_CHECKED2 = 3;
	private static final int LR_CHECKED3 = 4;
	private static final int LR_MAXROUTE = 5;

	private boolean isOpponentPresentAtVertex(int[] s, int pl, int ind) {
		boolean returnval;
		int val = s[OFS_VERTICES + ind];
		if ((val == VERTEX_EMPTY) || (val == VERTEX_TOOCLOSE) || (val == VERTEX_HASSETTLEMENT + pl)
				|| (val == VERTEX_HASCITY + pl))
			returnval = false;
		else
			returnval = true;
		for (int j = 0; j < 6; j++) {
			val = board.neighborVertexEdge[ind][j];
			if ((val != -1) && (s[OFS_EDGES + val] != EDGE_EMPTY)) // opponent
																	// has road
				returnval = true;
		}
		return returnval;
	}

	private void lrDepthFirstSearch(int[] s, int pl, int ind, int[] lrVertices, boolean[] lrOpponentPresent,
			boolean[] lrPlayerPresent, int UNCHECKEDVALUE, int CHECKEDVALUE, int[] returnvalues) {
		int cind, cpos, i, j;
		int[] lrStack = new int[N_VERTICES];
		int[] lrStackPos = new int[N_VERTICES];
		int lrStacklen;
		boolean foundnext, isstartind;
		int maxlen = 0, maxStartInd = 0;
		int nextind, nextedge;

		nextind = 0; // unnecessary, but otherwise "uninitialized" error

		lrStacklen = 0;
		cind = ind;
		cpos = 0;
		isstartind = true;
		// lrStack[0] = ind;
		do {
			// System.out.printf("(%d,%d,%d) ",cind,cpos,lrStacklen);
			foundnext = false;
			// System.out.printf("*");
			isstartind = false;
			lrVertices[cind] = CHECKEDVALUE;
			board.vertices[cind].debugLRstatus = CHECKEDVALUE;
			// TODO: if search starts in a "broken" vertex, the algorithm
			// believes that it is connected
			if ((cind == ind) || (lrPlayerPresent[cind]) || (!lrOpponentPresent[cind])) {
				for (j = cpos; j < 6; j++) {
					// System.out.printf(".");
					nextind = board.neighborVertexVertex[cind][j];
					nextedge = board.neighborVertexEdge[cind][j];
					if (nextind == -1)
						continue;
					if (s[OFS_EDGES + nextedge] != EDGE_OCCUPIED + pl)
						continue;
					if (lrVertices[nextind] != UNCHECKEDVALUE)
						continue;
					foundnext = true;
					lrStack[lrStacklen] = cind;
					lrStackPos[lrStacklen] = j + 1;
					lrStacklen++;
					if (lrStacklen > maxlen) {
						maxlen = lrStacklen;
						maxStartInd = nextind;
					}
					if ((CHECKEDVALUE == LR_CHECKED3) && (maxlen == returnvalues[0])) {
						for (i = 0; i < lrStacklen; i++) {
							board.vertices[lrStack[i]].debugLRstatus = CHECKEDVALUE;
							// TODO: implement this correctly
							// edges[neighborVertexEdge[lrStack[i]][lrStackPos[i]-1]].isPartOfLongestRoad
							// = true;
						}
						board.vertices[nextind].debugLRstatus = CHECKEDVALUE;
						break;
					}
					break;
				}
			}
			if (foundnext) {
				cind = nextind;
				cpos = 0;
			} else {
				if (lrStacklen == 0)
					break;
				lrStacklen--;
				cind = lrStack[lrStacklen];
				cpos = lrStackPos[lrStacklen];
			}
			// System.out.printf("x");
		} while (lrStacklen >= 0);
		returnvalues[0] = maxlen;
		returnvalues[1] = maxStartInd;

		lrStack = null;
		lrStackPos = null;
	}

	private void recalcLongestRoad(int[] s, int pl) {
		int ind, cind, cpos, j, k;
		int[] lrVertices = new int[N_VERTICES];
		boolean[] lrOpponentPresent = new boolean[N_VERTICES];
		boolean[] lrPlayerPresent = new boolean[N_VERTICES];
		int[] returnvalues = new int[2];
		int maxlen, maxStartInd = 0;
		int val;
		// int pl;

		for (ind = 0; ind < N_VERTICES; ind++)
			board.vertices[ind].debugLRstatus = 0;
		for (ind = 0; ind < N_EDGES; ind++)
			board.edges[ind].isPartOfLongestRoad = false;
		// for (pl = 0; pl < NPLAYERS; pl++)
		{
			for (ind = 0; ind < N_VERTICES; ind++) {
				// System.out.printf("/%d/",ind);

				lrVertices[ind] = LR_EMPTY;
				lrOpponentPresent[ind] = false;
				val = s[OFS_VERTICES + ind];
				if ((val == VERTEX_EMPTY) || (val == VERTEX_TOOCLOSE))
					;
				else if ((val == VERTEX_HASSETTLEMENT + pl) || (val == VERTEX_HASCITY + pl))
					lrPlayerPresent[ind] = true;
				else
					lrOpponentPresent[ind] = true;
				for (j = 0; j < 6; j++) {
					val = board.neighborVertexEdge[ind][j];
					if ((val != -1) && (s[OFS_EDGES + val] == EDGE_OCCUPIED + pl)) // player has road
						lrVertices[ind] = LR_UNCHECKED;
					// else if ((val!=-1) && (s[OFS_EDGES+val] != EDGE_EMPTY))
					// //opponent has road
					// lrOpponentPresent[ind] = true;
				}
			}

			// TODO!!! 6-length cycles counts only as a 5 !!!
			maxlen = 0;
			for (ind = 0; ind < N_VERTICES; ind++) {
				if (lrVertices[ind] != LR_UNCHECKED)
					continue;
				lrDepthFirstSearch(s, pl, ind, lrVertices, lrOpponentPresent, lrPlayerPresent, LR_UNCHECKED,
						LR_CHECKED1, returnvalues);
				lrDepthFirstSearch(s, pl, returnvalues[1], lrVertices, lrOpponentPresent, lrPlayerPresent, LR_CHECKED1,
						LR_CHECKED2, returnvalues);
				if (maxlen < returnvalues[0]) {
					maxlen = returnvalues[0];
					maxStartInd = returnvalues[1];
				}
			}
			// if (maxlen>0)
			// vertices[maxStartInd].isPartOfLongestRoad = LR_MAXROUTE;
			// maxlen = returnvalues[0];

			// the purpose of this call to DFS is to mark the longest road.
			lrDepthFirstSearch(s, pl, maxStartInd, lrVertices, lrOpponentPresent, lrPlayerPresent, LR_CHECKED2,
					LR_CHECKED3, returnvalues);
			s[OFS_PLAYERDATA[pl] + OFS_PLAYERSLONGESTROAD] = maxlen;
		}

		int maxpl = s[OFS_LONGESTROAD_AT]; // current player with longest road;
		if (maxpl != -1)
			maxlen = s[OFS_PLAYERDATA[maxpl] + OFS_PLAYERSLONGESTROAD];
		else
			maxlen = 0;
		for (pl = 0; pl < NPLAYERS; pl++) {
			if (s[OFS_PLAYERDATA[pl] + OFS_PLAYERSLONGESTROAD] > maxlen) {
				maxlen = s[OFS_PLAYERDATA[pl] + OFS_PLAYERSLONGESTROAD];
				maxpl = pl;
			}
		}
		if (maxlen >= 5) {
			s[OFS_LONGESTROAD_AT] = maxpl;
		}
		lrVertices = null;
		lrOpponentPresent = null;
		lrPlayerPresent = null;
		returnvalues = null;
	}

	private void recalcLargestArmy() {
		int pl;
		int largestpl = state[OFS_LARGESTARMY_AT];
		int current;

		for (pl = 0; pl < NPLAYERS; pl++) {
			current = state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_KNIGHT];
			if ((largestpl == -1) && (current >= 3))
				state[OFS_LARGESTARMY_AT] = pl;
			if ((largestpl != -1) && (current > state[OFS_PLAYERDATA[largestpl] + OFS_USEDCARDS + CARD_KNIGHT]))
				state[OFS_LARGESTARMY_AT] = pl;
		}
	}
	
	/**
	 * @param array
	 */
	private void shuffleArray(int[] array) {
		int index, temp;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = array.length - 1; i > 0; i--) {
			index = random.nextInt(i + 1);
			temp = array[index];
			array[index] = array[i];
			array[i] = temp;
		}
	}
	
	//display utilities
	public static void setBoardSize(int screenWidth, int screenHeight, double scale) {
		board.screenWidth = screenWidth;
		board.screenHeight = screenHeight;
		// scale = Math.min(screenHeight / 8.0, screenWidth / 8.0);
		board.scale = scale;
	}
	
	public void gameTick(){
		int[] action = sampleNextAction();
		performAction(action, true);
	}
	
	
	public static void main(String[] args) {
		Timer t = new Timer();
		long d = 0;
		double b = 0;
		int nGames = 1000000;
		for(int i =0; i < nGames; i++) {
			board.InitBoard();// a new board every game to test more scenarios, but in this case the timing may be misleading
			CatanFactoredBelief belief = new CatanFactoredBelief(NPLAYERS);
			CatanConfig config = new CatanConfig();
			CatanWithBelief game = new CatanWithBelief(config, belief);
			while(!game.isTerminal()) {
				game.gameTick();
			}
			d += depth;
			b += (double)breadth/depth;
			breadth = 0;
			depth = 0;
			if(game.getWinner() == -1)
				System.err.println("winner not recorded correctly");
			if(i % 10000 == 0)
				System.out.println();
			if(i % 100 == 0)
				System.out.print(".");
		}
		System.out.println(t.elapsed());
		System.out.println("Done");
		System.out.println("Depth: " + (double)d/nGames);
		System.out.println("branch: " + (double)b/nGames);
	}

	@Override
	public Game copy() {
		CatanFactoredBelief bel = null;
		if(belief != null)
			bel = belief.copy();
		CatanWithBelief ret = new CatanWithBelief(this.getState(), this.config.copy(), bel);
		ret.cardSequence = this.cardSequence.clone();
		return ret;
	}
	
	public CatanFactoredBelief getBelief() {
		return belief;
	}
	
}