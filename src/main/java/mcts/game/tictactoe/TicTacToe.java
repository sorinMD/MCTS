package mcts.game.tictactoe;

import java.util.concurrent.ThreadLocalRandom;
import mcts.game.Game;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;
import mcts.utils.Options;

/**
 * Simple example game for testing the MCTS algorithm
 * 
 * @author sorinMD
 *
 */
public class TicTacToe implements Game{
	
	public static final int EMPTY = 0;
	public static final int CROSS = 1;
	public static final int NOUGHT = 2;
	
	public static final int PLAYING = 0;
	public static final int DRAW = 1;
	public static final int CROSS_WON = 2;
	public static final int NOUGHT_WON = 3;

	//position 0,1 state and current player; 2,3,4 first row; 5,6,7 second row; 8,9,10 last row
	private int[] state = new int[11]; 
	
	public TicTacToe() {
		initGame();
	}
	
	public TicTacToe(int[] state) {
		this.state = state;
	}
	
	public int[] getState(){
		return state.clone();
	}
	
	public int getWinner(){
		return state[0] - 1;
	}
	
	private void updateState(){
		if(checkForWin(CROSS)){
			state[0] = CROSS_WON;
			return;
		}else if(checkForWin(NOUGHT)){
			state[0] = NOUGHT_WON;
			return;
		}
		state[0] = DRAW;
		for(int i = 2; i < state.length; i++){
			if(state[i] == EMPTY){
				state[0] = PLAYING;
				return;
			}
		}
	}
	
	public boolean isTerminal(){
		if(state[0] == PLAYING)
			return false;
		return true;
	}
	
	public void performAction(int[] action, boolean sample){
		if(state[1] == CROSS){
			state[action[0]] = CROSS;
			state[1] = NOUGHT;//update turn
			
		}else{
			state[action[0]] = NOUGHT;
			state[1] = CROSS; //update turn
		}
		updateState();
	}

	private boolean checkForWin(int seed){
		//check rows
		if(state[2] == state[3] && state[2] == state[4] && state[2] == seed)
			return true;
		if(state[5] == state[6] && state[5] == state[7] && state[5] == seed)
			return true;
		if(state[8] == state[9] && state[8] == state[10] && state[8] == seed)
			return true;
		//check columns
		if(state[2] == state[5] && state[2] == state[8] && state[2] == seed)
			return true;
		if(state[3] == state[6] && state[3] == state[9] && state[3] == seed)
			return true;
		if(state[4] == state[7] && state[4] == state[10] && state[4] == seed)
			return true;
		//check diagonals
		if(state[2] == state[6] && state[2] == state[10] && state[2] == seed)
			return true;
		if(state[4] == state[6] && state[4] == state[8] && state[4] == seed)
			return true;
		return false;
	}

	public Options listPossiblities(boolean quick){
		Options list = new Options();
		for(int i = 2; i < state.length; i++){
			if(state[i] == EMPTY){
				int[] action = new int[1]; //all actions description are of length 1 for this game
				action[0] = i;
				list.put(action, 1.0);
			}
		}
		return list;
	}

	private void initGame() {
		for (int i = 0; i < state.length; i++) {
			state[i] = EMPTY;  // all cells empty
		}
		state[1] = CROSS;//cross starts always
	}
	
	public TicTacToe copy(){
		TicTacToe clone = new TicTacToe(state.clone());
		return clone;
	}
	
	public int getCurrentPlayer(){
		return state[1];
	}

	@Override
	public int[] sampleNextAction() {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		Options options = listPossiblities(true);
		return options.getOptions().get(rnd.nextInt(options.size()));
	}
	
	@Override
	public int sampleNextActionIndex() {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		Options options = listPossiblities(true);
		return rnd.nextInt(options.size());
	}

	@Override
	public TreeNode generateNode() {
		return new StandardNode(getState(), null, isTerminal(), getCurrentPlayer());
	}

	@Override
	public void gameTick() {
		int[] action = sampleNextAction();
		performAction(action, true);
	}
}
