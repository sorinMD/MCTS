package mcts.tree.node;

import java.util.Arrays;

/**
 * The key structure used to store nodes in the tree, to handle transpositions.
 * 
 * @author sorinMD
 *
 */
public class Key {
	private final int[] state;
	private final int[] belief;
	
	public Key(int[] s, int[] b) {
		state = s;
		belief = b;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Key){
			return Arrays.equals(state, ((Key) obj).state) && Arrays.equals(belief, ((Key)obj).belief);
		}
		return false;
	}
	
	@Override
	public int hashCode() {
        int result = 1;
        for (int element : state)
            result = 31 * result + element;
        
        if(belief != null) {
            for (int element : belief) {
            	result = 31 * result + element;
            }
        }
        
        return result;
	}
	
	@Override
	public String toString() {
		return Arrays.toString(state);
	}
	
}
