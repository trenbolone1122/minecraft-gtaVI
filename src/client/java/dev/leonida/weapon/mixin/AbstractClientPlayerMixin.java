package dev.leonida.weapon.mixin;

import dev.leonida.weapon.WeaponClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Aim down sights zoom. The camera already smooths the field of view modifier. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void leonida$adsZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		if ((Object) this != Minecraft.getInstance().player) return;
		float f = WeaponClient.fovFactor();
		if (f != 1f) cir.setReturnValue(cir.getReturnValueF() * f);
	}
}
