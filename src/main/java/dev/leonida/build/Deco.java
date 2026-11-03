package dev.leonida.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Exterior props: palms, pools, hedges, lamps, parking, neon, rooftop gear. Local coordinates. */
final class Deco {
	private Deco() {}

	private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

	/** Royal palm (same shape as the world module's): slim pale trunk, green crown shaft, arching fronds. */
	static void palm(Site s, int x, int y, int z) {
		int hgt = 8 + Math.floorMod((int) (s.id ^ (x * 31L + z * 17L)), 4);
		for (int k = 0; k < hgt - 2; k++) s.set(x, y + k, z, B.axis("stripped_birch_log", Direction.Axis.Y));
		s.set(x, y + hgt - 2, z, B.of("moss_block"));
		s.set(x, y + hgt - 1, z, B.of("moss_block"));
		int t = y + hgt;
		BlockState lf = B.leaves("jungle_leaves");
		s.set(x, t + 1, z, lf);
		int[] dy = {1, 0, 0, -1, -1};
		for (int i = 0; i < DIRS.length; i++) {
			int l = i < 4 ? 4 : 3;
			for (int k = 1; k <= l; k++) {
				int yy = t + dy[k - 1];
				s.set(x + DIRS[i][0] * k, yy, z + DIRS[i][1] * k, lf);
				if (k == l) s.set(x + DIRS[i][0] * k, yy - 1, z + DIRS[i][1] * k, lf);
			}
		}
	}

	static void pool(Site s, int x0, int z0, int x1, int z1, int y) {
		s.fill(x0 - 1, y - 3, z0 - 1, x1 + 1, y - 1, z1 + 1, B.of("smooth_sandstone"));
		s.fill(x0, y - 3, z0, x1, y - 3, z1, B.of("light_blue_terracotta"));
		s.fill(x0, y - 2, z0, x1, y - 1, z1, B.of("water"));
		for (int x = x0; x <= x1; x += 3) s.set(x, y - 3, z0, B.of("sea_lantern"));
	}

