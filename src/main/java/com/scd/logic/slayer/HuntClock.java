package com.scd.logic.slayer;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Time actually spent hunting for a slayer boss: quest start to boss spawn minus the time you
 * weren't really slaying.
 *
 * <p>Two signals, both only ever caused by you:
 * <ul>
 *     <li><b>progress</b> - the quest's Combat XP on the sidebar went up (you got a kill);</li>
 *     <li><b>action</b> - you hit a mob or used an item (bow, wand, ability).</li>
 * </ul>
 * The clock runs while the last progress is recent - "recent" adapts to your own pace: 2.5x the
 * median gap between your recent progress ticks for this slayer (clamped to 6-45s, 8s until
 * there's data) - or while you acted in the last few seconds (a tanky mob gives no progress yet).
 * AFK, or others fighting nearby, doesn't count: nothing of yours happens, so the clock stops.
 * Pace is learned per slayer type (Voidgloom camping and Revenant hunting differ a lot).
 */
public final class HuntClock {
	public static final long DEFAULT_WINDOW_MS = 8_000;
	public static final long MIN_WINDOW_MS = 6_000;
	public static final long MAX_WINDOW_MS = 45_000;
	public static final long ACTION_GRACE_MS = 6_000;
	private static final int GAPS_KEPT = 30;
	/** Gaps longer than this are breaks, not pace. */
	private static final long MAX_PACE_GAP_MS = 120_000;

	private final Map<String, Deque<Long>> gaps = new HashMap<>();
	private String type;
	private long lastTick = -1;
	private long lastProgress = -1;
	private long lastAction = -1;
	private long activeMs;
	private boolean running;

	/** A quest was accepted (counts as progress: you just started). */
	public void start(String slayerType, long now) {
		type = slayerType;
		activeMs = 0;
		lastTick = now;
		lastProgress = now;
		lastAction = -1;
		running = true;
	}

	public void stop() {
		running = false;
	}

	public void progress(long now) {
		if (!running) return;
		tick(now);
		long gap = now - lastProgress;
		if (gap > 0 && gap <= MAX_PACE_GAP_MS) {
			Deque<Long> d = gaps.computeIfAbsent(type, k -> new ArrayDeque<>());
			d.addLast(gap);
			while (d.size() > GAPS_KEPT) d.removeFirst();
		}
		lastProgress = now;
	}

	public void action(long now) {
		if (!running) return;
		tick(now);
		lastAction = now;
	}

	/** Advance to {@code now}, counting the elapsed time if you were active. Call every tick. */
	public void tick(long now) {
		if (!running || lastTick < 0) return;
		long dt = now - lastTick;
		if (dt > 0) {
			// Count only the part of dt that falls inside an activity window.
			long activeUntil = Math.max(lastProgress + window(), lastAction >= 0 ? lastAction + ACTION_GRACE_MS : Long.MIN_VALUE);
			long counted = Math.min(now, activeUntil) - lastTick;
			if (counted > 0) activeMs += Math.min(counted, dt);
		}
		lastTick = now;
	}

	/** How long after your last progress the clock keeps running, from your own recent pace. */
	public long window() {
		Deque<Long> d = type != null ? gaps.get(type) : null;
		if (d == null || d.size() < 3) return DEFAULT_WINDOW_MS;
		long[] sorted = d.stream().mapToLong(Long::longValue).toArray();
		Arrays.sort(sorted);
		long median = sorted[sorted.length / 2];
		return Math.max(MIN_WINDOW_MS, Math.min(MAX_WINDOW_MS, median * 5 / 2));
	}

	public long activeMs() {
		return activeMs;
	}

	/** True while the clock is running but you currently count as idle. */
	public boolean paused(long now) {
		if (!running) return false;
		long activeUntil = Math.max(lastProgress + window(), lastAction >= 0 ? lastAction + ACTION_GRACE_MS : Long.MIN_VALUE);
		return now > activeUntil;
	}

	public boolean running() {
		return running;
	}
}
