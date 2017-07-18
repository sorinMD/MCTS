package mcts.game.catan;

import java.awt.Point;
import java.awt.Polygon;
import java.util.Random;

/**
 * 
 * The game board description which is not changing over one game. Game
 * representation courtesy of Pieter Spronck:
 * http://www.spronck.net/research.html
 * 
 * @author sorinMD
 *
 */
public class Board implements HexTypeConstants, VectorConstants, GameStateConstants {

	public static final int[][] LAND_COORD = { { 3, 1, -1 }, { 4, 1, -1 }, { 5, 1, -1 }, { 2, 2, -1 }, { 3, 2, -1 },
			{ 4, 2, -1 }, { 5, 2, -1 }, { 1, 3, -1 }, { 2, 3, -1 }, { 3, 3, -1 }, { 4, 3, -1 }, { 5, 3, -1 },
			{ 1, 4, -1 }, { 2, 4, -1 }, { 3, 4, -1 }, { 4, 4, -1 }, { 1, 5, -1 }, { 2, 5, -1 }, { 3, 5, -1 } };

	public static final int[][] PORT_COORD = { { 3, 0, 1 }, { 5, 0, 2 }, { 6, 1, 2 }, { 6, 3, 3 }, { 4, 5, 4 },
			{ 2, 6, 4 }, { 0, 6, 5 }, { 0, 4, 0 }, { 1, 2, 0 } };

	public static final int[][] SEA_COORD = { { 4, 0, -1 }, { 6, 0, -1 }, { 6, 2, -1 }, { 5, 4, -1 }, { 3, 6, -1 },
			{ 1, 6, -1 }, { 0, 5, -1 }, { 0, 3, -1 }, { 2, 1, -1 } };

	public static final int N_LAND_TILES = LAND_COORD.length;
	public static final int N_SEA_TILES = SEA_COORD.length;
	public static final int N_PORT_TILES = PORT_COORD.length;
	public static final int N_TILES = N_LAND_TILES + N_SEA_TILES + N_PORT_TILES;

	public static final int LAND_START_INDEX = 0;
	public static final int SEA_START_INDEX = LAND_START_INDEX + N_LAND_TILES;
	public static final int PORT_START_INDEX = SEA_START_INDEX + N_SEA_TILES;

	public static final int MAXX = 7;
	public static final int MAXY = 7;

	public int[] landSequence = { LAND_SHEEP, LAND_SHEEP, LAND_SHEEP, LAND_SHEEP, LAND_WHEAT, LAND_WHEAT, LAND_WHEAT,
			LAND_WHEAT, LAND_CLAY, LAND_CLAY, LAND_CLAY, LAND_WOOD, LAND_WOOD, LAND_WOOD, LAND_WOOD, LAND_STONE,
			LAND_STONE, LAND_STONE, LAND_DESERT };

	public int[] portSequence = { PORT_MISC, PORT_MISC, PORT_MISC, PORT_MISC, PORT_SHEEP, PORT_WOOD, PORT_CLAY,
			PORT_WHEAT, PORT_STONE };

	public int[] hexnumberSequence = { 2, 3, 3, 4, 4, 5, 5, 6, 6, 8, 8, 9, 9, 10, 10, 11, 11, 12 };

	public Random rnd = new Random(0);

	public int screenWidth, screenHeight;
	double A[][] = { { 1, 0 }, { 0.5, -0.86602540378443864676372317075294 } };
	double offset[] = { -0.5, 6.5 };
	public double scale = 20;

	public HexTile[] hextiles;
	public Edge[] edges;
	public Vertex[] vertices;
	public int[][] hexatcoord;

	public int[][] neighborHexHex;
	public int[][] neighborVertexVertex;
	public int[][] neighborHexVertex;
	public int[][] neighborHexEdge;
	public int[][] neighborVertexHex;
	public int[][] neighborVertexEdge;
	public int[][] neighborEdgeEdge;

	public boolean init = false;
	
	/**
	 * @param array
	 */
	private static void shuffleArray(int[] array) {
		int index, temp;
		Random random = new Random();
		for (int i = array.length - 1; i > 0; i--) {
			index = random.nextInt(i + 1);
			temp = array[index];
			array[index] = array[i];
			array[i] = temp;
		}
	}

