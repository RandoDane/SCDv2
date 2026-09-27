package com.scd.client.feature.accessory;

import com.scd.client.ScdMod;
import com.scd.client.core.ScdLog;
import com.scd.client.core.Tasks;
import com.scd.client.feature.bazaar.BazaarFeature;
import com.scd.client.hypixel.Players;
import com.scd.client.net.Backend;

import java.util.List;

/**
 * The player's accessory bag from the backend's profile lookup (owned + missing, grouped by upgrade
 * family). Prices are not taken from that response: every missing accessory is priced live from the
 * scd.wtf market (Bazaar instant-sell, else Auction House estimate / lowest BIN), falling back to
 * the backend's figure only when the market has nothing.
 */
public final class AccessoryService {
	public enum Status { IDLE, LOADING, LOADED, ERROR }

	private final ScdMod mod;
	private final BazaarFeature bazaar;
	private volatile Status status = Status.IDLE;
	private volatile Backend.AccessorySummary summary;
	private volatile String error;

	AccessoryService(ScdMod mod, BazaarFeature bazaar) {
		this.mod = mod;
		this.bazaar = bazaar;
	}

	/** Starts a fetch unless one is already running. */
	public void refresh() {
		if (status == Status.LOADING) return;
		status = Status.LOADING;
		String name = Players.selfName();
		mod.backend.accessories(name).whenComplete((result, err) -> Tasks.onClient(() -> {
			if (err != null) {
				Throwable c = err.getCause() != null ? err.getCause() : err;
				ScdLog.warn("Accessory fetch failed for " + name + ": " + c.getMessage());
				error = c.getMessage();
				status = Status.ERROR;
			} else {
				summary = result;
				status = Status.LOADED;
				bazaar.auctions().prefetch(result.missing().stream().map(Backend.MissingAccessory::id).toList());
			}
		}));
	}

	public Status status() {
		return status;
	}

	public Backend.AccessorySummary summary() {
		return summary;
	}

	public String error() {
		return error;
	}

	/** Market price for a missing accessory, or the backend's price if the market has none yet. */
	public Double price(Backend.MissingAccessory m) {
		Double market = bazaar.valueOf(m.id());
		return market != null ? market : m.price();
	}

	public Double coinsPerPower(Backend.MissingAccessory m) {
		Double p = price(m);
		return p != null && m.magicalPowerGain() > 0 ? p / m.magicalPowerGain() : null;
	}

	public List<Backend.MissingAccessory> missing() {
		return summary != null ? summary.missing() : List.of();
	}
}
