package dev.leonida.aircraft.mixin;

import dev.leonida.aircraft.entity.ParachuteEntity;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Parachute riders hang under the canopy with their legs down instead of sitting. */
@Mixin(HumanoidMobRenderer.class)
public abstract class HumanoidRiderMixin {
	@Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
	private static void leonida$hangFromParachute(LivingEntity entity, HumanoidRenderState state, float partialTicks,
			ItemModelResolver itemModelResolver, CallbackInfo ci) {
		if (entity.getVehicle() instanceof ParachuteEntity) state.isPassenger = false;
	}
}
