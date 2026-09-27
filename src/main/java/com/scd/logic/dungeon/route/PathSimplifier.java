package com.scd.logic.dungeon.route;

import java.util.ArrayList;
import java.util.List;

/**
 * Ramer-Douglas-Peucker line simplification: keeps the points where the path really turns (or
 * changes height) and drops the ones within {@code tolerance} blocks of the straight line between
 * their neighbours. Recorded paths are sampled every couple of blocks while you strafe, jump and
 * cut corners; drawn raw they zigzag, simplified they read as a few straight segments.
 */
public final class PathSimplifier {
	private PathSimplifier() {
	}

	/** @param points {x, y, z} points; the first and last are always kept */
	public static List<double[]> simplify(List<double[]> points, double tolerance) {
		if (points.size() <= 2 || tolerance <= 0) return new ArrayList<>(points);
		boolean[] keep = new boolean[points.size()];
		keep[0] = keep[points.size() - 1] = true;
		mark(points, 0, points.size() - 1, tolerance, keep);
		List<double[]> out = new ArrayList<>();
		for (int i = 0; i < points.size(); i++) if (keep[i]) out.add(points.get(i));
		return out;
	}

	private static void mark(List<double[]> pts, int from, int to, double tolerance, boolean[] keep) {
		if (to - from < 2) return;
		double max = -1;
		int index = -1;
		for (int i = from + 1; i < to; i++) {
			double d = distanceToSegment(pts.get(i), pts.get(from), pts.get(to));
			if (d > max) {
				max = d;
				index = i;
			}
		}
		if (max > tolerance) {
			keep[index] = true;
			mark(pts, from, index, tolerance, keep);
			mark(pts, index, to, tolerance, keep);
		}
	}

	static double distanceToSegment(double[] p, double[] a, double[] b) {
		double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
		double len2 = dx * dx + dy * dy + dz * dz;
		double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy + (p[2] - a[2]) * dz) / len2));
		double cx = a[0] + t * dx - p[0], cy = a[1] + t * dy - p[1], cz = a[2] + t * dz - p[2];
		return Math.sqrt(cx * cx + cy * cy + cz * cz);
	}
}
