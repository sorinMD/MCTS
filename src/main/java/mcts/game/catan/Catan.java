package mcts.game.catan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

import mcts.game.Game;
import mcts.game.catan.typepdf.HumanActionTypePdf;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;
import mcts.utils.Options;
import mcts.utils.Timer;
import mcts.tree.node.ChanceNode;

/**
 * Fast fully-observable Settlers of Catan game model implementation.
 * 
 * Part of the state representation and logic courtesy of Pieter Spronck:
 * http://www.spronck.net/research.html
 * 
 * Several improvements and additions have been made:
 * <ul>
 * <li>Added planning for the discard action instead of random decision (if the legal set is not too large)
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
 * </ul>
 * 
 * @author sorinMD
 *
 */
public class Catan implements Game, GameStateConstants {

	protected CatanConfig config;	
    protected int[] state;
	
	public static long breadth = 0;
	public static long depth = 0;
	
	/**
	 * The development cards
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
	 * Contains all the info that doesn't change during a game, hence it is shared between all instances
	 * TODO: this is fine for a single local game, but it will be problematic to have it static on the server side if multiple games are run in parallel.
	 */
	public static Board board = new Board();

	/**
	 * Stub constructor to allow for a simple extension by {@link CatanWithBelief}
	 * Note: the child class should do all the initialisation
	 */
	protected Catan() {}
	/**
	 * A new game object with the provided state, but assumes an existing board.
	 * To initialise the board call {@link #initBoard()}
	 * 
	 * @param state
	 *            the game state
	 */
	public Catan(int[] state, CatanConfig config) {
		this.config = config;
		this.state = state;
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
		recalcScores();
	}

