package dev.leonida.aircraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.leonida.LeonidaMod;
import dev.leonida.aircraft.client.bb.BBModel;
import dev.leonida.aircraft.client.bb.BBModelLoader;
import dev.leonida.aircraft.entity.ParachuteEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/** Draws the ram-air canopy above its rider; it unfolds over the first half second. */
public class ParachuteRenderer extends EntityRenderer<ParachuteEntity, ParachuteRenderer.State> {
	public static class State extends EntityRenderState {
		public BBModel model;
		public float yaw, roll, open;
	}

	public ParachuteRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(ParachuteEntity entity, float partialTicks) {
		return entity.getBoundingBox().inflate(4, 4, 4).expandTowards(0, 2, 0);
	}

	@Override
	public void extractRenderState(ParachuteEntity entity, State state, float tickDelta) {
		super.extractRenderState(entity, state, tickDelta);
		state.model = BBModelLoader.MODELS.get(LeonidaMod.id("aircraft/parachute"));
		state.yaw = entity.getYRot(tickDelta);
		state.roll = entity.getRoll(tickDelta);
		float t = Mth.clamp((entity.tickCount + tickDelta) / 10f, 0f, 1f);
		state.open = 1f - (1f - t) * (1f - t);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.model == null) return;
		DeferredRenderBuffer buffers = new DeferredRenderBuffer();
		pose.pushPose();
		pose.rotate(new Quaternionf().rotateY(-state.yaw * Mth.DEG_TO_RAD));
		pose.translate(0, 1.6, 0);
		pose.rotate(new Quaternionf().rotateZ(state.roll * Mth.DEG_TO_RAD));
		pose.translate(0, -1.6, 0);
		float o = state.open;
		pose.translate(0, 1.6 * (1 - o), 0);
		pose.scale(0.3f + 0.7f * o, o, 0.3f + 0.7f * o);
		BBModelRenderer.render(state.model, pose, buffers, state.lightCoords, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null);
		pose.popPose();
		buffers.submit(collector);
		super.submit(state, pose, collector, camera);
	}
}
