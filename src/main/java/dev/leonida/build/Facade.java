package dev.leonida.build;

import dev.leonida.look.LookBlocks.Window;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * LOOK.md facade styles in whole blocks (1 block = 1 m):
 * 0 glass curtain-wall tower, 1 white balcony condo, 2 Art Deco pastel, 3 low-rise stucco,
 * 4 grey commercial, 5 warehouse. Facade cells, wall relief (balconies, eyebrows, fins, awnings,
 * speed lines, ledges, neon), parapets and roofs (hipped clay tiles, tower crowns, rooftop pools,
 * helipads, AC units). Windows are look-module window blocks that light up at night by themselves.
 */
final class Facade {
	private Facade() {}

	static final int[] FLOOR_H = {4, 3, 3, 3, 4, 6};
	static final int[] LOBBY_H = {6, 4, 4, 4, 4, 6};

	/** Per-building facade choice, picked from the footprint id only (never from the room RNG). */
	static final class Look {
		int style, tone, accent, awning, neon;
		/** Night neon district: 0 suburbs and the Keys, 1 downtown, 2 Vice Beach. */
		int lvl;
		long seed;
		BlockState wall, grimy, window, band, frame, trim, ground, roof;
		boolean balconies, hip, aged;
	}

	static long mix(long z) {
		z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
		z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return z ^ (z >>> 33);
	}

	static int hash(long seed, int a, int b, int c) {
		return (int) (mix(seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (c * 0x165667B19E3779F9L)) >>> 33);
	}

	static Look look(Site s, int style) {
		Look L = new Look();
		L.style = Math.max(0, Math.min(5, style));
		L.seed = mix(s.id * 31 + 7);
		int h = (int) (L.seed >>> 36);
		L.tone = h & 0xFF;
		L.accent = (h >> 8) & 0xF;
		L.awning = (h >> 12) & 0xF;
		L.neon = (h >> 16) & 0x3;
		L.aged = ((h >> 18) & 1) == 0;
		L.lvl = district(s);
		L.trim = Palette.trim();
		L.roof = ((h >> 19) & 1) == 0 ? Palette.membrane() : Palette.gravelRoof();
		switch (L.style) {
			case 0 -> {
				L.window = Palette.towerGlass(L.tone);
				L.wall = L.window;
				L.grimy = L.window;
				L.band = Palette.spandrel(L.tone);
				L.frame = Palette.frame(((h >> 20) & 1) == 0 ? 2 : 0);
				L.trim = L.frame;
				L.ground = Palette.clearGlass();
			}
			case 1 -> {
				L.wall = Palette.condo(L.tone, false);
				L.grimy = Palette.condo(L.tone, true);
				L.band = Palette.condoSlab(L.tone);
				L.window = Palette.window(Window.CONDO);
				L.frame = L.trim;
				L.ground = Palette.clearGlass();
				L.balconies = Math.floorMod(h >> 21, 10) < 7;
			}
			case 2 -> {
				L.wall = Palette.pastel(L.tone, false);
				L.grimy = Palette.pastel(L.tone, true);
				L.band = L.trim;
				L.window = Palette.window(Window.DECO);
				L.frame = L.trim;
				L.ground = Palette.window(Window.SHOPFRONT);
			}
			case 3 -> {
				L.wall = Palette.earth(L.tone, false);
				L.grimy = Palette.earth(L.tone, true);
				L.band = L.wall;
				L.window = Palette.window(Window.HOUSE);
				L.frame = L.trim;
				L.ground = Palette.window(Window.SHOPFRONT);
			}
			case 4 -> {
				L.wall = Palette.panel(L.tone);
				L.grimy = L.wall;
				L.band = L.wall;
				L.window = Palette.window(Window.RIBBON);
				L.frame = Palette.frame(0);
				L.ground = Palette.window(Window.SHOPFRONT);
			}
			default -> {
				L.wall = Palette.metal(L.tone);
				L.grimy = L.wall;
				L.band = L.wall;
				L.window = Palette.window(Window.CLERESTORY);
				L.frame = Palette.frame(1);
				L.trim = Palette.concrete();
				L.ground = Palette.rollUp(((h >> 22) & 1) == 0);
				L.roof = Palette.metalRoof();
			}
		}
		return L;
	}

