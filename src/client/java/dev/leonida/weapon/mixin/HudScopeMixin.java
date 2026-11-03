package dev.leonida.weapon.mixin;

import dev.leonida.LeonidaMod;
import dev.leonida.weapon.WeaponClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sniper scope: our own round black scope (centred at any window size) instead of the spyglass overlay. */
@Mixin(Hud.class)
public abstract class HudScopeMixin {
	private static final Identifier LEONIDA_SCOPE = LeonidaMod.id("textures/gui/weapon/scope.png");

	@Inject(method = "extractSpyglassOverlay", at = @At("HEAD"), cancellable = true)
	private void leonida$sniperScope(GuiGraphicsExtractor g, float scale, CallbackInfo ci) {
		if (!WeaponClient.scoped()) return;
		ci.cancel();
		int gw = g.guiWidth(), gh = g.guiHeight();
		int size = Mth.floor(Math.min(gw, gh) * Math.min(scale, 1.0f));
		int left = (gw - size) / 2, top = (gh - size) / 2, right = left + size, bottom = top + size;
		g.blit(RenderPipelines.GUI_TEXTURED, LEONIDA_SCOPE, left, top, 0f, 0f, size, size, size, size);
		g.fill(RenderPipelines.GUI, 0, bottom, gw, gh, 0xFF000000);
		g.fill(RenderPipelines.GUI, 0, 0, gw, top, 0xFF000000);
		g.fill(RenderPipelines.GUI, 0, top, left, bottom, 0xFF000000);
		g.fill(RenderPipelines.GUI, right, top, gw, bottom, 0xFF000000);
	}
}
