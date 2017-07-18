package mcts.game.catan;

/**
 * Utility class for generating the action description
 * 
 * @author sorinMD
 *
 */
public class Actions implements GameStateConstants{

	/**
	 * Create a new action description
	 * 
	 * @param a
	 * @return
	 */
    public static int[] newAction(int a)
    {
    	int[] action = new int[ACTIONSIZE];
        action[0] = a;
        return action;
    }

    /**
     * Create a new action description
     * 
     * @param a
     * @param par1
     * @return
     */
    public static int[] newAction(int a, int par1)
    {
    	int[] action = new int[ACTIONSIZE];
        action[0] = a;
        action[1] = par1;
        return action;
    }
    
    /**
     * Create a new action description
     * 
     * @param a
     * @param par1
     * @param par2
     * @return
     */
    public static int[] newAction(int a, int par1, int par2)
    {
    	int[] action = new int[ACTIONSIZE];
        action[0] = a;
        action[1] = par1;
        action[2] = par2;
        return action;
    }
    
    /**
     * Create a new action description
     * 
     * @param a
     * @param par1
     * @param par2
     * @param par3
     * @return
     */
    public static int[] newAction(int a, int par1, int par2, int par3)
    {
    	int[] action = new int[ACTIONSIZE];
        action[0] = a;
        action[1] = par1;
        action[2] = par2;
        action[3] = par3;
        return action;
    }    

    /**
     * Create a new action description
     * 
     * @param a
     * @param par1
     * @param par2
     * @param par3
     * @param par4
     * @return
     */
    public static int[] newAction(int a, int par1, int par2, int par3, int par4)
    {
    	int[] action = new int[ACTIONSIZE];
        action[0] = a;
        action[1] = par1;
        action[2] = par2;
        action[3] = par3;
        action[4] = par4;
        return action;
    }
    
    /**
     * Create a new action description for the discard action
     * 
     * @param a
     * @param par1
     * @param par2
     * @param par3
     * @param par4
     * @param par5
     * @return
     */
    public static int[] newAction(int a, int par1, int par2, int par3, int par4, int par5)
    {
    	int[] action = new int[ACTIONSIZE];
        action[0] = a;
        action[1] = par1;
        action[2] = par2;
        action[3] = par3;
        action[4] = par4;
        action[5] = par5;
        return action;
    }
    
    /**
     * Create a new action description
     * 
     * @param a
     * @param par1
     * @param par2
     * @param par3
     * @param par4
     * @param par5
     * @param par6
     * @param par7
     * @param par8
     * @param par9
     * @param par10
     * @return
     */
    public static int[] newAction(int a, int par1, int par2, int par3, int par4, int par5, int par6, int par7, int par8, int par9, int par10)
    {
    	int[] action = new int[ACTIONSIZE];
        action[0] = a;
        action[1] = par1;
        action[2] = par2;
        action[3] = par3;
        action[4] = par4;
        action[5] = par5;
        action[6] = par6;
        action[7] = par7;
        action[8] = par8;
        action[9] = par9;
        action[10] = par10;
        return action;
    } 
	
	
	
}
