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
 * Brian's Boat Works and Marina, Key Lento (gtadb L326 shed, L1421 office and workshop, L3269 rent-a-rack; real
 * counterpart the Marina Club dry stack at Blackwater Sound, Key Largo). A big dry-stack boat barn on the gtadb footprint
 * (35 x 84 m, 13.1 m high, the south end cut on the diagonal), blue-teal corrugated walls, a low-pitch white corrugated
 * roof with skylight strips and the red pixel-letter "BRIAN'S MARINA" sign standing on the ridge (L326_ig). Inside: three
 * tiers of boats on steel racks either side of a forklift aisle, launch doors to the basin. Along the east wall the
 * outdoor four-tier rent-a-rack with its red sign (L3269_ig). South of the barn the white two-storey office with a hip
 * roof, cupola and balcony, and the skylit workshop shed beside it (L1421_ig). A paved yard, boats on trailers, palms,
 * a white picket fence on the road side, and a bulkhead with a walkway and finger piers along the basin.
 *
 * <p>Local frame: origin = north-west corner of the barn (world -2570, 5826), +x east, +z south, y from the yard.
 * Barn x 0..33 (east wall straight to z 55, then diagonal to x 0 at z 83); walls y 1..11; ridge 13 m above the yard.
 */
public final class BriansMarina extends Site {
	static final int OX = -2570, OZ = 5826;
	/** Ground probe: the middle of the barn (gtadb L326 pin). */
	static final int PX = -2554, PZ = 5859;
	/** Wall top and the yard area that gets levelled and paved (local). */
	static final int WALL = 11, PAVE_X0 = -24, PAVE_X1 = 50, PAVE_Z0 = -9, PAVE_Z1 = 98, FENCE_X = 51;

