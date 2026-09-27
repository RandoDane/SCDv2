package com.scd.logic.dungeon;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IceFillSolverTest {
	@Test
	void coversEveryTileOnceFromStartToEnd() {
		// 5x5 floor, entering bottom middle and leaving top middle.
		Set<Long> tiles = new HashSet<>();
		for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) tiles.add(IceFillSolver.key(new int[]{x, z}));
		List<int[]> path = IceFillSolver.solve(tiles, new int[]{2, 4}, new int[]{2, 0});
		assertNotNull(path);
		assertEquals(tiles.size(), path.size());
		Set<Long> seen = new HashSet<>();
		for (int i = 0; i < path.size(); i++) {
			assertTrue(seen.add(IceFillSolver.key(path.get(i))));
			if (i > 0) assertEquals(1, Math.abs(path.get(i)[0] - path.get(i - 1)[0]) + Math.abs(path.get(i)[1] - path.get(i - 1)[1]));
		}
		assertArrayEquals(new int[]{2, 0}, path.getLast());
	}

	@Test
	void impossibleFloorGivesNull() {
		// A hole on one checkerboard colour leaves 12/12 tiles, but start and end share a colour: no path.
		Set<Long> tiles = new HashSet<>();
		for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) if (!(x == 1 && z == 1)) tiles.add(IceFillSolver.key(new int[]{x, z}));
		assertNull(IceFillSolver.solve(tiles, new int[]{2, 4}, new int[]{2, 0}));
	}
}
