package dev.leonida.fx.look;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.leonida.fx.FxConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * Foam on the water: bands that roll in along shorelines and whitecaps that come and go out on open water. The
 * water columns around the camera are scanned on the client about once a second; foam is drawn as soft translucent
 * quads just above the water surface.
 */
public final class WaterFoam {
	public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("leonida", "textures/fx/foam.png");
	private static final int NONE = Integer.MIN_VALUE;
	/** Column in a chunk that is not loaded: neither water nor shore. */
	private static final int UNLOADED = Integer.MIN_VALUE + 1;
	private static final int FAR = 99;
	private static final int BAND = 4;

	private static int radius;
	private static int size;
	private static int originX, originZ;
	private static int[] surfaceY = new int[0];
	private static float[] surfaceH = new float[0];
	private static byte[] dist = new byte[0];
	private static int[] light = new int[0];
	private static boolean valid;
	private static int cooldown;
	private static int waterCells;

	private WaterFoam() {}

	public static int waterCells() {
		return waterCells;
	}

	private static int radiusFor(FxConfig.Quality q) {
		return switch (q) {
			case OFF -> 0;
			case LOW -> 24;
			case MEDIUM -> 40;
			case HIGH -> 56;
		};
	}

	public static void clear() {
		valid = false;
		waterCells = 0;
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		int r = radiusFor(FxConfig.get().quality);
		if (level == null || r == 0 || mc.getCameraEntity() == null || LeonidaLook.shaderPackActive()) {
			clear();
			return;
		}
		BlockPos c = mc.getCameraEntity().blockPosition();
		boolean moved = !valid || Math.abs(c.getX() - (originX + radius)) > 6 || Math.abs(c.getZ() - (originZ + radius)) > 6;
		if (--cooldown > 0 && !moved && r == radius) return;
		cooldown = 20;
		scan(level, c, r);
	}

