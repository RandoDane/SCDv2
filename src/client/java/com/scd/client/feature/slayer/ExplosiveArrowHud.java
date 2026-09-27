package com.scd.client.feature.slayer;

import com.scd.client.config.HudLayout;
import com.scd.client.config.ScdConfig;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudElement;
import com.scd.client.ui.Ui;

import java.util.Locale;
import java.util.function.Supplier;

/** Explosive Arrows remaining during Voidgloom fights; turns red at 100 or fewer. */
final class ExplosiveArrowHud extends HudElement {
	private static final int LOW = 100;

	private final Supplier<ScdConfig> config;
	private final QuiverTracker quiver;

	ExplosiveArrowHud(Supplier<ScdConfig> config, QuiverTracker quiver) {
		super("explosive_arrows", "Explosive Arrows", HudLayout.at(HudLayout.AnchorX.CENTER, HudLayout.AnchorY.BOTTOM, 0, 58));
		this.config = config;
		this.quiver = quiver;
	}

	@Override
	public boolean enabled() {
		return config.get().slayer.explosiveArrowCounter;
	}

	@Override
	public HudBox build(boolean preview) {
		QuiverTracker.Reading r = preview ? new QuiverTracker.Reading("Explosive Arrow", 2541) : quiver.reading();
		if (r == null) return null;
		String count = String.format(Locale.ROOT, "%,d left", r.remaining());
		HudBox box = new HudBox().minWidth(100).title(r.arrowName());
		return r.remaining() <= LOW ? box.colored(count, Ui.DANGER) : box.text(count);
	}
}
