package dev.leonida.fx.mixin;

import dev.leonida.fx.FxModule;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every vanilla (or modded) explosion that destroys blocks throws them as physical debris. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Shadow @Final private ServerLevel level;
	@Shadow @Final private Vec3 center;
	@Shadow @Final private float radius;

	@Inject(method = "interactWithBlocks", at = @At("HEAD"))
	private void leonida$fx$debris(List<BlockPos> targetBlocks, CallbackInfo ci) {
		FxModule.onExplosionBlocks(this.level, this.center, this.radius, targetBlocks);
	}
}