	/** Copy the facade colours into the building's style (decorators read st.wall/trim/accent/glass). */
	static void apply(Look L, Style st) {
		st.wall = L.wall; st.trim = L.trim; st.accent = L.frame; st.glass = L.window; st.roof = L.roof;
	}

	/** Floor heights from the style; floor count from the footprint height when the map gives one. */
	static void configure(Site s, Look L, Shell.Spec sp) {
		sp.h = FLOOR_H[L.style];
		sp.groundH = Math.max(sp.groundH, LOBBY_H[L.style]);
		if (s.heightM > 0 && sp.planGround) {
			// roofY - baseY + 1 = groundH + (floors - 1) * h
			int f = 1 + Math.max(0, (int) Math.round((s.heightM - sp.groundH) / (double) sp.h));
			sp.floors = Math.max(1, Math.min(f, 70));
		}
		int w = sp.x1 - sp.x0 + 1, d = sp.z1 - sp.z0 + 1;
		L.hip = L.style == 3 && sp.floors <= 2 && w <= 30 && d <= 30 && sp.lift == 0;
		sp.parapet = switch (L.style) {
			case 0 -> 2;
			case 2 -> 4;
			case 3 -> L.hip ? 0 : 2;
			default -> 2;
		};
	}

	static int along(Direction out, int x, int z) {
		return (out == Direction.NORTH || out == Direction.SOUTH) ? x : z;
	}

	/** Facade cell of floor f, row r (0 .. fh-2). */
	static BlockState block(Site s, Look L, Shell.Spec sp, int x, int z, int f, int r, int fh) {
		Direction out = s.outward(x, z);
		boolean corner = s.corner(x, z);
		int t = along(out, x, z);
		boolean front = out == Direction.NORTH;
		boolean ground = f == 0;
		boolean shopfront = ground && (front || sp.storefront && out != Direction.SOUTH);
		BlockState wall = ground && r <= 1 ? L.grimy : L.wall;
		switch (L.style) {
			case 0 -> {
				if (corner || Math.floorMod(t, 3) == 0) return L.frame;
				if (ground) return r == fh - 2 ? L.frame : L.ground;
				return L.window;
			}
			case 1 -> {
				if (corner || Math.floorMod(t, 4) == 0) return wall;
				if (ground) return front ? (r <= 2 ? L.ground : L.wall) : (r >= 1 && r <= 2 ? L.window : wall);
				return L.window;
			}
			case 2 -> {
				if (corner) return L.trim;
				if (shopfront) {
					if (Math.floorMod(t, 6) == 0) return L.trim;
					return r <= 2 ? L.ground : L.wall;
				}
				int m = Math.floorMod(t, 4);
				boolean win = m == 1 || m == 2;
				if (ground) return win && r >= 1 && r <= 2 ? L.window : wall;
				return win ? L.window : wall;
			}
			case 3 -> {
				if (corner) return L.trim;
				int m = Math.floorMod(t, 4);
				if (ground && sp.storefront && front) return m == 0 ? wall : (r <= 2 ? L.ground : L.wall);
				boolean win = m == 1 || m == 2;
				int hi = fh >= 4 ? 2 : 1;
				return win && r >= 1 && r <= hi ? L.window : wall;
			}
			case 4 -> {
				if (corner) return L.wall;
				if (shopfront) return Math.floorMod(t, 4) == 0 ? L.frame : (r <= 2 ? L.ground : L.wall);
				if (r == 0) return L.wall;
				return Math.floorMod(t, 4) == 0 ? L.frame : L.window;
			}
			default -> {
				if (r == 0) return Palette.concrete();
				if (corner) return L.frame;
				if (r == fh - 2) return Palette.concrete();
				if (ground && front) {
					int k = Math.floorMod(t - sp.x0, 14);
					if (k >= 5 && k <= 8 && r <= 3) return L.ground;
				}
				if (Math.floorMod(t - sp.x0, 14) == 0 || Math.floorMod(t - sp.x0, 14) == 13) return Palette.concrete();
				int m = Math.floorMod(t, 4);
				if (r == fh - 3 && (m == 1 || m == 2)) return L.window;
				return L.wall;
			}
		}
	}

