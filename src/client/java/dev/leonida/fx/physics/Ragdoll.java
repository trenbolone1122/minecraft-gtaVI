package dev.leonida.fx.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Humanoid ragdoll solved with position based (Verlet) dynamics: nine particles (shoulders, hips, head top,
 * hands, feet), hard distance constraints that keep the torso and limbs rigid, range constraints that act as joint
 * limits, and sphere collisions against the block world. Six bones (head, body, both arms, both legs) are derived
 * from the particles each frame.
 */
public final class Ragdoll {
	public static final int SR = 0, SL = 1, HR = 2, HL = 3, HEAD = 4, HAND_R = 5, HAND_L = 6, FOOT_R = 7, FOOT_L = 8;
	public static final int N = 9;
	public static final int BONE_HEAD = 0, BONE_BODY = 1, BONE_RARM = 2, BONE_LARM = 3, BONE_RLEG = 4, BONE_LLEG = 5;
	public static final int SUBSTEPS = 4;
	private static final float DT = 0.05F / SUBSTEPS;
	private static final int ITERATIONS = 6;

	final double[] pos = new double[N * 3];
	final double[] prev = new double[N * 3];
	/** Positions at the start of the current tick, for render interpolation. */
	final double[] tickStart = new double[N * 3];
	final float[] radius = new float[N];
	private final List<float[]> constraints = new ArrayList<>(); // a, b, min, max, stiffness
	private final Collider collider = new Collider();
	private final Collider.Hit hit = new Collider.Hit();

	/** Rest particle positions and rest bone frames (world space at spawn). */
	final Vector3d[] rest = new Vector3d[N];
	final Vector3d neck0 = new Vector3d();
	final Matrix3f[] restFrame = new Matrix3f[6];
	final Vector3d[] restAnchor = new Vector3d[6];

	public int age;
	public final int life;
	public int sleepTicks;

	/**
	 * @param pts  rest positions (world) of the nine particles, see the index constants
	 * @param neck rest position of the neck (head pivot)
	 * @param scale body scale, 1 for a player sized body
	 */
	public Ragdoll(Vector3d[] pts, Vector3d neck, float scale, int life) {
		this.life = life;
		for (int i = 0; i < N; i++) {
			rest[i] = new Vector3d(pts[i]);
			pos[i * 3] = prev[i * 3] = tickStart[i * 3] = pts[i].x;
			pos[i * 3 + 1] = prev[i * 3 + 1] = tickStart[i * 3 + 1] = pts[i].y;
			pos[i * 3 + 2] = prev[i * 3 + 2] = tickStart[i * 3 + 2] = pts[i].z;
		}
		neck0.set(neck);
		float rt = 0.13F * scale, rl = 0.11F * scale;
		radius[SR] = radius[SL] = radius[HR] = radius[HL] = rt;
		radius[HEAD] = 0.16F * scale;
		radius[HAND_R] = radius[HAND_L] = radius[FOOT_R] = radius[FOOT_L] = rl;

		// torso rigid box
		rigid(SR, SL);
		rigid(HR, HL);
		rigid(SR, HR);
		rigid(SL, HL);
		rigid(SR, HL);
		rigid(SL, HR);
		// head held by the shoulders, a little nodding allowed via the hips
		rigid(HEAD, SR);
		rigid(HEAD, SL);
		range(HEAD, HR, 0.88F, 1.05F, 0.6F);
		range(HEAD, HL, 0.88F, 1.05F, 0.6F);
		// limbs
		rigid(HAND_R, SR);
		rigid(HAND_L, SL);
		rigid(FOOT_R, HR);
		rigid(FOOT_L, HL);
		// joint limits: arms cannot pass through the chest, legs cannot split or fold all the way up
		range(HAND_R, SL, 0.75F, 10F, 0.5F);
		range(HAND_L, SR, 0.75F, 10F, 0.5F);
		range(FOOT_R, HL, 0.8F, 1.35F, 0.5F);
		range(FOOT_L, HR, 0.8F, 1.35F, 0.5F);
		range(FOOT_R, SR, 0.55F, 10F, 0.5F);
		range(FOOT_L, SL, 0.55F, 10F, 0.5F);
		range(FOOT_R, FOOT_L, 0.3F, 10F, 0.3F);

		for (int b = 0; b < 6; b++) {
			restFrame[b] = new Matrix3f();
			restAnchor[b] = new Vector3d();
		}
		computeFrames(pos, restFrame, restAnchor, null);
	}

	private void rigid(int a, int b) {
		range(a, b, 1.0F, 1.0F, 1.0F);
	}

	private void range(int a, int b, float min, float max, float stiffness) {
		double d = rest[a].distance(rest[b]);
		constraints.add(new float[] {a, b, (float) (d * min), (float) (d * max), stiffness});
	}

