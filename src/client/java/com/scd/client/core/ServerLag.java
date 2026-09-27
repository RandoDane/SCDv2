package com.scd.client.core;

/**
 * Server lag from the time packets the server sends (about once a second): real time that passes
 * while the server's game time doesn't advance is time the server lost. {@link #lostMs()} only
 * grows (network jitter is absorbed by comparing against the best drift seen), so the lag during
 * any stretch is {@code lostMs() at end - lostMs() at start}.
 */
public final class ServerLag {
	private static long baseTicks = -1;
	private static long baseWall;
	private static long bestDrift = Long.MAX_VALUE;
	private static long lost;
	private static long lastTicks = -1;

	private ServerLag() {
	}

	/** Called on every server time packet (client thread). */
	public static void onServerTime(long gameTime) {
		long now = System.currentTimeMillis();
		if (baseTicks < 0 || gameTime < lastTicks || gameTime - lastTicks > 1_200) {
			// New world / time jump: start a fresh baseline, keep what was already lost.
			baseTicks = gameTime;
			baseWall = now;
			bestDrift = 0;
			lostSinceBase = 0;
			lastTicks = gameTime;
			return;
		}
		lastTicks = gameTime;
		long drift = (now - baseWall) - (gameTime - baseTicks) * 50;
		// The smallest drift ever seen is the zero point (packets can only arrive late, never early).
		if (drift < bestDrift) bestDrift = drift;
		long lostNow = drift - bestDrift;
		if (lostNow > lostSinceBase) {
			lost += lostNow - lostSinceBase;
			lostSinceBase = lostNow;
		}
	}

	private static long lostSinceBase;

	/** Total server time lost since the game started, in ms (monotonic). */
	public static long lostMs() {
		return lost;
	}

	/** Lag since a {@link #lostMs()} snapshot. */
	public static long since(long snapshot) {
		return Math.max(0, lost - snapshot);
	}
}
