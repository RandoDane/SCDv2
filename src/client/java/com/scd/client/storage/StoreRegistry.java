package com.scd.client.storage;

import com.scd.client.core.ScdLog;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Flushes every open {@link JsonStore} every few seconds in the background, and once more on shutdown. */
public final class StoreRegistry {
	private static final List<JsonStore<?>> STORES = new CopyOnWriteArrayList<>();
	private static ScheduledExecutorService flusher;

	private StoreRegistry() {
	}

	static void register(JsonStore<?> store) {
		STORES.add(store);
	}

	public static synchronized void start() {
		if (flusher != null) return;
		flusher = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "scd-save");
			t.setDaemon(true);
			return t;
		});
		flusher.scheduleWithFixedDelay(StoreRegistry::flushAll, 3, 3, TimeUnit.SECONDS);
	}

	public static void flushAll() {
		for (JsonStore<?> store : STORES) {
			ScdLog.guard("save " + store.path().getFileName(), store::flush);
		}
	}
}
