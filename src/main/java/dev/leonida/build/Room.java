package dev.leonida.build;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;

/** A rectangular room on one floor in local coordinates (inclusive interior cells). */
final class Room {
	final int x0, z0, x1, z1;
	final int y, h;
	String type = "empty";
	final boolean[] occ;

	Room(int x0, int z0, int x1, int z1, int y, int h) {
		this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.y = y; this.h = h;
		occ = new boolean[Math.max(1, w() * d())];
	}

	int w() { return x1 - x0 + 1; }

	int d() { return z1 - z0 + 1; }

	int area() { return w() * d(); }

	int cx() { return (x0 + x1) / 2; }

	int cz() { return (z0 + z1) / 2; }

	boolean contains(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }

	boolean free(Site s, int x, int z) {
		return contains(x, z) && s.in(x, z) && !s.edge(x, z) && !occ[(x - x0) * d() + (z - z0)];
	}

	void take(int x, int z) {
		if (contains(x, z)) occ[(x - x0) * d() + (z - z0)] = true;
	}

	/** Mark cells next to doors as occupied so furniture never blocks a doorway. */
	void blockDoors(List<int[]> doors) {
		for (int[] dr : doors) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int x = dr[0] + d.getStepX(), z = dr[1] + d.getStepZ();
				take(x, z);
				take(x + d.getStepX(), z + d.getStepZ());
			}
		}
	}

	/** Wall-hugging slot: first cell, direction along the wall, direction into the room. */
	record Slot(int x, int z, Direction along, Direction in) {}

	/** Find a free run of len cells against a wall, depth cells deep; marks them taken. */
	Slot wall(Site s, int len, int depth) {
		List<Slot> c = new ArrayList<>();
		// north wall (z0), into room = SOUTH, along = EAST
		for (int x = x0; x + len - 1 <= x1; x++) c.add(new Slot(x, z0, Direction.EAST, Direction.SOUTH));
		for (int x = x0; x + len - 1 <= x1; x++) c.add(new Slot(x + len - 1, z1, Direction.WEST, Direction.NORTH));
		for (int z = z0; z + len - 1 <= z1; z++) c.add(new Slot(x0, z + len - 1, Direction.NORTH, Direction.EAST));
		for (int z = z0; z + len - 1 <= z1; z++) c.add(new Slot(x1, z, Direction.SOUTH, Direction.WEST));
		int n = c.size();
		if (n == 0) return null;
		int start = s.pick(n);
		for (int k = 0; k < n; k++) {
			Slot sl = c.get((start + k) % n);
			if (fits(s, sl, len, depth)) { mark(sl, len, depth); return sl; }
		}
		return null;
	}

	/** Free-standing block of w x d cells with one free cell around it. */
	int[] center(Site s, int w, int d) {
		List<int[]> c = new ArrayList<>();
		for (int x = x0 + 1; x + w <= x1; x++) for (int z = z0 + 1; z + d <= z1; z++) c.add(new int[] {x, z});
		if (c.isEmpty()) return null;
		// prefer the middle
		c.sort((a, b) -> Integer.compare(Math.abs(a[0] + w / 2 - cx()) + Math.abs(a[1] + d / 2 - cz()), Math.abs(b[0] + w / 2 - cx()) + Math.abs(b[1] + d / 2 - cz())));
		int lim = Math.min(c.size(), 3);
		int start = s.pick(lim);
		for (int k = 0; k < c.size(); k++) {
			int[] p = c.get(k < lim ? (start + k) % lim : k);
			boolean ok = true;
			for (int x = p[0]; x < p[0] + w && ok; x++) for (int z = p[1]; z < p[1] + d && ok; z++) ok = free(s, x, z);
			if (ok) {
				for (int x = p[0] - 1; x <= p[0] + w; x++) for (int z = p[1] - 1; z <= p[1] + d; z++) take(x, z);
				return p;
			}
		}
		return null;
	}

	private boolean fits(Site s, Slot sl, int len, int depth) {
		for (int i = 0; i < len; i++) for (int j = 0; j < depth; j++) {
			int x = sl.x + sl.along.getStepX() * i + sl.in.getStepX() * j;
			int z = sl.z + sl.along.getStepZ() * i + sl.in.getStepZ() * j;
			if (!free(s, x, z)) return false;
		}
		// keep the cell in front walkable
		int fx = sl.x + sl.in.getStepX() * depth, fz = sl.z + sl.in.getStepZ() * depth;
		return contains(fx, fz) && s.in(fx, fz);
	}

	private void mark(Slot sl, int len, int depth) {
		for (int i = 0; i < len; i++) for (int j = 0; j < depth; j++) take(sl.x + sl.along.getStepX() * i + sl.in.getStepX() * j, sl.z + sl.along.getStepZ() * i + sl.in.getStepZ() * j);
	}

	static int ax(Slot sl, int i, int j) { return sl.x + sl.along.getStepX() * i + sl.in.getStepX() * j; }

	static int az(Slot sl, int i, int j) { return sl.z + sl.along.getStepZ() * i + sl.in.getStepZ() * j; }
}
