package dev.leonida.vicecity.site;

import dev.leonida.vicecity.gen.Model;
import dev.leonida.vicecity.gen.Pal;
import dev.leonida.vicecity.gen.Site;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Beach Patrol Headquarters (GTADB L37, real Miami Beach Patrol HQ at 10th Street and Ocean Drive). A white
 * Streamline Moderne building styled like a ship: rounded ends, rows of portholes, a navy speed band, stacked decks
 * with white railings and a small glazed bridge with a mast on top. It stands on the beach and faces the sea (-v).
 */
public final class BeachPatrolHQ extends Site {
	public BeachPatrolHQ() {
		super("L37", "Beach Patrol Headquarters", 2052, 604, Math.atan2(1503.21 + 66.97, 2208.17 - 2168.02));
		claimFootprints();
		claimLocalRect(-11, -8, 11, 8);
		view("front", -14, 3, -28, 0, 6, 0);
		view("photo", 10, 2, -24, 0, 5, 0);
		view("night", -10, 4, -22, 0, 6, 0);
		view("rear", 12, 6, 24, 0, 6, 0);
		seal();
	}

	/** Plan with rounded ends: half-length a, half-depth b (ends are half discs of radius b). */
	static boolean plan(int u, int v, int a, int b) {
		double cu = u + 0.5, cv = v + 0.5;
		if (Math.abs(cv) > b + 0.01) return false;
		double core = a - b;
		if (Math.abs(cu) <= core) return true;
		double du = Math.abs(cu) - core;
		return du * du + cv * cv <= (b + 0.3) * (b + 0.3);
	}

	static boolean rim(int u, int v, int a, int b) {
		return plan(u, v, a, b) && (!plan(u + 1, v, a, b) || !plan(u - 1, v, a, b) || !plan(u, v + 1, a, b) || !plan(u, v - 1, a, b));
	}

	@Override
	protected void build(Model m) {
		BlockState white = Pal.white(), navy = Blocks.CONCRETE.pick(DyeColor.BLUE).defaultBlockState(), port = Pal.porthole();
		BlockState rail = Blocks.STAINED_GLASS_PANE.pick(DyeColor.WHITE).defaultBlockState(), floor = Pal.tile(), sand = Blocks.SAND.defaultBlockState();
		for (int u = -11; u <= 11; u++)
			for (int v = -8; v <= 8; v++) m.set(u, -1, v, Math.abs(u) <= 3 && v < -5 ? Pal.terrazzo() : sand);
		deck(m, 8, 5, 0, 3, white, navy, port, floor, true);
		roofDeck(m, 8, 5, 4, rail, white);
		deck(m, 6, 4, 5, 7, white, navy, port, floor, false);
		roofDeck(m, 6, 4, 8, rail, white);
		// glazed bridge on the top deck, navy visor, mast
		for (int u = -2; u <= 2; u++)
			for (int v = -2; v <= 1; v++)
				for (int h = 9; h <= 10; h++) {
					boolean edge = u == -2 || u == 2 || v == -2 || v == 1;
					m.set(u, h, v, edge ? (h == 10 && v != -2 ? white : Pal.clearGlass()) : Pal.AIR);
				}
		m.set(0, 10, 0, Pal.light(12));
		m.box(-3, 11, -3, 3, 11, 2, navy);
		m.box(0, 12, 0, 0, 16, 0, Blocks.IRON_BARS.defaultBlockState());
		m.set(0, 16, -1, Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState());
		m.set(0, 17, 0, Pal.lamp()); // mast light
		// deck lamps at the rounded ends and floods on the sand facing the building
		for (int u : new int[] {-8, 8}) {
			m.set(u, 5, 0, Pal.lamp());
			m.set(u / 4 * 3, 9, 0, Pal.lamp());
		}
		for (int u = -8; u <= 8; u += 4) {
			m.set(u, 0, -7, Pal.lamp());
			m.set(u, 1, -7, Pal.light(15));
		}
		// entrance stair from the boardwalk side to the first deck (+v side, rising to the west)
		for (int i = 0; i < 4; i++) m.set(4 - i, i, 6, Pal.whiteStairs(Direction.WEST, false));
	}

	/** One storey with a rounded plan: walls h0..h1, porthole row, navy band on the top course, door on the sea side. */
	static void deck(Model m, int a, int b, int h0, int h1, BlockState white, BlockState navy, BlockState port, BlockState floor, boolean door) {
		for (int u = -a - 1; u <= a + 1; u++)
			for (int v = -b - 1; v <= b + 1; v++) {
				if (!plan(u, v, a, b)) continue;
				m.set(u, h0, v, floor);
				boolean r = rim(u, v, a, b);
				for (int h = h0 + 1; h <= h1; h++) {
					if (!r) {
						m.set(u, h, v, Pal.AIR);
						continue;
					}
					BlockState s = white;
					if (h == h1) s = navy;
					else if (h == h0 + 1 + (h1 - h0 > 3 ? 1 : 0) && ((u & 1) == 0 || Math.abs(v) < b - 1) && (u % 3 == 0)) s = port;
					if (door && v < 0 && Math.abs(u) <= 1 && h <= h0 + 2) s = Pal.shopfront();
					m.set(u, h, v, s);
				}
				if (!r && ((u * 7 + v * 3) % 11 == 0)) m.set(u, h1, v, Pal.light(11));
			}
	}

	/** Flat deck over a storey with a white railing round its rim. */
	static void roofDeck(Model m, int a, int b, int h, BlockState rail, BlockState white) {
		for (int u = -a - 1; u <= a + 1; u++)
			for (int v = -b - 1; v <= b + 1; v++) {
				if (!plan(u, v, a, b)) continue;
				m.set(u, h, v, rim(u, v, a, b) ? white : Pal.tile());
				if (rim(u, v, a, b)) m.set(u, h + 1, v, rail);
			}
	}
}
