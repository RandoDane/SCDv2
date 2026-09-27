package com.scd.logic.dungeon.room;

import java.util.function.IntFunction;

/**
 * Identifies a room from a single block column: the column at the centre of a 32x32 tile, read
 * top-down from y=140 to y=12, hashed. Every room has a distinct column (per tile), and rotations
 * don't change it because the centre column doesn't move. This is Odin's scheme, reproduced
 * exactly so its room database (Odin rooms.json, BSD-3) can be reused:
 * <ul>
 *     <li>air and gold blocks above the roof become '0' (gold: Hypixel's decorative roof markers)</li>
 *     <li>every block after that appends {@code Block.toString()} = {@code "Block{" + id + "}"},
 *     except oak planks and chests (they change when doors open / chests are looted)</li>
 *     <li>after two bedrock blocks, the first air below y=69 pads the rest with '0' and stops</li>
 * </ul>
 * The hash is {@code String.hashCode()} of the result. A column with no blocks (unloaded or
 * outside the dungeon) hashes to {@link #EMPTY}.
 */
public final class RoomCore {
	public static final int TOP = 140;
	public static final int BOTTOM = 12;
	/** Hash of an all-air column: 129 zeros. */
	public static final int EMPTY = -318865360;

	public record Result(int core, int highestBlock) {
		public boolean empty() {
			return core == EMPTY;
		}
	}

	private RoomCore() {
	}

	/**
	 * @param blockAt registry id of the block at y ("minecraft:stone", "minecraft:air"); null counts as air
	 */
	public static Result compute(IntFunction<String> blockAt) {
		StringBuilder sb = new StringBuilder(2048);
		boolean foundHighest = false;
		int highest = 0;
		int bedrock = 0;
		for (int y = TOP; y >= BOTTOM; y--) {
			String id = blockAt.apply(y);
			if (id == null) id = "minecraft:air";
			boolean air = isAir(id);
			if (!foundHighest) {
				if (!air && !id.equals("minecraft:gold_block")) {
					foundHighest = true;
					highest = y;
				} else {
					sb.append('0');
				}
			}
			if (foundHighest) {
				if (air && bedrock >= 2 && y < 69) {
					sb.append("0".repeat(y - 11));
					break;
				}
				if ("minecraft:bedrock".equals(id)) {
					bedrock++;
				} else {
					bedrock = 0;
					if ("minecraft:oak_planks".equals(id) || "minecraft:trapped_chest".equals(id) || "minecraft:chest".equals(id)) continue;
				}
				sb.append("Block{").append(id).append('}');
			}
		}
		return new Result(sb.toString().hashCode(), highest);
	}

	private static boolean isAir(String id) {
		return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
	}
}
