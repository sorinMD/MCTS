package mcts;

import mcts.seeder.NullSeedTrigger;
import mcts.seeder.SeedTrigger;
import mcts.tree.selection.SelectionPolicy;
import mcts.tree.selection.UCT;
import mcts.tree.selection.UCTAction;
import mcts.tree.update.ActionUpdater;
import mcts.tree.update.StateUpdater;
import mcts.tree.update.UpdatePolicy;

/**
 * 
 * @author sorinMD
 *
 */
public class MCTSConfig {
	//default values and policies
	public int nRollouts = 10000;
	public int nThreads = 4;
	public long timeLimit = 0;
	public int treeSize = 500000;
	public int maxDepth = 50;
	public boolean afterstates = true;
	public SeedTrigger trigger = new NullSeedTrigger();
	public SelectionPolicy selectionPolicy = new UCT();
	public UpdatePolicy updatePolicy = new StateUpdater();
	
	@Override
	public String toString() {
		//TODO: add policy values
		return "[nRollouts-" + nRollouts + "; nThreads-" + nThreads + "; timeLimit-" + timeLimit + "; TreeSize-" + treeSize + "; trigger-" 
				+ trigger.getClass().getName() + "; selection policy-" + selectionPolicy.getClass().getName() +
				"; update policy-" + updatePolicy.getClass().getName() + "]";
	}
	
	/**
	 * Checks for configurations that are not currently supported, outputs a warning and resets to a known legal configuration.
	 */
	public void selfCheck(){
		if(!afterstates){
			if(!(updatePolicy instanceof ActionUpdater)){
				System.err.println("WARNING: Update policy not compatible with MCTS without afterstates. Changing to ActionUpdater." );
				updatePolicy = new ActionUpdater();
			}
			if(!(selectionPolicy instanceof UCTAction)){
				System.err.println("WARNING: Selection policy not compatible with MCTS without afterstates. Changing to UCTAction." );
				selectionPolicy = new UCTAction();
			}
			if(!(trigger instanceof NullSeedTrigger)){
				System.err.println("WARNING: Seeding not supported with MCTS without afterstates. Removing seeding configuration." );
				trigger = new NullSeedTrigger();
			}
		}else{
			if(updatePolicy instanceof ActionUpdater){
				System.err.println("WARNING: Update policy not compatible with MCTS with afterstates. Changing to StateUpdater." );
				updatePolicy = new StateUpdater();
			}
			if(selectionPolicy instanceof UCTAction){
				System.err.println("WARNING: Selection policy not compatible with MCTS with afterstates. Changing to standard UCT." );
				selectionPolicy = new UCT();
			}
		}
		if(!(trigger instanceof NullSeedTrigger)){
			if(selectionPolicy instanceof UCTAction || selectionPolicy instanceof UCT){
				System.err.println("WARNING: Seeding policy not compatible with UCT or UCTActions. Seeded values will not be used. Removing seeding configuration." );
				trigger = new NullSeedTrigger();
			}
		}
		System.out.println("Mcts configuration: " + toString());
	}
}