	/**
	 * A new game, but assumes an existing board. To initialise the board call {@link #initBoard()}
	 */
	public Catan(CatanConfig config) {
		this.config = config;
		state = new int[STATESIZE];
		shuffleArray(cardSequence);
		state[OFS_DEVCARDS_LEFT + CARD_KNIGHT] = 14;
		state[OFS_DEVCARDS_LEFT + CARD_ONEPOINT] = 5;
		state[OFS_DEVCARDS_LEFT + CARD_FREEROAD] = 2;
		state[OFS_DEVCARDS_LEFT + CARD_FREERESOURCE] = 2;
		state[OFS_DEVCARDS_LEFT + CARD_MONOPOLY] = 2;
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
	
	@Override
	public int[] getState() {
		return state.clone();
	}

	@Override
	public int getWinner() {
		//only current player can win
		int pl = getCurrentPlayer();
		if (state[OFS_PLAYERDATA[pl] + OFS_SCORE] >= 10)
				return pl;
		return -1;
	}

	@Override
	public boolean isTerminal() {
		int fsmlevel = state[OFS_FSMLEVEL];
		return state[OFS_FSMSTATE + fsmlevel] == S_FINISHED;
	}

	@Override
	public int getCurrentPlayer() {
		int fsmlevel = state[OFS_FSMLEVEL];
		return state[OFS_FSMPLAYER + fsmlevel];
	}

	@Override
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
						}
					}
				}
			} else if (fsmstate == S_NORMAL) {
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD]--;
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY]--;
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT]--;
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP]--;
			}

			break;
		case A_BUILDCITY:
			state[OFS_VERTICES + a[1]] = VERTEX_HASCITY + pl;
			state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS]--;
			state[OFS_PLAYERDATA[pl] + OFS_NCITIES]++;

			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] -= 3;
			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] -= 2;
			break;
		case A_BUILDROAD:
			state[OFS_LASTVERTEX] = 0;//clear the last free settlement location;
			state[OFS_EDGES + a[1]] = EDGE_OCCUPIED + pl;
			state[OFS_PLAYERDATA[pl] + OFS_NROADS]++;
			if (fsmstate == S_NORMAL) {
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD]--;
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY]--;
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
								state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + board.hextiles[ind].yields()]++;
							}
							// production for city
							if ((k >= VERTEX_HASCITY) && (k < VERTEX_HASCITY + NPLAYERS)) {
								pl = k - VERTEX_HASCITY;
								state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + board.hextiles[ind].yields()] += 2;
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
			state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = 0;
			state[OFS_DICE] = 0;
			state[OFS_NUMBER_OF_OFFERS] = 0;
			break;
        case A_PORTTRADE:
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[2]] -= a[1];
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[4]] += a[3];
            break;
        case A_BUYCARD:
        	state[OFS_NATURE_MOVE] = 1;//dealing a card is a non-deterministic action that always follows this one
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT]--;
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP]--;                    
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE]--;
        	break;
        case A_DEAL_DEVCARD:
            val = a[1];
            if (val==CARD_ONEPOINT)
                state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + val]++;
            else
                state[OFS_PLAYERDATA[pl] + OFS_NEWCARDS + val]++;
            state[OFS_DEVCARDS_LEFT + val]--;
            state[OFS_NCARDSGONE] ++;
            state[OFS_NATURE_MOVE] = 0;
            break;
            
		case A_PLAYCARD_KNIGHT:
			state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = 1;
			state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT]--;
			state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_KNIGHT]++;
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
			state[OFS_PLAYERDATA[state[OFS_VICTIM]] + OFS_RESOURCES + a[1]]--;
			state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[1]]++;
			state[OFS_NATURE_MOVE] = 0;
			state[OFS_VICTIM] = 0;
			break;
        case A_PLAYCARD_MONOPOLY:
            state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = 1;
            state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY]--;
            state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_MONOPOLY]++;
			state[OFS_MONO_RSS_CHOICE] = a[1];
			state[OFS_NATURE_MOVE] = 1; //a fake chance node required to synchronise with the belief version of the game
            break;
            
        case A_CHOOSE_MONO_TOTALS:
        	int choice = state[OFS_MONO_RSS_CHOICE];
            for (ind = 0; ind<NPLAYERS; ind++)
            {
                if (ind==pl)
                    continue;
                state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + choice] += state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice];                    
                state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice] = 0;
            }
			//resetting the choice. Even if default is equal to sheep, we use nature move offset and state transition to guide the state flow
			state[OFS_MONO_RSS_CHOICE] = 0;
			state[OFS_NATURE_MOVE] = 0;
        	break;
        case A_PLAYCARD_FREEROAD:
            state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = 1;
            state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD]--;
            state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_FREEROAD]++;
            break;
        case A_PLAYCARD_FREERESOURCE:
            state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] = 1;
            state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE]--;
            state[OFS_PLAYERDATA[pl] + OFS_USEDCARDS + CARD_FREERESOURCE]++;
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[1]] ++;
            state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[2]] ++;
            break;
		case A_PAYTAX:
			//the discard resources are specified (both types and amounts)
			for (i = 0; i < NRESOURCES; i++) {
				state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i] -= a[i + 1];
			}
			break;
        case A_TRADE:
        	state[OFS_NUMBER_OF_OFFERS]++;//action trade is composed of offer and accept actions
        	//execute the trade by swapping the resources;
        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[4]] -= a[3];
        	if(a[5] > -1)
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[6]] -= a[5];
        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[8]] += a[7];
        	if(a[9] > -1)
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[10]] += a[9];
        	
        	//for opponent 
        	state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[4]] += a[3];
        	if(a[5] > -1)
        		state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[6]] += a[5];
        	state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[8]] -= a[7];
        	if(a[9] > -1)
        		state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[10]] -= a[9];
        	
        	//check if any player has negative resources and report the problem;
        	if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[4]] < 0 || state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + a[6]] < 0 ||
        			state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[8]] < 0 || state[OFS_PLAYERDATA[a[2]] + OFS_RESOURCES + a[10]] < 0)
        		System.err.println("negative rss");
        	break;
        case A_ACCEPT:
        	//same as above, only that we need to look into the currentOffer and the initiator field in bl when executing trade;
        	state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 4]] -= state[OFS_CURRENT_OFFER + 3];
        	if(state[OFS_CURRENT_OFFER + 5] > -1)
        		state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES +state[OFS_CURRENT_OFFER + 6]] -= state[OFS_CURRENT_OFFER + 5];
        	state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 8]] += state[OFS_CURRENT_OFFER + 7];
        	if(state[OFS_CURRENT_OFFER + 9] > -1)
        		state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 10]] += state[OFS_CURRENT_OFFER + 9];
        	
        	//for the accepting player
        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 4]] += state[OFS_CURRENT_OFFER + 3];
        	if(state[OFS_CURRENT_OFFER + 5] > -1)
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 6]] += state[OFS_CURRENT_OFFER + 5];
        	state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 8]] -= state[OFS_CURRENT_OFFER + 7];
        	if(state[OFS_CURRENT_OFFER + 9] > -1)
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 10]] -= state[OFS_CURRENT_OFFER + 9];
        	
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
		}
        stateTransition(a);
	}

	@Override
	public Options listPossiblities(boolean sample) {
		int fsmlevel = state[OFS_FSMLEVEL];
		int fsmstate = state[OFS_FSMSTATE + fsmlevel];
		int pl = state[OFS_FSMPLAYER + fsmlevel];
		ArrayList<int[]> options = new ArrayList<>();
		
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
			listRoadPossibilities(options);
			break;
		case S_PAYTAX:
			listDiscardPossiblities(options,sample);
			break;
		case S_ROBBERAT7:
			listRobberPossibilities(options, A_PLACEROBBER);
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
			listMonopolyTotals(options);
			break;
		}
		Options opts = new Options(options, null);
		return opts;
	}
	
	@Override
	public TreeNode generateNode() {
		int fsmlevel = state[OFS_FSMLEVEL];
		int fsmstate = state[OFS_FSMSTATE + fsmlevel];
		if(fsmstate == S_PAYTAX){
			int pl = state[OFS_FSMPLAYER + fsmlevel];
			int val = 0;
			for (int i = 0; i < NRESOURCES; i++)
				val += state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i];
			val = val / 2;
			if(val <= config.N_MAX_DISCARD)
				return new StandardNode(getState(), null, isTerminal(), getCurrentPlayer());
			else
				return new ChanceNode(getState(), null, isTerminal(), getCurrentPlayer());
		}
		if(state[OFS_NATURE_MOVE] == 0)
			return new StandardNode(getState(), null, isTerminal(), getCurrentPlayer());
		else
			return new ChanceNode(getState(), null, isTerminal(), getCurrentPlayer());
	}

	@Override
	public Game copy() {
		return new Catan(getState(),config);
	}

	@Override
	public int[] sampleNextAction() {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int fsmlevel    = state[OFS_FSMLEVEL];
        int fsmstate    = state[OFS_FSMSTATE+fsmlevel];
        int val = -1;
        Options opts = listPossiblities(true);
        ArrayList<int[]> options = opts.getOptions();
        
        if(/*state[OFS_NATURE_MOVE] != 1 &&*/ options.size() >1){
        	depth ++;
        	breadth += options.size();
        }
        
		if(state[OFS_NATURE_MOVE] == 1){
			if(fsmstate == S_DICE_RESULT){
				val = rnd.nextInt(6) + rnd.nextInt(6) + 2;
				return Actions.newAction(A_CHOOSE_DICE, val);
			}else if(fsmstate == S_STEALING){
				val = selectRandomResourceInHand(state[OFS_VICTIM]);
				return Actions.newAction(A_CHOOSE_RESOURCE, val);
			}else if(fsmstate == S_BUYCARD){
				val = cardSequence[state[OFS_NCARDSGONE]];
				return Actions.newAction(A_DEAL_DEVCARD, val);
			}else if(fsmstate == S_MONOPOLY_EFFECT) {
				return opts.getOptions().get(0);//fake chance node, there should be a single option
			}
		}else{
			return options.get(rnd.nextInt(options.size()));
		}
		//iterate and choose the corresponding one
		if(val != -1){
			for(int i=0; i < options.size(); i++){
				if(options.get(i)[1] == val)
					return options.get(i);
			}
		}
		System.err.println("Couldn't find option in the list when sampling next action ");
		return options.get(rnd.nextInt(options.size()));
	}
	
	@Override
	public int sampleNextActionIndex() {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int fsmlevel    = state[OFS_FSMLEVEL];
        int fsmstate    = state[OFS_FSMSTATE+fsmlevel];
        int val = -1;
        Options opts = listPossiblities(true);
        ArrayList<int[]> options = opts.getOptions();
		if(state[OFS_NATURE_MOVE] == 1){
			if(fsmstate == S_DICE_RESULT){
				val = rnd.nextInt(6) + rnd.nextInt(6) + 2;
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
		//iterate and choose the corresponding one
		if(val != -1){
			for(int i=0; i < options.size(); i++){
				if(options.get(i)[1] == val)
					return i;
			}
		}
		System.err.println("Couldn't find option in the list when sampling next index");
		return rnd.nextInt(options.size());
	}

	public void recalcScores() {
		int[] auxScoreArray = new int[4];
		int pl;
		for (pl = 0; pl < NPLAYERS; pl++) {
			auxScoreArray[pl] = 0;
			auxScoreArray[pl] += state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS];
			auxScoreArray[pl] += state[OFS_PLAYERDATA[pl] + OFS_NCITIES] * 2;
			auxScoreArray[pl] += state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_ONEPOINT];
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
			if (a[0] == A_THROWDICE) {
				state[OFS_FSMSTATE + fsmlevel] = S_DICE_RESULT;
			}else if((a[0] == A_PLAYCARD_KNIGHT) && a[2] != -1){
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
				int val = 0;
				for (int i = 0; i < NRESOURCES; i++)
					val += state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i];
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
					val = 0;
					for (int i = 0; i < NRESOURCES; i++)
						val += state[OFS_PLAYERDATA[j] + OFS_RESOURCES + i];
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
				int val = 0;
				for (int i = 0; i < NRESOURCES; i++)
					val += state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i];
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
			ArrayList<int[]> roadOpt = new ArrayList<int[]>();
			listRoadPossibilities(roadOpt);
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
		}
		
		recalcScores();
		//do not allow winning in the stealing chance event so the state transition will be synced with the belief version of the game
		if (getWinner() != -1 && state[OFS_FSMSTATE + fsmlevel] != S_STEALING) { 
			state[OFS_FSMSTATE + fsmlevel] = S_FINISHED;
		}
	}

	//private listing actions methods//
    
	private void listInitSettlementPossibilities(ArrayList<int[]> options) {
		int i;
		for (i = 0; i < N_VERTICES; i++) {
			if (state[OFS_VERTICES + i] == 0) {
				options.add(Actions.newAction(A_BUILDSETTLEMENT, i));
			}
		}
	}
	
	private void listInitRoadPossibilities(ArrayList<int[]> options) {
		int i, ind;
		int lastvertex = state[OFS_LASTVERTEX];
		for (i = 0; i < 6; i++) {
			ind = board.neighborVertexEdge[lastvertex][i];
			if ((ind != -1) && (state[OFS_EDGES + ind] == 0)) {
				options.add(Actions.newAction(A_BUILDROAD, ind));
			}
		}
	}
	
	private void listBeforeRollPossibilities(ArrayList<int[]> options, int pl) {
		options.add(Actions.newAction(A_THROWDICE));
		if ((state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] >= 1)
				&& (state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] == 0)) {
			listRobberPossibilities(options, A_PLAYCARD_KNIGHT);
		}
	}
	
	private void listDiceResultPossibilities(ArrayList<int[]> options){
		for(int i = 2; i <= 12; i++){
			options.add(Actions.newAction(A_CHOOSE_DICE, i));
		}
	}
	
	private void listNormalPossibilities(ArrayList<int[]> options, boolean sample){
		
		if(sample && config.SAMPLE_FROM_DISTRIBUTION_OVER_TYPES_IN_ROLLOUTS){
			listNormalPossAndSampleType(options);
			return;
		}
        listPaidRoadPossibilities(options);
        listPaidSettlementPossibilities(options);
        listCityPossibilities(options);
        listBuyDevCardPossibility(options);
        listBankTradePossibilities(options);
        listDevCardPossibilities(options);
        if(config.TRADES && state[OFS_NUMBER_OF_OFFERS] < config.OFFERS_LIMIT){
        	if(config.ALLOW_SAMPLING_IN_NORMAL_STATE){
        		listTradePossibilities(options,sample);
        	}else{
        		listTradePossibilities(options,false);
        	}
        }
        options.add(Actions.newAction(A_ENDTURN));
	}
	
	/**
	 * Chooses uniformly at random the action type to execute next and only
	 * lists the normal possibilities of the chosen type.
	 * 
	 * TODO: add option for weights on action types such that some would be executed more
	 * often in the roll-outs (i.e. basic player types)
	 */
	private void listNormalPossAndSampleType(ArrayList<int[]> options){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
		int i,j;
        ArrayList<Integer> actionTypes = new ArrayList<>();
		ArrayList<int[]> roadOptions = new ArrayList<>();
		ArrayList<int[]> settlementOptions = new ArrayList<>();
		ArrayList<int[]> cityOptions = new ArrayList<>();
		ArrayList<int[]> buyCardOptions = new ArrayList<>();
		ArrayList<int[]> portTradeOptions = new ArrayList<>();
        
        //can always end turn in the normal state
		actionTypes.add(A_ENDTURN);
		
		int[] playersWithResources = new int[NPLAYERS];
		for (i = 0; i < NPLAYERS; i++) {
			for (j = 0; j < NRESOURCES; j++)
				playersWithResources[i] += state[OFS_PLAYERDATA[i] + OFS_RESOURCES + j];
				
		}
		//are there any other players with rss
		ArrayList<Integer> oppWithRss = new ArrayList<>();
		for (i = 0; i < NPLAYERS; i++) {
			if(i==pl)
				continue;
			if(playersWithResources[i] > 0)
				oppWithRss.add(i);
		}
		
		if(playersWithResources[pl] > 0){
			//can only do trades if any of the opponents have resources
			if(oppWithRss.size() != 0)
				actionTypes.add(A_TRADE);
			listPaidRoadPossibilities(roadOptions);
			listPaidSettlementPossibilities(settlementOptions);
			listCityPossibilities(cityOptions);
			listBuyDevCardPossibility(buyCardOptions);
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
				actionTypes.add(A_PORTTRADE);
			}
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
				ArrayList<int[]> roadOpt = new ArrayList<int[]>();
				listRoadPossibilities(roadOpt);
				if (roadOpt.size() > 0)
					actionTypes.add(A_PLAYCARD_FREEROAD);
			}
		}
		
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int chosenType = A_ENDTURN;
		if(config.rolloutTypeDist instanceof HumanActionTypePdf) {
			Map<Integer,Double> dist = config.rolloutTypeDist.getDist(actionTypes);
			double totalWeight = 0.0;
			for (Entry<Integer,Double> e : dist.entrySet()){
			    totalWeight += e.getValue();
			}
			double random = rnd.nextDouble() * totalWeight;
			for (Entry<Integer,Double> e : dist.entrySet()){
				random -= e.getValue();
			    if (random <= 0.0) {
			        chosenType = e.getKey();
			        break;
			    }
			}
		}else {
			//the alternative is uniform, so no need to do weighted random sampling
			chosenType = actionTypes.get(rnd.nextInt(actionTypes.size()));
		}
		
		switch (chosenType) {
		case A_ENDTURN:
			options.add(Actions.newAction(A_ENDTURN));
			break;
		case A_TRADE:
			listTradePossibilities(options, true);
			break;
		case A_BUILDROAD:
			options.addAll(roadOptions);
			break;
		case A_BUILDSETTLEMENT:
			options.addAll(settlementOptions);
			break;
		case A_BUILDCITY:
			options.addAll(cityOptions);
			break;
		case A_BUYCARD:
			options.addAll(buyCardOptions);
			break;
		case A_PORTTRADE:
			options.addAll(portTradeOptions);
			break;
		case A_PLAYCARD_KNIGHT:
			listRobberPossibilities(options, A_PLAYCARD_KNIGHT);
			break;
		case A_PLAYCARD_FREEROAD:
			options.add(Actions.newAction(A_PLAYCARD_FREEROAD));
			break;
		case A_PLAYCARD_MONOPOLY:
			listMonopolyPossibilities(options);
			break;
		case A_PLAYCARD_FREERESOURCE:
			listFreeResourcePossibilities(options);
			break;
		}
	}
	
	public ArrayList<Integer> listNormalActionTypes(){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
		int i,j;
        ArrayList<Integer> actionTypes = new ArrayList<>();
		ArrayList<int[]> roadOptions = new ArrayList<>();
		ArrayList<int[]> settlementOptions = new ArrayList<>();
		ArrayList<int[]> cityOptions = new ArrayList<>();
		ArrayList<int[]> buyCardOptions = new ArrayList<>();
		ArrayList<int[]> portTradeOptions = new ArrayList<>();
        
        //can always end turn in the normal state
		actionTypes.add(A_ENDTURN);
		
		int[] playersWithResources = new int[NPLAYERS];
		for (i = 0; i < NPLAYERS; i++) {
			for (j = 0; j < NRESOURCES; j++)
				playersWithResources[i] += state[OFS_PLAYERDATA[i] + OFS_RESOURCES + j];
				
		}
		//are there any other players with rss
		ArrayList<Integer> oppWithRss = new ArrayList<>();
		for (i = 0; i < NPLAYERS; i++) {
			if(i==pl)
				continue;
			if(playersWithResources[i] > 0)
				oppWithRss.add(i);
		}
		
		if(playersWithResources[pl] > 0){
			//can only do trades if any of the opponents have resources
			if(oppWithRss.size() != 0)
				actionTypes.add(A_TRADE);
			listPaidRoadPossibilities(roadOptions);
			listPaidSettlementPossibilities(settlementOptions);
			listCityPossibilities(cityOptions);
			listBuyDevCardPossibility(buyCardOptions);
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
				actionTypes.add(A_PORTTRADE);
			}
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
				ArrayList<int[]> roadOpt = new ArrayList<int[]>();
				listRoadPossibilities(roadOpt);
				if (roadOpt.size() > 0)
					actionTypes.add(A_PLAYCARD_FREEROAD);
			}
		}
		return actionTypes;
	}
	
	
	private void listPaidRoadPossibilities(ArrayList<int[]> options){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        
		if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] >= 1)
				&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] >= 1)
				&& (state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15)) {
			listRoadPossibilities(options);
		}
	}
	
	private void listPaidSettlementPossibilities(ArrayList<int[]> options){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        int ind;
        boolean hasneighbor;
        
		if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WOOD] >= 1)
				&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_CLAY] >= 1)
				&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] >= 1)
				&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] >= 1)
				&& (state[OFS_PLAYERDATA[pl] + OFS_NSETTLEMENTS] < 5)) {
			for (int i = 0; i < N_VERTICES; i++) {
				if (state[OFS_VERTICES + i] == VERTEX_EMPTY) {
					hasneighbor = false;
					for (int j = 0; j < 6; j++) {
						ind = board.neighborVertexEdge[i][j];
						if ((ind != -1) && (state[OFS_EDGES + ind] == EDGE_OCCUPIED + pl))
							hasneighbor = true;
					}
					if (hasneighbor)
						options.add(Actions.newAction(A_BUILDSETTLEMENT, i));
				}
			}
		}
	}
	
	private void listCityPossibilities(ArrayList<int[]> options){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        
		if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] >= 3)
				&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] >= 2)
				&& (state[OFS_PLAYERDATA[pl] + OFS_NCITIES] < 4)) {
			for (int i = 0; i < N_VERTICES; i++) {
				if (state[OFS_VERTICES + i] == VERTEX_HASSETTLEMENT + pl) {
					options.add(Actions.newAction(A_BUILDCITY, i));
				}
			}
		}
	}
	
	private void listBuyDevCardPossibility(ArrayList<int[]> options){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        
        if ((state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_STONE] >= 1)
				&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_WHEAT] >= 1)
				&& (state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + RES_SHEEP] >= 1) 
				&& state[OFS_NCARDSGONE] < NCARDS) {
			options.add(Actions.newAction(A_BUYCARD));
		}
	}
	
	private void listBankTradePossibilities(ArrayList<int[]> options){
		int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        
        for (int i=0; i<NRESOURCES; i++)
        {
            for (int j = 0; j<NRESOURCES; j++)
            {
                if (i==j) continue;
                // specific port
                if (    (state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+i] >= 2) &&
                        (state[OFS_PLAYERDATA[pl]+OFS_ACCESSTOPORT+i] == 1) )
                	options.add(Actions.newAction(A_PORTTRADE, 2, i, 1, j));
                // misc port
                else if (    (state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+i] >= 3) &&
                        (state[OFS_PLAYERDATA[pl]+OFS_ACCESSTOPORT+NRESOURCES] == 1) )
                	options.add(Actions.newAction(A_PORTTRADE, 3, i, 1, j));
                // bank
                else if (   (state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+i] >= 4) )
                	options.add(Actions.newAction(A_PORTTRADE, 4, i, 1, j));
            }
        }
        
	}
	
	private void listRoadPossibilities(ArrayList<int[]> options) {
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
							options.add(Actions.newAction(A_BUILDROAD, ind));
					}
				}
			}
		}
	}
	
	/**
	 * Lists the options of where to move the robber and who is the victim, without the action of stealing
	 * @param options
	 * @param action is this robber at 7 or following a played knight?
	 */
	private void listRobberPossibilities(ArrayList<int[]> options, int action) {
		int fsmlevel = state[OFS_FSMLEVEL];
		int pl = state[OFS_FSMPLAYER + fsmlevel];
		int i, j, ind, pl2, val;

		boolean[] adjacentPlayers = new boolean[NPLAYERS];
		boolean[] playersWithResources = new boolean[NPLAYERS];
		Arrays.fill(playersWithResources, false);// make sure it is false
		for (i = 0; i < NPLAYERS; i++) {
			int nrss = 0;
			for (j = 0; j < NRESOURCES; j++)
				nrss += state[OFS_PLAYERDATA[i] + OFS_RESOURCES + j];
			if (nrss > 0) {
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
					options.add(Actions.newAction(action, i, j));
					counter++;
				}
			}
			if(counter == 0)
				options.add(Actions.newAction(action, i, -1));//can still move the robber on the hex, even if there will be no victim
		}
	}
	
	private void listDiscardPossiblities(ArrayList<int[]> options, boolean sample){
		int fsmlevel = state[OFS_FSMLEVEL];
		int pl = state[OFS_FSMPLAYER + fsmlevel];
		int val = 0;
		int ind = 0;
		int i =0;
		for (i = 0; i < NRESOURCES; i++)
			val += state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i];
		val = val / 2;
		boolean random = false;
		if(sample) {
			random = true;
		}else if(val <= config.N_MAX_DISCARD){
			//get the resources
			int[] resources = new int[NRESOURCES];
			ResourceSet set = new ResourceSet();
			for (i = 0; i < NRESOURCES; i++)
				set.add(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i], i);
			List<ResourceSet> discardList = set.getSubsets(val,false);
			for(ResourceSet opt: discardList){
				resources = opt.getResourceArrayClone();
				options.add(Actions.newAction(A_PAYTAX, resources[0], resources[1],resources[2], resources[3], resources[4]));
			}
		}else {
			random = true;
		}
		
		if(random) {
			int[] rssSet = new int[NRESOURCES];
			for (i = 0; i < NRESOURCES; i++)
				rssSet[i] = state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + i];
			int[] action = Actions.newAction(A_PAYTAX);
			for (i = 0; i < val; i++) {
				ind = selectRandomResourceFromSet(rssSet);
				rssSet[ind] --;
				action[ind + 1] +=1;
			}
			options.add(action);
		}
	}
	
	private void listStealingPossiblities(ArrayList<int[]> options, int victim){
		//we know the victim, just list the possible rss as action (part 0 steal rss, 1 = type)
		for (int i = 0; i < NRESOURCES; i++) {
			if(state[OFS_PLAYERDATA[victim] + OFS_RESOURCES + i] > 0)
				options.add(Actions.newAction(A_CHOOSE_RESOURCE, i));
		}
	}
	
	private void listDealDevCardPossibilities(ArrayList<int[]> options){
		for (int i = 0; i < NCARDTYPES; i++) {
			if(state[OFS_DEVCARDS_LEFT + i] > 0)
				options.add(Actions.newAction(A_DEAL_DEVCARD, i));
		}
	}
	
	private void listDevCardPossibilities(ArrayList<int[]> options) {
		int fsmlevel = state[OFS_FSMLEVEL];
		int pl = state[OFS_FSMPLAYER + fsmlevel];

		if (state[OFS_PLAYERDATA[pl] + OFS_HASPLAYEDCARD] != 0)
			return;

		if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREERESOURCE] >= 1) {
			listFreeResourcePossibilities(options);
		}
		if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_MONOPOLY] >= 1) {
			listMonopolyPossibilities(options);
		}
		if ((state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_FREEROAD] >= 1)
				&& (state[OFS_PLAYERDATA[pl] + OFS_NROADS] < 15)) {
			// need to check if the player can place roads first
			ArrayList<int[]> roadOpt = new ArrayList<int[]>();
			listRoadPossibilities(roadOpt);
			if (roadOpt.size() > 0)
				options.add(Actions.newAction(A_PLAYCARD_FREEROAD));
		}
		if (state[OFS_PLAYERDATA[pl] + OFS_OLDCARDS + CARD_KNIGHT] >= 1) {
			listRobberPossibilities(options, A_PLAYCARD_KNIGHT);
		}
	}
	
	private void listMonopolyPossibilities(ArrayList<int[]> options){
		for (int i = 0; i < NRESOURCES; i++)
			options.add(Actions.newAction(A_PLAYCARD_MONOPOLY, i, -1,-1,-1,-1));
	}
	
	private void listMonopolyTotals(ArrayList<int[]> options){
        int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        int choice		= state[OFS_MONO_RSS_CHOICE];
        
		int[] action = Actions.newAction(A_CHOOSE_MONO_TOTALS);
		//include the effects such as what was lost or gained depending on the player number
		int total = 0;
		for (int ind = 0; ind<NPLAYERS; ind++){
			if(ind == pl)
				continue;
			total += state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice];
            action[1 + ind] = state[OFS_PLAYERDATA[ind] + OFS_RESOURCES + choice];                    
        }
		action[1 + pl] = total;
		options.add(action);
	}
	
	private void listFreeResourcePossibilities(ArrayList<int[]> options){
		for (int i = 0; i < NRESOURCES; i++)
			for (int j = i; j < NRESOURCES; j++)
				options.add(Actions.newAction(A_PLAYCARD_FREERESOURCE, i, j));
	}
	
    private void listTradePossibilities(ArrayList<int[]> options, boolean sample){
        int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        int pl2, action, j, i;
        
        if(sample){//if sampling, pick a random legal exchange that can be 1:1, 2:1, 1:2 (the same or conjunctive for the latter two)
        	int[] playersWithResources = new int[NPLAYERS];
    		for (i = 0; i < NPLAYERS; i++) {
    			for (j = 0; j < NRESOURCES; j++)
    				playersWithResources[i] += state[OFS_PLAYERDATA[i] + OFS_RESOURCES + j];
    				
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
    		int giveable;
    		int receiveable;
    		int[] rss;
    		switch (typeOfTrade) {
			case 0:
	        	giveable = selectRandomResourceInHand(pl);
	        	receiveable = selectRandomResourceInHand(pl2);
	        	options.add(Actions.newAction(A_TRADE, pl, pl2, 1, giveable, -1, -1, 1, receiveable, -1, -1));
				break;
			case 1://pl2 gives 2 rss
	        	giveable = selectRandomResourceInHand(pl);
	        	rss = select2RandomResourcesInHand(pl2);
	        	if(rss[0] == rss[1]){
	        		options.add(Actions.newAction(A_TRADE, pl, pl2, 1, giveable, -1, -1, 2, rss[0], -1, -1));
	        	}else{
	        		options.add(Actions.newAction(A_TRADE, pl, pl2, 1, giveable, -1, -1, 1, rss[0], 1,  rss[1]));
	        	}
				break;
			case 2://current pl gives 2 rss
				rss = select2RandomResourcesInHand(pl);
	        	receiveable = selectRandomResourceInHand(pl2);
	        	if(rss[0] == rss[1]){
	        		options.add(Actions.newAction(A_TRADE, pl, pl2, 2, rss[0], -1, -1, 1, receiveable, -1, -1));
	        	}else{
	        		options.add(Actions.newAction(A_TRADE, pl, pl2, 1, rss[0], 1, rss[1], 1, receiveable, -1, -1));
	        	}
				break;
			}
        }else{
	        if(config.NEGOTIATIONS){
	        	action = A_OFFER;
	        }else{
	        	action = A_TRADE;
	        }
	        
	        for(int[] trade : Trades.legalTrades){
	        	//do we have the resources
	        	if(trade[0] <= state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+trade[1]] && trade[2] <= state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+trade[3]]){
	        		//for each opponent
	        		for(pl2 = 0; pl2 < NPLAYERS; pl2++){
	        			if(pl2==pl) continue;
	        			if(trade[4] <= state[OFS_PLAYERDATA[pl2]+OFS_RESOURCES+trade[5]] && trade[6] <= state[OFS_PLAYERDATA[pl2]+OFS_RESOURCES+trade[7]]){
	        				options.add(Actions.newAction(action, pl, pl2, trade[0], trade[1], trade[2], trade[3], trade[4], trade[5], trade[6], trade[7]));
	        				// order: initiator, recipient, (number of res given, type of rss given) x2, (number of rss received, type of rss received) x2
	        			}
	        		}
	        	}
	        }
        }
    }
	
    private void listTradeResponsePossiblities(ArrayList<int[]> options){
        int fsmlevel    = state[OFS_FSMLEVEL];
        int pl          = state[OFS_FSMPLAYER+fsmlevel];
        
    	//due to sampling from belief, accept may not be possible now because either the player that made the offer or the current player doesn't have the rss
        if(state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 8]] >= state[OFS_CURRENT_OFFER + 7] && 
        		state[OFS_PLAYERDATA[pl] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 10]] >= state[OFS_CURRENT_OFFER + 9] &&
        		state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES + state[OFS_CURRENT_OFFER + 4]] >= state[OFS_CURRENT_OFFER + 3] &&
        		state[OFS_PLAYERDATA[state[OFS_CURRENT_OFFER + 1]] + OFS_RESOURCES +state[OFS_CURRENT_OFFER + 6]] >= state[OFS_CURRENT_OFFER + 5]) {
        	options.add(Actions.newAction(A_ACCEPT));
        }
        options.add(Actions.newAction(A_REJECT));
        if(config.ALLOW_COUNTEROFFERS){
            int pl2 = state[OFS_CURRENT_OFFER + 1];
            for(int[] trade : Trades.legalTrades){
            	//do we have the resources
            	if(trade[0] <= state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+trade[1]] && trade[2] <= state[OFS_PLAYERDATA[pl]+OFS_RESOURCES+trade[3]]){
            		//only to the initiator of the current offer
        			if(trade[4] <= state[OFS_PLAYERDATA[pl2]+OFS_RESOURCES+trade[5]] && trade[6] <= state[OFS_PLAYERDATA[pl2]+OFS_RESOURCES+trade[7]]){
        				options.add(Actions.newAction(A_OFFER, pl, pl2, trade[0], trade[1], trade[2], trade[3], trade[4], trade[5], trade[6], trade[7]));
        				// order: initiator, recipient, (number of res given, type of rss given) x2, (number of rss received, type of rss received) x2
        			}
            	}
            }
        }
    }
    
	// other utility methods //
    
	private int selectRandomResourceFromSet(int[] rss) {
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
		Random random = new Random();
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
		int nGames = 10000;
		for(int i =0; i < nGames; i++) {
			board.InitBoard();// a new board every game to test more scenarios, but in this case the timing may be misleading
			CatanConfig config = new CatanConfig();
			Catan game = new Catan(config);
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
		System.out.println("Done");
		System.out.println("Depth: " + (double)d/nGames);
		System.out.println("branch: " + (double)b/nGames);
	}

}
