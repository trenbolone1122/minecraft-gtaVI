package dev.leonida.fx.physics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Submits debris boxes and ragdoll bodies to the level renderer each frame. */
public final class FxRenderer {
	private static final float[][] FACES = {
		// normal, then 4 corners as sign triples
		{0, 1, 0, -1, 1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1},
		{0, -1, 0, -1, -1, 1, -1, -1, -1, 1, -1, -1, 1, -1, 1},
		{0, 0, 1, -1, 1, 1, -1, -1, 1, 1, -1, 1, 1, 1, 1},
		{0, 0, -1, 1, 1, -1, 1, -1, -1, -1, -1, -1, -1, 1, -1},
		{1, 0, 0, 1, 1, 1, 1, -1, 1, 1, -1, -1, 1, 1, -1},
		{-1, 0, 0, -1, 1, -1, -1, -1, -1, -1, -1, 1, -1, 1, 1},
	};

	private FxRenderer() {}

	public static void submit(SubmitNodeCollector collector, PoseStack ps, Vec3 cam, float partial, DebrisWorld debris, RagdollManager ragdolls) {
		Level level = Minecraft.getInstance().level;
		if (level == null) return;
		if (debris.size() > 0) {
			List<Piece> opaque = new ArrayList<>(), translucent = new ArrayList<>();
			for (Piece p : debris.pieces()) {
				if (p.sprite == null) continue;
				double dx = p.pos.x - cam.x, dy = p.pos.y - cam.y, dz = p.pos.z - cam.z;
				if (dx * dx + dy * dy + dz * dz > 160 * 160) continue;
				(p.translucent ? translucent : opaque).add(p);
			}
			if (!opaque.isEmpty()) {
				collector.submitCustomGeometry(ps, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS),
						(pose, vc) -> drawPieces(level, pose, vc, opaque, cam, partial));
			}
			if (!translucent.isEmpty()) {
				collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
						(pose, vc) -> drawPieces(level, pose, vc, translucent, cam, partial));
			}
		}
		for (RagdollManager.Body body : ragdolls.bodies()) {
			collector.submitCustomGeometry(ps, body.renderType, (pose, vc) -> drawBody(level, pose, vc, body, cam, partial));
		}
	}

	private static void drawPieces(Level level, PoseStack.Pose pose, VertexConsumer vc, List<Piece> pieces, Vec3 cam, float partial) {
		Quaternionf q = new Quaternionf();
		Matrix4f m = new Matrix4f();
		Vector3f v = new Vector3f();
		Vector3f nrm = new Vector3f();
		BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
		for (Piece p : pieces) {
			double x = p.prevPos.x + (p.pos.x - p.prevPos.x) * partial - cam.x;
			double y = p.prevPos.y + (p.pos.y - p.prevPos.y) * partial - cam.y;
			double z = p.prevPos.z + (p.pos.z - p.prevPos.z) * partial - cam.z;
			p.prevRot.slerp(p.rot, partial, q);
			float s = p.scale(partial);
			if (s <= 0.0F) continue;
			m.translation((float) x, (float) y, (float) z).rotate(q).scale(p.hx * s, p.hy * s, p.hz * s);
			bp.set(p.pos.x, p.pos.y, p.pos.z);
			int light = level.isLoaded(bp) ? LightCoordsUtil.getLightCoords(level, bp) : LightCoordsUtil.FULL_BRIGHT;
			int color = p.color;
			if (p.translucent) color = (color & 0x00FFFFFF) | 0xE0000000;
			for (float[] f : FACES) {
				q.transform(f[0], f[1], f[2], nrm);
				for (int c = 0; c < 4; c++) {
					m.transformPosition(f[3 + c * 3], f[4 + c * 3], f[5 + c * 3], v);
					float u = (c == 0 || c == 1) ? p.u0 : p.u1;
					float vv = (c == 0 || c == 3) ? p.v0 : p.v1;
					vc.addVertex(pose, v.x, v.y, v.z).setColor(color).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nrm.x, nrm.y, nrm.z);
				}
			}
		}
	}

	private static void drawBody(Level level, PoseStack.Pose pose, VertexConsumer vc, RagdollManager.Body body, Vec3 cam, float partial) {
		float sink = body.sink(partial);
		int overlay = body.age < 8 ? OverlayTexture.pack(0, true) : OverlayTexture.NO_OVERLAY;
		Vector3d center = new Vector3d();
		PoseStack ps = new PoseStack();
		if (body.ragdoll != null) {
			body.ragdoll.center(partial, center);
			int light = light(level, center);
			Matrix4f[] deltas = new Matrix4f[6];
			for (int i = 0; i < 6; i++) deltas[i] = new Matrix4f();
			body.ragdoll.boneDeltas(partial, deltas);
			for (int i = 0; i < 6; i++) {
				ModelPart part = body.parts[i];
				Matrix4f world = new Matrix4f().translation(0, -sink, 0).mul(deltas[i]).mul(body.restMatrices[i]);
				ps.last().set(pose);
				ps.translate(-cam.x, -cam.y, -cam.z);
				ps.mulPose(world);
				renderPartZeroed(part, ps, vc, light, overlay);
			}
		} else if (body.rigid != null) {
			Piece p = body.rigid;
			Vector3d pos = new Vector3d(p.prevPos).lerp(p.pos, partial);
			Quaternionf q = p.prevRot.slerp(p.rot, partial, new Quaternionf());
			int light = light(level, pos);
			Matrix4f world = new Matrix4f().translation((float) pos.x, (float) (pos.y - sink), (float) pos.z).rotate(q)
					.translate((float) -body.rigidPos0.x, (float) -body.rigidPos0.y, (float) -body.rigidPos0.z).mul(body.baseMatrix);
			ps.last().set(pose);
			ps.translate(-cam.x, -cam.y, -cam.z);
			ps.mulPose(world);
			body.model.resetPose();
			body.model.root().render(ps, vc, light, overlay, -1);
		}
	}

	private static int light(Level level, Vector3d at) {
		BlockPos bp = BlockPos.containing(at.x, at.y + 0.2, at.z);
		return level.isLoaded(bp) ? LightCoordsUtil.getLightCoords(level, bp) : LightCoordsUtil.FULL_BRIGHT;
	}

	/** Renders a model part with its own offset and rotation cleared (the bone matrix already contains them). */
	private static void renderPartZeroed(ModelPart part, PoseStack ps, VertexConsumer vc, int light, int overlay) {
		float x = part.x, y = part.y, z = part.z, xr = part.xRot, yr = part.yRot, zr = part.zRot;
		float sx = part.xScale, sy = part.yScale, sz = part.zScale;
		boolean visible = part.visible;
		part.x = part.y = part.z = 0;
		part.xRot = part.yRot = part.zRot = 0;
		part.xScale = part.yScale = part.zScale = 1;
		part.visible = true;
		try {
			part.render(ps, vc, light, overlay, -1);
		} finally {
			part.x = x;
			part.y = y;
			part.z = z;
			part.xRot = xr;
			part.yRot = yr;
			part.zRot = zr;
			part.xScale = sx;
			part.yScale = sy;
			part.zScale = sz;
			part.visible = visible;
		}
	}
}
