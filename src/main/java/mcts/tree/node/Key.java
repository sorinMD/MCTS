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
	
	public Key(int[] s) {
		state = s;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Key){
			return Arrays.equals(state, ((Key) obj).state);
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return Arrays.hashCode(state);
	}
	
	@Override
	public String toString() {
		return Arrays.toString(state);
	}
	
}
