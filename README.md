# MCTS

Parallel implementation of MCTS code, using tree parallelisation. It is a synchronised implementation, i.e. update, expansion steps are synchronised and the tree information is stored in a ConcurrentHashMap. This repository contains the code for the paper:

- Mihai Dobre and Alex Lascarides. Exploiting action categories in learning complex games. In IEEE SAI Intelligent Systems Conference (IntelliSys), London, UK, 2017

Game models:
TicTacToe (3 by 3) as a simple example;
Settlers of Catan, the fully-observable game, including trades. (extension of the SmartSettlers model*).

MCTS configuration options are defined in mcts.json:
- nRollouts : number of rollouts (or iterations);
- nThreads : number of threads;
- timeLimit : a time limit for each search (a maximum number of rollouts must also be specified). If this is set to 0, there is no limit;
- treeSize : option for limiting the tree size via setting the max number of nodes contained in the ConcurrentHashMap data structure;
- afterstates : boolean flag to allow the use of afterstates in the selection policy. If set to false, the selection policy and updating policy are automatically set to UCTAction and ActionUpdater;
- selectionPolicy : defining the selection policy: 
	- type : UCT, UCTAction (standard UCT but used only when afterstates are not allowed), PUCT, RAVE**;
	- C0 : exploration parameter;
	- MINVISITS : minimum number of node visits, before using the statistics in the selection policy (this is equivalent to not adding the node to the tree). 
- trigger : used to define the seeding policy. There are no options available as only the basic framework is included in the code; 
- updatePolicy : StateUpdater is the default option when afterstates are allowed, otherwise ActionUpdater is used;

Game configuration options are defined in game.json. Configuration specific to Settlers of Catan:
- type : choosing the config type also selects which game model is used in the search;
- TRADES : boolean flag for allowing or dissallowing trades between players;
- NEGOTIATIONS : boolean flag for allowing the negotiation actions of accept or reject. Should be left to true since false implies players can execute resource exchanges without the acknowledgement of the other participant;
- ALLOW_COUNTEROFFERS : boolean flag for allowing counter-offer as negotiation response. This will increase the tree size without any benefits as described in the paper; 
- N_MAX_DISCARD : option for setting the number of resources discarded when a 7 is rolled as a threshold before this action is executed at random instead of the chosen selection policy. The default value of 10, means that the player will discard at random if it has more than 20 resources in its hand when a 7 is rolled. The threshold of 10 was chosen as it is crossed only in the case of a draw, when there is no more space on the board and the players cannot use their resources.
- EVEN_DISTRIBUTION_OVER_TYPES : boolean flag for the double sampling approach proposed in the paper: first choose an action type, followed by choosing an option of that type;
- ALLOW_SAMPLING_IN_NORMAL_STATE : boolean flag for sampling from the trade options in the normal state and always presenting a single option, without the extra step of sampling from the other action types. This is a flawed implementation of the type sampling. 

To run the simple benchmarking on the Catan game, run Mcts.java with argument the name of the two configuration files and the number of games (e.g. java -cp mcts.jar mcts.MCTS mcts.json game.json 100). Code to test in agents playing the Settlers of Catan game will be released with the code for the [Strategic Conversation (STAC)] project. 

*Thanks to Pieter Spronck and Istvan Szita for most of the game model implementation, especially the basic rules and datastructures: Szita, I., Chaslot, G., and Spronck, P. (2010). Monte-carlo tree search in settlers of catan. In van den Herik, H. and Spronck, P., editors, Advances in Computer Games, pages 21–32. Springer.

** AMAF is not implemented yet. Both PUCT and RAVE are used as methods for managing the amount of prior information

[Strategic Conversation (STAC)]: https://www.irit.fr/STAC/index.html
