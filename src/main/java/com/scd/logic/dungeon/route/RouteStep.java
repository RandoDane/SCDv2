package com.scd.logic.dungeon.route;

import java.util.ArrayList;
import java.util.List;

/**
 * One leg of a room route: how to get from the previous secret to the next one. All positions are
 * room-relative blocks {x, y, z} (see RoomRotation). Mirrors one element of a SecretRoutes route.
 */
public final class RouteStep {
	public enum SecretType {
		/** Right-click a block (chest, lever, wither essence skull). */
		INTERACT("interact"),
		/** Pick up an item lying at the location. */
		ITEM("item"),
		/** Kill the bat that spawns near the location. */
		BAT("bat"),
		/** No secret: just walk to the location (leaving the room, or a waypoint). */
		EXIT("exit"),
		/** SecretRoutes' "exitroute": an explicit way out after the last secret. */
		EXIT_ROUTE("exitroute");

		public final String key;

		SecretType(String key) {
			this.key = key;
		}

		public static SecretType fromKey(String key) {
			for (SecretType t : values()) if (t.key.equalsIgnoreCase(key)) return t;
			return EXIT;
		}
	}

	/** Path to walk, in order. */
	public final List<int[]> locations = new ArrayList<>();
	/** Blocks to etherwarp onto. */
	public final List<int[]> etherwarps = new ArrayList<>();
	/** Blocks to break (stonk / pickaxe). */
	public final List<int[]> mines = new ArrayList<>();
	/** Blocks to right-click on the way (levers, doors) that aren't the secret itself. */
	public final List<int[]> interacts = new ArrayList<>();
	/** Where to place superboom TNT. */
	public final List<int[]> tnts = new ArrayList<>();
	/** Ender pearl throw spots, with {yaw, pitch} per pearl in {@link #pearlAngles}. */
	public final List<int[]> pearls = new ArrayList<>();
	public final List<float[]> pearlAngles = new ArrayList<>();
	public SecretType secretType = SecretType.EXIT;
	public int[] secret;

	public boolean isEmpty() {
		return locations.isEmpty() && etherwarps.isEmpty() && mines.isEmpty() && interacts.isEmpty() && tnts.isEmpty()
				&& pearls.isEmpty() && secret == null;
	}
}
