package com.scd.client.feature.dungeon;

import com.google.gson.JsonParser;
import com.scd.client.ScdMod;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.client.feature.world.WorldGizmos;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Show-only puzzle solvers (nothing is clicked for you):
 * <ul>
 *     <li><b>Quiz</b> - the right answer's pedestal is boxed and named in chat (answers: Odin's table, BSD-3).</li>
 *     <li><b>Three Weirdos</b> - the truthful NPC's chest is boxed.</li>
 *     <li><b>Higher/Lower Blaze</b> - the next blaze to kill (and the one after) boxed and linked.</li>
 * </ul>
 */
final class PuzzleSolvers {
	private static final Pattern QUIZ_CHOICE = Pattern.compile("^([ⓐⓑⓒ]) (.+)$");
	/** Room-relative answer pedestals (Odin frame = SCD frame for this 1x1 room). */
	private static final Map<String, BlockPos> QUIZ_SPOTS = Map.of("ⓐ", new BlockPos(20, 70, 6), "ⓑ", new BlockPos(15, 70, 9), "ⓒ", new BlockPos(10, 70, 6));
	private static final Pattern WEIRDO = Pattern.compile("^\\[NPC] ([A-Z][a-z]+): (?:The reward is(?: not in my chest!|n't in any of our chests\\.)|My chest (?:doesn't have the reward\\. We are all telling the truth\\.|has the reward and I'm telling the truth!)|At least one of them is lying, and the reward is not in [A-Z][a-z]+'s chest!|Both of them are telling the truth\\. Also, [A-Z][a-z]+ has the reward in their chest!)$");
	private static final Pattern BLAZE_HP = Pattern.compile("Blaze [\\d,]+/([\\d,]+)❤");
	/** SkyBlock year 1 began 2019-06-11 17:55 UTC; a year is 124 real hours. */
	private static final long SKYBLOCK_EPOCH_S = 1_560_275_700L, YEAR_S = 446_400L;

	private final ScdMod mod;
	private final DungeonFeature dungeon;
	private final Map<String, List<String>> quizAnswers = new HashMap<>();
	/** Answers SCD learned itself (checked before the bundled list). */
	private final com.scd.logic.dungeon.QuizMemory quizMemory = new com.scd.logic.dungeon.QuizMemory();
	private static final java.nio.file.Path QUIZ_FILE = com.scd.client.storage.ScdPaths.file("dungeon/quiz_learned.json");
	private List<String> quizSolutions = List.of();
	private String quizLetter;
	private BlockPos weirdoChest;
	private final List<Entity> blazeOrder = new ArrayList<>();

	PuzzleSolvers(ScdMod mod, DungeonFeature dungeon) {
		this.mod = mod;
		this.dungeon = dungeon;
		try (var in = PuzzleSolvers.class.getResourceAsStream("/assets/scd/dungeon/quiz.json")) {
			if (in != null) {
				JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().entrySet().forEach(e -> {
					List<String> list = new ArrayList<>();
					e.getValue().getAsJsonArray().forEach(v -> list.add(v.getAsString()));
					quizAnswers.put(e.getKey(), list);
				});
			}
		} catch (Exception e) {
			ScdLog.warn("Quiz answers failed to load", e);
		}
		if (java.nio.file.Files.isRegularFile(QUIZ_FILE)) {
			try (var r = java.nio.file.Files.newBufferedReader(QUIZ_FILE)) {
				quizMemory.load(r);
			} catch (Exception e) {
				ScdLog.warn("Learned quiz answers are invalid", e);
			}
		}
		mod.bus.subscribe(Events.ChatReceived.class, e -> {
			if (!e.isSystem() || !dungeon.state().inDungeon()) return;
			String line = e.clean().trim();
			// Learning runs even with the solver off, so SCD's own answer list keeps growing.
			if (quizMemory.onLine(line, com.scd.client.hypixel.Players.selfName())) saveQuiz();
			if (mod.config().dungeon.puzzleSolvers) onChat(line);
		});
		// Which pedestal you clicked in the Quiz room = the option you picked.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && "Quiz".equals(roomName())) {
				MappedRoom r = dungeon.rooms().current();
				for (var spot : QUIZ_SPOTS.entrySet()) {
					BlockPos w = r.toWorld(spot.getValue());
					if (w != null && w.distSqr(hit.getBlockPos()) <= 9) quizMemory.pick(spot.getKey());
				}
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
		mod.bus.subscribe(DungeonEvents.RoomEntered.class, e -> {
			weirdoChest = null;
			blazeOrder.clear();
		});
		mod.bus.subscribe(Events.Tick.class, e -> {
			if (mod.tasks.currentTick() % 5 == 0 && mod.config().dungeon.puzzleSolvers) blazes();
		});
		WorldGizmos.onWorldExtract(pt -> {
			if (mod.config().dungeon.puzzleSolvers && dungeon.state().inDungeon()) draw();
		});
	}