	public BriansMarina() {
		super("brians_marina", OX - 40, OZ - 11, OX + 54, OZ + 99);
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
				// L326_ig: long telephoto from the east over the trees, the white roof and the red letters on the ridge
				View.look("photo", 170, 30, 46, 17, 13, 40, 30),
				// L3269_ig: high over the yard looking down at the rent-a-rack against the barn wall
				View.look("rack", 75, 22, 50, 40, 5, 32, 55),
				// L1421_ig: over the barn's diagonal south end towards the office cupola
				View.look("office", 46, 20, 62, 17, 7, 90, 55),
				View.look("aerial", -70, 55, 125, 12, 0, 40, 70),
				View.look("dock", -36, 4, 86, -14, 2, 30, 60),
				View.look("inside", 16, 3, 2, 16, 4, 40, 70));
	}

	@Override
	public List<Footprint> footprints() {
		return List.of(
				// gtadb L326 footprint: 35 x 84 m, 13.1 m; built 34 x 84, ridge 13
				new Footprint("barn", OX, OZ, OX + 33, OZ + 83, 13, shedWall(), roofWhite(), 4),
				// L1421: two storeys, hip roof, cupola
				new Footprint("office", OX + 10, OZ + 85, OX + 23, OZ + 94, 15, clapboardWhite(), roofWhiteStairs(), 3),
				// L3269: 11 bays, four tiers, about eave height
				new Footprint("rent_a_rack", OX + 35, OZ + 10, OX + 42, OZ + 54, 11, rackDark(), rackDark(), 1));
	}

	// ------------------------------------------------------------------------------------------------ barn shape

	/** East wall x of a barn row (straight to z 55, then the diagonal south end). */
	static int xe(int lz) {
		if (lz < 0 || lz > 83) return -1;
		return lz <= 55 ? 33 : 33 - ((lz - 55) * 33 + 14) / 28;
	}

	static boolean inBarn(int lx, int lz) {
		return lz >= 0 && lz <= 83 && lx >= 0 && lx <= xe(lz);
	}

	static boolean barnWall(int lx, int lz) {
		return inBarn(lx, lz) && (!inBarn(lx - 1, lz) || !inBarn(lx + 1, lz) || !inBarn(lx, lz - 1) || !inBarn(lx, lz + 1));
	}

	/** Signed distance (blocks) inside the diagonal south-east edge. */
	static double diag(int lx, int lz) {
		return ((lx - 34) * -28.0 + (lz - 55) * -34.0) / 44.05;
	}

	/** Roof step 0..4 (half metres above the eave) or -1 where there is no roof. Gable at the north end, hip on the diagonal. */
	static int roofStep(int lx, int lz) {
		if (lx < -1 || lx > 34 || lz < -1 || lz > 84) return -1;
		double s = diag(lx, lz);
		if (s < -1.5) return -1;
		double d = Math.min(Math.min(lx + 1, 34 - lx), s + 1.0);
		if (d < 0) return -1;
		return Math.min(4, (int) Math.floor(d / 4));
	}

	static boolean westDoor(int lz) {
		return lz >= 8 && lz <= 15 || lz >= 28 && lz <= 35 || lz >= 48 && lz <= 55 || lz >= 66 && lz <= 73;
	}

	static boolean office(int lx, int lz) {
		return lx >= 10 && lx <= 34 && lz >= 83 && lz <= 96;
	}

	static boolean paved(int lx, int lz) {
		return lx >= PAVE_X0 && lx <= PAVE_X1 && lz >= PAVE_Z0 && lz <= PAVE_Z1;
	}

	// ------------------------------------------------------------------------------------------------ build

	@Override
	public void build(Canvas c, GenTerrain t) {
		int gy = groundY(t);
		B b = new B(c, t, OX, OZ, gy, 0xB0A7L);
		yard(b);
		docks(b);
		barn(b);
		roof(b);
		sign(b);
		racksInside(b);
		rentARack(b);
		office(b);
		props(b);
	}

	private void yard(B b) {
		BlockState dirt = Blocks.DIRT.defaultBlockState(), air = Blocks.AIR.defaultBlockState(), con = concrete();
		for (int lx = PAVE_X0; lx <= PAVE_X1; lx++)
			for (int lz = PAVE_Z0; lz <= PAVE_Z1; lz++) {
				if (!b.inClip(lx, lz) || b.wet(lx, lz)) continue;
				int tg = b.ground(lx, lz);
				for (int y = Math.min(tg + 1, -3); y < 0; y++) b.set(lx, y, lz, y < -1 ? dirt : Blocks.STONE.defaultBlockState());
				for (int y = 1; y <= Math.max(tg, 0) + 26; y++) b.set(lx, y, lz, air);
				boolean edge = b.wet(lx - 1, lz) || b.wet(lx + 1, lz) || b.wet(lx, lz - 1) || b.wet(lx, lz + 1);
				BlockState top = inBarn(lx, lz) || office(lx, lz) || edge ? con : asphalt(b.rnd(lx >> 1, 0, lz >> 1, 3));
				b.set(lx, 0, lz, top);
				if (edge) for (int y = b.ground(lx, lz) + 1; y < 0; y++) b.set(lx, y, lz, con); // bulkhead
			}
	}

	// ------------------------------------------------------------------------------------------------ basin

	/** First wet column west of the yard on a row, or Integer.MIN_VALUE. */
	static int shore(B b, int lz) {
		for (int lx = -2; lx >= -40; lx--)
			if (b.wet(lx, lz)) return lx;
		return Integer.MIN_VALUE;
	}

	private void docks(B b) {
		if (!b.inClipNear(-20, 40, 40) && !b.inClipNear(-20, 0, 24)) return;
		BlockState deck = deck(), post = B.axis(post(), Direction.Axis.Y), air = Blocks.AIR.defaultBlockState();
		for (int lz = -6; lz <= 80; lz++) {
			int s = shore(b, lz);
			if (s == Integer.MIN_VALUE || s < -30) continue;
			int wy = b.water(s, lz), dy = Math.min(0, wy + 1);
			// walkway along the bulkhead
			for (int x = s; x >= s - 2; x--) {
				if (!b.wet(x, lz)) continue;
				b.set(x, dy, lz, deck);
				for (int y = dy + 1; y <= dy + 4; y++) b.set(x, y, lz, air);
				if (x == s - 2 && Math.floorMod(lz, 4) == 0) pile(b, x, lz, dy, post);
			}
			// finger piers every 8 m with a boat in each slip
			if (Math.floorMod(lz - 2, 8) == 0 && lz <= 74) {
				for (int x = s - 3; x >= s - 13; x--) {
					if (!b.wet(x, lz) || !b.wet(x, lz + 1)) break;
					b.set(x, dy, lz, deck);
					b.set(x, dy, lz + 1, deck);
					if ((s - 3 - x) % 5 == 4 || x == s - 13) {
						pile(b, x, lz, dy, post);
						pile(b, x, lz + 1, dy, post);
					}
				}
				if (b.rnd(0, 0, lz, 21) < 0.8 && b.wet(s - 12, lz + 4)) boat(b, s - 5, wy, lz + 3, -1, b.rnd(0, 1, lz, 22), true);
			}
		}
	}

	private static void pile(B b, int x, int z, int dy, BlockState post) {
		int g = b.wet(x, z) ? b.ground(x, z) : dy - 1;
		for (int y = g + 1; y <= dy + 1; y++) if (y != dy) b.set(x, y, z, post);
	}

	// ------------------------------------------------------------------------------------------------ boats

	/**
	 * A boat 8 long and 3 wide with its stern at {@code xs} and the bow towards {@code dir} (+1 east, -1 west), hull at
	 * y, covered (khaki, grey or navy canvas) or an open cruiser with a cabin. Two blocks high so it fits a rack tier.
	 */
	static void boat(B b, int xs, int y, int z0, int dir, double style, boolean outboard) {
		if (!b.inClipNear(xs, z0, 10)) return;
		Direction bow = dir > 0 ? Direction.EAST : Direction.WEST, stern = bow.getOpposite();
		BlockState hull = hull(), slab = B.slab(hullSlab(), false), stairs = B.stairs(hullStairs(), stern, false);
		boolean covered = style < 0.6;
		BlockState cover = boatCover(style / 0.6);
		for (int i = 0; i < 8; i++) {
			int x = xs + dir * i;
			for (int z = z0; z <= z0 + 2; z++) {
				boolean edge = z != z0 + 1;
				if (i == 7) {
					if (!edge) b.set(x, y, z, stairs);
					continue;
				}
				if (i == 6) {
					b.set(x, y, z, edge ? slab : hull);
					continue;
				}
				b.set(x, y, z, hull);
				if (covered) {
					if (i >= 1) b.set(x, y + 1, z, cover);
					else if (edge) b.set(x, y + 1, z, slab);
				} else if (i >= 2 && i <= 4) b.set(x, y + 1, z, i == 4 && !edge ? v("minecraft:gray_stained_glass") : hull); // cabin and windshield
				else if (edge) b.set(x, y + 1, z, slab); // gunwale
			}
		}
		if (outboard) {
			BlockState motor = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();
			b.set(xs - dir, y, z0 + 1, motor);
			b.set(xs - dir, y + 1, z0 + 1, motor);
		}
	}

	// ------------------------------------------------------------------------------------------------ barn

	private void barn(B b) {
		BlockState wall = shedWall(), con = concrete(), air = Blocks.AIR.defaultBlockState();
		for (int lx = 0; lx <= 33; lx++)
			for (int lz = 0; lz <= 83; lz++) {
				if (!b.inClip(lx, lz) || !barnWall(lx, lz)) continue;
				b.set(lx, 1, lz, con); // concrete kicker
				for (int y = 2; y <= WALL; y++) b.set(lx, y, lz, wall);
				// north gable up to the roof
				if (lz == 0) {
					int k = roofStep(lx, lz);
					for (int y = WALL + 1; y < WALL + k / 2; y++) b.set(lx, y, lz, wall);
				}
				boolean door = lz == 0 && lx >= 10 && lx <= 23 // north door, 8 m high
						|| lx == 0 && westDoor(lz) // launch doors to the basin
						|| lz >= 62 && lz <= 71 && lx > 0; // south door on the diagonal
				if (door) for (int y = 1; y <= (lx == 0 ? 7 : 8); y++) b.set(lx, y, lz, air);
			}
		// roof trusses every 6 m
		BlockState truss = truss();
		for (int lz = 6; lz <= 78; lz += 6)
			for (int lx = 1; lx < xe(lz); lx++) if (b.inClip(lx, lz)) b.set(lx, WALL, lz, truss);
	}

	private void roof(B b) {
		BlockState sheet = roofWhite(), half = B.slab(roofWhiteSlab(), false), sky = skylight(), wall = shedWall();
		for (int lx = -1; lx <= 34; lx++)
			for (int lz = -1; lz <= 84; lz++) {
				if (!b.inClip(lx, lz)) continue;
				int k = roofStep(lx, lz);
				if (k < 0) continue;
				int y = WALL + k / 2;
				boolean light = k == 2 && Math.floorMod(lz + 2, 8) < 2 && lz >= 2 && lz <= 80 && inBarn(lx, lz);
				b.set(lx, y, lz, light ? sky : sheet);
				if (k % 2 == 1) b.set(lx, y + 1, lz, half);
				// close the gap between the wall top and a higher roof step on the walls
				if (barnWall(lx, lz)) for (int yy = WALL + 1; yy < y; yy++) b.set(lx, yy, lz, wall);
			}
	}

	// ------------------------------------------------------------------------------------------------ rooftop sign

	/** 4 x 7 (M 5 x 7, I 3 x 7, apostrophe 1 x 7) pixel letters, rows top to bottom. */
	static final String[][] BIG = {
			{"1110", "1001", "1001", "1110", "1001", "1001", "1110"}, // B
			{"1110", "1001", "1001", "1110", "1010", "1001", "1001"}, // R
			{"111", "010", "010", "010", "010", "010", "111"}, // I
			{"0110", "1001", "1001", "1111", "1001", "1001", "1001"}, // A
			{"1001", "1101", "1101", "1011", "1011", "1001", "1001"}, // N
			{"1", "1", "0", "0", "0", "0", "0"}, // '
			{"0111", "1000", "1000", "0110", "0001", "0001", "1110"}, // S
			{"000", "000", "000", "000", "000", "000", "000"}, // space
			{"10001", "11011", "10101", "10101", "10001", "10001", "10001"}, // M
	};
	static final int[] BRIANS_MARINA = {0, 1, 2, 3, 4, 5, 6, 7, 8, 3, 1, 2, 4, 3};

	static int textWidth(String[][] font, int[] text) {
		int w = 0;
		for (int i = 0; i < text.length; i++) w += font[text[i]][0].length() + (i > 0 ? 1 : 0);
		return w;
	}

	/**
	 * Letters on the plane x = lx facing east, so they read left to right from the east: column 0 at the south end
	 * (startZ) and later columns going north. Row 0 at y top.
	 */
	static void letters(B b, String[][] font, int[] text, int lx, int top, int startZ, BlockState on, BlockState off) {
		int z = startZ;
		for (int i = 0; i < text.length; i++) {
			String[] g = font[text[i]];
			for (int c = 0; c < g[0].length(); c++, z--)
				for (int r = 0; r < g.length; r++) {
					boolean px = g[r].charAt(c) == '1';
					if (px) b.set(lx, top - r, z, on);
					else if (off != null) b.set(lx, top - r, z, off);
				}
			if (i < text.length - 1) {
				if (off != null) for (int r = 0; r < g.length; r++) b.set(lx, top - r, z, off);
				z--;
			}
		}
	}

	static final int SIGN_TOP = 22, SIGN_MID_Z = 33;

	private void sign(B b) {
		if (!b.inClipNear(17, 40, 40)) return;
		int w = textWidth(BIG, BRIANS_MARINA), start = SIGN_MID_Z + w / 2, end = start - w + 1;
		letters(b, BIG, BRIANS_MARINA, 17, SIGN_TOP, start, signRed(), null);
		// steel frame behind the letters: a bottom chord, a top chord and posts down to the roof
		BlockState steel = rackSteel(), chord = B.axis(rackSteel(), Direction.Axis.Z);
		for (int z = end; z <= start; z++) {
			b.set(16, SIGN_TOP - 7, z, chord);
			if ((start - z) % 5 == 0 || z == end) {
				int k = Math.max(0, roofStep(16, z));
				for (int y = WALL + 1 + k / 2 + (k % 2); y <= SIGN_TOP; y++) b.set(16, y, z, steel);
			}
		}
		// night: a strip of lights under the letters washes them red
		for (int z = end; z <= start; z += 3) b.set(17, SIGN_TOP - 7, z, Blocks.SEA_LANTERN.defaultBlockState());
	}

	// ------------------------------------------------------------------------------------------------ racks

	private void racksInside(B b) {
		BlockState steel = B.axis(rackSteel(), Direction.Axis.Y), beam = B.axis(rackSteel(), Direction.Axis.X);
		// west racks the whole length, bows to the aisle; east racks along the straight wall
		for (int side = 0; side < 2; side++) {
			int xa = side == 0 ? 1 : 24, xb = side == 0 ? 9 : 32;
			for (int p = 2; p <= 80; p += 4) {
				int bayEnd = p + 4;
				if (!inBarn(xb + 1, bayEnd) || !inBarn(xb + 1, p)) break;
				if (side == 0 && (westDoor(p) || westDoor(p + 1) || westDoor(p + 3) || westDoor(p - 1))) continue;
				if (!b.inClipNear(xa, p, 12)) continue;
				for (int pz : new int[] {p, bayEnd}) {
					for (int y = 1; y <= 9; y++) {
						b.set(xa, y, pz, steel);
						b.set(xb, y, pz, steel);
					}
					for (int x = xa + 1; x < xb; x++) b.set(x, 9, pz, beam);
				}
				for (int tier = 0; tier < 3; tier++) {
					int y = 1 + tier * 3;
					if (tier > 0) for (int x = xa + 1; x < xb; x++) {
						b.set(x, y - 1, p + 1, beam);
						b.set(x, y - 1, p + 3, beam);
					}
					if (b.rnd(side, tier, p, 31) < 0.85) {
						int xs = side == 0 ? xa : xb, dir = side == 0 ? 1 : -1;
						boat(b, xs, y, p + 1, dir, b.rnd(side, tier, p, 32), false);
					}
				}
			}
		}
	}

	/** RENT A RACK in 3 x 5 letters (N 4 wide). */
	static final String[][] SMALL = {
			{"110", "101", "110", "101", "101"}, // R
			{"111", "100", "110", "100", "111"}, // E
			{"1001", "1101", "1011", "1001", "1001"}, // N
			{"111", "010", "010", "010", "010"}, // T
			{"00", "00", "00", "00", "00"}, // space
			{"010", "101", "111", "101", "101"}, // A
			{"011", "100", "100", "100", "011"}, // C
			{"101", "101", "110", "101", "101"}, // K
	};
	static final int[] RENT_A_RACK = {0, 1, 2, 3, 4, 5, 4, 0, 5, 6, 7};

	private void rentARack(B b) {
		if (!b.inClipNear(38, 32, 30)) return;
		BlockState steel = B.axis(rackDark(), Direction.Axis.Y), beam = B.axis(rackDark(), Direction.Axis.X), cap = B.axis(rackDark(), Direction.Axis.Z);
		for (int p = 10; p <= 54; p += 4) {
			for (int y = 1; y <= 11; y++) {
				b.set(35, y, p, steel);
				b.set(42, y, p, steel);
			}
			if (p == 54) break;
			for (int tier = 0; tier < 4; tier++) {
				int y = 1 + tier * 3;
				if (tier > 0) for (int x = 36; x <= 41; x++) {
					b.set(x, y - 1, p + 1, beam);
					b.set(x, y - 1, p + 3, beam);
				}
				if (b.rnd(7, tier, p, 41) < 0.88) boat(b, 35, y, p + 1, 1, b.rnd(7, tier, p, 42), false);
			}
		}
		for (int z = 10; z <= 54; z++) {
			b.set(35, 12, z, cap);
			b.set(42, 12, z, cap);
		}
		// red sign with white letters hung on the front of the rack (L3269: RENT A RACK)
		int w = textWidth(SMALL, RENT_A_RACK), start = 32 + w / 2;
		letters(b, SMALL, RENT_A_RACK, 43, 8, start, signWhite(), signRed());
		for (int y = 4; y <= 8; y++) {
			b.set(43, y, start + 1, signRed());
			b.set(43, y, start - w, signRed());
		}
	}

	// ------------------------------------------------------------------------------------------------ office

	private void office(B b) {
		if (!b.inClipNear(22, 90, 16)) return;
		BlockState wall = clapboardWhite(), win = houseWindow(), floor = hardwood(), air = Blocks.AIR.defaultBlockState();
		int x0 = 10, x1 = 23, z0 = 85, z1 = 94;
		for (int lx = x0; lx <= x1; lx++)
			for (int lz = z0; lz <= z1; lz++) {
				boolean edge = lx == x0 || lx == x1 || lz == z0 || lz == z1;
				if (!edge) {
					b.set(lx, 4, lz, floor); // upstairs floor
					continue;
				}
				boolean corner = (lx == x0 || lx == x1) && (lz == z0 || lz == z1);
				int run = lx == x0 || lx == x1 ? lz - z0 : lx - x0;
				for (int y = 1; y <= 8; y++) {
					boolean window = !corner && run % 3 == 1 && (y == 2 || y == 3 || y == 6 || y == 7);
					Direction out = lx == x0 ? Direction.WEST : lx == x1 ? Direction.EAST : lz == z0 ? Direction.NORTH : Direction.SOUTH;
					b.set(lx, y, lz, window ? B.facing(win, out) : wall);
				}
			}
		b.door(16, 1, z1, doorCottage(), Direction.SOUTH, false);
		// night: lights on both floors show through the windows
		BlockState hang = B.hanging(Blocks.LANTERN.defaultBlockState(), true), stand = Blocks.LANTERN.defaultBlockState();
		b.set(13, 3, 88, hang);
		b.set(20, 3, 91, hang);
		b.set(12, 5, 87, stand);
		b.set(21, 5, 92, stand);
		b.door(16, 5, z1, doorCottage(), Direction.SOUTH, false);
		// hip roof in white sheet stairs, flat top with the cupola
		BlockState st = roofWhiteStairs(), sheet = roofWhite();
		for (int i = 0; i <= 3; i++) {
			int a0 = x0 - 1 + i, a1 = x1 + 1 - i, c0 = z0 - 1 + i, c1 = z1 + 1 - i, y = 9 + i;
			for (int lx = a0; lx <= a1; lx++)
				for (int lz = c0; lz <= c1; lz++) {
					boolean edge = lx == a0 || lx == a1 || lz == c0 || lz == c1;
					if (!edge) {
						if (i == 3) b.set(lx, y, lz, sheet);
						continue;
					}
					Direction up = lz == c0 ? Direction.SOUTH : lz == c1 ? Direction.NORTH : lx == a0 ? Direction.EAST : Direction.WEST;
					b.set(lx, y, lz, B.stairs(st, up, false));
				}
		}
		for (int lx = 15; lx <= 18; lx++)
			for (int lz = 88; lz <= 91; lz++) {
				boolean edge = lx == 15 || lx == 18 || lz == 88 || lz == 91;
				if (edge) {
					b.set(lx, 13, lz, wall);
					boolean mid = lx == 16 || lx == 17 || lz == 89 || lz == 90;
					b.set(lx, 14, lz, mid ? v("minecraft:glass_pane") : wall);
				}
			}
		for (int lx = 14; lx <= 19; lx++)
			for (int lz = 87; lz <= 92; lz++) {
				boolean edge = lx == 14 || lx == 19 || lz == 87 || lz == 92;
				Direction up = lz == 87 ? Direction.SOUTH : lz == 92 ? Direction.NORTH : lx == 14 ? Direction.EAST : Direction.WEST;
				b.set(lx, 15, lz, edge ? B.stairs(st, up, false) : sheet);
			}
		for (int lx = 16; lx <= 17; lx++)
			for (int lz = 89; lz <= 90; lz++) b.set(lx, 16, lz, B.slab(hullSlab(), false));
		// balcony across the south front on white posts, picket railing
		BlockState deck = B.slab(of("trim_white_slab", "minecraft:smooth_quartz_slab"), true), rail = picket();
		for (int lx = x0; lx <= x1; lx++)
			for (int lz = z1 + 1; lz <= z1 + 2; lz++) {
				b.set(lx, 4, lz, deck);
				if (lz == z1 + 2 || lx == x0 || lx == x1) b.set(lx, 5, lz, rail);
			}
		for (int lx : new int[] {x0, 14, 19, x1})
			for (int y = 1; y <= 3; y++) b.set(lx, y, z1 + 2, rail);
		// workshop shed east of the office: light metal walls, roll-up door, skylit low roof
		BlockState mw = workshopWall(), door = rollUpWhite(), sky = skylight(), half = B.slab(roofWhiteSlab(), false);
		for (int lx = 24; lx <= 34; lx++)
			for (int lz = 83; lz <= 94; lz++) {
				boolean edge = lx == 34 || lz == 83 || lz == 94 || lx == 24 && lz < 85;
				if (edge) for (int y = 1; y <= 5; y++) b.set(lx, y, lz, lz == 94 && lx >= 27 && lx <= 31 && y <= 4 ? door : mw);
				else for (int y = 1; y <= 5; y++) b.set(lx, y, lz, air);
			}
		for (int lx = 24; lx <= 35; lx++)
			for (int lz = 82; lz <= 95; lz++) {
				boolean light = lx >= 27 && lx <= 32 && Math.floorMod(lz, 4) == 0 && lz > 83 && lz < 94;
				b.set(lx, 6, lz, light ? sky : sheet);
				if (lx <= 28) b.set(lx, 7, lz, half);
			}
	}

	// ------------------------------------------------------------------------------------------------ yard props

	static final int[][] PALMS = {{-8, -6, 9}, {20, -7, 8}, {44, -6, 10}, {47, 8, 9}, {47, 26, 11}, {47, 44, 8}, {47, 60, 10},
			{46, 76, 9}, {4, 98, 8}, {38, 97, 10}, {-12, 92, 9}, {-6, 78, 11}, {8, -8, 7}};

	static final int[][] YARD_LIGHTS = {{49, 2}, {49, 22}, {49, 42}, {49, 62}, {49, 82}, {-3, -5}, {30, -5}, {-3, 40}, {-3, 80}, {36, 97}};

	private void props(B b) {
		for (int[] p : PALMS) palm(b, p[0], p[1], p[2]);
		// night: yard light poles along the road side and the basin, work lights hung under the barn trusses
		for (int[] q : YARD_LIGHTS) {
			if (!b.inClipNear(q[0], q[1], 3) || b.wet(q[0], q[1])) continue;
			for (int y = 1; y <= 7; y++) b.set(q[0], y, q[1], v("minecraft:polished_andesite_wall"));
			b.set(q[0], 8, q[1], Blocks.SEA_LANTERN.defaultBlockState());
		}
		BlockState hang = B.hanging(Blocks.LANTERN.defaultBlockState(), true);
		for (int lz = 6; lz <= 78; lz += 12) if (b.inClip(16, lz)) b.set(16, WALL - 1, lz, hang);
		// boats on trailers in the yard south of the rack
		for (int i = 0; i < 3; i++) {
			int z = 60 + i * 5;
			if (!b.inClipNear(40, z, 10)) continue;
			BlockState wheel = v("minecraft:black_concrete"), frame = Blocks.IRON_BARS.defaultBlockState();
			for (int x = 37; x <= 45; x++) b.set(x, 1, z + 1, frame);
			b.set(39, 1, z, wheel);
			b.set(39, 1, z + 2, wheel);
			b.set(41, 1, z, wheel);
			b.set(41, 1, z + 2, wheel);
			boat(b, 37, 2, z, 1, b.rnd(i, 0, 0, 51), true);
		}
		forklift(b, 15, 30, Direction.SOUTH);
		forklift(b, 28, 74, Direction.NORTH);
		// white picket fence on the road side with the yard entrance
		BlockState rail = picket();
		for (int lz = PAVE_Z0; lz <= PAVE_Z1; lz++) {
			if (lz >= 76 && lz <= 84 || !b.inClip(FENCE_X, lz) || b.wet(FENCE_X, lz)) continue;
			b.set(FENCE_X, b.ground(FENCE_X, lz) + 1, lz, rail);
		}
		// pallets, coolers and buckets by the barn doors
		if (b.inClipNear(12, -3, 4)) {
			b.set(8, 1, -3, crate());
			b.set(7, 1, -3, crate());
			b.set(7, 2, -3, crate());
			b.set(25, 1, -2, B.facing(cooler(), Direction.NORTH));
			b.set(26, 1, -2, bucket());
		}
	}

	private static void palm(B b, int lx, int lz, int h) {
		if (!b.inClipNear(lx, lz, 6) || b.wet(lx, lz)) return;
		int g = paved(lx, lz) ? 0 : b.ground(lx, lz);
		if (paved(lx, lz)) b.set(lx, 0, lz, Blocks.GRASS_BLOCK.defaultBlockState());
		if (hasPalm()) {
			int ldx = b.rnd(lx, 0, lz, 61) < 0.5 ? 1 : -1, ldz = b.rnd(lx, 0, lz, 62) < 0.5 ? 1 : -1;
			JasonHouse.lookPalm(b, lx, g, lz, h, ldx, ldz);
			return;
		}
		BlockState trunk = B.axis(Blocks.JUNGLE_LOG.defaultBlockState(), Direction.Axis.Y), leaf = B.persistent(Blocks.JUNGLE_LEAVES.defaultBlockState());
		for (int y = 1; y <= h; y++) b.set(lx, g + y, lz, trunk);
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
			for (int k = 1; k <= 3; k++) b.set(lx + d[0] * k, g + h + (k == 3 ? -1 : 0), lz + d[1] * k, leaf);
	}

	/** Yellow marina forklift with a tall mast, forks towards {@code forks}. */
	private static void forklift(B b, int lx, int lz, Direction forks) {
		if (!b.inClipNear(lx, lz, 6)) return;
		BlockState body = v("minecraft:yellow_concrete"), wheel = v("minecraft:black_concrete"), mast = Blocks.IRON_BARS.defaultBlockState();
		int fx = forks.getStepX(), fz = forks.getStepZ();
		Direction side = forks.getClockWise();
		for (int i = 0; i < 3; i++)
			for (int s = 0; s < 2; s++) {
				int x = lx + fx * i + side.getStepX() * s, z = lz + fz * i + side.getStepZ() * s;
				b.set(x, 1, z, i == 1 ? body : wheel);
				if (i == 0) b.set(x, 2, z, body); // counterweight
				if (i == 1) b.set(x, 3, z, B.slab(v("minecraft:smooth_stone_slab"), false));
			}
		for (int s = 0; s < 2; s++) {
			int x = lx + fx * 3 + side.getStepX() * s, z = lz + fz * 3 + side.getStepZ() * s;
			for (int y = 1; y <= 6; y++) b.set(x, y, z, mast);
			b.set(x + fx, 1, z + fz, B.slab(v("minecraft:smooth_stone_slab"), false));
			b.set(x + fx * 2, 1, z + fz * 2, B.slab(v("minecraft:smooth_stone_slab"), false));
		}
	}
}
