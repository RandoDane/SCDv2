package com.scd.client.feature.slayer;

import com.scd.logic.slayer.Nameplate;
import com.scd.logic.slayer.SlayerTier;

import java.util.List;
import java.util.function.Function;

/**
 * Boss mechanic call-outs, as data: each cue has a stable id (its config toggle), the type and
 * minimum tier it applies to, and a function returning the line to show (or null). Only mechanics
 * with a real, detectable trigger are here - a fixed interval, an HP threshold crossing, a second
 * named entity, or nameplate state. Mechanics whose only signal is an animation or particle (Fire
 * Pillar's exact throw, Yang Glyphs, Killer Springs, ...) are deliberately left out rather than
 * faked with guessed timers.
 */
public record AbilityCue(String id, SlayerType type, String label, String minTier, Function<SlayerTracker, String> text) {

	public static final List<AbilityCue> ALL = List.of(
			new AbilityCue("zombie.enrage", SlayerType.ZOMBIE, "Enrage timer", "III",
					t -> countdown("Enrage", t.fightElapsedMs(), 40_000)),
			new AbilityCue("vampire.twinclaw", SlayerType.VAMPIRE, "Twinclaw timer", "II",
					t -> countdown("Twinclaw", t.fightElapsedMs(), 7_000)),
			new AbilityCue("vampire.mania", SlayerType.VAMPIRE, "Mania warning", "I", t -> {
				boolean high = SlayerTier.atLeast(t.quest().tier(), "III");
				boolean on = high ? t.isAlertActive("vamp_mania_75") || t.isAlertActive("vamp_mania_40") : t.isAlertActive("vamp_mania_50");
				return on ? "Mania! Stand in the green zone" : null;
			}),
			new AbilityCue("spider.egg_sacs", SlayerType.SPIDER, "Egg sac warning", "III",
					t -> t.isAlertActive("spider_egg_66") || t.isAlertActive("spider_egg_33") ? "Egg sacs spawning - destroy them" : null),
			new AbilityCue("spider.conjoined_brood", SlayerType.SPIDER, "Conjoined Brood warning", "V",
					t -> t.isAlertActive("spider_conjoined_transition") ? "Not dead yet - Conjoined Brood incoming!" : null),
			new AbilityCue("blaze.fire_pillars", SlayerType.BLAZE, "Fire Pillar warning", "II", t -> {
				Float f = t.hpFraction();
				return f != null && f <= 0.5f ? "Fire Pillars active - destroy within 7s" : null;
			}),
			new AbilityCue("blaze.demonsplit", SlayerType.BLAZE, "Demonsplit warning", "I",
					t -> BossLocator.anyNamedNearby("Quazii") && BossLocator.anyNamedNearby("Typhoeus") ? "Demonsplit - only one half is damageable" : null),
			new AbilityCue("enderman.beam_phase", SlayerType.ENDERMAN, "Beam phase warning", "IV",
					t -> t.isAlertActive("ender_beam_5_6") || t.isAlertActive("ender_beam_1_2") || t.isAlertActive("ender_beam_1_6") ? "Beam phase starting!" : null),
			new AbilityCue("enderman.hitshield", SlayerType.ENDERMAN, "Hitshield indicator", "I", t -> {
				Nameplate.Shield s = t.shield();
				if (!s.active()) return null;
				return "Hitshield" + (s.hitsRemaining() != null ? " (" + s.hitsRemaining() + " hits)" : "") + " - don't waste damage";
			}),
			new AbilityCue("wolf.call_the_pups", SlayerType.WOLF, "Call the Pups warning", "III",
					t -> t.isAlertActive("wolf_pups") ? "Call the Pups! Boss protected ~5s" : null));

	public static List<AbilityCue> forType(SlayerType type) {
		return ALL.stream().filter(c -> c.type() == type).toList();
	}

	private static String countdown(String name, long elapsed, long interval) {
		long remaining = interval - (elapsed % interval);
		return name + " in ~" + (remaining / 1000) + "s";
	}
}
