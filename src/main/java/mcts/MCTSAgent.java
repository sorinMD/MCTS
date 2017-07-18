package mcts;

import java.util.ArrayList;

import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.listeners.SearchListener;
import mcts.seeder.SeedTrigger;
import mcts.tree.ExpansionPolicy;
import mcts.tree.SimulationPolicy;
import mcts.tree.Tree;
import mcts.tree.node.TreeNode;
import mcts.tree.selection.SelectionPolicy;
import mcts.tree.update.UpdatePolicy;
import mcts.utils.BooleanNodePair;
import mcts.utils.Priority;
import mcts.utils.PriorityRunnable;

/**
 * Performs one pass through the four stages of the Monte Carlo Tree Search algorithm.
 * @author sorinMD
 *
 */
public class MCTSAgent implements Runnable,PriorityRunnable{
	private Tree tree;
	private SearchListener listener;
	private static Priority priority = Priority.LOW;
	private SeedTrigger trigger;
	private SelectionPolicy selectionPolicy;
	private UpdatePolicy updatePolicy;
	private GameFactory gameFactory;
	private int maxDepth;
	
	public MCTSAgent(Tree tree, SearchListener listener, SeedTrigger trigger, SelectionPolicy selectionPolicy, UpdatePolicy updatePolicy, GameFactory gameFactory, int maxDepth) {
		this.tree = tree;
		this.listener = listener;
		this.trigger = trigger;
		this.selectionPolicy = selectionPolicy;
		this.gameFactory = gameFactory;
		this.updatePolicy = updatePolicy;
		this.maxDepth = maxDepth;
	}
	
	@Override
	public void run() {
		try {
			ArrayList<TreeNode> visited = new ArrayList<TreeNode>();
			
			TreeNode node = tree.getRoot();
			visited.add(node);
			boolean allSiblingsVisited = true; //ensures the first expansion of the tree node is performed (just in case)
			int depth = 1;
			while(!node.isLeaf() && !node.isTerminal()){
				BooleanNodePair pair = selectionPolicy.selectAction(node, tree, gameFactory);
				node = pair.getNode();
				allSiblingsVisited = pair.getBoolean();
				visited.add(node);
				if(!allSiblingsVisited)
					break; //sample the space uniformly at random until all siblings are visited
				if(depth > maxDepth) //reached max tree depth or stuck in one of the game's cycles.
					break;
				depth++;	
			}
			
			/*
			 * Expand only if certain conditions are satisfied:
			 * -all siblings have been visited at least SelectionPolicy.MINVISITS times;
			 * -the node is a leaf and not a terminal node;
			 * -the node can be expanded (never expand chance/nature nodes);
			 * -max tree size was not reached.
			 * These are minor optimisations and for keeping the statistics significant
			 */
			if(!node.isTerminal() && node.canExpand() && allSiblingsVisited && !tree.maxSizeReached() && node.isLeaf()){
				node = ExpansionPolicy.expand(tree, node, trigger, gameFactory);
				visited.add(node);
			}
			
			Game game = gameFactory.getGame(node.getState());
			SimulationPolicy.simulate(game);
			
			updatePolicy.update(visited, game);
		
		} catch (Exception e) {
			e.printStackTrace();
		}finally {
			/*
			 * in case anything goes wrong in some of the iterations, we should
			 * still increment the listener to avoid deadlocks
			 */
			listener.increment();
		}
		
	}

	@Override
	public int getPriority() {
		return priority.getValue();
	}

}
