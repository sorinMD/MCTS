package mcts.game.catan.belief;

import java.io.Serializable;

/**
 * The model that tracks the resources in the imperfect information game. It
 * tracks the total resources of each type that are in play and each of the
 * players' hands.
 * 
 * @author sorinMD
 */
public class ResourcesModel implements Serializable{
	/**
	 * The model which tracks players' possible resource sets from this player's perspective.
	 * NB: even if we have less than the maxplayer value, we still define the world for all, 
	 * but we never modify the ones for the empty seats
	 */
	private PlayerResourceModel[] playersHandsModel;
	
	public ResourcesModel(int maxPlayers) {
		playersHandsModel = new PlayerResourceModel[maxPlayers];
		for(int i = 0; i < maxPlayers; i++){
			playersHandsModel[i] = new PlayerResourceModel();
		}
	}
	
	public ResourcesModel(ResourcesModel old) {
		playersHandsModel = new PlayerResourceModel[old.playersHandsModel.length];
		for(int i = 0; i < old.playersHandsModel.length; i++){
			playersHandsModel[i] = old.playersHandsModel[i].copy();
		}
	}
	
	public PlayerResourceModel[] getPlayerHandModel() {
		return playersHandsModel;
	}
	
	public PlayerResourceModel getPlayerHandModel(int pl) {
		return playersHandsModel[pl];
	}
	
	/**
	 * @return a deep copy of the object
	 */
	protected ResourcesModel copy() {
		return new ResourcesModel(this);
	}
	
	public void destroy() {
		for(PlayerResourceModel pl : getPlayerHandModel()) {
			pl.destroy();
			pl = null;
		}
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof ResourcesModel) {
			ResourcesModel objRef = (ResourcesModel) obj;
			if(playersHandsModel.length == objRef.playersHandsModel.length) {
				boolean equal = true;
				for(int i = 0; i < playersHandsModel.length; i++) {
					if(! playersHandsModel[i].equals(objRef.playersHandsModel[i]))
						equal = false;
				}
				if(equal)
					return true;
			}
		}
		return false;
	}
	
}
