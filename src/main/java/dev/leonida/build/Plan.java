package dev.leonida.build;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Floor plans: a vertical core (switchback-free stacked stair runs plus an elevator pad, or a ladder
 * for small buildings) and a BSP split of the rest into rooms joined by doors. All randomness comes
 * from the Site random in a fixed order, so every chunk sees the same plan.
 */
final class Plan {
	final List<Room> rooms = new ArrayList<>();
	final List<int[]> doors = new ArrayList<>();

	/** Vertical circulation, fixed for the whole building. */
	static final class Core {
		int kx, kz;
		boolean stairs, elevator, ladder;
		int lx, lz;
		Direction ladderFacing = Direction.EAST;
		int width() { return elevator ? 6 : 3; }
	}

	static final int SHAFT = 6;

	/** Plan the core inside interior bounds; floors &gt; 1 only. */
	static Core core(Site s, int x0, int z0, int x1, int z1, int floors, boolean wantElevator) {
		Core c = new Core();
		if (floors <= 1) return c;
		int iw = x1 - x0 + 1, id = z1 - z0 + 1;
		for (int pass = 0; pass < 2; pass++) {
			boolean elev = pass == 0 && wantElevator;
			int cw = elev ? 6 : 3;
			if (iw < cw + 2 || id < SHAFT + 5) continue;
			int kz = z1 - SHAFT + 1;
			int mid = x0 + (iw - cw) / 2;
			int[] tries = {mid, x0 + 1, x1 - cw, mid - 3, mid + 3};
			for (int kx : tries) {
				if (kx - 1 < x0 - 1 || kx + cw > x1 + 1) continue;
				if (fits(s, kx - 1, kz - 1, kx + cw, z1)) {
					c.kx = kx; c.kz = kz; c.stairs = true; c.elevator = elev;
					return c;
				}
			}
		}
		// ladder in a back corner
		for (int[] p : new int[][] {{x0, z1, 1}, {x1, z1, 0}, {x0, z0 + 1, 1}, {x1, z0 + 1, 0}}) {
			if (s.in(p[0], p[1]) && !s.edge(p[0], p[1])) {
				c.ladder = true; c.lx = p[0]; c.lz = p[1];
				c.ladderFacing = p[2] == 1 ? Direction.EAST : Direction.WEST;
				return c;
			}
		}
		return c;
	}

	private static boolean fits(Site s, int x0, int z0, int x1, int z1) {
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (!s.in(x, z)) return false;
		return true;
	}

	/** Lay out one floor. extraDoors are fixed openings (entrances) that walls must not block. */
	static Plan layout(Site s, int x0, int z0, int x1, int z1, Core core, int y, int h, int minRoom, int maxRoom,
		BlockState wall, String doorName, List<int[]> extraDoors) {
		Plan p = new Plan();
		p.doors.addAll(extraDoors);
		if (core.stairs) {
			int cw = core.width();
			int zl = core.kz - 1;
			for (int x = x0; x <= x1; x++) p.wallCell(s, x, zl, y, h, wall);
			p.door(s, core.kx + 1, zl, y, Direction.SOUTH, doorName);
			if (core.elevator) {
				for (int x = core.kx + 4; x <= core.kx + 5; x++) { s.fill(x, y, zl, x, y + h - 2, zl, B.AIR); p.doors.add(new int[] {x, zl}); }
			}
			if (core.kx - 1 >= x0) for (int z = core.kz; z <= z1; z++) p.wallCell(s, core.kx - 1, z, y, h, wall);
			if (core.kx + cw <= x1) for (int z = core.kz; z <= z1; z++) p.wallCell(s, core.kx + cw, z, y, h, wall);
			if (core.kx - 2 >= x0) {
				p.door(s, s.range(x0, core.kx - 2), zl, y, Direction.SOUTH, doorName);
				p.bsp(s, x0, core.kz, core.kx - 2, z1, y, h, minRoom, maxRoom, wall, doorName);
			}
			if (core.kx + cw + 1 <= x1) {
				p.door(s, s.range(core.kx + cw + 1, x1), zl, y, Direction.SOUTH, doorName);
				p.bsp(s, core.kx + cw + 1, core.kz, x1, z1, y, h, minRoom, maxRoom, wall, doorName);
			}
			z1 = zl - 1;
		}
		p.bsp(s, x0, z0, x1, z1, y, h, minRoom, maxRoom, wall, doorName);
		for (Room r : p.rooms) {
			r.blockDoors(p.doors);
			if (core.ladder) { r.take(core.lx, core.lz); r.take(core.lx + core.ladderFacing.getStepX(), core.lz); }
		}
		return p;
	}

	void wallCell(Site s, int x, int z, int y, int h, BlockState wall) {
		if (!s.in(x, z) || s.edge(x, z)) return;
		s.fill(x, y, z, x, y + h - 2, z, wall);
	}

	void door(Site s, int x, int z, int y, Direction facing, String doorName) {
		doors.add(new int[] {x, z});
		if (doorName == null) s.fill(x, y, z, x, y + 1, z, B.AIR);
		else { s.door(x, y, z, doorName, facing, false); }
	}