	public void InitBoard() {
		int i, j, k;
		HexTile t1, t2, t3;
		int ind1, ind2, ind3;

		// create Hex tiles, set screen coordinates,
		// place them on the coordinate system
		hextiles = new HexTile[N_TILES];
		edges = new Edge[N_EDGES];
		vertices = new Vertex[N_VERTICES];
		hexatcoord = new int[MAXX][MAXY];
		neighborHexHex = new int[N_TILES][6];
		neighborHexVertex = new int[N_TILES][6];
		neighborHexEdge = new int[N_TILES][6];
		neighborVertexHex = new int[N_VERTICES][6];
		neighborVertexVertex = new int[N_VERTICES][6]; // 6 directions, but only
														// 3 active: 0,2,4 or
														// 1,3,5
		neighborVertexEdge = new int[N_VERTICES][6];
		neighborEdgeEdge = new int[N_EDGES][6];

		for (i = 0; i < MAXX; i++)
			for (j = 0; j < MAXY; j++)
				hexatcoord[i][j] = -1;
		for (i = 0; i < N_TILES; i++)
			for (j = 0; j < 6; j++) {
				neighborHexHex[i][j] = -1;
				neighborHexVertex[i][j] = -1;
				neighborHexEdge[i][j] = -1;
			}
		for (i = 0; i < N_VERTICES; i++)
			for (j = 0; j < 6; j++) {
				neighborVertexVertex[i][j] = -1;
				neighborVertexEdge[i][j] = -1;
				neighborVertexHex[i][j] = -1;
			}
		for (i = 0; i < N_EDGES; i++)
			for (j = 0; j < 6; j++) {
				neighborEdgeEdge[i][j] = -1;
			}

		shuffleArray(landSequence);
		shuffleArray(portSequence);

		for (i = 0; i < N_LAND_TILES; i++) {
			hextiles[LAND_START_INDEX + i] = new HexTile(LAND_COORD[i][0], LAND_COORD[i][1], landSequence[i], -1);
			hexatcoord[LAND_COORD[i][0]][LAND_COORD[i][1]] = LAND_START_INDEX + i;
		}
		for (i = 0; i < N_SEA_TILES; i++) {
			hextiles[SEA_START_INDEX + i] = new HexTile(SEA_COORD[i][0], SEA_COORD[i][1], SEA, -1);
			hexatcoord[SEA_COORD[i][0]][SEA_COORD[i][1]] = SEA_START_INDEX + i;
		}
		for (i = 0; i < N_PORT_TILES; i++) {
			hextiles[PORT_START_INDEX + i] = new HexTile(PORT_COORD[i][0], PORT_COORD[i][1], portSequence[i],
					PORT_COORD[i][2]);
			hexatcoord[PORT_COORD[i][0]][PORT_COORD[i][1]] = PORT_START_INDEX + i;
		}

		int[][] delta = { { 1, 0 }, { 0, 1 }, { -1, 1 }, { -1, 0 }, { 0, -1 }, { 1, -1 } };
		int x1, y1, x2, y2;
		for (i = 0; i < N_TILES; i++) {
			x1 = (int) (hextiles[i].pos.x);
			y1 = (int) (hextiles[i].pos.y);
			for (j = 0; j < 6; j++) {
				x2 = x1 + delta[j][0];
				y2 = y1 + delta[j][1];

				if ((x2 >= 0) && (x2 < MAXX) && (y2 >= 0) && (y2 < MAXY))
					ind2 = hexatcoord[x2][y2];
				else
					ind2 = -1;
				ind1 = i;
				if ((ind1 != -1) && (ind2 != -1)) {
					neighborHexHex[i][j] = ind2;
				}
			}
		}

		Point p;
		Polygon hexagon;

		for (j = 0; j < N_TILES; j++) {
			hexagon = new Polygon();
			for (i = 0; i < 6; i++) {
				p = VectorToScreenCoord(hextiles[j].pos.Add(HEX_EDGES[i]));
				hexagon.addPoint(p.x, p.y);
			}
			hextiles[j].screenCoord = hexagon;
			hextiles[j].centerScreenCord = VectorToScreenCoord(hextiles[j].pos);
		}

		// create vertices
		int nvertices = 0;
		for (i = 0; i < MAXX; i++)
			for (j = 0; j < MAXY; j++) {
				ind1 = hexatcoord[i][j];
				if (i < MAXX - 1 && j < MAXY - 1) {
					ind2 = hexatcoord[i + 1][j];
					ind3 = hexatcoord[i][j + 1];
					if ((ind1 != -1) && (ind2 != -1) && (ind3 != -1)) {
						t1 = hextiles[ind1];
						t2 = hextiles[ind2];
						t3 = hextiles[ind3];

						if (t1.type == TYPE_LAND || t2.type == TYPE_LAND || t3.type == TYPE_LAND) {
							vertices[nvertices] = new Vertex(t1.pos.Add(HEX_EDGES[5]));
							vertices[nvertices].screenCoord = VectorToScreenCoord(t1.pos.Add(HEX_EDGES[0]));
							neighborHexVertex[ind1][0] = nvertices;
							neighborHexVertex[ind2][2] = nvertices;
							neighborHexVertex[ind3][4] = nvertices;
							neighborVertexHex[nvertices][3] = ind1;
							neighborVertexHex[nvertices][5] = ind2;
							neighborVertexHex[nvertices][1] = ind3;
							nvertices++;
						}
					}
				}
				if (i < MAXX - 1 && j > 0) {
					ind2 = hexatcoord[i + 1][j];
					ind3 = hexatcoord[i + 1][j - 1];
					if ((ind1 != -1) && (ind2 != -1) && (ind3 != -1)) {
						t1 = hextiles[ind1];
						t2 = hextiles[ind2];
						t3 = hextiles[ind3];

						if (t1.type == TYPE_LAND || t2.type == TYPE_LAND || t3.type == TYPE_LAND) {
							vertices[nvertices] = new Vertex(t1.pos.Add(HEX_EDGES[5]));
							vertices[nvertices].screenCoord = VectorToScreenCoord(t1.pos.Add(HEX_EDGES[5]));
							neighborHexVertex[ind1][5] = nvertices;
							neighborHexVertex[ind2][3] = nvertices;
							neighborHexVertex[ind3][1] = nvertices;
							neighborVertexHex[nvertices][2] = ind1;
							neighborVertexHex[nvertices][0] = ind2;
							neighborVertexHex[nvertices][4] = ind3;
							nvertices++;
						}
					}
				}
			}

		int v1, v2;
		for (ind1 = 0; ind1 < N_TILES; ind1++) {
			v1 = neighborHexVertex[ind1][0];
			v2 = neighborHexVertex[ind1][1];
			if ((v1 != -1) && (v2 != -1)) {
				neighborVertexVertex[v1][2] = v2;
				neighborVertexVertex[v2][5] = v1;
			}

			v1 = neighborHexVertex[ind1][1];
			v2 = neighborHexVertex[ind1][2];
			if ((v1 != -1) && (v2 != -1)) {
				neighborVertexVertex[v1][3] = v2;
				neighborVertexVertex[v2][0] = v1;
			}
			v1 = neighborHexVertex[ind1][2];
			v2 = neighborHexVertex[ind1][3];
			if ((v1 != -1) && (v2 != -1)) {
				neighborVertexVertex[v1][4] = v2;
				neighborVertexVertex[v2][1] = v1;
			}
			v1 = neighborHexVertex[ind1][3];
			v2 = neighborHexVertex[ind1][4];
			if ((v1 != -1) && (v2 != -1)) {
				neighborVertexVertex[v1][5] = v2;
				neighborVertexVertex[v2][2] = v1;
			}
			v1 = neighborHexVertex[ind1][4];
			v2 = neighborHexVertex[ind1][5];
			if ((v1 != -1) && (v2 != -1)) {
				neighborVertexVertex[v1][0] = v2;
				neighborVertexVertex[v2][3] = v1;
			}
			v1 = neighborHexVertex[ind1][5];
			v2 = neighborHexVertex[ind1][0];
			if ((v1 != -1) && (v2 != -1)) {
				neighborVertexVertex[v1][1] = v2;
				neighborVertexVertex[v2][4] = v1;
			}

		}

		// create edges
		int nedges = 0;
		for (i = 0; i < MAXX; i++)
			for (j = 0; j < MAXY; j++) {
				ind1 = hexatcoord[i][j];
				if (i < MAXX - 1) {
					ind2 = hexatcoord[i + 1][j];
					if ((ind1 != -1) && (ind2 != -1)) {
						t1 = hextiles[ind1];
						t2 = hextiles[ind2];
						if (t1.type == TYPE_LAND || t2.type == TYPE_LAND) {
							edges[nedges] = new Edge(t1.pos.Add(HEX_EDGES[5]), t1.pos.Add(HEX_EDGES[0]));
							edges[nedges].screenCoord[0] = VectorToScreenCoord(t1.pos.Add(HEX_EDGES[5]));
							edges[nedges].screenCoord[1] = VectorToScreenCoord(t1.pos.Add(HEX_EDGES[0]));
							v1 = neighborHexVertex[ind1][5];
							neighborVertexEdge[v1][1] = nedges;
							v1 = neighborHexVertex[ind1][0];
							neighborVertexEdge[v1][4] = nedges;
							neighborHexEdge[ind1][0] = nedges;
							neighborHexEdge[ind2][3] = nedges;
							nedges++;
						}

					}
				}

				if (j < MAXY - 1) {
					ind2 = hexatcoord[i][j + 1];
					if ((ind1 != -1) && (ind2 != -1)) {
						t1 = hextiles[ind1];
						t2 = hextiles[ind2];
						if (t1.type == TYPE_LAND || t2.type == TYPE_LAND) {
							edges[nedges] = new Edge(t1.pos.Add(HEX_EDGES[0]), t1.pos.Add(HEX_EDGES[1]));
							edges[nedges].screenCoord[0] = VectorToScreenCoord(t1.pos.Add(HEX_EDGES[0]));
							edges[nedges].screenCoord[1] = VectorToScreenCoord(t1.pos.Add(HEX_EDGES[1]));
							v1 = neighborHexVertex[ind1][0];
							neighborVertexEdge[v1][2] = nedges;
							v1 = neighborHexVertex[ind1][1];
							neighborVertexEdge[v1][5] = nedges;
							neighborHexEdge[ind1][1] = nedges;
							neighborHexEdge[ind2][4] = nedges;
							nedges++;
						}
					}
				}
				if (i > 0 && j < MAXY - 1) {
					ind2 = hexatcoord[i - 1][j + 1];
					if ((ind1 != -1) && (ind2 != -1)) {
						t1 = hextiles[ind1];
						t2 = hextiles[ind2];
						if (t1.type == TYPE_LAND || t2.type == TYPE_LAND) {
							edges[nedges] = new Edge(t1.pos.Add(HEX_EDGES[1]), t1.pos.Add(HEX_EDGES[2]));
							edges[nedges].screenCoord[0] = VectorToScreenCoord(t1.pos.Add(HEX_EDGES[1]));
							edges[nedges].screenCoord[1] = VectorToScreenCoord(t1.pos.Add(HEX_EDGES[2]));
							v1 = neighborHexVertex[ind1][1];
							neighborVertexEdge[v1][3] = nedges;
							v1 = neighborHexVertex[ind1][2];
							neighborVertexEdge[v1][0] = nedges;
							neighborHexEdge[ind1][2] = nedges;
							neighborHexEdge[ind2][5] = nedges;
							nedges++;
						}
					}
				}

			}

		for (i = 0; i < N_VERTICES; i++) {
			for (j = 0; j < 6; j++)
				for (k = 0; k < 6; k++) {
					ind1 = neighborVertexEdge[i][j];
					ind2 = neighborVertexEdge[i][k];
					if ((ind1 != -1) && (ind2 != -1)) {
						neighborEdgeEdge[ind1][k] = ind2;
						neighborEdgeEdge[ind2][j] = ind1;
					}
				}
		}

		InitProductionNumbers();
		init = true;
	}