	private void saveQuiz() {
		try {
			java.nio.file.Files.createDirectories(QUIZ_FILE.getParent());
			java.nio.file.Files.writeString(QUIZ_FILE, quizMemory.toJson());
			ScdLog.info("[puzzles] quiz: learned an answer (" + quizMemory.size() + " known)");
		} catch (Exception e) {
			ScdLog.warn("Could not save quiz answers", e);
		}
	}

	private String roomName() {
		MappedRoom r = dungeon.rooms().current();
		return r != null ? r.name() : null;
	}

	private void onChat(String text) {
		// Quiz
		if (mod.config().dungeon.puzzleOn("Quiz")) {
			if (text.contains("answered Question #") || text.contains("answered the final question") || text.startsWith("[STATUE] Oruo the Omniscient:")) {
				quizLetter = null;
			}
			if (text.equals("What SkyBlock year is it?")) {
				long year = (System.currentTimeMillis() / 1000 - SKYBLOCK_EPOCH_S) / YEAR_S + 1;
				quizSolutions = List.of("Year " + year);
				quizLetter = null;
				return;
			}
			// SCD's own learned answers first, then the bundled list.
			String learned = quizMemory.answer(text);
			if (learned != null) {
				quizSolutions = List.of(learned);
				quizLetter = null;
				return;
			}
			for (var e : quizAnswers.entrySet()) {
				if (text.contains(e.getKey())) {
					quizSolutions = e.getValue();
					quizLetter = null;
					return;
				}
			}
			Matcher m = QUIZ_CHOICE.matcher(text);
			if (m.matches() && quizSolutions.contains(m.group(2).trim())) {
				quizLetter = m.group(1);
				Chat.info(Component.literal("Quiz: " + m.group(1) + " " + m.group(2)).withStyle(ChatFormatting.GREEN));
				return;
			}
		}
		// Three Weirdos
		if (!mod.config().dungeon.puzzleOn("Three Weirdos")) return;
		Matcher w = WEIRDO.matcher(text);
		if (w.matches()) findWeirdoChest(w.group(1));
	}

	/** The truthful NPC's chest: the chest block right next to its armor stand. */
	private void findWeirdoChest(String name) {
		var mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ArmorStand) || !name.equals(e.getName().getString()) || e.distanceTo(mc.player) > 40) continue;
			BlockPos base = e.blockPosition();
			for (BlockPos p : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 1, 2))) {
				if (mc.level.getBlockState(p).getBlock() == Blocks.CHEST) {
					weirdoChest = p.immutable();
					Chat.info(Component.literal("Three Weirdos: " + name + "'s chest").withStyle(ChatFormatting.GREEN));
					return;
				}
			}
		}
	}

	private void blazes() {
		blazeOrder.clear();
		if (!mod.config().dungeon.puzzleOn("Higher/Lower Blaze")) return;
		String room = roomName();
		if (!"Higher Blaze".equals(room) && !"Lower Blaze".equals(room)) return;
		var mc = Minecraft.getInstance();
		if (mc.level == null) return;
		List<Object[]> found = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof ArmorStand) || e.getCustomName() == null) continue;
			Matcher m = BLAZE_HP.matcher(e.getCustomName().getString());
			if (m.find()) found.add(new Object[]{e, Integer.parseInt(m.group(1).replace(",", ""))});
		}
		// Lower Blaze: highest HP first; Higher Blaze: lowest first (Odin).
		found.sort((a, b) -> "Lower Blaze".equals(room) ? Integer.compare((int) b[1], (int) a[1]) : Integer.compare((int) a[1], (int) b[1]));
		for (Object[] f : found) blazeOrder.add((Entity) f[0]);
	}

	private void draw() {
		if (quizLetter != null && "Quiz".equals(roomName()) && mod.config().dungeon.puzzleOn("Quiz")) {
			MappedRoom r = dungeon.rooms().current();
			BlockPos spot = r.toWorld(QUIZ_SPOTS.get(quizLetter));
			if (spot != null) {
				WorldGizmos.block(spot.below(), 0xFF4ADE80, false);
				WorldGizmos.label(Vec3.atCenterOf(spot).add(0, 1, 0), quizLetter + " answer", 0xFF4ADE80, true);
			}
		}
		if (weirdoChest != null && mod.config().dungeon.puzzleOn("Three Weirdos")) WorldGizmos.block(weirdoChest, 0xFF4ADE80, true);
		if (!blazeOrder.isEmpty()) {
			Entity next = blazeOrder.getFirst();
			WorldGizmos.box(blazeBox(next), 0xFF4ADE80, true);
			if (blazeOrder.size() > 1) {
				Entity after = blazeOrder.get(1);
				WorldGizmos.box(blazeBox(after), 0xFFFACC15, true);
				WorldGizmos.line(next.position().add(0, -1, 0), after.position().add(0, -1, 0), 0xFFFACC15, true);
			}
		}
	}

	/** The blaze sits under its name tag. */
	private static AABB blazeBox(Entity nameTag) {
		Vec3 p = nameTag.position();
		return new AABB(p.x - 0.4, p.y - 2.0, p.z - 0.4, p.x + 0.4, p.y - 0.2, p.z + 0.4);
	}
}
