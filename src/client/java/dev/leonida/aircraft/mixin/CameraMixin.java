package dev.leonida.aircraft.mixin;

import dev.leonida.aircraft.entity.AircraftEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Pull the third person camera back while flying, so the whole aircraft is in view. */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private Entity entity;

	@ModifyArg(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"), index = 0)
	private float leonida$aircraftZoom(float distance) {
		if (entity != null && entity.getVehicle() instanceof AircraftEntity a) {
			return Math.max(distance, a.spec.cameraDistance);
		}
		return distance;
	}
}
