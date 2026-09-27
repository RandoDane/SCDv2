package com.scd.client.feature.dungeon;

import net.minecraft.world.level.block.Blocks;
import java.util.List;
import java.util.ArrayList;
import com.scd.client.ScdMod;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.feature.world.WorldGizmos;
import com.scd.logic.dungeon.room.MapLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.regex.Pattern;

/**
 * Wither and blood doors outlined (red while you have no key for them, green once someone in the
 * party holds one), the key itself boxed when it spawns, and a "key spawned" alert. Doors come
 * from the dungeon map and, sooner, from the world itself: every door sits at a fixed spot between
 * two tiles, coal blocks for wither doors and red terracotta for blood doors, and those chunks load
 * long before the map reveals the door. A door whose block is gone (opened) isn't drawn. Holding a
 * wither key with no wither door known yet marks the fairy room's entrance instead ("Wither door"),
 * since the next wither door is usually behind it.
 */
final class DoorKeys {
	private static final Pattern WITHER_KEY = Pattern.compile("^(?:\\[[^]]*] )?\\w{1,16} has obtained Wither Key!?$|^A Wither Key was picked up!$");
	private static final Pattern WITHER_OPEN = Pattern.compile("^(?:\\[[^]]*] )?\\w{1,16} opened a WITHER door!$");
	private static final Pattern BLOOD_KEY = Pattern.compile("^(?:\\[[^]]*] )?\\w{1,16} has obtained Blood Key!$|^A Blood Key was picked up!$");
	private static final int LOCKED = 0xFFF87171, OPENABLE = 0xFF4ADE80, KEY_WITHER = 0xFF94A3B8, KEY_BLOOD = 0xFFEF4444;

	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private int witherKeys;
	private boolean bloodKey, bloodOpened;
	private Entity key;
	/** Wither/blood doors found in the world (refreshed every 10 ticks). */
	private final List<MapLayout.Door> scanned = new ArrayList<>();
	private boolean keyIsBlood;

	DoorKeys(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		mod.bus.subscribe(Events.ChatReceived.class, e -> {
			if (!e.isSystem() || !dungeon.state().inDungeon()) return;
			String t = e.clean().trim();
			if (WITHER_KEY.matcher(t).matches()) witherKeys++;
			else if (WITHER_OPEN.matcher(t).matches()) witherKeys = Math.max(0, witherKeys - 1);
			else if (BLOOD_KEY.matcher(t).matches()) bloodKey = true;
			else if (t.equals("The BLOOD DOOR has been opened!")) {
				bloodKey = false;
				bloodOpened = true;
			}
		});
		mod.bus.subscribe(Events.WorldChanged.class, e -> reset());
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (dungeon.state().inDungeon() && mod.tasks.currentTick() % 5 == 0) findKey();
			if (dungeon.state().inDungeon() && mod.tasks.currentTick() % 10 == 0) scanDoors();
			if (!dungeon.state().inDungeon() && (witherKeys > 0 || bloodOpened || key != null)) reset();
		});
		WorldGizmos.onWorldExtract(pt -> {
			if (mod.config().dungeon.doorHighlight && dungeon.state().inDungeon()) draw();
		});
	}

	private void reset() {
		witherKeys = 0;
		bloodKey = bloodOpened = false;
		key = null;
		scanned.clear();
	}

	/** Reads every door spot between loaded tiles: coal = wither door, red terracotta = blood door. */
	private void scanDoors() {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		scanned.clear();
		for (int tz = 0; tz < 6; tz++) {
			for (int tx = 0; tx < 6; tx++) {
				for (boolean horizontal : new boolean[]{true, false}) {
					if (horizontal ? tx >= 5 : tz >= 5) continue;
					MapLayout.Door probe = new MapLayout.Door(tx, tz, horizontal, MapLayout.DoorType.NORMAL);
					BlockPos pos = new BlockPos(probe.worldX(), 69, probe.worldZ());
					if (!level.isLoaded(pos)) continue;
					var block = level.getBlockState(pos).getBlock();
					MapLayout.DoorType type = block == Blocks.COAL_BLOCK ? MapLayout.DoorType.WITHER
							: block == Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.RED) ? MapLayout.DoorType.BLOOD : null;
					if (type != null) scanned.add(new MapLayout.Door(tx, tz, horizontal, type));
				}
			}
		}
	}

	private void findKey() {
		var mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		if (key != null && key.isAlive() && !key.isRemoved()) return;
		key = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ArmorStand) || e.getCustomName() == null) continue;
			String name = e.getCustomName().getString();
			boolean wither = name.equals("Wither Key"), blood = name.equals("Blood Key");
			if (!wither && !blood) continue;
			key = e;
			keyIsBlood = blood;
			if (mod.config().dungeon.keyAlert) {
				Chat.title(Component.empty(), Component.literal(name + " spawned!").withStyle(blood ? ChatFormatting.RED : ChatFormatting.DARK_GRAY), true);
			}
			return;
		}
	}

	private void draw() {
		var mc = Minecraft.getInstance();
		var layout = dungeon.rooms().layout();
		if (mc.level == null) return;
		// Map doors plus world-scanned ones (same door = same tile pair and direction).
		java.util.Map<String, MapLayout.Door> doors = new java.util.LinkedHashMap<>();
		if (layout != null) for (MapLayout.Door d : layout.doors()) doors.put(d.x() + "," + d.z() + "," + d.horizontal(), d);
		for (MapLayout.Door d : scanned) doors.putIfAbsent(d.x() + "," + d.z() + "," + d.horizontal(), d);
		boolean witherDoorKnown = false;
		for (MapLayout.Door door : doors.values()) {
			boolean wither = door.type() == MapLayout.DoorType.WITHER, blood = door.type() == MapLayout.DoorType.BLOOD;
			if (!wither && !(blood && !bloodOpened)) continue;
			int x = door.worldX(), z = door.worldZ();
			// Opened: the door blocks are gone.
			if (mc.level.getBlockState(new BlockPos(x, 70, z)).isAir()) continue;
			if (wither) witherDoorKnown = true;
			boolean openable = wither ? witherKeys > 0 : bloodKey;
			int color = openable ? OPENABLE : LOCKED;
			WorldGizmos.box(new AABB(x - 1, 69, z - 1, x + 2, 73, z + 2), color, true);
		}
		// Key in hand, next wither door not loaded yet: it's behind the fairy room, so point there.
		if (witherKeys > 0 && !witherDoorKnown) {
			for (MapLayout.Door door : doors.values()) {
				if (door.type() != MapLayout.DoorType.FAIRY) continue;
				int x = door.worldX(), z = door.worldZ();
				WorldGizmos.box(new AABB(x - 1, 69, z - 1, x + 2, 73, z + 2), OPENABLE, true);
				WorldGizmos.label(new net.minecraft.world.phys.Vec3(x + 0.5, 73.5, z + 0.5), "Wither door", OPENABLE, true);
			}
		}
		if (key != null && key.isAlive()) {
			WorldGizmos.box(new AABB(key.getX() - 0.5, key.getY() + 1, key.getZ() - 0.5, key.getX() + 0.5, key.getY() + 2, key.getZ() + 0.5),
					keyIsBlood ? KEY_BLOOD : KEY_WITHER, true);
		}
	}
}