	void InitProductionNumbers() {
		boolean goodarrangement = false;
		int x, y, k, ind, ind2;
		int seqind;

		while (!goodarrangement) {

			shuffleArray(hexnumberSequence);
			seqind = 0;

			// deal numberts to hexes
			for (x = 0; x < MAXX; x++)
				for (y = 0; y < MAXY; y++) {
					ind = hexatcoord[x][y];
					if ((ind != -1) && hextiles[ind].type == TYPE_LAND && hextiles[ind].subtype != LAND_DESERT) {
						hextiles[ind].productionNumber = hexnumberSequence[seqind];
						seqind++;
					}
				}

			// check if the arrangement is good
			// it is good if no red numbers (6 or 8) are besides each other
			goodarrangement = true;
			outerloop: for (x = 0; x < MAXX; x++)
				for (y = 0; y < MAXY; y++) {
					ind = hexatcoord[x][y];
					for (k = 0; k < 6; k++) {
						if (ind != -1) {
							ind2 = neighborHexHex[ind][k];
							if ((ind2 != -1)
									&& (hextiles[ind].productionNumber == 6 || hextiles[ind].productionNumber == 8)
									&& (hextiles[ind2].productionNumber == 6 || hextiles[ind2].productionNumber == 8)) {
								goodarrangement = false;
								break outerloop;
							}
						}
					}
				}

		}
	}

	public Point VectorToScreenCoord(Vector2d v) {
		Point p = new Point();

		p.setLocation(scale * (A[0][0] * v.x + A[1][0] * v.y + offset[0]),
				scale * (A[0][1] * v.x + A[1][1] * v.y + offset[1]));
		return p;
	}

}
