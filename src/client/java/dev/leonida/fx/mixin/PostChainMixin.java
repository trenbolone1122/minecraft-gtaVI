package dev.leonida.fx.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.leonida.fx.look.LeonidaLook;
import java.util.List;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uploads the Leonida grade uniforms right before our post chain runs. */
@Mixin(PostChain.class)
public abstract class PostChainMixin {
	@Shadow @Final private Identifier id;
	@Shadow @Final private List<PostPass> passes;

	@Inject(method = "process", at = @At("HEAD"))
	private void leonida$fx$uniforms(CallbackInfo ci) {
		LeonidaLook.beforeProcess(this.id, this.passes);
	}
}
