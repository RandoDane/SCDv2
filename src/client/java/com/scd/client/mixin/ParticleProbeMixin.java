package com.scd.client.mixin;

import com.scd.client.feature.perf.LagKeys;
import com.scd.client.feature.perf.LagProbe;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lag scanner: CPU time spent ticking and extracting particles. */
@Mixin(ParticleEngine.class)
public abstract class ParticleProbeMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void scd$tickStart(CallbackInfo ci) {
		LagProbe.begin();
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void scd$tickEnd(CallbackInfo ci) {
		LagProbe.end(LagKeys.PARTICLES);
	}

	@Inject(method = "extract", at = @At("HEAD"))
	private void scd$extractStart(ParticlesRenderState state, Frustum frustum, Camera camera, float partialTick, CallbackInfo ci) {
		LagProbe.begin();
	}

	@Inject(method = "extract", at = @At("RETURN"))
	private void scd$extractEnd(ParticlesRenderState state, Frustum frustum, Camera camera, float partialTick, CallbackInfo ci) {
		LagProbe.end(LagKeys.PARTICLES);
	}
}