	static void hedge(Site s, int x0, int z0, int x1, int z1, int y, String leaves) {
		BlockState l = B.leaves(leaves);
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) { s.set(x, y, z, l); }
	}

	static void lampPost(Site s, int x, int y, int z) {
		s.set(x, y, z, B.of("polished_blackstone_wall"));
		s.set(x, y + 1, z, B.of("iron_chain"));
		s.set(x, y + 2, z, B.of("iron_chain"));
		s.set(x, y + 3, z, B.of("sea_lantern"));
	}

	/** Painted parking bays along z with white lines every 3 blocks. */
	static void parking(Site s, int x0, int z0, int x1, int z1, int y) {
		for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++)
			s.set(x, y - 1, z, (x - x0) % 3 == 0 && z != z1 ? B.of("white_concrete") : B.of("gray_concrete"));
	}

	static void asphalt(Site s, int x0, int z0, int x1, int z1, int y) {
		s.fill(x0, y - 1, z0, x1, y - 1, z1, B.of("gray_concrete"));
	}

	static void grass(Site s, int y) {
		for (int x = s.cx0; x <= s.cx1; x++) for (int z = s.cz0; z <= s.cz1; z++) if (s.inLot(x, z)) s.set(x, y - 1, z, B.of("grass_block"));
	}

	static void bollard(Site s, int x, int y, int z) {
		s.set(x, y, z, B.of("red_nether_brick_wall"));
	}

	static void acUnits(Site s, int x0, int z0, int x1, int z1, int y) {
		int n = 0;
		for (int x = x0 + 2; x <= x1 - 3 && n < 4; x += 5) for (int z = z0 + 2; z <= z1 - 3 && n < 4; z += 6) {
			if (s.chance(0.5)) continue;
			s.fill(x, y, z, x + 1, y, z, B.of("iron_block"));
			s.set(x, y + 1, z, B.of("iron_trapdoor"));
			s.set(x + 1, y + 1, z, B.of("iron_trapdoor"));
			n++;
		}
	}

	static void waterTank(Site s, int x, int y, int z) {
		for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) s.set(x + dx * 2, y, z + dz * 2, B.of("spruce_fence"));
		s.fill(x, y + 1, z, x + 2, y + 3, z + 2, B.of("spruce_planks"));
		s.fill(x, y + 4, z, x + 2, y + 4, z + 2, B.slab("spruce_slab", false));
	}

	static void helipad(Site s, int cx, int cz, int y) {
		s.fill(cx - 3, y, cz - 3, cx + 3, y, cz + 3, B.of("gray_concrete"));
		for (int k = -2; k <= 2; k++) { s.set(cx - 2, y, cz + k, B.of("yellow_concrete")); s.set(cx + 2, y, cz + k, B.of("yellow_concrete")); }
		s.set(cx - 1, y, cz, B.of("yellow_concrete")); s.set(cx, y, cz, B.of("yellow_concrete")); s.set(cx + 1, y, cz, B.of("yellow_concrete"));
		for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) s.set(cx + c[0], y + 1, cz + c[1], B.of("sea_lantern"));
	}

	/** Replace facade edge cells at height y along the front (local north) with neon. */
	static void neonFront(Site s, int x0, int x1, int y, BlockState neon) {
		for (int x = x0; x <= x1; x++) {
			for (int z = 0; z < s.D; z++) {
				if (s.in(x, z)) { if (s.edge(x, z)) s.set(x, y, z, neon); break; }
			}
		}
	}

	/** Neon outline at height y around the whole footprint edge. */
	static void neonRing(Site s, int y, BlockState neon, int x0, int z0, int x1, int z1) {
		for (int x = Math.max(x0, s.cx0); x <= Math.min(x1, s.cx1); x++) for (int z = Math.max(z0, s.cz0); z <= Math.min(z1, s.cz1); z++)
			if (s.in(x, z) && s.edge(x, z) && !s.corner(x, z)) s.set(x, y, z, neon);
	}

	/** Striped awning row on the front wall at height y. */
	static void awning(Site s, int x0, int x1, int y, String c1, String c2) {
		for (int x = x0; x <= x1; x++) {
			for (int z = 0; z < s.D; z++) {
				if (s.in(x, z)) { if (s.edge(x, z)) s.set(x, y, z, B.c(((x & 1) == 0) ? c1 : c2, "concrete")); break; }
			}
		}
	}

	/** Gable roof over a rectangle, ridge along local x, with one block overhang front and back. */
	static void gable(Site s, int x0, int z0, int x1, int z1, int y, String stair, BlockState gableWall) {
		int depth = z1 - z0 + 1;
		int half = (depth + 1) / 2;
		for (int k = 0; k < half; k++) {
			int zf = z0 - 1 + k, zb = z1 + 1 - k;
			int yy = y + k;
			for (int x = x0; x <= x1; x++) {
				s.set(x, yy, zf, B.stair(stair, Direction.SOUTH, false));
				if (zb != zf) s.set(x, yy, zb, B.stair(stair, Direction.NORTH, false));
			}
			if (k > 0) for (int z = zf + 1; z < zb; z++) { s.set(x0, yy, z, gableWall); s.set(x1, yy, z, gableWall); }
		}
		if ((depth & 1) == 1) for (int x = x0; x <= x1; x++) s.set(x, y + half, z0 - 1 + half, B.slab(stair.replace("_stairs", "_slab"), false));
	}

	/** Flat canopy on pillars (gas stations, entrances). */
	static void canopy(Site s, int x0, int z0, int x1, int z1, int y, int hgt, BlockState top, BlockState band, BlockState light) {
		for (int[] p : new int[][] {{x0 + 1, z0 + 1}, {x1 - 1, z0 + 1}, {x0 + 1, z1 - 1}, {x1 - 1, z1 - 1}})
			s.fill(p[0], y, p[1], p[0], y + hgt - 1, p[1], B.of("smooth_quartz"));
		s.fill(x0, y + hgt, z0, x1, y + hgt, z1, top);
		for (int x = x0; x <= x1; x++) { s.set(x, y + hgt + 1, z0, band); s.set(x, y + hgt + 1, z1, band); }
		for (int z = z0; z <= z1; z++) { s.set(x0, y + hgt + 1, z, band); s.set(x1, y + hgt + 1, z, band); }
		for (int x = x0 + 2; x <= x1 - 2; x += 3) for (int z = z0 + 2; z <= z1 - 2; z += 3) s.set(x, y + hgt, z, light);
	}

	static void pump(Site s, int x, int y, int z, Direction face) {
		s.set(x, y - 1, z, B.of("smooth_stone"));
		s.set(x, y, z, B.of("white_concrete"));
		s.set(x, y + 1, z, B.of("red_concrete"));
		s.set(x + face.getStepX(), y + 1, z + face.getStepZ(), B.button("polished_blackstone_button", face));
	}

	/** Free-standing pylon sign: pole with a neon box on top. */
	static void pylon(Site s, int x, int z, int y, int hgt, BlockState box, BlockState neon) {
		for (int k = 0; k < hgt; k++) s.set(x, y + k, z, B.of("polished_blackstone_wall"));
		s.fill(x - 1, y + hgt, z, x + 1, y + hgt + 2, z, box);
		s.set(x, y + hgt + 1, z, neon);
		s.set(x - 1, y + hgt + 1, z, neon);
		s.set(x + 1, y + hgt + 1, z, neon);
	}
}
