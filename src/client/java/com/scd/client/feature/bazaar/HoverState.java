package com.scd.client.feature.bazaar;

/**
 * The SkyBlock item whose tooltip is currently showing. The tooltip callback only fires while a
 * tooltip renders, so "seen within the last 250ms" means "hovered right now" - that drives the
 * graph HUD's visibility. The raw NBT is kept indefinitely for /scd debug item.
 */
public final class HoverState {
	private static final long FRESH_MS = 250;

	private volatile String itemId;
	private volatile int count;
	private volatile long seenAt;
	private volatile String lastRawNbt;
	private volatile String lastName;

	void record(String itemId, int count, String rawNbt, String name) {
		this.itemId = itemId;
		this.count = count;
		this.seenAt = System.currentTimeMillis();
		this.lastRawNbt = rawNbt;
		this.lastName = name;
	}

	public String currentOrNull() {
		return itemId != null && System.currentTimeMillis() - seenAt <= FRESH_MS ? itemId : null;
	}

	public int currentCount() {
		return count;
	}

	public String lastRawNbt() {
		return lastRawNbt;
	}

	public String lastName() {
		return lastName;
	}
}
