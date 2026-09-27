package com.scd.client.feature.slayer;

import com.scd.client.config.HudLayout;
import com.scd.client.config.ScdConfig;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudColor;
import com.scd.client.hud.HudElement;
import com.scd.client.ui.Ui;
import com.scd.logic.Numbers;
import com.scd.logic.slayer.Nameplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * One box for the whole Slayer loop: boss fight (timer, HP bar, PB, live mechanic cues), hunting
 * phase (spawn timer, RNG meter, top drops), a brief "Killed" flash, and the session stats below.
 */
final class SlayerHud extends HudElement {
	private final Supplier<ScdConfig> config;
	private final SlayerFeature slayer;

	SlayerHud(Supplier<ScdConfig> config, SlayerFeature slayer) {
		super("slayer", "Slayer", HudLayout.at(HudLayout.AnchorX.LEFT, HudLayout.AnchorY.TOP, 8, 8));
		this.config = config;
		this.slayer = slayer;
	}

	@Override
	public boolean enabled() {
		return config.get().slayer.hud || config.get().slayer.sessionStats;
	}

	@Override
	public HudBox build(boolean preview) {
		HudBox box = new HudBox().minWidth(190);
		if (preview) return sample(box);
		// Slayer quests can't progress inside the Catacombs - keep that corner for the dungeon HUD.
		if (slayer.inDungeon()) return null;
		if (config.get().slayer.hud) bossSection(box);
		if (config.get().slayer.sessionStats) statsSection(box);
		return box.isEmpty() ? null : box;
	}

	private void bossSection(HudBox box) {
		SlayerTracker t = slayer.tracker();
		SlayerQuest q = t.quest();
		if (t.cocoonRemainingMs() > 0) {
			box.title("Boss cocooned!").text("Respawning in " + (t.cocoonRemainingMs() / 1000 + 1) + "s");
			return;
		}
		if (q != null && q.bossSpawned()) {
			var boss = t.boss();
			String name = boss != null ? BossLocator.name(boss) : null;
			box.title(name != null ? stripHp(name) : q.type().bossName() + (q.tier() != null ? " " + q.tier() : ""));
			Nameplate.Health hp = t.health();
			Long pb = slayer.records().best(q.type(), q.tier());
			StringBuilder line = new StringBuilder("Fight " + Numbers.duration(t.fightElapsedMs()));
			if (hp != null) {
				Double max = t.maxHp();
				line.append("   HP ").append(Numbers.coins(hp.current(), 0)).append(max != null ? "/" + Numbers.coins(max, 0) : "");
			}
			if (pb != null) line.append("   PB ").append(Numbers.duration(pb));
			box.text(line.toString());
			for (AbilityCue cue : AbilityCue.forType(q.type())) {
				if (!config.get().slayer.cueEnabled(cue.id()) || !config.get().slayer.cueGroupEnabled(cue.type().name()) || !com.scd.logic.slayer.SlayerTier.atLeast(q.tier(), cue.minTier())) continue;
				String text = cue.text().apply(t);
				if (text != null) box.colored(text, Ui.WARNING);
			}
			Float frac = t.hpFraction();
			if (frac != null) box.bar(frac, Ui.DANGER);
			return;
		}
		if (q != null) {
			box.title(q.type().displayName() + " Slayer" + (q.tier() != null ? " " + q.tier() : ""));
			box.text("Hunting " + Numbers.duration(t.huntElapsedMs()) + (t.isHuntPaused() ? "  (paused)" : ""));
			rngLines(box, q.type());
			var drops = slayer.drops().entries(q.type());
			for (int i = 0; i < Math.min(3, drops.size()); i++) {
				var d = drops.get(i).getValue();
				box.text(d.name + ": " + Numbers.compactCount(d.count), HudColor.LABEL);
			}
			return;
		}
		SlayerType killed = t.justKilled();
		if (killed != null) box.title(killed.displayName() + " Slayer").text("Killed");
	}

