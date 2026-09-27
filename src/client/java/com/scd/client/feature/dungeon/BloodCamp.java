package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.config.HudLayout;
import com.scd.client.config.ScdConfig;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.feature.world.WorldGizmos;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudElement;
import com.scd.client.ui.Ui;
import com.scd.logic.dungeon.RunMessages;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Blood camp helper for mages: when the Watcher will move ("Kill mobs"), and for each blood mob
 * head flying out of the Watcher, where it will land and how long until it spawns. Timings follow
 * Odin's measured table (mobs spawn about 1.9s after they start moving; the first wave 2s later).
 */
final class BloodCamp {
	private static final double FIRST_WAVE_DISTANCE = 16.1, LATER_DISTANCE = 11.9;
	private static final long SPAWN_AFTER_MS = 1_900, FIRST_WAVE_EXTRA_MS = 2_000;

	private static final class Flight {
		final Vec3 start;
		final long startedAt;
		final boolean firstWave;
		Vec3 end;

		Flight(Vec3 start, long startedAt, boolean firstWave) {
			this.start = start;
			this.startedAt = startedAt;
			this.firstWave = firstWave;
		}
	}

	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private final Map<Entity, Flight> flights = new IdentityHashMap<>();
	private final Map<Entity, Vec3> seenAt = new IdentityHashMap<>();
	private long bloodOpenedAt;
	private long moveAt;
	private boolean firstWave = true;

	BloodCamp(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		mod.bus.subscribe(Events.ChatReceived.class, e -> {
			if (!e.isSystem() || !dungeon.state().inDungeon()) return;
			String t = e.clean().trim();
			if (RunMessages.classify(t) == RunMessages.Event.BLOOD_OPENED && bloodOpenedAt == 0) bloodOpenedAt = System.currentTimeMillis();
			if (t.equals("[BOSS] The Watcher: Let's see how you can handle this.")) onFinalWave();
		});
		mod.bus.subscribe(Events.WorldChanged.class, e -> reset());
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (!dungeon.state().inDungeon()) {
				if (bloodOpenedAt != 0) reset();
				return;
			}
			if (moveAt != 0 && System.currentTimeMillis() >= moveAt) {
				moveAt = 0;
				if (cfg().bloodCamp) Chat.title(Component.literal("Kill mobs").withStyle(ChatFormatting.RED), Component.empty(), true);
			}
			if (cfg().bloodCamp) track();
		});
		WorldGizmos.onWorldExtract(pt -> {
			if (cfg().bloodCamp && dungeon.state().inDungeon()) draw();
		});
		mod.huds.add(new CampHud());
	}

	private ScdConfig.Dungeon cfg() {
		return mod.config().dungeon;
	}

	private void reset() {
		flights.clear();
		seenAt.clear();
		bloodOpenedAt = 0;
		moveAt = 0;
		firstWave = true;
	}

	/** Odin's table: the Watcher moves a fixed time after the last line, depending on how long blood took. */
	private void onFinalWave() {
		firstWave = false;
		if (bloodOpenedAt == 0) return;
		long seconds = (System.currentTimeMillis() - bloodOpenedAt) / 1000;
		int ticks;
		if (seconds >= 31 && seconds < 34) ticks = 36;
		else if (seconds >= 28) ticks = seconds < 31 ? 33 : (int) seconds + 3;
		else if (seconds >= 25) ticks = 30;
		else if (seconds >= 22) ticks = 27;
		else ticks = 24;
		moveAt = System.currentTimeMillis() + ticks * 50L;
		if (cfg().bloodCamp) Chat.info(Component.literal(String.format(Locale.ROOT, "Watcher moves in %.1fs", ticks / 20.0)).withStyle(ChatFormatting.RED));
	}

	private void track() {
		MappedRoom room = dungeon.rooms().current();
		if (room == null || !"Blood".equals(room.name())) return;
		var mc = Minecraft.getInstance();
		if (mc.level == null) return;
		long now = System.currentTimeMillis();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ArmorStand stand) || !stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD)) continue;
			if (dungeon.rooms().roomAt(stand.blockPosition()) != room) continue;
			Vec3 pos = stand.position();
			Vec3 before = seenAt.put(stand, pos);
			if (before == null || before.distanceToSqr(pos) < 1e-4) continue;
			Flight f = flights.computeIfAbsent(stand, k -> new Flight(before, now, firstWave));
			Vec3 dir = pos.subtract(f.start);
			if (dir.lengthSqr() > 1e-4) f.end = f.start.add(dir.normalize().scale(f.firstWave ? FIRST_WAVE_DISTANCE : LATER_DISTANCE));
		}
		flights.keySet().removeIf(e -> !e.isAlive() || e.isRemoved());
		seenAt.keySet().removeIf(e -> !e.isAlive() || e.isRemoved());
	}

	private void draw() {
		long now = System.currentTimeMillis();
		for (var entry : flights.entrySet()) {
			Flight f = entry.getValue();
			if (f.end == null) continue;
			long left = (f.firstWave ? FIRST_WAVE_EXTRA_MS : 0) + SPAWN_AFTER_MS - (now - f.startedAt);
			if (left < -1_000) continue;
			Vec3 at = entry.getKey().position();
			WorldGizmos.box(new AABB(f.end.x - 0.5, f.end.y + 1.5, f.end.z - 0.5, f.end.x + 0.5, f.end.y + 2.5, f.end.z + 0.5), 0xFFF87171, false);
			WorldGizmos.line(at.add(0, 2, 0), f.end.add(0, 2, 0), 0xFFF87171, false);
			int color = left > 1_500 ? 0xFF4ADE80 : left > 500 ? 0xFFFACC15 : 0xFFF87171;
			WorldGizmos.label(f.end.add(0, 3, 0), String.format(Locale.ROOT, "%.1fs", Math.max(0, left) / 1000.0), color, true);
		}
	}

	private final class CampHud extends HudElement {
		CampHud() {
			super("blood_camp", "Blood camp", HudLayout.at(HudLayout.AnchorX.CENTER, HudLayout.AnchorY.MIDDLE, 0, -40));
		}

		@Override
		public boolean enabled() {
			return cfg().bloodCamp;
		}

		@Override
		public HudBox build(boolean preview) {
			HudBox box = new HudBox().minWidth(90);
			if (preview) return box.colored("Watcher moves in 1.4s", Ui.DANGER);
			if (moveAt == 0) return null;
			long left = moveAt - System.currentTimeMillis();
			return box.colored(String.format(Locale.ROOT, "Watcher moves in %.1fs", Math.max(0, left) / 1000.0), Ui.DANGER);
		}
	}
}
