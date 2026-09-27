package com.scd.client.feature.mayor;

import com.scd.client.Feature;
import com.scd.client.ScdMod;
import com.scd.client.core.ScdLog;
import com.scd.client.core.Tasks;
import com.scd.client.net.Backend;
import com.scd.logic.Text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Currently active mayor/minister perks (via the backend, which polls Hypixel's election resource).
 * Perks are matched by content rather than by mayor name, so a buff still counts when it comes
 * from the minister slot or a future mayor with the same effect.
 */
public final class MayorService implements Feature {
	private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)%");
	private static final int REFRESH_TICKS = 20 * 60 * 10;

	private volatile Backend.MayorInfo info = Backend.MayorInfo.NONE;
	private volatile double slayerXpBoost;
	private volatile String slayerXpSource;

	@Override
	public void init(ScdMod mod) {
		mod.tasks.every(REFRESH_TICKS, true, "mayor refresh", () -> refresh(mod));
		mod.configManager.onChange(() -> refresh(mod));
	}

	public void refresh(ScdMod mod) {
		mod.market.mayor().thenAccept(result -> Tasks.onClient(() -> update(result)))
				.exceptionally(err -> {
					ScdLog.warn("Mayor refresh failed: " + err.getMessage());
					return null;
				});
	}

	private void update(Backend.MayorInfo newInfo) {
		info = newInfo;
		double boost = 0;
		for (var perk : newInfo.perks()) {
			String d = Text.clean(perk.description()).toLowerCase(Locale.ROOT);
			if (!d.contains("slayer") || !d.contains("xp")) continue;
			Matcher m = PERCENT.matcher(d);
			if (m.find()) {
				boost = Double.parseDouble(m.group(1));
				break;
			}
		}
		slayerXpBoost = boost;
		slayerXpSource = boost > 0 ? newInfo.mayorName() : null;
	}

	public Backend.MayorInfo info() {
		return info;
	}

	/** Slayer XP multiplier from perks (1.25 for Aatrox's +25%), 1.0 when none is active. */
	public double slayerXpMultiplier() {
		return 1.0 + slayerXpBoost / 100.0;
	}

	public double slayerXpBoostPercent() {
		return slayerXpBoost;
	}

	public String slayerXpSource() {
		return slayerXpSource;
	}

	/** True if an active perk's name contains the fragment, e.g. "EZPZ" for Paul's dungeon bonus. */
	public boolean hasPerk(String nameFragment) {
		String needle = nameFragment.toLowerCase(Locale.ROOT);
		for (var perk : info.perks()) {
			if (perk.name() != null && perk.name().toLowerCase(Locale.ROOT).contains(needle)) return true;
		}
		return false;
	}
}
