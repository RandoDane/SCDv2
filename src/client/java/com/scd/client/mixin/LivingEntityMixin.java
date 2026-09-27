package com.scd.client.mixin;

import com.scd.client.ScdMod;
import com.scd.client.core.Events;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity event 3 is the server telling the client an entity died (it starts the death animation).
 * It arrives for every nearby mob, on the client thread, before the entity is removed.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "handleEntityEvent", at = @At("HEAD"))
	private void scd$onEntityEvent(byte id, CallbackInfo ci) {
		if (id != 3) return;
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide()) return;
		ScdMod mod = ScdMod.get();
		if (mod != null) mod.bus.post(new Events.EntityDied(self));
	}
}
