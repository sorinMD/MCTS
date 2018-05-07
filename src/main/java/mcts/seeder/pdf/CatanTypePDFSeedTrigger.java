package mcts.seeder.pdf;

import java.util.ArrayList;
import java.util.Map;

import com.google.common.util.concurrent.AtomicDoubleArray;

import mcts.game.GameFactory;
import mcts.game.catan.Catan;
import mcts.game.catan.CatanConfig;
import mcts.game.catan.GameStateConstants;
import mcts.game.catan.typepdf.ActionTypePdf;
import mcts.game.catan.typepdf.UniformActionTypePdf;
import mcts.seeder.SeedTrigger;
import mcts.tree.node.StandardNode;
import mcts.tree.node.TreeNode;

/**
 * This uses the typed pdf to seed in the tree without launching new threads.
 * 
 * @author sorinMD
 *
 */
public class CatanTypePDFSeedTrigger extends SeedTrigger implements GameStateConstants{
	
	public ActionTypePdf pdf = new UniformActionTypePdf();
	
	public CatanTypePDFSeedTrigger() {
		// TODO Auto-generated constructor stub
	}
	
	@Override
	public void addNode(TreeNode node, GameFactory factory) {
		//no need to start a new thread just for this. Also no need to track what was already evaluated as this should be pretty quick
		int task = getTaskIDFromState(node);
		if(task == -1)
			return;
		Catan game = (Catan) factory.getGame(node.getState());
		ArrayList<Integer> types = game.listNormalActionTypes();
		Map<Integer,Double> dist = pdf.getDist(types);
		//trades are A_TRADE in rollouts, but could be A_OFFER in tree, so handle this aspect here
		if(((CatanConfig)factory.getConfig()).NEGOTIATIONS) {
			if(dist.containsKey(A_TRADE)) {
				dist.put(A_OFFER, dist.get(A_TRADE));
				dist.remove(A_TRADE);
			}
		}
			
		ArrayList<int[]> actions = game.listPossiblities(false).getOptions();
		double[] prob = new double[actions.size()];
		for(Integer t : dist.keySet()) {
			int count = 0;
			for(int[] act : actions) {
				if(act[0] == t)
					count++;
			}
			double val = dist.get(t)/(double)count;
			for(int i = 0; i < actions.size(); i++) {
				if(actions.get(i)[0] == t)
					prob[i] = val;
			}
		}
		((StandardNode)node).pValue = new AtomicDoubleArray(prob);
	}

	@Override
	public void cleanUp() {
		//nothing to clean up
	}
		
	/**
	 * Logic to check if it is normal task. This type of seeding works only for this task.
	 * @param n the tree node containing the state description
	 * @return
	 */
	private int getTaskIDFromState(TreeNode n){
		int[] state = n.getState();
		int fsmlevel = state[OFS_FSMLEVEL];
		int fsmstate = state[OFS_FSMSTATE + fsmlevel];
		
		switch (fsmstate) {
		case S_NORMAL:
			return 2;
		default:
			return -1;
		}
	} 
	
	
}
