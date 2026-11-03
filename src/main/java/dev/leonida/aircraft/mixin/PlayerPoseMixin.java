package dev.leonida.aircraft.mixin;

import dev.leonida.aircraft.AircraftHooks;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skydivers fall belly down: the swimming pose gives the spread-eagle look. */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void leonida$freefallPose(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (AircraftHooks.freefalling.test(self)) {
			self.setPose(Pose.SWIMMING);
			ci.cancel();
		}
	}
}
