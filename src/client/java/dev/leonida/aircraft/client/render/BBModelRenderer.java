package dev.leonida.aircraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.leonida.aircraft.client.bb.BBModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Draws a Blockbench model, letting a callback animate named bones. Adapted from Immersive Aircraft (GPL-3.0). */
public final class BBModelRenderer {
	private BBModelRenderer() {}

	@FunctionalInterface
	public interface BoneAnimator {
		/** Apply extra transforms for this bone (called at the bone origin). Return false to hide the bone. */
		boolean animate(String bone, PoseStack pose);
	}

	public static void render(BBModel model, PoseStack pose, DeferredRenderBuffer buffers, int light, int overlay, int color, BoneAnimator animator) {
		for (BBModel.BBObject o : model.root) renderObject(model, o, pose, buffers, light, overlay, color, animator);
	}

	private static void renderObject(BBModel model, BBModel.BBObject object, PoseStack pose, DeferredRenderBuffer buffers,
			int light, int overlay, int color, BoneAnimator animator) {
		if (!object.visible) return;
		pose.pushPose();
		pose.translate(object.origin.x, object.origin.y, object.origin.z);
		pose.rotate(BBModel.fromXYZ(object.rotation));
		if (object instanceof BBModel.Bone bone) {
			if (animator != null && !animator.animate(bone.name, pose)) {
				pose.popPose();
				return;
			}
			pose.translate(-object.origin.x, -object.origin.y, -object.origin.z);
			for (BBModel.BBObject child : bone.children) renderObject(model, child, pose, buffers, light, overlay, color, animator);
		} else if (object instanceof BBModel.Cube cube) {
			renderFaces(cube, pose, buffers, light, overlay, color);
		}
		pose.popPose();
	}

	private static void renderFaces(BBModel.Cube cube, PoseStack pose, DeferredRenderBuffer buffers, int light, int overlay, int color) {
		PoseStack.Pose last = pose.last();
		Matrix4f m = last.pose();
		Matrix3f n = last.normal();
		Vector4f p = new Vector4f();
		Vector3f nv = new Vector3f();
		for (BBModel.Face face : cube.faces) {
			RenderType type = cube.glass ? RenderTypes.entityTranslucentCull(face.texture.location)
					: cube.cull ? RenderTypes.entityCutoutCull(face.texture.location) : RenderTypes.entityCutout(face.texture.location);
			VertexConsumer vc = buffers.getBuffer(type);
			int faceLight = cube.lamp ? 0xF000F0 : light;
			for (BBModel.Vertex v : face.vertices) {
				m.transform(p.set(v.x, v.y, v.z, 1f));
				n.transform(nv.set(v.nx, v.ny, v.nz)).normalize();
				vc.addVertex(p.x, p.y, p.z).setColor(color).setUv(v.u, v.v).setOverlay(overlay).setLight(faceLight).setNormal(nv.x, nv.y, nv.z);
			}
		}
	}
}
