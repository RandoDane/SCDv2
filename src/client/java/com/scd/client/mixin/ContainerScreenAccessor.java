package com.scd.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Container screen origin, to draw overlays over slots. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int scd$leftPos();

	@Accessor("topPos")
	int scd$topPos();

	@Accessor("imageWidth")
	int scd$imageWidth();
}
