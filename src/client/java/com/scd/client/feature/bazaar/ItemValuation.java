package com.scd.client.feature.bazaar;

import com.scd.client.core.ScdLog;
import com.scd.client.net.Backend;
import com.scd.client.net.MarketClient;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Value of a specific item including everything on it (stars, scrolls, enchants, books,
 * recombobulator, gems, reforge, pet level...), via scd.wtf's /auction/value. Non-blocking: an
 * unknown item returns null and is queued; results are cached per item (its SkyBlock uuid, else a
 * hash of its data) for 10 minutes. At most 4 requests run at once.
 */
public final class ItemValuation {
	private static final long TTL_MS = 10 * 60_000;
	private static final int MAX_IN_FLIGHT = 4;
	/** Custom-data keys that never change value - an item with only these is priced as its clean key. */
	private static final Set<String> NEUTRAL = Set.of("id", "uuid", "timestamp", "originTag", "donated_museum", "tier", "boss_tier");

	private record Cached(Backend.ItemValue value, long at) {
	}

	private record Job(String key, String bytes) {
	}

	private final MarketClient market;
	private final Map<String, Cached> cache = new ConcurrentHashMap<>();
	private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
	private final Deque<Job> queue = new ArrayDeque<>();

	ItemValuation(MarketClient market) {
		this.market = market;
	}

	/** True when the item carries anything beyond its identity, i.e. its clean price would be wrong. */
	public static boolean hasAddons(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return false;
		CompoundTag tag = data.copyTag();
		for (String k : tag.keySet()) if (!NEUTRAL.contains(k)) return true;
		return false;
	}

	/** Full valuation if known (null while pending - it gets queued). */
	public Backend.ItemValue get(ItemStack stack) {
		String key = cacheKey(stack);
		if (key == null) return null;
		Cached c = cache.get(key);
		if (c != null && System.currentTimeMillis() - c.at() < TTL_MS) return c.value();
		if (!inFlight.contains(key)) {
			String bytes = ItemBytes.encode(stack);
			if (bytes != null) {
				synchronized (queue) {
					if (queue.stream().noneMatch(j -> j.key().equals(key))) queue.addLast(new Job(key, bytes));
					while (queue.size() > 64) queue.removeFirst(); // only what's on screen matters
				}
			}
		}
		return c != null ? c.value() : null;
	}

	/** Called every tick: starts queued requests up to the concurrency limit. */
	void pump() {
		if (!market.hasKey()) return;
		while (inFlight.size() < MAX_IN_FLIGHT) {
			Job job;
			synchronized (queue) {
				job = queue.pollLast(); // newest first - the item the player is looking at now
			}
			if (job == null) return;
			if (!inFlight.add(job.key())) continue;
			market.itemValue(job.bytes()).whenComplete((v, err) -> {
				if (err != null) ScdLog.debug("Item valuation failed: " + err.getMessage());
				else cache.put(job.key(), new Cached(v, System.currentTimeMillis()));
				inFlight.remove(job.key());
			});
		}
	}

	private static String cacheKey(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		CompoundTag tag = data.copyTag();
		String uuid = tag.getString("uuid").orElse(null);
		// Same uuid can be re-rolled (new stars/enchants), so the data hash is part of the key too.
		return (uuid != null ? uuid : "") + "#" + tag.toString().hashCode() + "x" + stack.getCount();
	}
}
