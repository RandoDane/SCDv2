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

	/** The online player's exact-cased name for a typed one, or null if they aren't on the server. */
	public static String resolveOnline(String typed) {
		for (String name : others()) if (name.equalsIgnoreCase(typed.trim())) return name;
		return null;
	}
}
