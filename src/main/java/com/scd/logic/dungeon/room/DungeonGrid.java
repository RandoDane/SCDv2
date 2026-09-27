package com.scd.logic.dungeon.room;

/**
 * Geometry of the Catacombs room grid: 6x6 tiles of 32 blocks starting at x/z = -200 (a room is
 * 31 wide plus a 1-block wall gap). Tile centres are at -185 + 32 * i, which is also block 7 of
 * the even chunks -12, -10, ..., -2.
 */
public final class DungeonGrid {
	public static final int SIZE = 6;
	public static final int TILE = 32;
	public static final int ORIGIN = -200;

	private DungeonGrid() {
	}

	public static int centre(int tile) {
		return tile * TILE - 185;
	}

	/** Tile index along one axis for a block coordinate (Odin's +201 rounding); may be outside 0..5. */
	public static int tileOf(int block) {
		return (block + 201) >> 5;
	}

	public static boolean inGrid(int tileX, int tileZ) {
		return tileX >= 0 && tileX < SIZE && tileZ >= 0 && tileZ < SIZE;
	}

	/** Chunk coordinate holding a tile's centre column. */
	public static int chunkOf(int tile) {
		return (tile - 6) * 2;
	}

	public static int index(int tileX, int tileZ) {
		return tileX + tileZ * SIZE;
	}
}
