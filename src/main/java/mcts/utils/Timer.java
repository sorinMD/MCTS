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

    public Timer() {
        reset(DEFAULT_TIMER_NAME);
    }

    public long elapsed() {
        return elapsed(DEFAULT_TIMER_NAME);
    }

    public void reset() {
        reset(DEFAULT_TIMER_NAME);
    }

    public void reset(String name) {
        timerMap.put(name, System.nanoTime());
    }

    public void start() {
        reset(DEFAULT_TIMER_NAME);
    }

    public void start(String name) {
        reset(name);
    }

    public long elapsed(String name) {
        return System.nanoTime() - timerMap.get(name);
    }

    public String toString() {
        return toString(DEFAULT_TIMER_NAME);
    }

    public String toString(String name) {
        return elapsed(name) + " ns elapsed";
    }

    public static void main(String[] args) {
        Timer t = new Timer();
        System.out.println("ns elasped: " + t.elapsed());
    }
}


