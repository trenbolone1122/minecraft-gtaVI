package dev.leonida.weapon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.leonida.weapon.WeaponClient;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First person gun pose: aim down sights moves the gun to the centre, recoil kicks it back, reload dips it. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonRendererMixin {
	@Inject(method = "applyItemArmTransform", at = @At("TAIL"))
	private void leonida$gunPose(PoseStack pose, HumanoidArm arm, float inverseArmHeight, CallbackInfo ci) {
		WeaponClient.applyGunPose(pose, arm);
	}
}
