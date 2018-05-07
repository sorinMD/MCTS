package mcts.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple utility for reporting elapsed time since its creation or last reset
 * @author sorinMD
 */
public class Timer {

    private static final String DEFAULT_TIMER_NAME = ".~DEFAULT_TIMER~.";
    private final Map<String, Long> timerMap = new HashMap<>();
    
    private String name;
    private boolean pause = false;
    private long pauseTime = 0;

    public Timer() {
    	name = DEFAULT_TIMER_NAME;
        reset(name);
    }
    
    public Timer(String name) {
        reset(name);
    }
    
    public long elapsed() {
        return elapsed(name);
    }

    public void reset() {
        reset(name);
    }

    private void reset(String name) {
        timerMap.put(name, System.nanoTime());
    }

    private long elapsed(String name) {
    	if(pause)
    		return pauseTime - timerMap.get(name); 
    	else
    		return System.nanoTime() - timerMap.get(name);
    }

    public String toString() {
        return name + ": " + elapsed(name) + " ns elapsed";
    }

    public void pause(){
    	pause = true;
    	pauseTime = System.nanoTime();
    }
    
    public void unpause(){
    	pause = false;
    	timerMap.put(name, timerMap.get(name) + (System.nanoTime() - pauseTime));
    	pauseTime = 0;
    }
    
    public static void main(String[] args) {
        Timer t = new Timer();
        System.out.println("ns elasped: " + t.elapsed());
    }
}


