package com.scd.client.mixin;

import com.scd.client.core.ServerLag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds server time packets to {@link ServerLag} (client thread only; the handler runs twice). */
@Mixin(ClientPacketListener.class)
public abstract class ServerTimeMixin {
	@Inject(method = "handleSetTime", at = @At("HEAD"))
	private void scd$onTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
		if (Minecraft.getInstance().isSameThread()) ServerLag.onServerTime(packet.gameTime());
	}
}
