package com.scd.client.feature.dungeon;

import com.scd.client.config.HudLayout;
import com.scd.client.config.ScdConfig;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudColor;
import com.scd.client.hud.HudElement;
import com.scd.client.ui.Ui;
import com.scd.logic.Numbers;
import com.scd.logic.dungeon.ScoreCalculator;

import java.util.function.Supplier;

/** Live dungeon score estimate with optional breakdown lines; the score is colored by rank. */
final class ScoreHud extends HudElement {
	private final Supplier<ScdConfig> config;
	private final Supplier<DungeonRun.Score> score;

	ScoreHud(Supplier<ScdConfig> config, Supplier<DungeonRun.Score> score) {
		// Shares the Slayer HUD's corner by default - the two never show at the same time.
		super("dungeon_score", "Dungeon score", HudLayout.at(HudLayout.AnchorX.LEFT, HudLayout.AnchorY.TOP, 8, 8));
		this.config = config;
		this.score = score;
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
			if (c.scoreRoomsSecrets) box.text("Rooms 28/30   Secrets 71.3%", HudColor.TEXT);
			if (c.scoreCryptsDeathsPuzzles) box.text("Crypts 5  Deaths 0  Puzzles left 0", HudColor.TEXT);
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
			box.text("Rooms " + s.completedRooms() + "/" + b.totalRoomsEstimate() + "   Secrets " + Numbers.percent(s.secretsPercent(), 1), HudColor.TEXT);
		}
		if (c.scoreCryptsDeathsPuzzles) {
			box.text("Crypts " + s.crypts() + "  Deaths " + s.deaths() + "  Puzzles left " + s.incompletePuzzles(), HudColor.TEXT);
		}
		return box;
	}

	static int rankColor(int score) {
		if (score >= 300) return 0xFFFFAA00;
		if (score >= 270) return 0xFFFFFF55;
		if (score >= 230) return 0xFF55FF55;
		if (score >= 160) return 0xFF55FFFF;
		return Ui.DANGER;
	}
}
