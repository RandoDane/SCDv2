package com.scd.client.feature.slayer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.scd.client.Feature;
import com.scd.client.ScdMod;
import com.scd.client.config.ScdConfig;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.client.feature.bazaar.BazaarFeature;
import com.scd.client.feature.mayor.MayorService;
import com.scd.client.feature.world.GlowRegistry;
import com.scd.client.feature.world.WorldGizmos;
import com.scd.client.storage.JsonStore;
import com.scd.client.storage.ScdPaths;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.Ui;
import com.scd.logic.Numbers;
import com.scd.logic.chat.SlayerMessages;
import com.scd.logic.slayer.SlayerTier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;

/**
 * Slayer: quest/boss tracking, fight and hunt timers, mechanic cues, spawn/kill/miniboss alerts,
 * boss highlight, personal bests, RNG meter, drop tally and session stats.
 */
public final class SlayerFeature implements Feature {
	private ScdMod mod;
	private JsonStore<SlayerData> store;
	private SlayerTracker tracker;
	private RngMeter rng;
	private RngMenuReader menuReader;
	private SlayerRecords records;
	private DropTracker drops;
	private final SessionStats session = new SessionStats();
	private final QuiverTracker quiver = new QuiverTracker();
	private MayorService mayor;
	/** "Enderman" from "Enderman Slayer LVL 7", consumed by the RNG Meter line right after it. */
	private SlayerType pendingLevelType;
	/**
	 * A fight the sidebar says ended, waiting for Hypixel's verdict. Dying or timing out also drops
	 * "Slay the boss!", so a kill is only booked on "SLAYER QUEST COMPLETE!" - or, if neither
	 * verdict arrives within 5s (wording changed?), booked anyway so nothing is silently lost.
	 */
	private SlayerEvents.BossKilled pendingEnd;
	private long pendingEndTick;
	/** A verdict that arrived before the sidebar caught up - the two can land in either order. */
	private SlayerMessages.QuestResult earlyVerdict;
	private long earlyVerdictTick;
	private static final int VERDICT_WAIT_TICKS = 100;
	private static final int PAIRING_WINDOW_TICKS = 20;

