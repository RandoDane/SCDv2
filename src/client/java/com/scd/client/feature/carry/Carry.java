package com.scd.client.feature.carry;

import com.scd.client.feature.slayer.SlayerType;

/**
 * One carry-for-coins deal: a customer paid for N units (Slayer kills or dungeon runs) at an agreed
 * price per unit, with live progress. Reaching the target doesn't close it - closing (and asking
 * for a review) is a deliberate action, see {@link CarryService#finish}.
 */
public final class Carry {
	public enum Kind { SLAYER, DUNGEON }

	public enum Status { ACTIVE, COMPLETED }

	public long id;
	public Kind kind = Kind.SLAYER;
	public String customer;
	/** SLAYER only: SlayerType name and tier. */
	public String slayerType;
	public String tier;
	/** DUNGEON only: floor key, e.g. "F7", "M5". */
	public String floor;
	public long pricePerUnit;
	public int unitsOwed;
	public int unitsDone;
	public long totalTimeMs;
	public Status status = Status.ACTIVE;
	public long createdAt;
	public long completedAt;

	public boolean isActive() {
		return status == Status.ACTIVE;
	}

	public SlayerType type() {
		return slayerType != null ? SlayerType.valueOf(slayerType) : null;
	}

	/** "Revenant IV" / "F7". */
	public String target() {
		if (kind == Kind.DUNGEON) return floor;
		SlayerType t = type();
		return (t != null ? t.displayName() : "?") + " " + (tier != null ? tier : "");
	}

	public String unit() {
		return kind == Kind.DUNGEON ? "runs" : "kills";
	}

	public long totalPrice() {
		return pricePerUnit * unitsOwed;
	}

	public long earned() {
		return pricePerUnit * Math.min(unitsDone, unitsOwed);
	}

	public int remaining() {
		return Math.max(0, unitsOwed - unitsDone);
	}

	public long averageMs() {
		return unitsDone > 0 ? totalTimeMs / unitsDone : 0;
	}
}
