package dev.leonida.build;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Furniture from vanilla blocks and room programs. All coordinates are local. */
final class Interiors {
	private Interiors() {}

	// ---------- furniture pieces ----------

	static void sofa(Site s, Room r, String color, int len) {
		Room.Slot sl = r.wall(s, len, 1);
		if (sl == null && len > 2) { len--; sl = r.wall(s, len, 1); }
		if (sl == null) return;
		int n = len;
		for (int i = 0; i < n; i++) s.set(Room.ax(sl, i, 0), r.y, Room.az(sl, i, 0), B.stair(color + "_wool_stairs", sl.in().getOpposite(), false));
		// cushion and coffee table in front
		int fx = Room.ax(sl, n / 2, 2), fz = Room.az(sl, n / 2, 2);
		if (r.free(s, fx, fz)) {
			s.set(fx, r.y, fz, B.trap(s.pick(new String[] {"spruce_trapdoor", "dark_oak_trapdoor", "birch_trapdoor", "oak_trapdoor"}), Direction.NORTH, false, true));
			r.take(fx, fz);
			if (s.chance(0.6)) s.set(fx, r.y + 1, fz, B.of(s.pick(new String[] {"potted_red_tulip", "potted_fern", "potted_azalea_bush", "candle", "potted_cactus"})));
		}
	}

	static void tv(Site s, Room r, boolean big) {
		int len = big ? 3 : 2;
		Room.Slot sl = r.wall(s, len, 1);
		if (sl == null) { sl = r.wall(s, 2, 1); len = 2; }
		if (sl == null) return;
		for (int i = 0; i < len; i++) {
			int x = Room.ax(sl, i, 0), z = Room.az(sl, i, 0);
			s.set(x, r.y, z, B.slab(s.pick(new String[] {"dark_oak_slab", "spruce_slab", "polished_blackstone_slab", "white_concrete_slab"}), true));
			s.set(x, r.y + 1, z, B.of("black_concrete"));
		}
		if (big) for (int i = 0; i < len; i++) s.set(Room.ax(sl, i, 0), r.y + 2, Room.az(sl, i, 0), B.of("black_concrete"));
	}

	static void bed(Site s, Room r, String color, boolean dbl) {
		Room.Slot sl = r.wall(s, dbl ? 2 : 1, 2);
		if (sl == null) return;
		Direction head = sl.in().getOpposite();
		for (int i = 0; i < (dbl ? 2 : 1); i++) {
			int hx = Room.ax(sl, i, 0), hz = Room.az(sl, i, 0);
			s.set(hx, r.y, hz, B.bed(color, head, true));
			s.set(hx + sl.in().getStepX(), r.y, hz + sl.in().getStepZ(), B.bed(color, head, false));
		}
		// nightstands on both sides
		for (int side : new int[] {-1, dbl ? 2 : 1}) {
			int x = Room.ax(sl, side, 0), z = Room.az(sl, side, 0);
			if (r.free(s, x, z)) {
				r.take(x, z);
				s.set(x, r.y, z, B.slab(s.pick(new String[] {"spruce_slab", "birch_slab", "dark_oak_slab", "smooth_quartz_slab"}), true));
				s.set(x, r.y + 1, z, s.chance(0.5) ? B.of("lantern") : B.of("candle"));
			}
		}
	}

	static void wardrobe(Site s, Room r) {
		Room.Slot sl = r.wall(s, 2, 1);
		if (sl == null) return;
		for (int i = 0; i < 2; i++) {
			int x = Room.ax(sl, i, 0), z = Room.az(sl, i, 0);
			s.set(x, r.y, z, B.face("barrel", sl.in()));
			s.set(x, r.y + 1, z, B.face("barrel", sl.in()));
		}
	}

