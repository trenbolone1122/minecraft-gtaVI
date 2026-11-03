package dev.leonida.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.leonida.weapon.shop.WeaponPickupEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/** Floating, spinning, fully lit weapon pickup (3D gun model, no display transform). */
public class PickupRenderer extends EntityRenderer<WeaponPickupEntity, PickupRenderer.State> {
	public static class State extends EntityRenderState {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public float spin;
		public boolean gun;
	}

	private final ItemModelResolver resolver;

	public PickupRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.resolver = ctx.getItemModelResolver();
		this.shadowRadius = 0.25f;
		this.shadowStrength = 0.6f;
	}

	@Override
	protected int getBlockLightLevel(WeaponPickupEntity e, BlockPos pos) {
		return 15;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(WeaponPickupEntity e, State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.gun = WeaponItems.gunOf(e.getItem()) != null;
		// guns: 3D hand model without transforms; other items: their ground look
		resolver.updateForNonLiving(s.item, e.getItem(), s.gun ? ItemDisplayContext.NONE : ItemDisplayContext.GROUND, e);
		s.spin = (e.tickCount + pt) * 3.5f;
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		pose.pushPose();
		float bob = Mth.sin(s.spin * 0.035f) * 0.06f;
		pose.translate(0, 0.45 + bob, 0);
		pose.rotateDegrees(Axis.YP, s.spin);
		if (s.gun) pose.scale(0.55f, 0.55f, 0.55f);
		else pose.scale(1.1f, 1.1f, 1.1f);
		s.item.submit(pose, out, s.lightCoords, OverlayTexture.NO_OVERLAY, s.outlineColor);
		pose.popPose();
		super.submit(s, pose, out, camera);
	}
}
