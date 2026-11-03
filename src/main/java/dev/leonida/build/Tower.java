package dev.leonida.build;

import dev.leonida.core.api.BuildingGenerator;
import dev.leonida.look.LookBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tall buildings in the leonidastate3d.com look (mcmods/ref/ls3d/FACADES.md), in blocks.
 * <ul>
 * <li>Style 0, curtain-wall glass tower: one glass tint per building, metal mullions every 2 blocks, a dark spandrel band
 * on every 4-block floor slab, a full-glass lobby, a metal parapet cap, and an inset crown on the tallest.</li>
 * <li>Style 1, residential tower: white or cream walls, wide windows in 4-block bays, glass balcony railings with white
 * slab edges on about 70 percent of buildings, a glass lobby and a white parapet.</li>
 * </ul>
 * Floors, interiors and the elevator come from {@link Shell#build}; this class then redraws the outer wall and adds
 * the balconies, parapet and crown. Everything is chosen from the building id, with no static mutable state.
 */
final class Tower {
	private Tower() {}

	static final int GLASS = 0, CONDO = 1;
	private static final LookBlocks.GlassTint[] TINTS = LookBlocks.GlassTint.values();
	private static final LookBlocks.CondoWhite[] WHITES = {LookBlocks.CondoWhite.WARM, LookBlocks.CondoWhite.CREAM,
			LookBlocks.CondoWhite.COOL, LookBlocks.CondoWhite.WARM, LookBlocks.CondoWhite.SAND};
	private static final LookBlocks.Frame[] MULLIONS = {LookBlocks.Frame.SILVER, LookBlocks.Frame.STEEL, LookBlocks.Frame.DARK,
			LookBlocks.Frame.SILVER};

	/** Per-building choices, fixed by the id. */
	private static final class Look {
		int style, floorH, groundH;
		BlockState glass, spandrel, mullion, lobby, lobbyFrame, wall, slabEdge, window, railing, cap, capTop;
		boolean balconies, crown;
	}

	/** Builds the whole tall building into the site. Returns false to fall back to the generic path. */
	static boolean build(Site s) {
		if (s.W < 6 || s.D < 6) return false;
		long h = Facade.mix(s.id * 0x2545F4914F6CDD1DL + 0x7A11L);
		Look L = new Look();
		L.style = s.style == 0 || s.style == 1 ? s.style
				: (s.kind == BuildingGenerator.Kind.APARTMENT || s.kind == BuildingGenerator.Kind.HOTEL) ? ((h & 7) < 6 ? CONDO : GLASS)
				: ((h & 7) < 2 ? CONDO : GLASS);
		L.floorH = L.style == GLASS ? 4 : 3;
		L.groundH = L.style == GLASS ? 6 : 4;
		int total = s.heightM > 0 ? s.heightM : s.floors * L.floorH + L.groundH;
		int floors = Math.max(10, 1 + Math.max(0, (total - L.groundH - 3) / L.floorH));
		L.crown = floors >= 18 && s.W >= 12 && s.D >= 12;
		if (L.style == GLASS) {
			LookBlocks.GlassTint t = TINTS[(int) ((h >>> 8) % TINTS.length)];
			L.glass = LookBlocks.glass(t).defaultBlockState();
			L.spandrel = LookBlocks.spandrel(t).defaultBlockState();
			L.mullion = LookBlocks.frame(MULLIONS[(int) ((h >>> 16) % MULLIONS.length)]).defaultBlockState();
			L.lobbyFrame = LookBlocks.frame(LookBlocks.Frame.DARK).defaultBlockState();
			L.cap = L.mullion;
			L.capTop = LookBlocks.frame(LookBlocks.Frame.DARK).defaultBlockState();
		} else {
			LookBlocks.CondoWhite w = WHITES[(int) ((h >>> 8) % WHITES.length)];
			L.wall = LookBlocks.condo(w).defaultBlockState();
			L.slabEdge = LookBlocks.condoSlab(w).defaultBlockState();
			L.window = LookBlocks.window(LookBlocks.Window.CONDO).defaultBlockState();
			L.balconies = ((h >>> 20) % 10) < 7;
			L.lobbyFrame = LookBlocks.TRIM_WHITE.defaultBlockState();
			L.cap = L.wall;
			L.capTop = LookBlocks.TRIM_WHITE.defaultBlockState();
		}
		L.lobby = LookBlocks.CLEAR_GLASS.defaultBlockState();

		Style st = Style.pastel(s);
		st.wall = L.style == GLASS ? L.glass : L.wall;
		st.glass = L.style == GLASS ? L.glass : L.window;
		st.trim = L.style == GLASS ? L.mullion : L.capTop;
		st.accent = st.trim;
		st.roof = B.of("gray_concrete");
		st.base = B.of("polished_andesite");
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = floors;
		sp.h = L.floorH;
		sp.groundH = L.groundH;
		sp.windows = Shell.Windows.CURTAIN;
		sp.elevator = true;
		sp.parapet = 0;
		sp.minRoom = 4;
		sp.maxRoom = L.style == CONDO ? 8 : 14;
		sp.groundBig = new String[] {"lobby", "lobby", "hall"};
		sp.groundSmall = new String[] {"storage"};
		sp.upperBig = L.style == CONDO ? new String[] {"studio", "living", "bedroom", "studio", "kitchen"}
				: new String[] {"office", "office", "meeting", "office"};
		sp.upperSmall = new String[] {"bath", "storage"};
		sp.doubleDoor = true;
		sp.entranceDoor = "waxed_copper_door";
		Shell.Result res = Shell.build(s, st, sp);

		int by = s.baseY, roofY = res.roofY;
		for (int x = Math.max(0, s.cx0 - 1); x <= Math.min(s.W - 1, s.cx1 + 1); x++) {
			for (int z = Math.max(0, s.cz0 - 1); z <= Math.min(s.D - 1, s.cz1 + 1); z++) {
				if (!s.edge(x, z)) continue;
				Direction out = s.outward(x, z);
				if (out == null) continue;
				int t = (out == Direction.NORTH || out == Direction.SOUTH) ? x : z;
				boolean corner = s.corner(x, z);
				int ox = x + out.getStepX(), oz = z + out.getStepZ();
				int endDist = endDistance(s, x, z, out);
				for (int y = by; y < roofY; y++) {
					BlockState cur = s.get(x, y, z);
					if (cur != null && cur.getBlock() instanceof DoorBlock) continue;
					int rel = y - by;
					s.set(x, y, z, wall(L, rel, t, corner));
					if (L.style == CONDO && L.balconies && !corner && endDist >= 2 && rel >= L.groundH) balcony(s, L, ox, y, oz, rel, out);
				}
				// parapet or cap: 2 blocks, then a top course
				s.set(x, roofY, z, L.cap);
				s.set(x, roofY + 1, z, L.cap);
				s.set(x, roofY + 2, z, L.capTop);
			}
		}
		if (L.crown) crown(s, L, roofY + 1);
		return true;
	}

	/** Blocks from this edge cell to the nearest end of its wall run (for the 1.2 m balcony inset). */
	private static int endDistance(Site s, int x, int z, Direction out) {
		int ax = out.getAxis() == Direction.Axis.Z ? 1 : 0, az = 1 - ax, n = 0;
		while (n < 4 && s.in(x - ax * (n + 1), z - az * (n + 1)) && s.outward(x - ax * (n + 1), z - az * (n + 1)) == out) n++;
		int m = 0;
		while (m < 4 && s.in(x + ax * (m + 1), z + az * (m + 1)) && s.outward(x + ax * (m + 1), z + az * (m + 1)) == out) m++;
		return Math.min(n, m);
	}

	private static BlockState wall(Look L, int rel, int t, boolean corner) {
		if (L.style == GLASS) {
			if (rel < L.groundH) {
				if (corner || rel == L.groundH - 1 || Math.floorMod(t, 4) == 0) return L.lobbyFrame;
				return L.lobby;
			}
			if (corner) return L.mullion;
			int fr = Math.floorMod(rel - L.groundH, L.floorH);
			if (fr == L.floorH - 1) return L.spandrel; // slab row: dark spandrel band
			return Math.floorMod(t, 2) == 0 ? L.mullion : L.glass;
		}
		if (rel < L.groundH) {
			if (corner || rel == L.groundH - 1 || Math.floorMod(t, 4) == 0) return L.lobbyFrame;
			return L.lobby;
		}
		if (corner) return L.wall;
		int fr = Math.floorMod(rel - L.groundH, L.floorH);
		if (fr == L.floorH - 1) return L.wall; // slab row stays solid white
		return Math.floorMod(t, 4) == 0 ? L.wall : L.window; // 3-block wide window per 4-block bay
	}

	/** Glass railing band 1 block above each slab, white slab edge under it, one block outside the wall. */
	private static void balcony(Site s, Look L, int ox, int y, int oz, int rel, Direction out) {
		int fr = Math.floorMod(rel - L.groundH, L.floorH);
		if (fr == L.floorH - 1) s.setOut(ox, y, oz, L.slabEdge);
		else if (fr == 0) s.setOut(ox, y, oz, LookBlocks.onWall(LookBlocks.GLASS_RAILING, out));
	}

	/** Inset crown on the tallest: two storeys set back 2 blocks, with its own cap, plus a lit top course. */
	private static void crown(Site s, Look L, int y0) {
		int ch = L.style == GLASS ? 8 : 6;
		for (int x = Math.max(0, s.cx0 - 1); x <= Math.min(s.W - 1, s.cx1 + 1); x++) {
			for (int z = Math.max(0, s.cz0 - 1); z <= Math.min(s.D - 1, s.cz1 + 1); z++) {
				if (!inset(s, x, z)) continue;
				boolean e = !inset(s, x - 1, z) || !inset(s, x + 1, z) || !inset(s, x, z - 1) || !inset(s, x, z + 1);
				if (!e) {
					s.set(x, y0 + ch, z, B.of("gray_concrete"));
					continue;
				}
				int t = (!inset(s, x, z - 1) || !inset(s, x, z + 1)) ? x : z;
				for (int r = 0; r < ch; r++) {
					BlockState b;
					if (L.style == GLASS) b = r == ch / 2 - 1 || r == ch - 1 ? L.spandrel : Math.floorMod(t, 2) == 0 ? L.mullion : L.glass;
					else b = r % 3 == 2 ? L.wall : Math.floorMod(t, 4) == 0 ? L.wall : L.window;
					s.set(x, y0 + r, z, b);
				}
				s.set(x, y0 + ch, z, L.capTop);
				if (Math.floorMod(t, 6) == 0) s.set(x, y0 + ch + 1, z, Blocks.SEA_LANTERN.defaultBlockState());
			}
		}
	}

	private static boolean inset(Site s, int x, int z) {
		return s.in(x - 2, z) && s.in(x + 2, z) && s.in(x, z - 2) && s.in(x, z + 2) && s.in(x - 2, z - 2) && s.in(x + 2, z + 2)
				&& s.in(x - 2, z + 2) && s.in(x + 2, z - 2);
	}
}
