package com.scd.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.scd.client.feature.perf.LagProbe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lag scanner: CPU time to extract and submit each block entity (chests, skulls, signs...), per type. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderProbeMixin {
	@Inject(method = "tryExtractRenderState", at = @At("HEAD"))
	private void scd$extractStart(BlockEntity be, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumbling, boolean flag,
			CallbackInfoReturnable<BlockEntityRenderState> cir) {
		LagProbe.begin();
	}

	@Inject(method = "tryExtractRenderState", at = @At("RETURN"))
	private void scd$extractEnd(BlockEntity be, float partialTick, ModelFeatureRenderer.CrumblingOverlay crumbling, boolean flag,
			CallbackInfoReturnable<BlockEntityRenderState> cir) {
		LagProbe.end(be.getType());
	}

	@Inject(method = "submit", at = @At("HEAD"))
	private void scd$submitStart(BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		LagProbe.begin();
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private void scd$submitEnd(BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		LagProbe.end(state.blockEntityType);
	}
}
