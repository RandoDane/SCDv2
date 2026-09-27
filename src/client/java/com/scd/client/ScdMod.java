package com.scd.client;

import com.scd.client.config.ConfigManager;
import com.scd.client.config.ScdConfig;
import com.scd.client.core.EventBus;
import com.scd.client.core.Tasks;
import com.scd.client.hud.HudManager;
import com.scd.client.hypixel.ChatRouter;
import com.scd.client.hypixel.GameState;
import com.scd.client.net.BackendClient;
import com.scd.client.net.MarketClient;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared services every feature is built from - created once by {@link ScdClient}. Features
 * receive this in {@link Feature#init} and keep what they need; they never reach for each other
 * directly except through the typed accessors here.
 */
public final class ScdMod {
	private static ScdMod instance;

	public final String version;
	public final EventBus bus = new EventBus();
	public final Tasks tasks = new Tasks();
	public final ConfigManager configManager;
	public final GameState game;
	public final ChatRouter chat;
	public final BackendClient backend;
	public final MarketClient market;
	public final HudManager huds;
	private final List<Feature> features = new ArrayList<>();

	ScdMod(String version) {
		this.version = version;
		this.configManager = new ConfigManager();
		this.game = new GameState(bus);
		this.chat = new ChatRouter(bus);
		this.backend = new BackendClient(config().backend.serverUrl, version);
		this.market = new MarketClient(this::marketKey, () -> config().backend.serverUrl, version);
		this.huds = new HudManager(configManager, this::active);
		instance = this;
	}

	public static ScdMod get() {
		return instance;
	}

	public ScdConfig config() {
		return configManager.get();
	}

	/** Whether SkyBlock features should run right now (on SkyBlock, or the requirement is switched off). */
	public boolean active() {
		return game.isInWorld() && (!config().general.requireSkyblock || game.isOnSkyblock());
	}

	/** Configured scd.wtf key, or the SCD_KEY environment variable when the setting is empty. */
	private String marketKey() {
		String key = config().market.apiKey;
		if (key != null && !key.isBlank()) return key;
		String env = System.getenv("SCD_KEY");
		return env != null ? env : "";
	}

	void add(Feature feature) {
		features.add(feature);
	}

	public List<Feature> features() {
		return List.copyOf(features);
	}

	@SuppressWarnings("unchecked")
	public <T extends Feature> T feature(Class<T> type) {
		for (Feature f : features) if (type.isInstance(f)) return (T) f;
		throw new IllegalStateException("Feature not registered: " + type.getSimpleName());
	}
}
