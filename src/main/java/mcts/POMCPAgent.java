package mcts;

import java.util.ArrayList;
import mcts.game.DeterminizationSampler;
import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.listeners.SearchListener;
import mcts.tree.ExpansionPolicy;
import mcts.tree.SimulationPolicy;
import mcts.tree.Tree;
import mcts.tree.node.TreeNode;
import mcts.utils.GameSample;
import mcts.utils.Priority;
import mcts.utils.PriorityRunnable;
import mcts.utils.Selection;

public class POMCPAgent implements Runnable,PriorityRunnable{
	private Tree tree;
	private SearchListener listener;
	private MCTSConfig config;
	private GameFactory gameFactory;
	private static Priority priority = Priority.LOW;
	
	public POMCPAgent(Tree tree, SearchListener listener, MCTSConfig config,  GameFactory gameFactory) {
		this.tree = tree;
		this.listener = listener;
		this.gameFactory = gameFactory;
		this.config = config;
	}
	@Override
	public void run() {
		try {
			GameFactory factory = gameFactory.copy();
			ArrayList<Selection> visited = new ArrayList<Selection>();
			TreeNode node = tree.getRoot();
			Selection selection = new Selection(false, node, 1.0);
			visited.add(selection); 
			
			/*
			 * Sample one state that is followed through the algorithm to provide observations for POMCP
			 * NOTE: that the sampler may return null if there is no belief and pomcp flag doesn't make a difference.
			 */
			Game obsGame = null;
			DeterminizationSampler sampler = factory.getDeterminizationSampler();
			GameSample sample = sampler.sampleObservableState(factory.getGame(node.getState()), factory);
			obsGame = sample.getGame();
			
			boolean allSiblingsVisited = true; //ensures the first expansion of the tree node is performed
			int depth = 1;
			
			while(!node.isLeaf() && !node.isTerminal()){
				selection = config.selectionPolicy.selectChild(node, tree, factory, obsGame);
				node = selection.getNode();
				allSiblingsVisited = selection.getBoolean();
				visited.add(selection);			
				if(obsGame.isTerminal())
					break;//in pomcp, the observable game chooses the outcome, we could rely on the following chance node but there is no point in doing one more selection if we already know the outcome.
				if(!allSiblingsVisited)
					break; //do not select further, still gathering statistics on siblings
				if(depth > config.maxTreeDepth) 
					break; //reached max tree depth or stuck in one of the game's cycles.
				depth++;	
			}
			
			/*
			 * Expand only if certain conditions are satisfied:
			 * -all siblings have been visited at least SelectionPolicy.MINVISITS times;
			 * -the node is a leaf and not a terminal node;
			 * -the node can be expanded (never expand chance/nature nodes);
			 * -max tree size was not reached.
			 */
			if(!node.isTerminal() && node.canExpand() && allSiblingsVisited && !tree.maxSizeReached() && node.isLeaf()){
				if(!obsGame.isTerminal()) {
					selection = ExpansionPolicy.expand(tree, node, config.trigger, factory, obsGame, config.nRootActProbSmoothing);
					visited.add(selection);
					node = selection.getNode();
				}
			}
			
			//rollouts are always observable in POMCP since we follow one fully-observable state at random
			Game game = obsGame;
			factory = new GameFactory(factory.getConfig(), null);
			
			double[] wins = new double[GameFactory.nMaxPlayers()];
			//TODO: these rollouts could also be parallelised if multiple
			for (int i=0; i < config.nRolloutsPerIteration; i++) {
				Game gameClone = game.copy();
				gameClone = SimulationPolicy.simulate(gameClone);
				if(gameClone.getWinner() != -1) {
					if(config.weightedReturn) {
						double prob = sample.getProb();
						if(config.nRootStateProbSmoothing > 1)
							prob = Math.pow(prob, 1.0/config.nRootStateProbSmoothing);
						wins[gameClone.getWinner()] += prob;
					}else
						wins[gameClone.getWinner()] += 1.0;
				}else {
		 			/*
		 			 * this means everyone lost or the game hasn't finished yet (it
		 			 * could happen in Catan if roll-outs are too long, probably caused
		 			 * by no more legal building locations available on the board)
		 			 */
					System.err.println("WARNING: Possibly updating with results from a game that was not finished");
				}
			}
			int nRollouts = config.nRolloutsPerIteration;
			if(config.averageRolloutsResults) {
				for(int i = 0; i < wins.length; i++)
					wins[i] = wins[i]/config.nRolloutsPerIteration;
				nRollouts = 1;
			}
			config.updatePolicy.update(visited, wins, nRollouts);
		
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
