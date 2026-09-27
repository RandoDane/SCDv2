package com.scd.client.feature.slayer;

import java.util.List;

/** Every Slayer miniboss nameplate with its type and whether it's the rarer strong variant. Vampire has none. */
public final class Minibosses {
	public record Entry(SlayerType type, String name, boolean strong) {
	}

	public static final List<Entry> ALL = List.of(
			new Entry(SlayerType.ZOMBIE, "Revenant Sycophant", false),
			new Entry(SlayerType.ZOMBIE, "Revenant Champion", false),
			new Entry(SlayerType.ZOMBIE, "Deformed Revenant", true),
			new Entry(SlayerType.ZOMBIE, "Atoned Champion", false),
			new Entry(SlayerType.ZOMBIE, "Atoned Revenant", true),
			new Entry(SlayerType.SPIDER, "Tarantula Vermin", false),
			new Entry(SlayerType.SPIDER, "Tarantula Beast", false),
			new Entry(SlayerType.SPIDER, "Mutant Tarantula", true),
			new Entry(SlayerType.SPIDER, "Primordial Jockey", false),
			new Entry(SlayerType.SPIDER, "Primordial Viscount", true),
			new Entry(SlayerType.WOLF, "Pack Enforcer", false),
			new Entry(SlayerType.WOLF, "Sven Follower", false),
			new Entry(SlayerType.WOLF, "Sven Alpha", true),
			new Entry(SlayerType.ENDERMAN, "Voidling Devotee", false),
			new Entry(SlayerType.ENDERMAN, "Voidling Radical", false),
			new Entry(SlayerType.ENDERMAN, "Voidcrazed Maniac", true),
			new Entry(SlayerType.BLAZE, "Flare Demon", false),
			new Entry(SlayerType.BLAZE, "Kindleheart Demon", false),
			new Entry(SlayerType.BLAZE, "Burningsoul Demon", true));

	private Minibosses() {
	}

	public static Entry match(String nameplate) {
		for (Entry e : ALL) if (nameplate.contains(e.name())) return e;
		return null;
	}
}
