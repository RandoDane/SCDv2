package com.scd.client.feature.slayer;

import net.minecraft.world.entity.LivingEntity;

/** Events the Slayer tracker publishes. Fired from scoreboard/state transitions, never raw entity sightings. */
public final class SlayerEvents {
	private SlayerEvents() {
	}

	public record QuestStarted(SlayerQuest quest) {
	}

	/** huntMs: quest accepted to boss up, idle stretches excluded. */
	/** lagMs: server lag during the hunt (already inside huntMs). */
	public record BossSpawned(SlayerQuest quest, long huntMs, long lagMs) {
	}

	/** lagMs: server lag during the fight (already inside fightMs). */
	public record BossKilled(SlayerQuest quest, long fightMs, long lagMs) {
	}

	public record MinibossSpawned(Minibosses.Entry miniboss, LivingEntity entity) {
	}
}
