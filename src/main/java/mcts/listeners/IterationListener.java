package mcts.listeners;

import java.util.concurrent.atomic.AtomicInteger;

import mcts.MCTS;

/**
 * Iteration listener
 * 
 * @author sorinMD
 *
 */
public class IterationListener implements SearchListener{

	private AtomicInteger counter = new AtomicInteger(0);
	private MCTS mcts;
	
	public IterationListener(MCTS mcts) {
		this.mcts = mcts;
	}
	
	public boolean hasFinished(){
		return counter.intValue() >= mcts.getNSimulations();
	}
	
	public void waitForFinish(){
		synchronized (mcts) {
			try {
				mcts.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void increment(){
		if(counter.incrementAndGet() == mcts.getNSimulations()){
			synchronized (mcts) {
				mcts.shutdownNow(true);
				mcts.notify();
			}
		}
	}

	@Override
	public int getNSimulations() {
		return counter.get();
	}
	
}
