package dev.leonida.fx.mixin;

import dev.leonida.fx.FxClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An entity that is currently shown as a ragdoll is not drawn by its normal renderer. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void leonida$fx$hideRagdolled(Entity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks,
			CallbackInfoReturnable<Boolean> cir) {
		if (FxClient.ragdolls().isHidden(entity)) cir.setReturnValue(false);
	}
}
