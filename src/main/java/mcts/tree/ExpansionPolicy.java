package mcts.tree;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.seeder.SeedTrigger;
import mcts.tree.node.StandardNode;
import mcts.tree.node.Key;
import mcts.tree.node.TreeNode;

/**
 * The default expansion policy.
 * 
 * @author sorinMD
 *
 */
public class ExpansionPolicy {

	/**
	 * Expands a node and adds all the children to the tree data structure. It
	 * also selects the next one at random. If seeding is used it adds the newly
	 * expanded node to the seed trigger.
	 * 
	 * @param tree
	 * @param node
	 * @param trigger
	 * @param gameFactory
	 * @return
	 */
	public static TreeNode expand(Tree tree, TreeNode node, SeedTrigger trigger, GameFactory gameFactory) {
		int[] s = node.getState();
		Game game = gameFactory.getGame(s);
		ArrayList<int[]> actions = game.listPossiblities(false);//always list all options
		int[] act;
		Game clone;
		TreeNode child;
		ArrayList<Key> children = new ArrayList<Key>();
		for (int i = 0; i < actions.size(); i++) {
			clone = game.copy();
			act = actions.get(i);
			clone.performAction(act);
			child = clone.generateNode();
			children.add(child.getKey());
			tree.putNodeIfAbsent(child);
		}
		((StandardNode) node).addChildren(children);
		trigger.addNode(node);
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		return tree.getNode(children.get(rnd.nextInt(children.size())));
	}
}
