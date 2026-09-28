package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.config.HudLayout;
import com.scd.client.core.Events;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudElement;
import com.scd.logic.Numbers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dungeon XP this session, from the lines Hypixel prints when a run ends ("+52,302.3 Catacombs
 * Experience", "+39,767 Archer Experience"): last run, session total and per hour for Catacombs and
 * the class, shown in a HUD and on the Run stats page.
 */
public final class XpTracker {
	private static final Pattern XP = Pattern.compile("^\\+([\\d,]+(?:\\.\\d+)?) (Catacombs|Archer|Mage|Berserk|Healer|Tank) Experience$");

	public record Skill(String name, double last, double total) {
	}

	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private final Map<String, double[]> xp = new LinkedHashMap<>(); // name -> {last, total}
	private int runs;
	private long firstAt, lastAt;
	/** Time between runs longer than this doesn't count towards the per-hour rate. */
	private static final long BREAK_MS = 10 * 60_000;
	private long activeMs;

	XpTracker(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		mod.bus.subscribe(Events.ChatReceived.class, e -> {
			if (!e.isSystem()) return;
			Matcher m = XP.matcher(e.clean().trim());
			if (!m.matches()) return;
			double amount = Double.parseDouble(m.group(1).replace(",", ""));
			String skill = m.group(2);
			long now = System.currentTimeMillis();
			if (skill.equals("Catacombs")) {
				runs++;
				if (firstAt == 0) firstAt = now;
				else activeMs += Math.min(now - lastAt, BREAK_MS);
				lastAt = now;
			}
			double[] v = xp.computeIfAbsent(skill, k -> new double[2]);
			v[0] = amount;
			v[1] += amount;
		});
		mod.huds.add(new XpHud());
	}

	public int runs() {
		return runs;
	}

	public java.util.List<Skill> skills() {
		java.util.List<Skill> out = new java.util.ArrayList<>();
		xp.forEach((k, v) -> out.add(new Skill(k, v[0], v[1])));
		return out;
	}

	/** XP per hour over the time spent running (breaks over 10 minutes left out); null until two runs. */
	public Double perHour(Skill s) {
		if (runs < 2 || activeMs <= 0) return null;
		// The first run's XP was earned before we started timing, so leave it out of the rate.
		double firstRunShare = s.total() / runs;
		return (s.total() - firstRunShare) / (activeMs / 3_600_000.0);
	}

	public void reset() {
		xp.clear();
		runs = 0;
		firstAt = lastAt = activeMs = 0;
	}

	private final class XpHud extends HudElement {
		XpHud() {
			super("dungeon_xp", "Dungeon XP", HudLayout.at(HudLayout.AnchorX.LEFT, HudLayout.AnchorY.MIDDLE, 8, 60));
		}

		@Override
		public boolean enabled() {
			return mod.config().dungeon.xpTracker;
		}

		@Override
		public HudBox build(boolean preview) {
			HudBox box = new HudBox().title("Dungeon XP");
			if (preview) return box.text("Catacombs  +52.3k · 410k/h").text("Archer  +39.8k · 312k/h").text("7 runs");
			if (!dungeon.state().inDungeon() || runs == 0) return null;
			for (Skill s : skills()) {
				Double h = perHour(s);
				box.text(s.name() + "  +" + Numbers.compactCount((long) s.last()) + (h != null ? " · " + Numbers.compactCount(Math.round(h)) + "/h" : ""));
			}
			return box.text(runs + " run" + (runs == 1 ? "" : "s"));
		}
	}
}
