package com.scd.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** One self-contained area of the mod (Bazaar, Slayer, Carries, ...). */
public interface Feature {
	/** Wire event subscriptions, HUD elements and background tasks. Called once at startup. */
	void init(ScdMod mod);

	/** Add this feature's subcommands under /scd. */
	default void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
	}
}
