package com.scd.client.feature.dungeon.route;

import com.scd.client.feature.dungeon.MappedRoom;
import com.scd.client.feature.world.WorldGizmos;
import com.scd.logic.dungeon.route.RoutePack;
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
	// SecretRoutes' default colours: {box, label, muted box for the step after the current one}.
	static final int[] ETHERWARP = {0xFF800080, 0xFFAA00AA, 0xFF5F3D61};
	static final int[] MINE = {0xFFFFEC00, 0xFFFFFF55, 0xFFB1AD61};
	static final int[] TNT = {0xFFFF0000, 0xFFFF5555, 0xFFA85A5A};
	static final int[] INTERACT = {0xFF0000FF, 0xFF5555FF, 0xFF495295};
	static final int[] PEARL = {0xFF00FFFF, 0xFF55FFFF, 0xFF5FA7A7};
	static final int[] ITEM = {0xFF00FFFF, 0xFF55FF55, 0xFF5FA7A7};
	static final int[] BAT = {0xFF00FF00, 0xFF55FF55, 0xFF5B9A5B};
	static final int[] EXIT = {0xFFFF0000, 0xFFFF5555, 0xFFA85A5A};
	static final int START = 0xFFFF5555;
	static final int DONE = 0xFF94A3B8;

	private MappedRoom room;
	private List<RoutePack.Route> routes = List.of();
	private int routeIndex;
	private int index;
	private long lastAdvance;
	private double smoothing = 1.5;
	private final java.util.Map<RouteStep, List<Vec3>> pathCache = new java.util.IdentityHashMap<>();
	private boolean entryPicked;

	/**
	 * @param entry room-relative {x, z} where the player came in (null if unknown): the route that
	 *              starts closest to it is picked
	 */
	void set(MappedRoom room, List<RoutePack.Route> routes, int[] entry) {
		this.room = room;
		this.routes = routes;
		this.routeIndex = pick(routes, entry);
		this.pathCache.clear();
		this.entryPicked = entry != null && routes.size() > 1;
		this.index = 0;
	}

	static int pick(List<RoutePack.Route> routes, int[] entry) {
		if (entry == null) return 0;
		int best = 0;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < routes.size(); i++) {
			int[] e = RoutePack.start(routes.get(i).steps());
			if (e == null) continue;
			double d = Math.hypot(e[0] - entry[0], e[1] - entry[1]);
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	void clear() {
		set(null, List.of(), null);
	}

	MappedRoom room() {
		return room;
	}

	boolean active() {
		return room != null && room.anchor() != null && !routes.isEmpty();
	}

	List<RouteStep> steps() {
		return routes.isEmpty() ? List.of() : routes.get(routeIndex).steps();
	}

	String key() {
		return routes.isEmpty() ? null : routes.get(routeIndex).key();
	}

	/** Whether the playing route was chosen because it starts nearest your entrance. */
	boolean entryMatched() {
		return entryPicked;
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
		lastAdvance = System.currentTimeMillis();
	}

	/**
	 * The room's secret counter (action bar) went up: the current secret is done, whatever it was.
	 * Ignored right after a precise advance (chest click, item, bat) for the same secret.
	 */
	void onSecretCounted() {
		if (!active() || current() == null) return;
		if (System.currentTimeMillis() - lastAdvance < 1500) return;
		RouteStep s = current();
		if (s.secretType == RouteStep.SecretType.EXIT || s.secretType == RouteStep.SecretType.EXIT_ROUTE) return;
		next();
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

	/**
	 * World points of a leg's path, straightened to within {@code tolerance} blocks (see
	 * PathSimplifier) - the recording keeps every sample, only the drawing is simplified.
	 */
	static List<Vec3> path(MappedRoom room, List<int[]> locations, double tolerance) {
		List<double[]> pts = new java.util.ArrayList<>(locations.size());
		for (int[] p : locations) {
			BlockPos w = room.toWorld(new BlockPos(p[0], p[1], p[2]));
			pts.add(new double[]{w.getX() + 0.5, w.getY() + 0.1, w.getZ() + 0.5});
		}
		List<Vec3> out = new java.util.ArrayList<>();
		for (double[] d : com.scd.logic.dungeon.route.PathSimplifier.simplify(pts, tolerance)) out.add(new Vec3(d[0], d[1], d[2]));
		return out;
	}

	private List<Vec3> cachedPath(RouteStep s) {
		return pathCache.computeIfAbsent(s, k -> path(room, k.locations, k.manual ? 0 : smoothing));
	}

	static void polyline(Vec3 from, List<Vec3> pts, int color, boolean walls) {
		Vec3 prev = from;
		for (Vec3 v : pts) {
			if (prev != null) WorldGizmos.line(prev, v, color, walls);
			prev = v;
		}
	}

	void render(boolean throughWalls, boolean showNext, float partialTick, double smoothing) {
		if (smoothing != this.smoothing) {
			this.smoothing = smoothing;
			pathCache.clear();
		}
		if (!active()) return;
		var mc = Minecraft.getInstance();
		if (mc.player == null) return;
		drawStart(throughWalls);
		drawTaken(throughWalls);
		drawWholePath(throughWalls);
		RouteStep s = current();
		if (s == null) return;
		// Interpolated position: the per-tick one makes the line's start jump behind the smooth camera.
		draw(s, index, throughWalls, 0xFF, mc.player.getPosition(partialTick));
		if (showNext && index + 1 < steps().size()) draw(steps().get(index + 1), index + 1, throughWalls, 0x70, null);
	}

	/** "Start" on the block the route begins on top of, shown until the first secret is done. */
	private void drawStart(boolean walls) {
		List<RouteStep> all = steps();
		if (index > 0 || all.isEmpty() || all.getFirst().locations.isEmpty()) return;
		markStart(world(all.getFirst().locations.getFirst()), walls);
	}

	/** The entire route as a faint line, so the current leg is seen in context. */
	private void drawWholePath(boolean walls) {
		Vec3 last = null;
		for (RouteStep s : steps()) {
			List<Vec3> pts = cachedPath(s);
			polyline(last, pts, fade(PATH, 0x55), walls);
			if (!pts.isEmpty()) last = pts.getLast();
		}
	}

	/** Secrets already taken stay marked (muted, with a tick) so you can see what's done. */
	private void drawTaken(boolean walls) {
		List<RouteStep> all = steps();
		for (int i = 0; i < index && i < all.size(); i++) {
			RouteStep s = all.get(i);
			if (s.secret == null || s.secretType == RouteStep.SecretType.EXIT || s.secretType == RouteStep.SecretType.EXIT_ROUTE) continue;
			markSecret(world(s.secret), "✔ " + (i + 1) + " " + secretName(room, s), DONE, walls);
		}
	}

	/** @param feet the block the player stood in; the marked block is the one below it */
	static void markStart(BlockPos feet, boolean walls) {
		BlockPos ground = feet.below();
		WorldGizmos.block(ground, START, walls);
		WorldGizmos.label(Vec3.atCenterOf(ground).add(0, 1.3, 0), "Start", START, walls);
	}

	static void markSecret(BlockPos pos, String label, int color, boolean walls) {
		WorldGizmos.block(pos, color, walls);
		WorldGizmos.label(Vec3.atCenterOf(pos).add(0, 1.0, 0), label, color, walls);
	}

	private void draw(RouteStep s, int n, boolean walls, int alpha, Vec3 from) {
		polyline(from != null ? from.add(0, 0.1, 0) : null, cachedPath(s), fade(PATH, alpha), walls);
		drawActions(room, s, alpha, alpha == 0xFF, walls);
		if (s.secret != null) {
			BlockPos sp = world(s.secret);
			int[] c = secretColors(s);
			WorldGizmos.block(sp, alpha == 0xFF ? c[0] : c[2], walls);
			if (alpha == 0xFF) {
				String label = (n + 1) + "/" + steps().size() + " " + secretName(room, s);
				WorldGizmos.label(Vec3.atCenterOf(sp).add(0, 1.0, 0), label, c[1], walls);
			}
		}
	}

	/**
	 * Every action of a step as a coloured block, labelled with what to do there: "Etherwarp" on the
	 * block to warp to, "Ender pearl" where the pearl lands (throw spots say "Throw pearl"), one
	 * "Stonk" per group of blocks to break through, "Click" for levers/doors, "Superboom" for TNT.
	 */
	static void drawActions(MappedRoom room, RouteStep s, int alpha, boolean labels, boolean walls) {
		boolean next = alpha != 0xFF;
		for (int[] p : s.etherwarps) action(room, p, ETHERWARP, next, labels ? "Etherwarp" : null, walls);
		for (int[] p : s.pearls) action(room, p, PEARL, next, labels && s.pearlLandings.isEmpty() ? "Ender pearl" : labels ? "Throw pearl" : null, walls);
		for (int[] p : s.pearlLandings) action(room, p, PEARL, next, labels ? "Ender pearl" : null, walls);
		for (int[] p : s.interacts) action(room, p, INTERACT, next, labels ? "Click" : null, walls);
		for (int[] p : s.tnts) action(room, p, TNT, next, labels ? "Superboom" : null, walls);
		for (int[] p : s.mines) action(room, p, MINE, next, null, walls);
		if (labels) for (List<int[]> group : groups(s.mines)) {
			// Label the top of each wall you stonk through, once.
			int[] top = group.getFirst();
			double cx = 0, cz = 0;
			for (int[] p : group) {
				if (p[1] > top[1]) top = p;
				cx += p[0];
				cz += p[2];
			}
			BlockPos w = room.toWorld(new BlockPos((int) Math.round(cx / group.size()), top[1], (int) Math.round(cz / group.size())));
			WorldGizmos.label(Vec3.atCenterOf(w).add(0, 1.0, 0), "Stonk", MINE[1], walls);
		}
	}

	private static void action(MappedRoom room, int[] p, int[] colors, boolean next, String label, boolean walls) {
		BlockPos w = room.toWorld(new BlockPos(p[0], p[1], p[2]));
		WorldGizmos.block(w, next ? colors[2] : colors[0], walls);
		if (label != null) WorldGizmos.label(Vec3.atCenterOf(w).add(0, 1.0, 0), label, colors[1], walls);
	}

	/** Mined blocks that touch (including diagonally) form one group. */
	static List<List<int[]>> groups(List<int[]> blocks) {
		List<List<int[]>> out = new java.util.ArrayList<>();
		boolean[] seen = new boolean[blocks.size()];
		for (int i = 0; i < blocks.size(); i++) {
			if (seen[i]) continue;
			List<int[]> g = new java.util.ArrayList<>();
			java.util.ArrayDeque<Integer> todo = new java.util.ArrayDeque<>(List.of(i));
			seen[i] = true;
			while (!todo.isEmpty()) {
				int[] a = blocks.get(todo.poll());
				g.add(a);
				for (int j = 0; j < blocks.size(); j++) {
					int[] b = blocks.get(j);
					if (!seen[j] && Math.abs(a[0] - b[0]) <= 1 && Math.abs(a[1] - b[1]) <= 1 && Math.abs(a[2] - b[2]) <= 1) {
						seen[j] = true;
						todo.add(j);
					}
				}
			}
			out.add(g);
		}
		return out;
	}

	/** SecretRoutes' colours for a secret: interact blue, item cyan (green text), bat green, exit red. */
	static int[] secretColors(RouteStep s) {
		return switch (s.secretType) {
			case ITEM -> ITEM;
			case BAT -> BAT;
			case EXIT, EXIT_ROUTE -> EXIT;
			case INTERACT -> INTERACT;
		};
	}

	/** What the secret is: Chest, Wither essence, Lever, Item, Bat, or Exit/Waypoint. */
	static String secretName(MappedRoom room, RouteStep s) {
		return switch (s.secretType) {
			case ITEM -> "Item";
			case BAT -> "Bat";
			case EXIT -> "Waypoint";
			case EXIT_ROUTE -> "Exit";
			case INTERACT -> {
				var level = Minecraft.getInstance().level;
				if (level == null || s.secret == null) yield "Secret";
				var block = level.getBlockState(room.toWorld(new BlockPos(s.secret[0], s.secret[1], s.secret[2]))).getBlock();
				if (block == net.minecraft.world.level.block.Blocks.CHEST || block == net.minecraft.world.level.block.Blocks.TRAPPED_CHEST) yield "Chest";
				if (block == net.minecraft.world.level.block.Blocks.LEVER) yield "Lever";
				if (block instanceof net.minecraft.world.level.block.AbstractSkullBlock) yield "Wither essence";
				yield "Secret";
			}
		};
	}

	private static int fade(int argb, int alpha) {
		return (argb & 0x00FFFFFF) | (alpha << 24);
	}
}
