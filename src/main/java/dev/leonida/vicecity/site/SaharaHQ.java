package dev.leonida.vicecity.site;

import dev.leonida.look.LookBlocks.GlassTint;
import dev.leonida.vicecity.gen.Font;
import dev.leonida.vicecity.gen.Model;
import dev.leonida.vicecity.gen.Pal;
import dev.leonida.vicecity.gen.Props;
import dev.leonida.vicecity.gen.Site;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sahara HQ (gtadb L232, real Brickell Arch, 1395 Brickell Ave). 148 m, 36 floors (4.1 m), a slab of banded
 * blue-green glass whose Brickell Ave face carries a concave parabolic arch, a dark fin ladder up the centre, a darker
 * glass end wing, and an 11-storey glazed atrium near the top on the bay side. Sits on a full-block three-storey podium
 * (GTADB footprint, 13 m).
 *
 * Model frame: u along the block (toward the south), v toward the bay (east); the front (-v) faces Brickell Ave.
 */
public final class SaharaHQ extends Site {
	static final int FLOOR = 4, FLOORS = 36, PODIUM = 13;
	static final int TOP = PODIUM + 0 + FLOORS * FLOOR - 9; // roof about 148 m above grade
	static final int U0 = -11, U1 = 51, V0 = -17, V1 = 13; // tower body
	static final double AU = 20; // arch axis

	public SaharaHQ() {
		super("L232", "Sahara HQ", -781.7, -1101.3, -1.88);
		claimFootprints();
		view("front", -40, 40, -140, 20, 60, 0);
		view("photo", -120, 30, -150, 20, 70, 0);
		view("street", 5, 3, -50, 20, 60, -17);
		view("lobby", 20, 3, -30, 20, 4, 0);
		view("bay", 60, 60, 140, 20, 100, 0);
		seal();
	}

	/** Half width of the arch at height h above the podium roof. */
	static double archHalf(double h) {
		double t = h / (double) (TOP - PODIUM);
		return t >= 1 ? 0 : 27 * Math.sqrt(1 - t);
	}

	/** Front face v at (u, h): flat outside the arch, concave (up to 2.6 m deep) inside it. */
	static int frontV(int u, int h) {
		double half = archHalf(h - PODIUM);
		double x = (u + 0.5 - AU) / Math.max(half, 0.01);
		if (Math.abs(x) >= 1) return V0;
		return V0 + (int) Math.round(2.6 * (1 - x * x));
	}

