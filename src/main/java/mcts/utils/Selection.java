package mcts.utils;

import mcts.tree.node.TreeNode;

/**
 * Simple container needed for returning the result of the tree policy that
 * includes several pieces of information: the child node, a flag to indicate
 * whether all siblings have been explored and the action probability (given a belief).
 * 
 * @author sorinMD
 *
 */
public class Selection {

	private boolean b = false;
	private TreeNode node;
	private double actProb;

	public Selection(boolean b, TreeNode node, double prob) {
		this.b = b;
		this.node = node;
		this.actProb = prob;
	}

	public boolean getBoolean() {
		return b;
	}

	public TreeNode getNode() {
		return node;
	}
	
	public double getActProb() {
		return actProb;
	}
}
