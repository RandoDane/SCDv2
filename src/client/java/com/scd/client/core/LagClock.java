package com.scd.client.core;

/**
 * Shared switch + accumulator for the lag scanner, kept in core so hot paths (event bus, HUD
 * loop, mixins) can check one static field and skip all timing when the scanner is off.
 * Render/client thread only.
 */
public final class LagClock {
	/** True while the lag scanner is enabled. */
	public static volatile boolean on;
	/** CPU time SCD itself spent (events, HUDs) since the scanner last read it. */
	public static long scdNanos;

	private LagClock() {
	}
}
