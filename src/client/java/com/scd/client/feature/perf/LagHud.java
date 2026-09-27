package com.scd.client.feature.perf;

import com.scd.client.config.HudLayout;
import com.scd.client.config.ScdConfig;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudColor;
import com.scd.client.hud.HudElement;
import com.scd.client.ui.Ui;

import java.util.Locale;
import java.util.function.Supplier;

/** Live lag scanner readout: frame rate, tick time, GC and the most expensive things on screen. */
final class LagHud extends HudElement {
	private final Supplier<ScdConfig> config;
	private final Supplier<LagScanner.Snapshot> snapshot;

	LagHud(Supplier<ScdConfig> config, Supplier<LagScanner.Snapshot> snapshot) {
		super("lag_scanner", "Lag scanner", HudLayout.at(HudLayout.AnchorX.RIGHT, HudLayout.AnchorY.BOTTOM, 8, 40));
		this.config = config;
		this.snapshot = snapshot;
	}

	@Override
	public boolean enabled() {
		return config.get().perf.lagScanner;
	}

	@Override
	public HudBox build(boolean preview) {
		HudBox box = new HudBox().minWidth(160);
		LagScanner.Snapshot s = snapshot.get();
		if (preview && s == null) {
			return box.title("Lag scanner").text("142 fps · 1% low 61 · 7.0ms", HudColor.TEXT).text("Armor Stand x312  1.84ms", HudColor.LABEL);
		}
		if (s == null) return box.title("Lag scanner").text("measuring...", HudColor.LABEL);
		box.title("Lag scanner");
		box.colored(String.format(Locale.ROOT, "%.0f fps · 1%% low %.0f · %.1fms", s.fps(), s.lowFps(), s.avgMs()),
				s.lowFps() < 30 ? Ui.DANGER : s.lowFps() < 60 ? Ui.WARNING : Ui.SUCCESS);
		box.text(String.format(Locale.ROOT, "tick %.2fms · GC %d (%dms) · %d ents · %d particles",
				s.tickMs(), s.gcCount(), s.gcMs(), s.entities(), s.particles()), HudColor.LABEL);
		int shown = 0;
		for (LagScanner.Line l : s.top()) {
			if (shown++ >= 5 || l.msPerFrame() < 0.05) break;
			box.text(String.format(Locale.ROOT, "%s%s  %.2fms", l.label(), l.count() > 0 ? " x" + l.count() : "", l.msPerFrame()), HudColor.TEXT);
		}
		box.text(String.format(Locale.ROOT, "Other (terrain/GPU/mods)  %.1fms", s.otherMs()), HudColor.LABEL);
		if (s.scdMs() >= 0.05) box.text(String.format(Locale.ROOT, "SCD  %.2fms", s.scdMs()), HudColor.LABEL);
		return box;
	}
}
