package dev.leonida.character.mixin;

import dev.leonida.character.Appearance;
import dev.leonida.character.CharacterClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Every player in Leonida is Jason or Lucia: the skin comes from the synced Appearance. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
	private void leonida$characterSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		PlayerSkin s = CharacterClient.skin(Appearance.get((AbstractClientPlayer) (Object) this).outfit());
		if (s != null) cir.setReturnValue(s);
	}
}