	/** Edge cell of the slab row between floors (the bottom of floor f >= 1). */
	static BlockState slabEdge(Site s, Look L, int x, int z) {
		if (L.style == 0) return s.corner(x, z) ? L.frame : L.band;
		if (L.style == 2) return s.corner(x, z) ? L.trim : L.wall;
		return L.band;
	}

	/** Roof slab edge (top of the walls). */
	static BlockState roofEdge(Site s, Look L, int x, int z) {
		return switch (L.style) {
			case 0 -> L.frame;
			case 1 -> L.band;
			default -> L.wall;
		};
	}

	/** Wall relief outside the footprint: balconies, eyebrows, awnings, fins, speed lines, ledges, entry canopy. */
	static void relief(Site s, Look L, Shell.Spec sp, Shell.Result res) {
		int ry = res.roofY;
		// Deco fin with neon at the middle of the street front (drawn first so it wins over eyebrows)
		if (L.style == 2 && (sp.floors >= 4 || sp.floors >= 2 && sp.x1 - sp.x0 >= 9)) {
			int cx = sp.floors >= 4 ? (sp.x0 + sp.x1) / 2 : (((L.seed >>> 3) & 1) == 0 ? sp.x0 + 3 : sp.x1 - 3);
			if (Math.abs(cx - res.doorX) <= 1) cx = res.doorX + 3 <= sp.x1 - 2 ? res.doorX + 3 : res.doorX - 4;
			int fz = Shell.frontZ(s, cx, sp);
			if (fz >= 0) {
				int top = ry + sp.parapet + 5;
				for (int y = s.baseY + sp.groundH; y <= top; y++) {
					s.setOut(cx, y, fz - 1, Palette.fin(L.accent, Direction.NORTH));
					if (y > s.baseY + sp.groundH) {
						s.setOut(cx - 1, y, fz - 1, Palette.neon(L.neon, Direction.NORTH, true));
						s.setOut(cx + 1, y, fz - 1, Palette.neon(L.neon, Direction.NORTH, true));
					}
				}
				for (int y = ry + sp.parapet + 1; y <= top; y++) s.set(cx, y, fz, L.trim);
				s.set(cx, top + 1, fz, Palette.trimSlab(false));
			}
		}
		for (int x = sp.x0; x <= sp.x1; x++) for (int z = sp.z0; z <= sp.z1; z++) {
			if (!s.in(x, z) || !s.edge(x, z) || s.corner(x, z)) continue;
			Direction out = s.outward(x, z);
			int ox = x + out.getStepX(), oz = z + out.getStepZ();
			if (s.in(ox, oz)) continue;
			boolean front = out == Direction.NORTH, back = out == Direction.SOUTH;
			int t = along(out, x, z);
			switch (L.style) {
				case 1 -> {
					if (!L.balconies || !(front || back)) break;
					for (int f = 1; f < sp.floors; f++) {
						int y = sp.levelY(s, f) - 1;
						s.setOut(ox, y, oz, L.band);
						s.setOut(ox, y + 1, oz, Palette.railing(out));
					}
				}
				case 2 -> {
					for (int f = 1; f <= sp.floors; f++) {
						int y = sp.levelY(s, f) - 1;
						if (f == 1 && (front || sp.storefront && !back)) {
							if (!(front && Math.abs(x - res.doorX) <= 1 && x != res.doorX + 1)) s.setOut(ox, y, oz, Palette.awning(L.awning, out));
						} else if (f < sp.floors) {
							s.setOut(ox, y, oz, Palette.eyebrow(L.accent, out));
						}
					}
					if (!back) {
						s.setOut(ox, ry, oz, Palette.neon(L.neon, out, false));
						for (int k = 1; k <= 3; k++) s.setOut(ox, ry + k, oz, Palette.speedLine(L.accent, out));
						// neon outline along the parapet top
						s.setOut(ox, ry + 4, oz, Palette.neon(L.neon, out, false));
					}
					if (front || sp.storefront && !back) {
						// neon band just above the ground floor awnings, second colour
						if (sp.floors >= 2) s.setOut(ox, sp.levelY(s, 1), oz, Palette.neon(L.neon + 2, out, false));
						// vertical neon tubes framing the street front, next to the corners
						if (s.corner(x - out.getStepZ(), z + out.getStepX()) || s.corner(x + out.getStepZ(), z - out.getStepX()))
							for (int y = sp.levelY(s, 1) + 1; y < ry; y++) s.setOut(ox, y, oz, Palette.neon(L.neon, out, true));
					}
				}
				case 3 -> {
					if (sp.storefront && front && sp.floors >= 1) s.setOut(ox, sp.levelY(s, 1) - 1, oz, Palette.awning(L.awning, out));
				}
				case 4 -> {
					for (int f = 1; f < sp.floors; f++) s.setOut(ox, sp.levelY(s, f) - 1, oz, Palette.concreteSlab(true));
				}
				case 0 -> {
					// slim vertical fins on every third mullion of tall towers
					if (sp.floors >= 8 && Math.floorMod(t, 6) == 0)
						for (int y = s.baseY + sp.groundH; y <= ry + sp.parapet; y++) s.setOut(ox, y, oz, Palette.fin(4, out));
				}
				default -> {
					int k = Math.floorMod(t - sp.x0, 14);
					if (front) {
						// loading bay: metal canopy over the roll-up door, wall light, kerb and bollards
						int cy = s.baseY + 5;
						if (k >= 3 && k <= 10) for (int dd = 0; dd < 2; dd++) s.setOut(ox, cy, oz - dd, Palette.slab(Palette.metalRoofSlab(), false));
						if (k == 3 || k == 10) {
							s.setOut(ox, cy - 1, oz, B.hanging("lantern"));
							s.setOut(ox, s.baseY, oz - 1, B.of("yellow_concrete"));
							s.setOut(ox, s.baseY + 1, oz - 1, B.of("yellow_concrete"));
						}
					} else if (k == 13) {
						// downpipes on the sides and back
						for (int y = s.baseY; y <= ry; y++) s.setOut(ox, y, oz, B.of("iron_chain"));
					}
				}
			}
		}
		// entry canopy over the main door
		int dz = Shell.frontZ(s, res.doorX, sp);
		if (dz >= 0 && L.style != 2 && !(L.style == 3 && sp.storefront)) {
			int y = s.baseY + Math.min(sp.groundH - 2, 4);
			BlockState c = L.style == 5 ? Palette.concreteSlab(false) : Palette.trimSlab(false);
			int depth = L.style == 0 || L.style == 1 ? 3 : 2;
			for (int x = res.doorX - 2; x <= res.doorX + 1; x++) for (int k = 1; k <= depth; k++) s.setOut(x, y, dz - k, c);
			if (L.style == 0 || L.style == 1) for (int x = res.doorX - 2; x <= res.doorX + 1; x++) s.setOut(x, y - 1, dz - depth, B.light(7));
		}
	}

