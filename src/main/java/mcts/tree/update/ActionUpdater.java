package mcts.tree.update;

import java.util.ArrayList;

import mcts.game.Game;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;
import mcts.utils.HashMapList;

/**
 * Update rule when afterstates are not allowed.
 * 
 * @author sorinMD
 *
 */
public class ActionUpdater extends UpdatePolicy{
	public ActionUpdater() {}
	 
	@Override
	public void update(ArrayList<TreeNode> visited, Game game) {
		HashMapList<TreeNode, TreeNode> pairs = new HashMapList<>();
		
		//update all visited nodes but avoid duplicates
		for(int i = 0; i < visited.size()-1; i++){
			if(pairs.containsKeyValue(visited.get(i),visited.get(i+1))){
				continue;
			}
			if(visited.get(i) instanceof StandardNode)
				((StandardNode)visited.get(i)).update(game.getWinner(), visited.get(i+1).getKey());
			else
				visited.get(i).update(game.getWinner());
			pairs.put(visited.get(i), visited.get(i+1));
		}
		//update the terminal/leaf node
		if(visited.get(visited.size()-1) instanceof StandardNode)
			((StandardNode)visited.get(visited.size()-1)).update(game.getWinner(), null);
		else
			visited.get(visited.size()-1).update(game.getWinner());
		
	}

}
