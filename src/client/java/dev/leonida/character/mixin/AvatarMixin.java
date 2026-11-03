package dev.leonida.character.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Outfits use every overlay layer (hair, caps, bandanas, vests), so always show them on players. */
@Mixin(Avatar.class)
public abstract class AvatarMixin {
	@Inject(method = "isModelPartShown", at = @At("HEAD"), cancellable = true)
	private void leonida$allParts(PlayerModelPart part, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof AbstractClientPlayer && part != PlayerModelPart.CAPE) cir.setReturnValue(true);
	}
}
