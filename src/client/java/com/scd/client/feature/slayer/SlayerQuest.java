package com.scd.client.feature.slayer;

/** The active quest as read from the sidebar. tier may be null if the line hasn't fully rendered yet. */
public record SlayerQuest(SlayerType type, String tier, boolean bossSpawned) {
	public String label() {
		return type.displayName() + (tier != null ? " " + tier : "");
	}
}
