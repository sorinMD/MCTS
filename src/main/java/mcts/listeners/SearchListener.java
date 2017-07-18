package mcts.listeners;

/**
 * Listener interface, that monitors the MCTS search, stops it and cleans up
 * 
 * @author sorinMD
 *
 */
public interface SearchListener {
	
	public boolean hasFinished();
	
	public void waitForFinish();
	
	public void increment();
	
	public int getNSimulations();
}
