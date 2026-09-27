package com.scd.logic.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ice Fill: walk over every ice tile of a floor exactly once, from one end to the other. A floor is
 * a small grid (at most 7x7), so a depth-first search that tries the most constrained neighbour
 * first (Warnsdorff's rule) finds a covering path quickly.
 */
public final class IceFillSolver {
	private IceFillSolver() {
	}

	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	/** Tiles are {x, z}; returns the path from start to end covering every tile, or null. */
	public static List<int[]> solve(Set<Long> tiles, int[] start, int[] end) {
		if (!tiles.contains(key(start)) || !tiles.contains(key(end))) return null;
		List<int[]> path = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		path.add(start);
		seen.add(key(start));
		int[] budget = {200_000};
		return dfs(tiles, start, end, path, seen, budget) ? path : null;
	}

	private static boolean dfs(Set<Long> tiles, int[] at, int[] end, List<int[]> path, Set<Long> seen, int[] budget) {
		if (--budget[0] < 0) return false;
		if (path.size() == tiles.size()) return at[0] == end[0] && at[1] == end[1];
		if (at[0] == end[0] && at[1] == end[1]) return false; // reached the exit too early
		List<int[]> next = new ArrayList<>();
		for (int[] d : DIRS) {
			int[] n = {at[0] + d[0], at[1] + d[1]};
			if (tiles.contains(key(n)) && !seen.contains(key(n))) next.add(n);
		}
		next.sort((a, b) -> Integer.compare(freeNeighbours(tiles, seen, a), freeNeighbours(tiles, seen, b)));
		for (int[] n : next) {
			seen.add(key(n));
			path.add(n);
			if (dfs(tiles, n, end, path, seen, budget)) return true;
			path.removeLast();
			seen.remove(key(n));
		}
		return false;
	}

	private static int freeNeighbours(Set<Long> tiles, Set<Long> seen, int[] p) {
		int n = 0;
		for (int[] d : DIRS) {
			long k = key(new int[]{p[0] + d[0], p[1] + d[1]});
			if (tiles.contains(k) && !seen.contains(k)) n++;
		}
		return n;
	}

	public static long key(int[] p) {
		return ((long) p[0] << 32) ^ (p[1] & 0xFFFFFFFFL);
	}
}
