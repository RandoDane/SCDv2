package com.scd.client.feature.dungeon.route;

import com.scd.client.config.HudLayout;
import com.scd.client.config.ScdConfig;
import com.scd.client.hud.HudBox;
import com.scd.client.hud.HudColor;
import com.scd.client.hud.HudElement;
import com.scd.logic.dungeon.route.RouteStep;

import java.util.function.Supplier;

/** "Route 2/6 · chest" while a route plays; recording progress while recording. */
final class RouteHud extends HudElement {
	private final Supplier<ScdConfig> config;
	private final RouteFeature routes;

	RouteHud(Supplier<ScdConfig> config, RouteFeature routes) {
		super("dungeon_route", "Secret route", HudLayout.at(HudLayout.AnchorX.CENTER, HudLayout.AnchorY.TOP, 0, 8));
		this.config = config;
		this.routes = routes;
	}

	@Override
	public boolean enabled() {
		return config.get().dungeon.routes;
	}

	@Override
	public HudBox build(boolean preview) {
		HudBox box = new HudBox().minWidth(110);
		if (preview) return box.text("Route 2/6 · interact", HudColor.TEXT).text("3 etherwarps · 1 mine", HudColor.LABEL);
		RouteRecorder rec = routes.recorder();
		if (rec.active()) {
			box.colored("● Recording " + rec.room().label(), 0xFFF87171);
			box.text(rec.steps().size() + " steps · " + rec.pendingPoints() + " points", HudColor.LABEL);
			return box;
		}
		RouteRunner run = routes.runner();
		if (!run.active()) return null;
		RouteStep s = run.current();
		if (s == null) return box.text("Route done", HudColor.TEXT);
		box.text("Route " + (run.index() + 1) + "/" + run.steps().size() + " · " + s.secretType.key, HudColor.TEXT);
		StringBuilder sb = new StringBuilder();
		if (!s.etherwarps.isEmpty()) sb.append(s.etherwarps.size()).append(" etherwarp").append(s.etherwarps.size() > 1 ? "s" : "");
		if (!s.mines.isEmpty()) sb.append(sb.isEmpty() ? "" : " · ").append(s.mines.size()).append(" mine").append(s.mines.size() > 1 ? "s" : "");
		if (!s.tnts.isEmpty()) sb.append(sb.isEmpty() ? "" : " · ").append(s.tnts.size()).append(" TNT");
		if (!s.pearls.isEmpty()) sb.append(sb.isEmpty() ? "" : " · ").append(s.pearls.size()).append(" pearl").append(s.pearls.size() > 1 ? "s" : "");
		if (!sb.isEmpty()) box.text(sb.toString(), HudColor.LABEL);
		if (run.routeCount() > 1) {
			box.text("route " + (run.routeIndex() + 1) + "/" + run.routeCount() + (run.entryMatched() ? " · nearest your entrance" : "") + "  (/scd route alt)", HudColor.LABEL);
		}
		return box;
	}
}
