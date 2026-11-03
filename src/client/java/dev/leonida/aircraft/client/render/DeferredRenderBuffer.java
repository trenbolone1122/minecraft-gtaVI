package dev.leonida.aircraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Records vertices per render type during submit and hands them to the collector as custom geometry.
 * Adapted from Immersive Aircraft (GPL-3.0).
 */
public final class DeferredRenderBuffer {
	private final Map<RenderType, Recorder> buffers = new LinkedHashMap<>();

	public VertexConsumer getBuffer(RenderType type) {
		return buffers.computeIfAbsent(type, t -> new Recorder());
	}

	public void submit(SubmitNodeCollector collector) {
		buffers.forEach((type, buffer) -> {
			if (!buffer.vertices.isEmpty()) {
				List<Vertex> vertices = List.copyOf(buffer.vertices);
				collector.submitCustomGeometry(new PoseStack(), type, (pose, out) -> vertices.forEach(v -> v.write(out)));
			}
		});
		buffers.clear();
	}

	private static final class Recorder implements VertexConsumer {
		final List<Vertex> vertices = new ArrayList<>();
		Vertex current;

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			current = new Vertex(x, y, z);
			vertices.add(current);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			if (current != null) current.color = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
			return this;
		}

		@Override
		public VertexConsumer setColor(int color) {
			if (current != null) current.color = color;
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			if (current != null) {
				current.u = u;
				current.v = v;
			}
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			if (current != null) current.overlay = u & 0xFFFF | v << 16;
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			if (current != null) current.light = u & 0xFFFF | v << 16;
			return this;
		}

		@Override
		public VertexConsumer setUv3(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			if (current != null) {
				current.nx = x;
				current.ny = y;
				current.nz = z;
			}
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			return this;
		}
	}

	private static final class Vertex {
		final float x, y, z;
		int color = 0xFFFFFFFF;
		float u, v;
		int overlay, light;
		float nx, ny = 1, nz;

		Vertex(float x, float y, float z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		void write(VertexConsumer out) {
			out.addVertex(x, y, z).setColor(color).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
		}
	}
}
