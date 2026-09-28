package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.config.HudLayout;
import com.scd.client.core.Events;
import com.scd.client.feature.world.WorldGizmos;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Boss-room timers:
 * <ul>
 *     <li><b>Sadan (F6/M6)</b> - a dead terracotta leaves a flower pot and respawns 15s later (12s on
 *     Master Mode); the countdown floats over each one.</li>
 *     <li><b>Thorn (F4/M4)</b> - each spirit animal killed lights one of 25 (30 on M4) coal blocks
 *     around the arena; when the last one lights the Spirit Bear spawns 3.4s later. A HUD shows the
 *     kill count, the countdown, then "Alive!".</li>
 * </ul>
 */
final class BossHelpers {
	private static final Set<BlockPos> F4_BLOCKS = Set.of(
			new BlockPos(-3, 77, 33), new BlockPos(-9, 77, 31), new BlockPos(-16, 77, 26), new BlockPos(-20, 77, 20), new BlockPos(-23, 77, 13),
			new BlockPos(-24, 77, 6), new BlockPos(-24, 77, 0), new BlockPos(-22, 77, -7), new BlockPos(-18, 77, -13), new BlockPos(-12, 77, -19),
			new BlockPos(-5, 77, -22), new BlockPos(1, 77, -24), new BlockPos(8, 77, -24), new BlockPos(14, 77, -23), new BlockPos(21, 77, -19),
			new BlockPos(27, 77, -14), new BlockPos(31, 77, -8), new BlockPos(33, 77, -1), new BlockPos(34, 77, 5), new BlockPos(33, 77, 12),
			new BlockPos(31, 77, 19), new BlockPos(27, 77, 25), new BlockPos(20, 77, 30), new BlockPos(14, 77, 33), new BlockPos(7, 77, 34));
	private static final Set<BlockPos> M4_BLOCKS = Set.of(
			new BlockPos(-2, 77, 33), new BlockPos(-7, 77, 32), new BlockPos(-13, 77, 28), new BlockPos(-17, 77, 24), new BlockPos(-21, 77, 18),
			new BlockPos(-23, 77, 13), new BlockPos(-24, 77, 7), new BlockPos(-24, 77, 2), new BlockPos(-23, 77, -4), new BlockPos(-21, 77, -9),
			new BlockPos(-17, 77, -14), new BlockPos(-12, 77, -19), new BlockPos(-6, 77, -22), new BlockPos(-1, 77, -23), new BlockPos(5, 77, -24),
			new BlockPos(10, 77, -24), new BlockPos(16, 77, -22), new BlockPos(21, 77, -19), new BlockPos(27, 77, -15), new BlockPos(30, 77, -10),
			new BlockPos(32, 77, -5), new BlockPos(34, 77, 1), new BlockPos(34, 77, 7), new BlockPos(33, 77, 12), new BlockPos(31, 77, 18),
			new BlockPos(28, 77, 23), new BlockPos(23, 77, 28), new BlockPos(18, 77, 31), new BlockPos(12, 77, 33), new BlockPos(7, 77, 34));
	/** The block that lights last, when the bear starts spawning. */
	private static final BlockPos LAST = new BlockPos(7, 77, 34);

	private record Terracotta(BlockPos pos, long respawnAt) {
	}

	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private final List<Terracotta> terracottas = new ArrayList<>();
	private int bearKills;
	private boolean lastLit;
	/** 0 = not spawning; otherwise when the bear appears. */
	private long bearAt;

	BossHelpers(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		mod.bus.subscribe(Events.FlowerPotPlaced.class, e -> {
			if (!mod.config().dungeon.bossTimers || !onFloor(6)) return;
			if (terracottas.stream().noneMatch(t -> t.pos().equals(e.pos()))) {
				terracottas.add(new Terracotta(e.pos(), System.currentTimeMillis() + (master() ? 12_000 : 15_000)));
			}
		});
		mod.bus.subscribe(Events.WorldChanged.class, e -> {
			terracottas.clear();
			bearKills = 0;
			lastLit = false;
			bearAt = 0;
		});
		mod.bus.subscribe(Events.Tick.class, e -> {
			long now = System.currentTimeMillis();
			terracottas.removeIf(t -> t.respawnAt() <= now);
			if (mod.config().dungeon.bossTimers && onFloor(4)) bear();
		});
		WorldGizmos.onWorldExtract(pt -> {
			if (!mod.config().dungeon.bossTimers || terracottas.isEmpty()) return;
			long now = System.currentTimeMillis();
			for (Terracotta t : terracottas) {
				double left = (t.respawnAt() - now) / 1000.0;
				int color = left > 5 ? 0xFF55FF55 : left > 2 ? 0xFFFFAA00 : 0xFFFF5555;
				WorldGizmos.label(Vec3.atCenterOf(t.pos()).add(0, 0.6, 0), String.format(Locale.ROOT, "%.1fs", left), color, false);
			}
		});
		mod.huds.add(new BearHud());
	}

	private boolean master() {
		String f = dungeon.state().floor();
		return f != null && f.startsWith("M");
	}

	private boolean onFloor(int n) {
		String f = dungeon.state().floor();
		return dungeon.state().inDungeon() && dungeon.scoreInBoss() && f != null && f.length() == 2 && f.charAt(1) - '0' == n;
	}

	private void bear() {
		var level = Minecraft.getInstance().level;
		if (level == null || !level.isLoaded(LAST)) return;
		int lit = 0;
		for (BlockPos p : master() ? M4_BLOCKS : F4_BLOCKS) if (level.getBlockState(p).getBlock() == Blocks.SEA_LANTERN) lit++;
		bearKills = lit;
		boolean nowLit = level.getBlockState(LAST).getBlock() == Blocks.SEA_LANTERN;
		if (nowLit && !lastLit) bearAt = System.currentTimeMillis() + 3_400;
		if (!nowLit) bearAt = 0;
		lastLit = nowLit;
	}

	private final class BearHud extends HudElement {
		BearHud() {
			super("spirit_bear", "Spirit Bear", HudLayout.at(HudLayout.AnchorX.CENTER, HudLayout.AnchorY.MIDDLE, 0, -80));
		}

		@Override
		public boolean enabled() {
			return mod.config().dungeon.bossTimers;
		}

		@Override
		public HudBox build(boolean preview) {
			if (preview) return new HudBox().colored("Bear: 18/25", 0xFFFFAA00);
			if (!onFloor(4)) return null;
			long left = bearAt - System.currentTimeMillis();
			String text = bearAt == 0 ? bearKills + "/" + (master() ? 30 : 25) : left > 0 ? String.format(Locale.ROOT, "%.1fs", left / 1000.0) : "Alive!";
			int color = bearAt == 0 ? 0xFFFF55FF : left > 0 ? 0xFFFFFF55 : 0xFF55FF55;
			return new HudBox().colored("Bear: " + text, color);
		}
	}
}