	static void kitchen(Site s, Room r, Style st) {
		int len = Math.min(6, Math.max(3, Math.max(r.w(), r.d()) - 2));
		Room.Slot sl = null;
		while (len >= 2 && (sl = r.wall(s, len, 1)) == null) len--;
		if (sl == null) return;
		BlockState cab = B.of(st.cabinet);
		int sink = s.range(1, Math.max(1, len - 2)), stove = sink == 1 ? len - 1 : 0;
		for (int i = 0; i < len; i++) {
			int x = Room.ax(sl, i, 0), z = Room.az(sl, i, 0);
			if (i == len - 1 && len >= 4) { s.set(x, r.y, z, B.of("iron_block")); s.set(x, r.y + 1, z, B.of("iron_block")); continue; }
			if (i == sink) s.set(x, r.y, z, B.cauldron(2));
			else if (i == stove) s.set(x, r.y, z, B.face("smoker", sl.in()));
			else s.set(x, r.y, z, cab);
			if (r.h >= 4) s.set(x, r.y + 2, z, B.trap(st.wood + "_trapdoor", sl.in(), false, true));
		}
		// island or table
		if (r.w() >= 6 && r.d() >= 6) {
			int[] p = r.center(s, 2, 1);
			if (p != null) for (int i = 0; i < 2; i++) {
				s.set(p[0] + i, r.y, p[1], cab);
				if (r.free(s, p[0] + i, p[1] + 1) || r.contains(p[0] + i, p[1] + 1)) s.set(p[0] + i, r.y, p[1] + 1, B.stair(st.wood + "_stairs", Direction.SOUTH, false));
			}
		}
	}

	static void table(Site s, Room r, Style st, int len) {
		int[] p = r.center(s, len, 1);
		if (p == null) return;
		String top = s.pick(new String[] {"white_carpet", st.rug + "_carpet", "spruce_pressure_plate", "birch_pressure_plate"});
		for (int i = 0; i < len; i++) {
			s.set(p[0] + i, r.y, p[1], B.of(st.wood + "_fence"));
			s.set(p[0] + i, r.y + 1, p[1], B.of(top));
			chair(s, r, p[0] + i, p[1] - 1, Direction.SOUTH, st);
			chair(s, r, p[0] + i, p[1] + 1, Direction.NORTH, st);
		}
		if (s.chance(0.5)) s.set(p[0] + len / 2, r.y + 1, p[1], B.of("potted_white_tulip"));
	}

	/** Chair facing dir (the way a seated person looks). */
	static void chair(Site s, Room r, int x, int z, Direction look, Style st) {
		if (!r.contains(x, z) || !s.in(x, z) || s.edge(x, z)) return;
		s.set(x, r.y, z, B.stair(st.wood + "_stairs", look.getOpposite(), false));
	}

	static void rug(Site s, Room r, String color) {
		int w = Math.max(1, r.w() - 4), d = Math.max(1, r.d() - 4);
		int x0 = r.x0 + 2, z0 = r.z0 + 2;
		for (int x = x0; x < x0 + w; x++) for (int z = z0; z < z0 + d; z++) if (r.free(s, x, z)) s.set(x, r.y, z, B.c(color, "carpet"));
	}

	static void plant(Site s, Room r) {
		Room.Slot sl = r.wall(s, 1, 1);
		if (sl == null) return;
		int x = sl.x(), z = sl.z();
		switch (s.pick(4)) {
			case 0 -> { s.set(x, r.y, z, B.of("potted_bamboo")); }
			case 1 -> { s.set(x, r.y, z, B.of("moss_block")); s.set(x, r.y + 1, z, B.of(s.chance(0.5) ? "azalea" : "flowering_azalea")); }
			case 2 -> { s.set(x, r.y, z, B.of("decorated_pot")); s.set(x, r.y + 1, z, B.leaves("jungle_leaves")); }
			default -> { s.set(x, r.y, z, B.of("potted_fern")); }
		}
	}

	static void lamp(Site s, Room r) {
		Room.Slot sl = r.wall(s, 1, 1);
		if (sl == null) return;
		s.set(sl.x(), r.y, sl.z(), B.of(s.chance(0.5) ? "dark_oak_fence" : "iron_chain"));
		s.set(sl.x(), r.y + 1, sl.z(), B.of(s.chance(0.5) ? "lantern" : "end_rod"));
	}

	static void shelves(Site s, Room r, Style st, int len) {
		Room.Slot sl = r.wall(s, len, 1);
		if (sl == null) return;
		for (int i = 0; i < len; i++) {
			int x = Room.ax(sl, i, 0), z = Room.az(sl, i, 0);
			s.set(x, r.y, z, B.of(s.chance(0.5) ? "bookshelf" : "chiseled_bookshelf"));
			s.set(x, r.y + 1, z, B.face(st.wood + "_shelf", sl.in()));
		}
	}

