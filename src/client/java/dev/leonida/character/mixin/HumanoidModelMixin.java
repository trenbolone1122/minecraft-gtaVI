package dev.leonida.character.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Seated dual wield pose: a seated character with a pistol in each hand raises one beside the shoulder
 * and rests the other hand on the seat, legs hanging, like Lucia on the car hood in the key art. Applies to the vanilla player
 * model and to the detailed character models alike, so held items follow the arms.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
	@Shadow @Final public ModelPart rightArm;
	@Shadow @Final public ModelPart leftArm;
	@Shadow @Final public ModelPart rightLeg;
	@Shadow @Final public ModelPart leftLeg;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void leonida$seatedDualWield(HumanoidRenderState state, CallbackInfo ci) {
		if (!((Object) this instanceof PlayerModel) || !(state instanceof AvatarRenderState s)) return;
		if (!s.isPassenger || s.rightHandItemStack.isEmpty() || s.leftHandItemStack.isEmpty()) return;
		if (s.rightArmPose != HumanoidModel.ArmPose.CROSSBOW_HOLD && s.leftArmPose != HumanoidModel.ArmPose.CROSSBOW_HOLD) return;
		// one pistol raised beside the shoulder, the other hand resting on the hood, legs hanging over the edge
		float bob = net.minecraft.util.Mth.sin(s.ageInTicks * 0.06F) * 0.04F;
		boolean leftMain = s.rightArmPose != HumanoidModel.ArmPose.CROSSBOW_HOLD;
		ModelPart up = leftMain ? this.leftArm : this.rightArm;
		ModelPart rest = leftMain ? this.rightArm : this.leftArm;
		float sgn = leftMain ? -1.0F : 1.0F;
		up.xRot = -2.05F + bob;
		up.yRot = -0.1F * sgn;
		up.zRot = -0.18F * sgn;
		rest.xRot = 0.5F;
		rest.yRot = 0.0F;
		rest.zRot = 0.22F * sgn;
		this.rightLeg.xRot = -0.55F;
		this.rightLeg.yRot = 0.14F;
		this.rightLeg.zRot = 0.04F;
		this.leftLeg.xRot = -0.35F + bob;
		this.leftLeg.yRot = -0.1F;
		this.leftLeg.zRot = -0.04F;
	}
}