	static final double[] LIT = {0.38, 0.45, 0.42, 0.32, 0.35, 0.08};

	/**
	 * Stand-in night windows while look window blocks are off: a lit block just inside some windows (warm homes and
	 * shops, cool offices, blue TV light). Look windows glow by themselves, so this does nothing when they are on.
	 */
	static void nightPanels(Site s, Look L, Shell.Spec sp) {
		if (Palette.LOOK_WINDOWS) return;
		for (int x = Math.max(sp.x0, s.cx0); x <= Math.min(sp.x1, s.cx1); x++) for (int z = Math.max(sp.z0, s.cz0); z <= Math.min(sp.z1, s.cz1); z++) {
			if (!s.in(x, z) || !s.edge(x, z) || s.corner(x, z)) continue;
			Direction out = s.outward(x, z);
			int ix = x - out.getStepX(), iz = z - out.getStepZ();
			if (!s.in(ix, iz) || s.edge(ix, iz)) continue;
			for (int f = 0; f < sp.floors; f++) {
				int y0 = sp.levelY(s, f), fh = sp.height(f);
				int row = L.style == 5 ? fh - 3 : Math.min(fh - 2, L.style == 0 || L.style == 4 ? 2 : 1);
				int y = y0 + row;
				BlockState w = s.get(x, y, z);
				if (w != L.window && w != L.ground) continue;
				if (!s.get(ix, y, iz).isAir()) continue;
				boolean shop = w == L.ground;
				int hsh = hash(L.seed, x, y, z);
				// light whole floors of a bay together so a floor reads as one lit flat or office
				int bay = hash(L.seed, along(out, x, z) / 4, f, 7);
				double p = shop ? 0.6 : LIT[L.style];
				if ((bay & 0xFFFF) / 65536.0 >= p) continue;
				BlockState lit;
				if (shop) lit = B.of("ochre_froglight");
				else if (L.style == 0 || L.style == 4) lit = ((bay >> 16) % 6 == 0) ? B.of("ochre_froglight") : B.of("sea_lantern");
				else lit = ((bay >> 16) % 8 == 0) ? B.of("sea_lantern") : ((hsh >> 16) & 3) == 0 ? B.of("shroomlight") : B.of("ochre_froglight");
				s.set(ix, y, iz, lit);
			}
		}
	}

