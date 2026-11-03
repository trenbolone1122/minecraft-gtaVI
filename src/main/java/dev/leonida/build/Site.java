package dev.leonida.build;

import dev.leonida.core.api.BuildingGenerator;
import dev.leonida.core.api.MapQuery;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One building footprint seen in a local frame: local x runs along the street front, local z runs
 * from the street (z = 0) to the back. Local NORTH faces the street. States written in local
 * orientation are rotated into the world, so every design is authored once.
 */
final class Site {
	final Canvas cv;
	final BuildingGenerator.Footprint fp;
	final BuildingGenerator.Kind kind;
	final String landmark;
	final long id;
	final int baseY;
	final int floors;
	/** LOOK.md facade style from the map (-1 = choose by kind) and total height in blocks (0 = unknown). */
	final int style, heightM;
	final Random rnd;
	final int W, D;
	/** True when every polygon edge is axis-aligned (no diagonal staircase walls). */
	final boolean axisAligned;
	final Direction front;
	final Rotation rot;
	private final int ox, oz, ex, ez, sx, sz;
	private final boolean[] mask;
	/** Local clip window of the current chunk (inclusive). */
	final int cx0, cx1, cz0, cz1;
	private final BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

	private Site(Canvas cv, BuildingGenerator.Footprint fp, Direction front, int minX, int minZ, int maxX, int maxZ) {
		this.cv = cv;
		this.fp = fp;
		this.kind = fp.kind();
		this.landmark = fp.landmark();
		this.id = fp.id();
		this.baseY = fp.baseY();
		this.floors = Math.max(1, fp.floors());
		this.style = fp.style();
		this.heightM = fp.style() >= 0 ? fp.height() : 0;
		this.rnd = new Random(fp.id() * 0x9E3779B97F4A7C15L + 0x5DEECE66DL);
		this.front = front;
		this.rot = switch (front) {
			case EAST -> Rotation.CLOCKWISE_90;
			case SOUTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		Direction e = rot.rotate(Direction.EAST), s = rot.rotate(Direction.SOUTH);
		ex = e.getStepX(); ez = e.getStepZ(); sx = s.getStepX(); sz = s.getStepZ();
		int bw = maxX - minX + 1, bd = maxZ - minZ + 1;
		W = ex != 0 ? bw : bd;
		D = ex != 0 ? bd : bw;
		ox = (ex == 1 || sx == 1) ? minX : maxX;
		oz = (ez == 1 || sz == 1) ? minZ : maxZ;
		mask = new boolean[W * D];
		List<int[]> poly = fp.polygon();
		boolean aa = true;
		for (int i = 0; i < poly.size(); i++) {
			int[] a = poly.get(i), b = poly.get((i + 1) % poly.size());
			if (a[0] != b[0] && a[1] != b[1]) { aa = false; break; }
		}
		axisAligned = aa;
		for (int lx = 0; lx < W; lx++) for (int lz = 0; lz < D; lz++) mask[lx * D + lz] = Geo.inside(poly, wx(lx, lz), wz(lx, lz));
		int ax = lx(cv.minX, cv.minZ), az = lz(cv.minX, cv.minZ), bx = lx(cv.maxX, cv.maxZ), bz = lz(cv.maxX, cv.maxZ);
		cx0 = Math.max(0, Math.min(ax, bx)); cx1 = Math.min(W - 1, Math.max(ax, bx));
		cz0 = Math.max(0, Math.min(az, bz)); cz1 = Math.min(D - 1, Math.max(az, bz));
	}

	/** Inclusive world bbox of a footprint polygon {minX, minZ, maxX, maxZ}, or null if it has no usable polygon. */
	static int[] bbox(BuildingGenerator.Footprint fp) {
		List<int[]> poly = fp.polygon();
		if (poly == null || poly.size() < 3) return null;
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (int[] p : poly) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]); }
		return new int[] {minX, minZ, maxX, maxZ};
	}

	static Site create(Canvas cv, BuildingGenerator.Footprint fp) {
		List<int[]> poly = fp.polygon();
		if (poly == null || poly.size() < 3) return null;
		int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (int[] p : poly) { minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]); minZ = Math.min(minZ, p[1]); maxZ = Math.max(maxZ, p[1]); }
		if (maxX + 3 < cv.minX || minX - 3 > cv.maxX || maxZ + 3 < cv.minZ || minZ - 3 > cv.maxZ) return null;
		if (maxX - minX < 2 || maxZ - minZ < 2) return null;
		return new Site(cv, fp, pickFront(minX, minZ, maxX, maxZ), minX, minZ, maxX, maxZ);
	}

	/** Street side: the bbox side with most road or sidewalk just outside it; else the long side, south. */
	/** Debug command override of the street side for the current thread. */
	static final ThreadLocal<Direction> FORCE_FRONT = new ThreadLocal<>();

	static Direction pickFront(int minX, int minZ, int maxX, int maxZ) {
		Direction forced = FORCE_FRONT.get();
		if (forced != null) return forced;
		MapQuery q = MapQuery.get();
		int best = 0;
		Direction pick = null;
		for (Direction d : new Direction[] {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST}) {
			int n = 0;
			int len = d.getAxis() == Direction.Axis.Z ? maxX - minX : maxZ - minZ;
			int step = Math.max(1, len / 8);
			for (int t = 0; t <= len; t += step) {
				int x, z;
				switch (d) {
					case SOUTH -> { x = minX + t; z = maxZ + 3; }
					case NORTH -> { x = minX + t; z = minZ - 3; }
					case EAST -> { x = maxX + 3; z = minZ + t; }
					default -> { x = minX - 3; z = minZ + t; }
				}
				MapQuery.Surface sf;
				try { sf = q.surfaceAt(x, z); } catch (RuntimeException ex) { sf = MapQuery.Surface.LAND; }
				if (sf == MapQuery.Surface.ROAD || sf == MapQuery.Surface.SIDEWALK || sf == MapQuery.Surface.HIGHWAY) n++;
			}
			if (n > best) { best = n; pick = d; }
		}
		if (pick != null) return pick;
		return (maxX - minX) >= (maxZ - minZ) ? Direction.SOUTH : Direction.EAST;
	}

	int wx(int lx, int lz) { return ox + lx * ex + lz * sx; }

	int wz(int lx, int lz) { return oz + lx * ez + lz * sz; }

	int lx(int wx, int wz) { return (wx - ox) * ex + (wz - oz) * ez; }

	int lz(int wx, int wz) { return (wx - ox) * sx + (wz - oz) * sz; }

	/** Optional sub-rectangle (building inside a lot); null = whole footprint. */
	private int bx0 = Integer.MIN_VALUE, bz0, bx1, bz1;

	void bounds(int x0, int z0, int x1, int z1) { bx0 = x0; bz0 = z0; bx1 = x1; bz1 = z1; }

	void clearBounds() { bx0 = Integer.MIN_VALUE; }

	boolean in(int lx, int lz) {
		if (bx0 != Integer.MIN_VALUE && (lx < bx0 || lx > bx1 || lz < bz0 || lz > bz1)) return false;
		return lx >= 0 && lz >= 0 && lx < W && lz < D && mask[lx * D + lz];
	}

	/** Inside the raw footprint, ignoring bounds. */
	boolean inLot(int lx, int lz) {
		return lx >= 0 && lz >= 0 && lx < W && lz < D && mask[lx * D + lz];
	}

	boolean edge(int lx, int lz) {
		return in(lx, lz) && (!in(lx - 1, lz) || !in(lx + 1, lz) || !in(lx, lz - 1) || !in(lx, lz + 1));
	}

	/** Edge cell with two or more outside neighbours (or a diagonal gap): corner pillars. */
	boolean corner(int lx, int lz) {
		if (!edge(lx, lz)) return false;
		int n = (in(lx - 1, lz) ? 0 : 1) + (in(lx + 1, lz) ? 0 : 1) + (in(lx, lz - 1) ? 0 : 1) + (in(lx, lz + 1) ? 0 : 1);
		if (n >= 2) return !stair(lx, lz);
		return !in(lx - 1, lz - 1) && in(lx - 1, lz) && in(lx, lz - 1) || !in(lx + 1, lz + 1) && in(lx + 1, lz) && in(lx, lz + 1)
			|| !in(lx - 1, lz + 1) && in(lx - 1, lz) && in(lx, lz + 1) || !in(lx + 1, lz - 1) && in(lx + 1, lz) && in(lx, lz - 1);
	}

	/**
	 * Step cell of a rasterised diagonal edge: two outside neighbours on perpendicular sides, but neither inside
	 * neighbour continues a straight wall run on those sides. A real corner has at least one straight run.
	 */
	boolean stair(int lx, int lz) {
		boolean n = !in(lx, lz - 1), sth = !in(lx, lz + 1), w = !in(lx - 1, lz), e = !in(lx + 1, lz);
		if ((n && sth) || (w && e)) return false;
		if (!((n || sth) && (w || e))) return false;
		int vz = n ? -1 : 1, hx = w ? -1 : 1;
		// run along x continuing the north/south wall, run along z continuing the west/east wall
		boolean runX = in(lx - hx, lz) && !in(lx - hx, lz + vz);
		boolean runZ = in(lx, lz - vz) && !in(lx + hx, lz - vz);
		return !runX && !runZ;
	}

	/** Outward local direction of an edge cell (first outside neighbour). */
	Direction outward(int lx, int lz) {
		if (!in(lx, lz - 1)) return Direction.NORTH;
		if (!in(lx, lz + 1)) return Direction.SOUTH;
		if (!in(lx - 1, lz)) return Direction.WEST;
		if (!in(lx + 1, lz)) return Direction.EAST;
		return null;
	}

	boolean visible(int lx0, int lz0, int lx1, int lz1) {
		return Math.max(lx0, lx1) >= cx0 && Math.min(lx0, lx1) <= cx1 && Math.max(lz0, lz1) >= cz0 && Math.min(lz0, lz1) <= cz1;
	}

	void set(int lx, int y, int lz, BlockState s) {
		if (s == null || !in(lx, lz)) return;
		int x = wx(lx, lz), z = wz(lx, lz);
		if (!cv.contains(x, y, z)) return;
		mp.set(x, y, z);
		cv.put(mp, rot == Rotation.NONE ? s : s.rotate(rot));
	}

	/** Write just outside the building (balconies, eyebrows, eaves): only cells off the footprint that are still air. */
	/** Place a wall attachment inside or outside the lot, only into air or replaceable cells. */
	void setFree(int lx, int y, int lz, BlockState s) {
		if (s == null) return;
		if (!in(lx, lz)) { setOut(lx, y, lz, s); return; }
		if (isNeon(s) && !neonAttached(lx, y, lz, s)) return;
		int x = wx(lx, lz), z = wz(lx, lz);
		if (!cv.contains(x, y, z)) return;
		mp.set(x, y, z);
		cv.putIfFree(mp, rot == Rotation.NONE ? s : s.rotate(rot));
	}

	private static java.util.Set<net.minecraft.world.level.block.Block> neons;

	/** Look neon tubes (wall-mounted, FACING = outward). */
	static boolean isNeon(BlockState s) {
		if (neons == null) {
			java.util.Set<net.minecraft.world.level.block.Block> n = new java.util.HashSet<>();
			for (dev.leonida.look.LookBlocks.Neon c : dev.leonida.look.LookBlocks.Neon.values()) n.add(dev.leonida.look.LookBlocks.neon(c));
			neons = n;
		}
		return neons.contains(s.getBlock());
	}

	/**
	 * A neon tube may only hang flush on a real facade block: the cell behind it (against its FACING) must be in the
	 * footprint and, when that cell is in this chunk, a solid placed block (not air or replaceable). Never in the air.
	 */
	private boolean neonAttached(int lx, int y, int lz, BlockState s) {
		if (!s.hasProperty(dev.leonida.look.block.OrientedBlock.FACING)) return false;
		Direction f = s.getValue(dev.leonida.look.block.OrientedBlock.FACING);
		if (!f.getAxis().isHorizontal()) return false;
		int bx = lx - f.getStepX(), bz = lz - f.getStepZ();
		if (!in(bx, bz)) return false;
		int x = wx(bx, bz), z = wz(bx, bz);
		if (!cv.contains(x, y, z)) return true;
		BlockState w = cv.read(mp.set(x, y, z));
		return !w.isAir() && !w.canBeReplaced();
	}

	void setOut(int lx, int y, int lz, BlockState s) {
		if (s == null || in(lx, lz)) return;
		if (isNeon(s) && !neonAttached(lx, y, lz, s)) return;
		int x = wx(lx, lz), z = wz(lx, lz);
		if (!cv.contains(x, y, z)) return;
		mp.set(x, y, z);
		cv.putIfFree(mp, rot == Rotation.NONE ? s : s.rotate(rot));
	}

	BlockState get(int lx, int y, int lz) {
		int x = wx(lx, lz), z = wz(lx, lz);
		if (!cv.contains(x, y, z)) return B.AIR;
		mp.set(x, y, z);
		return cv.read(mp);
	}

	/** Fill a local box (inclusive), clipped to the chunk and the footprint. */
	void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
		int ax = Math.max(Math.min(x0, x1), cx0), bx = Math.min(Math.max(x0, x1), cx1);
		int az = Math.max(Math.min(z0, z1), cz0), bz = Math.min(Math.max(z0, z1), cz1);
		int ay = Math.min(y0, y1), by = Math.max(y0, y1);
		for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) for (int y = ay; y <= by; y++) set(x, y, z, s);
	}

	/** Two-block door in local frame; facing is the local direction a player walks through. */
	void door(int lx, int y, int lz, String name, Direction facing, boolean right) {
		set(lx, y, lz, B.door(name, facing, false, right));
		set(lx, y + 1, lz, B.door(name, facing, true, right));
	}

	void bed(int lx, int y, int lz, Direction toHead, String color) {
		set(lx, y, lz, B.bed(color, toHead, false));
		set(lx + toHead.getStepX(), y, lz + toHead.getStepZ(), B.bed(color, toHead, true));
	}

	int pick(int n) { return rnd.nextInt(n); }

	<T> T pick(T[] arr) { return arr[rnd.nextInt(arr.length)]; }

	boolean chance(double p) { return rnd.nextDouble() < p; }

	int range(int a, int b) { return b <= a ? a : a + rnd.nextInt(b - a + 1); }

	boolean isLandmark(String key) { return key.equals(landmark); }
}
