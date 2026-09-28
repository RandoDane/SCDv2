package com.scd.client.mixin;

import com.scd.client.ScdMod;
import com.scd.client.core.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A head block turning into air is a wither essence (or redstone key) secret being taken - by
 * anyone, anywhere in loaded chunks. Read before the update is applied (client thread only).
 */
@Mixin(ClientPacketListener.class)
public abstract class BlockUpdateMixin {
	@Inject(method = "handleBlockUpdate", at = @At("HEAD"))
	private void scd$onBlock(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) scd$check(packet.getPos(), packet.getBlockState());
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"))
	private void scd$onSection(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) packet.runUpdates(BlockUpdateMixin::scd$check);
	}

	private static void scd$check(BlockPos pos, BlockState next) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		// A flower pot appearing is a dead Sadan terracotta (F6 boss).
		if (next.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock && !(level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock)) {
			ScdMod m = ScdMod.get();
			if (m != null) m.bus.post(new Events.FlowerPotPlaced(pos.immutable()));
			return;
		}
		if (!next.isAir()) return;
		var before = level.getBlockState(pos).getBlock();
		if (before != Blocks.PLAYER_HEAD && before != Blocks.PLAYER_WALL_HEAD) return;
		ScdMod mod = ScdMod.get();
		if (mod != null) mod.bus.post(new Events.SkullRemoved(pos.immutable()));
	}
}
