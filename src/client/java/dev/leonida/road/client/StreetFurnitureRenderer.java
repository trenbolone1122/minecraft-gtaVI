package dev.leonida.road.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.leonida.road.api.RoadLanes.Light;
import dev.leonida.road.api.Signals;
import dev.leonida.road.net.Props;
import dev.leonida.road.net.Props.Pole;
import dev.leonida.road.net.RoadNetwork;
import dev.leonida.world.WorldModule;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the parts of the street furniture that do not fit the block grid, at their exact angles:
 * streetlight arms with cobra heads, Florida mast arms with two signal heads each (lit from
 * {@link Signals}), and sagging power lines between utility poles. The poles themselves are blocks.
 */
public final class StreetFurnitureRenderer {
	private StreetFurnitureRenderer() {}

	public static final Identifier WHITE = Identifier.fromNamespaceAndPath("leonida", "textures/road/white.png");
	private static final int RANGE = 128;
	private static final int FULL = 0xF000F0;

	private static final int STEEL = 0xFF9A9EA2, HEAD = 0xFF5E6266, LENS_ON = 0xFFFFF4D6, LENS_OFF = 0xFFB8B4A6;
	private static final int SIGNAL_BODY = 0xFFC9A400, VISOR = 0xFF1C1C1C, WIRE = 0xFF202020;
	private static final int RED_ON = 0xFFFF2A1A, YELLOW_ON = 0xFFFFC21A, GREEN_ON = 0xFF2CFF7A;
	private static final int RED_OFF = 0xFF3A1210, YELLOW_OFF = 0xFF3A2E10, GREEN_OFF = 0xFF0F3320;

