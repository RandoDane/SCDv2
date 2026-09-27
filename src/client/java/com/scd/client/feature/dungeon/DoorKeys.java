package com.scd.client.feature.dungeon;

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
 * from the dungeon map; a door whose block is gone (opened) isn't drawn.
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
		if (mc.level == null || layout == null) return;
		for (MapLayout.Door door : layout.doors()) {
			boolean wither = door.type() == MapLayout.DoorType.WITHER, blood = door.type() == MapLayout.DoorType.BLOOD;
			if (!wither && !(blood && !bloodOpened)) continue;
			int x = door.worldX(), z = door.worldZ();
			// Opened: the door blocks are gone.
			if (mc.level.getBlockState(new BlockPos(x, 70, z)).isAir()) continue;
			boolean openable = wither ? witherKeys > 0 : bloodKey;
			int color = openable ? OPENABLE : LOCKED;
			WorldGizmos.box(new AABB(x - 1, 69, z - 1, x + 2, 73, z + 2), color, true);
		}
		if (key != null && key.isAlive()) {
			WorldGizmos.box(new AABB(key.getX() - 0.5, key.getY() + 1, key.getZ() - 0.5, key.getX() + 0.5, key.getY() + 2, key.getZ() + 0.5),
					keyIsBlood ? KEY_BLOOD : KEY_WITHER, true);
		}
	}
}