	private void rngLines(HudBox box, SlayerType type) {
		RngMeter m = slayer.rng();
		String drop = m.selectedDrop(type);
		Long xp = m.storedXp(type);
		Long req = m.required(type, drop);
		Double pct = m.chancePercent(type);
		if (drop == null && xp == null && pct == null) return;
		StringBuilder s = new StringBuilder("RNG");
		if (drop != null) s.append(" · ").append(drop);
		box.text(s.toString(), HudColor.TITLE);
		if (xp != null && req != null) {
			box.text(Numbers.compactCount(xp) + " / " + Numbers.compactCount(req)
					+ String.format(Locale.ROOT, "  (%.1f%%)", Math.min(100, xp * 100.0 / req)), HudColor.TEXT);
			Float p = m.progress(type);
			if (p != null) box.bar(p, Ui.theme().accent());
		} else if (xp != null) {
			box.text(Numbers.compactCount(xp) + " stored XP" + (pct != null ? String.format(Locale.ROOT, "  (%.1f%%)", pct) : ""), HudColor.TEXT);
		} else if (pct != null) {
			box.text(String.format(Locale.ROOT, "%.1f%% - open the Slayer menu to sync", pct), HudColor.TEXT);
		}
	}

	private void statsSection(HudBox box) {
		SessionStats s = slayer.session();
		if (s.kills() == 0) return;
		if (slayer.tracker().quest() == null && !s.recentlyActive()) return;
		SlayerType last = slayer.tracker().lastActiveType();
		if (last != null && !slayer.tracker().inAllowedArea(last)) return;
		if (!box.isEmpty()) box.divider();
		box.label("Session" + (s.tier() != null ? " · " + (s.type() != null ? s.type().displayName() + " " : "") + s.tier() : ""));
		box.hero("Kills / hour", String.format(Locale.ROOT, "%.1f", s.killsPerHour()));
		List<String[]> grid = new ArrayList<>();
		grid.add(new String[] {"Kills", String.valueOf(s.kills())});
		grid.add(new String[] {"Avg fight", Numbers.duration(s.avgFightMs())});
		if (s.avgHuntMs() > 0) grid.add(new String[] {"Avg spawn", Numbers.duration(s.avgHuntMs())});
		grid.add(new String[] {"XP", s.xp() > 0 ? Numbers.compactCount(s.xp(), 100_000) : "-"});
		if (s.dropValue() > 0) {
			grid.add(new String[] {"Drops", Numbers.coins(s.dropValue())});
			grid.add(new String[] {"Drops / hr", Numbers.coins(s.dropValuePerHour())});
		}
		box.grid(grid);
		double boost = slayer.mayor().slayerXpBoostPercent();
		if (boost > 0 && slayer.mayor().slayerXpSource() != null) {
			box.text(String.format(Locale.ROOT, "+%.0f%% Slayer XP (%s)", boost, slayer.mayor().slayerXpSource()), HudColor.LABEL);
		}
	}

	private HudBox sample(HudBox box) {
		ScdConfig c = config.get();
		if (c.slayer.hud) {
			box.title("Revenant Horror IV").text("Fight 0:17   HP 248K/400K   PB 0:41")
					.colored("Enrage in ~23s", Ui.WARNING).bar(0.62f, Ui.DANGER);
		}
		if (c.slayer.sessionStats) {
			if (!box.isEmpty()) box.divider();
			box.label("Session · Zombie IV").hero("Kills / hour", "38.5")
					.grid(List.of(new String[] {"Kills", "12"}, new String[] {"Avg fight", "0:47"},
							new String[] {"Avg spawn", "0:46"}, new String[] {"XP", "7,500"},
							new String[] {"Drops", "4.2M"}, new String[] {"Drops / hr", "13.6M"}))
					.text("+25% Slayer XP (Aatrox)", HudColor.LABEL);
		}
		return box;
	}

	/** Boss nameplates end with the HP readout - the HUD shows HP on its own line already. */
	private static String stripHp(String name) {
		return name.replaceAll("\\s*[\\d.,]+[kKmMbB]?(/[\\d.,]+[kKmMbB]?)?\\s*❤.*$", "").replace("☠", "").trim();
	}
}
