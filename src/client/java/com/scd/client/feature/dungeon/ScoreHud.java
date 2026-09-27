package com.scd.client.feature.dungeon;

import com.scd.client.config.HudLayout;
import com.scd.client.config.ScdConfig;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudColor;
import com.scd.client.hud.HudElement;
import com.scd.client.ui.Ui;
import com.scd.logic.Numbers;
import com.scd.logic.dungeon.ScoreCalculator;

import java.util.List;
import java.util.function.Supplier;

/** Live dungeon score estimate with optional breakdown lines; the score is colored by rank. */
final class ScoreHud extends HudElement {
	private final Supplier<ScdConfig> config;
	private final Supplier<DungeonRun.Score> score;
	private final Supplier<List<String>> splits;
	private final Supplier<String> pacing;

	ScoreHud(Supplier<ScdConfig> config, Supplier<DungeonRun.Score> score, Supplier<List<String>> splits, Supplier<String> pacing) {
		// Shares the Slayer HUD's corner by default - the two never show at the same time.
		super("dungeon_score", "Dungeon score", HudLayout.at(HudLayout.AnchorX.LEFT, HudLayout.AnchorY.TOP, 8, 8));
		this.config = config;
		this.score = score;
		this.splits = splits;
		this.pacing = pacing;
	}

	@Override
	public boolean enabled() {
		return config.get().dungeon.scoreHud;
	}

	@Override
	public HudBox build(boolean preview) {
		ScdConfig.Dungeon c = config.get().dungeon;
		HudBox box = new HudBox().minWidth(150);
		if (preview) {
			box.colored("Score 273  (S)", rankColor(273)).text("F7 · 4:12");
			if (c.scoreBreakdown) box.text("Skill 98  Explore 95  Speed 100  Bonus 5", HudColor.LABEL);
			if (c.scoreRoomsSecrets) {
				box.text("Rooms 28/30   Secrets 37/52", HudColor.TEXT);
				box.text("S+ at 44 secrets (7 more)", HudColor.LABEL);
			}
			if (c.scoreCryptsDeathsPuzzles) box.text("Crypts 5  Deaths 0  Puzzles left 0  Mimic ✔", HudColor.TEXT);
			if (c.scoreSplits) box.text("Blood open 0:48  -0:03", HudColor.LABEL).text("Watcher PB 1:52", HudColor.LABEL);
			return box;
		}
		DungeonRun.Score s = score.get();
		if (s == null) return null;
		var b = s.breakdown();
		String rank = ScoreCalculator.rank(s.total());
		box.colored("Score " + s.total() + "  (" + rank + ")" + (b.entrance() ? "  x0.7" : ""), rankColor(s.total()));
		String time = s.state().elapsedSeconds() != null ? Numbers.duration(s.state().elapsedSeconds() * 1000L) : "-";
		box.text(s.state().floor() + " · " + time + (s.inBoss() ? " · boss" : ""), HudColor.TITLE);
		if (c.scoreBreakdown) {
			box.text("Skill " + b.skill() + "  Explore " + b.explore() + "  Speed " + b.speed() + "  Bonus " + b.bonus(), HudColor.LABEL);
		}
		if (c.scoreRoomsSecrets) {
			String secrets = s.secretsFound() >= 0 && b.totalSecrets() > 0
					? s.secretsFound() + "/" + (b.exactSecrets() ? "" : "~") + b.totalSecrets()
					: Numbers.percent(s.secretsPercent(), 1);
			box.text("Rooms " + s.completedRooms() + "/" + b.totalRoomsEstimate() + "   Secrets " + secrets, HudColor.TEXT);
			String need = secretsNeed(s, b);
			if (need != null) box.text(need, HudColor.LABEL);
			// Where to get them, while they're still needed.
			String where = need != null && need.contains("more") ? pacing.get() : null;
			if (where != null) box.text("  " + where, HudColor.LABEL);
		}
		if (c.scoreCryptsDeathsPuzzles) {
			String mimic = ScoreCalculator.hasMimic(s.state().floor()) ? (s.mimic() ? "  Mimic ✔" : "  Mimic ✖") : "";
			box.text("Crypts " + s.crypts() + "  Deaths " + s.deaths() + "  Puzzles left " + s.incompletePuzzles() + mimic, HudColor.TEXT);
		}
		for (String line : splits.get()) box.text(line, HudColor.LABEL);
		return box;
	}

	/** "S+ at 44 secrets (7 more)" - the next rank that secrets can still decide. */
	private static String secretsNeed(DungeonRun.Score s, ScoreCalculator.Breakdown b) {
		if (s.secretsFound() < 0 || b.totalSecrets() <= 0) return null;
		for (int i = 0; i < 2; i++) {
			int need = i == 0 ? b.secretsForS() : b.secretsForSPlus();
			String rank = i == 0 ? "S" : "S+";
			if (need < 0) continue;
			if (need > b.totalSecrets()) return rank + " out of reach by secrets";
			if (need > s.secretsFound()) return rank + " at " + need + " secrets (" + (need - s.secretsFound()) + " more)";
		}
		return "S+ secured by secrets";
	}

	static int rankColor(int score) {
		if (score >= 300) return 0xFFFFAA00;
		if (score >= 270) return 0xFFFFFF55;
		if (score >= 230) return 0xFF55FF55;
		if (score >= 160) return 0xFF55FFFF;
		return Ui.DANGER;
	}
}
