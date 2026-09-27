package com.scd.client.feature.dungeon;

import com.scd.client.ScdMod;
import com.scd.client.config.HudLayout;
import com.scd.client.config.ScdConfig;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.feature.world.WorldGizmos;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudColor;
import com.scd.client.hud.HudElement;
import com.scd.client.ui.Ui;
import com.scd.logic.Numbers;
import com.scd.logic.Text;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small dungeon helpers: puzzle status, teammate deaths and low-health alerts, blessings,
 * invincibility (Bonzo/Spirit/Phoenix) timers, and a chime + box when a secret is found.
 */
final class DungeonExtras {
	private static final Pattern PUZZLE = Pattern.compile("^(.+?): \\[([✖✔✦])]");
	private static final Pattern DEATH = Pattern.compile("^☠ (You|\\w{1,16}) .*(?:became a ghost|disconnected)");
	private static final Pattern TEAMMATE = Pattern.compile("^\\[(\\w)] (\\w{1,16}) ([\\d,.]+k?|DEAD)❤?");
	private static final Pattern BLESSING = Pattern.compile("Blessing of (Power|Time|Stone|Life|Wisdom) ([IVXL]+)");
	private static final Pattern MASK_COOLDOWN = Pattern.compile("^Cooldown: (\\d+)s$");
	private static final int LOW_RED = 0xFF5555;

	private enum Saver {
		SPIRIT("Spirit Mask", Pattern.compile("^Second Wind Activated! Your Spirit Mask saved your life!$"), 3_000, 30_000),
		BONZO("Bonzo's Mask", Pattern.compile("^Your (?:. )?Bonzo's Mask saved your life!$"), 3_000, 180_000),
		PHOENIX("Phoenix", Pattern.compile("^Your Phoenix Pet saved you from certain death!$"), 4_000, 60_000);

		final String label;
		final Pattern chat;
		final long invincibleMs, cooldownMs;

		Saver(String label, Pattern chat, long invincibleMs, long cooldownMs) {
			this.label = label;
			this.chat = chat;
			this.invincibleMs = invincibleMs;
			this.cooldownMs = cooldownMs;
		}
	}

	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private final Map<String, Integer> deaths = new LinkedHashMap<>();
	private final Map<String, Long> lowAlerted = new java.util.HashMap<>();
	private final Map<Saver, Long> procAt = new java.util.EnumMap<>(Saver.class);
	private final Map<Saver, Long> cooldownOverride = new java.util.EnumMap<>(Saver.class);
	private BlockPos lastClicked;
	private long lastClickedAt;
	private BlockPos secretBox;
	private long secretBoxUntil;

