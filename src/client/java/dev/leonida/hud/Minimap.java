package dev.leonida.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.leonida.LeonidaMod;
import dev.leonida.core.PlayerState;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** GTA VI minimap, bottom left: translucent rounded rectangle, heading-up, speed zoom, GPS route, blips. */
public final class Minimap {
	private Minimap() {}

	static final Identifier TEX = LeonidaMod.id("hud/minimap_dynamic");
	private static DynamicTexture texture;
	private static int texW, texH;
	private static final MapRaster RASTER = new MapRaster();

	/** Smoothed blocks-per-pixel (zoom) and speed. */
	private static double bpp = 0.9, speed;
	private static Vec3 lastPos;

	/** Layout in real pixels, valid after draw (other HUD parts align to it). */
	public static float x, y, w, h;

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Entity e = mc.player.getVehicle() != null ? mc.player.getRootVehicle() : mc.player;
		Vec3 p = e.position();
		if (lastPos != null) {
			double v = Math.hypot(p.x - lastPos.x, p.z - lastPos.z) * 20; // m/s
			if (v > 120) v = 0; // teleport
			speed += (v - speed) * 0.15;
		}
		lastPos = p;
	}

	static void layout() {
		float u = Draw.U;
		w = Math.round(u * 27.9f);
		h = Math.round(u * 15.4f);
		x = Math.round(u * 3.0f);
		y = Math.round(Draw.H - u * 2.7f - h - u * 1.6f); // leave room for health/armour bars
	}

	private static void ensureTexture(int tw, int th) {
		if (texture != null && tw == texW && th == texH) return;
		if (texture != null) Minecraft.getInstance().getTextureManager().release(TEX);
		texture = new DynamicTexture(() -> "leonida minimap", tw, th, true);
		Minecraft.getInstance().getTextureManager().register(TEX, texture);
		texW = tw;
		texH = th;
	}

	private static long lastRaster;
	private static int lastW;
	/** Number of map redraws so far (tests check the update rate setting). */
	public static long rasterCount;

	public static void draw(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		if (!LeonidaSettings.get().minimap) { w = 0; return; }
		Draw.begin(g);
		try {
			drawInner(g, mc, dt.getGameTimeDeltaPartialTick(false));
		} finally {
			Draw.end(g);
		}
	}

	private static void drawInner(GuiGraphicsExtractor g, Minecraft mc, float pt) {
		layout();
		int tw = (int) w, th = (int) h;
		ensureTexture(tw, th);
		Vec3 pos = mc.player.getPosition(pt);
		float yaw = mc.gameRenderer.mainCamera().yRot();
		// zoom out with speed: ~0.9 bpp on foot, up to ~3.2 at highway speed
		double target = 0.9 + Math.min(2.3, Math.max(0, speed - 6) / 14.0);
		bpp += (target - bpp) * 0.05;
		double scale = bpp * 1080.0 / Draw.H; // same world area at any resolution
		float anchorX = tw / 2f, anchorY = th * 0.74f;

		PlayerState ps = HudState.player();
		RASTER.w = tw;
		RASTER.h = th;
		RASTER.radius = Draw.U * 1.2f;
		RASTER.border = true;
		RASTER.opacity = 0.84f;
		RASTER.fullMap = false;
		RASTER.tint = 0;
		if (ps.wanted() > 0) RASTER.tint = (HudState.now() / 450) % 2 == 0 ? 1 : 2;
		RASTER.headingUp(pos.x, pos.z, anchorX, anchorY, yaw, scale);
		List<BlockPos> route = Route.get();
		double[] wp = Blips.waypoint();
		long nowMs = System.currentTimeMillis();
		if (nowMs - lastRaster >= 1000 / Math.max(1, LeonidaSettings.get().minimapRate) || tw != lastW) {
			lastRaster = nowMs;
			lastW = tw;
			rasterCount++;
			MapColors.frame(6);
			NativeImage img = texture.getPixels();
			RASTER.render(img);
			if (wp != null && route.size() >= 2)
				RASTER.drawRoute(img, route, Math.max(4, Draw.U * 1.15f), 0xFF4FB0, anchorX, anchorY, Draw.U * 2.5f);
			texture.upload();
		}
		g.blit(RenderPipelines.GUI_TEXTURED, TEX, (int) x, (int) y, 0, 0, tw, th, tw, th, tw, th);

		String distLabel = null;
		float blipSize = Draw.U * 2.2f;
		// blips (edge clamped)
		for (Blips.Blip b : Blips.all()) {
			double dist = Math.hypot(b.x() - pos.x, b.z() - pos.z);
			double[] p = RASTER.toPixel(b.x(), b.z());
			boolean inside = p[0] >= blipSize / 2 && p[0] <= tw - blipSize / 2 && p[1] >= blipSize / 2 && p[1] <= th - blipSize / 2;
			if (!inside && (!b.edge() || dist > Blips.EDGE_RANGE)) continue;
			float[] q = inside ? new float[] {(float) p[0], (float) p[1]} : clampToEdge(p[0], p[1], anchorX, anchorY, tw, th, 0);
			Draw.iconCentered(g, b.type().texture(), x + q[0], y + q[1], blipSize, 0, 0xFFFFFFFF);
		}
		// waypoint dot
		if (wp != null) {
			double[] p = RASTER.toPixel(wp[0], wp[1]);
			float ds = Draw.U * 1.5f;
			boolean inside = p[0] >= 0 && p[0] <= tw && p[1] >= 0 && p[1] <= th;
			float[] q = inside ? new float[] {(float) p[0], (float) p[1]} : clampToEdge(p[0], p[1], anchorX, anchorY, tw, th, 0);
			Draw.iconCentered(g, "waypoint", x + q[0], y + q[1], ds, 0, 0xFFFFFFFF);
			double blocks = route.size() >= 2 ? Route.lengthFrom(pos.x, pos.z) : Math.hypot(wp[0] - pos.x, wp[1] - pos.z);
			double miles = blocks / 1609.34;
			distLabel = String.format(java.util.Locale.ROOT, "%.2f mi", miles);
		}
		// north marker on the edge
		{
			double[] p = RASTER.toPixel(pos.x, pos.z - 100000);
			float[] q = clampToEdge(p[0], p[1], anchorX, anchorY, tw, th, 0);
			Draw.iconCentered(g, "north", x + q[0], y + q[1], Draw.U * 1.5f, 0, 0xFFFFFFFF);
		}
		// player arrow (always points up: map is heading-up); rotate by body vs camera difference
		float body = mc.player.getYRot(pt);
		float rel = (float) Math.toRadians(wrap(body - yaw));
		Draw.iconCentered(g, "player", x + anchorX, y + anchorY, Draw.U * 1.75f, rel, 0xFFFFFFFF);
		if (distLabel != null) Draw.text(g, distLabel, Draw.BOLD, x + Draw.U * 1.0f, y + Draw.U * 0.9f, Draw.U * 1.75f, 0xFFFFFFFF, true);
	}

	static float wrap(float deg) {
		deg %= 360;
		if (deg >= 180) deg -= 360;
		if (deg < -180) deg += 360;
		return deg;
	}

	/** Intersects the ray from the anchor toward (px, py) with the map rectangle border. */
	static float[] clampToEdge(double px, double py, double ax, double ay, double w, double h, double inset) {
		double dx = px - ax, dy = py - ay;
		double tMax = Double.MAX_VALUE;
		if (dx > 1e-9) tMax = Math.min(tMax, (w - inset - ax) / dx);
		if (dx < -1e-9) tMax = Math.min(tMax, (inset - ax) / dx);
		if (dy > 1e-9) tMax = Math.min(tMax, (h - inset - ay) / dy);
		if (dy < -1e-9) tMax = Math.min(tMax, (inset - ay) / dy);
		if (tMax == Double.MAX_VALUE) tMax = 0;
		return new float[] {(float) (ax + dx * tMax), (float) (ay + dy * tMax)};
	}
}
