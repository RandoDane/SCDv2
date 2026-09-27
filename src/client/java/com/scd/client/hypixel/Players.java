package com.scd.client.hypixel;

import com.scd.logic.Ign;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Real players on the current server, from the tab list's profiles (Hypixel pads it with fake "!A-a" rows). */
public final class Players {
	private Players() {
	}

	public static String selfName() {
		var player = Minecraft.getInstance().player;
		return player != null ? player.getGameProfile().name() : Minecraft.getInstance().getUser().getName();
	}

	/** Every other real, listed player, sorted case-insensitively. */
	public static List<String> others() {
		var player = Minecraft.getInstance().player;
		if (player == null || player.connection == null) return List.of();
		String self = player.getGameProfile().name();
		return player.connection.getListedOnlinePlayers().stream()
				.map(info -> info.getProfile().name())
				.filter(Ign::isValid)
				.filter(name -> !name.equalsIgnoreCase(self))
				.distinct()
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	/**
	 * Lobby players for a name field: names starting with {@code prefix} first, then ones containing
	 * it; within each group the nearest players first, then the rest of the tab list.
	 */
	public static List<String> suggest(String prefix, int limit) {
		var mc = Minecraft.getInstance();
		var player = mc.player;
		if (player == null) return List.of();
		java.util.Map<String, Double> distance = new java.util.HashMap<>();
		if (mc.level != null) for (var p : mc.level.players()) {
			distance.put(p.getGameProfile().name().toLowerCase(java.util.Locale.ROOT), p.distanceToSqr(player));
		}
		String q = prefix == null ? "" : prefix.trim().toLowerCase(java.util.Locale.ROOT);
		return others().stream()
				.filter(n -> n.toLowerCase(java.util.Locale.ROOT).contains(q))
				.sorted(java.util.Comparator.<String>comparingInt(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(q) ? 0 : 1)
						.thenComparingDouble(n -> distance.getOrDefault(n.toLowerCase(java.util.Locale.ROOT), Double.MAX_VALUE)))
				.limit(limit)
				.toList();
	}

	/** The online player's exact-cased name for a typed one, or null if they aren't on the server. */
	public static String resolveOnline(String typed) {
		for (String name : others()) if (name.equalsIgnoreCase(typed.trim())) return name;
		return null;
	}
}
