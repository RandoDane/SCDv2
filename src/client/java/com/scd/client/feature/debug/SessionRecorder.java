package com.scd.client.feature.debug;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.scd.client.Feature;
import com.scd.client.ScdMod;
import com.scd.client.config.HudLayout;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.client.core.Tasks;
import com.scd.client.feature.dungeon.DungeonFeature;
import com.scd.client.feature.slayer.BossLocator;
import com.scd.client.feature.slayer.SlayerFeature;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudElement;
import com.scd.client.hypixel.ChatText;
import com.scd.client.hypixel.Items;
import com.scd.client.storage.ScdPaths;
import com.scd.client.ui.Ui;
import com.scd.logic.Numbers;
import com.scd.logic.Text;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.Entity;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Opt-in debug recording for live test sessions: /scd record start. Captures what SCD sees and
 * decides - raw chat (with hover text), sidebar and tab-list changes, nearby nameplates, opened
 * menus (slot names + lore), SCD's own state and log lines, and player-placed marks - as JSON events.
 *
 * Events are appended to config/scd/recordings/&lt;session&gt;.jsonl and, every 2 seconds, uploaded
 * to the SCD backend (POST /api/debug/sessions/&lt;session&gt;/events) with a random per-session token,
 * so the recording can be watched live. Nothing is recorded or sent unless a session is running,
 * and a red REC indicator is shown the whole time.
 */
public final class SessionRecorder implements Feature {
	private static final int FLUSH_TICKS = 40;
	private static final int NAMEPLATE_TICKS = 10;
	private static final int STATE_TICKS = 20;
	private static final int TAB_TICKS = 40;
	private static final int MAX_BATCH = 400;
	private static final int MAX_PENDING = 20_000;
	private static final double NAMEPLATE_RADIUS = 30;

	private ScdMod mod;
	private volatile boolean recording;
	private String sessionId;
	private String token;
	private long startedMs;
	private long seq;
	private long uploaded;
	private String lastUploadError;
	private boolean uploadInFlight;
	private BufferedWriter file;
	private final ConcurrentLinkedQueue<JsonObject> pending = new ConcurrentLinkedQueue<>();

	private List<String> lastSidebar = List.of();
	private List<String> lastTab = List.of();
	private Map<Integer, String> lastPlates = new HashMap<>();
	private Screen lastScreen;
	private int lastScreenHash;
	private long lastMenuTick = -100;
	private String lastState;
	private long ticks;

