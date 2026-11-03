package dev.leonida.landmark.site;

import static dev.leonida.landmark.gen.KeysPalette.*;

import dev.leonida.core.api.GenTerrain;
import dev.leonida.landmark.gen.B;
import dev.leonida.landmark.gen.Canvas;
import dev.leonida.landmark.gen.Site;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Jason's house, Key Lento (gtadb L266, map x -2353.5 y -5532.3; real counterpart 12522 FL-24, Cedar Key). Weathered
 * sea-green board-and-batten stilt cottage, floor about 4 m up on bleached posts with knee braces, low gable roof in dark
 * shingles with a blue tarp patch and a TV antenna, L-shaped wrap deck on the west gable and the south front, a straight
 * run of stairs down the front past a satellite dish, grey shutters, a 9-lite front door, lattice under the stairs and
 * the rear deck, an enclosed storage room under the east end, a basketball hoop under the house. Inside (Trailer 2
 * 1:28, 1:57, 2:09, 2:14): living room with a tropical-print sofa, glass-top coffee table, TV by the deck door, snake
 * terrarium and shelf divider, green kitchen with a white fridge, hallway to a green 9-lite back door, bedroom and bath.
 * The lot: crushed-shell drive, parking spot for the turquoise Ganado, palms, agave and flowering shrubs, a power pole
 * with the line to the house, a dock with a skiff, mangroves and riprap at the shore.
 *
 * <p>Local frame: origin = north-west corner of the house body walls, +x east, +z south, y from the lot ground.
 * Body x 0..13, z 0..9; floor y 4; walls y 5..7; ceiling y 8; ridge y 11.
 */
public final class JasonHouse extends Site {
	static final int OX = -2361, OZ = 5527;
	/** Ground probe: the gtadb pin. */
	static final int PX = -2354, PZ = 5532;
	static final int F = 4;

