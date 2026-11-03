package dev.leonida.character.mixin;

import dev.leonida.character.SwitchTransition;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Character switch camera: lift into the sky and drop onto the other character. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow private boolean detached;
	@Shadow private Entity entity;

	@Shadow protected abstract void setRotation(float yRot, float xRot);

	@Shadow protected abstract void setPosition(double x, double y, double z);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void leonida$switchCamera(float partialTicks, CallbackInfo ci) {
		if (this.entity == null) return;
		double[] c = SwitchTransition.camera(this.entity, partialTicks);
		if (c == null) return;
		this.detached = true;
		this.setRotation((float) c[3], (float) c[4]);
		this.setPosition(c[0], c[1], c[2]);
	}
}
