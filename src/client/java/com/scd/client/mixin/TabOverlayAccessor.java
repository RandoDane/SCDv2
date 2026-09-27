package com.scd.client.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Tab list footer (dungeon blessings live there). */
@Mixin(PlayerTabOverlay.class)
public interface TabOverlayAccessor {
	@Accessor("footer")
	Component scd$footer();
}