	static void desk(Site s, Room r, int x, int z, Direction look, Style st, boolean dual) {
		if (!r.free(s, x, z)) return;
		r.take(x, z);
		s.set(x, r.y, z, B.slab(s.pick(new String[] {"birch_slab", "smooth_quartz_slab", "dark_oak_slab", "spruce_slab"}), true));
		// monitor: open trapdoor on the far side of the desk
		s.set(x, r.y + 1, z, B.trap("dark_oak_trapdoor", look.getOpposite(), true, false));
		int cx = x - look.getStepX(), cz = z - look.getStepZ();
		if (r.free(s, cx, cz)) { r.take(cx, cz); s.set(cx, r.y, cz, B.stair("polished_blackstone_stairs", look.getOpposite(), false)); }
	}

	static void lights(Site s, Room r, BlockState fixture) {
		int stepX = r.w() > 7 ? 4 : r.w(), stepZ = r.d() > 7 ? 4 : r.d();
		for (int x = r.x0 + Math.min(stepX, r.w()) / 2; x <= r.x1; x += stepX)
			for (int z = r.z0 + Math.min(stepZ, r.d()) / 2; z <= r.z1; z += stepZ)
				if (r.h <= 3) s.set(x, r.y + r.h - 1, z, Palette.ceilingWarm());
				else s.set(x, r.y + r.h - 2, z, fixture);
	}

	static BlockState rodDown() {
		return B.face("end_rod", Direction.DOWN);
	}

	static void floor(Site s, Room r, BlockState f) {
		s.fill(r.x0, r.y - 1, r.z0, r.x1, r.y - 1, r.z1, f);
	}

	static void checker(Site s, Room r, BlockState a, BlockState b) {
		for (int x = r.x0; x <= r.x1; x++) for (int z = r.z0; z <= r.z1; z++) s.set(x, r.y - 1, z, ((x + z) & 1) == 0 ? a : b);
	}

	// ---------- room programs ----------

