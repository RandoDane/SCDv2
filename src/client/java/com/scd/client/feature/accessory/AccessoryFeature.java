package com.scd.client.feature.accessory;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.scd.client.Feature;
import com.scd.client.ScdMod;
import com.scd.client.core.Chat;
import com.scd.client.core.Events;
import com.scd.client.core.ScdLog;
import com.scd.client.feature.bazaar.BazaarFeature;
import com.scd.client.ui.ScdScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import java.util.Comparator;

/** Accessory bag: owned/missing viewer, live bag scan with exact Accessory Power, and the missing-accessories overlay. */
public final class AccessoryFeature implements Feature {
	private ScdMod mod;
	private AccessoryService service;
	private final BagScanner scanner = new BagScanner();
	private BagOverlay overlay;
	/** True while bag pages have been open back-to-back - Hypixel sends a new Screen per page turn. */
	private boolean bagSession;

	@Override
	public void init(ScdMod mod) {
		this.mod = mod;
		this.service = new AccessoryService(mod, mod.feature(BazaarFeature.class));
		this.overlay = new BagOverlay(service, scanner, mod::config);
		mod.bus.subscribe(Events.ScreenOpened.class, e -> onScreen(e.screen()));
		mod.bus.subscribe(Events.ScreenTick.class, e -> {
			if (BagScanner.isBag(e.screen())) scanner.scan(e.screen());
		});
	}

	private void onScreen(net.minecraft.client.gui.screens.Screen screen) {
		if (!BagScanner.isBag(screen)) {
			bagSession = false;
			return;
		}
		if (!bagSession) {
			// Fresh visit: rescan from scratch and refetch - the profile may have changed since.
			scanner.reset();
			overlay.resetPage();
			if (mod.config().accessories.bagOverlay) service.refresh();
		}
		bagSession = true;
		if (!mod.config().accessories.bagOverlay) return;
		ScreenEvents.afterExtract(screen).register((s, g, mx, my, pt) ->
				ScdLog.guard("accessory overlay", () -> overlay.render(g, mx, my, pt, s.height)));
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
				ScdLog.guard("accessory overlay click", () -> !overlay.click(event), true));
	}

	public AccessoryService service() {
		return service;
	}

	BagScanner scanner() {
		return scanner;
	}

	@Override
	public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
		root.then(ClientCommands.literal("accessories")
				.executes(ctx -> {
					ScdScreen.open(new AccessoryScreen(new com.scd.client.ui.clickgui.ClickGuiScreen(mod), mod, this));
					return 1;
				})
				.then(ClientCommands.literal("scan").executes(ctx -> {
					Chat.info("Bag scan: " + scanner.count() + " accessories, " + scanner.pagesSeen() + "/" + scanner.totalPages()
							+ " pages, " + scanner.totalPower() + " Accessory Power (details in log)");
					scanner.items().stream().sorted(Comparator.comparingInt(BagScanner.Scanned::power).reversed())
							.forEach(i -> ScdLog.info("  " + i.power() + " " + i.name() + " [" + i.rarity() + "]"));
					return 1;
				})));
	}
}
