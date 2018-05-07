# MCTS

Java implementation of various parallel MCTS algorithms using tree parallelisation. It is a synchronised implementation, i.e. update, expansion and selection steps are synchronised. The tree information is stored in a ConcurrentHashMap. This repository contains the code for the paper:

- Mihai Dobre and Alex Lascarides. Exploiting action categories in learning complex games. In IEEE SAI Intelligent Systems Conference (IntelliSys), London, UK, 2017

and also code developed for the following PhD Thesis:
- Mihai Dobre, Low-resource Learning in Complex Games, School of Informatics, University of Edinburgh, 2018.

Game models:
- TicTacToe (3 by 3) as a simple example of how to create a game model;
- Catan.java : Settlers of Catan, the fully-observable game, including trades. (extension of the SmartSettlers* model).
- CatanWithBelief.java: partially observable version of Settlers of Catan that allows MCTS versions to run from the perspective of one player. This game model is called CatanWithBelief as it accepts as input the belief of the current player. The version also contains a Factored belief model that can be used to track the belief of agents in Settlers of Catan (see mcts.game.catan.belief package)

MCTS algorithms:
- MCTS : standard MCTS method for planning in fully-observable games with the modifications described in the IntelliSys paper.
and versions that can handle Settlers of Catan with imperfect information:
- BMCTS (Belief MCTS) : utilises the CatanWithBelief model to handle belief transitions and the factored belief model to track a players belief. It avoids sampling of observable states and should aggregate the results by avoiding a number of stochastic transitions common in the observable version of the game. Unfortunately, this is the slowest and the weakest model due to the belief rollouts, while the rest have comparable performance.
- BMCTSOR (Belief MCTS with Observable Rollouts) : BMCTS which samples observable states at the leaf node only.
- POMCP (a modified version as presented in the paper and thesis)
- ISMCTS single observer with chance nodes to handle opponents' partially observable moves.

We will now describe the parameters that need to be set in order to run specific MCTS algorithms and choosing the game. There are other parameters that were not used in any of the experiments presented in the publications, so we skipped describing them. There are further comments in the code to explain the ones we missed.

MCTS configuration options are defined in mcts.json or in MCTSConfig.java class:
- nIterations : number of rollouts (or iterations);
- nThreads : number of threads;
- timeLimit : a time limit for each search (a maximum number of rollouts must also be specified). If this is set to 0, there is no limit;
- treeSize : option for limiting the tree size via setting the max number of nodes contained in the ConcurrentHashMap data structure;
- afterstates : boolean flag to allow the use of afterstates in the selection policy. If set to false, the selection policy and updating policy are automatically set to UCTAction and ActionUpdater;
- selectionPolicy : defining the selection policy: 
	- type : UCT, UCTAction (standard UCT but used only when afterstates are not allowed), PUCT, RAVE**;
	- C0 : exploration parameter;
	- ismcts : flag to run ISMCTS. pomcp flag (see below) must be set also;
	- weightedSelection : flag for ignoring the action legality probability in the tree phase (for BMCTS or BMCTSOR)
- pomcp : flag for running POMCP;
- trigger : used to define the seeding policy. NullSeedTrigger does nothing, while CatanTypePDFSeedTrigger can use the pdf over types learned from the human corpus. To be used with PUCT or RAVE.
- updatePolicy : StateUpdater is the default option when afterstates are allowed, otherwise ActionUpdater is used;
	- expectedReturn : for computing the expectation with respect to the action legality probability when running BMCTS(OR)
- observableRollouts : perform observable rollouts instead of belief rollouts (i.e. change from BMCTS to BMCTSOR);
- nRootActProbSmoothing : n value for the root smoothing action probabilities used in the tree phase (of BMCTS or BMCTSOR) by increasing the temperature;

Game configuration options are defined in game.json or in CatanConfig.java. Configuration specific to Settlers of Catan:
- type : choosing the config type also selects which game model is used in the search;
- TRADES : boolean flag for allowing trades between players;
- NEGOTIATIONS : boolean flag for allowing the negotiation actions of accept or reject. Should be left to true since false implies players can execute resource exchanges without the acknowledgement of the other participant;
- N_MAX_DISCARD : option for setting the number of resources discarded when a 7 is rolled as a threshold before this action is executed at random instead of the chosen selection policy. The default value of 8, means that the player will discard at random if it has more than 16 resources in its hand when a 7 is rolled. The threshold of 8 was chosen as it is rarely crossed in simulations.
- SAMPLE_FROM_DISTRIBUTION_OVER_TYPES_IN_ROLLOUTS : boolean flag for the double sampling approach proposed in the paper: first choose an action type, followed by choosing an option of that type. Specify the rolloutTypeDist described below to select the distribution learned from human games;
- rolloutTypeDist : the distribution over action types used in rollouts, either uniform or the (conditioned or unconditioned) distribution learned from the human corpus with MLE;
- OBS_DISCARDS_IN_ROLLOUTS : make discards observable in belief rollouts. This flag is recommended with belief rollouts, otherwise BMCTS will be even more expensive;
- OFFERS_LIMIT : max number of offers an agent can make per turn;
- LIST_POMS : flag for listing all possible moves an opponent can make when it is making a partially observable move. This flag is required when running POMCP or ISMCTS as it helps synchronising the two game models (observable and partially observable);
- OBSERVABLE_VPS : flag to make victory point development cards observable by opponents when drawn;
- ENFORCE_UNIFORM_TYPE_DIST_IN_BELIEF_ROLLOUTS : flag to ignore the action legality probability or the type distribution in belief rollouts (for BMCTS).

To run the simple benchmarking on the observable Catan game, run the jar that contains the dependencies with arguments the name of the two configuration files and the number of games (e.g. java -cp MCTS-1.0.jar mcts.MCTS mcts.json game.json 100). Code to test these agents playing the Settlers of Catan game versus other agents can be found at: https://github.com/sorinMD/StacSettlers.git . The example configurations provided in the StacSettlers repository describe the configurations required for the experiments presented in the above publications.

* Thanks to Pieter Spronck and Istvan Szita for most of the game model implementation, especially the basic game rules and data structures: Szita, I., Chaslot, G., and Spronck, P. (2010). Monte-carlo tree search in settlers of catan. In van den Herik, H. and Spronck, P., editors, Advances in Computer Games, pages 21–32. Springer.

** AMAF is not implemented yet. Both PUCT and RAVE are used as methods for managing the amount of prior information

[Strategic Conversation (STAC)]: https://www.irit.fr/STAC/index.html
