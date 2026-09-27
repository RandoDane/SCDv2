package com.scd.client.core;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two execution contexts, kept deliberately separate:
 * <ul>
 *   <li>{@link #later} / {@link #every} - run on the client thread, counted in ticks (world state,
 *       chat, screens are only safe to touch there);</li>
 *   <li>{@link #io} - a small daemon pool for disk and network work, which must never block a frame.</li>
 * </ul>
 */
public final class Tasks {
	private record Scheduled(long dueTick, long intervalTicks, String name, Runnable action) {
	}

	private static final ExecutorService IO = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "scd-io");
		t.setDaemon(true);
		return t;
	});

	private final List<Scheduled> scheduled = new ArrayList<>();
	private long tick;

	public long currentTick() {
		return tick;
	}

	public void later(int ticks, String name, Runnable action) {
		scheduled.add(new Scheduled(tick + Math.max(1, ticks), 0, name, action));
	}

	/** Repeats every {@code intervalTicks}, first run after one interval (or immediately with runNow). */
	public void every(int intervalTicks, boolean runNow, String name, Runnable action) {
		scheduled.add(new Scheduled(runNow ? tick : tick + intervalTicks, intervalTicks, name, action));
	}

	/** Called once per client tick by the mod entrypoint. */
	public void tick() {
		tick++;
		List<Scheduled> due = new ArrayList<>();
		for (Iterator<Scheduled> it = scheduled.iterator(); it.hasNext(); ) {
			Scheduled s = it.next();
			if (s.dueTick() > tick) continue;
			it.remove();
			due.add(s);
		}
		for (Scheduled s : due) {
			ScdLog.guard(s.name(), s.action());
			if (s.intervalTicks() > 0) scheduled.add(new Scheduled(tick + s.intervalTicks(), s.intervalTicks(), s.name(), s.action()));
		}
	}

	public static void io(Runnable action) {
		IO.execute(() -> ScdLog.guard("background task", action));
	}

	/** Hops back onto the client thread (e.g. from an HTTP callback). */
	public static void onClient(Runnable action) {
		Minecraft.getInstance().execute(() -> ScdLog.guard("client callback", action));
	}
}
