package mcts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import mcts.game.Game;
import mcts.game.GameConfig;
import mcts.game.GameFactory;
import mcts.game.catan.Catan;
import mcts.game.catan.CatanConfig;
import mcts.game.catan.belief.CatanFactoredBelief;
import mcts.listeners.IterationListener;
import mcts.listeners.SearchListener;
import mcts.listeners.TimeListener;
import mcts.listeners.TimedIterationListener;
import mcts.tree.ExpansionPolicy;
import mcts.tree.Tree;
import mcts.utils.PriorityRunnableComparator;
import mcts.utils.Timer;

/**
 * The class that contains the configuration and the thread management.
 * 
 * @author sorinMD
 *
 */
public class MCTS {

	private ExecutorService execService;
	private Tree tree;
	private SearchListener listener;
	private GameFactory gameFactory;
	private MCTSConfig config;
	private Timer t;
	
	public MCTS(MCTSConfig config, GameFactory gameFactory, Game state) {
		config.selfCheck();
		gameFactory.getConfig().selfCheck(config);
		this.config = config;
		tree = new Tree(state, config.treeSize);
        execService = new ThreadPoolExecutor(config.nThreads,config.nThreads, 0L, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<Runnable>(config.nIterations, new PriorityRunnableComparator()));
		t = new Timer();
		this.gameFactory = gameFactory;
		this.config.trigger.init(this);
	}
	
	public MCTS(MCTSConfig config, GameFactory gameFactory) {
		config.selfCheck();
		gameFactory.getConfig().selfCheck(config);
		this.config = config;
        execService = new ThreadPoolExecutor(config.nThreads,config.nThreads, 0L, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<Runnable>(config.nIterations, new PriorityRunnableComparator()));
        t = new Timer();
        this.gameFactory = gameFactory;
        this.config.trigger.init(this);
	}
	
	public void newTree(Game rootState){
		tree = new Tree(rootState, config.treeSize);
		//the below should avoid nullpointers if none of the rollouts finish in the allocated budget
		ExpansionPolicy.expand(tree, tree.getRoot(), config.trigger, gameFactory.copy(), null, config.nRootActProbSmoothing);
	}
	
