package dev.leonida.fx.mixin;

import dev.leonida.fx.FxClient;
import dev.leonida.fx.look.LeonidaLook;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	/** Leonida sky, fog, cloud and sunset colours go on top of the vanilla environment layers. */
	@Inject(method = "addEnvironmentAttributeLayers", at = @At("RETURN"))
	private void leonida$fx$layers(EnvironmentAttributeSystem.Builder builder, CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> cir) {
		LeonidaLook.addLayers(cir.getReturnValue(), (ClientLevel) (Object) this);
	}

	/** Blocks broken by hand (level event 2001) chip into small pieces; glass shatters. */
	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"))
	private void leonida$fx$destroyEffect(BlockPos pos, BlockState state, CallbackInfo ci) {
		FxClient.onBlockDestroyed((ClientLevel) (Object) this, pos, state);
	}
}
