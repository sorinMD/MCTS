package mcts.utils;

import mcts.tree.node.TreeNode;

/**
 * Simple utility needed for returning the result of the tree policy as the node
 * and a flag to indicate whether all siblings have been explored.
 * 
 * @author sorinMD
 *
 */
public class BooleanNodePair {

	private boolean b = false;
	private TreeNode node;

	public BooleanNodePair(boolean b, TreeNode node) {
		this.b = b;
		this.node = node;
	}

	public boolean getBoolean() {
		return b;
	}

	public TreeNode getNode() {
		return node;
	}

}
