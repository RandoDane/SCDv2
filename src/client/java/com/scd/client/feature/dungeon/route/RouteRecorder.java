package com.scd.client.feature.dungeon.route;

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
 */
final class RouteRecorder {
	private static final double SAMPLE = 2.4;
	private static final double WARP_JUMP = 4.5;

	private MappedRoom room;
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
		if (!force && lastSample != null && lastSample.distanceToSqr(player) < SAMPLE * SAMPLE) return;
		step.locations.add(rel(BlockPos.containing(player)));
		lastSample = player;
	}

	void onInteract(BlockPos pos, Block block, boolean holdingTnt, Vec3 player) {
		if (!active() || room.anchor() == null) return;
		if (holdingTnt) {
			step.tnts.add(rel(pos));
		} else if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.PLAYER_HEAD || block == Blocks.PLAYER_WALL_HEAD
				|| block == Blocks.SKELETON_SKULL || block == Blocks.SKELETON_WALL_SKULL) {
			finish(RouteStep.SecretType.INTERACT, rel(pos), player);
		} else {
			step.interacts.add(rel(pos));
		}
	}

	void onItemPickup(Vec3 itemPos, Vec3 player) {
		if (!active() || room.anchor() == null || itemPos.distanceToSqr(player) > 6 * 6) return;
		finish(RouteStep.SecretType.ITEM, rel(BlockPos.containing(itemPos)), player);
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
		sample(player, true);
		step.secretType = type;
		step.secret = secret;
		steps.add(step);
		step = new RouteStep();
		lastSample = null;
		sample(player, true);
	}

	private int[] rel(BlockPos world) {
		BlockPos r = room.toRelative(world);
		return new int[]{r.getX(), r.getY(), r.getZ()};
	}

	int pendingPoints() {
		return step == null ? 0 : step.locations.size() + step.etherwarps.size() + step.mines.size() + step.interacts.size();
	}
}