	private void bsp(Site s, int x0, int z0, int x1, int z1, int y, int h, int min, int max, BlockState wall, String doorName) {
		int w = x1 - x0 + 1, d = z1 - z0 + 1;
		if (w <= 0 || d <= 0) return;
		boolean canX = w >= 2 * min + 1, canZ = d >= 2 * min + 1;
		boolean leaf = (w <= max && d <= max) || (!canX && !canZ);
		if (!leaf && w * d < max * max && s.chance(0.25)) leaf = true;
		if (leaf) { rooms.add(new Room(x0, z0, x1, z1, y, h)); return; }
		boolean splitX = canX && (!canZ || w > d || (w == d && s.chance(0.5)));
		if (splitX) {
			int cut = -1;
			for (int t = 0; t < 8 && cut < 0; t++) {
				int c = s.range(x0 + min, x1 - min);
				if (!blocked(c, z0 - 1) && !blocked(c, z1 + 1)) cut = c;
			}
			if (cut < 0) { rooms.add(new Room(x0, z0, x1, z1, y, h)); return; }
			for (int z = z0; z <= z1; z++) wallCell(s, cut, z, y, h, wall);
			int dz = doorSpot(s, cut, z0, z1, true);
			door(s, cut, dz, y, Direction.EAST, doorName);
			bsp(s, x0, z0, cut - 1, z1, y, h, min, max, wall, doorName);
			bsp(s, cut + 1, z0, x1, z1, y, h, min, max, wall, doorName);
		} else {
			int cut = -1;
			for (int t = 0; t < 8 && cut < 0; t++) {
				int c = s.range(z0 + min, z1 - min);
				if (!blocked(x0 - 1, c) && !blocked(x1 + 1, c)) cut = c;
			}
			if (cut < 0) { rooms.add(new Room(x0, z0, x1, z1, y, h)); return; }
			for (int x = x0; x <= x1; x++) wallCell(s, x, cut, y, h, wall);
			int dx = doorSpot(s, cut, x0, x1, false);
			door(s, dx, cut, y, Direction.SOUTH, doorName);
			bsp(s, x0, z0, x1, cut - 1, y, h, min, max, wall, doorName);
			bsp(s, x0, cut + 1, x1, z1, y, h, min, max, wall, doorName);
		}
	}

	private int doorSpot(Site s, int line, int a, int b, boolean vertical) {
		int best = (a + b) / 2;
		for (int t = 0; t < 10; t++) {
			int v = s.range(a, b);
			int x = vertical ? line : v, z = vertical ? v : line;
			boolean ok = vertical ? s.in(x - 1, z) && s.in(x + 1, z) && !s.edge(x - 1, z) && !s.edge(x + 1, z)
				: s.in(x, z - 1) && s.in(x, z + 1) && !s.edge(x, z - 1) && !s.edge(x, z + 1);
			if (ok) return v;
		}
		return best;
	}

	private boolean blocked(int x, int z) {
		for (int[] d : doors) if (d[0] == x && d[1] == z) return true;
		return false;
	}

	/** Build the core for one floor (f counted from 0). */
	static void buildCore(Site s, Core c, int f, int floors, int y, int h, BlockState wall, BlockState cabin) {
		if (c.ladder) {
			if (f > 0) s.set(c.lx, y - 1, c.lz, B.face("ladder", c.ladderFacing));
			for (int k = 0; k < h - 1; k++) s.set(c.lx, y + k, c.lz, B.face("ladder", c.ladderFacing));
			return;
		}
		if (!c.stairs) return;
		int kx = c.kx, kz = c.kz;
		s.fill(kx, y, kz, kx + 2, y + h - 2, kz + SHAFT - 1, B.AIR);
		if (f < floors - 1) {
			int col = kx + ((f & 1) == 0 ? 0 : 2);
			for (int r = 1; r <= h; r++) {
				s.set(col, y + r - 1, kz + r, B.stair("stone_brick_stairs", Direction.SOUTH, false));
				if (r < h) s.fill(col, y + r, kz + r, col, y + h - 1, kz + r, B.AIR);
			}
		}
		// light
		s.set(kx + 1, y + h - 2, kz + SHAFT - 1, B.light(13));
		if (c.elevator) {
			s.fill(kx + 3, y, kz, kx + 3, y + h - 2, kz + SHAFT - 1, wall);
			s.fill(kx + 4, y, kz + 2, kx + 5, y + h - 2, kz + SHAFT - 1, cabin);
			s.fill(kx + 4, y, kz, kx + 5, y + h - 2, kz + 1, B.AIR);
			s.set(kx + 4, y - 1, kz, B.of("polished_andesite"));
			s.set(kx + 5, y - 1, kz, B.of("polished_andesite"));
			s.set(kx + 4, y - 1, kz + 1, B.of("polished_andesite"));
			s.set(kx + 5, y - 1, kz + 1, B.of("lodestone"));
			s.set(kx + 5, y, kz + 1, B.of("heavy_weighted_pressure_plate"));
			s.set(kx + 4, y + h - 2, kz + 1, B.light(14));
			s.set(kx + 4, y + 1, kz + 1, B.button("stone_button", Direction.NORTH));
		}
	}
}