	/** Parapets, hipped tile roofs, crowns, pools, helipads, AC units. */
	static void roof(Site s, Look L, Shell.Spec sp, Shell.Result res) {
		int ry = res.roofY;
		if (L.hip) { hipRoof(s, L, sp, ry); return; }
		for (int x = Math.max(sp.x0, s.cx0); x <= Math.min(sp.x1, s.cx1); x++) for (int z = Math.max(sp.z0, s.cz0); z <= Math.min(sp.z1, s.cz1); z++) {
			if (!s.in(x, z) || !s.edge(x, z)) continue;
			switch (L.style) {
				case 0 -> { s.set(x, ry + 1, z, L.frame); s.set(x, ry + 2, z, L.frame); }
				case 2 -> {
					for (int k = 1; k <= 3; k++) s.set(x, ry + k, z, L.wall);
					s.set(x, ry + 4, z, L.trim);
				}
				default -> { s.set(x, ry + 1, z, L.wall); s.set(x, ry + 2, z, Palette.parapetCap()); }
			}
		}
		int w = sp.x1 - sp.x0 + 1, d = sp.z1 - sp.z0 + 1;
		int cx = (sp.x0 + sp.x1) / 2, cz = (sp.z0 + sp.z1) / 2;
		boolean tall = sp.floors >= 8;
		if (tall && L.style == 0) {
			if ((L.seed & 1) == 0 && w >= 11 && d >= 11) {
				Deco.helipad(s, cx, cz, ry + 1);
			} else if (w >= 8 && d >= 8) {
				// stepped glass crown with a frame cap and an antenna
				int ix0 = sp.x0 + 2, ix1 = sp.x1 - 2, iz0 = sp.z0 + 2, iz1 = sp.z1 - 2;
				int ch = 4 + (int) ((L.seed >>> 8) & 3);
				for (int x = ix0; x <= ix1; x++) for (int z = iz0; z <= iz1; z++) {
					boolean e = x == ix0 || x == ix1 || z == iz0 || z == iz1;
					if (e) {
						boolean c = (x == ix0 || x == ix1) && (z == iz0 || z == iz1);
						s.fill(x, ry + 1, z, x, ry + ch, z, c || Math.floorMod(x + z, 3) == 0 ? L.frame : L.window);
					}
					s.set(x, ry + ch + 1, z, L.frame);
				}
				for (int k = 2; k <= 6; k++) s.set(cx, ry + ch + k, cz, B.of("iron_bars"));
				s.set(cx, ry + ch + 7, cz, B.of("lightning_rod"));
				s.set(cx, ry + ch + 8, cz, B.of("redstone_lamp"));
			}
		} else if (tall && L.style == 1 && w >= 10 && d >= 10) {
			roofPool(s, L, sp, ry);
		} else if (L.style == 2 && sp.floors >= 2 && sp.floors < 4) {
			// small stepped Deco tower over the entrance
			int dx = res.doorX;
			for (int x = dx - 2; x <= dx + 1; x++) for (int z = sp.z0; z <= sp.z0 + 2; z++) {
				if (!s.in(x, z)) continue;
				boolean side = x == dx - 2 || x == dx + 1;
				s.fill(x, ry + 1, z, x, ry + 6, z, side ? L.trim : L.wall);
				s.set(x, ry + 7, z, side ? L.trim : Palette.trimSlab(false));
			}
			int fz = Shell.frontZ(s, dx, sp);
			if (fz >= 0) for (int y = ry + 5; y <= ry + 6; y++) for (int x = dx - 1; x <= dx; x++) s.setOut(x, y, fz - 1, Palette.neon(L.neon + 1, Direction.NORTH, false));
		}
		if (!(tall && L.style == 0) && (L.style != 2 || w * d > 200)) acUnits(s, L, sp, ry + 1);
	}

