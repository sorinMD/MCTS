package mcts.game.catan.typepdf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class UniformActionTypePdf extends ActionTypePdf{

	public UniformActionTypePdf() {}
	
	@Override
	public Map<Integer, Double> getDist(ArrayList<Integer> legalTypes) {
		Map<Integer, Double> dist = new HashMap<Integer, Double>();
		for(Integer i : legalTypes)
			dist.put(i, 1.0/legalTypes.size());
		return dist;
	}

}
