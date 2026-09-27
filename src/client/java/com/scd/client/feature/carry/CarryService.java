package com.scd.client.feature.carry;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.scd.client.Feature;
import com.scd.client.ScdMod;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.feature.dungeon.DungeonEvents;
import com.scd.client.feature.slayer.SlayerType;
import com.scd.client.hypixel.Players;
import com.scd.client.storage.JsonStore;
import com.scd.client.storage.ScdPaths;
import com.scd.client.ui.ScdScreen;
import com.scd.logic.Numbers;
import com.scd.logic.dungeon.Floor;
import com.scd.logic.slayer.SlayerTier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;

/**
 * Carries for coins - Slayer (credited when the customer's own boss dies) and Dungeon (credited
 * when the carrier's run of that floor completes; several customers in one party each get +1).
 * Progress is announced in party chat from a configurable template; hitting the target posts a
 * client-only prompt with clickable Done / +5 / +10 / Custom buttons.
 */
public final class CarryService implements Feature {
	private ScdMod mod;
	private JsonStore<CarryBook> store;
	private SlayerCarryWatcher watcher;

	@Override
	public void init(ScdMod mod) {
		this.mod = mod;
		boolean fresh = !Files.exists(ScdPaths.file("carries.json"));
		store = JsonStore.open(ScdPaths.file("carries.json"), CarryBook.class, CarryBook::new);
		if (fresh) {
			LegacyCarryImport.into(store.get());
			store.markDirty();
		}
		watcher = new SlayerCarryWatcher(this::credit);
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (mod.active()) watcher.tick(active());
		});
		mod.bus.subscribe(DungeonEvents.RunCompleted.class, this::onRunCompleted);
	}

	// ---- queries ---------------------------------------------------------------------------------

	/** Active first (oldest first), then completed (most recent first). */
	public List<Carry> all() {
		List<Carry> list = new ArrayList<>(store.get().carries);
		list.sort(Comparator.comparing((Carry c) -> !c.isActive())
				.thenComparingLong(c -> c.isActive() ? c.createdAt : -c.completedAt));
		return list;
	}

	public List<Carry> active() {
		return store.get().carries.stream().filter(Carry::isActive).toList();
	}

	public Carry find(long id) {
		for (Carry c : store.get().carries) if (c.id == id) return c;
		return null;
	}

	/** The newest carry of a kind - forms default to its target so repeat work is one click. */
	public Carry latest(Carry.Kind kind) {
		return store.get().carries.stream().filter(c -> c.kind == kind).max(Comparator.comparingLong(c -> c.createdAt)).orElse(null);
	}

	public long earnedTotal() {
		return store.get().carries.stream().mapToLong(Carry::earned).sum();
	}

	public long outstandingTotal() {
		return active().stream().mapToLong(c -> c.pricePerUnit * c.remaining()).sum();
	}

	// ---- mutations -------------------------------------------------------------------------------

	public Carry addSlayer(String customer, SlayerType type, String tier, long price, int count) {
		Carry c = create(customer, price, count);
		c.kind = Carry.Kind.SLAYER;
		c.slayerType = type.name();
		c.tier = tier;
		return save(c);
	}

	public Carry addDungeon(String customer, String floor, long price, int count) {
		Carry c = create(customer, price, count);
		c.kind = Carry.Kind.DUNGEON;
		c.floor = floor;
		return save(c);
	}

	private Carry create(String customer, long price, int count) {
		Carry c = new Carry();
		c.id = store.get().nextId++;
		c.customer = customer;
		c.pricePerUnit = price;
		c.unitsOwed = count;
		c.createdAt = System.currentTimeMillis();
		return c;
	}

	private Carry save(Carry c) {
		store.get().carries.add(c);
		store.markDirty();
		return c;
	}

	/** Adds units at the already-agreed price; reopens a completed carry that now has work left. */
	public void extend(Carry c, int units) {
		c.unitsOwed += units;
		if (!c.isActive() && c.unitsDone < c.unitsOwed) {
			c.status = Carry.Status.ACTIVE;
			c.completedAt = 0;
		}
		store.markDirty();
	}

	/** Manual correction when detection missed (or double-counted) a kill/run. */
	public void adjust(Carry c, int delta) {
		c.unitsDone = Math.max(0, c.unitsDone + delta);
		store.markDirty();
	}

	/** Closes the carry and posts the review request in party chat. */
	public void finish(Carry c) {
		if (!c.isActive()) return;
		c.status = Carry.Status.COMPLETED;
		c.completedAt = System.currentTimeMillis();
		store.markDirty();
		String msg = fill(mod.config().carries.finishTemplate, c);
		if (!msg.isBlank()) Chat.party(msg);
	}

	public void remove(Carry c) {
		store.get().carries.remove(c);
		store.markDirty();
	}

	// ---- crediting -------------------------------------------------------------------------------

	private void credit(Carry c, long timeMs) {
		credit(c, timeMs, 0);
	}

	private void credit(Carry c, long timeMs, int messageDelayTicks) {
		if (!c.isActive()) return;
		c.unitsDone++;
		c.totalTimeMs += timeMs;
		store.markDirty();
		boolean reached = c.unitsDone == c.unitsOwed;
		Runnable announce = () -> {
			if (mod.config().carries.partyProgress) Chat.party(fill(mod.config().carries.progressTemplate, c));
			if (reached) promptTargetReached(c);
		};
		if (messageDelayTicks > 0) mod.tasks.later(messageDelayTicks, "carry message", announce);
		else announce.run();
	}

	private void onRunCompleted(DungeonEvents.RunCompleted e) {
		String floor = e.report().floorKey();
		if (floor == null) return;
		int delay = mod.config().carries.dungeonMessageDelayTicks;
		for (Carry c : active()) {
			if (c.kind == Carry.Kind.DUNGEON && floor.equals(c.floor)) credit(c, e.clearTimeMs(), delay);
		}
	}

	private void promptTargetReached(Carry c) {
		MutableComponent line = Component.literal(c.customer + "'s " + c.target().trim() + " carry is at " + c.unitsDone + "/" + c.unitsOwed + "! ")
				.withStyle(ChatFormatting.AQUA);
		line.append(Chat.button("Done", "/scd carry done " + c.id, true, "Close it and post the review message"));
		line.append(Component.literal(" "));
		line.append(Chat.button("+5", "/scd carry extend " + c.id + " 5", true, "5 more at " + Numbers.coins(c.pricePerUnit) + " each"));
		line.append(Component.literal(" "));
		line.append(Chat.button("+10", "/scd carry extend " + c.id + " 10", true, "10 more at " + Numbers.coins(c.pricePerUnit) + " each"));
		line.append(Component.literal(" "));
		line.append(Chat.button("Custom", "/scd carry extend " + c.id + " ", false, "Type how many more"));
		Chat.info(line);
	}

	/** Template placeholders: {player} {done} {owed} {left} {unit} {target} {price} {total}. */
	static String fill(String template, Carry c) {
		return template.replace("{player}", c.customer)
				.replace("{done}", String.valueOf(c.unitsDone))
				.replace("{owed}", String.valueOf(c.unitsOwed))
				.replace("{left}", String.valueOf(c.remaining()))
				.replace("{unit}", c.unit())
				.replace("{target}", c.target().trim())
				.replace("{price}", Numbers.coins(c.pricePerUnit))
				.replace("{total}", Numbers.coins(c.totalPrice()));
	}

	public String describe(Carry c) {
		return c.kind == Carry.Kind.SLAYER ? watcher.describe(c) : "credited on " + c.floor + " completion";
	}

	// ---- commands --------------------------------------------------------------------------------

	private static final SuggestionProvider<FabricClientCommandSource> PLAYERS = (ctx, b) -> {
		for (String p : Players.others()) b.suggest(p);
		return b.buildFuture();
	};
	private static final SuggestionProvider<FabricClientCommandSource> TYPES = (ctx, b) -> {
		for (SlayerType t : SlayerType.values()) b.suggest(t.commandName());
		return b.buildFuture();
	};
	private static final SuggestionProvider<FabricClientCommandSource> TIERS = (ctx, b) -> {
		for (String t : SlayerTier.ALL) b.suggest(t);
		return b.buildFuture();
	};
	private static final SuggestionProvider<FabricClientCommandSource> FLOORS = (ctx, b) -> {
		for (String f : Floor.CARRYABLE) b.suggest(f);
		return b.buildFuture();
	};

	private SuggestionProvider<FabricClientCommandSource> ids(boolean activeOnly) {
		return (ctx, b) -> {
			for (Carry c : activeOnly ? active() : all()) b.suggest(String.valueOf(c.id), Component.literal(c.customer + " " + c.target()));
			return b.buildFuture();
		};
	}

	private Carry carryArg(CommandContext<FabricClientCommandSource> ctx) {
		Carry c = find(LongArgumentType.getLong(ctx, "id"));
		if (c == null) Chat.error("No carry with that id - see /scd carry list.");
		return c;
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(ClientCommands.literal("carry")
				.executes(ctx -> {
					ScdScreen.open(new com.scd.client.ui.clickgui.ClickGuiScreen(mod));
					return 1;
				})
				.then(ClientCommands.literal("list").executes(ctx -> {
					listToChat();
					return 1;
				}))
				.then(ClientCommands.literal("add")
						.then(ClientCommands.literal("slayer")
								.then(ClientCommands.argument("player", StringArgumentType.word()).suggests(PLAYERS)
										.then(ClientCommands.argument("type", StringArgumentType.word()).suggests(TYPES)
												.then(ClientCommands.argument("tier", StringArgumentType.word()).suggests(TIERS)
														.then(ClientCommands.argument("price", StringArgumentType.word())
																.then(ClientCommands.argument("count", IntegerArgumentType.integer(1)).executes(ctx -> {
																	SlayerType type = SlayerType.parse(StringArgumentType.getString(ctx, "type"));
																	String tier = SlayerTier.normalize(StringArgumentType.getString(ctx, "tier"));
																	OptionalLong price = Numbers.parseCompactLong(StringArgumentType.getString(ctx, "price"));
																	if (type == null || tier == null || price.isEmpty() || price.getAsLong() <= 0) {
																		Chat.error("Usage: /scd carry add slayer <player> <type> <I-V> <price e.g. 1.3m> <count>");
																		return 0;
																	}
																	Carry c = addSlayer(StringArgumentType.getString(ctx, "player"), type, tier, price.getAsLong(),
																			IntegerArgumentType.getInteger(ctx, "count"));
																	Chat.success("Added #" + c.id + ": " + c.customer + " " + c.target() + " x" + c.unitsOwed
																			+ " for " + Numbers.coins(c.totalPrice()) + ".");
																	return 1;
																})))))))
						.then(ClientCommands.literal("dungeon")
								.then(ClientCommands.argument("player", StringArgumentType.word()).suggests(PLAYERS)
										.then(ClientCommands.argument("floor", StringArgumentType.word()).suggests(FLOORS)
												.then(ClientCommands.argument("price", StringArgumentType.word())
														.then(ClientCommands.argument("count", IntegerArgumentType.integer(1)).executes(ctx -> {
															String floor = Floor.normalize(StringArgumentType.getString(ctx, "floor"));
															OptionalLong price = Numbers.parseCompactLong(StringArgumentType.getString(ctx, "price"));
															if (floor == null || !Floor.CARRYABLE.contains(floor) || price.isEmpty() || price.getAsLong() <= 0) {
																Chat.error("Usage: /scd carry add dungeon <player> <F1-F7|M1-M7> <price> <count>");
																return 0;
															}
															Carry c = addDungeon(StringArgumentType.getString(ctx, "player"), floor, price.getAsLong(),
																	IntegerArgumentType.getInteger(ctx, "count"));
															Chat.success("Added #" + c.id + ": " + c.customer + " " + floor + " x" + c.unitsOwed
																	+ " for " + Numbers.coins(c.totalPrice()) + ".");
															return 1;
														})))))))
				.then(ClientCommands.literal("done")
						.then(ClientCommands.argument("id", LongArgumentType.longArg(1)).suggests(ids(true)).executes(ctx -> {
							Carry c = carryArg(ctx);
							if (c == null || !c.isActive()) return 0;
							finish(c);
							Chat.success("Closed " + c.customer + "'s carry.");
							return 1;
						})))
				.then(ClientCommands.literal("extend")
						.then(ClientCommands.argument("id", LongArgumentType.longArg(1)).suggests(ids(false))
								.then(ClientCommands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
									Carry c = carryArg(ctx);
									if (c == null) return 0;
									extend(c, IntegerArgumentType.getInteger(ctx, "amount"));
									Chat.success(c.customer + " now owes " + c.unitsOwed + " " + c.unit() + " (" + Numbers.coins(c.totalPrice()) + ").");
									return 1;
								}))))
				.then(ClientCommands.literal("adjust")
						.then(ClientCommands.argument("id", LongArgumentType.longArg(1)).suggests(ids(true))
								.then(ClientCommands.argument("delta", IntegerArgumentType.integer(-1000, 1000)).executes(ctx -> {
									Carry c = carryArg(ctx);
									if (c == null) return 0;
									adjust(c, IntegerArgumentType.getInteger(ctx, "delta"));
									Chat.success(c.customer + ": " + c.unitsDone + "/" + c.unitsOwed + " " + c.unit() + ".");
									return 1;
								}))))
				.then(ClientCommands.literal("remove")
						.then(ClientCommands.argument("id", LongArgumentType.longArg(1)).suggests(ids(false)).executes(ctx -> {
							Carry c = carryArg(ctx);
							if (c == null) return 0;
							remove(c);
							Chat.success("Removed carry #" + c.id + ".");
							return 1;
						}))));
	}

	private void listToChat() {
		List<Carry> list = all();
		Chat.info("Carries (" + active().size() + " active, " + Numbers.coins(outstandingTotal()) + " outstanding, "
				+ Numbers.coins(earnedTotal()) + " earned):");
		if (list.isEmpty()) Chat.raw(Component.literal("  none yet - /scd carry add ...").withStyle(ChatFormatting.GRAY));
		for (Carry c : list) {
			String progress = c.isActive() ? c.unitsDone + "/" + c.unitsOwed : "done";
			Chat.raw(Component.literal("  #" + c.id + " " + c.customer + " - " + c.target().trim() + " (" + progress + ")")
					.withStyle(c.isActive() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
		}
	}
}