	public static void submit(SubmitNodeCollector collector, PoseStack ps, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || level.dimension() != WorldModule.LEONIDA) return;
		Props props = Props.peek();
		if (props == null) return;
		int cx = (int) Math.floor(cam.x), cz = (int) Math.floor(cam.z);
		List<Pole> poles = props.in(cx - RANGE, cz - RANGE, cx + RANGE, cz + RANGE);
		if (poles.isEmpty()) return;
		long time = level.getGameTime();
		boolean night = level.getSkyDarken() >= 4 || level.isThundering();
		collector.submitCustomGeometry(ps, RenderTypes.entitySolid(WHITE), (pose, vc) -> {
			Draw d = new Draw(pose, vc, cam);
			BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
			for (Pole p : poles) {
				double dx = p.x - cam.x, dz = p.z - cam.z;
				if (dx * dx + dz * dz > RANGE * RANGE) continue;
				bp.set(p.bx(), (int) p.topY(), p.bz());
				d.light = LightCoordsUtil.getLightCoords(level, bp);
				switch (p.kind) {
					case LAMP -> lamp(d, p, p.armAngle, false, night);
					case MEDIAN_LAMP -> {
						lamp(d, p, p.armAngle, true, night);
						lamp(d, p, p.armAngle + Math.PI, true, night);
					}
					case SIGNAL -> signal(d, p, time);
					case POWER -> power(d, p, night);
				}
			}
		});
	}

	/** Davit arm rising slightly from the pole top, ending in a flat cobra head with a glowing lens. */
	private static void lamp(Draw d, Pole p, double ang, boolean highway, boolean night) {
		double c = Math.cos(ang), s = Math.sin(ang);
		double top = p.topY() - 0.15;
		double len = p.armLen;
		double px = poleX(p), pz = poleZ(p);
		double ex = px + c * len, ez = pz + s * len;
		// pole cap and arm (two pieces: a short upsweep then the level arm)
		d.beam(px, top - 0.6, pz, px + c * 0.6, top, pz + s * 0.6, 0.09, STEEL);
		d.beam(px + c * 0.6, top, pz + s * 0.6, ex, top + 0.15, ez, 0.08, STEEL);
		// cobra head
		double hx = ex + c * 0.3, hz = ez + s * 0.3, hy = top + 0.1;
		d.box(hx, hy, hz, c, s, 0.75, highway ? 0.42 : 0.36, 0.2, HEAD);
		int lens = night ? LENS_ON : LENS_OFF;
		d.flatDown(hx, hy - 0.101, hz, c, s, 0.55, 0.28, lens, night ? FULL : d.light);
	}

	/** Florida mast arm: a long tapered steel arm over the approach with two signal heads facing the traffic. */
	private static void signal(Draw d, Pole p, long time) {
		double c = Math.cos(p.armAngle), s = Math.sin(p.armAngle);
		double px = poleX(p), pz = poleZ(p);
		double y = p.topY() - 0.6;
		double ex = px + c * p.armLen, ez = pz + s * p.armLen;
		d.beam(px, y, pz, px + c * p.armLen * 0.5, y + 0.08, pz + s * p.armLen * 0.5, 0.17, STEEL);
		d.beam(px + c * p.armLen * 0.5, y + 0.08, pz + s * p.armLen * 0.5, ex, y + 0.25, ez, 0.11, STEEL);
		// diagonal tie from the pole up to the arm
		d.beam(px, y + 1.4, pz, px + c * p.armLen * 0.35, y + 0.06, pz + s * p.armLen * 0.35, 0.04, STEEL);
		Light light = Light.RED;
		RoadNetwork net = RoadNetwork.peek();
		if (net != null && p.node >= 0 && p.node < net.nodes.size()) light = Signals.light(net.nodes.get(p.node), p.mainAxis, time);
		double fc = Math.cos(p.faceAngle), fs = Math.sin(p.faceAngle);
		if (p.heads == null) return;
		for (double h : p.heads) {
			double t = Math.min(h, p.armLen) / p.armLen;
			double hx = px + c * h, hz = pz + s * h, hy = y + 0.25 * t - 0.15;
			head(d, hx, hy, hz, fc, fs, light);
		}
	}

	/** Three-light vertical head hanging below the arm, face pointing along (fc, fs). */
	private static void head(Draw d, double x, double topY, double z, double fc, double fs, Light light) {
		double h = 1.05, cy = topY - h / 2;
		d.beam(x, topY + 0.12, z, x, topY, z, 0.05, STEEL);
		// body: depth along the face direction, width across
		d.box(x, cy, z, fc, fs, 0.32, 0.36, h, SIGNAL_BODY);
		double[] off = {0.33, 0, -0.33};
		int[] on = {RED_ON, YELLOW_ON, GREEN_ON};
		int[] offc = {RED_OFF, YELLOW_OFF, GREEN_OFF};
		Light[] which = {Light.RED, Light.YELLOW, Light.GREEN};
		double fx = x + fc * 0.161, fz = z + fs * 0.161;
		for (int i = 0; i < 3; i++) {
			boolean lit = light == which[i];
			d.face(fx, cy + off[i], fz, fc, fs, 0.24, 0.24, lit ? on[i] : offc[i], lit ? FULL : d.light);
			// visor
			d.box(fx + fc * 0.09, cy + off[i] + 0.14, fz + fs * 0.09, fc, fs, 0.18, 0.28, 0.03, VISOR);
		}
	}

	/** Three conductors sagging to the next pole, plus a cobra head on lit poles. */
	private static void power(Draw d, Pole p, boolean night) {
		double c = Math.cos(p.armAngle), s = Math.sin(p.armAngle);
		if (p.lamp) {
			Pole q = new Pole();
			q.x = p.x;
			q.z = p.z;
			q.baseY = p.baseY;
			q.height = p.height - 3;
			q.armLen = 2.0;
			lamp(d, q, p.armAngle, false, night);
		}
		Pole n = p.next;
		if (n == null) return;
		double nc = Math.cos(n.armAngle), ns = Math.sin(n.armAngle);
		double y0 = p.topY() - 0.35, y1 = n.topY() - 0.35;
		double ax = poleX(p), az = poleZ(p), bx = poleX(n), bz = poleZ(n);
		double len = Math.hypot(bx - ax, bz - az);
		if (len < 1 || len > 90) return;
		double sag = 0.025 * len;
		for (double o : new double[] {-0.85, 0, 0.85}) {
			double sx = ax + c * o, sz = az + s * o, tx = bx + nc * o, tz = bz + ns * o;
			double yo = o == 0 ? 0.35 : 0;
			int steps = 10;
			double lx = sx, ly = y0 + yo, lz = sz;
			for (int i = 1; i <= steps; i++) {
				double t = i / (double) steps;
				double x = sx + (tx - sx) * t, z = sz + (tz - sz) * t;
				double y = y0 + yo + (y1 - y0) * t - sag * 4 * t * (1 - t);
				d.beam(lx, ly, lz, x, y, z, 0.025, WIRE);
				lx = x;
				ly = y;
				lz = z;
			}
		}
	}

	private static double poleX(Pole p) {
		return p.bx() + 0.5;
	}

	private static double poleZ(Pole p) {
		return p.bz() + 0.5;
	}

	/** Small immediate-mode helper for coloured boxes and beams in camera space. */
	static final class Draw {
		final PoseStack.Pose pose;
		final VertexConsumer vc;
		final Vec3 cam;
		int light;

		Draw(PoseStack.Pose pose, VertexConsumer vc, Vec3 cam) {
			this.pose = pose;
			this.vc = vc;
			this.cam = cam;
		}

		/** Square beam of half width r from a to b. */
		void beam(double ax, double ay, double az, double bx, double by, double bz, double r, int color) {
			double dx = bx - ax, dy = by - ay, dz = bz - az;
			double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (l < 1e-4) return;
			dx /= l;
			dy /= l;
			dz /= l;
			// first perpendicular: horizontal if possible
			double ux, uy, uz;
			if (Math.abs(dy) > 0.95) {
				ux = 1;
				uy = 0;
				uz = 0;
			} else {
				double h = Math.hypot(dx, dz);
				ux = -dz / h;
				uy = 0;
				uz = dx / h;
			}
			double vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
			prism(ax, ay, az, bx, by, bz, ux * r, uy * r, uz * r, vx * r, vy * r, vz * r, color, light);
		}

		/** Horizontal box centred at (x, y, z), length along (c, s), width across, height h. */
		void box(double x, double y, double z, double c, double s, double len, double w, double h, int color) {
			double hl = len / 2;
			double ux = -s * w / 2, uz = c * w / 2;
			prism(x - c * hl, y, z - s * hl, x + c * hl, y, z + s * hl, ux, 0, uz, 0, h / 2, 0, color, light);
		}

		/** Downward facing quad (lamp lens). */
		void flatDown(double x, double y, double z, double c, double s, double len, double w, int color, int lt) {
			double ax = c * len / 2, az = s * len / 2, bx = -s * w / 2, bz = c * w / 2;
			quad(x - ax - bx, y, z - az - bz, x - ax + bx, y, z - az + bz, x + ax + bx, y, z + az + bz, x + ax - bx, y, z + az - bz,
					0, -1, 0, color, lt);
		}

		/** Vertical quad facing (c, s). */
		void face(double x, double y, double z, double c, double s, double w, double h, int color, int lt) {
			double rx = -s * w / 2, rz = c * w / 2, hh = h / 2;
			quad(x - rx, y - hh, z - rz, x + rx, y - hh, z + rz, x + rx, y + hh, z + rz, x - rx, y + hh, z - rz,
					(float) c, 0, (float) s, color, lt);
		}

		/** Box from a to b with half extents u and v around the axis. */
		void prism(double ax, double ay, double az, double bx, double by, double bz, double ux, double uy, double uz,
				double vx, double vy, double vz, int color, int lt) {
			double[][] a = new double[4][], b = new double[4][];
			int[][] sg = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
			for (int i = 0; i < 4; i++) {
				double su = sg[i][0], sv = sg[i][1];
				a[i] = new double[] {ax + ux * su + vx * sv, ay + uy * su + vy * sv, az + uz * su + vz * sv};
				b[i] = new double[] {bx + ux * su + vx * sv, by + uy * su + vy * sv, bz + uz * su + vz * sv};
			}
			for (int i = 0; i < 4; i++) {
				int j = (i + 1) % 4;
				double nx = (a[i][0] + a[j][0]) / 2 - ax, ny = (a[i][1] + a[j][1]) / 2 - ay, nz = (a[i][2] + a[j][2]) / 2 - az;
				double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (nl > 0) {
					nx /= nl;
					ny /= nl;
					nz /= nl;
				}
				quad(a[i], a[j], b[j], b[i], nx, ny, nz, color, lt);
			}
			double ex = bx - ax, ey = by - ay, ez = bz - az, el = Math.sqrt(ex * ex + ey * ey + ez * ez);
			quad(a[3], a[2], a[1], a[0], -ex / el, -ey / el, -ez / el, color, lt);
			quad(b[0], b[1], b[2], b[3], ex / el, ey / el, ez / el, color, lt);
		}

		private void quad(double[] p0, double[] p1, double[] p2, double[] p3, double nx, double ny, double nz, int color, int lt) {
			quad(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], p3[0], p3[1], p3[2], nx, ny, nz, color, lt);
		}

		void quad(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double x3,
				double y3, double z3, double nx, double ny, double nz, int color, int lt) {
			// solid render type culls back faces: emit both windings so orientation never hides a face
			v(x0, y0, z0, 0, 0, nx, ny, nz, color, lt);
			v(x1, y1, z1, 1, 0, nx, ny, nz, color, lt);
			v(x2, y2, z2, 1, 1, nx, ny, nz, color, lt);
			v(x3, y3, z3, 0, 1, nx, ny, nz, color, lt);
			v(x3, y3, z3, 0, 1, nx, ny, nz, color, lt);
			v(x2, y2, z2, 1, 1, nx, ny, nz, color, lt);
			v(x1, y1, z1, 1, 0, nx, ny, nz, color, lt);
			v(x0, y0, z0, 0, 0, nx, ny, nz, color, lt);
		}

		private void v(double x, double y, double z, float u, float vv, double nx, double ny, double nz, int color, int lt) {
			vc.addVertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
					.setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, 255).setUv(u, vv)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(lt).setNormal(pose, (float) nx, (float) ny, (float) nz);
		}
	}
}
