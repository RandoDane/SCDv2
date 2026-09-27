package com.scd.client.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Minimal typed publish/subscribe bus. Features talk to each other through events (a boss died, a
 * dungeon run completed, a chat line arrived) instead of holding references to each other, which is
 * what let the original single 1,800-line entrypoint class grow the way it did. Every listener call
 * is isolated through {@link ScdLog#guard}, so one failing subscriber never starves the rest.
 * Always published and consumed on the client thread.
 */
public final class EventBus {
	private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

	public <E> void subscribe(Class<E> type, Consumer<? super E> listener) {
		listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
	}

	@SuppressWarnings("unchecked")
	public <E> void post(E event) {
		List<Consumer<?>> list = listeners.get(event.getClass());
		if (list == null) return;
		long start = LagClock.on ? System.nanoTime() : 0;
		for (Consumer<?> listener : list) {
			// Inline try/catch: guard() would allocate a lambda and a context string per listener per post.
			try {
				((Consumer<E>) listener).accept(event);
			} catch (Throwable t) {
				ScdLog.report(event.getClass().getSimpleName() + " listener", t);
			}
		}
		if (start != 0) LagClock.scdNanos += System.nanoTime() - start;
	}
}
