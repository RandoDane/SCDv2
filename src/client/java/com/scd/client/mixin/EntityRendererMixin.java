package com.scd.client.mixin;

import com.scd.client.feature.world.GlowRegistry;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Per-entity outline color, independent of vanilla's scoreboard-team glow: writes
 * {@link EntityRenderState#outlineColor} after the renderer filled in its default. The generic
 * extractRenderState(T, S, float) erases to (Entity, EntityRenderState, float).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void scd$applyGlow(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		Integer color = GlowRegistry.colorFor(entity);
		if (color != null) state.outlineColor = color;
	}
}
