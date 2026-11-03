package dev.leonida.weapon.mixin;

import dev.leonida.weapon.GunItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Humanoid mobs (police, gang members) holding a gun aim it with both hands. */
@Mixin(HumanoidMobRenderer.class)
public abstract class HumanoidMobRendererMixin {
	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private void leonida$gunPose(Mob mob, HumanoidArm arm, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
		if (arm == mob.getMainArm() && mob.getMainHandItem().getItem() instanceof GunItem) cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
	}
}
