package mcts.seeder;

import mcts.tree.node.TreeNode;

/**
 * Null placeholder seeder when evaluation is not set.
 * 
 * @author sorinMD
 *
 */
public class NullSeedTrigger extends SeedTrigger{

	@Override
	public void addNode(TreeNode node) {
		// do nothing
	}

	@Override
	public void cleanUp() {
		// do nothing
	}

}
