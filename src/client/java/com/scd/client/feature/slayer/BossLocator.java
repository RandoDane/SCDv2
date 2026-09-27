package com.scd.client.feature.slayer;

import com.scd.logic.Text;
import com.scd.logic.slayer.Nameplate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds Slayer boss entities. Most bosses carry their nameplate on a separate armor stand stacked
 * above the (unnamed) mob, together with a "Spawned by: &lt;IGN&gt;" line - the only reliable way to
 * tell your boss apart from another player's identically-named one on a shared island. Used both
 * for the local player's boss and for carry customers' bosses.
 */
public final class BossLocator {
	public static final double SCAN_RADIUS = 30.0;
	private static final double STACK_RADIUS = 5.0;
	private static final Pattern SPAWNED_BY = Pattern.compile("^Spawned by:\\s*(\\w{1,16})$", Pattern.CASE_INSENSITIVE);

	private BossLocator() {
	}

	public static String name(Entity e) {
		return e.hasCustomName() && e.getCustomName() != null ? Text.clean(e.getCustomName().getString()) : null;
	}

	/** A live (non-corpse) nameplate matching this type's boss names. */
	public static boolean isBossNameplate(Entity e, SlayerType type) {
		String n = name(e);
		return n != null && type.matchesNameplate(n) && !Nameplate.isDead(n);
	}

	/** The boss carrying the "Spawned by: owner" tag, or null if no such tag is in range right now. */
	public static LivingEntity owned(SlayerType type, String ownerIgn) {
		var mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return null;
		Entity tag = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			String n = name(e);
			if (n == null || e.distanceTo(mc.player) > SCAN_RADIUS) continue;
			Matcher m = SPAWNED_BY.matcher(n);
			if (m.matches() && m.group(1).equalsIgnoreCase(ownerIgn)) {
				tag = e;
				break;
			}
		}
		if (tag == null) return null;
		LivingEntity best = null;
		double bestDist = STACK_RADIUS;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || !living.isAlive() || !isBossNameplate(e, type)) continue;
			double d = living.distanceTo(tag);
			if (d < bestDist) {
				bestDist = d;
				best = living;
			}
		}
		return best;
	}

	/** Closest matching boss nameplate to the player. */
	public static LivingEntity closest(SlayerType type) {
		var mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return null;
		LivingEntity best = null;
		double bestDist = SCAN_RADIUS;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || !living.isAlive() || !isBossNameplate(e, type)) continue;
			double d = living.distanceTo(mc.player);
			if (d < bestDist) {
				bestDist = d;
				best = living;
			}
		}
		return best;
	}

	/** Living, non-armor-stand entities within range whose nameplate contains {@code fragment}. */
	public static boolean anyNamedNearby(String fragment) {
		var mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return false;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity l) || e instanceof ArmorStand || !l.isAlive()) continue;
			if (l.distanceTo(mc.player) > SCAN_RADIUS) continue;
			String n = name(e);
			if (n != null && n.contains(fragment)) return true;
		}
		return false;
	}
}
