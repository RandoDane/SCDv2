package com.scd.client.mixin;

import com.scd.client.core.Events.ChatReceived.Channel;
import com.scd.client.hypixel.ChatRouter;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes every chat packet at HEAD of its handler, before any Fabric event or other mod sees it.
 * Purely observational - nothing is changed or cancelled. See {@link ChatRouter}.
 */
@Mixin(ClientPacketListener.class)
public abstract class ChatPacketMixin {
	@Inject(method = "handleSystemChat", at = @At("HEAD"))
	private void scd$onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
		ChatRouter.onPacket(packet.content(), packet.overlay() ? Channel.ACTION_BAR : Channel.SYSTEM);
	}

	@Inject(method = "handlePlayerChat", at = @At("HEAD"))
	private void scd$onPlayerChat(ClientboundPlayerChatPacket packet, CallbackInfo ci) {
		var unsigned = packet.unsignedContent();
		ChatRouter.onPacket(unsigned != null ? unsigned : net.minecraft.network.chat.Component.literal(packet.body().content()), Channel.PLAYER);
	}

	@Inject(method = "handleDisguisedChat", at = @At("HEAD"))
	private void scd$onDisguisedChat(ClientboundDisguisedChatPacket packet, CallbackInfo ci) {
		ChatRouter.onPacket(packet.message(), Channel.DISGUISED);
	}
}
