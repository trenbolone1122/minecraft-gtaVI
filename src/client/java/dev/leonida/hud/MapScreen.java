package dev.leonida.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.leonida.LeonidaMod;
import dev.leonida.core.api.MapQuery;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;

/** Full screen pause map: north-up, pan (drag), zoom (scroll), legend, right click sets the GPS waypoint. */
public class MapScreen extends Screen {
	static final Identifier TEX = LeonidaMod.id("hud/map_dynamic");
	private static DynamicTexture texture;
	private static int texW, texH;
	/** Remembered between openings, like GTA. */
	private static double bpp = 2.0;
	private static double cx = Double.NaN, cz;
	/** Texels of the last full map redraw whose map colour was not computed yet (0 = complete). */
	public static volatile int lastMisses = -1;

	/** Sets the remembered zoom in blocks per real pixel (tests, other modules). */
	public static void setZoom(double blocksPerPixel) { bpp = Math.max(0.25, Math.min(24, blocksPerPixel)); }

	private final MapRaster raster = new MapRaster();
	private final int res = 2; // real pixels per map texel
	private boolean dragging;
	private double lastViewKey = Double.NaN;
	private int redrawFrames;
	private long openedAt;

	public MapScreen() {
		super(Component.translatable("screen.leonida.hud.map"));
	}

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			cx = mc.player.getX();
			cz = mc.player.getZ();
		}
		openedAt = HudState.now();
	}

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}

	private double gs() { return Minecraft.getInstance().getWindow().getGuiScale(); }

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Minecraft mc = Minecraft.getInstance();
		Draw.begin(g);
		try {
			int W = Draw.W, H = Draw.H;
			float u = Draw.U;
			int tw = Math.max(1, W / res), th = Math.max(1, H / res);
			if (texture == null || tw != texW || th != texH) {
				if (texture != null) mc.getTextureManager().release(TEX);
				texture = new DynamicTexture(() -> "leonida map", tw, th, true);
				mc.getTextureManager().register(TEX, texture);
				texW = tw;
				texH = th;
				redrawFrames = 0;
			}
			raster.w = tw;
			raster.h = th;
			raster.radius = 0;
			raster.border = false;
			raster.opacity = 1f;
			raster.fullMap = true;
			raster.tint = 0;
			raster.northUp(cx, cz, bpp * res);
			MapColors.frame(48, bpp * res);
			double key = cx * 31 + cz * 17 + bpp * 1e6 + tw;
			// keep redrawing until every visible region is computed (the old fixed 40 frame cap left strips)
			if (key != lastViewKey || lastMisses > 0 || redrawFrames < 3 || Route.get().size() > 1) {
				NativeImage img = texture.getPixels();
				raster.render(img);
				lastMisses = MapColors.misses;
				Vec3 pp = mc.player != null ? mc.player.position() : Vec3.ZERO;
				double[] ppx = raster.toPixel(pp.x, pp.z);
				raster.drawRoute(img, Route.get(), Math.max(2.5f, u * 0.55f / res * 1.6f), 0xFF4FB0, ppx[0], ppx[1], 0);
				texture.upload();
				if (key != lastViewKey) redrawFrames = 0;
				lastViewKey = key;
				redrawFrames++;
			}
			g.blit(RenderPipelines.GUI_TEXTURED, TEX, 0, 0, 0, 0, tw * res, th * res, tw, th, tw, th);

			// vignette top/bottom for legibility
			for (int i = 0; i < 12; i++) {
				float t = 1 - i / 12f;
				Draw.rect(g, 0, i * u * 0.9f, W, u * 0.9f, Draw.argb(0.5f * t * t, 0x000000));
				Draw.rect(g, 0, H - (i + 1) * u * 0.9f, W, u * 0.9f, Draw.argb(0.5f * t * t, 0x000000));
			}

			// blips
			Set<Blips.Type> present = EnumSet.noneOf(Blips.Type.class);
			for (Blips.Blip b : Blips.all()) {
				double[] p = toScreen(b.x(), b.z());
				present.add(b.type());
				if (p[0] < -50 || p[1] < -50 || p[0] > W + 50 || p[1] > H + 50) continue;
				Draw.iconCentered(g, b.type().texture(), (float) p[0], (float) p[1], u * 2.8f, 0, 0xFFFFFFFF);
			}
			double[] wp = Blips.waypoint();
			if (wp != null) {
				double[] p = toScreen(wp[0], wp[1]);
				Draw.iconCentered(g, "waypoint", (float) p[0], (float) p[1], u * 2.2f, 0, 0xFFFFFFFF);
			}
			if (mc.player != null) {
				double[] p = toScreen(mc.player.getX(), mc.player.getZ());
				float rot = (float) Math.toRadians(mc.player.getYRot() + 180);
				Draw.iconCentered(g, "player", (float) p[0], (float) p[1], u * 2.6f, rot, 0xFFFFFFFF);
			}

			// cursor
			double mx = mouseX * gs(), my = mouseY * gs();
			Draw.rect(g, (float) mx - u * 1.2f, (float) my - 1, u * 2.4f, 2, 0xCCFFFFFF);
			Draw.rect(g, (float) mx - 1, (float) my - u * 1.2f, 2, u * 2.4f, 0xCCFFFFFF);
			double[] wc = toWorld(mx, my);
			String place = null;
			try {
				place = !MapColors.onLeonida() ? null : MapQuery.get().placeName((int) Math.floor(wc[0]), (int) Math.floor(wc[1]));
			} catch (RuntimeException ignored) {}

			// header
			Draw.text(g, "LEONIDA", Draw.BLACK, u * 3, u * 2.6f, u * 4.2f, 0xFFFFFFFF, true);
			Draw.text(g, place != null ? place.toUpperCase(Locale.ROOT) : String.format(Locale.ROOT, "%d, %d", (int) wc[0], (int) wc[1]),
					Draw.BOLD, u * 3, u * 7.6f, u * 2.2f, 0xFFE0E4EC, true);
			if (wp != null && mc.player != null) {
				double miles = (Route.get().size() >= 2 ? Route.lengthFrom(mc.player.getX(), mc.player.getZ()) : Math.hypot(wp[0] - mc.player.getX(), wp[1] - mc.player.getZ())) / 1609.34;
				Draw.text(g, String.format(Locale.ROOT, "Waypoint  %.2f mi", miles), Draw.BOLD, u * 3, u * 10.6f, u * 2.0f, 0xFFFF7BC4, true);
			}

			// legend
			float lw = u * 24, lx = W - lw - u * 3, ly = u * 3;
			List<Object[]> rows = new java.util.ArrayList<>();
			rows.add(new Object[] {"player", "You"});
			rows.add(new Object[] {"waypoint", "Waypoint"});
			for (Blips.Type t : Blips.Type.values()) if (present.contains(t)) rows.add(new Object[] {t.texture(), t.label});
			float rh = u * 3.4f;
			float lh = u * 4.6f + rows.size() * rh + u * 1.0f;
			Draw.roundRect(g, lx, ly, lw, lh, u * 1.0f, 0xC8101218);
			Draw.text(g, "LEGEND", Draw.BLACK, lx + u * 1.6f, ly + u * 1.4f, u * 2.0f, 0xFFFFFFFF, false);
			float ry = ly + u * 4.6f;
			for (Object[] r : rows) {
				Draw.iconCentered(g, (String) r[0], lx + u * 3f, ry + rh / 2 - u * 0.3f, u * 2.4f, 0, 0xFFFFFFFF);
				Draw.text(g, (String) r[1], Draw.BOLD, lx + u * 5.6f, ry + u * 0.4f, u * 1.9f, 0xFFE8E8EE, false);
				ry += rh;
			}

			// footer hints + scale bar
			String hint = "Drag  Pan     Scroll  Zoom     Right click  Set waypoint     Space  Center     M / Esc  Close";
			Draw.text(g, hint, Draw.BOLD, u * 3, H - u * 4.2f, u * 1.9f, 0xFFE0E4EC, true);
			double metersPer = bpp; // blocks per real pixel
			double barM = niceDistance(metersPer * u * 16);
			float barPx = (float) (barM / metersPer);
			float bx = W - u * 3 - barPx, by = H - u * 3.2f;
			Draw.rect(g, bx, by, barPx, Math.max(2, u * 0.3f), 0xFFFFFFFF);
			Draw.rect(g, bx, by - u * 0.8f, Math.max(2, u * 0.3f), u * 1.1f, 0xFFFFFFFF);
			Draw.rect(g, bx + barPx - Math.max(2, u * 0.3f), by - u * 0.8f, Math.max(2, u * 0.3f), u * 1.1f, 0xFFFFFFFF);
			String bl = barM >= 1609 ? String.format(Locale.ROOT, "%.1f mi", barM / 1609.34) : String.format(Locale.ROOT, "%d ft", (int) Math.round(barM * 3.28084));
			Draw.textRight(g, bl, Draw.BOLD, W - u * 3, by - u * 3.0f, u * 1.8f, 0xFFFFFFFF, true);

			// open fade
			float fa = 1 - Math.min(1, (HudState.now() - openedAt) / 200f);
			if (fa > 0) Draw.rect(g, 0, 0, W, H, Draw.argb(fa, 0x000000));
		} finally {
			Draw.end(g);
		}
	}

	static double niceDistance(double m) {
		double[] steps = {25, 50, 100, 200, 250, 500, 804.67, 1609.34, 3218.69, 8046.72, 16093.4};
		double best = steps[0];
		for (double s : steps) if (s <= m) best = s;
		return best;
	}

	/** World -> real screen pixels. */
	double[] toScreen(double wx, double wz) {
		return new double[] {(wx - cx) / bpp + Draw.W / 2.0, (wz - cz) / bpp + Draw.H / 2.0};
	}

	double[] toWorld(double sx, double sy) {
		return new double[] {cx + (sx - Draw.W / 2.0) * bpp, cz + (sy - Draw.H / 2.0) * bpp};
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (e.button() == 0) { dragging = true; return true; }
		if (e.button() == 1) {
			double[] w = toWorld(e.x() * gs(), e.y() * gs());
			double[] wp = Blips.waypoint();
			if (wp != null) {
				double[] s = toScreen(wp[0], wp[1]);
				if (Math.hypot(s[0] - e.x() * gs(), s[1] - e.y() * gs()) < Draw.U * 2) {
					Blips.clearWaypoint();
					return true;
				}
			}
			Blips.setWaypoint(w[0], w[1]);
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.4f);
			return true;
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (e.button() == 0) dragging = false;
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging) {
			cx -= dx * gs() * bpp;
			cz -= dy * gs() * bpp;
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		double[] before = toWorld(x * gs(), y * gs());
		bpp = Math.max(0.25, Math.min(24, bpp * Math.pow(0.8, sy)));
		double[] after = toWorld(x * gs(), y * gs());
		cx += before[0] - after[0];
		cz += before[1] - after[1];
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (HudClient.MAP_KEY.matches(e)) { onClose(); return true; }
		Minecraft mc = Minecraft.getInstance();
		switch (e.key()) {
			case InputConstants.KEY_SPACE -> { if (mc.player != null) { cx = mc.player.getX(); cz = mc.player.getZ(); } return true; }
			case InputConstants.KEY_EQUALS, InputConstants.KEY_ADD -> { bpp = Math.max(0.25, bpp * 0.8); return true; }
			case InputConstants.KEY_MINUS -> { bpp = Math.min(24, bpp / 0.8); return true; }
			case InputConstants.KEY_W, InputConstants.KEY_UP -> { cz -= 40 * bpp; return true; }
			case InputConstants.KEY_S, InputConstants.KEY_DOWN -> { cz += 40 * bpp; return true; }
			case InputConstants.KEY_A, InputConstants.KEY_LEFT -> { cx -= 40 * bpp; return true; }
			case InputConstants.KEY_D, InputConstants.KEY_RIGHT -> { cx += 40 * bpp; return true; }
			default -> { return super.keyPressed(e); }
		}
	}

	/** Test hook: set view. */
	public static void setView(double x, double z, double blocksPerPixel) {
		cx = x;
		cz = z;
		bpp = blocksPerPixel;
	}
}
