package com.scd.client.feature.bazaar;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.scd.client.Feature;
import com.scd.client.ScdMod;
import com.scd.client.core.Chat;
import com.scd.client.core.ScdLog;
import com.scd.client.core.Tasks;
import com.scd.client.hypixel.Items;
import com.scd.logic.Numbers;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Market prices (scd.wtf) on tooltips - Bazaar and Auction House - a hover price graph, and /scd price. */
public final class BazaarFeature implements Feature {
	private ScdMod mod;
	private PriceService prices;
	private AuctionPriceCache auctions;
	private ItemValuation valuation;
	private final HoverState hover = new HoverState();
	/** Display name -> item id resolved via Auction House search ("" = not found). */
	private final java.util.Map<String, String> nameIds = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<String> nameLookups = java.util.concurrent.ConcurrentHashMap.newKeySet();

	@Override
	public void init(ScdMod mod) {
		this.mod = mod;
		this.prices = new PriceService(mod);
		this.auctions = new AuctionPriceCache(mod.market);
		this.valuation = new ItemValuation(mod.market);
		mod.bus.subscribe(com.scd.client.core.Events.Tick.class, e -> valuation.pump());
		HistoryCache history = new HistoryCache(mod.market);

		int interval = Math.max(15, mod.config().market.bazaarRefreshSeconds) * 20;
		mod.tasks.every(interval, true, "bazaar refresh", prices::refreshPrices);
		mod.tasks.every(20, false, "auction price batch", auctions::flush);
		prices.refreshShards();
		mod.configManager.onChange(() -> {
			prices.refreshPrices();
			prices.refreshShards();
		});

		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) ->
				ScdLog.guard("bazaar tooltip", () -> onTooltip(stack, lines)));
		mod.huds.add(new PriceGraphHud(mod::config, prices, history, hover));
	}

	public PriceService prices() {
		return prices;
	}

	public ItemValuation valuation() {
		return valuation;
	}

	/**
	 * Best coin value of a concrete stack: Bazaar instant-sell for Bazaar items; for everything else
	 * the full valuation including add-ons when it has any, else the clean AH estimate / lowest BIN.
	 * Null while unknown (a lookup is queued).
	 */
	public Double valueOf(ItemStack stack) {
		String id = idOf(stack);
		if (id == null) return null;
		var p = prices.get(id);
		if (p != null && p.sellPrice() > 0) return p.sellPrice() * stack.getCount();
		if (ItemValuation.hasAddons(stack)) {
			var v = valuation.get(stack);
			if (v != null && v.estimatedValue() != null) return v.estimatedValue();
		}
		Double unit = valueOf(id);
		return unit != null ? unit * stack.getCount() : null;
	}

	public AuctionPriceCache auctions() {
		return auctions;
	}

	/** Best single coin value for an item id: Bazaar instant-sell, else AH estimate, else lowest BIN. Null if unknown. */
	public Double valueOf(String itemId) {
		var p = prices.get(itemId);
		if (p != null && p.sellPrice() > 0) return p.sellPrice();
		var a = auctions.get(itemId);
		if (a == null) return null;
		return a.price() != null ? a.price() : a.lbin();
	}

	/**
	 * Item id for a display name as shown in lore ("Necron's Handle", "Wither Essence",
	 * "Enchanted Book (Ultimate Wise V)"): fixed rules, then Bazaar names, then an Auction House
	 * search (asynchronous: null until it returns).
	 */
	public String idForName(String name) {
		String hint = com.scd.logic.dungeon.loot.ChestLoot.idHint(name);
		if (hint != null) return hint;
		String bz = prices.idByName(name);
		if (bz != null) return bz;
		String cached = nameIds.get(name);
		if (cached != null) return cached.isEmpty() ? null : cached;
		if (nameLookups.add(name)) {
			mod.market.auctionSearch(name, 5).whenComplete((list, err) -> {
				String id = "";
				if (list != null) {
					for (var k : list) {
						if (k.name() != null && k.name().equalsIgnoreCase(name)) {
							id = k.key() != null ? k.key() : k.itemId();
							break;
						}
					}
					if (id.isEmpty() && !list.isEmpty()) id = list.getFirst().key();
				}
				if (err == null) nameIds.put(name, id == null ? "" : id);
				nameLookups.remove(name);
			});
		}
		return null;
	}

	/** Coin value of {@code amount} of a named item; null while unknown. */
	public Double valueOfName(String name, int amount) {
		String id = idForName(name);
		if (id == null) return null;
		Double unit = valueOf(id);
		return unit != null ? unit * amount : null;
	}

	/** Bazaar instant-buy price (what it costs you), e.g. for Dungeon Chest Keys; null if unknown. */
	public Double buyPrice(String itemId) {
		var p = prices.get(itemId);
		return p != null && p.buyPrice() > 0 ? p.buyPrice() : null;
	}

	public HoverState hover() {
		return hover;
	}

	/** Bazaar product id for a stack, resolving enchanted books and attribute shards. */
	public String idOf(ItemStack stack) {
		return Items.skyblockId(stack, prices::shardFor);
	}

	private void onTooltip(ItemStack stack, List<Component> lines) {
		String id = idOf(stack);
		// Tooltips are also built outside menus (e.g. the held item when switching hotbar slots);
		// only a hover inside a container/inventory screen drives the price graph.
		if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
			hover.record(id, stack.getCount(), Items.rawCustomData(stack), Items.name(stack));
		}
		if (id == null || !mod.config().bazaar.tooltip) return;
		var p = prices.get(id);
		if (p == null) {
			if (mod.config().market.auctionTooltips) auctionTooltip(stack, id, lines);
			return;
		}
		lines.add(Component.literal("Bazaar").withStyle(ChatFormatting.DARK_GRAY));
		lines.add(Component.literal("  Insta-Sell: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(Numbers.coins(p.sellPrice()) + " coins").withStyle(ChatFormatting.GREEN)));
		lines.add(Component.literal("  Insta-Buy:  ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(Numbers.coins(p.buyPrice()) + " coins").withStyle(ChatFormatting.GOLD)));
		if (mod.config().bazaar.tooltipStackValue && stack.getCount() > 1) {
			lines.add(Component.literal("  Stack (x" + stack.getCount() + "): ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Numbers.coins(p.sellPrice() * stack.getCount()) + " coins").withStyle(ChatFormatting.GREEN)));
		}
		lines.add(Component.literal("  Spread: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(Numbers.percent(p.spreadPercent(), 1)).withStyle(ChatFormatting.DARK_GRAY)));
	}

	/**
	 * Auction House lines. For an item with add-ons (stars, scrolls, enchants, books, recomb, gems,
	 * reforge, pet level) the headline is the valuation of this exact item; the clean-item estimate
	 * and lowest BIN are shown underneath for comparison.
	 */
	private void auctionTooltip(ItemStack stack, String id, List<Component> lines) {
		int count = stack.getCount();
		var a = auctions.get(id);
		boolean addons = ItemValuation.hasAddons(stack);
		var full = addons ? valuation.get(stack) : null;
		boolean anything = (a != null && (a.price() != null || a.lbin() != null)) || addons;
		if (!anything) return;
		lines.add(Component.literal("Auction House").withStyle(ChatFormatting.DARK_GRAY));
		if (addons) {
			if (full == null) {
				lines.add(Component.literal("  Value: calculating...").withStyle(ChatFormatting.DARK_GRAY));
			} else if (full.estimatedValue() != null) {
				lines.add(Component.literal("  Value: ").withStyle(ChatFormatting.GRAY)
						.append(Component.literal(Numbers.coins(full.estimatedValue()) + " coins").withStyle(ChatFormatting.GOLD))
						.append(Component.literal(" (with add-ons)").withStyle(ChatFormatting.DARK_GRAY)));
				if (full.addonsValue() > 0) {
					lines.add(Component.literal("  Add-ons: ").withStyle(ChatFormatting.GRAY)
							.append(Component.literal("+" + Numbers.coins(full.addonsValue())).withStyle(ChatFormatting.AQUA)));
				}
			} else {
				lines.add(Component.literal("  Value: no sales data for this item").withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		if (a != null && a.price() != null) {
			String conf = a.confidence() != null ? " (" + a.confidence() + ")" : "";
			lines.add(Component.literal(addons ? "  Clean item: " : "  Estimate: ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Numbers.coins(a.price() * (addons ? 1 : count)) + " coins").withStyle(addons ? ChatFormatting.YELLOW : ChatFormatting.GOLD))
					.append(Component.literal(conf).withStyle(ChatFormatting.DARK_GRAY)));
		}
		if (a != null && a.lbin() != null) {
			lines.add(Component.literal("  Lowest BIN: ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Numbers.coins(a.lbin()) + " coins").withStyle(ChatFormatting.YELLOW)));
		}
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(ClientCommands.literal("price")
				.then(ClientCommands.argument("item", StringArgumentType.greedyString())
						.executes(ctx -> {
							priceLookup(StringArgumentType.getString(ctx, "item"));
							return 1;
						})));
	}

	/** /scd price: Bazaar matches first, then Auction House keys, all from the scd.wtf market API. */
	private void priceLookup(String query) {
		mod.market.bazaarSearch(query, 3).whenComplete((bz, err) -> Tasks.onClient(() -> {
			if (err != null) {
				Chat.error("Price lookup failed: " + err.getMessage());
				return;
			}
			for (var p : bz) {
				Chat.info(Component.literal("[BZ] ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.literal(p.name()).withStyle(ChatFormatting.WHITE))
						.append(Component.literal("  sell ").withStyle(ChatFormatting.GRAY))
						.append(Component.literal(Numbers.coins(p.sellPrice(), 2)).withStyle(ChatFormatting.GREEN))
						.append(Component.literal("  buy ").withStyle(ChatFormatting.GRAY))
						.append(Component.literal(Numbers.coins(p.buyPrice(), 2)).withStyle(ChatFormatting.GOLD)));
			}
			mod.market.auctionSearch(query, 3).whenComplete((keys, err2) -> {
				if (err2 != null || keys.isEmpty()) {
					if (bz.isEmpty()) Tasks.onClient(() -> Chat.info("Nothing on the Bazaar or Auction House matches \"" + query + "\"."));
					return;
				}
				List<String> ids = keys.stream().map(k -> k.key()).toList();
				mod.market.auctionPrices(ids).whenComplete((priced, err3) -> Tasks.onClient(() -> {
					for (var k : keys) {
						var a = err3 == null ? priced.get(k.key()) : null;
						String est = a != null && a.price() != null ? Numbers.coins(a.price()) : "?";
						String lbin = a != null && a.lbin() != null ? Numbers.coins(a.lbin()) : "-";
						Chat.info(Component.literal("[AH] ").withStyle(ChatFormatting.DARK_GRAY)
								.append(Component.literal(k.name() != null ? k.name() : k.key()).withStyle(ChatFormatting.WHITE))
								.append(Component.literal("  est ").withStyle(ChatFormatting.GRAY))
								.append(Component.literal(est).withStyle(ChatFormatting.GOLD))
								.append(Component.literal("  lbin ").withStyle(ChatFormatting.GRAY))
								.append(Component.literal(lbin).withStyle(ChatFormatting.YELLOW)));
					}
				}));
			});
		}));
	}
}
