package mcts.tree.node;

import java.util.ArrayList;

/**
 * As the name suggests, the decision in this node is taken simultaneously by
 * multiple players. The participant for each action is stored as the decision is
 * made based on the action value for both the current player and the second
 * participant. E.g. action in Catan: the exchange of resources, seen as a
 * concise trade (the accept, reject actions are built into the evaluation). or
 * the discard action which should be hidden to other players and there is no
 * exact order in which this should be performed following the game rules
 * TODO: decide if this will be used or remove it entirely
 * @author MD
 *
 */
public class SimultaneousActionNode extends StandardNode {

	private ArrayList<Integer> participants;

	public SimultaneousActionNode(int[] state, boolean terminal, int cpn) {
		super(state, terminal, cpn);
	}

	public void addParticipants(ArrayList<Integer> p) {
		this.participants = p;
	}

	public ArrayList<Integer> getParticipants() {
		return participants;
	}

}