	/** Adds a velocity (blocks per second) to every particle, with an optional bias to the upper body. */
	public void addVelocity(double vx, double vy, double vz, double upperBias, Random random) {
		for (int i = 0; i < N; i++) {
			double bias = (i == HEAD || i == SR || i == SL || i == HAND_R || i == HAND_L) ? 1.0 + upperBias : 1.0 - upperBias * 0.5;
			double jx = (random.nextDouble() - 0.5) * 0.6, jz = (random.nextDouble() - 0.5) * 0.6;
			prev[i * 3] -= (vx * bias + jx) * DT;
			prev[i * 3 + 1] -= vy * bias * DT;
			prev[i * 3 + 2] -= (vz * bias + jz) * DT;
		}
	}

	public boolean tick(Level level) {
		age++;
		System.arraycopy(pos, 0, tickStart, 0, pos.length);
		if (sleepTicks > 40) return age < life;
		collider.begin(level);
		collider.invalidate();
		double moved = 0;
		for (int s = 0; s < SUBSTEPS; s++) {
			for (int i = 0; i < N; i++) {
				int k = i * 3;
				double vx = (pos[k] - prev[k]) * 0.995, vy = (pos[k + 1] - prev[k + 1]) * 0.995, vz = (pos[k + 2] - prev[k + 2]) * 0.995;
				prev[k] = pos[k];
				prev[k + 1] = pos[k + 1];
				prev[k + 2] = pos[k + 2];
				pos[k] += vx;
				pos[k + 1] += vy - DebrisWorld.GRAVITY * DT * DT;
				pos[k + 2] += vz;
			}
			for (int it = 0; it < ITERATIONS; it++) {
				for (float[] c : constraints) solve(c);
				collide();
			}
		}
		for (int i = 0; i < pos.length; i++) moved = Math.max(moved, Math.abs(pos[i] - tickStart[i]));
		if (moved < 0.004) sleepTicks++;
		else sleepTicks = 0;
		return age < life;
	}

	private void solve(float[] c) {
		int a = (int) c[0] * 3, b = (int) c[1] * 3;
		double dx = pos[b] - pos[a], dy = pos[b + 1] - pos[a + 1], dz = pos[b + 2] - pos[a + 2];
		double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (d < 1.0E-9) return;
		double target = d < c[2] ? c[2] : d > c[3] ? c[3] : d;
		if (target == d) return;
		double diff = (d - target) / d * 0.5 * c[4];
		pos[a] += dx * diff;
		pos[a + 1] += dy * diff;
		pos[a + 2] += dz * diff;
		pos[b] -= dx * diff;
		pos[b + 1] -= dy * diff;
		pos[b + 2] -= dz * diff;
	}

	private void collide() {
		for (int i = 0; i < N; i++) {
			int k = i * 3;
			if (!collider.sphereContact(pos[k], pos[k + 1], pos[k + 2], radius[i], hit)) continue;
			pos[k] += hit.nx * hit.depth;
			pos[k + 1] += hit.ny * hit.depth;
			pos[k + 2] += hit.nz * hit.depth;
			// friction and a little bounce, through the implicit velocity
			double vx = pos[k] - prev[k], vy = pos[k + 1] - prev[k + 1], vz = pos[k + 2] - prev[k + 2];
			double vn = vx * hit.nx + vy * hit.ny + vz * hit.nz;
			double tx = vx - hit.nx * vn, ty = vy - hit.ny * vn, tz = vz - hit.nz * vn;
			double nvn = vn < 0 ? -vn * 0.1 : vn;
			double f = 0.55;
			prev[k] = pos[k] - (tx * f + hit.nx * nvn);
			prev[k + 1] = pos[k + 1] - (ty * f + hit.ny * nvn);
			prev[k + 2] = pos[k + 2] - (tz * f + hit.nz * nvn);
		}
	}

	/** Interpolated particle positions for rendering. */
	public void interpolated(float partial, double[] out) {
		for (int i = 0; i < pos.length; i++) out[i] = tickStart[i] + (pos[i] - tickStart[i]) * partial;
	}

	/**
	 * World transforms that move each bone from its rest pose to its current pose:
	 * {@code T(anchor) * R * T(-anchor0)}.
	 */
	public void boneDeltas(float partial, Matrix4f[] out) {
		double[] p = new double[N * 3];
		interpolated(partial, p);
		Matrix3f[] frames = new Matrix3f[6];
		Vector3d[] anchors = new Vector3d[6];
		for (int b = 0; b < 6; b++) {
			frames[b] = new Matrix3f();
			anchors[b] = new Vector3d();
		}
		computeFrames(p, frames, anchors, this);
		Matrix3f rot = new Matrix3f();
		for (int b = 0; b < 6; b++) {
			frames[b].mul(new Matrix3f(restFrame[b]).transpose(), rot);
			Vector3d a = anchors[b], a0 = restAnchor[b];
			out[b].identity()
					.translate((float) a.x, (float) a.y, (float) a.z)
					.mul(new Matrix4f().set(rot))
					.translate((float) -a0.x, (float) -a0.y, (float) -a0.z);
		}
	}

