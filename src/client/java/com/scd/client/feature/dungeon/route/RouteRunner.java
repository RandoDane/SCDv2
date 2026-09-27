package com.scd.client.feature.dungeon.route;

import com.scd.client.feature.dungeon.MappedRoom;
import com.scd.client.feature.world.WorldGizmos;
import com.scd.logic.dungeon.route.RouteStep;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Plays a route in the room you're in: draws the current step (path, etherwarp/mine/interact/TNT/
 * pearl spots and the secret) and advances when the step's secret is done - right-clicking it,
 * picking up its item, killing its bat nearby, or reaching an exit point.
 */
final class RouteRunner {
	static final int PATH = 0xFF4ADE80;
	static final int ETHERWARP = 0xFF38BDF8;
	static final int MINE = 0xFFF87171;
	static final int INTERACT = 0xFFFACC15;
	static final int TNT = 0xFFFB923C;
	static final int PEARL = 0xFFE879F9;
	static final int SECRET = 0xFF4ADE80;

	private MappedRoom room;
	private List<List<RouteStep>> routes = List.of();
	private int routeIndex;
	private int index;

	void set(MappedRoom room, List<List<RouteStep>> routes) {
		this.room = room;
		this.routes = routes;
		this.routeIndex = 0;
		this.index = 0;
	}

	void clear() {
		set(null, List.of());
	}

	MappedRoom room() {
		return room;
	}

	boolean active() {
		return room != null && room.anchor() != null && !routes.isEmpty();
	}

	List<RouteStep> steps() {
		return routes.isEmpty() ? List.of() : routes.get(routeIndex);
	}

	int index() {
		return index;
	}

	int routeCount() {
		return routes.size();
	}

	int routeIndex() {
		return routeIndex;
	}

	RouteStep current() {
		List<RouteStep> s = steps();
		return index < s.size() ? s.get(index) : null;
	}

	void next() {
		if (index < steps().size()) index++;
	}

	void previous() {
		if (index > 0) index--;
	}

	void reset() {
		index = 0;
	}

	void cycleRoute() {
		if (routes.size() > 1) {
			routeIndex = (routeIndex + 1) % routes.size();
			index = 0;
		}
	}

	BlockPos world(int[] rel) {
		return room.toWorld(new BlockPos(rel[0], rel[1], rel[2]));
	}

	// --- advancing -------------------------------------------------------------------------

	void onInteract(BlockPos pos) {
		RouteStep s = current();
		if (!active() || s == null || s.secret == null || s.secretType != RouteStep.SecretType.INTERACT) return;
		if (world(s.secret).equals(pos)) next();
	}

	void onItemPickup(Vec3 pos) {
		RouteStep s = current();
		if (!active() || s == null || s.secret == null || s.secretType != RouteStep.SecretType.ITEM) return;
		if (Vec3.atCenterOf(world(s.secret)).distanceToSqr(pos) < 5 * 5) next();
	}

	void onBatDeath(Vec3 pos) {
		RouteStep s = current();
		if (!active() || s == null || s.secret == null || s.secretType != RouteStep.SecretType.BAT) return;
		if (Vec3.atCenterOf(world(s.secret)).distanceToSqr(pos) < 16 * 16) next();
	}

	void tick(Vec3 player) {
		RouteStep s = current();
		if (!active() || s == null) return;
		if (s.secretType == RouteStep.SecretType.EXIT || s.secretType == RouteStep.SecretType.EXIT_ROUTE) {
			int[] target = s.secret != null ? s.secret : s.locations.isEmpty() ? null : s.locations.getLast();
			if (target != null && Vec3.atBottomCenterOf(world(target)).distanceToSqr(player) < 2.0 * 2.0) next();
		}
	}

	// --- drawing ---------------------------------------------------------------------------

	void render(boolean throughWalls, boolean showNext) {
		if (!active()) return;
		var mc = Minecraft.getInstance();
		if (mc.player == null) return;
		RouteStep s = current();
		if (s == null) return;
		draw(s, index, throughWalls, 0xFF, mc.player.position());
		if (showNext && index + 1 < steps().size()) draw(steps().get(index + 1), index + 1, throughWalls, 0x70, null);
	}

	private void draw(RouteStep s, int n, boolean walls, int alpha, Vec3 from) {
		Vec3 prev = from != null ? from.add(0, 0.1, 0) : null;
		for (int[] p : s.locations) {
			Vec3 v = Vec3.atBottomCenterOf(world(p)).add(0, 0.1, 0);
			if (prev != null) WorldGizmos.line(prev, v, fade(PATH, alpha), walls);
			prev = v;
		}
		for (int[] p : s.etherwarps) WorldGizmos.block(world(p), fade(ETHERWARP, alpha), walls);
		for (int[] p : s.mines) WorldGizmos.block(world(p), fade(MINE, alpha), walls);
		for (int[] p : s.interacts) WorldGizmos.block(world(p), fade(INTERACT, alpha), walls);
		for (int[] p : s.tnts) WorldGizmos.block(world(p), fade(TNT, alpha), walls);
		for (int[] p : s.pearls) WorldGizmos.block(world(p), fade(PEARL, alpha), walls);
		if (s.secret != null) {
			BlockPos sp = world(s.secret);
			WorldGizmos.block(sp, fade(SECRET, alpha), walls);
			if (alpha == 0xFF) {
				String label = (n + 1) + "/" + steps().size() + " " + s.secretType.key.toLowerCase(Locale.ROOT);
				WorldGizmos.label(Vec3.atCenterOf(sp).add(0, 1.0, 0), label, SECRET, walls);
			}
		}
	}

	private static int fade(int argb, int alpha) {
		return (argb & 0x00FFFFFF) | (alpha << 24);
	}
}
