package mcts.game.catan.belief;

import java.io.Serializable;
import java.util.Arrays;

import mcts.game.catan.GameStateConstants;

/**
 * Model that keeps track of development cards, that have not been played yet, including victory points.
 * @author sorinMD
 */
public class PlayerDevCardModel implements Serializable, GameStateConstants{
	
	/**
	 * Total cards, including known and unknowns
	 */
	private int total;
	/**
	 * From the unknowns, total cards that we know are non-vp cards.
	 */
	private int nonVp;
	/**
	 * Everything that is known or inferred from the totals.
	 */
	private int[] revealed;
	
	public PlayerDevCardModel() {
		total = 0;
		nonVp = 0;
		revealed = new int[N_DEVCARDTYPES];
	}
	
	public PlayerDevCardModel(PlayerDevCardModel old) {
		total = old.total;
		nonVp = old.nonVp;
		revealed = old.revealed.clone();
	}
	
	public int getNonVP() {
		return nonVp;
	}
	
	public int getTotal() {
		return total;
	}
	
	public int getTotalRevealed() {
		int t = 0;
		for(int i =0; i < N_DEVCARDTYPES; i++)
			t += revealed[i];
		return t;
	}
	
	public int getRevealed(int type) {
		return revealed[type];
	}
	
	public int[] getRevealedSet() {
		return revealed;
	}
	
	/**
	 * Updates the model when a card is drawn
	 * @param type the type of development card if known or {@link GameStateConstants#N_DEVCARDTYPES} if not known
	 */
	public void updateDraw(int type) {
		total++;
		if(type < N_DEVCARDTYPES)//if it is known increment the revealed (i.e. our player or fully observable game cases)
			revealed[type]++;
	}
	
	/**
	 * Updates the model when a card is played
	 * @param type the type of development card
	 */
	public void updatePlay(int type) {
		total--;
		if(revealed[type] > 0)
			revealed[type]--;
		if(type != CARD_ONEPOINT && nonVp > 0) //if we knew this player had a non-vp card then we should assume it was one of those
			nonVp--;
	}
	
	/**
	 * When doing a revision we may reveal certain cards given the current chances and remaining quantities.
	 * @param type the type of development card
	 * @param amt the amount that was revealed
	 */
	protected void reveal(int type, int amt) {
		revealed[type] += amt;
		if(type != CARD_ONEPOINT) {
			while(amt > 0) {
				if(nonVp == 0)
					break;
				nonVp--;
				amt--;
			}
		}
	}
	
	/**
	 * Certain actions may reveal that this player has a certain amount of non vp cards.
	 * @param amt the amount that was revealed to be nonVp cards
	 */
	protected void addNonVp(int amt) {
		nonVp += amt;
	}
	
	/**
	 * @return a deep copy of the object
	 */
	protected PlayerDevCardModel copy() {
		return new PlayerDevCardModel(this);
	}
	
	@Override
	public String toString() {
		return " total:" + total + "; non-vp:" + nonVp + "; revealed:" + Arrays.toString(revealed);
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof PlayerDevCardModel) {
			PlayerDevCardModel objRef = (PlayerDevCardModel) obj;
			if(total == objRef.total && nonVp == objRef.nonVp && Arrays.equals(revealed, objRef.revealed))
				return true;
		}
		return false;
	}
	
	
}
