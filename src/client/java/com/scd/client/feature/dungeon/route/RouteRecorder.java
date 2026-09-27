package com.scd.client.feature.dungeon.route;

import com.scd.client.core.ScdLog;
import com.scd.client.feature.dungeon.MappedRoom;
import com.scd.logic.dungeon.route.RouteStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Records a route by playing it: the path is sampled as you move, a big jump in one tick is an
 * etherwarp, broken blocks are mines, levers/buttons are interacts, TNT placed with a right-click is
 * a TNT spot, thrown pearls keep their angles. Each secret (chest/skull click, item pickup, bat
 * kill, or {@code /scd route mark} for an exit) closes the current step and starts the next.
 * Crouch + left-click drops a node: from then on that step's path is straight lines through the
 * nodes instead of the sampled walk.
 */
final class RouteRecorder {
	private static final double SAMPLE = 2.4;
	private static final double WARP_JUMP = 4.5;

	private MappedRoom room;
	private long lastFinish;
	private boolean lastWasGuess;
	/** Recent candidates for "what was that secret" when only the counter says one was found. */
	private BlockPos recentClick;
	private long recentClickAt;
	private Vec3 recentItem;
	private long recentItemAt;
	private final List<RouteStep> steps = new ArrayList<>();
	private RouteStep step;
	private Vec3 lastSample;
	private Vec3 lastTick;

	boolean active() {
		return room != null;
	}

	MappedRoom room() {
		return room;
	}

	List<RouteStep> steps() {
		return steps;
	}

	void start(MappedRoom room, Vec3 player) {
		this.room = room;
		steps.clear();
		step = new RouteStep();
		lastSample = null;
		lastTick = player;
		sample(player, true);
	}

	/** Ends recording; returns the finished steps (a trailing partial step becomes an exit). */
	List<RouteStep> stop() {
		// A trailing step that is just its starting point (e.g. right after the last secret) is noise.
		boolean moved = step != null && (step.locations.size() > 1 || !step.etherwarps.isEmpty() || !step.mines.isEmpty()
				|| !step.interacts.isEmpty() || !step.tnts.isEmpty() || !step.pearls.isEmpty());
		if (moved) {
			if (step.secret == null && !step.locations.isEmpty()) {
				step.secretType = RouteStep.SecretType.EXIT_ROUTE;
				step.secret = step.locations.getLast();
			}
			if (step.secret != null) steps.add(step);
		}
		List<RouteStep> out = List.copyOf(steps);
		room = null;
		step = null;
		steps.clear();
		return out;
	}

	void cancel() {
		room = null;
		step = null;
		steps.clear();
	}

	/** Drops the last finished step (and the one in progress). */
	boolean undo() {
		if (steps.isEmpty()) return false;
		steps.removeLast();
		step = new RouteStep();
		return true;
	}

	void tick(Vec3 player) {
		if (!active() || room.anchor() == null) return;
		if (lastTick != null && lastTick.distanceToSqr(player) > WARP_JUMP * WARP_JUMP) {
			// Teleported (etherwarp/AOTV): the block under the landing spot is the target.
			step.etherwarps.add(rel(BlockPos.containing(player).below()));
			sample(player, true);
		} else {
			sample(player, false);
		}
		lastTick = player;
	}

	private void sample(Vec3 player, boolean force) {
		if (room.anchor() == null) return;
		// Hand-placed nodes replace the walked path for this step.
		if (step.manual && !force) return;
		if (!force && lastSample != null && lastSample.distanceToSqr(player) < SAMPLE * SAMPLE) return;
		step.locations.add(rel(BlockPos.containing(player)));
		lastSample = player;
	}

	/**
	 * Adds the block the player stands on as a path node. The first node of a step drops the
	 * sampled walk (keeping where the step started), so the step becomes straight lines.
	 * Returns the node number within the step, or 0 when not recording.
	 */
	int addNode(Vec3 player) {
		if (!active() || room.anchor() == null || step == null) return 0;
		if (!step.manual) {
			int[] start = step.locations.isEmpty() ? null : step.locations.getFirst();
			step.locations.clear();
			if (start != null) step.locations.add(start);
			step.manual = true;
		}
		int[] node = rel(BlockPos.containing(player));
		int[] last = step.locations.isEmpty() ? null : step.locations.getLast();
		if (last == null || last[0] != node[0] || last[1] != node[1] || last[2] != node[2]) step.locations.add(node);
		lastSample = player;
		ScdLog.info("[routes] node " + (step.locations.size() - 1) + " at " + fmt(node));
		return Math.max(1, step.locations.size() - 1);
	}

