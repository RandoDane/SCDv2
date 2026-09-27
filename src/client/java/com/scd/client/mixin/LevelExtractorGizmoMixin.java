package com.scd.client.mixin;

import com.scd.client.feature.world.WorldGizmos;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World highlights (routes, slayer boss) are submitted right after vanilla gathers the frame's
 * gizmos. Verified in game: gizmos added before that point are dropped. Unlike the HUD layer used
 * before, this runs even with the HUD hidden (F1), and Fabric's block-outline hook only fires
 * while you look at a block.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorGizmoMixin {
	@Inject(method = "extractGizmos", at = @At("TAIL"))
	private void scd$afterGizmos(CallbackInfo ci) {
		WorldGizmos.fireWorldExtract();
	}
}
