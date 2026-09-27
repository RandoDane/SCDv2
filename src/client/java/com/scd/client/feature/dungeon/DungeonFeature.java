package com.scd.client.feature.dungeon;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.scd.client.Feature;
import com.scd.client.ScdMod;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
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
 * event that credits dungeon carries, and the opt-in room-mapping contributor.
 */
public final class DungeonFeature implements Feature {
	/** If the fuller second report block never arrives, summarize from the first after this long. */
	private static final int SUMMARY_WAIT_TICKS = 40;

	private ScdMod mod;
	private MayorService mayor;
	private final DungeonRun run = new DungeonRun();
	private final RoomScanner scanner = new RoomScanner();
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
		scanner.tick(mod.backend, state, mod.config().dungeon.roomMapping);
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

	String scannerState() {
		return scanner.describe();
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
				})));
	}
}