	@Override
	public void init(ScdMod mod) {
		this.mod = mod;
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (recording) tick();
		});
		mod.bus.subscribe(Events.ChatReceived.class, this::onChat);
		mod.bus.subscribe(Events.ScreenOpened.class, e -> {
			if (recording) {
				lastScreen = null;
				screen(e.screen());
			}
		});
		mod.bus.subscribe(Events.ScreenTick.class, e -> {
			if (recording) screen(e.screen());
		});
		mod.bus.subscribe(Events.WorldChanged.class, e -> {
			if (recording) event("world", o -> o.addProperty("joined", e.joined()));
		});
		mod.huds.add(new RecIndicator());
	}

	// ---- lifecycle -------------------------------------------------------------------------------

	public boolean recording() {
		return recording;
	}

	/** "session token" as shown to the player, for tests and /scd record status. */
	public String credentials() {
		return recording ? sessionId + " " + token : null;
	}

	private void start() {
		if (recording) {
			Chat.info("Already recording session " + sessionId + ".");
			return;
		}
		SecureRandom rnd = new SecureRandom();
		sessionId = String.format("%08x", rnd.nextInt());
		token = String.format("%016x", rnd.nextLong());
		startedMs = System.currentTimeMillis();
		seq = 0;
		uploaded = 0;
		lastUploadError = null;
		lastSidebar = List.of();
		lastTab = List.of();
		lastPlates = new HashMap<>();
		lastScreen = null;
		lastState = null;
		try {
			Files.createDirectories(ScdPaths.file("recordings"));
			file = Files.newBufferedWriter(ScdPaths.file("recordings/" + sessionId + ".jsonl"), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			ScdLog.warn("Could not open local recording file", e);
			file = null;
		}
		recording = true;
		ScdLog.setTap(this::onLog);
		event("start", o -> {
			o.addProperty("version", mod.version);
			o.addProperty("player", com.scd.client.hypixel.Players.selfName());
			o.addProperty("backend", mod.backend.baseUrl());
		});
		Component idLine = Component.literal(sessionId + " / " + token).withStyle(s -> s.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.CopyToClipboard(sessionId + " " + token))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to copy"))));
		Chat.info(Component.literal("Recording started. Session / token: ").withStyle(ChatFormatting.GRAY).append(idLine));
		Chat.info("Share the session and token with whoever is watching. /scd record mark <note> flags a moment; /scd record stop ends it.");
	}

	private void stop() {
		if (!recording) {
			Chat.info("Not recording.");
			return;
		}
		event("stop", o -> o.addProperty("durationMs", System.currentTimeMillis() - startedMs));
		recording = false;
		ScdLog.setTap(null);
		flush(true);
		BufferedWriter f = file;
		file = null;
		Tasks.io(() -> {
			try {
				if (f != null) f.close();
			} catch (IOException ignored) {
				// best effort
			}
		});
		Chat.info("Recording " + sessionId + " stopped - " + seq + " events (" + uploaded + " uploaded). Local copy: config/scd/recordings/"
				+ sessionId + ".jsonl");
	}

	// ---- capture ---------------------------------------------------------------------------------

	private interface Filler {
		void fill(JsonObject o);
	}

	private synchronized void event(String type, Filler filler) {
		if (!recording) return;
		JsonObject o = new JsonObject();
		o.addProperty("seq", ++seq);
		o.addProperty("t", System.currentTimeMillis() - startedMs);
		o.addProperty("type", type);
		filler.fill(o);
		if (pending.size() < MAX_PENDING) pending.add(o);
		BufferedWriter f = file;
		if (f != null) {
			String line = o.toString();
			Tasks.io(() -> {
				synchronized (this) {
					try {
						f.write(line);
						f.newLine();
					} catch (IOException ignored) {
						// file closed - uploads still carry the event
					}
				}
			});
		}
	}

	private void onLog(String level, String message) {
		if (message.startsWith("[capture:")) return; // raw chat is already its own event
		event("log", o -> {
			o.addProperty("level", level);
			o.addProperty("msg", message);
		});
	}

	private void onChat(Events.ChatReceived e) {
		if (!recording) return;
		if (e.channel() == Events.ChatReceived.Channel.ACTION_BAR && ticks % 10 != 0) return; // action bar spams every tick
		event("chat", o -> {
			o.addProperty("channel", e.channel().name());
			o.addProperty("text", e.text());
			String hover = ChatText.hoverText(e.component());
			if (hover != null) o.addProperty("hover", hover);
		});
	}

	private void tick() {
		ticks++;
		List<String> sidebar = mod.game.sidebar();
		if (!sidebar.equals(lastSidebar)) {
			lastSidebar = sidebar;
			event("sidebar", o -> {
				o.addProperty("title", mod.game.sidebarTitle());
				o.add("lines", array(sidebar));
			});
		}
		if (ticks % TAB_TICKS == 0) {
			List<String> tab = mod.game.tabList();
			if (!tab.equals(lastTab)) {
				lastTab = tab;
				event("tab", o -> o.add("lines", array(tab)));
			}
		}
		if (ticks % NAMEPLATE_TICKS == 0) nameplates();
		if (ticks % STATE_TICKS == 0) state();
		if (ticks % FLUSH_TICKS == 0) flush(false);
	}

	/** Named entities near the player; only changes (added/changed/removed) are recorded. */
	private void nameplates() {
		var mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		Map<Integer, String> now = new HashMap<>();
		JsonArray changed = new JsonArray();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || e.distanceTo(mc.player) > NAMEPLATE_RADIUS) continue;
			String name = BossLocator.name(e);
			if (name == null || name.isEmpty()) continue;
			String key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath() + "|" + name;
			now.put(e.getId(), key);
			if (!key.equals(lastPlates.get(e.getId()))) {
				JsonObject p = new JsonObject();
				p.addProperty("id", e.getId());
				p.addProperty("entity", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath());
				p.addProperty("name", name);
				p.addProperty("x", Math.round(e.getX() * 10) / 10.0);
				p.addProperty("y", Math.round(e.getY() * 10) / 10.0);
				p.addProperty("z", Math.round(e.getZ() * 10) / 10.0);
				changed.add(p);
			}
		}
		JsonArray removed = new JsonArray();
		for (Integer id : lastPlates.keySet()) if (!now.containsKey(id)) removed.add(id);
		lastPlates = now;
		if (!changed.isEmpty() || !removed.isEmpty()) {
			event("nameplates", o -> {
				if (!changed.isEmpty()) o.add("changed", changed);
				if (!removed.isEmpty()) o.add("removed", removed);
			});
		}
	}

	/** Container menus: every slot's name + lore, re-sent whenever the contents change. */
	private void screen(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> c)) {
			if (screen != lastScreen) {
				lastScreen = screen;
				event("screen", o -> o.addProperty("class", screen.getClass().getSimpleName()));
			}
			return;
		}
		JsonArray slots = new JsonArray();
		for (var slot : c.getMenu().slots) {
			if (!slot.hasItem()) continue;
			JsonObject s = new JsonObject();
			s.addProperty("slot", slot.index);
			s.addProperty("name", Items.name(slot.getItem()));
			s.addProperty("count", slot.getItem().getCount());
			String id = Items.skyblockId(slot.getItem(), null);
			if (id != null) s.addProperty("sbId", id);
			List<String> lore = Items.lore(slot.getItem());
			if (!lore.isEmpty()) s.add("lore", array(lore));
			slots.add(s);
		}
		int hash = Objects.hash(screen.getTitle().getString(), slots.toString());
		if (screen == lastScreen && hash == lastScreenHash) return;
		// Animated menus (blinking arrows, cycling icons) change every few ticks - record the same
		// screen at most every 2 seconds; a new screen or title is always recorded immediately.
		if (screen == lastScreen && ticks - lastMenuTick < 40) return;
		lastMenuTick = ticks;
		lastScreen = screen;
		lastScreenHash = hash;
		event("menu", o -> {
			o.addProperty("title", Text.clean(screen.getTitle().getString()));
			o.add("slots", slots);
		});
	}

	/** What SCD currently believes - only recorded when it changes. */
	private void state() {
		JsonObject s = new JsonObject();
		s.addProperty("active", mod.active());
		s.addProperty("area", mod.game.area());
		var tracker = mod.feature(SlayerFeature.class).tracker();
		var q = tracker.quest();
		if (q != null) {
			s.addProperty("quest", q.label() + (q.bossSpawned() ? " (boss)" : ""));
			if (tracker.boss() != null) s.addProperty("boss", BossLocator.name(tracker.boss()));
			if (tracker.hpFraction() != null) s.addProperty("hp", Math.round(tracker.hpFraction() * 1000) / 10.0);
			s.addProperty("huntPaused", tracker.isHuntPaused());
		}
		var score = mod.feature(DungeonFeature.class).score();
		if (score != null) {
			var b = score.breakdown();
			s.addProperty("dungeon", score.state().floor() + " score=" + score.total() + (score.baselined() ? " (baseline)" : "")
					+ " skill=" + b.skill() + " explore=" + b.explore() + " speed=" + b.speed() + " bonus=" + b.bonus()
					+ " rooms=" + score.completedRooms() + "/" + b.totalRoomsEstimate() + " secrets=" + score.secretsPercent()
					+ " crypts=" + score.crypts() + " deaths=" + score.deaths() + " puzzles=" + score.incompletePuzzles()
					+ " boss=" + score.inBoss());
		}
		String text = s.toString();
		if (text.equals(lastState)) return;
		lastState = text;
		event("state", o -> o.add("scd", s));
	}

	private static JsonArray array(List<String> lines) {
		JsonArray a = new JsonArray();
		for (String l : lines) a.add(l);
		return a;
	}

	// ---- upload ----------------------------------------------------------------------------------

	private void flush(boolean force) {
		if (pending.isEmpty() || (uploadInFlight && !force)) return;
		JsonArray batch = new JsonArray();
		List<JsonObject> taken = new ArrayList<>();
		JsonObject o;
		while (batch.size() < MAX_BATCH && (o = pending.poll()) != null) {
			batch.add(o);
			taken.add(o);
		}
		uploadInFlight = true;
		mod.backend.postDebugEvents(sessionId, token, batch).whenComplete((status, err) -> {
			uploadInFlight = false;
			if (err == null && status / 100 == 2) {
				uploaded += taken.size();
				lastUploadError = null;
			} else {
				lastUploadError = err != null ? (err.getCause() != null ? err.getCause().getMessage() : err.getMessage()) : "HTTP " + status;
				// Put the batch back (in order) so nothing is lost while the backend is unreachable.
				List<JsonObject> rest = new ArrayList<>(taken);
				pending.forEach(rest::add);
				pending.clear();
				pending.addAll(rest.subList(0, Math.min(rest.size(), MAX_PENDING)));
			}
		});
	}

	// ---- commands + indicator --------------------------------------------------------------------

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(ClientCommands.literal("record")
				.then(ClientCommands.literal("start").executes(ctx -> {
					start();
					return 1;
				}))
				.then(ClientCommands.literal("stop").executes(ctx -> {
					stop();
					return 1;
				}))
				.then(ClientCommands.literal("status").executes(ctx -> {
					Chat.info(recording ? "Recording " + sessionId + ": " + seq + " events, " + uploaded + " uploaded"
							+ (lastUploadError != null ? ", upload error: " + lastUploadError : "") : "Not recording.");
					return 1;
				}))
				.then(ClientCommands.literal("mark")
						.then(ClientCommands.argument("note", StringArgumentType.greedyString()).executes(ctx -> {
							String note = StringArgumentType.getString(ctx, "note");
							if (!recording) {
								Chat.info("Not recording - /scd record start first.");
								return 0;
							}
							event("mark", o -> o.addProperty("note", note));
							state();
							flush(true);
							Chat.success("Marked: " + note);
							return 1;
						}))));
	}

	/** Always-on-top REC badge while a session runs, so recording is never invisible. */
	private final class RecIndicator extends HudElement {
		RecIndicator() {
			super("recording", "Recording indicator", HudLayout.at(HudLayout.AnchorX.RIGHT, HudLayout.AnchorY.BOTTOM, 4, 40));
		}

		@Override
		public boolean enabled() {
			return true;
		}

		@Override
		public HudBox build(boolean preview) {
			if (!recording && !preview) return null;
			long elapsed = preview ? 83_000 : System.currentTimeMillis() - startedMs;
			HudBox box = new HudBox().minWidth(80).colored("● REC " + Numbers.duration(elapsed), Ui.DANGER);
			if (lastUploadError != null && !preview) box.colored("upload failing", Ui.WARNING);
			return box;
		}
	}
}
