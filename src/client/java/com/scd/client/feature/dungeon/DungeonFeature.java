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
import com.scd.client.storage.JsonStore;
import com.scd.client.storage.ScdPaths;
import com.scd.logic.dungeon.ScoreCalculator;
import com.scd.client.ui.ScdScreen;
import com.scd.logic.Numbers;
import com.scd.logic.dungeon.CompletionParser;
import com.scd.logic.dungeon.CompletionReport;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
	private Integer estimateAtEnd;
	private long lastRunLagMs;
	private RoomTimes roomTimes;
	private SecretTracker secrets;
	private JsonStore<DungeonRecords> records;

	@Override
	public void init(ScdMod mod) {
		this.mod = mod;
		this.mayor = mod.feature(MayorService.class);
		this.parser = new CompletionParser(this::onReport);
		this.records = JsonStore.open(ScdPaths.file("dungeon.json"), DungeonRecords.class, DungeonRecords::new);
		mod.bus.subscribe(Events.EntityDied.class, e -> onEntityDied(e.entity()));
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
		mod.huds.add(new ScoreHud(mod::config, () -> score, this::splitLines, this::secretPacing));
		mod.huds.add(new RoomHud(mod::config, rooms::current, this::roomTimeSummary));
		new ChestProfit(mod);
		new DungeonExtras(mod, this);
		new DoorKeys(mod, this);
		roomTimes = new RoomTimes(mod, this);
		secrets = new SecretTracker(mod, this);
		mod.huds.add(new DungeonMapHud(mod::config, this, secrets));
		learning = new RoomLearning(mod, this);
		capture = new RoomCapture(mod, this);
		new StarredMobs(mod, this);
		new DraftReminder(mod, this);
		new LividFinder(mod, this);
		studio = new RoomStudio(mod, this);
		new PuzzleSolvers(mod, this);
		new PuzzleSolvers2(mod, this);
		new BloodCamp(mod, this);
	}

	private void onTick() {
		DungeonState previous = state;
		state = mod.active() ? DungeonState.read(mod.game) : DungeonState.NONE;
		if (state.inDungeon() && !previous.inDungeon()) run.reset();
		score = state.inDungeon() ? run.compute(state, mod.game, mayor.hasPerk("EZPZ"), mod.config().dungeon.assumeSpiritPet, roomFacts()) : null;
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
						+ " core " + r.core + " tiles " + r.tiles().stream().map(t -> t[0] + "," + t[1]).toList());
			}
			mod.bus.post(new DungeonEvents.RoomEntered(r));
		}
	}

	/** The mimic is the only baby zombie in F6/F7 dungeons; its death is its own score event. */
	private void onEntityDied(net.minecraft.world.entity.LivingEntity entity) {
		if (!state.inDungeon() || run.inBoss() || run.mimicDead()) return;
		if (!ScoreCalculator.hasMimic(state.floor())) return;
		if (entity instanceof net.minecraft.world.entity.monster.zombie.Zombie z && z.isBaby()) {
			run.mimicKilled();
			ScdLog.info("[score] mimic died (entity " + entity.getId() + ")");
		}
	}

	private DungeonRun.RoomFacts roomFacts() {
		var list = rooms.rooms();
		if (list.isEmpty()) return new DungeonRun.RoomFacts(0, 0, false, 0);
		int identified = 0, secrets = 0;
		for (MappedRoom r : list) {
			if (r.info() == null) continue;
			identified++;
			secrets += r.info().secrets();
		}
		int mapRooms = rooms.layout() != null ? rooms.layout().rooms().size() : 0;
		return new DungeonRun.RoomFacts(Math.max(identified, mapRooms), identified, identified == list.size(), secrets);
	}

	private List<String> splitLines() {
		if (!mod.config().dungeon.scoreSplits || state.floor() == null) return List.of();
		var got = run.splits();
		Map<String, Long> best = records.get().bestSplits.getOrDefault(state.floor(), Map.of());
		List<String> out = new ArrayList<>();
		for (DungeonRun.Split s : DungeonRun.Split.values()) {
			Long t = got.get(s);
			if (t == null) continue;
			out.add(s.label + " " + Numbers.duration(t) + delta(t, best.get(s.name())));
		}
		long now = run.elapsedMs();
		if (now >= 0 && got.size() < DungeonRun.Split.values().length) {
			DungeonRun.Split next = DungeonRun.Split.values()[got.size()];
			Long pb = best.get(next.name());
			if (pb != null) out.add(next.label + " PB " + Numbers.duration(pb));
		}
		return out;
	}

	private static String delta(long t, Long pb) {
		if (pb == null) return "";
		long d = t - pb;
		return (d <= 0 ? "  -" : "  +") + Numbers.duration(Math.abs(d));
	}

	private void recordRun(CompletionReport report, long clearMs) {
		String floor = report.floorKey() != null ? report.floorKey() : state.floor();
		if (floor == null) return;
		run.split(DungeonRun.Split.CLEAR);
		Map<String, Long> times = new HashMap<>();
		run.splits().forEach((k, v) -> times.put(k.name(), v));
		// Hypixel's own clear time beats our clock (which may have started late).
		if (clearMs > 0) times.put(DungeonRun.Split.CLEAR.name(), clearMs);
		DungeonRecords data = records.get();
		Map<String, Long> best = data.bestSplits.computeIfAbsent(floor, k -> new HashMap<>());
		List<String> newPbs = new ArrayList<>();
		times.forEach((k, v) -> {
			Long old = best.get(k);
			if (old == null || v < old) {
				if (old != null) newPbs.add(k);
				best.put(k, v);
			}
		});
		DungeonRecords.RunResult r = new DungeonRecords.RunResult();
		r.floor = floor;
		r.endedAt = System.currentTimeMillis();
		r.finalScore = report.teamScore();
		r.estimate = estimateAtEnd;
		r.splits = times;
		r.deaths = report.deaths() != null ? report.deaths() : 0;
		r.secrets = report.secretsFound();
		r.lagMs = run.lagMs();
		lastRunLagMs = r.lagMs;
		data.runs.addFirst(r);
		while (data.runs.size() > 200) data.runs.removeLast();
		records.markDirty();
		if (report.teamScore() != null && estimateAtEnd != null) {
			ScdLog.info("[score] " + floor + " estimate " + estimateAtEnd + " vs final " + report.teamScore()
					+ (estimateAtEnd.equals(report.teamScore()) ? " (exact)" : " (off by " + (estimateAtEnd - report.teamScore()) + ")"));
		}
		if (!newPbs.isEmpty()) {
			Chat.info(Component.literal("New " + floor + " split PB" + (r.lagMs >= 1_000 ? " (despite " + Numbers.durationTenths(r.lagMs) + " server lag)" : "") + ": " + String.join(", ", newPbs.stream()
					.map(k -> DungeonRun.Split.valueOf(k).label + " " + Numbers.duration(best.get(k))).toList())).withStyle(ChatFormatting.GOLD));
		}
	}

	private void onReport(CompletionReport report) {
		String sig = report.runSignature();
		if (!sig.equals(lastRunSignature)) {
			// First block of a new completion: this is the earliest reliable "run done" signal.
			lastRunSignature = sig;
			bestReport = report;
			estimateAtEnd = score != null ? score.total() : null;
			ScdLog.guard("dungeon run record", () -> recordRun(report, Numbers.parseClearTimeMs(report.clearTime())));
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
		var show = mod.config().dungeon.summary;
		List<String> parts = new ArrayList<>();
		if (show.floor) parts.add((r.floorKey() != null ? r.floorKey() : "?") + (r.boss() != null ? " " + r.boss() : ""));
		if (show.time && r.clearTime() != null) parts.add(r.clearTime());
		if (show.score && r.teamScore() != null) {
			String score = r.teamScore() + " (" + r.scoreRank() + ")";
			if (show.estimate && estimateAtEnd != null && !estimateAtEnd.equals(r.teamScore())) score += " [est " + estimateAtEnd + "]";
			parts.add(score);
		}
		if (show.secrets && r.secretsFound() != null) parts.add(r.secretsFound() + " secrets");
		if (show.deaths && r.deaths() != null) parts.add(r.deaths() + " deaths");
		if (show.lag && lastRunLagMs >= 1_000) parts.add(Numbers.durationTenths(lastRunLagMs) + " server lag");
		if (show.damage && r.totalDamage() != null) parts.add(Numbers.compactCount(r.totalDamage()) + " dmg");
		if (show.xp && r.cataExp() != null) parts.add("+" + Numbers.compactCount(Math.round(r.cataExp())) + " Cata XP");
		if (parts.isEmpty()) return;
		String sb = String.join(" · ", parts);
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

	public DungeonRecords records() {
		return records.get();
	}

	/**
	 * Where the secrets you still need are: "here 2 · Altar 3 · Pit 2" - the room you're in first,
	 * then the nearest unfinished rooms. Null when there's nothing to say.
	 */
	String secretPacing() {
		if (secrets == null || score == null || score.inBoss()) return null;
		List<MappedRoom> open = secrets.unfinished();
		if (open.isEmpty()) return null;
		MappedRoom here = rooms.current();
		var p = net.minecraft.client.Minecraft.getInstance().player;
		int px = p != null ? com.scd.logic.dungeon.room.DungeonGrid.tileOf(p.getBlockX()) : 0;
		int pz = p != null ? com.scd.logic.dungeon.room.DungeonGrid.tileOf(p.getBlockZ()) : 0;
		open.sort(java.util.Comparator.comparingInt((MappedRoom r) -> r == here ? -1 : distance(r, px, pz)));
		StringBuilder sb = new StringBuilder();
		int shown = 0;
		for (MappedRoom r : open) {
			if (shown++ == 3) break;
			if (!sb.isEmpty()) sb.append(" · ");
			sb.append(r == here ? "here" : r.name()).append(' ').append(r.info().secrets() - secrets.found(r));
		}
		return sb.toString();
	}

	private static int distance(MappedRoom r, int px, int pz) {
		int best = Integer.MAX_VALUE;
		for (int[] t : r.tiles()) best = Math.min(best, Math.abs(t[0] - px) + Math.abs(t[1] - pz));
		return best;
	}

	boolean scoreInBoss() {
		return run.inBoss();
	}

	SecretTracker secrets() {
		return secrets;
	}

	void recordsDirty() {
		records.markDirty();
	}

	String roomTimeSummary(String room) {
		return roomTimes != null && room != null ? roomTimes.summary(room) : null;
	}

	public RoomEngine rooms() {
		return rooms;
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(studio.roomsCommand(capture)).then(studio.secretCommand()).then(studio.studioCommand());
		root.then(ClientCommands.literal("dungeon")
				.executes(ctx -> {
					ScdScreen.open(new com.scd.client.ui.clickgui.ClickGuiScreen(mod));
					return 1;
				})
				.then(ClientCommands.literal("score").executes(ctx -> {
					if (score == null) {
						Chat.info("Not in a recognized dungeon right now.");
						return 0;
					}
					var b = score.breakdown();
					Chat.info("Score " + score.total() + (score.baselined() ? " (Hypixel sidebar, ahead of the formula)" : " (formula)")
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
						.then(ClientCommands.literal("learned").executes(ctx -> {
							var l = learning.learner();
							Chat.info("SCD has learned " + l.namedCount() + " named rooms itself (" + l.entries().size()
									+ " incl. unnamed) of the " + rooms.database().size() + " it knows. Unnamed ones: /scd dungeon room name.");
							return 1;
						}))
						.then(ClientCommands.literal("reload").executes(ctx -> {
							rooms.reloadDatabase();
							Chat.info("Room database reloaded: " + rooms.database().size() + " rooms.");
							return 1;
						}))));
	}

	private RoomLearning learning;
	private RoomCapture capture;
	private RoomStudio studio;

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
