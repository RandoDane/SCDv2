package com.scd.client.feature.bazaar;

import com.scd.client.core.ScdLog;
import com.scd.client.net.Backend;
import com.scd.client.net.MarketClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bazaar mid-price candles per (product, range), from the scd.wtf market API. The graph asks every frame while hovering, so this returns
 * whatever is cached immediately and fetches at most once per key; entries expire after 10 minutes
 * and the least recently used are evicted past 32 keys (not a whole-cache clear like 1.x).
 */
final class HistoryCache {
	private static final long TTL_MS = 10 * 60_000;
	private static final int MAX = 32;

	private record Cached(List<Backend.HistoryPoint> points, long fetchedAt, boolean loading) {
	}

	private final MarketClient market;
	private final Map<String, Cached> cache = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
			return size() > MAX;
		}
	};

	HistoryCache(MarketClient market) {
		this.market = market;
	}

	synchronized List<Backend.HistoryPoint> get(String itemId, String range) {
		String key = itemId + "|" + range;
		Cached e = cache.get(key);
		long now = System.currentTimeMillis();
		if (e != null && (e.loading() || now - e.fetchedAt() < TTL_MS)) return e.points();
		List<Backend.HistoryPoint> stale = e != null ? e.points() : List.of();
		cache.put(key, new Cached(stale, now, true));
		market.bazaarHistory(itemId, range).whenComplete((points, err) -> {
			synchronized (this) {
				if (err != null) ScdLog.warn("History fetch failed for " + itemId + ": " + err.getMessage());
				cache.put(key, new Cached(err == null ? points : stale, System.currentTimeMillis(), false));
			}
		});
		return stale;
	}
}
