package mcts.tree.node;

/**
 * A chance node, where the next node/state is sampled according to the game
 * logic. In some cases listing all possible children would be expensive due to
 * the size of the space. For such nodes it is quicker to sample from the
 * possible options. It is also unnecessary to apply a deterministic tree
 * policy. Therefore, no edges are kept in memory, instead a new key is
 * generated and is checked against the existing nodes in the tree.
 * 
 * @author MD
 *
 */
public class ChanceNode extends TreeNode {

	public ChanceNode(int[] state, boolean terminal, int cpn) {
		super(state, terminal, cpn);
		// always use the tree policy to select the action if it is not terminal
		// as these nodes are treated as special cases in the tree policy logic
		setLeaf(false);
	}

	/**
	 * Never expand this type of node.
	 */
	public boolean canExpand() {
		return false;
	}

}
