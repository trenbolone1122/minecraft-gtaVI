package dev.leonida.aircraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.leonida.LeonidaMod;
import dev.leonida.aircraft.client.bb.BBModel;
import dev.leonida.aircraft.client.bb.BBModelLoader;
import dev.leonida.aircraft.entity.AircraftEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/** Renders any Leonida aircraft from its Blockbench model, spins rotors and propellers, and draws the searchlight beam. */
public class AircraftRenderer extends EntityRenderer<AircraftEntity, AircraftRenderer.State> {
	public static class State extends EntityRenderState {
		public BBModel model;
		public float yaw, pitch, roll, engineRotation;
		public int hurtTime;
		public @Nullable Vec3 spot;
		public boolean helicopter;
		public Vector3f lightPivot = new Vector3f();
		public float groundPitch, groundPivotZ;
	}

	public AircraftRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.2f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	protected AABB getBoundingBoxForCulling(AircraftEntity entity, float partialTicks) {
		return entity.getBoundingBox().inflate(9, 3, 9);
	}

	@Override
	public void extractRenderState(AircraftEntity entity, State state, float tickDelta) {
		super.extractRenderState(entity, state, tickDelta);
		int variant = entity.getVariant();
		BBModel model = variant > 0 ? BBModelLoader.MODELS.get(LeonidaMod.id("aircraft/" + entity.spec.model + "_v" + variant)) : null;
		state.model = model != null ? model : BBModelLoader.MODELS.get(LeonidaMod.id("aircraft/" + entity.spec.model));
		state.lightPivot.set((float) entity.spec.lightPivot.x, (float) entity.spec.lightPivot.y, (float) entity.spec.lightPivot.z);
		state.groundPitch = entity.spec.groundPitch * Mth.lerp(tickDelta, entity.prevGroundTilt, entity.groundTilt);
		state.groundPivotZ = entity.spec.groundPivotZ;
		state.yaw = entity.getYRot(tickDelta);
		state.pitch = entity.getXRot(tickDelta);
		state.roll = entity.getRoll(tickDelta);
		state.engineRotation = entity.getEngineRotation(tickDelta);
		state.hurtTime = entity.getHurtTime();
		state.helicopter = entity.spec.isHelicopter();
		Vec3 spot = entity.getSearchlightSpot(tickDelta);
		state.spot = spot == null ? null : spot.subtract(entity.getPosition(tickDelta));
	}

	private static Matrix4f vehicleMatrix(State s) {
		return new Matrix4f().rotateY(-s.yaw * Mth.DEG_TO_RAD).rotateX(s.pitch * Mth.DEG_TO_RAD).rotateZ(s.roll * Mth.DEG_TO_RAD);
	}

