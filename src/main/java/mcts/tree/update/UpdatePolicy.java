package mcts.tree.update;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import mcts.game.Game;
import mcts.tree.node.TreeNode;

/**
 * A utility for updating the tree nodes following a rollout result.
 * 
 * @author sorinMD
 *
 */
@JsonTypeInfo(use = Id.CLASS,
include = JsonTypeInfo.As.PROPERTY,
property = "type")
@JsonSubTypes({
	@Type(value = StateUpdater.class),
	@Type(value = ActionUpdater.class),
})
public abstract class UpdatePolicy {

	public abstract void update(ArrayList<TreeNode> visited, Game game);
	
}
