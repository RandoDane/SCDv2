package com.scd.client.feature.dungeon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.scd.client.Feature;
import com.scd.client.ScdMod;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.client.feature.mayor.MayorService;
import com.scd.client.ui.ScdScreen;
import com.scd.logic.Numbers;
import com.scd.logic.dungeon.CompletionParser;
import com.scd.logic.dungeon.CompletionReport;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Catacombs: live score estimate + HUD, S/S+ milestone alerts, end-of-run summary, the RunCompleted
 * event that credits dungeon carries, and the room engine (which room you're in, and its frame).
 */
public final class DungeonFeature implements Feature {
	/** If the fuller second report block never arrives, summarize from the first after this long. */
	private static final int SUMMARY_WAIT_TICKS = 40;

	private ScdMod mod;
	private MayorService mayor;
	private final DungeonRun run = new DungeonRun();
	private final RoomEngine rooms = new RoomEngine();
	private CompletionParser parser;
	private DungeonState state = DungeonState.NONE;
	private DungeonRun.Score score;
	private String lastRunSignature;
	private CompletionReport bestReport;
	private boolean summaryPending;

	@Override
	public void init(ScdMod mod) {
		this.mod = mod;
		this.mayor = mod.feature(MayorService.class);
		this.parser = new CompletionParser(this::onReport);
		mod.bus.subscribe(Events.Tick.class, e -> onTick());
		mod.bus.subscribe(Events.ChatReceived.class, e -> {
			if (!e.isSystem()) {
				// Mimic/prince/bat call-outs usually come from party members' mods via party chat.
				if (state.inDungeon()) run.onMessage(e.text());
				return;
			}
			parser.accept(e.text());
			if (state.inDungeon()) run.onMessage(e.text());
		});
		mod.huds.add(new ScoreHud(mod::config, () -> score));
		mod.huds.add(new RoomHud(mod::config, rooms::current));
	}

	private void onTick() {
		DungeonState previous = state;
		state = mod.active() ? DungeonState.read(mod.game) : DungeonState.NONE;
		if (state.inDungeon() && !previous.inDungeon()) run.reset();
		score = state.inDungeon() ? run.compute(state, mod.game, mayor.hasPerk("EZPZ")) : null;
		if (score != null && mod.config().dungeon.scoreMilestoneAlerts) {
			int hit = run.milestone(score.total());
			if (hit > 0) {
				Chat.title(Component.literal(hit >= 300 ? "S+ reached!" : "S reached!").withStyle(ChatFormatting.GOLD),
						Component.literal("Score " + score.total()), true);
			}
		}
		if (rooms.tick(state.inDungeon(), run.inBoss(), state.floor())) {
			MappedRoom r = rooms.current();
			if (r != null) {
				var a = r.anchor();
				ScdLog.info("[rooms] entered " + r.label() + (a != null ? " " + a.rotation() + " via " + r.anchorSource + " clay " + a.x() + "," + a.z() : " (no anchor)")
						+ " core " + r.core);
			}
			mod.bus.post(new DungeonEvents.RoomEntered(r));
		}
	}

	private void onReport(CompletionReport report) {
		String sig = report.runSignature();
		if (!sig.equals(lastRunSignature)) {
			// First block of a new completion: this is the earliest reliable "run done" signal.
			lastRunSignature = sig;
			bestReport = report;
			run.markCompleted();
			mod.bus.post(new DungeonEvents.RunCompleted(report, Numbers.parseClearTimeMs(report.clearTime())));
			summaryPending = true;
			mod.tasks.later(SUMMARY_WAIT_TICKS, "dungeon summary", this::summarize);
		} else {
			bestReport = report;
			summarize();
		}
	}

	private void summarize() {
		if (!summaryPending || bestReport == null) return;
		summaryPending = false;
		if (!mod.config().dungeon.completionSummary) return;
		CompletionReport r = bestReport;
		StringBuilder sb = new StringBuilder();
		sb.append(r.floorKey() != null ? r.floorKey() : "?").append(" ").append(r.boss() != null ? r.boss() : "");
		if (r.clearTime() != null) sb.append(" in ").append(r.clearTime());
		if (r.teamScore() != null) sb.append(" · ").append(r.teamScore()).append(" (").append(r.scoreRank()).append(")");
		if (r.secretsFound() != null) sb.append(" · ").append(r.secretsFound()).append(" secrets");
		if (r.deaths() != null) sb.append(" · ").append(r.deaths()).append(" deaths");
		if (r.totalDamage() != null) sb.append(" · ").append(Numbers.compactCount(r.totalDamage())).append(" dmg");
		if (r.cataExp() != null) sb.append(" · +").append(Numbers.compactCount(Math.round(r.cataExp()))).append(" Cata XP");
		Chat.info(Component.literal("Run complete: ").withStyle(ChatFormatting.AQUA).append(Component.literal(sb.toString()).withStyle(ChatFormatting.WHITE)));
	}

	public DungeonState state() {
		return state;
	}

	public DungeonRun.Score score() {
		return score;
	}

	public CompletionReport lastReport() {
		return bestReport;
	}

	String roomEngineState() {
		return rooms.describe();
	}

	public RoomEngine rooms() {
		return rooms;
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(ClientCommands.literal("dungeon")
				.executes(ctx -> {
					ScdScreen.open(new DungeonScreen(null, mod, this));
					return 1;
				})
				.then(ClientCommands.literal("score").executes(ctx -> {
					if (score == null) {
						Chat.info("Not in a recognized dungeon right now.");
						return 0;
					}
					var b = score.breakdown();
					Chat.info("Score " + score.total() + (score.baselined() ? " (Hypixel baseline + live speed/deaths)" : " (estimate)")
							+ " = skill " + b.skill() + " + explore " + b.explore() + " + speed " + b.speed() + " + bonus " + b.bonus());
					Chat.info("rooms " + score.completedRooms() + " (+pad " + (b.paddedCompletedRooms() - score.completedRooms()) + ")/"
							+ b.totalRoomsEstimate() + ", secrets " + score.secretsPercent() + "%, crypts " + score.crypts()
							+ ", deaths " + score.deaths() + ", puzzles left " + score.incompletePuzzles() + ", boss " + score.inBoss());
					return 1;
				}))
				.then(ClientCommands.literal("room")
						.executes(ctx -> roomInfo())
						.then(ClientCommands.literal("name")
								.then(ClientCommands.argument("name", StringArgumentType.string())
										.executes(ctx -> nameRoom(StringArgumentType.getString(ctx, "name"), -1))
										.then(ClientCommands.argument("secrets", IntegerArgumentType.integer(0, 20))
												.executes(ctx -> nameRoom(StringArgumentType.getString(ctx, "name"), IntegerArgumentType.getInteger(ctx, "secrets")))))))
				.then(ClientCommands.literal("rooms")
						.executes(ctx -> listRooms())
						.then(ClientCommands.literal("reload").executes(ctx -> {
							rooms.reloadDatabase();
							Chat.info("Room database reloaded: " + rooms.database().size() + " rooms.");
							return 1;
						}))));
	}

	private int roomInfo() {
		MappedRoom r = rooms.current();
		if (r == null) {
			Chat.info("Not in a mapped room. " + rooms.describe());
			return 0;
		}
		var a = r.anchor();
		Chat.info(r.label() + " · " + (r.kind() != null ? r.kind().name().toLowerCase(java.util.Locale.ROOT) : "?")
				+ (r.info() != null ? " · " + r.info().shape().key + " · " + r.info().secrets() + " secrets · " + r.info().crypts() + " crypts" : "")
				+ " · " + r.checkmark().name().toLowerCase(java.util.Locale.ROOT));
		Chat.info("core " + r.core + " · tiles " + r.tiles().stream().map(t -> t[0] + "," + t[1]).toList()
				+ " · roof y" + r.highestBlock);
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (a == null) {
			Chat.info("No anchor yet (footprint incomplete or clay not found).");
		} else if (mc.player != null) {
			var rel = r.toRelative(mc.player.blockPosition());
			String look = "";
			if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
				var lr = r.toRelative(hit.getBlockPos());
				look = " · looking at " + lr.getX() + " " + lr.getY() + " " + lr.getZ();
			}
			Chat.info(a.rotation() + " via " + r.anchorSource + ", clay " + a.x() + "," + a.z()
					+ " · you " + rel.getX() + " " + rel.getY() + " " + rel.getZ() + look);
		}
		return 1;
	}

	private int nameRoom(String name, int secrets) {
		try {
			Chat.info(rooms.nameCurrentRoom(name, secrets));
		} catch (Exception e) {
			Chat.error("Could not save room: " + e.getMessage());
		}
		return 1;
	}

	private int listRooms() {
		var list = rooms.rooms();
		if (list.isEmpty()) {
			Chat.info("No rooms mapped. " + rooms.describe());
			return 0;
		}
		Chat.info(rooms.describe());
		for (MappedRoom r : list) {
			var a = r.anchor();
			Chat.info(" " + r.tiles().stream().map(t -> t[0] + "," + t[1]).toList() + " " + r.label()
					+ (a != null ? " · " + a.rotation() + " (" + r.anchorSource + ")" : "")
					+ " · " + r.checkmark().name().toLowerCase(java.util.Locale.ROOT));
		}
		return 1;
	}
}