	@Override
	public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.model == null) return;
		DeferredRenderBuffer buffers = new DeferredRenderBuffer();
		Matrix4f vehicle = vehicleMatrix(state);
		// searchlight aim in model space
		float[] aim = null;
		if (state.spot != null) {
			Vector3f local = new Matrix4f(vehicle).invert().transformPosition(
					new Vector3f((float) state.spot.x, (float) state.spot.y, (float) state.spot.z)).sub(state.lightPivot);
			float yawL = (float) Math.atan2(local.x, local.z);
			float pitchL = (float) Math.atan2(-local.y, Math.sqrt(local.x * local.x + local.z * local.z));
			aim = new float[] {yawL, pitchL};
		}
		float[] aimF = aim;
		pose.pushPose();
		pose.rotate(new Quaternionf().rotateY(-state.yaw * Mth.DEG_TO_RAD));
		pose.rotate(new Quaternionf().rotateX(state.pitch * Mth.DEG_TO_RAD));
		pose.rotate(new Quaternionf().rotateZ(state.roll * Mth.DEG_TO_RAD));
		if (state.groundPitch != 0) {  // parked tail dragger: pivot nose-up on the main gear
			pose.translate(0, 0, state.groundPivotZ);
			pose.rotate(new Quaternionf().rotateX(-state.groundPitch * Mth.DEG_TO_RAD));
			pose.translate(0, 0, -state.groundPivotZ);
		}
		int overlay = OverlayTexture.pack(0, state.hurtTime > 0 ? 3 : 10);
		float spin = state.engineRotation;
		BBModelRenderer.render(state.model, pose, buffers, state.lightCoords, overlay, 0xFFFFFFFF, (bone, p) -> {
			switch (bone) {
				case "propeller", "propeller_l" -> p.rotate(new Quaternionf().rotateZ(spin));
				case "propeller_r" -> p.rotate(new Quaternionf().rotateZ(-spin));
				case "rotor" -> p.rotate(new Quaternionf().rotateY(-spin));
				case "tail_rotor" -> p.rotate(new Quaternionf().rotateX(spin * 2.2f));
				case "searchlight" -> {
					if (aimF != null) {
						p.rotate(new Quaternionf().rotateY(aimF[0]));
						p.rotate(new Quaternionf().rotateX(aimF[1]));
					}
				}
				default -> {}
			}
			return true;
		});
		pose.popPose();

		if (state.spot != null) {
			Vector3f from = vehicle.transformPosition(new Vector3f(state.lightPivot));
			Vector3f to = new Vector3f((float) state.spot.x, (float) state.spot.y + 0.05f, (float) state.spot.z);
			drawBeam(buffers.getBuffer(RenderTypes.lightning()), pose.last().pose(), from, to);
		}
		buffers.submit(collector);
		super.submit(state, pose, collector, camera);
	}

	/** A soft additive light cone plus a bright pool where it lands. */
	static void drawBeam(VertexConsumer vc, Matrix4f m, Vector3f from, Vector3f to) {
		Vector3f axis = new Vector3f(to).sub(from);
		float len = axis.length();
		if (len < 0.5f) return;
		axis.div(len);
		Vector3f up = Math.abs(axis.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
		Vector3f u = new Vector3f(axis).cross(up).normalize();
		Vector3f v = new Vector3f(axis).cross(u).normalize();
		float r0 = 0.25f, r1 = Mth.clamp(len * 0.09f, 1.2f, 4.5f);
		int seg = 16;
		for (int layer = 0; layer < 2; layer++) {
			float scale = layer == 0 ? 1f : 0.55f;
			int a0 = layer == 0 ? 60 : 70, a1 = layer == 0 ? 8 : 14;
			for (int i = 0; i < seg; i++) {
				double t0 = i * Math.PI * 2 / seg, t1 = (i + 1) * Math.PI * 2 / seg;
				Vector3f d0 = new Vector3f(u).mul((float) Math.cos(t0)).add(new Vector3f(v).mul((float) Math.sin(t0)));
				Vector3f d1 = new Vector3f(u).mul((float) Math.cos(t1)).add(new Vector3f(v).mul((float) Math.sin(t1)));
				Vector3f p0 = new Vector3f(from).add(new Vector3f(d0).mul(r0 * scale));
				Vector3f p1 = new Vector3f(from).add(new Vector3f(d1).mul(r0 * scale));
				Vector3f q0 = new Vector3f(to).add(new Vector3f(d0).mul(r1 * scale));
				Vector3f q1 = new Vector3f(to).add(new Vector3f(d1).mul(r1 * scale));
				quad(vc, m, p0, q0, q1, p1, a0, a1);
				quad(vc, m, p1, q1, q0, p0, a0, a1);
			}
		}
		// pool of light on the ground
		float pr = r1 * 1.15f;
		for (int i = 0; i < seg; i++) {
			double t0 = i * Math.PI * 2 / seg, t1 = (i + 1) * Math.PI * 2 / seg;
			Vector3f c = new Vector3f(to).add(0, 0.02f, 0);
			Vector3f e0 = new Vector3f(c).add((float) Math.cos(t0) * pr, 0, (float) Math.sin(t0) * pr);
			Vector3f e1 = new Vector3f(c).add((float) Math.cos(t1) * pr, 0, (float) Math.sin(t1) * pr);
			vertex(vc, m, c, 150);
			vertex(vc, m, c, 150);
			vertex(vc, m, e1, 0);
			vertex(vc, m, e0, 0);
			vertex(vc, m, c, 150);
			vertex(vc, m, e0, 0);
			vertex(vc, m, e1, 0);
			vertex(vc, m, c, 150);
		}
	}

	private static void quad(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int nearAlpha, int farAlpha) {
		vertex(vc, m, a, nearAlpha);
		vertex(vc, m, b, farAlpha);
		vertex(vc, m, c, farAlpha);
		vertex(vc, m, d, nearAlpha);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, Vector3f p, int alpha) {
		Vector3f t = m.transformPosition(new Vector3f(p));
		vc.addVertex(t.x, t.y, t.z).setColor(255, 250, 225, alpha);
	}
}
