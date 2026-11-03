package dev.leonida.landmark.site;

import static dev.leonida.landmark.gen.KeysPalette.*;

import dev.leonida.core.api.GenTerrain;
import dev.leonida.landmark.gen.B;
import dev.leonida.landmark.gen.Canvas;
import dev.leonida.landmark.gen.Site;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Starlet Motel, Leonida Keys (gtadb L304 motel, L307 roadside sign, L1159 pool, L1205 utility hut). A one-storey
 * L-shaped roadside motel: tan brick walls, weathered board gable ends and a blue-teal metal roof at 45 degrees, rooms
 * opening onto a covered walkway around a cracked asphalt courtyard, the office at the east end of the front wing under
 * a gabled porch with a small red neon box. On the road shoulder the tall slate "MOTEL" sign on red poles with the
 * lit script, the bulb arrow curling over the top, VACANCY, KING SIZE BEDS / LOW RATES and POOL and WIFI boards
 * (L304_ig, L307_ig; signage about twice real scale so it reads at block resolution, same rule as Brian's letters).
 * Behind the sign the fenced pool on green turf with umbrellas and loungers, the block utility hut, gravel and road
 * works barrels on the front lot, palms.
 *
 * <p>Local frame: origin = north-west corner of the front wing (world -5380, -3515), +x east, +z south, y from the lot.
 * Front wing A x 0..47, z 0..11 (ridge along x); side wing B x 10..21, z 12..57 (ridge along z). Walls y 1..4, roof
 * pitched 45 degrees to a ridge 10 m above the lot. The highway runs NNW to SSE just east of the sign; road columns
 * are left alone and are not claimed.
 */
public final class StarletMotel extends Site {
	static final int OX = -5380, OZ = -3515;
	/** Ground probe: the courtyard. */
	static final int PX = -5345, PZ = -3480;
	/** Area that gets levelled (local). */
	static final int LX0 = -8, LX1 = 88, LZ0 = -11, LZ1 = 69;
	static final int WALL = 4;

	public StarletMotel() {
		super("starlet_motel", OX - 12, OZ - 15, OX + 92, OZ + 73);
	}

	@Override
	public int originX() {
		return OX;
	}

	@Override
	public int originZ() {
		return OZ;
	}

	@Override
	public int groundY(GenTerrain t) {
		int g = t.groundY(PX, PZ), w = t.waterY(PX, PZ);
		if (w != Integer.MIN_VALUE) g = Math.max(g, w + 1);
		return Math.max(g, t.seaLevel() + 1);
	}

	@Override
	public List<View> views() {
		return List.of(
				// L304_ig / L307_ig: from the north on the road shoulder, the sign on the left, the motel behind on the right
				View.look("photo", 74, 2, -27, 54, 7, 10, 70),
				// L1159_ig: over the pool towards the side wing
				View.look("pool", 86, 8, 62, 60, 2, 45, 70),
				View.look("aerial", 110, 60, -40, 40, 0, 30, 70),
				View.look("courtyard", 52, 3, 64, 24, 3, 14, 70),
				View.look("office", 66, 3, 6, 47, 4, 5, 70));
	}

	@Override
	public List<Footprint> footprints() {
		return List.of(
				// gtadb L304: front wing 48 x 12, 10.9 m (the L leg and office 8.7 m); built as one L, ridge 10 m
				new Footprint("motel", OX, OZ, OX + 47, OZ + 57, 10, motelBrick(), motelRoof(), 3),
				// L307 roadside sign face, about twice real scale; arrow bulbs to 21 m
				new Footprint("sign", OX + 56, OZ + 2, OX + 76, OZ + 2, 21, signPanel(), bulbs(), 2),
				// L1159 fenced pool deck
				new Footprint("pool_fence", OX + 58, OZ + 38, OX + 82, OZ + 59, 1, poolFence(), poolFence(), 2));
	}

	// ------------------------------------------------------------------------------------------------ shape

	/** Wing boxes {x0, z0, x1, z1, ridgeAlongX}. */
	static final int[][] WINGS = {{0, 0, 47, 11, 1}, {10, 12, 21, 57, 0}};

	static boolean in(int w, int lx, int lz) {
		int[] a = WINGS[w];
		return lx >= a[0] && lx <= a[2] && lz >= a[1] && lz <= a[3];
	}

	static boolean inMotel(int lx, int lz) {
		return in(0, lx, lz) || in(1, lx, lz);
	}

	/** Perimeter wall of either wing (each wing keeps its own walls, so the joint gets a double wall). */
	static boolean wall(int lx, int lz) {
		for (int w = 0; w < 2; w++)
			if (in(w, lx, lz) && (!in(w, lx - 1, lz) || !in(w, lx + 1, lz) || !in(w, lx, lz - 1) || !in(w, lx, lz + 1))) return true;
		return false;
	}

	/** Gable end wall columns (filled with boards up to the roof). */
	static boolean gable(int lx, int lz) {
		return in(0, lx, lz) && (lx == 0 || lx == 47) || in(1, lx, lz) && lz == 57;
	}

	/**
	 * Roof of one wing at a column: {y, facing ordinal, ridge?} or null. Eave overhang 1 all round; wing B's roof runs
	 * north into wing A up to A's ridge so the two meet in a valley.
	 */
	static int[] wingRoof(int w, int lx, int lz) {
		int[] a = WINGS[w];
		int x0 = a[0] - 1, x1 = a[2] + 1, z0 = a[1] - 1, z1 = a[3] + 1;
		if (w == 1) z0 = 6;
		if (lx < x0 || lx > x1 || lz < z0 || lz > z1) return null;
		if (a[4] == 1) {
			int d = Math.min(lz - z0, z1 - lz), max = (z1 - z0) / 2;
			Direction up = lz - z0 < z1 - lz ? Direction.SOUTH : Direction.NORTH;
			return new int[] {WALL + d, up.ordinal(), d >= max ? 1 : 0};
		}
		int bx0 = a[0] - 1, d = Math.min(lx - bx0, x1 - lx), max = (x1 - bx0) / 2;
		Direction up = lx - bx0 < x1 - lx ? Direction.EAST : Direction.WEST;
		return new int[] {WALL + d, up.ordinal(), d >= max ? 1 : 0};
	}

	static int[] roofAt(int lx, int lz) {
		int[] r0 = wingRoof(0, lx, lz), r1 = wingRoof(1, lx, lz);
		if (r0 == null) return r1;
		if (r1 == null) return r0;
		return r1[0] > r0[0] ? r1 : r0;
	}

	static int roofY(int lx, int lz) {
		int[] r = roofAt(lx, lz);
		return r == null ? Integer.MIN_VALUE : r[0];
	}

	static boolean porch(int lx, int lz) {
		return lx >= 48 && lx <= 53 && lz >= 1 && lz <= 10;
	}

	static boolean walkway(int lx, int lz) {
		return lx >= 22 && lx <= 47 && lz >= 12 && lz <= 13 || lx >= 22 && lx <= 23 && lz >= 14 && lz <= 57;
	}

	static boolean deck(int lx, int lz) {
		return lx >= 59 && lx <= 81 && lz >= 39 && lz <= 58;
	}

	static boolean courtyard(int lx, int lz) {
		return lx >= 24 && lx <= 56 && lz >= 14 && lz <= 69;
	}

	/** Pool basin, rounded corners: x 63..77, z 45..52. */
	static boolean basin(int lx, int lz) {
		if (lx < 63 || lx > 77 || lz < 45 || lz > 52) return false;
		return !((lx == 63 || lx == 77) && (lz == 45 || lz == 52));
	}

	// ------------------------------------------------------------------------------------------------ build

	@Override
	public void build(Canvas c, GenTerrain t) {
		int gy = groundY(t);
		B b = new B(c, t, OX, OZ, gy, 0x5747L);
		lot(b);
		pool(b);
		walls(b);
		roof(b);
		porchAndWalkways(b);
		rooms(b);
		hut(b);
		sign(b);
		props(b);
	}

	boolean road(B b, int lx, int lz) {
		return roadAt(b.wx(lx), b.wz(lz));
	}

	private void lot(B b) {
		BlockState dirt = Blocks.DIRT.defaultBlockState(), stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		BlockState con = concrete(), grass = Blocks.GRASS_BLOCK.defaultBlockState(), coarse = Blocks.COARSE_DIRT.defaultBlockState();
		BlockState gravel = Blocks.GRAVEL.defaultBlockState(), shell = shellGravel(), tf = turf();
		for (int lx = LX0; lx <= LX1; lx++)
			for (int lz = LZ0; lz <= LZ1; lz++) {
				if (!b.inClip(lx, lz) || road(b, lx, lz)) continue;
				int tg = b.wet(lx, lz) ? Math.min(b.ground(lx, lz), -1) : b.ground(lx, lz);
				for (int y = Math.min(tg + 1, -3); y < 0; y++) b.set(lx, y, lz, y < -1 ? dirt : stone);
				for (int y = 1; y <= Math.max(tg, b.water(lx, lz)) + 24 && y <= 40; y++) b.set(lx, y, lz, air);
				double r = b.rnd(lx, 0, lz, 3), r2 = b.rnd(lx >> 2, 0, lz >> 2, 4);
				BlockState top;
				if (inMotel(lx, lz) || porch(lx, lz) || walkway(lx, lz)) top = con;
				else if (deck(lx, lz)) top = tf;
				else if (courtyard(lx, lz)) top = asphalt(b.rnd(lx >> 1, 0, lz >> 1, 5));
				else if (lz < 0 || lx > 47 && lz < 14 || lx > 82) top = r2 < 0.35 ? gravel : r2 < 0.6 ? shell : r < 0.5 ? coarse : grass;
				else top = r < 0.25 ? coarse : grass;
				b.set(lx, 0, lz, top);
			}
	}

	private void pool(B b) {
		if (!b.inClipNear(70, 48, 16)) return;
		BlockState tile = poolTile(), cop = poolCoping(), water = Blocks.WATER.defaultBlockState();
		for (int lx = 62; lx <= 78; lx++)
			for (int lz = 44; lz <= 53; lz++) {
				if (!b.inClip(lx, lz)) continue;
				if (basin(lx, lz)) {
					b.set(lx, -3, lz, tile);
					for (int y = -2; y <= 0; y++) b.set(lx, y, lz, water);
				} else if (basin(lx - 1, lz) || basin(lx + 1, lz) || basin(lx, lz - 1) || basin(lx, lz + 1) || basin(lx - 1, lz - 1)
						|| basin(lx + 1, lz + 1) || basin(lx - 1, lz + 1) || basin(lx + 1, lz - 1)) {
					for (int y = -3; y < 0; y++) b.set(lx, y, lz, tile);
					b.set(lx, 0, lz, cop);
				}
			}
		// ladder rails at the deep end
		b.set(77, 1, 48, poolFence());
		b.set(77, 1, 49, poolFence());
		// fence ring with a gate towards the courtyard
		BlockState f = poolFence();
		for (int lx = 58; lx <= 82; lx++) {
			if (lx < 68 || lx > 70) b.set(lx, 1, 38, f);
			b.set(lx, 1, 59, f);
		}
		for (int lz = 38; lz <= 59; lz++) {
			if (lz < 47 || lz > 49) b.set(58, 1, lz, f);
			b.set(82, 1, lz, f);
		}
		// umbrellas and loungers (L1159)
		int[][] umb = {{61, 41, 0}, {79, 41, 1}, {61, 56, 1}, {79, 56, 0}};
		for (int[] u : umb) umbrella(b, u[0], u[1], u[2] == 0 ? v("minecraft:yellow_wool") : v("minecraft:red_wool"));
		BlockState lo = B.slab(lounger(), false), back = B.stairs(v("minecraft:smooth_quartz_stairs"), Direction.SOUTH, false);
		for (int lx = 65; lx <= 76; lx += 3) {
			b.set(lx, 1, 55, lo);
			b.set(lx, 1, 56, back);
			b.set(lx, 1, 41, lo);
			b.set(lx, 1, 40, B.stairs(v("minecraft:smooth_quartz_stairs"), Direction.NORTH, false));
		}
	}

	private static void umbrella(B b, int lx, int lz, BlockState cloth) {
		BlockState pole = v("minecraft:birch_fence"), alt = v("minecraft:white_wool");
		for (int y = 1; y <= 3; y++) b.set(lx, y, lz, pole);
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++) b.set(lx + dx, 4, lz + dz, B.flat(((dx + dz) & 1) == 0 ? cloth : alt, false));
	}

	private void walls(B b) {
		BlockState brick = motelBrick(), boards = gableBoards(), win = window(), high = houseWindow();
		for (int lx = 0; lx <= 47; lx++)
			for (int lz = 0; lz <= 57; lz++) {
				if (!b.inClip(lx, lz) || !wall(lx, lz)) continue;
				for (int y = 1; y <= WALL; y++) b.set(lx, y, lz, brick);
				if (gable(lx, lz)) {
					int ry = roofY(lx, lz);
					for (int y = WALL + 1; y < ry; y++) b.set(lx, y, lz, boards);
				}
				// north (road) wall: high bathroom windows on the rooms, big office windows
				if (lz == 0 && lx >= 37 && lx <= 46 && (lx % 4 == 1 || lx % 4 == 2)) {
					b.set(lx, 2, lz, high);
					b.set(lx, 3, lz, high);
				} else if (lz == 0 && lx < 35 && Math.floorMod(lx - 3, 6) == 0) b.set(lx, 3, lz, win);
				// west wall of wing B and south wall of wing A behind B: small windows
				if (lx == 10 && lz > 12 && Math.floorMod(lz - 15, 6) == 0) b.set(lx, 3, lz, win);
			}
	}

	private void roof(B b) {
		BlockState full = motelRoof(), st = motelRoofStairs();
		Direction[] dirs = Direction.values();
		for (int lx = -1; lx <= 48; lx++)
			for (int lz = -1; lz <= 58; lz++) {
				if (!b.inClip(lx, lz)) continue;
				int[] r = roofAt(lx, lz);
				if (r == null) continue;
				b.set(lx, r[0], lz, r[2] == 1 ? full : B.stairs(st, dirs[r[1]], false));
				// close steps of more than one block towards a neighbour (valleys and the wing joint)
				int lo = r[0];
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					int n = roofY(lx + d[0], lz + d[1]);
					if (n != Integer.MIN_VALUE) lo = Math.min(lo, n);
				}
				for (int y = Math.max(lo + 1, WALL + 1); y < r[0]; y++) b.set(lx, y, lz, full);
			}
	}

	private void porchAndWalkways(B b) {
		BlockState st = motelRoofStairs(), full = motelRoof(), boards = gableBoards(), slab = B.slab(motelRoofSlab(), false);
		BlockState col = v("minecraft:mud_brick_wall"), post = picket(), lat = lattice();
		if (b.inClipNear(50, 5, 8)) {
			// gabled porch on the office end (L304_ig), ridge along x at z 5..6
			for (int lx = 48; lx <= 54; lx++)
				for (int lz = 0; lz <= 11; lz++) {
					int d = Math.min(lz, 11 - lz), y = WALL + 1 + d / 2;
					b.set(lx, y, lz, d >= 5 ? full : d % 2 == 0 ? B.stairs(st, lz < 6 ? Direction.SOUTH : Direction.NORTH, false) : B.slab(motelRoofSlab(), true));
					if (lx == 54) for (int yy = WALL + 1; yy < y; yy++) b.set(lx, yy, lz, boards);
				}
			for (int lz : new int[] {1, 10})
				for (int y = 1; y <= WALL; y++) b.set(53, y, lz, col);
			for (int lz = 2; lz <= 3; lz++) b.set(53, 1, lz, lat);
			for (int lz = 8; lz <= 9; lz++) b.set(53, 1, lz, lat);
			b.door(47, 1, 5, doorCottage(), Direction.EAST, false);
			b.set(48, WALL, 3, B.hanging(Blocks.LANTERN.defaultBlockState(), true));
			// small red neon box above the porch ridge
			for (int lz = 3; lz <= 8; lz++) {
				b.set(48, 8, lz, v("minecraft:red_concrete"));
				b.set(48, 9, lz, (lz & 1) == 0 ? Blocks.SHROOMLIGHT.defaultBlockState() : v("minecraft:red_concrete"));
			}
		}
		// covered walkways on posts
		for (int lx = 22; lx <= 48; lx++)
			for (int lz = 12; lz <= 13; lz++) b.set(lx, WALL + 1, lz, slab);
		for (int lx = 22; lx <= 23; lx++)
			for (int lz = 14; lz <= 58; lz++) b.set(lx, WALL + 1, lz, slab);
		for (int lx = 24; lx <= 48; lx += 6)
			for (int y = 1; y <= WALL; y++) b.set(lx, y, 13, post);
		for (int lz = 19; lz <= 58; lz += 6)
			for (int y = 1; y <= WALL; y++) b.set(23, y, lz, post);
	}

	static final String[] BEDS = {"red", "blue", "cyan", "orange"};

	/** Low bed: a white slab pillow at the head, a coloured cover at the foot. */
	static void bed(B b, int lx, int lz, String color, Direction head) {
		b.set(lx, 1, lz, v("minecraft:" + color + "_carpet"));
		b.set(lx + head.getStepX(), 1, lz + head.getStepZ(), B.slab(v("minecraft:smooth_quartz_slab"), false));
	}

	private void rooms(B b) {
		BlockState part = interiorWall(), win = window(), lamp = B.hanging(Blocks.LANTERN.defaultBlockState(), true);
		// wing A: six rooms x 1..35 (partitions every 6) and the office x 36..46
		for (int k = 0; k < 6; k++) {
			int px = 6 * (k + 1);
			if (b.inClipNear(px, 5, 8)) for (int lz = 1; lz <= 10; lz++)
				for (int y = 1; y <= WALL; y++) b.set(px == 36 ? 35 : px, y, lz, part);
			int dx = 6 * k + 4;
			bed(b, 6 * k + 2, 2, BEDS[k & 3], Direction.NORTH);
			b.set(6 * k + 4, 1, 1, Blocks.LANTERN.defaultBlockState()); // bedside light, shows through the windows at night
			if (dx > 22) {
				b.door(dx, 1, 11, doorCottage(), Direction.SOUTH, false);
				b.set(dx - 2, 2, 11, win);
				b.set(dx, WALL, 12, lamp);
			}
		}
		// office: counter and lamps behind the big windows
		for (int lx = 38; lx <= 45; lx += 3) b.set(lx, 1, 1, Blocks.LANTERN.defaultBlockState());
		for (int lz = 3; lz <= 8; lz++) b.set(41, 1, lz, counter());
		// wing B: seven rooms along the courtyard, doors on the east wall
		for (int j = 0; j < 7; j++) {
			int z0 = 12 + 6 * j;
			if (j > 0) for (int lx = 11; lx <= 20; lx++)
				for (int y = 1; y <= WALL; y++) b.set(lx, y, z0, part);
			bed(b, 19, z0 + 2, BEDS[(j + 1) & 3], Direction.EAST);
			b.set(20, 1, z0 + 4, Blocks.LANTERN.defaultBlockState());
			b.door(21, 1, z0 + 3, doorCottage(), Direction.EAST, false);
			b.set(21, 2, z0 + 1, win);
			b.set(22, WALL, z0 + 3, lamp);
		}
	}

	private void hut(B b) {
		if (!b.inClipNear(57, 20, 6)) return;
		BlockState w = blockHut(), slab = B.slab(v("minecraft:smooth_stone_slab"), false);
		for (int lx = 54; lx <= 60; lx++)
			for (int lz = 17; lz <= 22; lz++) {
				boolean edge = lx == 54 || lx == 60 || lz == 17 || lz == 22;
				for (int y = 1; y <= 3; y++) b.set(lx, y, lz, edge ? w : Blocks.AIR.defaultBlockState());
			}
		for (int lx = 53; lx <= 61; lx++)
			for (int lz = 16; lz <= 23; lz++) b.set(lx, 4, lz, slab);
		b.door(57, 1, 22, doorCottage(), Direction.SOUTH, false);
		b.set(57, 3, 23, B.facing(Blocks.LANTERN.defaultBlockState(), Direction.SOUTH));
		b.set(59, 2, 23, acUnit());
	}

	// ------------------------------------------------------------------------------------------------ roadside sign

	/** 3 x 5 letters, rows top to bottom. */
	static final String[][] FONT = {
			{"101", "111", "101", "101", "101"}, // M
			{"111", "101", "101", "101", "111"}, // O
			{"111", "010", "010", "010", "010"}, // T
			{"111", "100", "110", "100", "111"}, // E
			{"100", "100", "100", "100", "111"}, // L
	};

	static final int SZ = 2, PANEL_X0 = 56, PANEL_X1 = 76, PANEL_Y0 = 9, PANEL_Y1 = 17;

	/**
	 * Sign in the plane z = 2 facing north (the camera side); seen from the north +x is on the viewer's left, so text
	 * runs from east to west.
	 */
	private void sign(B b) {
		if (!b.inClipNear(66, 2, 16)) return;
		BlockState panel = signPanel(), lit = signLight(), bulb = bulbs(), pole = signPole();
		// poles: one straight, one leaning (L307_ig)
		for (int y = 1; y < PANEL_Y0; y++) b.set(70, y, SZ, pole);
		b.line(59, 1, SZ, 62, PANEL_Y0 - 1, SZ, a -> pole);
		b.set(59, 1, SZ, pole);
		for (int lx = PANEL_X0; lx <= PANEL_X1; lx++)
			for (int y = PANEL_Y0; y <= PANEL_Y1; y++) b.set(lx, y, SZ, panel);
		// MOTEL, 19 wide, starting at the east end
		int x = PANEL_X1 - 1;
		for (int i = 0; i < 5; i++) {
			String[] g = FONT[i];
			for (int c = 0; c < 3; c++, x--)
				for (int r = 0; r < 5; r++) if (g[r].charAt(c) == '1') b.set(x, 14 - r, SZ, lit);
			x--;
		}
		// "Starlet" script: an abstract looping light stroke above the letters
		for (int lx = PANEL_X1 - 2; lx >= PANEL_X0 + 3; lx--) {
			int y = Math.floorMod(lx, 4) < 2 ? 15 : 16;
			if (Math.floorMod(lx, 7) != 0) b.set(lx, y, SZ, lit);
		}
		// bulb arrow: up the east edge, over the top, pointing west
		for (int y = PANEL_Y0 - 1; y <= 19; y++) b.set(PANEL_X1 + 1, y, SZ, bulb);
		for (int lx = PANEL_X1 + 1; lx >= 58; lx--) b.set(lx, 19, SZ, bulb);
		for (int y = 18; y <= 20; y++) b.set(57, y, SZ, bulb);
		b.set(56, 19, SZ, bulb);
		b.set(58, 18, SZ, bulb);
		b.set(58, 20, SZ, bulb);
		b.set(57, 17 + 0, SZ, bulb);
		b.set(57, 21 - 0, SZ, bulb);
		// VACANCY board, lower left as seen from the road
		for (int lx = 72; lx <= 78; lx++) {
			b.set(lx, 7, SZ, v("minecraft:black_concrete"));
			b.set(lx, 8, SZ, lx == 72 || lx == 78 || (lx & 1) == 1 ? v("minecraft:black_concrete") : v("minecraft:red_concrete"));
		}
		// KING SIZE BEDS / LOW RATES oval lightbox and the POOL and WIFI board
		BlockState box = of("metal_blue_grey", "minecraft:light_gray_concrete"), white = v("minecraft:white_concrete");
		for (int lx = 61; lx <= 71; lx++)
			for (int y = 5; y <= 6; y++) {
				boolean end = lx == 61 || lx == 71;
				boolean txt = !end && b.rnd(lx, y, 0, 77) < 0.55;
				b.set(lx, y, SZ, end ? B.slab(v("minecraft:smooth_quartz_slab"), y == 5) : txt ? white : box);
			}
		for (int lx = 62; lx <= 70; lx++) b.set(lx, 3, SZ, (lx & 1) == 0 ? v("minecraft:smooth_quartz") : v("minecraft:light_blue_concrete"));
	}

	// ------------------------------------------------------------------------------------------------ props

	/** {x, z, height}: tall slender palms, a cluster behind the sign (L307_ig). */
	static final int[][] PALMS = {{50, -4, 12}, {55, 8, 14}, {61, 12, 13}, {66, 9, 11}, {46, 15, 12}, {85, 30, 13},
			{56, 34, 11}, {85, 64, 12}, {-5, 20, 12}, {28, 64, 10}, {-5, -6, 11}, {30, -7, 12}, {44, 66, 13}};

	private void props(B b) {
		for (int[] p : PALMS) {
			if (!b.inClipNear(p[0], p[1], 6) || road(b, p[0], p[1])) continue;
			b.set(p[0], 0, p[1], Blocks.GRASS_BLOCK.defaultBlockState());
			if (hasPalm()) JasonHouse.lookPalm(b, p[0], 0, p[1], p[2], b.rnd(p[0], 0, p[1], 61) < 0.5 ? 1 : -1, b.rnd(p[0], 0, p[1], 62) < 0.5 ? 1 : -1);
			else {
				BlockState trunk = B.axis(Blocks.JUNGLE_LOG.defaultBlockState(), Direction.Axis.Y), leaf = B.persistent(Blocks.JUNGLE_LEAVES.defaultBlockState());
				for (int y = 1; y <= p[2]; y++) b.set(p[0], y, p[1], trunk);
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
					for (int k = 1; k <= 3; k++) b.set(p[0] + d[0] * k, p[2] + (k == 3 ? -1 : 0), p[1] + d[1] * k, leaf);
			}
		}
		// road works on the front lot: orange drums, barriers, a gravel heap (L304_ig)
		BlockState drum = v("minecraft:orange_concrete"), barrier = B.slab(v("minecraft:smooth_stone_slab"), false);
		int[][] drums = {{48, -6}, {51, -8}, {54, -6}, {57, -9}, {44, -9}, {80, -4}, {83, -8}};
		for (int[] d : drums) if (b.inClip(d[0], d[1]) && !road(b, d[0], d[1])) b.set(d[0], 1, d[1], drum);
		for (int lx = 36; lx <= 42; lx++) if (!road(b, lx, -10)) b.set(lx, 1, -10, (lx & 1) == 0 ? drum : barrier);
		for (int lx = 50; lx <= 55; lx++)
			for (int lz = -10; lz <= -7; lz++) {
				if (road(b, lx, lz)) continue;
				int h = (lx == 50 || lx == 55 || lz == -10 || lz == -7) ? 1 : 2;
				if ((lx == 50 || lx == 55) && (lz == -10 || lz == -7)) continue;
				for (int y = 1; y <= h; y++) b.set(lx, y, lz, Blocks.GRAVEL.defaultBlockState());
			}
		// street lamp by the sign
		if (b.inClipNear(80, -6, 4) && !road(b, 80, -6)) {
			BlockState lp = v("minecraft:polished_andesite_wall");
			for (int y = 1; y <= 9; y++) b.set(80, y, -6, lp);
			b.set(79, 9, -6, B.slab(v("minecraft:smooth_stone_slab"), true));
			b.set(78, 9, -6, B.slab(v("minecraft:smooth_stone_slab"), true));
			b.set(78, 8, -6, Blocks.SEA_LANTERN.defaultBlockState());
		}
		// power poles along the front with sagging lines
		int[] poles = {0, 26, 52};
		for (int px : poles) {
			if (!b.inClipNear(px, -12, 4) || road(b, px, -12)) continue;
			for (int y = 1; y <= 9; y++) b.set(px, y, -12, pole());
			for (int dz = -1; dz <= 1; dz++) b.set(px, 9, -12 + dz, poleCrossarm());
		}
		for (int i = 0; i + 1 < poles.length; i++) {
			if (road(b, poles[i + 1], -12)) continue;
			for (int dz = -1; dz <= 1; dz += 2) b.line(poles[i], 10, -12 + dz, poles[i + 1], 10, -12 + dz, a -> v("minecraft:black_wool"));
		}
		// courtyard lamp posts
		for (int[] q : new int[][] {{40, 30}, {40, 52}, {56, 60}}) {
			if (!b.inClipNear(q[0], q[1], 3)) continue;
			for (int y = 1; y <= 5; y++) b.set(q[0], y, q[1], v("minecraft:polished_andesite_wall"));
			b.set(q[0], 6, q[1], Blocks.SEA_LANTERN.defaultBlockState());
		}
		// a grey electrical cabinet by the front wing
		if (b.inClipNear(30, -2, 3)) {
			b.set(30, 1, -2, v("minecraft:gray_concrete"));
			b.set(31, 1, -2, v("minecraft:gray_concrete"));
		}
	}
}
