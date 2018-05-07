package mcts.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import mcts.game.Game;
import mcts.game.GameFactory;
import mcts.seeder.SeedTrigger;
import mcts.tree.node.StandardNode;
import mcts.tree.node.Key;
import mcts.tree.node.TreeNode;
import mcts.utils.Options;
import mcts.utils.Selection;
import mcts.utils.Utils;

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
	public static Selection expand(Tree tree, TreeNode node, SeedTrigger trigger, GameFactory gameFactory, Game obsGame, int nRootActLeg) {
		Game game = gameFactory.getGame(node.getState());
		Options options = game.listPossiblities(false);//always list all options when expanding
		ArrayList<int[]> actions = options.getOptions();
		int[] act;
		Game gameClone;
		TreeNode child;
		ArrayList<Key> children = new ArrayList<Key>();
		ArrayList<Double> mask = new ArrayList<Double>(Collections.nCopies(actions.size(), 1.0));
		Options obsOptions = null;
		if(obsGame != null) {//POMCP and ISMCTS, list actions here so we can compute the mask for initialising parent visits
			obsOptions = obsGame.listPossiblities(false);
			mask = Utils.createActMask(obsOptions.getOptions(), actions);
		}
		for (int i = 0; i < actions.size(); i++) {
			gameClone = game.copy();
			act = actions.get(i);
			gameClone.performAction(act, false);
			child = gameClone.generateNode();
			children.add(child.getKey());
			tree.putNodeIfAbsent(child);
			if(mask.get(i) == 1.0)//update parent visits for ISMCTS
				tree.getNode(child.getKey()).incrementParentVisits();
		}
		if(nRootActLeg > 1 && options.getProbabilities() != null) {//smooth out action legality prob
			ArrayList<Double> probs = options.getProbabilities();
			for(int i =0; i < probs.size(); i++) {
				probs.set(i, Math.pow(probs.get(i), 1.0/nRootActLeg));
			}
		}
		((StandardNode) node).addChildren(children, options.getOptions(), options.getProbabilities());
		if(children.size() > 1)// there is nothing to seed if there is a single option.
			trigger.addNode(node, gameFactory.copy());
		int[] action;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		//pick one action at random and execute it so we can update the results of one of the new nodes also.
		double actProb = 1.0;
		int idx = 0;
		if(obsGame != null) {//POMCP
			idx = rnd.nextInt(obsOptions.size());
			action = obsOptions.getOptions().get(idx);
			//get action idx from the whole set of options
			idx = options.indexOfAction(action);
			actProb = options.getProbabilities().get(idx);//we don't actually need this	
			obsGame.performAction(action, false);
		}else {
			idx = rnd.nextInt(options.size());
			action = options.getOptions().get(idx);
			ArrayList<Double> probs = options.getProbabilities();
			if(probs != null)//handling the MCTS version on observable games
				actProb = probs.get(idx);
		}
		game.performAction(action, false);
		return new Selection(false, tree.getNode(children.get(idx)), actProb);
	}
}
