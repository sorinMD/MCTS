package mcts.tree.update;

import java.util.ArrayList;
import java.util.HashSet;

import mcts.game.Game;
import mcts.tree.node.TreeNode;

/**
 * Update rule with afterstates.
 * 
 * @author sorinMD
 *
 */
public class StateUpdater extends UpdatePolicy{
    public StateUpdater() {}
    
	@Override
	public void update(ArrayList<TreeNode> visited, Game game) {
		//hashset to avoid updating nodes multiple times due to possible cyclic behaviour
		HashSet<TreeNode> v = new HashSet<TreeNode>(visited);
		//update all visited nodes
		v.forEach(n -> n.update(game.getWinner()));
		
	}

}
