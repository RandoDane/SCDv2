package com.scd.client.feature.bazaar;

import com.scd.client.ScdMod;
import com.scd.client.core.ScdLog;
import com.scd.client.net.Backend;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local mirror of every Bazaar product (from the scd.wtf market API) plus the attribute-shard id
 * table (from the SCD backend - identity data, not a price), refreshed in the background.
 */
public final class PriceService {
	private final ScdMod mod;
	private final Map<String, Backend.Product> products = new ConcurrentHashMap<>();
	private final Map<String, String> shards = new ConcurrentHashMap<>();
	private volatile long lastRefreshMs;
	private volatile boolean loggedFirst;

	PriceService(ScdMod mod) {
		this.mod = mod;
	}

	void refreshPrices() {
		mod.market.bazaarProducts().thenAccept(list -> {
			replace(list);
			lastRefreshMs = System.currentTimeMillis();
			if (!loggedFirst) {
				loggedFirst = true;
				ScdLog.info("Bazaar prices loaded: " + list.size() + " products from scd.wtf");
			}
		}).exceptionally(err -> {
			ScdLog.warn("Bazaar price refresh failed: " + err.getMessage());
			return null;
		});
	}

	void refreshShards() {
		mod.backend.attributeShards().thenAccept(map -> {
			shards.clear();
			shards.putAll(map);
		}).exceptionally(err -> {
			ScdLog.warn("Attribute shard table fetch failed: " + err.getMessage());
			return null;
		});
	}

	private void replace(List<Backend.Product> list) {
		Map<String, Backend.Product> next = new ConcurrentHashMap<>();
		for (var p : list) next.put(p.itemId(), p);
		products.keySet().retainAll(next.keySet());
		products.putAll(next);
	}

	public Backend.Product get(String itemId) {
		return itemId == null ? null : products.get(itemId);
	}

	public String shardFor(String attributeKey) {
		return shards.get(attributeKey);
	}

	public int size() {
		return products.size();
	}

	public long lastRefreshMs() {
		return lastRefreshMs;
	}
}
