package mcts.utils;

import java.util.ArrayList;
import java.util.Arrays;

public class Utils {

	
    public static ArrayList<Double> createActMask(ArrayList<int[]> stateActions, ArrayList<int[]> beliefActions){
    	ArrayList<Double> mask = new ArrayList<>(beliefActions.size());
    	
    	//this code works well and is faster if the order in which actions are listed is consistent...but this is not the case in Catan due to the discard action
//    	int idx = 0;
//    	for(int[] act : stateActions) {
//    		while(idx < beliefActions.size()) {
//    			if(Arrays.equals(act, beliefActions.get(idx))) {
//    				mask.add(1.0);
//    				idx++;
//    				break;
//    			}
//				mask.add(0.0);
//				idx++;
//    		}
//    	}
//    	//pad the array to the correct size
//    	while(mask.size() != beliefActions.size())
//    		mask.add(0.0);
    	
    	//this code is the safer but slightly slower option. It guarantees all actions are checked even if actions are not listed in the same order
    	for(int i = 0; i < beliefActions.size(); i++) {
    		mask.add(0.0);
    		int[] action = beliefActions.get(i);
    		for(int[] act : stateActions) {
    			if(Arrays.equals(act, action)) {
    				mask.set(i, 1.0);
    				break;
    			}
    		}
    	}
    	
    	return mask;
    }
}
