package com.scd.client.net;

import java.util.List;

/** Data shapes returned by the SCD backend (server/src/routes/api.js). */
public final class Backend {
	private Backend() {
	}

	/** Enough to rebuild an item icon: a legacy material name (+ damage value), or a player-head skin. */
	public record Icon(String material, Integer durability, String skinValue, String skinSignature) {
	}

	public record Product(String itemId, String name, double buyPrice, double sellPrice, Icon icon) {
		/** Instant-buy vs instant-sell spread as a percentage of the buy price. */
		public double spreadPercent() {
			return buyPrice != 0 ? (buyPrice - sellPrice) / buyPrice * 100 : 0;
		}
	}

	/** One chart point: Bazaar candle closes for the instant-sell and instant-buy side. */
	public record HistoryPoint(long timestampMs, double sellPrice, double buyPrice) {
	}

	/** Result of valuing one concrete item (with its enchants, stars, books, ...). */
	public record ItemValue(Double estimatedValue, Double baseValue, double addonsValue, String key) {
	}

	public record CalendarEvent(String key, String name, long startSec, long endSec, boolean active) {
	}

	public record BazaarFlip(String product, String name, double buyOrderAt, double sellOfferAt, double marginPct,
			double hourlyVolume, double estHourlyProfit) {
	}

	/**
	 * Auction House estimate for a comparable key (usually the SkyBlock item id). price is the typical
	 * clean price, lbin the current lowest BIN; either may be null when there is no data.
	 */
	public record AuctionPrice(String key, Double price, Double lbin, String confidence, Double volumePerDay) {
	}

	public record AuctionKey(String key, String name, String itemId, int sales14d) {
	}

	public record MayorPerk(String name, String description) {
	}

	/** The elected mayor's perks plus the minister's single perk, if one is serving. */
	public record MayorInfo(String mayorName, List<MayorPerk> perks) {
		public static final MayorInfo NONE = new MayorInfo(null, List.of());
	}

	public record Accessory(String id, String name, String rarity, int magicalPower, int count) {
	}

	/**
	 * One accessory the player could still add. For an {@code upgrade} (a lower tier of the family
	 * is already owned) magicalPowerGain is what this purchase adds on top, so price / gain is
	 * always the real marginal coins-per-MP. price is null when neither market has data.
	 */
	public record MissingAccessory(String id, String name, String tier, String requirement, Double price, Icon icon,
			String obtainMethod, int magicalPowerGain, boolean upgrade) {
		public Double coinsPerPower() {
			return price != null && magicalPowerGain > 0 ? price / magicalPowerGain : null;
		}
	}

	/**
	 * accessoryPower is the backend's family-deduplicated live total; peakMagicalPower is Hypixel's
	 * lifetime best and can exceed it after swaps.
	 */
	public record AccessorySummary(String username, int accessoryCount, Integer accessoryPower, Integer peakMagicalPower,
			List<Accessory> accessories, List<MissingAccessory> missing) {
	}

	public record RoomBlock(int relX, int y, int relZ, String blockId) {
	}
}
