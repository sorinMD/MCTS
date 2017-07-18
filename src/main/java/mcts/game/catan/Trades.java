package mcts.game.catan;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Only used to store all legal trades that the SmartSettlers agent should consider when listing the legal actions that a player can take
 * At the moment we only consider 1:1, 1:2 of the same, 2 of the same:1, 1:2 different and 2 different:1
 * Therefore, a trade has the following format according to {@link ActionList} class:
 * [quant, type, quant, type, quant, type, quant, type] where the first 4 fields are the givable and the others are receiveable. 
 * @author sorinMD
 *
 */
public class Trades implements HexTypeConstants{
	
	/** trades included assume resource types are defined using the SmartSettlers interface i.e. {@link HexTypeConstants}*/
	public static ArrayList<int[]> legalTrades = new ArrayList<int[]>();
	public static final int[] set = {RES_SHEEP, RES_WOOD, RES_CLAY, RES_WHEAT, RES_STONE};
	
	static{
		ArrayList<int[]> temp = new ArrayList<int[]>();
		//do 1:1, 1:2, 2:1 unique resources and the parts for conjunctive trades
		for(int r1 : set){
			for(int r2: set){
				if(r1 != r2){
					temp.add(new int[]{1,r1,1,r2});
					legalTrades.add(new int[]{1,r1,-1,-1,1,r2,-1,-1});//comment out everything else if we want 1:1 only TODO add a flag to decide
					legalTrades.add(new int[]{2,r1,-1,-1,1,r2,-1,-1});
					legalTrades.add(new int[]{1,r1,-1,-1,2,r2,-1,-1});
				}
			}
		}
		//now do the conjunctive 2:1 and 1:2
		for(int[] s : temp){
			for(int r : set){
				if(s[1] != r && s[3] != r){
					legalTrades.add(new int[]{s[0],s[1],s[2],s[3],1,r,-1,-1});
					legalTrades.add(new int[]{1,r,-1,-1,s[0],s[1],s[2],s[3]});
				}
			}
		}
		//don't care about 2:2 as these can be split into 2 trades
	}
	
	
	public static void main(String[] args) {
		System.out.println(legalTrades.size());
		for(int[] set : legalTrades){
			System.out.println(Arrays.toString(set));
		}
	}
	
}
