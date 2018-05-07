package mcts.seeder;

import java.util.HashSet;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import mcts.MCTS;
import mcts.game.GameFactory;
import mcts.seeder.pdf.CatanTypePDFSeedTrigger;
import mcts.tree.node.TreeNode;

/**
 * Trigger class that gathers the nodes to be evaluated (e.g. in a queue) and
 * starts the evaluation threads based on some logic. Manages the number of
 * evaluation threads, as well as cleans up afterwards.
 * 
 * @author sorinMD
 *
 */
@JsonTypeInfo(use = Id.CLASS,
include = JsonTypeInfo.As.PROPERTY,
property = "type")
@JsonSubTypes({
	@Type(value = NullSeedTrigger.class),
	@Type(value = CatanTypePDFSeedTrigger.class)
})
public abstract class SeedTrigger {
	@JsonIgnore
	protected MCTS mcts;
	@JsonIgnore
	protected HashSet<Seeder> aliveSeeders = new HashSet<>();
	@JsonIgnore
	protected boolean initialised = false;
	/**
	 * Max number of seeders alive at one point.
	 */
	public int maxSeederCount = 2;
	
	/**
	 * Add a new node to the queue, and possibly start the evaluation thread
	 * @param node
	 */
	public abstract void addNode(TreeNode node, GameFactory factory);
	
	/**
	 * e.g. clears queue(s), resets counters etc
	 */
	public abstract void cleanUp();
	
	/**
	 * Give access to the thread pool executor in MCTS and initialise fields
	 * @param mcts
	 */
	public void init(MCTS mcts){
		this.mcts = mcts;
		initialised = true;
	}
	
	@Override
	public String toString() {
		return "[name-" + this.getClass().getName() + "; maxSeederCount-" + maxSeederCount + "]";
	}
}
