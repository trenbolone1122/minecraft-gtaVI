package dev.leonida.fx.physics;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Read-only view of the block world for the physics solver: collision boxes per block position, cached for one
 * tick. Everything is in world coordinates.
 */
public final class Collider {
	private static final double[] EMPTY = new double[0];
	private final Long2ObjectOpenHashMap<double[]> cache = new Long2ObjectOpenHashMap<>();
	private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
	private Level level;

	/** Result of a contact query: outward normal and penetration depth. */
	public static final class Hit {
		public double nx, ny, nz, depth;
	}

	public void begin(Level level) {
		if (this.level != level || cache.size() > 20000) cache.clear();
		this.level = level;
	}

	public void invalidate() {
		cache.clear();
	}

	/** Flattened boxes {minX,minY,minZ,maxX,maxY,maxZ,...} of the block at x,y,z in world space. */
	public double[] boxes(int x, int y, int z) {
		long key = BlockPos.asLong(x, y, z);
		double[] b = cache.get(key);
		if (b != null) return b;
		m.set(x, y, z);
		if (!level.isLoaded(m)) {
			b = EMPTY;
		} else {
			BlockState s = level.getBlockState(m);
			if (s.isAir()) {
				b = EMPTY;
			} else {
				VoxelShape shape = s.getCollisionShape(level, m);
				if (shape.isEmpty()) {
					b = EMPTY;
				} else {
					List<AABB> list = shape.toAabbs();
					b = new double[list.size() * 6];
					int i = 0;
					for (AABB a : list) {
						b[i++] = a.minX + x;
						b[i++] = a.minY + y;
						b[i++] = a.minZ + z;
						b[i++] = a.maxX + x;
						b[i++] = a.maxY + y;
						b[i++] = a.maxZ + z;
					}
				}
			}
		}
		cache.put(key, b);
		return b;
	}

	public boolean insideSolid(double x, double y, double z) {
		double[] b = boxes(floor(x), floor(y), floor(z));
		for (int i = 0; i < b.length; i += 6) {
			if (x > b[i] && y > b[i + 1] && z > b[i + 2] && x < b[i + 3] && y < b[i + 4] && z < b[i + 5]) return true;
		}
		return false;
	}

	/**
	 * Point contact: if the point is inside a collision box, fills {@code out} with the shortest way out that does
	 * not lead into another solid block, and returns true.
	 */
	public boolean pointContact(double x, double y, double z, Hit out) {
		double[] b = boxes(floor(x), floor(y), floor(z));
		for (int i = 0; i < b.length; i += 6) {
			if (!(x > b[i] && y > b[i + 1] && z > b[i + 2] && x < b[i + 3] && y < b[i + 4] && z < b[i + 5])) continue;
			double best = Double.MAX_VALUE;
			double bnx = 0, bny = 1, bnz = 0;
			// six faces: -x,+x,-y,+y,-z,+z
			double[] depth = {x - b[i], b[i + 3] - x, y - b[i + 1], b[i + 4] - y, z - b[i + 2], b[i + 5] - z};
			for (int f = 0; f < 6; f++) {
				double d = depth[f];
				// slight preference for pushing up so resting pieces do not creep sideways
				double score = f == 3 ? d * 0.8 : d;
				if (score >= best) continue;
				double nx = f == 0 ? -1 : f == 1 ? 1 : 0;
				double ny = f == 2 ? -1 : f == 3 ? 1 : 0;
				double nz = f == 4 ? -1 : f == 5 ? 1 : 0;
				double ex = x + nx * (d + 0.02), ey = y + ny * (d + 0.02), ez = z + nz * (d + 0.02);
				if (insideSolid(ex, ey, ez)) continue;
				best = score;
				bnx = nx;
				bny = ny;
				bnz = nz;
				out.depth = d;
			}
			if (best == Double.MAX_VALUE) {
				out.depth = depth[3];
			}
			out.nx = bnx;
			out.ny = bny;
			out.nz = bnz;
			return true;
		}
		return false;
	}

	/** Sphere contact against boxes around the point. Returns true and fills {@code out} for the deepest one. */
	public boolean sphereContact(double x, double y, double z, double r, Hit out) {
		boolean any = false;
		out.depth = 0;
		int x0 = floor(x - r), x1 = floor(x + r), y0 = floor(y - r), y1 = floor(y + r), z0 = floor(z - r), z1 = floor(z + r);
		for (int bx = x0; bx <= x1; bx++) {
			for (int by = y0; by <= y1; by++) {
				for (int bz = z0; bz <= z1; bz++) {
					double[] b = boxes(bx, by, bz);
					for (int i = 0; i < b.length; i += 6) {
						double cx = clamp(x, b[i], b[i + 3]), cy = clamp(y, b[i + 1], b[i + 4]), cz = clamp(z, b[i + 2], b[i + 5]);
						double dx = x - cx, dy = y - cy, dz = z - cz;
						double d2 = dx * dx + dy * dy + dz * dz;
						double nx, ny, nz, depth;
						if (d2 > 1.0E-12) {
							if (d2 >= r * r) continue;
							double d = Math.sqrt(d2);
							nx = dx / d;
							ny = dy / d;
							nz = dz / d;
							depth = r - d;
						} else {
							// centre inside the box: use point logic
							Hit h = new Hit();
							if (!pointContact(x, y, z, h)) continue;
							nx = h.nx;
							ny = h.ny;
							nz = h.nz;
							depth = h.depth + r;
						}
						if (depth > out.depth) {
							out.nx = nx;
							out.ny = ny;
							out.nz = nz;
							out.depth = depth;
							any = true;
						}
					}
				}
			}
		}
		return any;
	}

	private static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : Math.min(v, hi);
	}

	public static int floor(double v) {
		int i = (int) v;
		return v < i ? i - 1 : i;
	}
}
