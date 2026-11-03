package dev.leonida.weapon.mixin;

import dev.leonida.weapon.WeaponClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The sniper scope reuses vanilla scoping: scope overlay, lower mouse sensitivity, strong zoom, hidden hand item. */
@Mixin(Player.class)
public abstract class PlayerScopingMixin {
	@Inject(method = "isScoping", at = @At("RETURN"), cancellable = true)
	private void leonida$weaponScope(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && (Object) this == Minecraft.getInstance().player && WeaponClient.scoped()) cir.setReturnValue(true);
	}
}
