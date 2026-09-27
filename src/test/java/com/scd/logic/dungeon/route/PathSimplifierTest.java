package com.scd.logic.dungeon.route;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PathSimplifierTest {
	@Test
	void wobblyStraightWalkBecomesOneSegmentButCornersStay() {
		// Walk east with +-0.6 block wobble, then turn north.
		List<double[]> walk = List.of(new double[]{0, 70, 0}, new double[]{2.4, 70, 0.6}, new double[]{4.8, 70, -0.5},
				new double[]{7.2, 71, 0.4}, new double[]{9.6, 70, 0}, new double[]{9.8, 70, 2.4}, new double[]{10.2, 70, 4.8},
				new double[]{9.9, 70, 7.2});
		List<double[]> out = PathSimplifier.simplify(walk, 1.5);
		assertEquals(3, out.size(), "start, the corner and the end");
		assertArrayEquals(new double[]{9.6, 70, 0}, out.get(1));
		assertEquals(walk.size(), PathSimplifier.simplify(walk, 0).size());
		assertEquals(2, PathSimplifier.simplify(List.of(new double[]{0, 0, 0}, new double[]{1, 1, 1}), 5).size());
	}

	@Test
	void heightChangesAreKept() {
		// A drop down a hole is a real feature of the route.
		List<double[]> drop = List.of(new double[]{0, 70, 0}, new double[]{3, 70, 0}, new double[]{3.5, 62, 0}, new double[]{6, 62, 0});
		assertEquals(4, PathSimplifier.simplify(drop, 1.5).size());
	}
}