	static void acUnits(Site s, Look L, Shell.Spec sp, int y) {
		int n = 0;
		for (int x = sp.x0 + 2; x <= sp.x1 - 2 && n < 6; x += 4) for (int z = sp.z0 + 3; z <= sp.z1 - 2 && n < 6; z += 5) {
			if ((hash(L.seed, x, y, z) & 3) != 0) continue;
			if (!s.get(x, y, z).isAir()) continue;
			s.set(x, y, z, Palette.acUnit());
			if (s.get(x + 1, y, z).isAir()) s.set(x + 1, y, z, Palette.acUnit());
			n++;
		}
	}

	static void roofPool(Site s, Look L, Shell.Spec sp, int ry) {
		int x0 = sp.x0 + 2, x1 = sp.x1 - 2, z0 = sp.z0 + 2, z1 = Math.min(sp.z1 - 2, sp.z0 + 12);
		int px0 = x0 + 1, px1 = x1 - 1, pz0 = z0 + 1, pz1 = z1 - 3;
		if (px1 - px0 < 2 || pz1 - pz0 < 1) return;
		s.fill(x0, ry, z0, x1, ry, z1, Palette.terrazzo());
		for (int x = px0 - 1; x <= px1 + 1; x++) for (int z = pz0 - 1; z <= pz1 + 1; z++) {
			boolean e = x == px0 - 1 || x == px1 + 1 || z == pz0 - 1 || z == pz1 + 1;
			s.set(x, ry + 1, z, e ? Palette.trimSlab(false) : B.of("water"));
			if (!e) s.set(x, ry, z, B.of("light_blue_concrete"));
		}
		for (int x = px0; x <= px1; x += 2) {
			s.set(x, ry + 1, z1 - 1, B.stair("white_wool_stairs", Direction.NORTH, false));
			s.set(x, ry + 1, z1, B.slab("white_wool_slab", false));
		}
		s.set(x0, ry + 1, z1, B.of("potted_bamboo"));
		s.set(x1, ry + 1, z1, B.of("potted_fern"));
		s.set(x0, ry + 1, z0, B.of("lantern"));
		s.set(x1, ry + 1, z0, B.of("lantern"));
		// pool house / lift overrun behind the pool deck
		int bz0 = z1 + 1, bz1 = Math.min(sp.z1 - 2, bz0 + 3);
		if (bz1 > bz0) {
			for (int x = x0; x <= Math.min(x1, x0 + 5); x++) for (int z = bz0; z <= bz1; z++) {
				boolean e = x == x0 || x == Math.min(x1, x0 + 5) || z == bz0 || z == bz1;
				if (e) s.fill(x, ry + 1, z, x, ry + 3, z, z == bz0 && x > x0 && x < Math.min(x1, x0 + 5) ? L.window : L.wall);
				s.set(x, ry + 4, z, L.band);
			}
		}
	}