	/**
	 * Basic benchmarking on Catan game.
	 * @param args
	 */
	public static void main(String[] args) {
		MCTSConfig mctsConf = new MCTSConfig();
		GameConfig gameConf = new CatanConfig();
		int nGames = 10;
		if(args.length == 3){
			try{
				mctsConf = readMCTSConfig(args[0]);
				gameConf = readGameConfig(args[1]);
		    } 
		    catch (JsonParseException e) { e.printStackTrace(); }
		    catch (JsonMappingException e) { e.printStackTrace(); }
		    catch (IOException e) { e.printStackTrace(); }
			nGames = Integer.parseInt(args[2]);
		}else if(args.length > 0){
			System.out.println("Incorrect number of parameters specified, running with default values on Catan game.");
		}
		//uncommment to save configuration
        writeJSON(mctsConf, gameConf);
		GameFactory gf = new GameFactory(gameConf, null);
//		GameFactory gf = new GameFactory(gameConf, new CatanFactoredBelief(4));
		MCTS mcts = new MCTS(mctsConf, gf);
        
		long overallTime = 0;
		long treeSize = 0;
		System.out.println("Running " + nGames + " games.");
		for(int i = 0; i < nGames+1; i++){
			if(i%10 == 0)
				System.out.println(i);
			try {
				//wait for the seeder threads to finish before modifying the board to avoid nullpointers
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			//new board and game;
			Catan.initBoard();
			Game state = mcts.gameFactory.getNewGame();
			mcts.newTree(state);
			SearchListener listener = mcts.search();
			listener.waitForFinish();
			
			if(i!=0){//ignore the first run, to allow JIT optimizations
				overallTime+=mcts.t.elapsed();
				treeSize+=mcts.tree.getTreeSize();
			}
		}
		overallTime/=nGames;
		treeSize/=nGames;
		System.out.println("Average time: " + overallTime);
		System.out.println("Average tree size: " + treeSize);
		mcts.shutdownNow(false);
	}
	
	public SearchListener search(){
		if(config.timeLimit==0)
			listener = new IterationListener(this);
//			listener = new TimedIterationListener(this); //replace the above with this line to time the agent
		else{
			listener = new TimeListener(this);
		}
		t.reset();
		//TODO: create a factory for each type of agent would be nicer than having this check here
		if(config.pomcp) {
			for(int i = 0; i <config.nIterations; i++){
				execService.execute(new POMCPAgent(tree, listener, config, gameFactory));
			}
		}else {
			for(int i = 0; i <config.nIterations; i++){
				execService.execute(new MCTSAgent(tree, listener, config, gameFactory));
			}
		}
		return listener;
	}
	
	public void shutdownNow(boolean restart){
		execService.shutdownNow();
		config.trigger.cleanUp();
		if(restart)
	        execService = new ThreadPoolExecutor(config.nThreads,config.nThreads, 0L, TimeUnit.MILLISECONDS,
	                new PriorityBlockingQueue<Runnable>(config.nIterations, new PriorityRunnableComparator()));
	}
	
	public void execute(Runnable r){
		execService.execute(r);
	}
	
	public int getNSimulations(){
		return config.nIterations;
	}
	
	public int getNextActionIndex(){
		return config.selectionPolicy.selectBestAction(tree);
	}
	
	public long getTimeLimit(){
		return config.timeLimit;
	}
	
	/**
	 * Lists the legal options and orders them based on their value
	 * @return
	 */
	public ArrayList<int[]> getOrderedActionList(){
		ArrayList<int[]> actions = gameFactory.getGame(tree.getRoot().getState()).listPossiblities(false).getOptions();
		double[] values = config.selectionPolicy.getChildrenValues(tree);
		if(values.length != actions.size()){
			throw new RuntimeException("root game state options size is different to root node children size");
		}
		Map<int[],Double> unorderedOptions = new HashMap<int[],Double>();
		for(int i = 0; i < values.length; i++){
			unorderedOptions.put(actions.get(i),values[i]);
		}
		Map<int[],Double> orderedOptions = sortByValue(unorderedOptions);
		ArrayList<int[]> orderedActions = new ArrayList<int[]>();
		for(Map.Entry<int[], Double> entry : orderedOptions.entrySet()){
			orderedActions.add(entry.getKey());
		}
		//reverse order as we need the opposite to the natural ordering
		Collections.reverse(orderedActions);
		return orderedActions;
	}
	
	private static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
		Map<K, V> result = new LinkedHashMap<>();
		Stream<Map.Entry<K, V>> st = map.entrySet().stream();
		st.sorted(Map.Entry.comparingByValue()).forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
		return result;
	}
		
	public GameFactory getGameFactory(){
		return gameFactory;
	}
	
	private static void writeJSON(MCTSConfig mctsConf, GameConfig gameConf) {
		try{
			ObjectMapper mapper = new ObjectMapper();
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
			mapper.writeValue(new File("mcts.json"), mctsConf);
			mapper.writeValue(new File("game.json"), gameConf);
	    } 
	    catch (JsonGenerationException e) { e.printStackTrace(); }
	    catch (JsonMappingException e) { e.printStackTrace(); }
	    catch (IOException e) { e.printStackTrace(); }
	}

	private static MCTSConfig readMCTSConfig(String path) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		MCTSConfig conf = mapper.readValue(new File(path), MCTSConfig.class);
		return conf;
	}
	
	private static GameConfig readGameConfig(String path) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		GameConfig conf = mapper.readValue(new File(path), GameConfig.class);
		return conf;
	}
	
	public MCTSConfig getMCTSConfig() {
		return config;
	}
	
	/**
	 * This is a very unsafe method!!! But it is unfortunately needed for loading a game in StacSettlers project.
	 * TODO: find a better way to handle this, in the meantime do not use it for anything else!
	 * @param newFac
	 */
	public void setGameFactory(GameFactory newFac) {
		gameFactory = newFac;
	}
	
	
	/////debugging methods////
	public void report(){
		System.out.println(t.toString());
		System.out.println("Tree size: " + tree.getTreeSize());
//		System.out.println("Best index: " + TreePolicy.selectBestAction(tree));
	}
	
}
