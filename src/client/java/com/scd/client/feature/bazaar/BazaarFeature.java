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
	private final HoverState hover = new HoverState();

	@Override
	public void init(ScdMod mod) {
		this.mod = mod;
		this.prices = new PriceService(mod);
		this.auctions = new AuctionPriceCache(mod.market);
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

	public HoverState hover() {
		return hover;
	}

	/** Bazaar product id for a stack, resolving enchanted books and attribute shards. */
	public String idOf(ItemStack stack) {
		return Items.skyblockId(stack, prices::shardFor);
	}

	private void onTooltip(ItemStack stack, List<Component> lines) {
		String id = idOf(stack);
		hover.record(id, stack.getCount(), Items.rawCustomData(stack), Items.name(stack));
		if (id == null || !mod.config().bazaar.tooltip) return;
		var p = prices.get(id);
		if (p == null) {
			if (mod.config().market.auctionTooltips) auctionTooltip(id, stack.getCount(), lines);
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

	private void auctionTooltip(String id, int count, List<Component> lines) {
		var a = auctions.get(id);
		if (a == null || (a.price() == null && a.lbin() == null)) return;
		lines.add(Component.literal("Auction House").withStyle(ChatFormatting.DARK_GRAY));
		if (a.price() != null) {
			String conf = a.confidence() != null ? " (" + a.confidence() + ")" : "";
			lines.add(Component.literal("  Estimate: ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Numbers.coins(a.price()) + " coins").withStyle(ChatFormatting.GOLD))
					.append(Component.literal(conf).withStyle(ChatFormatting.DARK_GRAY)));
		}
		if (a.lbin() != null) {
			lines.add(Component.literal("  Lowest BIN: ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Numbers.coins(a.lbin()) + " coins").withStyle(ChatFormatting.YELLOW)));
		}
		if (count > 1 && a.price() != null && mod.config().bazaar.tooltipStackValue) {
			lines.add(Component.literal("  Stack (x" + count + "): ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Numbers.coins(a.price() * count) + " coins").withStyle(ChatFormatting.GOLD)));
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
