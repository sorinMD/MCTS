package mcts.listeners;

import java.util.concurrent.atomic.AtomicInteger;

import mcts.MCTS;

/**
 * Time listener
 * 
 * @author sorinMD
 *
 */
public class TimeListener implements SearchListener {

	private AtomicInteger counter = new AtomicInteger(0);
	private MCTS mcts;
	private long startTime;

	public TimeListener(MCTS mcts) {
		this.mcts = mcts;
		startTime = System.currentTimeMillis();
	}

	@Override
	public boolean hasFinished() {
		// TODO: this is not really safe since we keep track of time but there
		// may still be something running; Should also check the queue of the
		// executor service
		return System.currentTimeMillis() - startTime >= mcts.getTimeLimit();
	}

	@Override
	public void waitForFinish() {
		synchronized (mcts) {
			try {
				mcts.wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		try {
			Thread.sleep(100);//TODO: this is horrible, but it is the simplest way to wait for the shutdown and restart...it will have to do for now...
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void increment() {
		// there is an upper limit on the number of simulations
		if (counter.incrementAndGet() == mcts.getNSimulations()) {
			synchronized (mcts) {
				mcts.shutdownNow(true);
				mcts.notify();
			}
		} else if (System.currentTimeMillis() - startTime >= mcts.getTimeLimit()) {
			synchronized (mcts) {
				// TODO: find a better way to stop threads in the future, this seems to be sufficient for now
				mcts.shutdownNow(true);
				mcts.notify();
			}
		}
	}
	public int getNSimulations(){
		return counter.get();
	}

}
