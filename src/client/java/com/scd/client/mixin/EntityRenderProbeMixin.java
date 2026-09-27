package com.scd.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.scd.client.feature.perf.LagProbe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lag scanner: CPU time to extract and submit each entity, per entity type. */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderProbeMixin {
	@Inject(method = "extractEntity", at = @At("HEAD"))
	private void scd$extractStart(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
		LagProbe.begin();
	}

	@Inject(method = "extractEntity", at = @At("RETURN"))
	private void scd$extractEnd(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
		LagProbe.end(entity.getType());
	}

	@Inject(method = "submit", at = @At("HEAD"))
	private void scd$submitStart(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack pose,
			SubmitNodeCollector collector, CallbackInfo ci) {
		LagProbe.begin();
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private void scd$submitEnd(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack pose,
			SubmitNodeCollector collector, CallbackInfo ci) {
		LagProbe.end(state.entityType);
	}
}