	private BlockPos lastInteract;
	private long lastInteractAt;

	void onInteract(BlockPos pos, Block block, boolean holdingTnt, Vec3 player) {
		if (!active() || room.anchor() == null) return;
		// The use callback can fire more than once per click (hands, retries): one click, one point.
		long now = System.currentTimeMillis();
		if (pos.equals(lastInteract) && now - lastInteractAt < 500) return;
		lastInteract = pos.immutable();
		lastInteractAt = now;
		recentClick = lastInteract;
		recentClickAt = now;
		if (holdingTnt) {
			step.tnts.add(rel(pos));
		} else if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.PLAYER_HEAD || block == Blocks.PLAYER_WALL_HEAD
				|| block == Blocks.SKELETON_SKULL || block == Blocks.SKELETON_WALL_SKULL) {
			finish(RouteStep.SecretType.INTERACT, rel(pos), player);
		} else if (isInteractable(block)) {
			step.interacts.add(rel(pos));
		}
		// Anything else (a bow shot or ability aimed at a wall) isn't part of the route.
	}

	private static boolean isInteractable(Block block) {
		var state = block.defaultBlockState();
		return block == Blocks.LEVER || state.is(net.minecraft.tags.BlockTags.BUTTONS) || state.is(net.minecraft.tags.BlockTags.DOORS)
				|| state.is(net.minecraft.tags.BlockTags.TRAPDOORS) || state.is(net.minecraft.tags.BlockTags.FENCE_GATES);
	}

	void onItemPickup(Vec3 itemPos, Vec3 player) {
		if (!active() || room.anchor() == null || itemPos.distanceToSqr(player) > 6 * 6) return;
		recentItem = itemPos;
		recentItemAt = System.currentTimeMillis();
		finish(RouteStep.SecretType.ITEM, rel(BlockPos.containing(itemPos)), player);
	}

	/** Wither essence ("You found a Wither Essence!"): the skull nearest the player. */
	void onEssence(BlockPos skull, Vec3 player) {
		if (!active() || room.anchor() == null) return;
		finish(RouteStep.SecretType.INTERACT, rel(skull != null ? skull : BlockPos.containing(player)), player);
	}

	/** The action bar secret counter went up; uses the freshest clue for what it was. */
	void onSecretCounted(Vec3 player, BlockPos lookedAt) {
		if (!active() || room.anchor() == null) return;
		long now = System.currentTimeMillis();
		if (now - lastFinish < 1500) return; // already recorded from a precise signal
		if (recentItem != null && now - recentItemAt < 3000) {
			finish(RouteStep.SecretType.ITEM, rel(BlockPos.containing(recentItem)), player, true);
		} else if (recentClick != null && now - recentClickAt < 3000) {
			finish(RouteStep.SecretType.INTERACT, rel(recentClick), player, true);
		} else {
			finish(RouteStep.SecretType.INTERACT, rel(lookedAt != null ? lookedAt : BlockPos.containing(player)), player, true);
		}
	}

	void onBatDeath(Vec3 batPos, Vec3 player) {
		if (!active() || room.anchor() == null || batPos.distanceToSqr(player) > 16 * 16) return;
		finish(RouteStep.SecretType.BAT, rel(BlockPos.containing(batPos)), player);
	}

	void onBlockBroken(BlockPos pos) {
		if (!active() || room.anchor() == null) return;
		int[] r = rel(pos);
		for (int[] m : step.mines) if (m[0] == r[0] && m[1] == r[1] && m[2] == r[2]) return;
		step.mines.add(r);
	}

	void onPearl(Vec3 player, float yaw, float pitch) {
		if (!active() || room.anchor() == null) return;
		step.pearls.add(rel(BlockPos.containing(player)));
		step.pearlAngles.add(new float[]{yaw, pitch});
	}

	/** Manual secret/exit marker at the player's feet. */
	void markExit(Vec3 player) {
		if (!active() || room.anchor() == null) return;
		finish(RouteStep.SecretType.EXIT, rel(BlockPos.containing(player)), player);
	}

	private void finish(RouteStep.SecretType type, int[] secret, Vec3 player) {
		finish(type, secret, player, false);
	}

	/**
	 * Several signals can describe one secret (a click, the essence chat, the counter). Within 1.5s
	 * they merge into the last step when it is the same spot, or when the last step was only a
	 * counter guess (then the precise signal wins). Anything else is a new secret.
	 */
	private void finish(RouteStep.SecretType type, int[] secret, Vec3 player, boolean guess) {
		long now = System.currentTimeMillis();
		if (now - lastFinish < 1500 && !steps.isEmpty() && type != RouteStep.SecretType.EXIT) {
			RouteStep last = steps.getLast();
			if (last.secretType != RouteStep.SecretType.EXIT && last.secret != null) {
				boolean samePlace = Math.abs(last.secret[0] - secret[0]) + Math.abs(last.secret[1] - secret[1]) + Math.abs(last.secret[2] - secret[2]) <= 3;
				// The counter confirming a secret we already have.
				if (guess) return;
				// A precise signal replacing the counter's guess for the same secret.
				if (lastWasGuess) {
					last.secretType = type;
					last.secret = secret;
					lastWasGuess = false;
					ScdLog.info("[routes] step " + steps.size() + " refined: " + type.key + " at " + fmt(secret));
					return;
				}
				// The same secret reported twice (double click, skull click + essence message).
				if (samePlace && last.secretType == type) return;
			}
		}
		lastWasGuess = guess;
		ScdLog.info("[routes] step " + (steps.size() + 1) + ": " + type.key + " at " + fmt(secret) + (guess ? " (from the secret counter)" : ""));
		lastFinish = now;
		sample(player, true);
		step.secretType = type;
		step.secret = secret;
		steps.add(step);
		step = new RouteStep();
		lastSample = null;
		sample(player, true);
	}

	private static String fmt(int[] p) {
		return p[0] + "," + p[1] + "," + p[2];
	}

	private int[] rel(BlockPos world) {
		BlockPos r = room.toRelative(world);
		return new int[]{r.getX(), r.getY(), r.getZ()};
	}

	/** Live overlay while recording: the start block, every secret taken so far, and the path in progress. */
	void render(boolean walls, float partialTick, double smoothing) {
		if (!active() || room.anchor() == null) return;
		RouteStep first = !steps.isEmpty() ? steps.getFirst() : step;
		if (first != null && !first.locations.isEmpty()) RouteRunner.markStart(world(first.locations.getFirst()), walls);
		for (int i = 0; i < steps.size(); i++) {
			RouteStep s = steps.get(i);
			if (s.secret == null) continue;
			boolean exit = s.secretType == RouteStep.SecretType.EXIT || s.secretType == RouteStep.SecretType.EXIT_ROUTE;
			RouteRunner.markSecret(world(s.secret), (i + 1) + " " + (exit ? "waypoint" : s.secretType.key.toLowerCase(java.util.Locale.ROOT)),
					exit ? RouteRunner.PATH : RouteRunner.SECRET, walls);
		}
		// The whole path so far (straightened like playback), not just the leg in progress.
		Vec3 last = null;
		for (RouteStep done : steps) {
			markNodes(done, walls);
			List<Vec3> pts = RouteRunner.path(room, done.locations, done.manual ? 0 : smoothing);
			RouteRunner.polyline(last, pts, RouteRunner.PATH, walls);
			if (!pts.isEmpty()) last = pts.getLast();
		}
		if (step != null) {
			markNodes(step, walls);
			List<Vec3> pts = RouteRunner.path(room, step.locations, step.manual ? 0 : smoothing);
			RouteRunner.polyline(last, pts, RouteRunner.PATH, walls);
			if (!pts.isEmpty()) last = pts.getLast();
			var player = net.minecraft.client.Minecraft.getInstance().player;
			if (last != null && player != null) {
				com.scd.client.feature.world.WorldGizmos.line(last, player.getPosition(partialTick).add(0, 0.1, 0), RouteRunner.PATH, walls);
			}
		}
	}

	/** The blocks under hand-placed nodes (not the step's start point). */
	private void markNodes(RouteStep s, boolean walls) {
		if (!s.manual) return;
		for (int i = 1; i < s.locations.size(); i++) {
			com.scd.client.feature.world.WorldGizmos.block(world(s.locations.get(i)).below(), 0x9060A5FA, walls);
		}
	}

	private BlockPos world(int[] rel) {
		return room.toWorld(new BlockPos(rel[0], rel[1], rel[2]));
	}

	int pendingPoints() {
		return step == null ? 0 : step.locations.size() + step.etherwarps.size() + step.mines.size() + step.interacts.size();
	}
}
