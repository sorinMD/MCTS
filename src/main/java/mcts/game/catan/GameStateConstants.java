/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package mcts.game.catan;

import java.awt.Color;

/**
 *
 * @author szityu, sorinMD
 */
public interface GameStateConstants extends HexTypeConstants {

    int NPLAYERS = 4;
    
    int N_VERTICES              = 54;
    int N_HEXES                 = 19;
    int N_EDGES                 = 72;
    int N_DEVCARDTYPES          = 5;

    /**
     * The trade action contains 11 parts. All other action descriptions are smaller.
     */
    int ACTIONSIZE = 11;
    
    //board description
    int OFS_STARTING_PLAYER = 0;//the first player to place the first free settlement
    /**the first player to discard, such that it will be consistent with the JSettlers agent using the MCTS implementation**/
    int OFS_DISCARD_FIRST_PL	= OFS_STARTING_PLAYER 	+1;
    int OFS_NATURE_MOVE			= OFS_DISCARD_FIRST_PL 	+1;
    int OFS_TURN                = OFS_NATURE_MOVE 		+1;
    int OFS_FSMLEVEL            = OFS_TURN         		+1;
    int OFS_FSMSTATE            = OFS_FSMLEVEL      	+1;
    int OFS_FSMPLAYER           = OFS_FSMSTATE      	+3;
    int OFS_NCARDSGONE          = OFS_FSMPLAYER     	+3;
    int OFS_DICE          		= OFS_NCARDSGONE    	+1;
    int OFS_ROBBERPLACE         = OFS_DICE      		+1;
    int OFS_LONGESTROAD_AT      = OFS_ROBBERPLACE   	+1;
    int OFS_LARGESTARMY_AT      = OFS_LONGESTROAD_AT   	+1;
    int OFS_LASTVERTEX          = OFS_LARGESTARMY_AT   	+1;
    int OFS_VICTIM				= OFS_LASTVERTEX    	+1;
    int OFS_DEVCARDS_LEFT		= OFS_VICTIM			+1;
    int OFS_CURRENT_OFFER		= OFS_DEVCARDS_LEFT    	+ N_DEVCARDTYPES;
    int OFS_NUMBER_OF_OFFERS    = OFS_CURRENT_OFFER 	+ ACTIONSIZE;
    int OFS_EDGES               = OFS_NUMBER_OF_OFFERS 	+1;
    int OFS_VERTICES            = OFS_EDGES         	+ N_EDGES;
            
    //player information
    int OFS_SCORE               = 0;
    int OFS_NSETTLEMENTS        = OFS_SCORE				+1;
    int OFS_NCITIES             = OFS_NSETTLEMENTS		+1;
    int OFS_NROADS              = OFS_NCITIES			+1;
    int OFS_PLAYERSLONGESTROAD  = OFS_NROADS			+1;
    int OFS_HASPLAYEDCARD       = OFS_PLAYERSLONGESTROAD+1;
    int OFS_RESOURCES           = OFS_HASPLAYEDCARD   	+1;
    int OFS_ACCESSTOPORT        = OFS_RESOURCES     	+NRESOURCES;
    int OFS_USEDCARDS           = OFS_ACCESSTOPORT  	+(NRESOURCES+1);
    int OFS_OLDCARDS            = OFS_USEDCARDS     	+N_DEVCARDTYPES;
    int OFS_NEWCARDS            = OFS_OLDCARDS      	+N_DEVCARDTYPES + 1; //the last index is the total
    int PLAYERSTATESIZE         = OFS_NEWCARDS      	+N_DEVCARDTYPES + 1; //the last index is the total
    
    int[] OFS_PLAYERDATA        = { OFS_VERTICES+N_VERTICES,
                                    OFS_VERTICES+N_VERTICES + PLAYERSTATESIZE,
                                    OFS_VERTICES+N_VERTICES + 2*PLAYERSTATESIZE,
                                    OFS_VERTICES+N_VERTICES + 3*PLAYERSTATESIZE};    
    int STATESIZE = OFS_VERTICES+N_VERTICES + 4*PLAYERSTATESIZE;
    
    int S_GAME                  =  0;
    int S_START                 =  1;//the root node, where nature will choose determinization?
    int S_SETTLEMENT1           =  2;
    int S_ROAD1                 =  3;
    int S_SETTLEMENT2           =  4;
    int S_ROAD2                 =  5;
    int S_BEFOREDICE            =  6;
    int S_NORMAL                =  7;
    int S_BUYCARD               =  8;//nature choice of dealing the bought devcard
    int S_PAYTAX                =  9;
    int S_FREEROAD1             = 10;
    int S_FREEROAD2             = 11;
    int S_ROBBERAT7             = 12;
    int S_NEGOTIATIONS			= 13;
    int S_STEALING 				= 14;//nature choice of stealing resource after move robber or play knight
    int S_DICE_RESULT 			= 15;//nature choice of dice result
    int S_FINISHED              = 100;
            
//    int A_NOTHING               = 0; //there is always a legal option and nothing isn't one
    int A_BUILDSETTLEMENT       = 1;
    int A_BUILDROAD             = 2;
    int A_BUILDCITY             = 3;
    int A_THROWDICE             = 4;
    int A_ENDTURN               = 5;
    int A_PORTTRADE             = 6;
    int A_BUYCARD               = 7;
    int A_PLAYCARD_KNIGHT       = 8;
    int A_PLAYCARD_FREEROAD     = 9;
    int A_PLAYCARD_FREERESOURCE = 10;
    int A_PLAYCARD_MONOPOLY     = 11;
    int A_PAYTAX_RANDOM         = 12;//randomly sample from the options (uniform sampling)
    int A_PAYTAX_SPECIFIED 		= 13;//the discard action is fully specified
    int A_PLACEROBBER           = 14;
    //trade actions below, last one includes the negotiations as one action and just executes the trade
    int A_OFFER           		= 15;
    int A_REJECT				= 16;
    int A_ACCEPT				= 17;
    int A_TRADE					= 18;
    //nature actions (where chances are involved)
    int A_CHOOSE_DET 			= 19;//nature dealing dev cards at the root node?
    int A_CHOOSE_RESOURCE 		= 20;//nature choosing stolen resource (either after knight or robber at 7)
    int A_DEAL_DEVCARD 			= 21;//nature choosing the development card bought based on the card sequence 
    int A_CHOOSE_DICE 			= 22;//nature choosing the dice result based on chances
    
    int VERTEX_EMPTY            = 0;
    int VERTEX_TOOCLOSE         = 1;
    int VERTEX_HASSETTLEMENT    = 2; //+player number
    int VERTEX_HASCITY          = 6; //+player number
    
    int EDGE_EMPTY              = 0;
    int EDGE_OCCUPIED           = 1; //+player number
    
    
    int CARD_KNIGHT             = 0;
    int CARD_ONEPOINT           = 1;
    int CARD_FREEROAD           = 2;
    int CARD_FREERESOURCE       = 3;
    int CARD_MONOPOLY           = 4;
    
    int NCARDTYPES              = 5;
    int NCARDS                  = 25;
    
    String[] resourceNames = {"sheep", "wood", "clay", "wheat", "stone"};
    String[] cardNames = {"knight", "+1 point", "+2 road", "+2 res.", "monopoly"};
    
    public final static Color[] playerColor = 
    {
        Color.BLUE,
        Color.RED,
        Color.WHITE,
        Color.ORANGE,
    };
    
}
