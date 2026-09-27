package com.scd.client.feature.slayer;

import com.scd.client.ScdMod;
import com.scd.client.config.ScdConfig;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.logic.Numbers;
import com.scd.logic.slayer.SlayerTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Slayer settings: shared options first, then one collapsible section per boss with its cues, records and drops. */
public final class SlayerScreen extends ScdScreen {
	private final ScdMod mod;
	private final SlayerFeature slayer;

	public SlayerScreen(Screen parent, ScdMod mod, SlayerFeature slayer) {
		super("Slayer", parent);
		this.mod = mod;
		this.slayer = slayer;
	}

	@Override
	protected String subtitle() {
		SlayerQuest q = slayer.tracker().quest();
		return q != null ? q.label() + (q.bossSpawned() ? " · fighting" : " · hunting") : null;
	}

	@Override
	protected void onClosing() {
		mod.configManager.save();
	}

	@Override
	protected void build(Rows rows) {
		ScdConfig.Slayer c = mod.config().slayer;
		rows.header("HUD");
		rows.toggle("Boss tracker", "Fight/hunt timers, HP bar, mechanic cues, RNG meter", () -> c.hud, v -> c.hud = v);
		rows.toggle("Session stats", "Kills/hour, average times, XP and drop value", () -> c.sessionStats, v -> c.sessionStats = v);
		rows.slider("Hunt timer pauses after", 2, 30, 1, () -> c.huntIdleSeconds, v -> c.huntIdleSeconds = (int) Math.round(v),
				v -> Math.round(v) + "s idle");

		rows.header("Alerts");
		rows.toggle("Spawn alert", "Chat line when your boss spawns", () -> c.spawnAlert, v -> c.spawnAlert = v);
		rows.toggle("Spawn title + sound", null, () -> c.spawnAlertTitle, v -> c.spawnAlertTitle = v);
		rows.toggle("Kill message", "Fight time, with NEW BEST on a personal record", () -> c.killMessage, v -> c.killMessage = v);
		rows.toggle("Miniboss alert", null, () -> c.minibossAlert, v -> c.minibossAlert = v);
		rows.toggle("Miniboss title + sound", null, () -> c.minibossTitle, v -> c.minibossTitle = v);

		rows.header("Boss highlight");
		rows.toggle("Glow", "Outline your boss through walls", () -> c.highlightGlow, v -> c.highlightGlow = v);
		rows.toggle("Hitbox", null, () -> c.highlightBox, v -> c.highlightBox = v);
		rows.toggle("Tracer line", null, () -> c.highlightLine, v -> c.highlightLine = v);

		rows.header("Tracking");
		rows.toggle("Drop tracking", "Count drops picked up during a quest (inventory + sacks)", () -> c.dropTracking, v -> c.dropTracking = v);
		rows.buttons(List.of("Reset session stats"), List.of(() -> slayer.session().reset()));

		rows.header("Bosses");
		for (SlayerType type : SlayerType.values()) {
			rows.section("slayer." + type.name(), type.bossName(), () -> slayer.tracker().phase(type), body -> {
				for (AbilityCue cue : AbilityCue.forType(type)) {
					body.toggle(cue.label(), "Tier " + cue.minTier() + "+", () -> c.cueEnabled(cue.id()), v -> c.cues.put(cue.id(), v));
				}
				if (type == SlayerType.ENDERMAN) {
					body.toggle("Explosive Arrow counter", "Arrows left while holding a bow", () -> c.explosiveArrowCounter, v -> c.explosiveArrowCounter = v);
				}
				StringBuilder pbs = new StringBuilder();
				for (String tier : SlayerTier.ALL) {
					Long best = slayer.records().best(type, tier);
					if (best != null) pbs.append(tier).append(' ').append(Numbers.duration(best)).append("   ");
				}
				body.value("Personal bests", () -> pbs.isEmpty() ? "none yet" : pbs.toString().trim());
				RngMeter m = slayer.rng();
				body.value("RNG meter", () -> {
					Long xp = m.storedXp(type);
					String drop = m.selectedDrop(type);
					return xp == null ? "open the Slayer menu" : Numbers.compactCount(xp) + " XP" + (drop != null ? " → " + drop : "");
				});
				int count = slayer.drops().entries(type).size();
				body.button(count > 0 ? "Drops (" + count + ")..." : "Drops...",
						() -> Minecraft.getInstance().gui.setScreen(new DropsScreen(this, slayer, type)));
			});
		}
	}
}
