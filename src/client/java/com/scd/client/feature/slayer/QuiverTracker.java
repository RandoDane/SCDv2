package com.scd.client.feature.slayer;

import com.scd.client.hypixel.Items;
import com.scd.logic.Numbers;
import net.minecraft.client.Minecraft;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Explosive Arrows left, read from the SkyBlock-menu hotbar slot, which shows the equipped arrow
 * type and an "Arrows Remaining" lore line while a bow is held. Only tracked during an Enderman
 * quest (the Voidgloom weapon); the last reading is kept while the bow is put away, and dropped
 * when a different arrow type is equipped.
 */
final class QuiverTracker {
	private static final int SLOT = 8;
	private static final Pattern REMAINING = Pattern.compile("Arrows Remaining:\\s*([\\d,]+)");

	record Reading(String arrowName, int remaining) {
	}

	private Reading reading;

	void tick(SlayerQuest quest) {
		if (quest == null || quest.type() != SlayerType.ENDERMAN) {
			reading = null;
			return;
		}
		var player = Minecraft.getInstance().player;
		if (player == null) return;
		boolean bow = player.getMainHandItem().is(net.minecraft.world.item.Items.BOW) || player.getOffhandItem().is(net.minecraft.world.item.Items.BOW);
		if (!bow) return;
		var stack = player.getInventory().getItem(SLOT);
		for (String line : Items.lore(stack)) {
			Matcher m = REMAINING.matcher(line);
			if (!m.find()) continue;
			String name = Items.name(stack);
			Integer n = Numbers.parseIntOrNull(m.group(1));
			reading = name.equalsIgnoreCase("Explosive Arrow") && n != null ? new Reading(name, n) : null;
			return;
		}
	}

	Reading reading() {
		return reading;
	}
}
