package dev.leonida.fx.physics;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Lightweight rigid body solver for debris. Each piece is an oriented box; contacts are found by testing the
 * eight corners against block collision boxes and resolved with sequential impulses (restitution and Coulomb
 * friction) plus positional correction. Pieces push each other apart with bounding spheres. Resting pieces go to
 * sleep and are woken when their support disappears. Pure Java, no natives.
 */
public final class DebrisWorld {
	public static final float GRAVITY = 22.0F;
	public static final int SUBSTEPS = 4;
	public static final float DT = 0.05F / SUBSTEPS;
	private static final float MAX_SPEED = 28.0F;

	private final List<Piece> pieces = new ArrayList<>();
	private final Collider collider = new Collider();
	private final Collider.Hit hit = new Collider.Hit();
	private final Long2ObjectOpenHashMap<List<Piece>> grid = new Long2ObjectOpenHashMap<>();

	// scratch
	private final Vector3f r = new Vector3f();
	private final Vector3f n = new Vector3f();
	private final Vector3f tmp = new Vector3f();
	private final Vector3f tmp2 = new Vector3f();
	private final Matrix3f invI = new Matrix3f();
	private final float[] cr = new float[8 * 3];
	private final float[] cn = new float[8 * 3];
	private final float[] cd = new float[8];

	public List<Piece> pieces() {
		return pieces;
	}

	public int size() {
		return pieces.size();
	}

	public void clear() {
		pieces.clear();
	}

	/** Adds a piece, dropping the oldest ones when over {@code budget}. */
	public void add(Piece p, int budget) {
		pieces.add(p);
		int over = pieces.size() - budget;
		if (over > 0) pieces.subList(0, over).clear();
	}

	public void tick(Level level) {
		collider.begin(level);
		collider.invalidate();
		for (int i = pieces.size() - 1; i >= 0; i--) {
			Piece p = pieces.get(i);
			p.prevPos.set(p.pos);
			p.prevRot.set(p.rot);
			p.age++;
			if (p.age >= p.life || p.pos.y < level.getMinY() - 64) {
				pieces.remove(i);
				continue;
			}
			if (p.sleeping) {
				if ((p.age + i) % 10 == 0 && !supported(p)) {
					p.sleeping = false;
					p.restTicks = 0;
				}
				continue;
			}
			for (int s = 0; s < SUBSTEPS; s++) step(p);
		}
		separatePieces();
	}

	private boolean supported(Piece p) {
		p.rotM.set(p.rot);
		for (int c = 0; c < 8; c++) {
			corner(p, c, r);
			if (collider.insideSolid(p.pos.x + r.x, p.pos.y + r.y - 0.06, p.pos.z + r.z)) return true;
		}
		return false;
	}

	private void corner(Piece p, int c, Vector3f out) {
		out.set((c & 1) == 0 ? -p.hx : p.hx, (c & 2) == 0 ? -p.hy : p.hy, (c & 4) == 0 ? -p.hz : p.hz);
		p.rotM.transform(out);
	}

	private void step(Piece p) {
		float dt = DT;
		p.vel.y -= GRAVITY * dt;
		double damp = 1.0 - 0.15 * dt;
		p.vel.mul(damp);
		double sp = p.vel.length();
		if (sp > MAX_SPEED) p.vel.mul(MAX_SPEED / sp);
		p.angVel.mul(1.0F - 0.2F * dt);
		p.pos.fma(dt, p.vel);
		integrateRotation(p.rot, p.angVel, dt);
		p.rotM.set(p.rot);

		// world inverse inertia: R * diag * R^T
		Matrix3f R = p.rotM;
		invI.set(R).scale(p.invInertiaBody).mul(tmpTranspose(R));

		int count = 0;
		for (int c = 0; c < 8; c++) {
			corner(p, c, r);
			if (collider.pointContact(p.pos.x + r.x, p.pos.y + r.y, p.pos.z + r.z, hit)) {
				cr[count * 3] = r.x;
				cr[count * 3 + 1] = r.y;
				cr[count * 3 + 2] = r.z;
				cn[count * 3] = (float) hit.nx;
				cn[count * 3 + 1] = (float) hit.ny;
				cn[count * 3 + 2] = (float) hit.nz;
				cd[count] = (float) hit.depth;
				count++;
			}
		}
		p.contacts = count;
		if (count == 0) {
			p.restTicks = 0;
			return;
		}
		// positional correction: deepest push per axis direction
		double px = 0, py = 0, pz = 0, nxm = 0, nym = 0, nzm = 0;
		for (int i = 0; i < count; i++) {
			double d = Math.max(0.0, cd[i] - 0.002);
			double vx = cn[i * 3] * d, vy = cn[i * 3 + 1] * d, vz = cn[i * 3 + 2] * d;
			if (vx > px) px = vx;
			if (vx < nxm) nxm = vx;
			if (vy > py) py = vy;
			if (vy < nym) nym = vy;
			if (vz > pz) pz = vz;
			if (vz < nzm) nzm = vz;
		}
		p.pos.add((px + nxm) * 0.9, (py + nym) * 0.9, (pz + nzm) * 0.9);

		// impulses, two iterations
		for (int it = 0; it < 2; it++) {
			for (int i = 0; i < count; i++) {
				r.set(cr[i * 3], cr[i * 3 + 1], cr[i * 3 + 2]);
				n.set(cn[i * 3], cn[i * 3 + 1], cn[i * 3 + 2]);
				// relative velocity at contact
				p.angVel.cross(r, tmp);
				float vrx = (float) p.vel.x + tmp.x, vry = (float) p.vel.y + tmp.y, vrz = (float) p.vel.z + tmp.z;
				float vn = vrx * n.x + vry * n.y + vrz * n.z;
				if (vn >= 0) continue;
				float e = vn < -2.0F ? p.restitution : 0.0F;
				float k = p.invMass + n.dot(angularTerm(r, n));
				float j = -(1.0F + e) * vn / k / (it == 0 ? Math.max(1, count / 2) : count);
				applyImpulse(p, r, n.x * j, n.y * j, n.z * j);
				// friction
				p.angVel.cross(r, tmp);
				vrx = (float) p.vel.x + tmp.x;
				vry = (float) p.vel.y + tmp.y;
				vrz = (float) p.vel.z + tmp.z;
				vn = vrx * n.x + vry * n.y + vrz * n.z;
				float tx = vrx - n.x * vn, ty = vry - n.y * vn, tz = vrz - n.z * vn;
				float tl = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
				if (tl < 1.0E-4F) continue;
				tmp2.set(tx / tl, ty / tl, tz / tl);
				float kt = p.invMass + tmp2.dot(angularTerm(r, tmp2));
				float jt = Math.min(tl / kt, p.friction * j);
				applyImpulse(p, r, -tmp2.x * jt, -tmp2.y * jt, -tmp2.z * jt);
			}
		}
		// rolling resistance while touching
		p.angVel.mul(0.985F);
		if (p.vel.lengthSquared() < 0.09 && p.angVel.lengthSquared() < 0.25) {
			if (++p.restTicks > 24) {
				p.sleeping = true;
				p.vel.zero();
				p.angVel.zero();
			}
		} else {
			p.restTicks = 0;
		}
	}