	public JasonHouse() {
		super("jason_house", -2398, 5512, -2334, 5585);
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
	public boolean claims(int x, int z) {
		if (roadAt(x, z)) return false; // roads are generated regardless of claims
		int lx = x - OX, lz = z - OZ;
		if (lx >= -18 && lx <= 26 && lz >= -12 && lz <= 25) return true; // lot
		if (lx >= 4 && lx <= 12 && lz >= 25 && lz <= 58) return true; // drive to the road
		return lx >= -37 && lx <= -12 && lz >= -4 && lz <= 13; // dock
	}

	@Override
	public int groundY(GenTerrain t) {
		int w = t.waterY(PX, PZ);
		int g = t.groundY(PX, PZ);
		return w != Integer.MIN_VALUE ? Math.max(g, w + 1) : g;
	}

	@Override
	public List<View> views() {
		return List.of(
				// gtadb in-game photo / Trailer 2 0:08: low camera south-west of the house, car in front of the stairs
				View.look("photo", -5, 1.9, 19.5, 5, 6.5, 7, 70),
				View.look("photo_b", -2, 1.6, 22, 5, 6.5, 6, 70),
				View.look("photo_c", -1, 1.6, 20, 5, 6, 7, 70),
				View.look("stair", 6, 2.6, 19, 6, 3.5, 10, 70),
				View.look("aerial", -26, 30, 38, 6, 4, 5, 70),
				// Trailer 2 0:11: on the roof looking out over the water
				View.look("roof", 16, 13, 14, -12, 8, -4, 60),
				// Trailer 2 0:24: Jason at the front door on the deck
				View.look("porch", -2.5, 6.6, 11.5, 3, 6, 9, 70),
				// Trailer 2 2:14: living room towards the hallway and the back door
				View.look("living", 1.5, 6.5, 3.2, 13, 5.6, 4.5, 70),
				// Trailer 2 2:09: from the sofa to the TV and the deck door
				View.look("tv", 3.5, 6.3, 7.6, 1.5, 6, 2.5, 70),
				// Trailer 2 1:28: kitchen from the hallway
				View.look("kitchen", 8.5, 6.5, 4.5, 10.5, 5.8, 0.8, 75),
				View.look("dock", -30, 3.2, 12, 3, 6, 4, 60),
				View.look("drive", 8.5, 2.7, 42, 6, 5, 5, 60),
				View.look("under", 20, 2.7, 8, -2, 2.5, 6, 70),
				View.look("corner", -10, 2.8, 18, -2, 2.5, 9, 70),
				View.look("south", 6, 6, 42, 6, 5.5, 5, 40),
				View.look("west", -32, 6, 5, 6, 5.5, 5, 40));
	}

	// ------------------------------------------------------------------------------------------------ build

	@Override
	public void build(Canvas c, GenTerrain t) {
		int gy = groundY(t);
		B b = new B(c, t, OX, OZ, gy, 0x4A50L);
		lot(b);
		puddles(b);
		structure(b);
		roof(b);
		openings(b);
		decks(b);
		interior(b);
		underHouse(b);
		yard(b);
		shore(b);
	}

	/** Columns levelled to the site ground (the house pad and the parking area). */
	static boolean levelled(int lx, int lz) {
		return lx >= -7 && lx <= 20 && lz >= -4 && lz <= 24 || lx >= -12 && lx <= 20 && lz >= 12 && lz <= 26;
	}

	/** Surface y (relative) of a dry lot column after levelling. */
	static int surf(B b, int lx, int lz) {
		return levelled(lx, lz) ? 0 : b.ground(lx, lz);
	}

	static boolean nearWater(B b, int lx, int lz, int r) {
		for (int dx = -r; dx <= r; dx++)
			for (int dz = -r; dz <= r; dz++) if (b.wet(lx + dx, lz + dz)) return true;
		return false;
	}

	private void lot(B b) {
		BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState(), coarse = Blocks.COARSE_DIRT.defaultBlockState();
		BlockState gravel = Blocks.GRAVEL.defaultBlockState(), sand = sand(), shell = shellGravel(), air = Blocks.AIR.defaultBlockState();
		for (int lx = -18; lx <= 26; lx++)
			for (int lz = -12; lz <= 58; lz++) {
				boolean drive = lz > 25;
				if (drive && (lx < 4 || lx > 12)) continue;
				if (!b.inClip(lx, lz) || b.wet(lx, lz)) continue;
				int tg = b.ground(lx, lz);
				int s = drive ? tg : surf(b, lx, lz);
				for (int y = tg + 1; y < s; y++) b.set(lx, y, lz, dirt);
				for (int y = s + 1; y <= Math.max(tg, s) + (levelled(lx, lz) ? 16 : 3); y++) b.set(lx, y, lz, air);
				double r = b.rnd(lx, 0, lz, 1), r2 = b.rnd(lx, 0, lz, 2);
				BlockState top;
				boolean under = lx >= -4 && lx <= 16 && lz >= -1 && lz <= 12;
				boolean park = (lx >= 3 && lx <= 21 && lz >= 13 && lz <= 25) || (drive && lx >= 5 && lx <= 11);
				if (park) {
					boolean track = drive && (lx == 7 || lx == 8 || lx == 9) && r < 0.55; // grass between the wheel tracks
					top = track ? grass : r < 0.5 ? gravel : r < 0.75 ? Blocks.SAND.defaultBlockState() : r < 0.85 ? shell : r < 0.93 ? sand : grass;
				} else if (under) top = r < 0.45 ? sand : r < 0.7 ? coarse : r < 0.85 ? shell : grass;
				else if (lz >= 9 && lx >= -14 && lx <= 22) {
					// front yard (L266_ig): pale wet sandy gravel and crushed shell with clumps of grass, no lawn
					double clump = b.rnd(lx >> 1, 0, lz >> 1, 8);
					top = clump < 0.2 ? grass : r < 0.55 ? gravel : r < 0.82 ? Blocks.SAND.defaultBlockState() : r < 0.92 ? sand : shell;
				}
				else if (nearWater(b, lx, lz, 2)) top = r < 0.7 ? sand : r < 0.85 ? Blocks.ANDESITE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
				else {
					// patchy sandy lawn: sand and bare dirt in 4 m clumps, not single-block speckles
					double clump = b.rnd(lx >> 2, 0, lz >> 2, 7);
					top = clump < 0.2 && r < 0.8 ? sand : grass;
				}
				b.set(lx, s, lz, top);
				b.set(lx, s - 1, lz, top == grass ? dirt : top == shell ? gravel : sand);
				boolean nearCam = lz >= 17 && lx <= 6; // keep the photo camera foreground clear of grass blades
				if (top == grass && !under && !nearCam) {
					if (r2 < 0.22) b.set(lx, s + 1, lz, Blocks.SHORT_GRASS);
					else if (r2 < 0.26) b.set(lx, s + 1, lz, Blocks.FERN);
					else if (r2 < 0.27) b.set(lx, s + 1, lz, Blocks.DANDELION);
				} else if (top == grass && park && r2 < 0.5) b.set(lx, s + 1, lz, Blocks.SHORT_GRASS);
			}
	}

	/** Rain puddles in the front yard (L266_ig): x, z, width, depth. */
	private static final int[][] PUDDLES = {{-9, 17, 3, 2}, {-4, 21, 4, 2}, {1, 16, 2, 2}, {6, 22, 3, 2}, {9, 18, 2, 1}, {15, 21, 3, 2}, {-6, 14, 2, 1}, {-1, 24, 3, 1}};

	private void puddles(B b) {
		BlockState water = Blocks.WATER.defaultBlockState(), sand = sand();
		for (int[] p : PUDDLES)
			for (int x = p[0]; x < p[0] + p[2]; x++)
				for (int z = p[1]; z < p[1] + p[3]; z++) {
					if (!b.inClip(x, z) || !levelled(x, z)) continue;
					// only where every neighbour is dry levelled ground, so the water can never spill
					boolean ok = true;
					for (int dx = -1; dx <= 1 && ok; dx++)
						for (int dz = -1; dz <= 1 && ok; dz++) ok = levelled(x + dx, z + dz) && !b.wet(x + dx, z + dz);
					if (!ok) continue;
					b.set(x, 0, z, water);
					b.set(x, -1, z, sand);
					b.set(x, 1, z, Blocks.AIR.defaultBlockState());
				}
	}

	// ------------------------------------------------------------------------------------------------ frame

	private static final int[][] POSTS = {
			{0, 0}, {4, 0}, {9, 0}, {13, 0}, {9, 5}, {13, 5}, {0, 9}, {4, 9}, {9, 9}, {13, 9}, // body
			{-4, -1}, {-4, 5}, {-4, 12}, {2, 12}, // west deck and front deck (stair head)
			{16, 2}, {16, 5}, {16, 9}}; // rear deck

	static boolean deckPost(int[] p) {
		return p[0] < 0 || p[0] > 13 || p[1] < 0 || p[1] > 9;
	}

	private void structure(B b) {
		BlockState bx = B.axis(joist(), Direction.Axis.X), bz = B.axis(joist(), Direction.Axis.Z);
		// slender 25 cm wood stilts; deck posts run up through the half-slab deck so their tops sit flush with the boards
		BlockState gird = B.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), true);
		b.fill(1, 3, 5, 12, 3, 5, gird); // centre girder, hidden under the house
		for (int[] p : POSTS) {
			int g = b.wet(p[0], p[1]) ? Math.max(b.ground(p[0], p[1]), -6) : surf(b, p[0], p[1]);
			b.fill(p[0], g + 1, p[1], p[0], deckPost(p) ? F : 3, p[1], stilt());
		}
		// knee braces (L266_ig: Y-shaped braces at the post heads): under the body floor an upside-down stair either side
		// of each post along the wall lines; the deck posts get theirs in decks()
		BlockState br = braceWood();
		for (int[] p : POSTS) {
			if (deckPost(p)) continue;
			for (Direction d : Direction.Plane.HORIZONTAL) {
				int x = p[0] + d.getStepX(), z = p[1] + d.getStepZ();
				if (x < 0 || x > 13 || z < 0 || z > 9) continue;
				boolean onEdge = (x == 0 || x == 13) && d.getAxis() == Direction.Axis.Z || (z == 0 || z == 9) && d.getAxis() == Direction.Axis.X;
				if (onEdge) b.set(x, 3, z, B.stairs(br, d.getOpposite(), true));
			}
		}
		// floor (y 4): rim joists on the body edge, hardwood inside, tile in the bath
		b.fill(0, F, 0, 13, F, 0, bx);
		b.fill(0, F, 9, 13, F, 9, bx);
		b.fill(0, F, 1, 0, F, 8, bz);
		b.fill(13, F, 1, 13, F, 8, bz);
		b.fill(1, F, 1, 12, F, 8, hardwood());
		b.fill(11, F, 6, 12, F, 8, tile());
		// walls y 5..7 with corner boards
		for (int x = 0; x <= 13; x++)
			for (int y = 5; y <= 7; y++) {
				b.set(x, y, 0, sidingLined(Direction.NORTH, b.rnd(x, 0, 0, 3)));
				b.set(x, y, 9, sidingLined(Direction.SOUTH, b.rnd(x, 0, 9, 3)));
			}
		for (int z = 1; z <= 8; z++)
			for (int y = 5; y <= 7; y++) {
				b.set(0, y, z, sidingLined(Direction.WEST, b.rnd(0, 0, z, 3)));
				b.set(13, y, z, sidingLined(Direction.EAST, b.rnd(13, 0, z, 3)));
			}
		// off-white corner boards (peeling trim in the photo)
		for (int[] cnr : new int[][] {{0, 0}, {13, 0}, {0, 9}, {13, 9}}) b.fill(cnr[0], 5, cnr[1], cnr[0], 7, cnr[1], trim());
		// interior partitions (birch lining): bedroom/bath block z 5..8 east of x 6, bath wall x 10
		BlockState iw = interiorWall();
		b.fill(6, 5, 5, 12, 7, 5, iw);
		b.fill(6, 5, 6, 6, 7, 8, iw);
		b.fill(10, 5, 6, 10, 7, 8, iw);
		// ceiling y 8 with dark exposed beams
		b.fill(1, 8, 1, 12, 8, 8, iw);
		BlockState dk = B.axis(Blocks.DARK_OAK_LOG.defaultBlockState(), Direction.Axis.Z);
		for (int x : new int[] {3, 6, 9}) b.fill(x, 8, 1, x, 8, 8, dk);
	}

	// ------------------------------------------------------------------------------------------------ roof

	/** Roof profile across z (-1..10): cell y and kind (0 bottom slab, 1 top slab, 2 full). */
	private static final int[][] PROFILE = {{8, 0}, {8, 2}, {9, 0}, {9, 1}, {10, 0}, {10, 1}, {10, 1}, {10, 0}, {9, 1}, {9, 0}, {8, 2}, {8, 0}};

	private void roof(B b) {
		BlockState sh = shingle(), shs = shingleSlab(), fas = fasciaSlab();
		for (int x = -1; x <= 14; x++)
			for (int z = -1; z <= 10; z++) {
				int[] p = PROFILE[z + 1];
				int y = p[0], kind = p[1];
				BlockState s;
				if ((x == -1 || x == 14) && kind == 2) s = B.slab(shs, true);
				else if ((x == 0 || x == 13) && kind == 1) s = sh;
				else s = kind == 2 ? sh : B.slab(shs, kind == 1);
				b.set(x, y, z, s);
			}
		// gable triangles
		for (int z = 1; z <= 8; z++) {
			int top = PROFILE[z + 1][0] - 1;
			for (int y = 8; y <= top; y++) {
				b.set(0, y, z, siding(b.rnd(0, y, z, 4)));
				b.set(13, y, z, siding(b.rnd(13, y, z, 4)));
			}
		}
		// gable louvre vents under the peak
		for (int z = 4; z <= 5; z++) {
			b.set(-1, 9, z, B.panel(Blocks.SPRUCE_TRAPDOOR.defaultBlockState(), Direction.WEST, false));
			b.set(14, 9, z, B.panel(Blocks.SPRUCE_TRAPDOOR.defaultBlockState(), Direction.EAST, false));
		}
		// metal ridge cap
		b.fill(-1, 11, 4, 14, 11, 5, B.slab(ridgeSlab(), false));
		// blue tarp patch over the leak Jason is fixing (Trailer 2 0:11), both slopes near the middle
		BlockState tarp = tarp();
		for (int x = 6; x <= 9; x++) b.set(x, 10, 2, tarp);
		for (int x = 5; x <= 8; x++) b.set(x, 10, 7, tarp);
		for (int x = 6; x <= 8; x++) {
			b.set(x, 11, 4, tarp);
			b.set(x, 11, 5, tarp);
		}
		// TV antenna on the east end of the ridge
		BlockState bars = Blocks.IRON_BARS.defaultBlockState();
		b.fill(12, 11, 4, 12, 14, 4, bars);
		b.set(11, 13, 4, bars);
		b.set(13, 13, 4, bars);
		b.set(11, 14, 4, bars);
		b.set(13, 14, 4, bars);
		b.set(12, 15, 4, v("minecraft:lightning_rod"));
		// yellow ladder against the front-west corner, up from the deck to the eave (Trailer 2 0:18)
		BlockState ladder = B.facing(Blocks.LADDER.defaultBlockState(), Direction.WEST);
		b.fill(-1, 5, 9, -1, 7, 9, ladder);
	}

	// ------------------------------------------------------------------------------------------------ windows and doors

	private void window2(B b, int x, int z, Direction out, boolean shutters, boolean blindInside) {
		BlockState w = window();
		b.set(x, 5, z, w);
		b.set(x, 6, z, w);
		int ox = out.getStepX(), oz = out.getStepZ();
		if (shutters) {
			Direction side = out.getClockWise();
			for (int s = -1; s <= 1; s += 2)
				for (int y = 5; y <= 6; y++) b.set(x + side.getStepX() * s + ox, y, z + side.getStepZ() * s + oz, B.panel(shutter(), out, false));
		}
		if (blindInside) b.set(x - ox, 6, z - oz, B.panel(blind(), out.getOpposite(), true));
	}

	private void openings(B b) {
		// south front (z 9): door from the deck, living window, bedroom window, louvre vent, bath window
		b.door(2, 5, 9, doorCottage(), Direction.SOUTH, false);
		window2(b, 4, 9, Direction.SOUTH, true, false);
		window2(b, 7, 9, Direction.SOUTH, true, true);
		b.set(9, 6, 10, B.panel(Blocks.SPRUCE_TRAPDOOR.defaultBlockState(), Direction.SOUTH, false));
		window2(b, 11, 9, Direction.SOUTH, true, true);
		// porch light beside the door, under the eave
		b.set(3, 7, 10, B.hanging(Blocks.LANTERN.defaultBlockState(), true));
		// north (z 0): living window with shutters, two small kitchen windows above the counter with bamboo blinds
		window2(b, 3, 0, Direction.NORTH, true, false);
		for (int x : new int[] {8, 11}) {
			b.set(x, 6, 0, window());
			b.set(x, 6, 1, B.panel(blind(), Direction.SOUTH, true));
		}
		// west gable (x 0): window, French doors to the deck, window
		window2(b, 0, 3, Direction.WEST, true, false);
		b.door(0, 5, 5, Blocks.BIRCH_DOOR.defaultBlockState(), Direction.WEST, false);
		b.door(0, 5, 6, Blocks.BIRCH_DOOR.defaultBlockState(), Direction.WEST, true);
		window2(b, 0, 8, Direction.WEST, true, false);
		// east gable (x 13): green 9-lite back door, small bath window
		b.door(13, 5, 4, doorCottage(), Direction.EAST, false);
		b.set(13, 6, 7, window());
		// interior doors
		b.door(8, 5, 5, Blocks.SPRUCE_DOOR.defaultBlockState(), Direction.SOUTH, false);
		b.door(12, 5, 5, Blocks.SPRUCE_DOOR.defaultBlockState(), Direction.SOUTH, true);
	}

	// ------------------------------------------------------------------------------------------------ decks and stairs

	private void decks(B b) {
		BlockState d = B.slab(deckSlab(), true), rail = railing();
		b.fill(-4, F, -1, -1, F, 12, d); // west deck (water side)
		b.fill(0, F, 10, 2, F, 12, d); // front deck strip to the stair head
		b.fill(14, F, 2, 16, F, 9, d); // rear deck
		// open baluster railing on the deck edges
		b.fill(-4, 5, -1, -4, 5, 12, rail);
		b.fill(-4, 5, -1, -1, 5, -1, rail);
		b.fill(-4, 5, 12, 2, 5, 12, rail);
		b.fill(16, 5, 2, 16, 5, 9, rail);
		b.fill(14, 5, 2, 16, 5, 2, rail);
		b.fill(14, 5, 9, 16, 5, 9, rail);
		b.set(-1, 5, 9, Blocks.AIR.defaultBlockState()); // ladder foot stays clear
		// knee braces at the deck post heads: the deck board next to each post becomes an upside-down stair whose lower
		// half points at the post, so post, brace and deck board read as one bracket from below
		BlockState ds = deckStairs();
		for (int[] p : POSTS) {
			if (!deckPost(p)) continue;
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				int x = p[0] + dir.getStepX(), z = p[1] + dir.getStepZ();
				if (b.get(x, F, z) == d) b.set(x, F, z, B.stairs(ds, dir.getOpposite(), true));
			}
		}
		// front stairs (L266_ig): one long diagonal run down the front face from the stair head (x 2) to the yard (x 10),
		// open half-metre treads (alternating bottom and top slabs, no risers), a wooden handrail on both sides
		BlockState tread = deckSlab();
		for (int i = 1; i <= 7; i++) {
			int x = 2 + i;
			double h = 5 - 0.5 * i; // tread surface height
			boolean odd = (i & 1) == 1;
			int ty = odd ? (int) Math.floor(h) : (int) h - 1;
			b.set(x, ty, 11, B.slab(tread, !odd));
			int ry = (int) Math.ceil(h);
			b.set(x, ry, 10, rail);
			b.set(x, ry, 12, rail);
			// lattice screen behind the lower half of the run, against the space under the house
			if (i >= 5) for (int y = 1; y < ry - 1; y++) b.set(x, y, 10, B.panel(lattice(), Direction.SOUTH, false));
		}
		b.set(10, 1, 10, rail); // newel posts at the foot
		b.set(10, 1, 12, rail);
		// satellite dish on a short mast on the deck corner at the top of the stairs (gtadb photo)
		b.set(2, 6, 12, v("minecraft:lightning_rod"));
		b.set(2, 6, 13, B.panel(Blocks.IRON_TRAPDOOR.defaultBlockState(), Direction.SOUTH, false));
		b.set(2, 7, 12, v("minecraft:lightning_rod"));
		// rear deck lattice skirt
		for (int z = 3; z <= 8; z++)
			if (z != 5) b.fill(16, 1, z, 16, 2, z, B.panel(lattice(), Direction.EAST, false));
		for (int x = 14; x <= 15; x++) b.fill(x, 1, 9, x, 2, 9, B.panel(lattice(), Direction.SOUTH, false));
		// deck furniture: two wicker chairs and a side table facing the water, potted plants
		BlockState wick = Blocks.BAMBOO_MOSAIC_STAIRS.defaultBlockState();
		b.set(-3, 5, 4, B.stairs(wick, Direction.EAST, false));
		b.set(-3, 5, 6, B.stairs(wick, Direction.EAST, false));
		b.set(-3, 5, 5, B.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), true));
		b.set(-3, 5, 11, Blocks.POTTED_FERN);
		b.set(-3, 5, 0, v("minecraft:potted_flowering_azalea"));
		b.set(15, 5, 8, B.stairs(wick, Direction.WEST, false));
	}

	// ------------------------------------------------------------------------------------------------ interior

	private void interior(B b) {
		// living room x 1..5
		BlockState dkSlabTop = B.slab(Blocks.DARK_OAK_SLAB.defaultBlockState(), true);
		b.set(2, 5, 1, dkSlabTop);
		b.set(2, 5, 2, dkSlabTop);
		b.set(2, 6, 1, v("minecraft:black_stained_glass_pane")); // flat TV
		b.set(2, 6, 2, v("minecraft:black_stained_glass_pane"));
		b.set(1, 5, 1, Blocks.NOTE_BLOCK); // speaker
		b.set(4, 5, 2, B.stairs(Blocks.BAMBOO_MOSAIC_STAIRS.defaultBlockState(), Direction.EAST, false)); // wicker chair
		BlockState sofa = sofa();
		for (int x = 2; x <= 4; x++) b.set(x, 5, 8, B.stairs(sofa, Direction.SOUTH, false));
		b.set(1, 5, 8, B.slab(sofaSlab(), false));
		b.set(5, 5, 8, Blocks.SPRUCE_FENCE); // lamp table
		b.set(5, 6, 8, Blocks.LANTERN);
		for (int x = 2; x <= 4; x++) b.set(x, 5, 6, B.facing(B.slab(coffeeTable(), false), Direction.SOUTH)); // glass-top coffee table
		for (int x = 1; x <= 5; x++) {
			b.set(x, 5, 5, v("minecraft:brown_carpet"));
			b.set(x, 5, 7, v("minecraft:brown_carpet"));
		}
		b.set(1, 5, 6, v("minecraft:brown_carpet"));
		b.set(5, 5, 6, v("minecraft:brown_carpet"));
		// snake terrarium on a dark stand by the divider
		b.set(5, 5, 1, dkSlabTop);
		b.set(5, 5, 2, dkSlabTop);
		b.set(5, 6, 1, Blocks.GLASS);
		b.set(5, 6, 2, Blocks.GLASS);
		b.set(1, 5, 4, Blocks.POTTED_FERN);
		b.set(1, 7, 8, B.persistent(Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState()));
		b.set(3, 7, 5, B.hanging(Blocks.LANTERN.defaultBlockState(), true));
		// open shelf divider between the living room and the kitchen
		b.set(6, 5, 1, Blocks.BOOKSHELF);
		b.set(6, 5, 2, Blocks.BOOKSHELF);
		b.set(6, 6, 1, Blocks.SPRUCE_FENCE);
		b.set(6, 6, 2, Blocks.SPRUCE_FENCE);
		b.set(6, 7, 1, B.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), true));
		b.set(6, 7, 2, B.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), true));
		// kitchen x 7..12, z 1..3: counters along the north wall, sink, stove, white fridge, green upper cabinets
		BlockState cab = kitchenCabinet();
		for (int x = 7; x <= 11; x++) b.set(x, 5, 1, counter());
		b.set(8, 5, 1, Blocks.CAULDRON);
		b.set(10, 5, 1, B.facing(Blocks.SMOKER.defaultBlockState(), Direction.SOUTH));
		// white fridge with the memo board of notes and magnets on its door (Trailer 2 1:28)
		BlockState fr = B.facing(fridge(), Direction.SOUTH);
		if (fr.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			b.set(12, 5, 1, fr.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			b.set(12, 6, 1, fr.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		} else {
			b.set(12, 5, 1, fr);
			b.set(12, 6, 1, fr);
		}
		for (int x = 7; x <= 12; x++) b.set(x, 7, 1, cab);
		b.set(7, 6, 1, Blocks.GLASS); // small tank on the counter (Trailer 2 1:28)
		b.set(9, 7, 3, B.hanging(Blocks.LANTERN.defaultBlockState(), true));
		b.set(11, 5, 3, B.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), true)); // small table
		b.set(12, 5, 3, B.stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.EAST, false));
		// hallway z 4 to the back door
		b.set(11, 7, 4, B.hanging(Blocks.LANTERN.defaultBlockState(), true));
		b.set(12, 5, 4, v("minecraft:green_carpet"));
		// bedroom x 7..9, z 6..8
		b.bed(8, 5, 7, v("minecraft:red_bed").getBlock(), Direction.SOUTH);
		b.set(9, 5, 8, B.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), true));
		b.set(9, 6, 8, Blocks.LANTERN);
		b.set(9, 5, 6, B.facing(Blocks.BARREL.defaultBlockState(), Direction.UP));
		b.set(7, 5, 6, v("minecraft:brown_carpet"));
		// bath x 11..12, z 6..8
		b.set(12, 5, 8, B.stairs(Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState(), Direction.SOUTH, false));
		b.set(11, 5, 8, Blocks.CAULDRON);
		b.set(11, 5, 6, Blocks.CAULDRON);
		b.set(11, 7, 7, B.hanging(Blocks.LANTERN.defaultBlockState(), true));
	}

	// ------------------------------------------------------------------------------------------------ under the house

	private void underHouse(B b) {
		BlockState wall;
		// storage room under the east end (x 9..13, z 5..9), siding walls, door to the front
		for (int x = 9; x <= 13; x++)
			for (int z = 5; z <= 9; z++) {
				boolean edge = x == 9 || x == 13 || z == 5 || z == 9;
				if (!edge) continue;
				for (int y = 1; y <= 3; y++) {
					if (!b.get(x, y, z).isAir()) continue;
					wall = y == 1 || b.rnd(x, y, z, 5) < 0.4 ? sidingDark() : siding(b.rnd(x, y, z, 5));
					b.set(x, y, z, wall);
				}
			}
		b.door(11, 1, 9, Blocks.SPRUCE_DOOR.defaultBlockState(), Direction.SOUTH, false);
		b.set(13, 2, 7, window());
		b.set(10, 1, 6, B.facing(Blocks.BARREL.defaultBlockState(), Direction.UP));
		b.set(12, 1, 6, Blocks.CAULDRON);
		// basketball backboard on the storage wall, facing the open space under the house (Extended Look)
		b.set(8, 3, 6, v("minecraft:white_stained_glass_pane"));
		b.set(8, 3, 7, v("minecraft:white_stained_glass_pane"));
		b.set(7, 2, 6, Blocks.IRON_CHAIN);
		// clutter: lumber stack, barrel, white bucket, kayak
		BlockState lumber = B.axis(Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Direction.Axis.X);
		b.fill(5, 1, 2, 7, 1, 2, lumber);
		b.set(6, 2, 2, B.slab(Blocks.SPRUCE_SLAB.defaultBlockState(), false));
		b.set(2, 1, 2, B.facing(Blocks.BARREL.defaultBlockState(), Direction.UP));
		// buckets, crates, a cooler and the hose reel (L266_ig: white bucket at the post foot, yard clutter)
		b.set(4, 1, 11, bucket());
		b.set(6, 1, 13, bucket());
		b.set(-2, 1, 10, bucket());
		b.set(-2, 1, 7, crate());
		b.set(-2, 1, 8, crate());
		b.set(-2, 2, 7, crate());
		b.set(3, 1, 8, B.facing(cooler(), Direction.SOUTH));
		b.set(-3, 5, 8, B.facing(cooler(), Direction.EAST));
		b.set(14, 2, 7, B.facing(hoseReel(), Direction.EAST)); // on the storage room wall
		b.fill(-3, 1, 2, -3, 1, 4, v("minecraft:orange_carpet"));
		b.set(-3, 1, 3, v("minecraft:black_carpet"));
		b.set(1, 1, 7, Blocks.COMPOSTER);
	}

	// ------------------------------------------------------------------------------------------------ yard

	private void yard(B b) {
		// palms (coconut palms behind and beside the house, gtadb photo)
		palm(b, -2, -6, 11, 0, -1);
		palm(b, 3, -5, 13, 1, -1);
		palm(b, 17, -4, 9, 1, 0);
		palm(b, 23, 10, 7, 1, 1);
		palm(b, -14, 9, 10, -1, 0);
		palm(b, -15, 21, 8, -1, 1);
		palm(b, 24, 22, 11, 1, 1);
		// agave and flowering shrubs under the front deck, sea grape and hibiscus around the lot
		doublePlant(b, 3, 13, Blocks.LARGE_FERN);
		doublePlant(b, 6, 13, Blocks.LARGE_FERN);
		doublePlant(b, 4, 14, Blocks.LILAC);
		doublePlant(b, 1, 13, Blocks.ROSE_BUSH);
		doublePlant(b, 7, 12, Blocks.PEONY);
		bush(b, -7, 11, Blocks.JUNGLE_LEAVES, 2);
		bush(b, -8, 4, Blocks.JUNGLE_LEAVES, 2);
		bush(b, 19, 0, Blocks.AZALEA_LEAVES, 2);
		bush(b, 20, 3, Blocks.FLOWERING_AZALEA_LEAVES, 1);
		bush(b, -10, 1, Blocks.FLOWERING_AZALEA_LEAVES, 1);
		bush(b, 18, 13, Blocks.OAK_LEAVES, 1);
		bush(b, 22, 16, Blocks.JUNGLE_LEAVES, 2);
		bush(b, 24, 4, Blocks.AZALEA_LEAVES, 1);
		// AC condenser and trash bin beside the storage room
		b.set(17, 1, 6, stilt()); // condenser up on a short wooden stand
		b.set(17, 2, 6, acUnit());
		b.set(17, 1, 11, Blocks.COMPOSTER);
		// power pole by the drive with the line to the east gable and on towards the road
		BlockState pole = pole();
		b.fill(20, 1, 14, 20, 9, 14, pole);
		b.set(20, 10, 14, B.axis(poleCrossarm(), Direction.Axis.X));
		b.set(19, 9, 14, Blocks.SEA_LANTERN.defaultBlockState()); // yard light on the pole, lights the drive at night
		// night: porch lanterns on the deck rail corners, lights under the house, two low yard lamps by the drive and lot
		BlockState lamp = Blocks.LANTERN.defaultBlockState(), hang = B.hanging(Blocks.LANTERN.defaultBlockState(), true);
		b.set(0, F + 2, 12, lamp);
		b.set(-4, F + 2, 12, lamp);
		b.set(-4, F + 2, -1, lamp);
		b.set(16, F + 2, 9, lamp);
		b.set(4, F - 1, 3, hang);
		b.set(10, F - 1, 6, hang);
		for (int[] q : new int[][] {{14, 18}, {-8, 16}, {3, 30}}) {
			int g = surf(b, q[0], q[1]);
			for (int y = 1; y <= 3; y++) b.set(q[0], g + y, q[1], v("minecraft:polished_andesite_wall"));
			b.set(q[0], g + 4, q[1], Blocks.SEA_LANTERN.defaultBlockState());
		}
		int gz = surf(b, 21, 50);
		b.fill(21, gz + 1, 50, 21, gz + 9, 50, pole);
		b.set(21, gz + 10, 50, B.axis(poleCrossarm(), Direction.Axis.X));
		java.util.function.Function<Direction.Axis, BlockState> wire = a -> B.axis(Blocks.IRON_CHAIN.defaultBlockState(), a);
		b.line(19, 10, 14, 14, 8, 7, wire);
		b.line(20, 11, 15, 21, gz + 11, 49, wire);
		// mailbox at the end of the drive
		int mz = 53, mg = b.wet(12, mz) ? 0 : b.ground(12, mz);
		b.set(12, mg + 1, mz, Blocks.SPRUCE_FENCE);
		b.set(12, mg + 2, mz, Blocks.HEAVY_CORE);
	}

	static void palm(B b, int lx, int lz, int h, int ldx, int ldz) {
		if (!b.inClipNear(lx, lz, 6) || b.wet(lx, lz)) return;
		int g = surf(b, lx, lz);
		if (hasPalm()) {
			lookPalm(b, lx, g, lz, h, ldx, ldz);
			return;
		}
		BlockState trunk = B.axis(Blocks.JUNGLE_LOG.defaultBlockState(), Direction.Axis.Y);
		int x = lx, z = lz;
		for (int i = 0; i < h; i++) {
			double f = (double) i / h;
			x = lx + (int) Math.round(ldx * f * f * 2.5);
			z = lz + (int) Math.round(ldz * f * f * 2.5);
			b.set(x, g + 1 + i, z, trunk);
		}
		int top = g + h;
		BlockState leaf = B.persistent(Blocks.JUNGLE_LEAVES.defaultBlockState());
		b.set(x, top + 1, z, leaf);
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
			boolean diag = d[0] != 0 && d[1] != 0;
			int len = diag ? 3 : 4;
			for (int k = 1; k <= len; k++) {
				int dy = k <= 1 ? 1 : k == 2 ? 0 : -(k - 2);
				b.set(x + d[0] * k, top + dy, z + d[1] * k, leaf);
			}
		}
		b.set(x + 1, top, z, Blocks.COCOA.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
	}

	/** Curved coconut palm from the look palm parts (same curve as LookBlocks.palm, drawn on the canvas). */
	static void lookPalm(B b, int lx, int g, int lz, int h, int ldx, int ldz) {
		Direction lean = Math.abs(ldx) >= Math.abs(ldz) ? (ldx >= 0 ? Direction.EAST : Direction.WEST) : (ldz >= 0 ? Direction.SOUTH : Direction.NORTH);
		int maxLean = Math.max(16, Math.min(64, (int) (h * 16 / 2.5)));
		int total = 16 + (int) (b.rnd(lx, h, lz, 13) * (maxLean - 16 + 1));
		for (int k = 0; k < h; k++) {
			double t = (double) k / h;
			int off = (int) Math.round(total * t * t / 4.0) * 4;
			int col = off / 16;
			b.set(lx + lean.getStepX() * col, g + 1 + k, lz + lean.getStepZ() * col, palmTrunk(lean, (off % 16) / 4));
		}
		int col = ((int) Math.round(total / 4.0) * 4 + 8) / 16;
		int cx = lx + lean.getStepX() * col, cy = g + 1 + h, cz = lz + lean.getStepZ() * col;
		b.set(cx, cy, cz, palmCrown());
		for (Direction d : Direction.Plane.HORIZONTAL) {
			b.set(cx + d.getStepX(), cy, cz + d.getStepZ(), palmFrond(d, false));
			Direction cw = d.getClockWise();
			b.set(cx + d.getStepX() + cw.getStepX(), cy, cz + d.getStepZ() + cw.getStepZ(), palmFrond(d, true));
		}
	}

	static void doublePlant(B b, int lx, int lz, Block plant) {
		if (b.wet(lx, lz)) return;
		int g = surf(b, lx, lz);
		BlockState s = plant.defaultBlockState();
		b.set(lx, g + 1, lz, s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
		b.set(lx, g + 2, lz, s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
	}

	static void bush(B b, int lx, int lz, Block leaves, int r) {
		if (b.wet(lx, lz)) return;
		int g = surf(b, lx, lz);
		BlockState s = B.persistent(leaves.defaultBlockState());
		for (int dx = -r; dx <= r; dx++)
			for (int dz = -r; dz <= r; dz++)
				for (int dy = 1; dy <= r + 1; dy++) {
					double dd = dx * dx + dz * dz + (dy - 1) * (dy - 1) * 1.5;
					if (dd > r * r + 0.8 || b.rnd(lx + dx, dy, lz + dz, 9) < 0.15) continue;
					if (b.wet(lx + dx, lz + dz)) continue;
					b.set(lx + dx, surf(b, lx + dx, lz + dz) + dy, lz + dz, s);
				}
	}

	// ------------------------------------------------------------------------------------------------ shore and dock

	private void shore(B b) {
		// find the shoreline on the dock row
		int shoreX = Integer.MIN_VALUE;
		for (int lx = -6; lx >= -40; lx--)
			if (b.wet(lx, 4)) {
				shoreX = lx;
				break;
			}
		if (shoreX == Integer.MIN_VALUE) return;
		int wy = b.water(shoreX - 1, 4);
		int dy = wy + 1;
		BlockState d = deck(), post = B.axis(post(), Direction.Axis.Y), bz = B.axis(beam(), Direction.Axis.Z);
		int end = shoreX - 14;
		for (int x = shoreX + 1; x >= end; x--) {
			boolean head = x <= end + 2;
			for (int z = head ? 2 : 4; z <= (head ? 7 : 5); z++) {
				b.set(x, dy, z, d);
				for (int y = dy + 1; y <= dy + 3; y++) b.set(x, y, z, Blocks.AIR.defaultBlockState());
			}
			if ((shoreX - x) % 3 == 0 || x == end) {
				int z0 = head ? 1 : 3, z1 = head ? 8 : 6;
				for (int z : new int[] {z0, z1}) {
					int g = b.wet(x, z) ? b.ground(x, z) : dy - 1;
					for (int y = g + 1; y <= dy + 1; y++) b.set(x, y, z, post);
				}
				for (int z = z0 + 1; z < z1; z++) b.set(x, dy - 1, z, bz);
			}
		}
		// bench and a cleat line on the dock head
		b.set(end, dy + 1, 4, B.stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.WEST, false));
		b.set(end, dy + 1, 5, B.stairs(Blocks.SPRUCE_STAIRS.defaultBlockState(), Direction.WEST, false));
		skiff(b, shoreX - 11, 7, wy);
		// mangroves either side of the dock, riprap along the bank
		mangrove(b, shoreX - 2, -2, wy);
		mangrove(b, shoreX - 1, 11, wy);
		mangrove(b, shoreX - 4, 14, wy);
	}

	/** Small white centre-console skiff moored south of the dock, bow west. 3 wide, 6 long. */
	private void skiff(B b, int x0, int z0, int wy) {
		BlockState hull = Blocks.SMOOTH_QUARTZ.defaultBlockState(), gun = B.slab(Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState(), false);
		BlockState floor = B.slab(Blocks.BIRCH_SLAB.defaultBlockState(), true);
		for (int x = x0; x <= x0 + 5; x++)
			for (int z = z0; z <= z0 + 2; z++) {
				boolean edge = z == z0 || z == z0 + 2 || x == x0 + 5;
				if (x == x0) {
					if (z == z0 + 1) {
						b.set(x, wy, z, B.stairs(Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState(), Direction.EAST, false));
						b.set(x, wy + 1, z, gun);
					}
					continue;
				}
				b.set(x, wy, z, edge ? hull : floor);
				if (edge) b.set(x, wy + 1, z, gun);
			}
		b.set(x0 + 3, wy + 1, z0 + 1, v("minecraft:light_gray_concrete")); // console
		b.set(x0 + 3, wy + 2, z0 + 1, Blocks.GLASS_PANE);
		b.set(x0 + 6, wy, z0 + 1, Blocks.POLISHED_BLACKSTONE_WALL); // outboard
		b.set(x0 + 6, wy + 1, z0 + 1, Blocks.POLISHED_BLACKSTONE_WALL);
		b.set(x0 + 2, wy + 1, z0 - 1, Blocks.IRON_CHAIN); // mooring line to the dock
	}

	private static void mangrove(B b, int lx, int lz, int wy) {
		if (!b.wet(lx, lz)) return;
		BlockState roots = Blocks.MANGROVE_ROOTS.defaultBlockState();
		BlockState leaf = B.persistent(Blocks.MANGROVE_LEAVES.defaultBlockState());
		for (int dx = -1; dx <= 1; dx++)
			for (int dz = -1; dz <= 1; dz++) {
				if (b.rnd(lx + dx, 0, lz + dz, 11) < 0.35) continue;
				int g = b.ground(lx + dx, lz + dz);
				for (int y = g + 1; y <= wy + 1; y++) {
					BlockState r = roots;
					if (y <= wy && r.hasProperty(BlockStateProperties.WATERLOGGED)) r = r.setValue(BlockStateProperties.WATERLOGGED, true);
					b.set(lx + dx, y, lz + dz, r);
				}
			}
		b.set(lx, wy + 2, lz, B.axis(Blocks.MANGROVE_LOG.defaultBlockState(), Direction.Axis.Y));
		for (int dx = -2; dx <= 2; dx++)
			for (int dz = -2; dz <= 2; dz++)
				for (int y = wy + 3; y <= wy + 4; y++) {
					if (Math.abs(dx) + Math.abs(dz) > (y == wy + 4 ? 2 : 3) || b.rnd(lx + dx, y, lz + dz, 12) < 0.12) continue;
					b.set(lx + dx, y, lz + dz, leaf);
				}
	}
}
