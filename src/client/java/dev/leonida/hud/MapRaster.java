package dev.leonida.hud;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * CPU map rasteriser shared by the minimap and the full map. Each output pixel is mapped back to a
 * world column (rotation + scale), coloured from {@link MapColors}, styled to the GTA VI look and written
 * to a NativeImage that is then uploaded as one texture (one quad on screen, any rotation, rounded mask).
 */
public final class MapRaster {
	/** Output pixel (px, py) -> world: wx = ox + px*ax + py*bx, wz = oz + px*az + py*bz. */
	public double ox, oz, ax, az, bx, bz;
	public int w, h;
	/** 0 none, 1 red, 2 blue: wanted flash tint. */
	public int tint;
	public float tintStrength = 0.42f;
	/** Overall map opacity (minimap is translucent). */
	public float opacity = 1f;
	/** Rounded rect mask radius in pixels (0 = no mask). */
	public float radius;
	/** Draw a thin light border inside the mask. */
	public boolean border;
	/** Full map uses a slightly different palette (more contrast). */
	public boolean fullMap;

	/** Configures a heading-up view: world point (cx, cz) at pixel (anchorX, anchorY), yaw in MC degrees. */
	public void headingUp(double cx, double cz, double anchorX, double anchorY, float yawDeg, double bpp) {
		double yaw = Math.toRadians(yawDeg);
		double fx = -Math.sin(yaw), fz = Math.cos(yaw); // forward
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw); // right
		ax = rx * bpp; az = rz * bpp; // +px -> right
		bx = -fx * bpp; bz = -fz * bpp; // +py -> backwards
		ox = cx - anchorX * ax - anchorY * bx;
		oz = cz - anchorX * az - anchorY * bz;
	}

	/** North-up view centred on (cx, cz). */
	public void northUp(double cx, double cz, double bpp) {
		ax = bpp; az = 0; bx = 0; bz = bpp;
		ox = cx - w / 2.0 * bpp;
		oz = cz - h / 2.0 * bpp;
	}

	/** World -> pixel (inverse of the affine map). */
	public double[] toPixel(double wx, double wz) {
		double det = ax * bz - bx * az;
		double dx = wx - ox, dz = wz - oz;
		double px = (dx * bz - dz * bx) / det;
		double py = (ax * dz - az * dx) / det;
		return new double[] {px, py};
	}

	public double[] toWorld(double px, double py) {
		return new double[] {ox + px * ax + py * bx, oz + px * az + py * bz};
	}

	private float maskAlpha(int x, int y) {
		if (radius <= 0) return 1f;
		float cx = x + 0.5f, cy = y + 0.5f;
		float qx = Math.max(Math.max(radius - cx, cx - (w - radius)), 0);
		float qy = Math.max(Math.max(radius - cy, cy - (h - radius)), 0);
		if (qx == 0 && qy == 0) return 1f;
		float d = (float) Math.sqrt(qx * qx + qy * qy);
		return Math.max(0, Math.min(1, radius - d + 0.5f));
	}

	private float edgeDist(int x, int y) {
		float cx = x + 0.5f, cy = y + 0.5f;
		float qx = Math.max(Math.max(radius - cx, cx - (w - radius)), 0);
		float qy = Math.max(Math.max(radius - cy, cy - (h - radius)), 0);
		if (qx > 0 && qy > 0) return radius - (float) Math.sqrt(qx * qx + qy * qy);
		return Math.min(Math.min(cx, w - cx), Math.min(cy, h - cy));
	}

	/** GTA VI style: soft desaturated colours with a cool blue cast; water deep blue; unknown = slate. */
	int style(int argb) {
		if ((argb >>> 24) == 0) return fullMap ? 0x2B3A55 : 0x34466A;
		int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
		float l = (0.3f * r + 0.59f * g + 0.11f * b);
		float sat = fullMap ? 0.55f : 0.42f;
		float rf = l + (r - l) * sat, gf = l + (g - l) * sat, bf = l + (b - l) * sat;
		// cool cast + lift
		rf = rf * 0.86f + 18; gf = gf * 0.9f + 24; bf = bf * 0.92f + 44;
		return (clamp(rf) << 16) | (clamp(gf) << 8) | clamp(bf);
	}

	private static int clamp(float v) { return v < 0 ? 0 : v > 255 ? 255 : (int) v; }

	/** Renders the map into img (must be at least w x h). */
	public void render(NativeImage img) {
		int tr = tint == 1 ? 0xE0202C : 0x2050F0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float m = maskAlpha(x, y);
				if (m <= 0) { img.setPixel(x, y, 0); continue; }
				double wx = ox + (x + 0.5) * ax + (y + 0.5) * bx;
				double wz = oz + (x + 0.5) * az + (y + 0.5) * bz;
				int c = style(MapColors.get((int) Math.floor(wx), (int) Math.floor(wz)));
				if (tint != 0) c = mix(c, tr, tintStrength);
				float a = opacity;
				if (border && radius > 0) {
					float e = edgeDist(x, y);
					if (e < 1.6f) { c = mix(c, 0xFFFFFF, 0.55f); a = Math.min(1, a + 0.1f); }
				}
				img.setPixel(x, y, ((int) (a * m * 255) << 24) | c);
			}
		}
	}

	static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bb;
	}

	/**
	 * Draws the GPS route as a thick anti-aliased pink line (blended over the map pixels, inside the mask).
	 * fadeStart: pixels within this distance of the player end fade out, like the GTA VI route tail.
	 */
	public void drawRoute(NativeImage img, List<BlockPos> route, float width, int rgb, double playerPx, double playerPy, float fade) {
		if (route.size() < 2) return;
		float[] cov = new float[w * h];
		double hw = width / 2.0;
		for (int i = 0; i + 1 < route.size(); i++) {
			double[] p0 = toPixel(route.get(i).getX() + 0.5, route.get(i).getZ() + 0.5);
			double[] p1 = toPixel(route.get(i + 1).getX() + 0.5, route.get(i + 1).getZ() + 0.5);
			int minX = (int) Math.max(0, Math.floor(Math.min(p0[0], p1[0]) - hw - 1));
			int maxX = (int) Math.min(w - 1, Math.ceil(Math.max(p0[0], p1[0]) + hw + 1));
			int minY = (int) Math.max(0, Math.floor(Math.min(p0[1], p1[1]) - hw - 1));
			int maxY = (int) Math.min(h - 1, Math.ceil(Math.max(p0[1], p1[1]) + hw + 1));
			if (minX > maxX || minY > maxY) continue;
			double vx = p1[0] - p0[0], vy = p1[1] - p0[1];
			double len2 = vx * vx + vy * vy;
			for (int y = minY; y <= maxY; y++) {
				for (int x = minX; x <= maxX; x++) {
					double qx = x + 0.5 - p0[0], qy = y + 0.5 - p0[1];
					double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, (qx * vx + qy * vy) / len2));
					double dx = qx - t * vx, dy = qy - t * vy;
					double d = Math.sqrt(dx * dx + dy * dy);
					float a = (float) Math.max(0, Math.min(1, hw - d + 0.5));
					if (a > cov[y * w + x]) cov[y * w + x] = a;
				}
			}
		}
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float a = cov[y * w + x];
				if (a <= 0) continue;
				int dst = img.getPixel(x, y);
				int da = dst >>> 24;
				if (da == 0) continue;
				if (fade > 0) {
					double pd = Math.hypot(x - playerPx, y - playerPy);
					a *= (float) Math.max(0, Math.min(1, (pd - fade * 0.25) / fade));
				}
				int c = mix(dst & 0xFFFFFF, rgb, a * 0.95f);
				int na = Math.min(255, (int) (da + (255 - da) * a * 0.9f));
				img.setPixel(x, y, (na << 24) | c);
			}
		}
	}
}
