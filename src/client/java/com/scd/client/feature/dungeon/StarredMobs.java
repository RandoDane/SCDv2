package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.config.HudLayout;
import com.scd.client.core.Events;
import com.scd.client.feature.world.WorldGizmos;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.List;

/**
 * Starred mobs: Hypixel floats a "✯ Name 500k❤" tag over each one. The mob under every live tag
 * gets a box (optionally through walls), and a small HUD counts how many are left in your room.
 */
final class StarredMobs {
	private static final int COLOR = 0xFFFACC15;

	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private final List<Entity> mobs = new ArrayList<>();
	private int inRoom;

	StarredMobs(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (mod.tasks.currentTick() % 5 == 0) scan();
		});
		WorldGizmos.onWorldExtract(pt -> {
			if (!mod.config().dungeon.starredMobs || !dungeon.state().inDungeon() || dungeon.scoreInBoss()) return;
			boolean walls = mod.config().dungeon.starredThroughWalls;
			for (Entity m : mobs) if (m.isAlive()) WorldGizmos.box(m, pt, COLOR, walls);
		});
		mod.huds.add(new CountHud());
	}

	private void scan() {
		mobs.clear();
		inRoom = 0;
		var mc = Minecraft.getInstance();
		if (!mod.config().dungeon.starredMobs || !dungeon.state().inDungeon() || mc.level == null) return;
		MappedRoom here = dungeon.rooms().current();
		List<ArmorStand> tags = new ArrayList<>();
		List<LivingEntity> living = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof ArmorStand s) {
				var name = s.getCustomName();
				if (name != null) {
					String n = name.getString();
					// Dead ones keep their tag for a moment at "0❤".
					if (n.contains("✯") && !n.endsWith(" 0❤")) tags.add(s);
				}
			} else if (e instanceof LivingEntity l && e != mc.player && l.isAlive()) {
				living.add(l);
			}
		}
		for (ArmorStand tag : tags) {
			// The mob stands right under its tag.
			LivingEntity best = null;
			double bestD = 9;
			for (LivingEntity l : living) {
				double dx = l.getX() - tag.getX(), dz = l.getZ() - tag.getZ(), dy = tag.getY() - l.getY();
				if (dy < -0.5 || dy > 4) continue;
				double d = dx * dx + dz * dz;
				if (d < bestD) {
					bestD = d;
					best = l;
				}
			}
			if (best == null) continue;
			mobs.add(best);
			if (here != null && dungeon.rooms().roomAt(best.blockPosition()) == here) inRoom++;
		}
	}

	private final class CountHud extends HudElement {
		CountHud() {
			super("starred_mobs", "Starred mobs", HudLayout.at(HudLayout.AnchorX.CENTER, HudLayout.AnchorY.MIDDLE, 0, 30));
		}

		@Override
		public boolean enabled() {
			return mod.config().dungeon.starredMobs && mod.config().dungeon.starredHud;
		}

		@Override
		public HudBox build(boolean preview) {
			if (preview) return new HudBox().colored("✯ 3 starred mobs left", COLOR);
			if (!dungeon.state().inDungeon() || dungeon.scoreInBoss() || dungeon.rooms().current() == null || inRoom == 0) return null;
			return new HudBox().colored("✯ " + inRoom + " starred mob" + (inRoom == 1 ? "" : "s") + " left", COLOR);
		}
	}
}