	/** Mid point of the hips, for lighting and culling. */
	public Vector3d center(float partial, Vector3d out) {
		double[] p = new double[N * 3];
		interpolated(partial, p);
		return out.set((p[HR * 3] + p[HL * 3] + p[SR * 3] + p[SL * 3]) / 4, (p[HR * 3 + 1] + p[HL * 3 + 1] + p[SR * 3 + 1] + p[SL * 3 + 1]) / 4,
				(p[HR * 3 + 2] + p[HL * 3 + 2] + p[SR * 3 + 2] + p[SL * 3 + 2]) / 4);
	}

	private static Vector3d pt(double[] p, int i) {
		return new Vector3d(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]);
	}

	/**
	 * Bone frames from particle positions. Frame columns are (right, down, forward). {@code self} is null while
	 * computing the rest frames.
	 */
	private void computeFrames(double[] p, Matrix3f[] frames, Vector3d[] anchors, Ragdoll self) {
		Vector3d sr = pt(p, SR), sl = pt(p, SL), hr = pt(p, HR), hl = pt(p, HL);
		Vector3d sMid = new Vector3d(sr).add(sl).mul(0.5);
		Vector3d hMid = new Vector3d(hr).add(hl).mul(0.5);
		Vector3f right = new Vector3f((float) (sl.x - sr.x), (float) (sl.y - sr.y), (float) (sl.z - sr.z));
		Vector3f down = new Vector3f((float) (hMid.x - sMid.x), (float) (hMid.y - sMid.y), (float) (hMid.z - sMid.z));
		frame(down, right, frames[BONE_BODY]);
		anchors[BONE_BODY].set(sMid);

		// neck rides rigidly on the torso
		Vector3d neck;
		if (self == null) {
			neck = new Vector3d(neck0);
		} else {
			Matrix3f d = new Matrix3f(frames[BONE_BODY]).mul(new Matrix3f(restFrame[BONE_BODY]).transpose());
			Vector3f off = new Vector3f((float) (neck0.x - restAnchor[BONE_BODY].x), (float) (neck0.y - restAnchor[BONE_BODY].y),
					(float) (neck0.z - restAnchor[BONE_BODY].z));
			d.transform(off);
			neck = new Vector3d(sMid).add(off.x, off.y, off.z);
		}
		Vector3d head = pt(p, HEAD);
		frame(new Vector3f((float) (neck.x - head.x), (float) (neck.y - head.y), (float) (neck.z - head.z)), right, frames[BONE_HEAD]);
		anchors[BONE_HEAD].set(neck);

		limb(p, SR, HAND_R, right, frames[BONE_RARM], anchors[BONE_RARM]);
		limb(p, SL, HAND_L, right, frames[BONE_LARM], anchors[BONE_LARM]);
		limb(p, HR, FOOT_R, right, frames[BONE_RLEG], anchors[BONE_RLEG]);
		limb(p, HL, FOOT_L, right, frames[BONE_LLEG], anchors[BONE_LLEG]);
	}

	private static void limb(double[] p, int from, int to, Vector3f right, Matrix3f frame, Vector3d anchor) {
		Vector3f d = new Vector3f((float) (p[to * 3] - p[from * 3]), (float) (p[to * 3 + 1] - p[from * 3 + 1]), (float) (p[to * 3 + 2] - p[from * 3 + 2]));
		frame(d, right, frame);
		anchor.set(p[from * 3], p[from * 3 + 1], p[from * 3 + 2]);
	}

	private static void frame(Vector3f down, Vector3f refRight, Matrix3f out) {
		Vector3f d = new Vector3f(down);
		if (d.lengthSquared() < 1.0E-10F) d.set(0, -1, 0);
		d.normalize();
		Vector3f r = new Vector3f(refRight).fma(-refRight.dot(d), d);
		if (r.lengthSquared() < 1.0E-8F) {
			r.set(1, 0, 0).fma(-d.x, d);
			if (r.lengthSquared() < 1.0E-8F) r.set(0, 0, 1);
		}
		r.normalize();
		Vector3f f = new Vector3f(r).cross(d);
		out.setColumn(0, r).setColumn(1, d).setColumn(2, f);
	}
}
