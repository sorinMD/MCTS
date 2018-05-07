package mcts.game.catan.typepdf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class HumanActionTypePdf extends ActionTypePdf implements Serializable{
	//11 types during normal phase of game: build road, build city, build settlement, buy card, end turn, port trade, play knight, play mono, play rb, play yop, trade;
	
	//C(a = t_i | [t1,t2..t11]) == number of times each type was picked given that a set of types was legal
	@JsonIgnore
	protected Map<ArrayList<Integer>,Map<Integer,Integer>> chosenTypeCountConditioned = new HashMap<>();
	//C([t1,t2..t11]) == number of times a set of types were legal
	@JsonIgnore
	protected Map<ArrayList<Integer>, Integer> conditionCount = new HashMap<>();
	//C(t_i) == number of times a type was legal
	@JsonIgnore
	protected Map<Integer,Integer> typeCount = new HashMap<>();
	//C(a=t_i|t_i) == how many times a type was chosen given that it was legal
	@JsonIgnore
	protected Map<Integer,Integer> chosenTypeCount = new HashMap<>();
	
	public boolean conditionOnLegalTypes = false;
	
	public double temperature = 1.0;
	
	public HumanActionTypePdf() {}
	
	public Map<Integer,Double> getDist(ArrayList<Integer> legalTypes){
		Collections.sort(legalTypes);
		Map<Integer,Double> pdf = new HashMap<>();
		if(conditionCount.containsKey(legalTypes) && conditionOnLegalTypes) {
			int totalCount = conditionCount.get(legalTypes);
			Map<Integer,Integer> counts = chosenTypeCountConditioned.get(legalTypes);
			for(Integer t : legalTypes) {
				if(counts.containsKey(t)) {
					double val = ((double)counts.get(t))/totalCount;
					pdf.put(t, val);
				}else {//fallback to the basic count if not seen in the data
					double val = ((double)chosenTypeCount.get(t))/typeCount.get(t);
					pdf.put(t, val);
				}
			}
		}else {//use the basic count for all types
			for(Integer t : legalTypes) {
				double val = ((double)chosenTypeCount.get(t))/typeCount.get(t);
				pdf.put(t, val);
			}
		}
		//normalise distribution since not all possible types are always legal and also we may not have data for all types in the conditioned case.
		double sumP = 0.0;
		double curr = 0.0;
		for(Entry<Integer, Double> entry : pdf.entrySet()) {
			curr = Math.pow(entry.getValue(), 1.0/temperature);
			pdf.put(entry.getKey(), curr);
			sumP += curr;
		}
		for(Entry<Integer, Double> entry : pdf.entrySet()) {
			pdf.put(entry.getKey(), entry.getValue()/sumP);
		}
		
		return pdf;
	}
	
    protected boolean copyToFile(){
        try {
            // Write the object out to a byte array
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bout);
            out.writeObject(this);
            out.flush();
            out.close();
            
            //remove any special characters if they could exist
            String className = this.getClass().getName();
            //create a file and write to it the bytes
            File file = new File(className + ".dat");
            byte[] byteData = bout.toByteArray();
            FileOutputStream fout = new FileOutputStream(file);
            fout.write(byteData);
            fout.close();

        }
        catch(IOException e) {
        	e.printStackTrace();
            return false;
        }
        return true;
    }
    
    public static HumanActionTypePdf readFromFile(){
    	HumanActionTypePdf obj = null;
    	Path path;
    	String fileName = HumanActionTypePdf.class.getName() + ".dat";//add the termination
    	File f = new File(fileName);	
    	//very brittle way of getting the path, but should work if the working directory always has the same structure
    	path = f.toPath();
    		
    	try{
    		byte[] data = Files.readAllBytes(path);
    		ObjectInputStream in = new ObjectInputStream(
    	            new ByteArrayInputStream(data));
    	        obj = (HumanActionTypePdf) in.readObject();
    	}catch (Exception e) {
    		e.printStackTrace();
    	}
    	return obj;
    }
}