	@Override
	protected void build(Model m) {
		BlockState conc = Pal.concrete(), terr = Pal.terrazzo(), white = Pal.white();
		// ---------------------------------------------------------------- podium over the whole block
		for (int u = -62; u <= 62; u++)
			for (int v = -45; v <= 42; v++) {
				double[] w = toMap(u + 0.5, v + 0.5);
				if (!claims((int) Math.floor(w[0]), (int) Math.floor(-w[1]))) continue;
				boolean edge = false;
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					double[] n = toMap(u + 0.5 + d[0] * 2, v + 0.5 + d[1] * 2);
					if (!claims((int) Math.floor(n[0]), (int) Math.floor(-n[1]))) edge = true;
				}
				m.set(u, 0, v, terr);
				boolean tower = u >= U0 && u <= U1 && v >= V0 && v <= V1;
				if (edge) {
					m.set(u, 0, v, Pal.sidewalk(u, v));
					continue;
				}
				boolean rim = false;
				for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
					double[] n = toMap(u + 0.5 + d[0] * 3, v + 0.5 + d[1] * 3);
					if (!claims((int) Math.floor(n[0]), (int) Math.floor(-n[1]))) rim = true;
				}
				if (tower) continue;
				if (rim) {
					// podium facade: shopfront at grade, then two storeys of ribbon glass with white bands
					for (int h = 1; h <= PODIUM; h++) {
						BlockState b;
						if (h <= 4) b = h == 4 ? white : (Math.floorMod(u + v, 6) == 0 ? Pal.frameSilver() : Pal.shopfront());
						else if (h == PODIUM || h == 8) b = white;
						else b = Pal.glassLit(GlassTint.TEAL);
						m.set(u, h, v, b);
					}
					m.set(u, PODIUM + 1, v, Pal.parapet());
				} else {
					for (int h : new int[] {4, 8}) m.set(u, h, v, conc);
					m.set(u, PODIUM, v, Pal.roofGravel());
				}
			}
		// pool deck and planters on the podium roof (bay side)
		for (int u = 0; u <= 40; u++)
			for (int v = 18; v <= 32; v++) {
				BlockState cur = m.get(u, PODIUM, v);
				if (cur == null) continue;
				m.set(u, PODIUM, v, Pal.deck());
				if (u >= 8 && u <= 32 && v >= 22 && v <= 28) m.set(u, PODIUM, v, Pal.water());
			}
		for (int u = 2; u <= 38; u += 9) Props.palm(m, u, PODIUM + 1, 33, 7, u);

		// ---------------------------------------------------------------- tower shaft
		for (int h = 1; h <= TOP; h++) {
			boolean slab = (h - 1) % FLOOR == 0;
			int lvl = (h - 1) / FLOOR;
			for (int u = U0; u <= U1; u++) {
				int fv = h <= PODIUM ? V0 : frontV(u, h);
				for (int v = fv; v <= V1; v++) {
					boolean faceU = u == U0 || u == U1, faceBack = v == V1, faceFront = v == fv;
					boolean wing = u >= U1 - 9; // darker end wing
					if (!(faceU || faceBack || faceFront)) {
						// interior: floor plates, core
						if (slab) m.set(u, h, v, terr);
						else if (u >= 14 && u <= 26 && v >= -4 && v <= 4) m.set(u, h, v, conc);
						else m.set(u, h, v, Pal.AIR);
						continue;
					}
					BlockState b;
					boolean atrium = faceBack && lvl >= 25 && lvl <= 33 && u >= 2 && u <= 38;
					if (h <= 5 && faceFront && Math.abs(u + 0.5 - AU) < 9) b = (h == 5) ? Pal.frameSilver() : Pal.clearGlass(); // lobby doors
					else if (atrium) b = (u % 6 == 2) ? Pal.frameSilver() : Pal.clearGlass();
					else if (slab) b = wing ? Pal.spandrel(GlassTint.NAVY) : Pal.spandrel(GlassTint.SLATE);
					else if (wing) b = Pal.glass(GlassTint.NAVY);
					else b = Pal.glassLit(GlassTint.TEAL);
					// the dark fin ladder up the centre of the arch
					if (faceFront && u == (int) AU - 2 && h > PODIUM) b = slab ? Pal.frameDark() : Pal.glassDark(GlassTint.GRAPHITE);
					m.set(u, h, v, b);
				}
				// fill vertical steps of the concave face
				if (h > PODIUM) {
					int prev = frontV(u - 1, h);
					for (int v = Math.min(prev, fv); v < Math.max(prev, fv); v++) if (u > U0) m.add(u, h, v, Pal.glassLit(GlassTint.TEAL));
				}
			}
			// arch edge: a silver rib where the concave face meets the flat face
			if (h > PODIUM) {
				double half = archHalf(h - PODIUM);
				for (int sgn : new int[] {-1, 1}) {
					int eu = (int) Math.floor(AU + sgn * half);
					if (eu > U0 && eu < U1) m.set(eu, h, V0 - 1, Pal.frameSilver());
				}
			}
			if (faceFinRow(h)) m.set((int) AU - 2, h, frontV((int) AU - 2, h) - 1, Pal.frameDark());
		}
		// crown: parapet, screen wall and mast
		for (int u = U0; u <= U1; u++)
			for (int v = V0; v <= V1; v++) {
				m.set(u, TOP + 1, v, Pal.roofGravel());
				if (u == U0 || u == U1 || v == V0 || v == V1) for (int h = TOP + 1; h <= TOP + 4; h++) m.set(u, h, v, h == TOP + 4 ? Pal.parapet() : Pal.spandrel(GlassTint.SLATE));
			}
		m.box(10, TOP + 2, -6, 30, TOP + 6, 6, Pal.metalSilver());
		for (int h = TOP + 7; h <= TOP + 18; h++) m.set(20, h, 0, Pal.frameSteel());
		m.set(20, TOP + 19, 0, Pal.glowstone());
		// company name high on the front, left of the arch (white letters on the glass)
		String name = "SAHARA";
		int su = U0 + 3, sh = TOP - 14;
		Font.draw(name, 1, (a, b) -> m.set(su + a, sh + b, V0 - 1, Pal.ledWhite()));
		// entrance canopy and drop-off on the front
		for (int u = (int) AU - 12; u <= (int) AU + 12; u++)
			for (int v = V0 - 8; v < V0; v++) m.set(u, 6, v, Pal.whiteSlab());
		for (int u : new int[] {(int) AU - 12, (int) AU + 12}) for (int h = 1; h <= 5; h++) m.set(u, h, V0 - 8, Pal.frameSilver());
		for (int u = (int) AU - 20; u <= (int) AU + 20; u += 8) Props.palm(m, u, 1, V0 - 14, 9, u * 7L);
		// lobby: marble floor, reception desk
		for (int u = (int) AU - 10; u <= (int) AU + 10; u++)
			for (int v = V0 + 3; v <= V0 + 12; v++) m.set(u, 1, v, Pal.marble());
		for (int u = (int) AU - 4; u <= (int) AU + 4; u++) m.set(u, 2, V0 + 10, white);
		Props.lights(m, U0 + 2, V0 + 2, U1 - 2, V1 - 2, 3, 6, 13);
		for (int h = 3 + FLOOR; h < TOP; h += FLOOR) Props.lights(m, U0 + 3, V0 + 3, U1 - 3, V1 - 3, h, 9, 12);
	}

	static boolean faceFinRow(int h) {
		return h > PODIUM && (h - 1) % FLOOR == 0;
	}
}