	DungeonExtras(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		mod.bus.subscribe(Events.ChatReceived.class, e -> {
			if (e.isSystem()) onSystem(e.clean().trim());
		});
		mod.bus.subscribe(Events.Tick.class, e -> tick());
		mod.bus.subscribe(DungeonEvents.SecretFound.class, e -> onSecret());
		mod.bus.subscribe(Events.WorldChanged.class, e -> {
			deaths.clear();
			lowAlerted.clear();
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) {
				lastClicked = hit.getBlockPos().immutable();
				lastClickedAt = System.currentTimeMillis();
			}
			return InteractionResult.PASS;
		});
		WorldGizmos.onWorldExtract(pt -> {
			if (secretBox != null && System.currentTimeMillis() < secretBoxUntil) WorldGizmos.block(secretBox, 0xFF4ADE80, true);
		});
		mod.huds.add(new PuzzleHud());
		mod.huds.add(new TeamHud());
		mod.huds.add(new BlessingHud());
		mod.huds.add(new SaverHud());
	}

	private ScdConfig.Dungeon cfg() {
		return mod.config().dungeon;
	}

	private boolean inDungeon() {
		return dungeon.state().inDungeon();
	}

	// ---- events -----------------------------------------------------------------------------

	private void onSystem(String text) {
		for (Saver s : Saver.values()) {
			if (s.chat.matcher(text).matches()) {
				procAt.put(s, System.currentTimeMillis());
				cooldownOverride.put(s, maskCooldownFromHelmet(s));
			}
		}
		if (!inDungeon()) return;
		Matcher m = DEATH.matcher(text);
		if (m.find()) {
			String who = m.group(1);
			var self = Minecraft.getInstance().player;
			if (who.equals("You") && self != null) who = self.getName().getString();
			int n = deaths.merge(who, 1, Integer::sum);
			boolean me = self != null && who.equals(self.getName().getString());
			if (cfg().deathAlert && !me) {
				Chat.title(Component.literal("☠ " + who + " died").withStyle(ChatFormatting.RED),
						Component.literal(n > 1 ? "death #" + n : "").withStyle(ChatFormatting.GRAY), true);
			}
		}
	}

	/** Live mask cooldown from the helmet's lore ("Cooldown: 26s", reduced by upgrades), else null. */
	private static Long maskCooldownFromHelmet(Saver s) {
		if (s == Saver.PHOENIX) return null;
		var p = Minecraft.getInstance().player;
		if (p == null) return null;
		var head = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
		for (String line : com.scd.client.hypixel.Items.lore(head)) {
			Matcher m = MASK_COOLDOWN.matcher(Text.clean(line).trim());
			if (m.matches()) return Long.parseLong(m.group(1)) * 1000;
		}
		return null;
	}

	private void onSecret() {
		if (!inDungeon() || !cfg().secretChime) return;
		var mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.6f);
		if (lastClicked != null && System.currentTimeMillis() - lastClickedAt < 3_000) {
			secretBox = lastClicked;
			secretBoxUntil = System.currentTimeMillis() + 1_500;
		}
	}

	private void tick() {
		if (!inDungeon() || !cfg().lowHealthAlert) return;
		var self = Minecraft.getInstance().player;
		String me = self != null ? self.getName().getString() : "";
		List<Component> raw = mod.game.sidebarRaw();
		long now = System.currentTimeMillis();
		for (Component line : raw) {
			Matcher m = TEAMMATE.matcher(Text.clean(line.getString()).trim());
			if (!m.find() || m.group(2).equals(me) || m.group(3).equals("DEAD")) continue;
			if (!healthIsRed(line)) continue;
			Long last = lowAlerted.get(m.group(2));
			if (last != null && now - last < 10_000) continue;
			lowAlerted.put(m.group(2), now);
			Chat.title(Component.empty(), Component.literal(m.group(2) + " is low! " + m.group(3) + "❤").withStyle(ChatFormatting.RED), true);
		}
	}

	/** Hypixel colours a teammate's health red when it's low. */
	private static boolean healthIsRed(Component line) {
		boolean[] red = {false};
		line.visit((Style style, String part) -> {
			if (part.contains("❤") || part.matches(".*\\d.*")) {
				TextColor c = style.getColor();
				if (c != null && (c.getValue() & 0xFFFFFF) == LOW_RED && !part.contains("[")) red[0] = true;
			}
			return Optional.empty();
		}, Style.EMPTY);
		return red[0];
	}

	// ---- HUDs -------------------------------------------------------------------------------

	private final class PuzzleHud extends HudElement {
		PuzzleHud() {
			super("dungeon_puzzles", "Dungeon puzzles", HudLayout.at(HudLayout.AnchorX.LEFT, HudLayout.AnchorY.MIDDLE, 8, -20));
		}

		@Override
		public boolean enabled() {
			return cfg().puzzleHud;
		}

		@Override
		public HudBox build(boolean preview) {
			HudBox box = new HudBox().minWidth(100).title("Puzzles");
			if (preview) {
				return box.colored("Three Weirdos ✔", Ui.SUCCESS).colored("Water Board ✦", 0xFFAAAAAA).colored("Ice Fill ✖", Ui.DANGER);
			}
			if (!inDungeon()) return null;
			int n = 0;
			for (String line : mod.game.tabList()) {
				Matcher m = PUZZLE.matcher(line.trim());
				if (!m.find() || m.group(1).startsWith("Puzzles")) continue;
				String mark = m.group(2);
				int color = mark.equals("✔") ? Ui.SUCCESS : mark.equals("✖") ? Ui.DANGER : 0xFFAAAAAA;
				box.colored(m.group(1) + " " + mark, color);
				n++;
			}
			return n > 0 ? box : null;
		}
	}

	private final class TeamHud extends HudElement {
		TeamHud() {
			super("dungeon_deaths", "Dungeon deaths", HudLayout.at(HudLayout.AnchorX.LEFT, HudLayout.AnchorY.MIDDLE, 8, 40));
		}

		@Override
		public boolean enabled() {
			return cfg().deathHud;
		}

		@Override
		public HudBox build(boolean preview) {
			HudBox box = new HudBox().minWidth(100).title("Deaths");
			if (preview) return box.text("Steve ×2", HudColor.TEXT).text("Alex ×1", HudColor.TEXT);
			if (!inDungeon() || deaths.isEmpty()) return null;
			deaths.forEach((who, n) -> box.text(who + " ×" + n, HudColor.TEXT));
			return box;
		}
	}

	private final class BlessingHud extends HudElement {
		BlessingHud() {
			super("dungeon_blessings", "Dungeon blessings", HudLayout.at(HudLayout.AnchorX.LEFT, HudLayout.AnchorY.MIDDLE, 8, 100));
		}

		@Override
		public boolean enabled() {
			return cfg().blessingHud;
		}

		@Override
		public HudBox build(boolean preview) {
			HudBox box = new HudBox().minWidth(90).title("Blessings");
			if (preview) return box.text("Power 19", HudColor.TEXT).text("Time V", HudColor.TEXT).text("Life 3", HudColor.TEXT);
			if (!inDungeon()) return null;
			Map<String, String> found = new LinkedHashMap<>();
			for (String line : mod.game.tabFooter()) {
				Matcher m = BLESSING.matcher(line);
				while (m.find()) found.put(m.group(1), m.group(2));
			}
			if (found.isEmpty()) return null;
			found.forEach((type, roman) -> {
				Integer n = Numbers.romanToInt(roman);
				box.text(type + " " + (type.equals("Time") ? roman : n != null ? String.valueOf(n) : roman), HudColor.TEXT);
			});
			return box;
		}
	}

	private final class SaverHud extends HudElement {
		SaverHud() {
			super("invincibility", "Invincibility timers", HudLayout.at(HudLayout.AnchorX.CENTER, HudLayout.AnchorY.MIDDLE, 0, 30));
		}

		@Override
		public boolean enabled() {
			return cfg().invincibilityHud;
		}

		@Override
		public HudBox build(boolean preview) {
			HudBox box = new HudBox().minWidth(110);
			if (preview) return box.colored("Bonzo's Mask  INVINCIBLE 2.1s", Ui.SUCCESS).text("Spirit Mask  0:21", HudColor.LABEL);
			long now = System.currentTimeMillis();
			for (Saver s : Saver.values()) {
				Long at = procAt.get(s);
				if (at == null) continue;
				long since = now - at;
				Long cd = cooldownOverride.get(s);
				long cooldown = cd != null ? cd : s.cooldownMs;
				if (since < s.invincibleMs) {
					box.colored(s.label + "  INVINCIBLE " + String.format(Locale.ROOT, "%.1fs", (s.invincibleMs - since) / 1000.0), Ui.SUCCESS);
				} else if (since < cooldown) {
					box.text(s.label + "  " + Numbers.duration(cooldown - since + 999), HudColor.LABEL);
				} else if (since < cooldown + 3_000) {
					box.colored(s.label + "  ready", Ui.WARNING);
				}
			}
			return box.isEmpty() ? null : box;
		}
	}
}
