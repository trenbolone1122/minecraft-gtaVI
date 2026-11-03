package dev.leonida.vicecity.site;

import dev.leonida.vicecity.ViceLandmarks;
import dev.leonida.vicecity.gen.Model;
import dev.leonida.vicecity.gen.Pal;
import dev.leonida.vicecity.gen.Site;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Vice Beach lifeguard towers (GTADB L373 to L381, L3344, L3345 on Ocean Beach, L2262 and L2263 on Washington Beach).
 * Trailer reference L379: a painted timber hut about 4 m wide on a 2.4 m stilt frame with X bracing, a wrap-around
 * deck with railings, a ramp down to the sand on the land side, a deep overhanging hip roof and a tall quarter-round
 * sail fin on the roof ridge. L381 shows the blue variant with a green box on the roof. The hut faces the ocean (-v).
 */
public final class LifeguardTower extends Site {
	/** Paint scheme: hut, roof, roof topper, stilts, railing; box = a block topper instead of the sail fin. */
	public record Paint(DyeColor hut, DyeColor roof, DyeColor topper, DyeColor stilts, boolean box) {}

	static final Paint YELLOW = new Paint(DyeColor.YELLOW, DyeColor.YELLOW, DyeColor.YELLOW, DyeColor.ORANGE, false);
	static final Paint BLUE = new Paint(DyeColor.LIGHT_BLUE, DyeColor.WHITE, DyeColor.LIME, DyeColor.LIGHT_BLUE, true);
	static final Paint LIME_FIN = new Paint(DyeColor.YELLOW, DyeColor.YELLOW, DyeColor.LIME, DyeColor.YELLOW, false);
	static final Paint GREEN = new Paint(DyeColor.LIME, DyeColor.WHITE, DyeColor.LIME, DyeColor.WHITE, true);
	static final Paint PINK = new Paint(DyeColor.PINK, DyeColor.WHITE, DyeColor.MAGENTA, DyeColor.WHITE, false);
	static final Paint CYAN = new Paint(DyeColor.CYAN, DyeColor.WHITE, DyeColor.ORANGE, DyeColor.WHITE, false);
	static final Paint ORANGE = new Paint(DyeColor.ORANGE, DyeColor.WHITE, DyeColor.ORANGE, DyeColor.RED, false);
	static final Paint PURPLE = new Paint(DyeColor.PURPLE, DyeColor.WHITE, DyeColor.PINK, DyeColor.WHITE, true);

	private final Paint paint;

	LifeguardTower(String id, double mapX, double mapY, double theta, Paint paint) {
		super(id, "Lifeguard Tower " + id, mapX, mapY, theta);
		this.paint = paint;
		claimLocalRect(-6, -7, 6, 9);
		view("front", -9, 3.5, -16, 0, 5, 0);
		view("photo", -6.5, 1.7, -7.5, 0, 4.5, 0);
		view("night", 8, 2.5, -14, 0, 4.5, 0);
		view("rear", 7, 4, 16, 0, 4, 0);
		seal();
	}

	/** Every tower: GTADB positions; theta follows the shoreline so each hut faces the sea. */
	public static void registerAll(ViceLandmarks reg) {
		double[][] ocean = {
			{2168.02, -66.97}, {2163.14, 22.60}, {2135.68, 171.41}, {2158.61, 345.86}, {2170.09, 476.85}, {2184.66, 605.85},
			{2199.47, 753.72}, {2219.19, 881.45}, {2232.60, 997.99}, {2205.27, 1355.32}, {2208.17, 1503.21}};
		String[] ids = {"L373", "L374", "L375", "L376", "L377", "L378", "L379", "L380", "L381", "L3344", "L3345"};
		Paint[] paints = {PINK, CYAN, ORANGE, PURPLE, GREEN, CYAN, YELLOW, PINK, BLUE, ORANGE, LIME_FIN};
		double shore = Math.atan2(1503.21 + 66.97, 2208.17 - 2168.02); // south to north along Ocean Beach
		for (int i = 0; i < ids.length; i++) reg.add(new LifeguardTower(ids[i], ocean[i][0], ocean[i][1], shore, paints[i]));
		double wash = Math.atan2(2842.80 - 2634.94, 1496.51 - 1660.09); // Washington Beach runs north-west
		reg.add(new LifeguardTower("L2262", 1660.09, 2634.94, wash, GREEN));
		reg.add(new LifeguardTower("L2263", 1496.51, 2842.80, wash, LIME_FIN));
	}

	@Override
	protected BlockState foundation() {
		return Blocks.SAND.defaultBlockState();
	}

	static BlockState conc(DyeColor c) {
		return Blocks.CONCRETE.pick(c).defaultBlockState();
	}

