package dev.leonida.fx.mixin;

import dev.leonida.fx.look.LeonidaLook;
import java.util.List;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Requests the Leonida colour grade post chain every frame (vanilla keeps a list of requested post effects). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow @Final private List<Identifier> requestedPostEffects;

	@Inject(method = "update", at = @At("TAIL"))
	private void leonida$fx$requestGrade(CallbackInfo ci) {
		LeonidaLook.requestPostEffect(this.requestedPostEffects);
	}
}
