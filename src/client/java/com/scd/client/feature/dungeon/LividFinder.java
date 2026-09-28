package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.config.HudLayout;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.feature.world.WorldGizmos;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * F5/M5: the real Livid among its clones. When the fight starts, the wool block at (5, 108, 43)
 * turns the real Livid's colour; the clones are players named "&lt;Name&gt; Livid". The real one is
 * boxed, and a HUD shows its name in its colour and the 19.5s it can't be hurt at the start.
 */
final class LividFinder {
	private static final BlockPos WOOL = new BlockPos(5, 108, 43);
	private static final String START = "[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.";

	private enum Livid {
		VENDETTA("Vendetta", DyeColor.WHITE, 0xFFFFFFFF), CROSSED("Crossed", DyeColor.MAGENTA, 0xFFFF55FF), ARCADE("Arcade", DyeColor.YELLOW, 0xFFFFFF55),
		SMILE("Smile", DyeColor.LIME, 0xFF55FF55), DOCTOR("Doctor", DyeColor.GRAY, 0xFFAAAAAA), PURPLE("Purple", DyeColor.PURPLE, 0xFFAA00AA),
		SCREAM("Scream", DyeColor.BLUE, 0xFF5555FF), FROG("Frog", DyeColor.GREEN, 0xFF00AA00), HOCKEY("Hockey", DyeColor.RED, 0xFFFF5555);

		final String name;
		final DyeColor dye;
		final int color;

		Livid(String name, DyeColor dye, int color) {
			this.name = name;
			this.dye = dye;
			this.color = color;
		}
	}

	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private Livid livid;
	private Player entity;
	private long invulnerableUntil;

	LividFinder(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		mod.bus.subscribe(Events.ChatReceived.class, e -> {
			if (e.isSystem() && active() && e.clean().trim().equals(START)) invulnerableUntil = System.currentTimeMillis() + 19_500;
		});
		mod.bus.subscribe(Events.WorldChanged.class, e -> {
			livid = null;
			entity = null;
			invulnerableUntil = 0;
		});
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (active() && mod.config().dungeon.lividFinder) tick();
		});
		WorldGizmos.onWorldExtract(pt -> {
			if (!active() || !mod.config().dungeon.lividFinder || entity == null || !entity.isAlive() || livid == null) return;
			WorldGizmos.box(entity, pt, livid.color, true);
		});
		mod.huds.add(new LividHud());
	}

	private boolean active() {
		String f = dungeon.state().floor();
		return dungeon.state().inDungeon() && dungeon.scoreInBoss() && ("F5".equals(f) || "M5".equals(f));
	}

	private void tick() {
		var level = Minecraft.getInstance().level;
		if (level == null || !level.isLoaded(WOOL)) return;
		Block wool = level.getBlockState(WOOL).getBlock();
		Livid found = null;
		for (Livid l : Livid.values()) if (Blocks.WOOL.pick(l.dye) == wool) found = l;
		if (found != null && found != livid) {
			livid = found;
			entity = null;
			int rgb = found.color & 0xFFFFFF;
			Chat.info(Component.literal("Livid: ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(found.name + " Livid").withStyle(s -> s.withColor(rgb))));
		}
		if (livid != null && (entity == null || !entity.isAlive())) {
			String want = livid.name + " Livid";
			for (Player p : level.players()) if (p.getName().getString().equals(want)) entity = p;
		}
	}

	private final class LividHud extends HudElement {
		LividHud() {
			super("livid", "Livid finder", HudLayout.at(HudLayout.AnchorX.CENTER, HudLayout.AnchorY.MIDDLE, 0, -60));
		}

		@Override
		public boolean enabled() {
			return mod.config().dungeon.lividFinder;
		}

		@Override
		public HudBox build(boolean preview) {
			if (preview) return new HudBox().colored("Hockey Livid", Livid.HOCKEY.color).text("Invulnerable 12.4s");
			if (!active() || livid == null) return null;
			HudBox box = new HudBox().colored(livid.name + " Livid", livid.color);
			long left = invulnerableUntil - System.currentTimeMillis();
			if (left > 0) box.text(String.format(java.util.Locale.ROOT, "Invulnerable %.1fs", left / 1000.0));
			return box;
		}
	}
}
