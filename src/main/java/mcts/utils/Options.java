package mcts.utils;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A simple container to store a list of int[] and the probabilities attached to
 * each, in the situation where duplicates are acceptable and where we do not
 * care about O(1) contains call. The purpose is to avoid the computation of
 * hashcode in these situations. Due to how both actions and states are
 * represented as an array of integers, this simple datastructure could be used
 * with either, hence the generic name.
 * 
 * @author sorinMD
 *
 */
public class Options {
	ArrayList<int[]> options;
	ArrayList<Double> probs;
	double totalMass = 0.0;

	public Options() {
		options = new ArrayList<>();
		probs = new ArrayList<>();
	}

	public Options(int size) {
		options = new ArrayList<>(size);
		probs = new ArrayList<>(size);
	}
	
	/**
	 * Note that this constructor doesn't set the total mass. Use with care!
	 * @param opts
	 * @param probs
	 */
	public Options(ArrayList<int[]> opts, ArrayList<Double> probs) {
		options = opts;
		this.probs = probs;
	}
	
	@SuppressWarnings("unchecked")
	public Options(Options opt) {
		options = (ArrayList<int[]>) opt.options.clone();
		probs = (ArrayList<Double>) opt.probs.clone();
	}

	public ArrayList<int[]> getOptions() {
		return options;
	}

	public ArrayList<Double> getProbabilities() {
		return probs;
	}

	public double getTotalMass() {
		return totalMass;
	}
	
	public void put(int[] opt, double prob) {
		options.add(opt);
		probs.add(prob);
		totalMass += prob;
	}
	
	public void putAll(Options opt) {
		for(int i = 0; i < opt.size(); i++ ) {
			options.add(opt.options.get(i));
			probs.add(opt.probs.get(i));
			totalMass += opt.probs.get(i);
		}
	}

	public void clear() {
		options.clear();
		probs.clear();
		totalMass = 0.0;
	}
	
	/**
	 * Same implementation as {@link ArrayList#indexOf(Object)} but using
	 * {@link Arrays#equals(Object)} instead of {@link Object#equals(Object)}
	 * 
	 * @param action
	 *            the description of the action
	 * @return -1 if index not found or the index of the corresponding action
	 */
	public int indexOfAction(int [] action) {
        if (action == null) {
            for (int i = 0; i < options.size(); i++)
                if (options.get(i)==null)
                    return i;
        } else {
            for (int i = 0; i < options.size(); i++)
                if (Arrays.equals(action, options.get(i)))
                    return i;
        }
        return -1;
	}

	public boolean isEmpty() {
		return options.isEmpty();
	}
	
	public int size() {
		return options.size();// the two should be the same
	}

	@Override
	protected Object clone() throws CloneNotSupportedException {
		return new Options(this);
	}

}