	/** Hipped clay-tile roof, about 27 degrees (half a block per block), one block eave overhang. */
	static void hipRoof(Site s, Look L, Shell.Spec sp, int ry) {
		BlockState lo = Palette.slab(Palette.tileSlab(L.aged), false), hi = Palette.slab(Palette.tileSlab(L.aged), true), full = Palette.tileFull(L.aged);
		int ex0 = sp.x0 - 1, ex1 = sp.x1 + 1, ez0 = sp.z0 - 1, ez1 = sp.z1 + 1;
		for (int x = ex0; x <= ex1; x++) for (int z = ez0; z <= ez1; z++) {
			boolean inside = s.in(x, z);
			if (!inside) {
				boolean near = false;
				for (int dx = -1; dx <= 1 && !near; dx++) for (int dz = -1; dz <= 1; dz++) if (s.in(x + dx, z + dz)) { near = true; break; }
				if (!near) continue;
				s.setOut(x, ry, z, lo);
				continue;
			}
			int dist = Math.min(Math.min(x - ex0, ex1 - x), Math.min(z - ez0, ez1 - z));
			int y = ry + dist / 2;
			boolean top = (dist & 1) == 1;
			for (int k = ry + 1; k < y; k++) s.set(x, k, z, full);
			if (y > ry) s.set(x, y, z, top ? hi : lo);
			else if (top) s.set(x, ry, z, s.edge(x, z) ? hi : full);
		}
	}

	// ------------------------------------------------------------------ night neon by district

	static final int SUBURB = 0, DOWNTOWN = 1, VICE_BEACH = 2;
	/** Neon colour indices (Palette.neon): pink, cyan, violet, blue, gold, white, mint. */
	private static final int[] VB_COL = {0, 1, 2, 6, 0, 1, 2, 6, 3};
	private static final int[] DT_COL = {1, 2, 5, 1, 5};
	private static final int[] SIGN_COL = {0, 1, 2, 3, 6, 0, 1, 2};

	/** District from the first polygon corner (once per building): Vice Beach east of x 1500, downtown box, else suburbs. */
	static int district(Site s) {
		java.util.List<int[]> poly = s.fp.polygon();
		if (poly == null || poly.isEmpty()) return SUBURB;
		int x = poly.get(0)[0], z = poly.get(0)[1];
		if (x > 1500) return VICE_BEACH;
		if (x >= -1150 && x <= 300 && z >= 100 && z <= 1250) return DOWNTOWN;
		return SUBURB;
	}

	static boolean commercial(Site s) {
		return switch (s.kind) {
			case SHOP, GAS_STATION, MOTEL, CLUB, HOTEL, MALL, BANK -> true;
			default -> false;
		};
	}

