package dev.leonida.character.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.leonida.character.model.HdModelLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides the flat vanilla body when Jason or Lucia render as their detailed model, and swaps the first person arms. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/resources/Identifier;",
			at = @At("HEAD"), cancellable = true)
	private void leonida$hideFlatBody(AvatarRenderState state, CallbackInfoReturnable<Identifier> cir) {
		if (HdModelLayer.active(state.skin.body().texturePath()) != null) cir.setReturnValue(HdModelLayer.BLANK);
	}

	@Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
	private void leonida$rightHand(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, boolean sleeve, CallbackInfo ci) {
		if (HdModelLayer.submitHand(poseStack, collector, light, skin, true)) ci.cancel();
	}

	@Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
	private void leonida$leftHand(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skin, boolean sleeve, CallbackInfo ci) {
		if (HdModelLayer.submitHand(poseStack, collector, light, skin, false)) ci.cancel();
	}
}