	private static void scan(ClientLevel level, BlockPos center, int r) {
		radius = r;
		size = 2 * r + 1;
		int n = size * size;
		if (surfaceY.length != n) {
			surfaceY = new int[n];
			surfaceH = new float[n];
			dist = new byte[n];
			light = new int[n];
		}
		originX = center.getX() - r;
		originZ = center.getZ() - r;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int count = 0;
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int x = originX + i, z = originZ + j;
				int k = i * size + j;
				surfaceY[k] = NONE;
				dist[k] = 0;
				if (!level.hasChunk(x >> 4, z >> 4)) {
					surfaceY[k] = UNLOADED;
					continue;
				}
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				p.set(x, y, z);
				FluidState fs = level.getFluidState(p);
				if (!fs.is(FluidTags.WATER)) continue;
				surfaceY[k] = y;
				surfaceH[k] = fs.getHeight(level, p);
				dist[k] = (byte) FAR;
				p.setY(y + 1);
				light[k] = LightCoordsUtil.getLightCoords(level, p);
				count++;
			}
		}
		waterCells = count;
		// distance to the nearest land column (8-neighbour), up to BAND + 2
		for (int pass = 1; pass <= BAND + 2; pass++) {
			for (int i = 0; i < size; i++) {
				for (int j = 0; j < size; j++) {
					int k = i * size + j;
					if (surfaceY[k] <= UNLOADED || dist[k] != FAR) continue;
					boolean near = false;
					for (int di = -1; di <= 1 && !near; di++) {
						for (int dj = -1; dj <= 1; dj++) {
							int a = i + di, b = j + dj;
							if (a < 0 || b < 0 || a >= size || b >= size) continue;
							int kk = a * size + b;
							if (surfaceY[kk] == UNLOADED) continue;
							if (surfaceY[kk] == NONE ? pass == 1 : dist[kk] == pass - 1) {
								near = true;
								break;
							}
						}
					}
					if (near) dist[k] = (byte) -pass; // mark, commit after the pass
				}
			}
			for (int k = 0; k < n; k++) {
				if (dist[k] < 0) dist[k] = (byte) -dist[k];
			}
		}
		valid = true;
	}

	private static float distAt(int i, int j) {
		if (i < 0 || j < 0 || i >= size || j >= size) return BAND + 1;
		int k = i * size + j;
		return surfaceY[k] == NONE ? 0.0F : surfaceY[k] == UNLOADED ? BAND + 1 : dist[k];
	}

	/** Distance to shore at a cell corner: the average of the four cells around it. */
	private static float cornerDist(int i, int j) {
		return (distAt(i - 1, j - 1) + distAt(i, j - 1) + distAt(i - 1, j) + distAt(i, j)) * 0.25F;
	}

	private static float hash(int x, int z, int s) {
		int h = x * 374761393 + z * 668265263 + s * 1274126177;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0x1000000;
	}

	private static float fade = 1.0F;

	public static void submit(SubmitNodeCollector collector, PoseStack ps, Vec3 cam) {
		if (!valid || waterCells == 0 || LeonidaLook.shaderPackActive()) return;
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) return;
		float rain = level.getRainLevel(1.0F);
		// foam is mostly unseen at night: keep it faint so it does not read as dark blotches
		fade = 1.0F - 0.6F * LeonidaLook.night();
		float t = (float) ((Util.getMillis() % 3_600_000L) / 1000.0);
		collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(TEXTURE), (pose, vc) -> draw(pose, vc, cam, t, rain));
	}

	private static void draw(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam, float t, float rain) {
		float whitecapDensity = 0.018F + 0.05F * rain;
		for (int i = 0; i < size; i++) {
			for (int j = 0; j < size; j++) {
				int k = i * size + j;
				if (surfaceY[k] <= UNLOADED) continue;
				int d = dist[k];
				int x = originX + i, z = originZ + j;
				float y = (float) (surfaceY[k] + surfaceH[k] + 0.012 - cam.y);
				float x0 = (float) (x - cam.x), z0 = (float) (z - cam.z);
				int lt = light[k];
				// texture: 4x4 cells per tile
				float u0 = (x & 3) * 0.25F, v0 = (z & 3) * 0.25F;
				if (d <= BAND) {
					// foam bands rolling in towards the shore
					float a00 = band(cornerDist(i, j), t, x, z);
					float a10 = band(cornerDist(i + 1, j), t, x + 1, z);
					float a11 = band(cornerDist(i + 1, j + 1), t, x + 1, z + 1);
					float a01 = band(cornerDist(i, j + 1), t, x, z + 1);
					if (a00 + a10 + a11 + a01 < 0.02F) continue;
					quad(vc, pose, x0, y, z0, u0, v0, 0.25F, lt, a00, a10, a11, a01);
				} else if (d >= FAR && openWater(i, j)) {
					// whitecaps: short-lived patches on open water
					float cycle = 3.2F;
					float off = hash(x, z, 7) * cycle;
					int gen = (int) Math.floor((t + off) / cycle);
					if (hash(x, z, gen) > whitecapDensity) continue;
					float ph = ((t + off) / cycle) - gen;
					float a = (float) Math.pow(Math.sin(ph * Math.PI), 2.0) * 0.75F;
					float uu = (hash(x, z, gen + 3) * 3.0F) * 0.25F, vv = (hash(z, x, gen + 5) * 3.0F) * 0.25F;
					quad(vc, pose, x0, y, z0, uu, vv, 0.25F, lt, a * 0.6F, a, a * 0.8F, a * 0.5F);
				}
			}
		}
	}

	private static boolean openWater(int i, int j) {
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				int a = i + di, b = j + dj;
				if (a < 0 || b < 0 || a >= size || b >= size || surfaceY[a * size + b] <= UNLOADED) return false;
			}
		}
		return true;
	}

	/** Foam opacity at a distance from shore: solid at the shore line, bands moving in, fading out to sea. */
	private static float band(float d, float t, int x, int z) {
		if (d >= BAND + 0.5F) return 0.0F;
		float edge = (float) Math.pow(Math.max(0.0F, 1.0F - d / (BAND + 0.5F)), 1.3F);
		float phase = hash(x >> 3, z >> 3, 11) * 6.2831855F;
		float wave = 0.5F + 0.5F * (float) Math.sin(d * 2.2F + t * 1.4F + phase);
		float shore = d < 0.9F ? 0.55F : 0.0F;
		return Math.min(0.92F, edge * (0.25F + 0.75F * wave * wave) + shore * edge);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, float s, int light,
			float a00, float a10, float a11, float a01) {
		vertex(vc, pose, x, y, z, u, v, light, a00);
		vertex(vc, pose, x, y, z + 1, u, v + s, light, a01);
		vertex(vc, pose, x + 1, y, z + 1, u + s, v + s, light, a11);
		vertex(vc, pose, x + 1, y, z, u + s, v, light, a10);
	}

	private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int light, float a) {
		int alpha = Math.max(0, Math.min(255, (int) (a * fade * 255)));
		vc.addVertex(pose, x, y, z).setColor(255, 255, 255, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}
