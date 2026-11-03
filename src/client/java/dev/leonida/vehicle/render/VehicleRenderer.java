package dev.leonida.vehicle.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.leonida.LeonidaMod;
import dev.leonida.vehicle.VehicleControls;
import dev.leonida.vehicle.VehicleEntity;
import dev.leonida.vehicle.VehicleSpec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws the procedural box models with lights, steering wheels, spinning props and an NPC driver figure. */
public class VehicleRenderer extends EntityRenderer<VehicleEntity, VehicleRenderState> {
	public static final Identifier ATLAS = LeonidaMod.id("textures/entity/vehicle/atlas.png");
	private static final int FULL_BRIGHT = 0xF000F0;
	private static final int[] SHIRTS = {0xE85D75, 0x4FB3BF, 0xF2B134, 0x7CC36B, 0xFFFFFF, 0x6A5ACD, 0xF07F3C, 0x3B3B3B};

	public VehicleRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.0f;
	}

	@Override
	public VehicleRenderState createRenderState() {
		return new VehicleRenderState();
	}

	@Override
	protected AABB getBoundingBoxForCulling(VehicleEntity entity, float partialTicks) {
		return entity.getInterpolatedBoundingBox(partialTicks).inflate(entity.spec.length() / 2, 0.5, entity.spec.length() / 2);
	}

	@Override
	public void extractRenderState(VehicleEntity e, VehicleRenderState s, float pt) {
		super.extractRenderState(e, s, pt);
		s.spec = e.spec;
		s.yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
		s.pitch = Mth.lerp(pt, e.bodyPitchO, e.bodyPitch);
		s.roll = Mth.lerp(pt, e.bodyRollO, e.bodyRoll);
		s.suspension = Mth.lerp(pt, e.suspensionO, e.suspension);
		s.doorL = e.doorAngle(-1, pt);
		s.doorR = e.doorAngle(1, pt);
		s.steer = Mth.lerp(pt, e.visualSteerO, e.visualSteer);
		s.wheelSpin = Mth.lerp(pt, e.wheelSpinO, e.wheelSpin);
		s.speed = e.clientSpeed;
		s.healthFraction = e.healthFraction();
		s.hurt = Math.max(0, e.getHurtTime() - pt);
		s.color = e.getColor();
		s.accent = dev.leonida.vehicle.VehicleTypes.accentFor(e.spec, s.color);
		s.sheriff = e.spec.police() && dev.leonida.vehicle.VehicleTypes.isSheriffLivery(s.color);
		s.rimTile = VehicleModels.get(e.spec).rimTile;
		s.controls = e.getControlBits();
		s.entityId = e.getId();
		s.wrecked = e.isWrecked();
		s.npcDriver = e.hasNpcDriver() && e.getPassengers().isEmpty();
		s.hasDriver = !e.getPassengers().isEmpty() || e.hasNpcDriver();
		s.windowBroken = e.flag(VehicleEntity.F_WINDOW_BROKEN);
		s.headlights = e.headlightsOn() && !s.wrecked;
		s.siren = e.isSirenOn() && !s.wrecked;
		s.alarm = e.flag(VehicleEntity.F_ALARM);
		boolean fwd = (s.controls & VehicleControls.FORWARD) != 0, back = (s.controls & VehicleControls.BACK) != 0;
		boolean hb = (s.controls & VehicleControls.HANDBRAKE) != 0;
		s.braking = !s.wrecked && (hb || (back && s.speed > 0.5f) || (fwd && s.speed < -0.5f) || (s.hasDriver && !fwd && !back && Math.abs(s.speed) < 0.3f));
		s.reversing = !s.wrecked && back && s.speed < 0.3f;
	}

	@Override
	public void submit(VehicleRenderState s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
		VehicleModel model = VehicleModels.get(s.spec);
		pose.pushPose();
		pose.rotateDegrees(Axis.YP, -s.yaw);
		pose.translate(0, s.suspension, 0);
		float pivotY = s.spec.height() * 0.4f;
		pose.translate(0, pivotY, 0);
		pose.rotateDegrees(Axis.XP, s.pitch);
		pose.rotateDegrees(Axis.ZP, s.roll);
		if (s.hurt > 0) pose.rotateDegrees(Axis.ZP, Mth.sin(s.ageInTicks * 2.2f) * s.hurt * 0.35f);
		pose.translate(0, -pivotY, 0);
		pose.scale(1 / 16f, 1 / 16f, 1 / 16f);

		out.submitCustomGeometry(pose, RenderTypes.entityCutout(ATLAS), (p, buf) -> emit(model, s, new Emitter(p, buf, s), false));
		out.submitCustomGeometry(pose, RenderTypes.entityTranslucent(ATLAS), (p, buf) -> emit(model, s, new Emitter(p, buf, s), true));
		if (s.headlights) {
			out.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(ATLAS), (p, buf) -> beams(model, new Emitter(p, buf, s)));
		}
		pose.popPose();
		super.submit(s, pose, out, camera);
	}

	// ------------------------------------------------------------------ emission

	/** Writes quads in model pixel space, mirroring x so that +x is the vehicle's right in the world. */
	static final class Emitter {
		final PoseStack.Pose pose;
		final VertexConsumer buf;
		final VehicleRenderState s;
		final float[] px = new float[4], py = new float[4], pz = new float[4];

		Emitter(PoseStack.Pose pose, VertexConsumer buf, VehicleRenderState s) {
			this.pose = pose;
			this.buf = buf;
			this.s = s;
		}

		/** Emits the quad stored in px/py/pz (semantic coords) with outward normal hint (nx,ny,nz). */
		void quad(float[] us, float[] vs, int argb, int light, float nx, float ny, float nz) {
			// mirror x
			float ax = -px[0], bx = -px[1], cx = -px[2];
			float e1x = bx - ax, e1y = py[1] - py[0], e1z = pz[1] - pz[0];
			float e2x = cx - bx, e2y = py[2] - py[1], e2z = pz[2] - pz[1];
			float cxn = e1y * e2z - e1z * e2y, cyn = e1z * e2x - e1x * e2z, czn = e1x * e2y - e1y * e2x;
			float mnx = -nx;
			boolean flip = cxn * mnx + cyn * ny + czn * nz < 0;
			for (int k = 0; k < 4; k++) {
				int i = flip ? 3 - k : k;
				buf.addVertex(pose, -px[i], py[i], pz[i]).setColor(argb).setUv(us[i], vs[i]).setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(light).setNormal(pose, mnx, ny, nz);
			}
		}
	}

	private static final float T = 1 / 8f;

	private static int scale(int rgb, float f) {
		int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
		int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
		int b = Math.min(255, (int) ((rgb & 255) * f));
		return (r << 16) | (g << 8) | b;
	}

	private void emit(VehicleModel m, VehicleRenderState s, Emitter em, boolean glassPass) {
		float steerRad = s.steer * s.spec.maxSteerDeg() * 0.75f * Mth.DEG_TO_RAD;
		float prop = s.ageInTicks * (0.6f + Math.min(1, Math.abs(s.speed) / 8f) * 1.2f) * (s.hasDriver ? 1 : 0);
		boolean flashOn = ((int) (s.ageInTicks / 3)) % 2 == 0;
		boolean blinkOn = ((int) (s.ageInTicks / 8)) % 2 == 0;
		for (VehicleModel.Box b : m.boxes) {
			boolean glass = b.mat.pass == Mat.Pass.GLASS;
			if (glass != glassPass) continue;
			if (b.group == VehicleModel.G_DRIVER_WINDOW && glass && s.windowBroken) continue;
			int color;
			int light = s.lightCoords;
			int alpha = glass ? 0x78 : 0xFF;
			color = switch (b.mat.color) {
				case -1 -> s.color;
				case -2 -> scale(s.color, 0.62f);
				case -3 -> SHIRTS[Math.floorMod(s.entityId, SHIRTS.length)];
				case -4 -> s.accent;
				default -> b.mat.color;
			};
			switch (b.mat.light) {
				case HEAD -> { if (s.headlights) light = FULL_BRIGHT; }
				case BRAKE -> {
					if (s.braking) { light = FULL_BRIGHT; color = 0xFFFFFF; }
					else if (s.headlights) { light = FULL_BRIGHT; color = 0xB0B0B0; }
					else color = 0xA0A0A0;
				}
				case REVERSE -> { if (s.reversing) { light = FULL_BRIGHT; color = 0xFFFFFF; } }
				case INDICATOR -> { if (s.alarm && blinkOn) { light = FULL_BRIGHT; color = 0xFFFFFF; } }
				case SIREN_RED -> {
					if (s.siren && flashOn) { light = FULL_BRIGHT; color = 0xFF2020; } else color = scale(color, 0.55f);
				}
				case SIREN_BLUE -> {
					if (s.siren && !flashOn) { light = FULL_BRIGHT; color = 0x2850FF; } else color = scale(color, 0.55f);
				}
				default -> {}
			}
			if (s.wrecked) {
				if (b.mat.color == -1 || b.mat.color == -2 || b.mat.color == -4) color = 0x2E2B28;
				color = scale(color, 0.45f);
				light = s.lightCoords;
			}
			box(em, b, s, color | (alpha << 24), light, steerRad, prop, m);
		}
		if (!glassPass) {
			for (VehicleModel.Wheel w : m.wheels) wheel(em, w, s, w.steers() ? steerRad : 0);
			if (s.npcDriver) driver(em, m, s, steerRad);
		}
	}

	/** Transforms a point of a box by its own rotation and its group's animation (semantic px space). */
	private static void transform(float[] p, VehicleModel.Box b, VehicleRenderState s, float steerRad, float prop, VehicleModel m) {
		float x = p[0], y = p[1], z = p[2];
		if (b.pitch != 0 || b.roll != 0) {
			float cx = (b.x0 + b.x1) / 2, cy = (b.y0 + b.y1) / 2, cz = (b.z0 + b.z1) / 2;
			float dx = x - cx, dy = y - cy, dz = z - cz;
			if (b.pitch != 0) {
				float a = b.pitch * Mth.DEG_TO_RAD, c = Mth.cos(a), sn = Mth.sin(a);
				float ny = dy * c - dz * sn, nz = dy * sn + dz * c;
				dy = ny;
				dz = nz;
			}
			if (b.roll != 0) {
				float a = b.roll * Mth.DEG_TO_RAD, c = Mth.cos(a), sn = Mth.sin(a);
				float nx = dx * c - dy * sn, ny = dx * sn + dy * c;
				dx = nx;
				dy = ny;
			}
			x = cx + dx;
			y = cy + dy;
			z = cz + dz;
		}
		if (b.door != 0) {
			float deg = b.door < 0 ? s.doorL : s.doorR;
			if (deg != 0) {
				// swing about the vertical hinge at the door's front edge, rear edge outwards
				float a = deg * Mth.DEG_TO_RAD, c = Mth.cos(a), sn = Mth.sin(a) * b.door;
				float hx = b.door * m.doorHingeX, dx = x - hx, dz = z - m.doorZ1;
				x = hx + dx * c - dz * sn;
				z = m.doorZ1 + dx * sn + dz * c;
			}
		}
		if (b.group == VehicleModel.G_STEER && steerRad != 0) {
			float dx = x, dz = z - m.steerPivotZ;
			float c = Mth.cos(steerRad), sn = Mth.sin(steerRad);
			x = dx * c + dz * sn;
			z = m.steerPivotZ - dx * sn + dz * c;
		} else if (b.group == VehicleModel.G_PROP) {
			float dx = x, dy = y - m.propY;
			float c = Mth.cos(prop), sn = Mth.sin(prop);
			x = dx * c - dy * sn;
			y = m.propY + dx * sn + dy * c;
		}
		p[0] = x;
		p[1] = y;
		p[2] = z;
	}

	private static final int[][] FACES = {
		// corners as (xIdx, yIdx, zIdx) for TL, TR, BR, BL seen from outside; 0 = min, 1 = max
		{0, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1},   // -x left side
		{1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0},   // +x right side
		{0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},   // -y bottom
		{0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0},   // +y top
		{0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0},   // -z back
		{1, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1},   // +z front
	};
	private static final float[][] NORMALS = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};

	private void box(Emitter em, VehicleModel.Box b, VehicleRenderState s, int argb, int light, float steerRad, float prop, VehicleModel m) {
		float[] xs = {b.x0, b.x1}, ys = {b.y0, b.y1}, zs = {b.z0, b.z1};
		float[] pt = new float[3];
		float[] us = new float[4], vs = new float[4];
		boolean animated = b.pitch != 0 || b.roll != 0 || b.group == VehicleModel.G_STEER || b.group == VehicleModel.G_PROP
				|| (b.door < 0 ? s.doorL != 0 : b.door > 0 && s.doorR != 0);
		for (int f = 0; f < 6; f++) {
			if ((b.faces & (1 << f)) == 0) continue;
			int[] c = FACES[f];
			for (int k = 0; k < 4; k++) {
				pt[0] = xs[c[k * 3]];
				pt[1] = ys[c[k * 3 + 1]];
				pt[2] = zs[c[k * 3 + 2]];
				if (animated) transform(pt, b, s, steerRad, prop, m);
				em.px[k] = pt[0];
				em.py[k] = pt[1];
				em.pz[k] = pt[2];
			}
			int tile = b.mat.tileFor(f);
			if (s.sheriff && (tile == 24 || tile == 37)) tile++;
			float u0 = (tile % 8) * T, v0 = (tile / 8) * T;
			float in = 0.0005f;
			us[0] = u0 + in; vs[0] = v0 + in;
			us[1] = u0 + T - in; vs[1] = v0 + in;
			us[2] = u0 + T - in; vs[2] = v0 + T - in;
			us[3] = u0 + in; vs[3] = v0 + T - in;
			float[] n = NORMALS[f];
			float nx = n[0], ny = n[1], nz = n[2];
			if (animated) {
				float[] np = {n[0] + (b.x0 + b.x1) / 2, n[1] + (b.y0 + b.y1) / 2, n[2] + (b.z0 + b.z1) / 2};
				float[] cp = {(b.x0 + b.x1) / 2, (b.y0 + b.y1) / 2, (b.z0 + b.z1) / 2};
				transform(np, b, s, steerRad, prop, m);
				transform(cp, b, s, steerRad, prop, m);
				nx = np[0] - cp[0];
				ny = np[1] - cp[1];
				nz = np[2] - cp[2];
			}
			em.quad(us, vs, argb, light, nx, ny, nz);
		}
	}

	private void wheel(Emitter em, VehicleModel.Wheel w, VehicleRenderState s, float steer) {
		int n = 12;
		float r = w.radius(), hw = w.width() / 2;
		float spin = s.wheelSpin;
		int color = s.wrecked ? 0x404040 : 0xFFFFFF;
		int argb = 0xFF000000 | color;
		float cs = Mth.cos(steer), sn = Mth.sin(steer);
		float[] us = new float[4], vs = new float[4];
		int rim = s.rimTile;
		float sideU = (rim % 8) * T, sideV = (rim / 8) * T, treadU = (3 % 8) * T, treadV = (3 / 8) * T;
		for (int i = 0; i < n; i++) {
			float a0 = spin + i * Mth.TWO_PI / n, a1 = spin + (i + 1) * Mth.TWO_PI / n;
			float y0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, y1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
			// tread
			setW(em, 0, -hw, y0, z0, w, cs, sn);
			setW(em, 1, hw, y0, z0, w, cs, sn);
			setW(em, 2, hw, y1, z1, w, cs, sn);
			setW(em, 3, -hw, y1, z1, w, cs, sn);
			float tv0 = treadV + T * i / n, tv1 = treadV + T * (i + 1) / n;
			us[0] = treadU; vs[0] = tv0; us[1] = treadU + T; vs[1] = tv0; us[2] = treadU + T; vs[2] = tv1; us[3] = treadU; vs[3] = tv1;
			float mny = Mth.cos((a0 + a1) / 2), mnz = Mth.sin((a0 + a1) / 2);
			em.quad(us, vs, argb, s.lightCoords, mnz * sn, mny, mnz * cs);
			// sides as degenerate quads (centre, p0, p1, p1)
			float relA0 = i * Mth.TWO_PI / n, relA1 = (i + 1) * Mth.TWO_PI / n;
			for (int side = -1; side <= 1; side += 2) {
				float x = side * hw;
				setW(em, 0, x, 0, 0, w, cs, sn);
				setW(em, 1, x, y0, z0, w, cs, sn);
				setW(em, 2, x, y1, z1, w, cs, sn);
				setW(em, 3, x, y1, z1, w, cs, sn);
				float cu = sideU + T / 2, cv = sideV + T / 2, h = T / 2 * 0.98f;
				float sgn = side;
				us[0] = cu; vs[0] = cv;
				us[1] = cu + sgn * Mth.sin(relA0) * h; vs[1] = cv - Mth.cos(relA0) * h;
				us[2] = cu + sgn * Mth.sin(relA1) * h; vs[2] = cv - Mth.cos(relA1) * h;
				us[3] = us[2]; vs[3] = vs[2];
				em.quad(us, vs, argb, s.lightCoords, side * cs, 0, -side * sn);
			}
		}
	}

	/** Wheel-local point (x across, y, z along) to model space with steering about the wheel's vertical axis. */
	private static void setW(Emitter em, int k, float x, float y, float z, VehicleModel.Wheel w, float cs, float sn) {
		em.px[k] = w.x() + x * cs + z * sn;
		em.py[k] = w.y() + y;
		em.pz[k] = w.z() - x * sn + z * cs;
	}

	/** Simple seated figure at the driver seat for AI-driven cars without a real passenger entity. */
	private void driver(Emitter em, VehicleModel m, VehicleRenderState s, float steerRad) {
		if (s.spec.seats().isEmpty()) return;
		Vec3 seat = s.spec.seats().get(0);
		float x = (float) seat.x * 16, z = (float) seat.z * 16;
		float hip = (float) seat.y * 16 + 2.4f;
		int light = s.lightCoords;
		int shirt = SHIRTS[Math.floorMod(s.entityId, SHIRTS.length)];
		figBox(em, Mat.SHIRT, x - 3.8f, hip, z - 2, x + 3.8f, hip + 11, z + 1.8f, 0xFF000000 | shirt, light, 0);
		figBox(em, Mat.FACE, x - 3.6f, hip + 11, z - 3.4f, x + 3.6f, hip + 18.2f, z + 3.8f, 0xFFFFFFFF, light, 0);
		figBox(em, Mat.HAIR, x - 3.75f, hip + 15.6f, z - 3.55f, x + 3.75f, hip + 18.4f, z + 2.8f, 0xFFFFFFFF, light, 0);
		// arms reaching for the wheel or bars
		float armZ = m.bikeRider ? 9 : 6.5f;
		figBox(em, Mat.SHIRT, x - 6, hip + 7, z - 0.5f, x - 3.8f, hip + 9.2f, z + armZ, 0xFF000000 | shirt, light, 0);
		figBox(em, Mat.SHIRT, x + 3.8f, hip + 7, z - 0.5f, x + 6, hip + 9.2f, z + armZ, 0xFF000000 | shirt, light, 0);
		if (m.bikeRider) {
			figBox(em, Mat.PANTS, x - 4, hip - 1, z - 1, x - 0.2f, hip + 2.6f, z + 7, 0xFF2D3A55, light, 0);
			figBox(em, Mat.PANTS, x + 0.2f, hip - 1, z - 1, x + 4, hip + 2.6f, z + 7, 0xFF2D3A55, light, 0);
			figBox(em, Mat.PANTS, x - 4.4f, hip - 9, z + 4, x - 0.6f, hip + 1, z + 7.2f, 0xFF2D3A55, light, 0);
			figBox(em, Mat.PANTS, x + 0.6f, hip - 9, z + 4, x + 4.4f, hip + 1, z + 7.2f, 0xFF2D3A55, light, 0);
		}
	}

	private void figBox(Emitter em, Mat mat, float x0, float y0, float z0, float x1, float y1, float z1, int argb, int light, int group) {
		VehicleModel.Box b = new VehicleModel.Box(mat, x0, y0, z0, x1, y1, z1);
		box(em, b, em.s, argb, light, 0, 0, null);
	}

	/** Soft additive light cones in front of the headlights. */
	private void beams(VehicleModel m, Emitter em) {
		float u = 0.06f, v = 0.06f;
		float[] us = {u, u, u, u}, vs = {v, v, v, v};
		java.util.List<float[]> origins = m.beams;
		if (origins.isEmpty()) {
			origins = new java.util.ArrayList<>();
			for (VehicleModel.Box b : m.boxes) if (b.mat == Mat.HEADLIGHT) origins.add(new float[] {(b.x0 + b.x1) / 2, (b.y0 + b.y1) / 2, b.z1});
		}
		for (float[] o : origins) {
			float cx = o[0], cy = o[1], z0 = o[2];
			float len = 150, spread = 30, drop = 14;
			int near = 0x40FFF4D8, far = 0x00FFF4D8;
			// horizontal sheet and vertical sheet, both sides
			for (int pass = 0; pass < 2; pass++) {
				for (int side = 0; side < 2; side++) {
					if (pass == 0) {
						em.px[0] = cx - 1.2f; em.py[0] = cy; em.pz[0] = z0;
						em.px[1] = cx + 1.2f; em.py[1] = cy; em.pz[1] = z0;
						em.px[2] = cx + spread; em.py[2] = cy - drop; em.pz[2] = z0 + len;
						em.px[3] = cx - spread; em.py[3] = cy - drop; em.pz[3] = z0 + len;
					} else {
						em.px[0] = cx; em.py[0] = cy + 1; em.pz[0] = z0;
						em.px[1] = cx; em.py[1] = cy - 1; em.pz[1] = z0;
						em.px[2] = cx; em.py[2] = cy - drop - 10; em.pz[2] = z0 + len;
						em.px[3] = cx; em.py[3] = cy - drop + 10; em.pz[3] = z0 + len;
					}
					float ny = pass == 0 ? (side == 0 ? 1 : -1) : 0, nx = pass == 1 ? (side == 0 ? 1 : -1) : 0;
					emitGradient(em, us, vs, near, far, nx, ny, 0);
				}
			}
		}
	}

	private static void emitGradient(Emitter em, float[] us, float[] vs, int near, int far, float nx, float ny, float nz) {
		// two near vertices then two far vertices, emitted in both windings so it is visible from any side
		for (int w = 0; w < 2; w++) {
			for (int k = 0; k < 4; k++) {
				int i = w == 0 ? k : 3 - k;
				int col = i < 2 ? near : far;
				em.buf.addVertex(em.pose, -em.px[i], em.py[i], em.pz[i]).setColor(col).setUv(us[i], vs[i])
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(em.pose, -nx, ny, nz);
			}
		}
	}

	static VehicleSpec specOf(VehicleRenderState s) { return s.spec; }
}