	static void furnish(Site s, Room r, Style st) {
		if (r.w() < 1 || r.d() < 1) return;
		BuildCommand.record(s, r);
		switch (r.type) {
			case "living" -> {
				floor(s, r, st.floor);
				rug(s, r, st.rug);
				sofa(s, r, st.sofa, Math.min(3, Math.max(2, r.w() - 2)));
				tv(s, r, r.area() > 30);
				if (s.chance(0.7)) sofa(s, r, st.sofa, 2);
				plant(s, r); lamp(s, r);
				if (s.chance(0.5)) shelves(s, r, st, 2);
				lights(s, r, B.hanging("lantern"));
			}
			case "kitchen" -> {
				if (s.chance(0.5)) checker(s, r, B.of("white_concrete"), B.of(s.chance(0.5) ? "black_concrete" : "light_gray_concrete"));
				else floor(s, r, B.of(s.pick(new String[] {"smooth_quartz", "polished_diorite", "white_terracotta", "mud_bricks"})));
				kitchen(s, r, st);
				if (r.area() >= 20) table(s, r, st, 2);
				plant(s, r);
				lights(s, r, rodDown());
			}
			case "dining" -> { floor(s, r, st.floor); table(s, r, st, Math.min(3, Math.max(1, r.w() - 4))); plant(s, r); shelves(s, r, st, 2); lights(s, r, B.hanging("lantern")); }
			case "bedroom" -> {
				floor(s, r, st.floor); rug(s, r, st.rug);
				bed(s, r, st.bed, r.w() >= 4 && r.d() >= 4);
				wardrobe(s, r);
				if (s.chance(0.6)) tv(s, r, false);
				if (r.area() > 20) { Room.Slot d = r.wall(s, 1, 2); if (d != null) desk(s, r, d.x(), d.z(), d.in().getOpposite(), st, false); }
				plant(s, r);
				lights(s, r, B.hanging("lantern"));
			}
			case "bath" -> {
				floor(s, r, B.of(s.pick(new String[] {"smooth_quartz", "white_concrete", "light_blue_terracotta", "polished_diorite", "prismarine_bricks"})));
				Room.Slot t = r.wall(s, 2, 1);
				if (t != null) for (int i = 0; i < 2; i++) s.set(Room.ax(t, i, 0), r.y, Room.az(t, i, 0), B.cauldron(3));
				Room.Slot w = r.wall(s, 1, 1);
				if (w != null) { s.set(w.x(), r.y, w.z(), B.stair("quartz_stairs", w.in().getOpposite(), false)); }
				Room.Slot k = r.wall(s, 1, 1);
				if (k != null) { s.set(k.x(), r.y, k.z(), B.slab("smooth_quartz_slab", true)); s.set(k.x(), r.y + 1, k.z(), B.face("tripwire_hook", k.in())); }
				lights(s, r, rodDown());
			}
			case "studio" -> {
				floor(s, r, st.floor);
				rug(s, r, st.rug);
				bed(s, r, st.bed, r.w() >= 5);
				kitchen(s, r, st);
				sofa(s, r, st.sofa, 2);
				tv(s, r, false);
				plant(s, r);
				lights(s, r, B.hanging("lantern"));
			}
			case "hotel" -> {
				floor(s, r, st.floor);
				rug(s, r, st.rug);
				bed(s, r, "white", true);
				tv(s, r, false);
				Room.Slot d = r.wall(s, 1, 2);
				if (d != null) desk(s, r, d.x(), d.z(), d.in().getOpposite(), st, false);
				sofa(s, r, st.sofa, 2);
				plant(s, r);
				lights(s, r, B.hanging("lantern"));
			}
			case "office" -> {
				floor(s, r, B.of(s.pick(new String[] {"gray_wool", "light_gray_wool", "blue_wool", "polished_andesite", "gray_concrete"})));
				for (int z = r.z0 + 1; z <= r.z1 - 1; z += 3)
					for (int x = r.x0 + 1; x <= r.x1 - 1; x += 2) desk(s, r, x, z, Direction.NORTH, st, true);
				plant(s, r); plant(s, r);
				Room.Slot c = r.wall(s, 1, 1);
				if (c != null) { s.set(c.x(), r.y, c.z(), B.of("iron_block")); s.set(c.x(), r.y + 1, c.z(), B.of("light_blue_stained_glass")); }
				lights(s, r, B.of("sea_lantern"));
			}
			case "trading" -> {
				// trailer office: dark carpet, rows of dual-monitor desks with red chairs, dim spots
				floor(s, r, B.of(s.chance(0.5) ? "gray_wool" : "black_wool"));
				for (int z = r.z0 + 1; z <= r.z1 - 1; z += 3) {
					for (int x = r.x0 + 1; x <= r.x1 - 1; x++) {
						if (!r.free(s, x, z)) continue;
						r.take(x, z);
						s.set(x, r.y, z, B.slab("dark_oak_slab", true));
						s.set(x, r.y + 1, z, B.trap(((x & 1) == 0) ? "dark_oak_trapdoor" : "spruce_trapdoor", Direction.NORTH, true, false));
						if ((x & 1) == 0 && r.free(s, x, z - 1)) { r.take(x, z - 1); s.set(x, r.y, z - 1, B.stair("red_wool_stairs", Direction.NORTH, false)); }
					}
				}
				plant(s, r);
				Room.Slot c = r.wall(s, 1, 1);
				if (c != null) { s.set(c.x(), r.y, c.z(), B.of("red_concrete")); s.set(c.x(), r.y + 1, c.z(), B.of("light_blue_stained_glass")); }
				lights(s, r, B.of("pearlescent_froglight"));
			}
			case "meeting" -> {
				floor(s, r, B.of("gray_wool"));
				int len = Math.max(1, Math.min(r.w() - 4, 6));
				int[] p = r.center(s, len, 1);
				if (p != null) for (int i = 0; i < len; i++) {
					s.set(p[0] + i, r.y, p[1], B.slab("smooth_quartz_slab", true));
					chair(s, r, p[0] + i, p[1] - 1, Direction.SOUTH, st);
					chair(s, r, p[0] + i, p[1] + 1, Direction.NORTH, st);
				}
				tv(s, r, true);
				plant(s, r);
				lights(s, r, rodDown());
			}
			case "lobby" -> {
				checker(s, r, B.of("smooth_quartz"), B.of(s.chance(0.5) ? "polished_diorite" : "polished_andesite"));
				Room.Slot d = r.wall(s, Math.min(5, Math.max(2, r.w() - 4)), 1);
				if (d != null) {
					int n = Math.min(5, Math.max(2, r.w() - 4));
					for (int i = 0; i < n; i++) s.set(Room.ax(d, i, 0), r.y, Room.az(d, i, 0), B.of(s.chance(0.5) ? "smooth_quartz" : "polished_deepslate"));
					s.set(Room.ax(d, n / 2, 0), r.y + 1, Room.az(d, n / 2, 0), B.trap("dark_oak_trapdoor", d.in().getOpposite(), true, false));
				}
				sofa(s, r, st.sofa, 3);
				sofa(s, r, st.sofa, 2);
				palm(s, r); palm(s, r);
				lights(s, r, rodDown());
			}
			case "storage" -> {
				floor(s, r, B.of("polished_andesite"));
				for (int i = 0; i < 4; i++) {
					Room.Slot sl = r.wall(s, 1, 1);
					if (sl == null) break;
					s.set(sl.x(), r.y, sl.z(), B.of(s.chance(0.5) ? "barrel" : "chest"));
					if (s.chance(0.5)) s.set(sl.x(), r.y + 1, sl.z(), B.of("barrel"));
				}
				lights(s, r, rodDown());
			}
			case "cell" -> {
				floor(s, r, B.of("smooth_stone"));
				bed(s, r, "white", false);
				Room.Slot w = r.wall(s, 1, 1);
				if (w != null) s.set(w.x(), r.y, w.z(), B.of("cauldron"));
				lights(s, r, rodDown());
			}
			case "ward" -> {
				floor(s, r, B.of("white_concrete"));
				for (int i = 0; i < 4; i++) bed(s, r, "white", false);
				Room.Slot m = r.wall(s, 1, 1);
				if (m != null) s.set(m.x(), r.y, m.z(), B.of("brewing_stand"));
				Room.Slot k = r.wall(s, 1, 1);
				if (k != null) s.set(k.x(), r.y, k.z(), B.cauldron(2));
				plant(s, r);
				lights(s, r, B.of("sea_lantern"));
			}
			case "garage" -> {
				floor(s, r, B.of("smooth_stone"));
				Room.Slot w = r.wall(s, 2, 1);
				if (w != null) for (int i = 0; i < 2; i++) { s.set(Room.ax(w, i, 0), r.y, Room.az(w, i, 0), B.of("smithing_table")); }
				Room.Slot w2 = r.wall(s, 1, 1);
				if (w2 != null) s.set(w2.x(), r.y, w2.z(), B.of("anvil"));
				lights(s, r, rodDown());
			}
			case "hall" -> {
				floor(s, r, st.floor);
				plant(s, r);
				lights(s, r, rodDown());
			}
			default -> lights(s, r, rodDown());
		}
	}

