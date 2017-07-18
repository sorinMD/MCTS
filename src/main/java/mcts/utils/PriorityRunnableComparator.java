package mcts.utils;

import java.util.Comparator;

/**
 * 
 * @author sorinMD
 *
 */
public class PriorityRunnableComparator implements Comparator<Runnable> {
    public int compare(Runnable o1, Runnable o2) {
        if (o1 == null && o2 == null)
            return 0;
        else if (o1 == null)
            return -1;
        else if (o2 == null)
            return 1;
        else {
            int p1 = ((PriorityRunnable) o1).getPriority();
            int p2 = ((PriorityRunnable) o2).getPriority();

            return p1 > p2 ? 1 : (p1 == p2 ? 0 : -1);
        }
    }
}