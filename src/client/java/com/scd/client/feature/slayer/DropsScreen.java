package com.scd.client.feature.slayer;

import com.scd.client.ScdMod;
import com.scd.client.feature.bazaar.BazaarFeature;
import com.scd.client.ui.Rows;
import com.scd.client.ui.ScdScreen;
import com.scd.client.ui.Ui;
import com.scd.client.ui.widget.FlatButton;
import com.scd.logic.Numbers;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Every recorded drop for one type with its market value; remove single entries or clear the type. */
public final class DropsScreen extends ScdScreen {
	private final SlayerFeature slayer;
	private final SlayerType type;
	private boolean confirmClear;

	public DropsScreen(Screen parent, SlayerFeature slayer, SlayerType type) {
		super(type.displayName() + " Drops", parent, 300);
		this.slayer = slayer;
		this.type = type;
	}

	@Override
	protected void build(Rows rows) {
		var entries = slayer.drops().entries(type);
		BazaarFeature bazaar = ScdMod.get().feature(BazaarFeature.class);
		if (entries.isEmpty()) {
			rows.note("Nothing recorded yet. Drops are counted while a " + type.displayName()
					+ " quest is active (and for 20s after a kill).");
			return;
		}
		double total = 0;
		for (var e : entries) {
			var d = e.getValue();
			Double unit = e.getKey().startsWith("SACK:") ? null : bazaar.valueOf(e.getKey());
			if (unit != null) total += unit * d.count;
			String value = unit != null ? Numbers.coins(unit * d.count) : "";
			FlatButton remove = new FlatButton(rows.x() + rows.width() - 14, 0, 14, 12, "×", () -> {
				slayer.drops().remove(type, e.getKey());
				rebuild();
			}).tooltip("Remove this entry");
			int x0 = rows.x();
			int w = rows.width();
			rows.custom(12, (g, x, y, rw, mx, my) -> {
				Ui.text(g, Ui.ellipsize(d.name, w - 120), x0, y + 2, Ui.theme().textSecondary());
				Ui.rightAligned(g, Numbers.compactCount(d.count), x0 + w - 80, y + 2, Ui.theme().textPrimary());
				Ui.rightAligned(g, value, x0 + w - 20, y + 2, Ui.GOLD);
			}, List.of(remove));
		}
		double sum = total;
		rows.space(4);
		rows.value("Estimated value (scd.wtf)", () -> Numbers.coins(sum));
	}

	@Override
	protected List<FooterButton> footer() {
		return List.of(new FooterButton(confirmClear ? "Really clear?" : "Clear all", () -> {
			if (confirmClear) {
				slayer.drops().clear(type);
				confirmClear = false;
			} else {
				confirmClear = true;
			}
			rebuild();
		}));
	}
}
