package com.scd.client.feature.bazaar;

import com.scd.client.core.ScdLog;
import com.scd.client.net.Backend;
import com.scd.client.net.MarketClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auction House estimates for items as they get hovered or listed. Lookups never block: an unknown
 * key is queued, and {@link #flush()} (every second) sends all queued keys in one
 * /api/auction/prices request. Results - including "no data" - are kept for 10 minutes.
 */
public final class AuctionPriceCache {
	private static final long TTL_MS = 10 * 60_000;
	private static final int BATCH = 200;

	private record Cached(Backend.AuctionPrice price, long at) {
	}

	private final MarketClient market;
	private final Map<String, Cached> cache = new ConcurrentHashMap<>();
	private final Set<String> queued = ConcurrentHashMap.newKeySet();
	private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

	AuctionPriceCache(MarketClient market) {
		this.market = market;
	}

	/** Cached estimate, or null (and queue a fetch) if not known yet. */
	public Backend.AuctionPrice get(String key) {
		if (key == null) return null;
		Cached c = cache.get(key);
		if (c != null && System.currentTimeMillis() - c.at() < TTL_MS) return c.price();
		if (!inFlight.contains(key)) queued.add(key);
		return c != null ? c.price() : null;
	}

	/** Queue several keys at once (e.g. the missing-accessories list). */
	public void prefetch(Iterable<String> keys) {
		for (String k : keys) get(k);
	}

	void flush() {
		if (queued.isEmpty() || !market.hasKey()) return;
		List<String> batch = new ArrayList<>();
		for (String k : queued) {
			if (batch.size() >= BATCH) break;
			batch.add(k);
		}
		queued.removeAll(batch);
		inFlight.addAll(batch);
		market.auctionPrices(batch).whenComplete((result, err) -> {
			long now = System.currentTimeMillis();
			if (err != null) {
				ScdLog.debug("Auction price batch failed: " + err.getMessage());
			} else {
				for (String k : batch) cache.put(k, new Cached(result.get(k), now));
			}
			inFlight.removeAll(batch);
		});
	}
}
