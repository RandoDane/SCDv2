package com.scd.client.screen;

import com.scd.client.ScdMod;
import com.scd.client.config.ScdConfig;
import com.scd.client.core.Tasks;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.Ui;
import com.scd.client.ui.widget.TextField;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** scd.wtf key and every price-related option. The key is never shown once saved - only its last 4 characters. */
public final class MarketScreen extends ScdScreen {
	private final ScdMod mod;
	private String newKey = "";
	private String testResult;

	public MarketScreen(Screen parent, ScdMod mod) {
		super("Market & Bazaar", parent);
		this.mod = mod;
	}

	@Override
	protected void onClosing() {
		if (!newKey.isBlank()) mod.config().market.apiKey = newKey.trim();
		mod.configManager.save();
	}

	@Override
	protected String navKey() {
		return "market";
	}

	@Override
	protected void build(Rows rows) {
		ScdConfig c = mod.config();
		if (underClickGui()) expand("api");
		rows.group("api", "scd.wtf API", null, false, g -> {
			g.note("All prices come from https://market.scd.wtf. SCD ships with its own key - a personal key is only needed by admins.");
			g.value("Key", () -> {
				String k = c.market.apiKey;
				if (k != null && !k.isBlank()) return "personal (…" + k.substring(Math.max(0, k.length() - 4)) + ")";
				if (mod.market.usingBuiltInKey()) return "built into SCD";
				return mod.market.hasKey() ? "from SCD_KEY env" : "none";
			});
			g.text("Personal key (optional)", "scd_... (paste, then Save)", "", ch -> ch > ' ' && ch < 127, s -> newKey = s);
			g.buttons(List.of("Save key", "Remove key", "Test"), List.of(() -> {
				if (!newKey.isBlank()) {
					c.market.apiKey = newKey.trim();
					newKey = "";
					mod.configManager.save();
				}
				rebuild();
			}, () -> {
				c.market.apiKey = "";
				mod.configManager.save();
				rebuild();
			}, this::test));
			g.line(() -> testResult != null ? testResult : "Status: " + mod.market.status().message(),
					() -> mod.market.status().ok() ? Ui.SUCCESS : Ui.theme().textMuted());
		});
		if (!underClickGui()) {
			rows.group("tooltips", "Tooltips", null, true, g -> {
				g.toggle("Bazaar prices", "Instant buy/sell and spread", () -> c.bazaar.tooltip, v -> c.bazaar.tooltip = v);
				g.toggle("Auction House prices", "Estimate and lowest BIN for non-Bazaar items", () -> c.market.auctionTooltips, v -> c.market.auctionTooltips = v);
				g.toggle("Whole-stack value", null, () -> c.bazaar.tooltipStackValue, v -> c.bazaar.tooltipStackValue = v);
			});
		}
		if (!underClickGui()) {
			rows.group("graph", "Price graph", null, true, g -> {
				g.toggle("Graph HUD while hovering", null, () -> c.bazaar.graphHud, v -> c.bazaar.graphHud = v);
				g.cycle("Range", List.of("1d", "7d", "30d"), () -> c.bazaar.graphRange, v -> c.bazaar.graphRange = v, v -> v);
				g.slider("Bazaar refresh", 15, 300, 15, () -> c.market.bazaarRefreshSeconds, v -> c.market.bazaarRefreshSeconds = (int) Math.round(v),
						v -> Math.round(v) + "s");
				g.note("Refresh interval changes apply after restarting the game.");
			});
		}
	}

	private void test() {
		if (!newKey.isBlank()) {
			mod.config().market.apiKey = newKey.trim();
			newKey = "";
			mod.configManager.save();
		}
		testResult = "Testing...";
		rebuild();
		mod.market.bazaarProducts().whenComplete((list, err) -> Tasks.onClient(() -> {
			testResult = err != null ? "Failed: " + (err.getCause() != null ? err.getCause().getMessage() : err.getMessage())
					: "OK - " + list.size() + " Bazaar products";
			
			rebuild();
		}));
	}
}