	/**
	 * GTA VI night neon on the generic facades, scaled by district: Vice Beach heavy (outlines, signage bands, corner
	 * tubes, glass-block glow), downtown medium (LED crown rings and vertical accent strips on towers, lit lobby band),
	 * commercial kinds get a sign everywhere, suburbs only the odd sign. Deterministic from the building seed; only
	 * edge cells of the current chunk are visited.
	 */
	static void nightNeon(Site s, Look L, Shell.Spec sp, Shell.Result res) {
		int h = hash(L.seed, 0x4E, 0x30, 0x4E);
		int ry = res.roofY;
		boolean tall = sp.floors >= 8;
		boolean com = commercial(s) || sp.storefront;
		int lvl = L.lvl;
		int c1, c2;
		if (lvl == VICE_BEACH) { c1 = VB_COL[Math.floorMod(h, VB_COL.length)]; c2 = VB_COL[Math.floorMod(h >> 4, VB_COL.length)]; }
		else if (lvl == DOWNTOWN) { c1 = DT_COL[Math.floorMod(h, DT_COL.length)]; c2 = DT_COL[Math.floorMod(h >> 4, DT_COL.length)]; }
		else { c1 = SIGN_COL[Math.floorMod(h, SIGN_COL.length)]; c2 = c1 + 1; }
		if (c2 == c1) c2 = c1 + 1;
		int roll = (h >>> 8) & 0xFF;
		boolean crown = false, strips = false, outline = false, band = false, sign = false, lobby = false, glow = false;
		switch (lvl) {
			case VICE_BEACH -> {
				if (L.style == 0 || L.style == 1) { crown = tall || sp.floors >= 5; strips = tall || roll < 160; band = com || roll < 140; }
				else if (L.style == 2) { glow = roll < 170; band = false; }
				else if (L.style != 5) { outline = true; band = com || roll < 170; strips = roll < 110; glow = L.style == 3 && roll < 90; }
				else sign = roll < 120;
			}
			case DOWNTOWN -> {
				if (L.style == 0 && tall) { crown = true; strips = roll < 150; lobby = true; }
				else if (L.style == 1 && tall) { crown = roll < 170; strips = roll < 70; lobby = roll < 128; }
				else if (L.style == 0 || L.style == 4) { lobby = roll < 128; sign = com; }
				else sign = com || roll < 40;
			}
			default -> sign = com ? roll < 200 : roll < 14;
		}
		if (!(crown || strips || outline || band || sign || lobby || glow)) return;
		int ax = Math.max(sp.x0, s.cx0 - 1), bx = Math.min(sp.x1, s.cx1 + 1);
		int az = Math.max(sp.z0, s.cz0 - 1), bz = Math.min(sp.z1, s.cz1 + 1);
		int signY = sp.floors >= 2 ? sp.levelY(s, 1) : ry + 1;
		int signW = 2 + (roll & 3);
		int dx = res.doorX;
		for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) {
			if (!s.in(x, z) || !s.edge(x, z) || s.corner(x, z)) continue;
			Direction out = s.outward(x, z);
			int ox = x + out.getStepX(), oz = z + out.getStepZ();
			if (s.in(ox, oz)) continue;
			boolean front = out == Direction.NORTH, back = out == Direction.SOUTH;
			boolean nextToCorner = s.corner(x - out.getStepZ(), z + out.getStepX()) || s.corner(x + out.getStepZ(), z - out.getStepX());
			if (crown) {
				// LED crown: rings at the roof line and on the parapet top, all four sides
				s.setOut(ox, ry, oz, Palette.neon(c1, out, false));
				s.setOut(ox, ry + sp.parapet, oz, Palette.neon(c2, out, false));
			}
			if (strips && !back && nextToCorner) {
				// downtown: accent strips on the upper quarter only (they lead the eye to the crown); Vice Beach: full height
				int y0 = lvl == DOWNTOWN ? Math.max(s.baseY + sp.groundH, ry - (ry - s.baseY) / 4) : s.baseY + sp.groundH;
				for (int y = y0; y < ry; y++) s.setOut(ox, y, oz, Palette.neon(c1, out, true));
			}
			if (outline) s.setOut(ox, ry + 1, oz, Palette.neon(c1, out, false));
			if (front || sp.storefront && !back) {
				int y = signY;
				if (band) s.setOut(ox, y, oz, Palette.neon(c2, out, false));
				else if (sign && front && Math.abs(x - dx) <= signW) s.setOut(ox, y, oz, Palette.neon(c1, out, false));
				if (sign && front && Math.abs(x - dx) == signW + 1) s.setOut(ox, y + 1, oz, Palette.neon(c2, out, true));
				if (lobby && front) s.setOut(ox, s.baseY + sp.groundH - 1, oz, Palette.neon(5, out, false));
			}
		}
		if (glow) {
			// glowing column beside the entrance as a vertical cyan tube (low block light) instead of a light-15 block in
			// the wall, which washed the surrounding stucco with warm shader block light
			int gx = dx + 3 <= sp.x1 - 1 ? dx + 3 : dx - 4;
			if (gx >= s.cx0 && gx <= s.cx1) {
				int fz = Shell.frontZ(s, gx, sp);
				if (fz >= 1) for (int y = s.baseY + 1; y < ry; y++) s.setOut(gx, y, fz - 1, Palette.neon(1, Direction.NORTH, true));
			}
		}
	}
}
