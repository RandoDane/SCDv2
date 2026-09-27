package com.scd.client.storage;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** Every file SCD owns lives under config/scd/ - one folder to back up, share or delete. */
public final class ScdPaths {
	private ScdPaths() {
	}

	public static Path root() {
		return FabricLoader.getInstance().getConfigDir().resolve("scd");
	}

	public static Path file(String name) {
		return root().resolve(name);
	}

	/** Pre-2.0 files sat directly in config/ (scd.json, scd_carries.json, ...). */
	public static Path legacy(String name) {
		return FabricLoader.getInstance().getConfigDir().resolve(name);
	}
}
