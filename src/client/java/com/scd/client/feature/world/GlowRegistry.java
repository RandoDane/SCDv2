package com.scd.client.feature.world;

import com.scd.client.core.ScdLog;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * "Should this entity glow, and in what color?" - a list of small strategies consulted once per
 * entity per frame by {@code EntityRendererMixin}. First non-null answer wins. Features register
 * one function each instead of editing a shared if-chain.
 */
public final class GlowRegistry {
	private static final List<Function<Entity, Integer>> SOURCES = new CopyOnWriteArrayList<>();

	private GlowRegistry() {
	}

	public static void register(Function<Entity, Integer> source) {
		SOURCES.add(source);
	}

	public static Integer colorFor(Entity entity) {
		for (Function<Entity, Integer> source : SOURCES) {
			Integer[] out = new Integer[1];
			ScdLog.guard("glow", () -> out[0] = source.apply(entity));
			if (out[0] != null) return out[0];
		}
		return null;
	}
}