	static BlockState stair(Block b, Direction facing, boolean top) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slab(Block b, boolean top) {
		return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	@Override
	protected void build(Model m) {
		BlockState hut = conc(paint.hut()), roof = conc(paint.roof()), topper = conc(paint.topper()), stilt = conc(paint.stilts());
		BlockState white = Pal.white(), deck = Blocks.STRIPPED_BIRCH_WOOD.defaultBlockState();
		BlockState deckSlab = slab(Blocks.BIRCH_SLAB, true), rail = Blocks.BIRCH_FENCE.defaultBlockState();
		BlockState sand = Blocks.SAND.defaultBlockState();
		Block step = Blocks.BIRCH_STAIRS;
		// sand pad (levels the beach under the tower)
		for (int u = -6; u <= 6; u++)
			for (int v = -7; v <= 9; v++) m.set(u, -1, v, sand);
		// stilt frame: six posts down into the sand, X bracing on the sides and front
		int[] pu = {-3, 3}, pv = {-3, 0, 3};
		for (int u : pu)
			for (int v : pv) m.box(u, -1, v, u, 1, v, stilt);
		for (int u : pu) {
			m.line(u, 0, -3, u, 1.6, 0, 0, stilt);
			m.line(u, 1.6, -3, u, 0, 0, 0, stilt);
			m.line(u, 0, 0, u, 1.6, 3, 0, stilt);
			m.line(u, 1.6, 0, u, 0, 3, 0, stilt);
		}
		m.line(-3, 0, -3, 3, 1.6, -3, 0, stilt);
		m.line(-3, 1.6, -3, 3, 0, -3, 0, stilt);
		// deck at h 2 (top at 2.5 m): wraps the hut, wider at the sea front
		for (int u = -4; u <= 4; u++)
			for (int v = -4; v <= 4; v++) m.set(u, 2, v, deckSlab);
		for (int u = -4; u <= 4; u++) {
			m.set(u, 1, -4, white);
			m.set(u, 1, 4, white);
		}
		for (int v = -4; v <= 4; v++) {
			m.set(-4, 1, v, white);
			m.set(4, 1, v, white);
		}
		// railing round the deck, gap at the ramp (u -1..0 on the land side)
		for (int u = -4; u <= 4; u++) {
			m.set(u, 3, -4, rail);
			if (u > 0 || u < -1) m.set(u, 3, 4, rail);
		}
		for (int v = -4; v <= 4; v++) {
			m.set(-4, 3, v, rail);
			m.set(4, 3, v, rail);
		}
		// hut: u -2..2, v -2..2, walls h 3..5, glass band on the sea side and the flanks
		for (int u = -2; u <= 2; u++)
			for (int v = -2; v <= 2; v++) m.set(u, 2, v, deck);
		for (int h = 3; h <= 5; h++)
			for (int u = -2; u <= 2; u++)
				for (int v = -2; v <= 2; v++) {
					boolean edge = u == -2 || u == 2 || v == -2 || v == 2;
					if (!edge) {
						m.set(u, h, v, Pal.AIR);
						continue;
					}
					boolean corner = (u == -2 || u == 2) && (v == -2 || v == 2);
					boolean glassBand = h == 4 && !corner && v != 2;
					m.set(u, h, v, glassBand ? Pal.clearGlass() : hut);
				}
		m.set(0, 3, 2, Pal.AIR); // door to the ramp side
		m.set(0, 4, 2, Pal.AIR);
		m.set(-1, 3, 2, Pal.AIR);
		m.set(-1, 4, 2, Pal.AIR);
		m.set(0, 5, 0, Pal.light(12));
		// lamps on the four deck corner posts and one under the sea-side eave
		for (int u : new int[] {-4, 4})
			for (int v : new int[] {-4, 4}) m.set(u, 3, v, Pal.lamp());
		m.set(0, 5, -3, Pal.lamp());
		// red rescue sign on the sea face
		m.set(2, 5, -3, conc(DyeColor.RED));
		m.set(1, 5, -3, conc(DyeColor.RED));
		// deep overhanging hip roof: eave h 6 over u -4..4 v -4..4, then a hip step and a ridge
		for (int u = -4; u <= 4; u++)
			for (int v = -4; v <= 4; v++) {
				boolean eave = u == -4 || u == 4 || v == -4 || v == 4;
				m.set(u, 6, v, eave ? slab(Blocks.SMOOTH_QUARTZ_SLAB, false) : roof);
			}
		for (int u = -3; u <= 3; u++)
			for (int v = -3; v <= 3; v++) m.set(u, 7, v, (u == -3 || u == 3 || v == -3 || v == 3) ? slab(Blocks.SMOOTH_QUARTZ_SLAB, false) : roof);
		for (int u = -1; u <= 1; u++)
			for (int v = -2; v <= 2; v++) m.set(u, 8, v, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
		if (paint.box()) {
			// L381: a bright box on the ridge
			m.box(-1, 8, -1, 1, 9, 1, topper);
		} else {
			// L379: quarter-round sail fin, tall at the sea end, sweeping down to the land side
			for (int v = -3; v <= 3; v++) {
				double t = (v + 3) / 6.5;
				int top = 8 + (int) Math.round(2.8 * Math.sqrt(Math.max(0, 1 - t * t)));
				for (int h = 8; h <= top; h++) m.set(0, h, v, topper);
			}
		}
		// ramp from the deck down to the sand on the land side (+v)
		for (int i = 0; i < 4; i++) {
			int v = 5 + i, h = 2 - i;
			for (int u = -1; u <= 0; u++) {
				m.set(u, h, v, stair(step, Direction.SOUTH, false));
				for (int k = h - 1; k >= 0; k--) m.set(u, k, v, stilt);
			}
			m.set(-2, h + 1, v, rail);
			m.set(1, h + 1, v, rail);
			m.set(-2, h, v, white);
			m.set(1, h, v, white);
		}
	}
}
