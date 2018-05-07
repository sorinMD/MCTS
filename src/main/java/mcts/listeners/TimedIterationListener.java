package mcts.listeners;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import mcts.MCTS;
import mcts.utils.Timer;

/**
 * Iteration listener which also times how long it takes to run MCTS.
 * 
 * @author sorinMD
 *
 */
public class TimedIterationListener implements SearchListener{

	private AtomicInteger counter = new AtomicInteger(0);
	private MCTS mcts;
	public Timer timer = new Timer();
	public static ArrayList<Long> times = new ArrayList<>();
	
	public TimedIterationListener(MCTS mcts) {
		this.mcts = mcts;
	}
	
	public boolean hasFinished(){
		return counter.intValue() >= mcts.getNSimulations();
	}
	
	public void waitForFinish(){
		timer.reset();
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
			times.add(timer.elapsed());
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
