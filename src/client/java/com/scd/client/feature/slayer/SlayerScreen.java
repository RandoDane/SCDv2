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
	protected String navKey() {
		return "slayer";
	}

	@Override
	protected void build(Rows rows) {
		ScdConfig.Slayer c = mod.config().slayer;
		rows.group("hud", "HUD", null, true, g -> {
			g.toggle("Boss tracker", "Fight/hunt timers, HP bar, mechanic cues, RNG meter", () -> c.hud, v -> c.hud = v);
			g.toggle("Session stats", "Kills/hour, average times, XP and drop value", () -> c.sessionStats, v -> c.sessionStats = v);
			g.value("Hunt timer", () -> "pauses after " + Math.round(slayer.tracker().huntWindowMs() / 1000.0) + "s without progress");
			g.note("Spawn times only count time you're actually slaying: your quest XP going up, or you hitting mobs / using items. "
					+ "The pause point adapts to your own kill pace per slayer, so AFK time (or others fighting nearby) never inflates the average.");
		});
		rows.group("alerts", "Alerts", null, true, g -> {
			g.toggle("Spawn alert", "Chat line when your boss spawns", () -> c.spawnAlert, v -> c.spawnAlert = v);
			g.toggle("Spawn title + sound", null, () -> c.spawnAlertTitle, v -> c.spawnAlertTitle = v);
			g.toggle("Kill message", "Fight time, with NEW BEST on a personal record", () -> c.killMessage, v -> c.killMessage = v);
			g.toggle("Miniboss alert", null, () -> c.minibossAlert, v -> c.minibossAlert = v);
			g.toggle("Miniboss title + sound", null, () -> c.minibossTitle, v -> c.minibossTitle = v);
		});
		rows.group("highlight", "Boss highlight", null, false, g -> {
			g.toggle("Glow", "Outline your boss through walls", () -> c.highlightGlow, v -> c.highlightGlow = v);
			g.toggle("Hitbox", null, () -> c.highlightBox, v -> c.highlightBox = v);
			g.toggle("Tracer line", null, () -> c.highlightLine, v -> c.highlightLine = v);
		});
		rows.group("tracking", "Tracking", null, false, g -> {
			g.toggle("Drop tracking", "Count drops picked up during a quest (inventory + sacks)", () -> c.dropTracking, v -> c.dropTracking = v);
			g.buttons(List.of("Reset session stats"), List.of(() -> slayer.session().reset()));
		});
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
