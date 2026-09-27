package com.scd.client.feature.perf;

import com.scd.client.core.LagClock;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Timing sink for the lag scanner's mixins. Every probe is two {@code System.nanoTime()} calls
 * around a vanilla method, keyed by what was processed (entity type, block entity type,
 * "particles"). Only the outermost timed call counts so nested work isn't double-counted.
 * Render/client thread only; does nothing while {@link LagClock#on} is false.
 */
public final class LagProbe {
	/** Accumulated cost of one kind of thing since the scanner last drained the probe. */
	public static final class Cost {
		public long nanos;
		public int calls;
	}

	private static final Map<Object, Cost> COSTS = new IdentityHashMap<>();
	private static final long[] STACK = new long[16];
	private static int depth;

	private LagProbe() {
	}

	public static void begin() {
		if (!LagClock.on) return;
		if (depth < STACK.length) STACK[depth] = System.nanoTime();
		depth++;
	}

	public static void end(Object key) {
		if (depth == 0) return;
		depth--;
		if (depth >= STACK.length || !LagClock.on) return;
		long took = System.nanoTime() - STACK[depth];
		if (depth > 0) return; // nested: the outer call already covers it
		Cost c = COSTS.get(key);
		if (c == null) COSTS.put(key, c = new Cost());
		c.nanos += took;
		c.calls++;
	}

	/** Hands over everything collected since the last drain and starts fresh. */
	static Map<Object, Cost> drain() {
		Map<Object, Cost> out = new IdentityHashMap<>(COSTS);
		COSTS.clear();
		depth = 0; // a probe interrupted by an exception must not poison later timing
		return out;
	}
}