	private final Matrix3f transposeScratch = new Matrix3f();

	private Matrix3f tmpTranspose(Matrix3f m) {
		return m.transpose(transposeScratch);
	}

	private final Vector3f angScratch = new Vector3f();

	/** (I^-1 (r x d)) x r */
	private Vector3f angularTerm(Vector3f r, Vector3f d) {
		r.cross(d, angScratch);
		invI.transform(angScratch);
		return angScratch.cross(r);
	}

	private void applyImpulse(Piece p, Vector3f r, float jx, float jy, float jz) {
		p.vel.add(jx * p.invMass, jy * p.invMass, jz * p.invMass);
		tmp.set(jx, jy, jz);
		r.cross(tmp, tmp);
		invI.transform(tmp);
		p.angVel.add(tmp);
	}

	private static final Quaternionf SPIN = new Quaternionf();

	static void integrateRotation(Quaternionf q, Vector3f w, float dt) {
		float wl = w.length();
		if (wl < 1.0E-6F) return;
		float angle = wl * dt;
		SPIN.setAngleAxis(angle, w.x / wl, w.y / wl, w.z / wl);
		SPIN.mul(q, q);
		q.normalize();
	}

	/** Bounding-sphere pushes between nearby pieces, once per tick, using a spatial hash. */
	private void separatePieces() {
		if (pieces.size() < 2) return;
		grid.clear();
		for (Piece p : pieces) {
			long key = cell(p.pos.x, p.pos.y, p.pos.z);
			grid.computeIfAbsent(key, k -> new ArrayList<>(4)).add(p);
		}
		Vector3d d = new Vector3d();
		for (Piece a : pieces) {
			if (a.sleeping) continue;
			int cx = (int) Math.floor(a.pos.x), cy = (int) Math.floor(a.pos.y), cz = (int) Math.floor(a.pos.z);
			float ra = a.contactRadius();
			for (int ox = -1; ox <= 1; ox++) for (int oy = -1; oy <= 1; oy++) for (int oz = -1; oz <= 1; oz++) {
				List<Piece> list = grid.get(cellKey(cx + ox, cy + oy, cz + oz));
				if (list == null) continue;
				for (Piece b : list) {
					if (b == a) continue;
					float rr = ra + b.contactRadius();
					a.pos.sub(b.pos, d);
					double l2 = d.lengthSquared();
					if (l2 >= rr * rr || l2 < 1.0E-10) continue;
					double l = Math.sqrt(l2);
					double pen = (rr - l);
					d.mul(1.0 / l);
					double share = b.sleeping ? 1.0 : 0.5;
					a.pos.fma(pen * share * 0.8, d);
					if (!b.sleeping) b.pos.fma(-pen * (1 - share) * 0.8, d);
					// inelastic along the normal
					double van = a.vel.dot(d), vbn = b.sleeping ? 0 : b.vel.dot(d);
					double rel = van - vbn;
					if (rel < 0) {
						a.vel.fma(-rel * share, d);
						if (!b.sleeping) b.vel.fma(rel * (1 - share), d);
						else if (rel < -3.0) b.sleeping = false;
					}
				}
			}
		}
	}

	private static long cell(double x, double y, double z) {
		return cellKey((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
	}

	private static long cellKey(int x, int y, int z) {
		return ((long) x & 0x3FFFFFL) | (((long) z & 0x3FFFFFL) << 22) | (((long) y & 0xFFFFFL) << 44);
	}
}