	@Override
	public void init(ScdMod mod) {
		this.mod = mod;
		boolean fresh = !Files.exists(ScdPaths.file("slayer.json"));
		store = JsonStore.open(ScdPaths.file("slayer.json"), SlayerData.class, SlayerData::new);
		if (fresh) {
			LegacySlayerImport.into(store.get());
			store.markDirty();
		}
		mayor = mod.feature(MayorService.class);
		BazaarFeature bazaar = mod.feature(BazaarFeature.class);
		tracker = new SlayerTracker(mod.bus, mod.game);
		mod.bus.subscribe(Events.EntityDied.class, e -> tracker.onEntityDied(e.entity()));
		// Your own hits and item uses (bows, wands, abilities) count as hunting; others' fights don't.
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) tracker.onPlayerAction();
			return net.minecraft.world.InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() && player == Minecraft.getInstance().player) tracker.onPlayerAction();
			return net.minecraft.world.InteractionResult.PASS;
		});
		rng = new RngMeter(store);
		menuReader = new RngMenuReader(rng);
		records = new SlayerRecords(store);
		drops = new DropTracker(store, () -> tracker.typeForLoot(), bazaar::idOf, bazaar::valueOf, session);

		mod.bus.subscribe(Events.Tick.class, e -> onTick());
		mod.bus.subscribe(Events.ChatReceived.class, this::onChat);
		mod.bus.subscribe(Events.ScreenTick.class, e -> menuReader.scan(e.screen()));
		mod.bus.subscribe(Events.WorldChanged.class, e -> tracker.reset());
		mod.bus.subscribe(SlayerEvents.BossSpawned.class, this::onSpawn);
		mod.bus.subscribe(SlayerEvents.BossKilled.class, this::onFightEnded);
		mod.bus.subscribe(SlayerEvents.MinibossSpawned.class, this::onMiniboss);

		mod.huds.add(new SlayerHud(mod::config, this));
		mod.huds.add(new ExplosiveArrowHud(mod::config, quiver));

		GlowRegistry.register(entity -> config().slayer.highlightGlow && entity == tracker.boss() ? Ui.theme().accent() : null);
		WorldGizmos.onWorldExtract(this::drawHighlight);
	}

	private ScdConfig config() {
		return mod.config();
	}

	private void onTick() {
		if (pendingEnd != null && mod.tasks.currentTick() - pendingEndTick > VERDICT_WAIT_TICKS) {
			ScdLog.warn("No SLAYER QUEST COMPLETE/FAILED line after a fight - counting it as a kill");
			confirmKill();
		}
		boolean active = mod.active();
		tracker.tick(active);
		session.track(tracker.quest());
		quiver.tick(tracker.quest());
		if (active && config().slayer.dropTracking) drops.tick();
	}

	private void onChat(Events.ChatReceived e) {
		if (!e.isSystem()) return;
		SlayerMessages.QuestResult verdict = SlayerMessages.questResult(e.text());
		if (verdict == SlayerMessages.QuestResult.COMPLETE || verdict == SlayerMessages.QuestResult.FAILED) {
			ScdLog.debug("Quest verdict " + verdict + " (pending fight: " + (pendingEnd != null) + ")");
			if (pendingEnd != null) {
				applyVerdict(verdict);
			} else {
				earlyVerdict = verdict;
				earlyVerdictTick = mod.tasks.currentTick();
			}
			return;
		}
		if (SlayerMessages.isCocoon(e.text())) {
			tracker.onCocoon();
			Chat.info(Component.literal("Boss cocooned - respawning in 6s").withStyle(ChatFormatting.LIGHT_PURPLE));
			return;
		}
		String level = SlayerMessages.levelUpTypeName(e.text());
		if (level != null) {
			pendingLevelType = SlayerType.parse(level);
			return;
		}
		Long stored = SlayerMessages.rngMeterStoredXp(e.text());
		if (stored != null && pendingLevelType != null) {
			rng.recordStoredXp(pendingLevelType, stored);
			pendingLevelType = null;
			return;
		}
		if (config().slayer.dropTracking && mod.active() && e.clean().startsWith("[Sacks]")) drops.onChat(e.component());
	}

	private void onSpawn(SlayerEvents.BossSpawned e) {
		// Averages leave out server lag so one laggy lobby doesn't skew them.
		session.recordHunt(e.quest().tier(), e.huntMs() - e.lagMs());
		if (!config().slayer.spawnAlert) return;
		Chat.info(Component.literal(e.quest().type().bossName() + " spawned!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
				.append(Component.literal((config().slayer.spawnShowTime ? "  (spawn " + Numbers.durationTenths(e.huntMs()) + (config().slayer.killShowLag ? lagNote(e.lagMs()) : "") + ")" : "")).withStyle(ChatFormatting.GRAY)));
		if (config().slayer.spawnAlertTitle) {
			Chat.title(Component.literal("Boss spawned!").withStyle(ChatFormatting.RED), Component.literal(e.quest().label()), true);
		}
	}

	private void onFightEnded(SlayerEvents.BossKilled e) {
		ScdLog.debug("Fight ended (sidebar): " + e.quest().label() + " after " + e.fightMs() + "ms");
		if (pendingEnd != null) confirmKill(); // an older fight never got a verdict - book it before replacing
		pendingEnd = e;
		pendingEndTick = mod.tasks.currentTick();
		if (earlyVerdict != null && pendingEndTick - earlyVerdictTick <= PAIRING_WINDOW_TICKS) applyVerdict(earlyVerdict);
		earlyVerdict = null;
	}

	private void applyVerdict(SlayerMessages.QuestResult verdict) {
		if (verdict == SlayerMessages.QuestResult.COMPLETE) {
			confirmKill();
		} else {
			Chat.info(Component.literal(pendingEnd.quest().label() + " quest failed - not counted").withStyle(ChatFormatting.RED));
			pendingEnd = null;
		}
	}

	private void confirmKill() {
		SlayerEvents.BossKilled e = pendingEnd;
		pendingEnd = null;
		if (e != null) onKill(e);
	}

	/** " · 1.2s server lag" when the server lost noticeable time (PBs keep the real time; averages drop the lag). */
	static String lagNote(long lagMs) {
		return lagMs >= 300 ? " · " + Numbers.durationTenths(lagMs) + " server lag" : "";
	}

	private void onKill(SlayerEvents.BossKilled e) {
		SlayerQuest q = e.quest();
		boolean best = records.recordKill(q.type(), q.tier(), e.fightMs());
		session.recordKill(q.tier(), e.fightMs() - e.lagMs());
		Long base = RngMeter.baseXp(q.tier());
		if (base != null) {
			long xp = Math.round(base * mayor.slayerXpMultiplier());
			session.addXp(xp);
			rng.recordKillEstimate(q.type(), q.tier(), xp);
		}
		if (config().slayer.killMessage) {
			Chat.info(Component.literal(q.label() + " down in " + Numbers.durationTenths(e.fightMs())).withStyle(ChatFormatting.GREEN)
					.append(Component.literal(config().slayer.killShowLag ? lagNote(e.lagMs()) : "").withStyle(ChatFormatting.GRAY))
					.append(best && config().slayer.killShowBest ? Component.literal("  NEW BEST!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD) : Component.empty()));
		}
	}

	private void onMiniboss(SlayerEvents.MinibossSpawned e) {
		if (!config().slayer.minibossAlert) return;
		var mb = e.miniboss();
		Chat.info(Component.literal(mb.name() + " spawned" + (mb.strong() ? " (strong)" : ""))
				.withStyle(mb.strong() ? ChatFormatting.DARK_RED : ChatFormatting.GOLD));
		if (config().slayer.minibossTitle) Chat.title(Component.literal(mb.name()).withStyle(ChatFormatting.GOLD), null, true);
	}

	private void drawHighlight(float partialTick) {
		var boss = tracker.boss();
		var player = Minecraft.getInstance().player;
		if (boss == null || player == null || !mod.active()) return;
		int color = Ui.theme().accent();
		if (config().slayer.highlightLine) WorldGizmos.tracer(player.getEyePosition(partialTick), boss, partialTick, color, true);
		if (config().slayer.highlightBox) WorldGizmos.box(boss, partialTick, color, true);
	}

	// ---- accessors for HUD/screens --------------------------------------------------------------

	public SlayerTracker tracker() {
		return tracker;
	}

	public RngMeter rng() {
		return rng;
	}

	public SlayerRecords records() {
		return records;
	}

	DropTracker drops() {
		return drops;
	}

	public SessionStats session() {
		return session;
	}

	boolean inDungeon() {
		return mod.game.sidebarContains("The Catacombs");
	}

	MayorService mayor() {
		return mayor;
	}

	// ---- commands ------------------------------------------------------------------------------

	static final SuggestionProvider<FabricClientCommandSource> TYPES = (ctx, b) -> {
		for (SlayerType t : SlayerType.values()) b.suggest(t.commandName());
		return b.buildFuture();
	};

	private static SlayerType type(CommandContext<FabricClientCommandSource> ctx) {
		String text = StringArgumentType.getString(ctx, "type");
		SlayerType t = SlayerType.parse(text);
		if (t == null) Chat.error("Unknown Slayer type \"" + text + "\" - one of zombie, spider, wolf, enderman, blaze, vampire.");
		return t;
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(ClientCommands.literal("slayer")
				.executes(ctx -> {
					ScdScreen.open(new SlayerScreen(new com.scd.client.ui.clickgui.ClickGuiScreen(mod), mod, this));
					return 1;
				})
				.then(ClientCommands.literal("drops")
						.then(ClientCommands.argument("type", StringArgumentType.word()).suggests(TYPES)
								.executes(ctx -> {
									SlayerType t = type(ctx);
									if (t != null) ScdScreen.open(new DropsScreen(null, this, t));
									return 1;
								})
								.then(ClientCommands.literal("clear").executes(ctx -> {
									SlayerType t = type(ctx);
									if (t != null) {
										drops.clear(t);
										Chat.success("Cleared all " + t.displayName() + " drops.");
									}
									return 1;
								}))
								.then(ClientCommands.literal("remove")
										.then(ClientCommands.argument("index", IntegerArgumentType.integer(1)).executes(ctx -> {
											SlayerType t = type(ctx);
											if (t == null) return 0;
											var list = drops.entries(t);
											int i = IntegerArgumentType.getInteger(ctx, "index") - 1;
											if (i >= list.size()) {
												Chat.error("No drop #" + (i + 1) + ".");
												return 0;
											}
											drops.remove(t, list.get(i).getKey());
											Chat.success("Removed " + list.get(i).getValue().name + ".");
											return 1;
										})))))
				.then(ClientCommands.literal("records").executes(ctx -> {
					Chat.info("Personal bests:");
					for (SlayerType t : SlayerType.values()) {
						StringBuilder sb = new StringBuilder();
						for (String tier : SlayerTier.ALL) {
							Long b = records.best(t, tier);
							if (b != null) sb.append("  ").append(tier).append(" ").append(Numbers.durationTenths(b))
									.append(" (").append(records.kills(t, tier)).append(" kills)");
						}
						if (!sb.isEmpty()) Chat.raw(Component.literal(" " + t.displayName() + ":" + sb).withStyle(ChatFormatting.GRAY));
					}
					return 1;
				}))
				.then(ClientCommands.literal("session").then(ClientCommands.literal("reset").executes(ctx -> {
					session.reset();
					Chat.success("Session stats reset.");
					return 1;
				}))));
	}
}
