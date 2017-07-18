package mcts.tree;

import java.util.concurrent.ConcurrentHashMap;

import mcts.game.Game;
import mcts.tree.node.Key;
import mcts.tree.node.TreeNode;

/**
 * The container for the tree nodes and the references to them. 
 * 
 * @author sorinMD
 *
 */
public class Tree {
	private ConcurrentHashMap<Key, TreeNode> nodes;
	private TreeNode root;
	private int maxTreeSize = 500000;
	
	public boolean maxSizeReached(){
		return nodes.size() > maxTreeSize;
	}
	
	public Tree(Game currentState, int maxSize) {
		maxTreeSize = maxSize;
		nodes = new ConcurrentHashMap<Key, TreeNode>(maxTreeSize);
		root = currentState.generateNode();
		nodes.put(root.getKey(), root);
	}
	
	public TreeNode getNode(Key k){
		return nodes.get(k);
	}
	
	public void putNodeIfAbsent(TreeNode node){
		nodes.putIfAbsent(node.getKey(),node);
	}
	
	public TreeNode getRoot(){
		return root;
	}
	
	public int getTreeSize(){
		return nodes.size();
	}
		
}