	static void palm(Site s, Room r) {
		Room.Slot sl = r.wall(s, 1, 1);
		if (sl == null) return;
		int x = sl.x(), z = sl.z();
		s.set(x, r.y, z, B.of("decorated_pot"));
		if (r.h >= 4) {
			s.set(x, r.y + 1, z, B.of("stripped_jungle_log"));
			s.set(x, r.y + 2, z, B.leaves("jungle_leaves"));
		}
	}

	/** Assign room types: biggest first from {@code big}, the smallest rooms get {@code small}. */
	static void assign(java.util.List<Room> rooms, String[] big, String[] small) {
		java.util.List<Room> sorted = new java.util.ArrayList<>(rooms);
		sorted.sort((a, b) -> b.area() != a.area() ? Integer.compare(b.area(), a.area()) : Integer.compare(a.x0 * 1000 + a.z0, b.x0 * 1000 + b.z0));
		int n = sorted.size();
		int ns = Math.min(small.length, Math.max(0, n - 1));
		for (int i = 0; i < n; i++) {
			Room r = sorted.get(i);
			if (i >= n - ns) r.type = small[n - 1 - i];
			else r.type = big[Math.min(i, big.length - 1)];
			if (r.area() <= 4 && !r.type.equals("bath")) r.type = "storage";
		}
	}
}
