package mcts.seeder;

import mcts.game.GameFactory;
import mcts.tree.node.TreeNode;

/**
 * Null placeholder seeder when evaluation is not set.
 * 
 * @author sorinMD
 *
 */
public class NullSeedTrigger extends SeedTrigger{

	@Override
	public void addNode(TreeNode node, GameFactory gameFactory) {
		// do nothing
	}

	@Override
	public void cleanUp() {
		// do nothing
	}

}
