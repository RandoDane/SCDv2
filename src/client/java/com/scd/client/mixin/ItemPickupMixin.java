package com.scd.client.mixin;

import com.scd.client.ScdMod;
import com.scd.client.core.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server says a player picked up an item entity. Read before the handler removes the entity,
 * on the client thread only (the handler first runs on the network thread and reschedules itself).
 */
@Mixin(ClientPacketListener.class)
public abstract class ItemPickupMixin {
	@Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
	private void scd$onTakeItem(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (!mc.isSameThread() || mc.level == null || mc.player == null) return;
		if (!(mc.level.getEntity(packet.getItemId()) instanceof ItemEntity item)) return;
		ScdMod mod = ScdMod.get();
		if (mod == null) return;
		if (packet.getPlayerId() == mc.player.getId()) mod.bus.post(new Events.ItemPickedUp(item.position(), item.getItem().copy()));
		else mod.bus.post(new Events.ItemTakenByOther(item.position(), packet.getPlayerId()));
	}
}
