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
	public int nIterations = 10000;
	public int nThreads = 4;
	public long timeLimit = 0;
	public int treeSize = 500000;
	public int maxTreeDepth = 50;
	public boolean afterstates = true;
	public boolean observableRollouts = false;
	/**Number of rollouts to be performed per iteration from the current leaf node*/
	public int nRolloutsPerIteration = 1;
	public boolean pomcp = false;
	public SeedTrigger trigger = new NullSeedTrigger();
	public SelectionPolicy selectionPolicy = new UCT();
	public UpdatePolicy updatePolicy = new StateUpdater(false,false);
	/**Flag to average results if multiple rollouts are run*/
	public boolean averageRolloutsResults = false;
	/**Flag to weight the return by the probability of the state being the true state given the agent's belief.*/
	public boolean weightedReturn = false;
	public int nRootActProbSmoothing = 1;
	public int nRootStateProbSmoothing = 1;
	
	@Override
	public String toString() {
		return "[nIterations-" + nIterations + "; nThreads-" + nThreads + "; timeLimit-" + timeLimit + "; TreeSize-" + treeSize  
				+ "; maxTreeDeph-" + maxTreeDepth + "; afterstates-" + afterstates + "; " + afterstates
				+ "; observableRollouts-" + observableRollouts + "; nRolloutsPerSearch-" + nRolloutsPerIteration 
				+ "; averageRolloutsResults-" + averageRolloutsResults + "; weightedReturn-" + weightedReturn + "; POMCP-" + pomcp
				+ "; nRootActProbSmoothing-" + nRootActProbSmoothing + "; nRootStateProbSmoothing-" + nRootStateProbSmoothing 
				+ "; selection policy-" + selectionPolicy.toString() 
				+ "; update policy-" + updatePolicy.toString() 
				+ "; trigger-" + trigger.toString() + "]";
	}
	
	/**
	 * Checks for configurations that are not currently supported, outputs a warning and resets to a known legal configuration.
	 */
	public void selfCheck(){
		if(!afterstates){
			if(!(updatePolicy instanceof ActionUpdater)){
				System.err.println("WARNING: Update policy not compatible with MCTS without afterstates. Changing to ActionUpdater." );
				updatePolicy = new ActionUpdater(updatePolicy.expectedReturn,updatePolicy.everyVisit);
			}
			if(!(selectionPolicy instanceof UCTAction)){
				System.err.println("WARNING: Selection policy not compatible with MCTS without afterstates. Changing to UCTAction." );
				boolean weightedSelection = selectionPolicy.weightedSelection;
			    int minvisits = selectionPolicy.MINVISITS;
			    double c = selectionPolicy.C0;
				selectionPolicy = new UCTAction();
				selectionPolicy.weightedSelection = weightedSelection;
				selectionPolicy.MINVISITS = minvisits;
				selectionPolicy.C0 = c;
			}
			if(!(trigger instanceof NullSeedTrigger)){
				System.err.println("WARNING: Seeding not supported with MCTS without afterstates. Removing seeding configuration." );
				trigger = new NullSeedTrigger();
			}
			if((selectionPolicy.ismcts)){
				selectionPolicy.ismcts = false;
				System.err.println("WARNING: ISMCTS is not defined without afterstates. Removing configuration." );
			}
		}else{
			if(updatePolicy instanceof ActionUpdater){
				System.err.println("WARNING: Update policy not compatible with MCTS with afterstates. Changing to StateUpdater." );
				updatePolicy = new StateUpdater(updatePolicy.expectedReturn,updatePolicy.everyVisit);
			}
			if(selectionPolicy instanceof UCTAction){
				System.err.println("WARNING: Selection policy not compatible with MCTS with afterstates. Changing to standard UCT." );
				boolean weightedSelection = selectionPolicy.weightedSelection;
			    int minvisits = selectionPolicy.MINVISITS;
			    double c = selectionPolicy.C0;
				selectionPolicy = new UCT();
				selectionPolicy.weightedSelection = weightedSelection;
				selectionPolicy.MINVISITS = minvisits;
				selectionPolicy.C0 = c;
			}
		}
		
		if(!(trigger instanceof NullSeedTrigger)){
			if(selectionPolicy instanceof UCTAction || selectionPolicy instanceof UCT){
				System.err.println("WARNING: Seeding policy not compatible with UCT or UCTActions. Seeded values will not be used. Removing seeding configuration." );
				trigger = new NullSeedTrigger();
			}
		}
		if(pomcp) {
			if(observableRollouts) {
				System.err.println("WARNING: POMCP already has observable rollouts. Ignoring observable rollouts parameter." );
				observableRollouts = false;
			}
				
		}else if (selectionPolicy.ismcts){
			selectionPolicy.ismcts = false;
			System.err.println("WARNING: ISMCTS is only defined with POMCP. Removing configuration since pomcp is false." );
		}
		
	}
}
