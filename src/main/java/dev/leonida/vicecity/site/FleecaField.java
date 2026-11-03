package dev.leonida.vicecity.site;

import dev.leonida.look.LookBlocks.GlassTint;
import dev.leonida.vicecity.gen.Bowl;
import dev.leonida.vicecity.gen.Font;
import dev.leonida.vicecity.gen.Model;
import dev.leonida.vicecity.gen.Pal;
import dev.leonida.vicecity.gen.Props;
import dev.leonida.vicecity.gen.Site;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fleeca Field (gtadb L196, real LoanDepot Park, Little Havana). Placement and parti from our pipeline's
 * authored-stadiums.js (elliptical glazed concourse drum wrapped by projecting white bands, white perimeter columns,
 * four super-columns with forked supports carrying two track beams, three vaulted roof panels with a raised middle
 * panel, tall outfield glazing, south entry with canopy and the name on the crown band). Sized to the GTADB footprint
 * and the real ballpark: 100 m foul lines, 115 m centre field, roof panels about 60 m above the field.
 *
 * Model frame: u east, v north. Home plate at the south (v = -38), the outfield and its glass wall face north.
 */
public final class FleecaField extends Site {
	static final double RX = 100, RY = 87;
	static final int HOME_V = -38;
	static final int BAND_TOP = 31, CROWN_TOP = 41, TRACK = 44;
	static final double TRACK_U = 72;

	public FleecaField() {
		super("L196", "Fleeca Field", -1588, 1047, 0);
		claimFootprints();
		claimLocalEllipse(RX + 8, RY + 8);
		claimLocalRect(-113, -99, 114, 100); // whole plaza square, so no generic lot lands on the podium edge
		view("front", -60, 30, -175, 0, 30, -80);
		view("photo", 30, 4, -125, -10, 26, -88);
		view("aerial", -170, 120, -150, 0, 20, 0);
		view("field", 0, 24, -70, 0, 4, 30);
		view("outfield", -40, 30, 70, 0, 6, -40);
		view("concourse", -60, 9, -52, -20, 9, -82);
		view("north", 40, 30, 190, 0, 30, 70);
		seal();
	}

	static boolean inEnv(double u, double v, double shrink) {
		double a = RX - shrink, b = RY - shrink;
		return (u * u) / (a * a) + (v * v) / (b * b) <= 1;
	}

	/** Outfield wall distance from home plate at angle phi off the centre line. */
	static double wallDist(double phi) {
		return 100 + 15 * Math.cos(2 * phi);
	}

	/** True inside fair territory (to the outfield wall). */
	static boolean fair(double u, double v) {
		double dv = v - HOME_V;
		if (dv < Math.abs(u)) return false;
		double r = Math.hypot(u, dv), phi = Math.atan2(u, dv);
		return r <= wallDist(phi);
	}

	/** Playing field: fair territory plus a foul strip along the lines and the backstop arc. */
	static boolean field(double u, double v) {
		if (fair(u, v)) return true;
		double dv = v - HOME_V, r = Math.hypot(u, dv);
		if (r <= 15) return true;
		// distance to the nearer foul line segment (home to pole)
		double len = 100 / Math.sqrt(2), au = Math.abs(u);
		double t = Math.max(0, Math.min(len * Math.sqrt(2), (au + dv) / Math.sqrt(2)));
		double pu = t / Math.sqrt(2), pv = t / Math.sqrt(2);
		double d = Math.hypot(au - pu, dv - pv);
		return d <= 13 && dv > -15 && t < 100 && dv <= au + 0.1 + 100;
	}

	static double wallV(double u) {
		return -RY * Math.sqrt(Math.max(0, 1 - u * u / (RX * RX)));
	}

	@Override
	protected void build(Model m) {
		BlockState conc = Pal.concrete(), terr = Pal.terrazzo(), white = Pal.white();
		Bowl bowl = new Bowl(-104, -92, 104, 92, FleecaField::field);

		// ---------------------------------------------------------------- plaza, podium, entry
		for (int u = -112; u <= 112; u++)
			for (int v = -98; v <= 98; v++) {
				double cu = u + 0.5, cv = v + 0.5;
				double[] w = toMap(cu, cv);
				boolean claimed = claims((int) Math.floor(w[0]), (int) Math.floor(-w[1]));
				if (inEnv(cu, cv, -5)) {
					m.set(u, 0, v, conc);
					m.set(u, 1, v, inEnv(cu, cv, 0) ? terr : Pal.sidewalk(u, v));
				} else if (claimed) m.set(u, -1, v, ((u ^ v) & 7) == 0 ? terr : Pal.sidewalk(u, v));
			}
		// south entry steps from the plaza onto the podium and a white canopy
		for (int u = -14; u <= 13; u++) {
			int ev = (int) Math.floor(wallV(u + 0.5)) - 6;
			m.set(u, 0, ev - 1, Pal.concreteStairs(Direction.NORTH));
			m.set(u, 0, ev, conc);
			m.set(u, 1, ev, Pal.concreteStairs(Direction.NORTH));
		}
		for (int u = -18; u <= 17; u++)
			for (int v = -95; v <= (int) Math.floor(wallV(u + 0.5)); v++) m.set(u, 9, v, Pal.whiteSlab());
		for (int u : new int[] {-17, 16}) for (int h = 2; h <= 8; h++) m.set(u, h, -95, Pal.frameSilver());
		long seed = 31;
		for (int i = 0; i < 28; i++) {
			double a = Math.PI + 0.12 + i * (Math.PI * 2 - 0.24) / 28;
			if (Math.abs(Math.cos(a)) < 0.2 && Math.sin(a) < 0) continue; // keep the entry axis open
			Props.palm(m, (int) Math.floor((RX + 9) * Math.cos(a)), 1, (int) Math.floor((RY + 9) * Math.sin(a)), 10 + (int) (seed++ % 4), seed);
		}

		// ---------------------------------------------------------------- facade drum, concourses and bowl
		for (int u = -100; u <= 100; u++)
			for (int v = -87; v <= 87; v++) {
				double cu = u + 0.5, cv = v + 0.5;
				if (!inEnv(cu, cv, 0)) continue;
				if (!inEnv(cu, cv, 1.6)) {
					facadeColumn(m, u, v, cu, cv);
					continue;
				}
				m.set(u, 1, v, terr);
				float s = bowl.s(u, v);
				boolean ring = !inEnv(cu, cv, 8);
				if (s <= 0) continue;
				if (ring) {
					for (int h = 7; h <= BAND_TOP; h += 6) m.set(u, h, v, terr);
					m.set(u, CROWN_TOP + 1, v, Pal.roofGravel());
					continue;
				}
				int r = (int) (s / 2);
				if (s < 24) {
					int t = 3 + r + r / 3;
					boolean aisle = aisle(cu, cv, 28);
					boolean vom = vomitory(cu, cv) && r >= 6 && r <= 9;
					int base = r >= 3 ? Math.max(2, t - 1) : 2;
					for (int h = base; h <= t; h++) m.set(u, h, v, conc);
					if (r >= 3) for (int h = 2; h < t - 1; h++) m.set(u, h, v, h == 7 && s >= 14 ? terr : Pal.AIR);
					boolean inner = s - 2 * r < 1;
					if (r == 0) {
						// padded outfield wall and the backstop
						for (int h = 2; h <= 3; h++) m.set(u, h, v, fair(cu, cv - 2) || fair(cu, cv + 2) ? Pal.padding() : conc);
						m.set(u, 4, v, cv < HOME_V ? Pal.netting() : Pal.ledGold());
						continue;
					}
					if (vom) {
						for (int h = 2; h <= t + 2; h++) m.set(u, h, v, Pal.AIR);
						continue;
					}
					if (aisle) {
						if (inner) m.set(u, t, v, Pal.concreteSlab());
						continue;
					}
					if (inner) m.set(u, t + 1, v, Pal.seat(seatColor(cu, cv, r), bowl.outward(u, v)));
				} else {
					for (int h = 7; h <= 13; h += 6) m.set(u, h, v, terr);
					if (s < 27) {
						// club level: glazed suites over the lower bowl
						for (int h = 14; h <= 18; h++) m.set(u, h, v, s < 25 ? (h == 14 || h == 18 ? Pal.frameDark() : Pal.shopfront()) : Pal.AIR);
						m.set(u, 19, v, conc);
						continue;
					}
					int q = (int) ((s - 27) / 2);
					int t = Math.min(BAND_TOP + 6, 21 + q + q / 2);
					for (int h = Math.max(20, t - 1); h <= t; h++) m.set(u, h, v, conc);
					m.set(u, 19, v, terr);
					boolean inner = (s - 27) - 2 * q < 1;
					if (aisle(cu, cv, 36)) {
						if (inner) m.set(u, t, v, Pal.concreteSlab());
					} else if (inner && t < BAND_TOP + 6) m.set(u, t + 1, v, Pal.seat(Pal.Seat.NAVY, bowl.outward(u, v)));
					if (q == 0 && s < 27.9) m.set(u, t + 1, v, Pal.railing(bowl.outward(u, v).getOpposite()));
				}
			}
		// stair cores between the concourse floors
		for (int sx : new int[] {-1, 1}) {
			Props.stairRun(m, sx * 62 - 1, -60, sx > 0 ? Direction.EAST : Direction.WEST, 1, 7, 3, terr, conc);
			Props.stairRun(m, sx * 62 - 1, -55, sx > 0 ? Direction.WEST : Direction.EAST, 7, 13, 3, terr, conc);
		}

		// ---------------------------------------------------------------- the field
		for (int u = -90; u <= 90; u++)
			for (int v = -60; v <= 80; v++) {
				double cu = u + 0.5, cv = v + 0.5;
				if (!field(cu, cv) || !inEnv(cu, cv, 8)) continue;
				double dv = cv - HOME_V, r = Math.hypot(cu, dv);
				BlockState b = ((int) Math.floor((cv + 200) / 6)) % 2 == 0 ? Pal.grass() : Pal.grassDark();
				boolean isFair = fair(cu, cv);
				if (isFair && r > wallDist(Math.atan2(cu, dv)) - 5) b = Pal.warningTrack();
				if (!isFair && r > 15) b = Pal.warningTrack();
				// infield dirt: arc around the mound and base paths
				double mr = Math.hypot(cu, cv - (HOME_V + 18.4));
				double dia = Math.abs(cu) + Math.abs(cv - (HOME_V + 19.4));
				if (isFair && mr <= 29 && dv > 0) b = Pal.infield();
				if (dia <= 16.5) b = Pal.grass();
				if (Math.abs(dia - 19.4) <= 1.2 && dv > -1 && dv < 40) b = Pal.infield();
				if (r <= 4.2) b = Pal.infield();
				if (mr <= 2.8) b = Pal.infield();
				m.set(u, 1, v, b);
				for (int h = 2; h <= 3; h++) m.set(u, h, v, Pal.AIR);
			}
		// foul lines, bases, plate, mound
		for (int k = 0; k <= 100; k++) {
			for (int sgn : new int[] {-1, 1}) {
				int u = (int) Math.floor(sgn * k / Math.sqrt(2) + (sgn < 0 ? 0 : 0)), v = (int) Math.floor(HOME_V + k / Math.sqrt(2));
				if (inEnv(u, v, 8) && m.get(u, 1, v) != null) m.set(u, 1, v, Pal.chalk());
			}
		}
		m.set(0, 1, HOME_V, Pal.quartz());
		m.set(14, 2, HOME_V + 14, Pal.whiteSlab());
		m.set(0, 2, HOME_V + 27, Pal.whiteSlab());
		m.set(-14, 2, HOME_V + 14, Pal.whiteSlab());
		m.set(0, 2, HOME_V + 18, Pal.infield());
		// dugouts along both lines
		for (int sgn : new int[] {-1, 1})
			for (int k = 0; k < 14; k++) {
				int u = sgn * (10 + k), v = HOME_V - 6 + k;
				for (int h = 0; h <= 1; h++) m.set(u, h, v, Pal.AIR);
				m.set(u, -1, v, conc);
				m.set(u, 2, v, Pal.whiteSlab());
			}
		// foul poles
		for (int sgn : new int[] {-1, 1}) {
			int u = (int) Math.floor(sgn * 70.2), v = (int) Math.floor(HOME_V + 70.2);
			for (int h = 2; h <= 30; h++) m.set(u, h, v, Pal.ledGold());
		}

		// ---------------------------------------------------------------- outfield glass wall and scoreboard
		for (int u = -6; u <= 52; u++) {
			double cu = u + 0.5;
			int gv = (int) Math.floor(-wallV(cu)) - 9;
			for (int h = 14; h <= CROWN_TOP; h++) {
				boolean frame = Math.floorMod(u + 6, 11) == 0 || h == 14 || (h - 14) % 9 == 0;
				m.set(u, h, gv, frame ? white : Pal.glass(GlassTint.TEAL));
				for (int v = gv + 1; v <= gv + 7; v++) if (h > 14) m.set(u, h, v, Pal.AIR);
			}
			for (int v = gv; v <= gv + 8; v++) m.set(u, 13, v, terr);
		}
		for (int u = -58; u <= -14; u++) {
			double cu = u + 0.5;
			int sv = (int) Math.floor(-wallV(cu)) - 11;
			for (int h = 18; h <= 32; h++) {
				boolean edge = u == -58 || u == -14 || h == 18 || h == 32;
				m.set(u, h, sv, edge ? Pal.frameDark() : (h == 25 ? Pal.ledCyan() : (h > 27 ? Pal.ledWhite() : Pal.screen())));
				m.set(u, h, sv + 1, Pal.frameDark());
			}
		}

		// ---------------------------------------------------------------- crown band with the name
		String name = "FLEECA FIELD";
		int w = Font.width(name, 1);
		int su = -w / 2;
		Font.draw(name, 1, (a, b) -> {
			int u = su + a;
			m.set(u, 33 + b, (int) Math.floor(wallV(u + 0.5)) - 1, Pal.ledWhite());
		});

		// ---------------------------------------------------------------- roof: super-columns, tracks, three vaults
		for (int sx : new int[] {-1, 1}) {
			int tu = (int) Math.floor(sx * TRACK_U);
			for (int sy : new int[] {-1, 1}) {
				int cv = sy * 54;
				m.box(tu - 1, -2, cv - 2, tu + 1, TRACK - 12, cv + 1, white);
				for (int h = TRACK - 12; h < TRACK; h++) {
					int k = (h - (TRACK - 12)) / 3;
					m.set(tu - 1 - k, h, cv, white);
					m.set(tu + 1 + k, h, cv, white);
					m.set(tu, h, cv, white);
				}
			}
			for (int v = -92; v <= 92; v++) {
				m.box(tu - 1, TRACK, v, tu + 1, TRACK + 1, v, white);
				m.set(tu, TRACK + 2, v, Pal.frameSteel());
			}
			for (int v = -84; v <= 84; v += 14) m.box(tu - 1, TRACK + 2, v, tu + 1, TRACK + 2, v + 2, Pal.frameDark());
		}
		vault(m, -92, -31, false);
		vault(m, -30, 30, true);
		vault(m, 31, 92, false);
		// flat roof over the outer ring where the vaults do not reach
		for (int u = -100; u <= 100; u++)
			for (int v = -87; v <= 87; v++) {
				double cu = u + 0.5, cv = v + 0.5;
				if (!inEnv(cu, cv, 0)) continue;
				if (Math.abs(cu) > 78 || !inEnv(cu, cv, 9)) m.set(u, CROWN_TOP + 1, v, Pal.roofWhite());
			}
		for (int x : new int[] {-80, 80}) {
			m.box(x - 4, CROWN_TOP + 2, -6, x + 4, CROWN_TOP + 3, 6, Pal.metalSilver());
			for (int k = -4; k <= 4; k += 2) m.set(x + k, CROWN_TOP + 4, 0, Pal.acUnit());
		}

		// ---------------------------------------------------------------- lights
		Props.floodRing(m, 108, 95, 36, 10); // plaza floodlights
		Props.lights(m, -84, -72, 84, 72, 40, 9, 15);
		Props.lights(m, -60, -50, 60, 70, 12, 10, 15);
		for (int h : new int[] {4, 10, 16, 22, 28}) Props.lights(m, -98, -86, 98, 86, h, 7, 13);
		for (int i = 0; i < 40; i++) {
			double a = i * Math.PI * 2 / 40;
			m.set((int) Math.floor(66 * Math.cos(a)), 40, (int) Math.floor(58 * Math.sin(a)), Pal.lamp());
		}
	}

	/** Facade wall cell: five glazed concourse levels with projecting white bands, then the dark crown band. */
	void facadeColumn(Model m, int u, int v, double cu, double cv) {
		double a = Math.atan2(cv / RY, cu / RX);
		boolean column = Math.floorMod((int) Math.floor(a * 28 / Math.PI) , 2) == 0
			&& Math.abs(a * 28 / Math.PI - Math.floor(a * 28 / Math.PI) - 0.5) < 0.12;
		boolean mullion = Math.abs(a * 84 / Math.PI - Math.round(a * 84 / Math.PI)) < 0.12;
		for (int h = 2; h <= CROWN_TOP; h++) {
			BlockState b;
			if (h <= BAND_TOP) {
				int k = (h - 2) % 6;
				if (k == 5) b = Pal.white();
				else if (column) b = Pal.white();
				else if (mullion) b = Pal.frameSilver();
				else b = k == 0 ? Pal.spandrel(GlassTint.SLATE) : Pal.glassLit(GlassTint.TEAL);
			} else if (h == CROWN_TOP) b = Pal.white();
			else b = Pal.spandrel(GlassTint.SLATE);
			m.set(u, h, v, b);
		}
		// projecting bands and perimeter columns one to two blocks out
		Direction out = Math.abs(cu / RX) > Math.abs(cv / RY) ? (cu > 0 ? Direction.EAST : Direction.WEST) : (cv > 0 ? Direction.NORTH : Direction.SOUTH);
		int ou = out.getStepX(), ov = -out.getStepZ();
		for (int h = 7; h <= BAND_TOP; h += 6) {
			m.add(u + ou, h, v + ov, Pal.whiteSlab());
		}
		if (column) for (int h = 1; h <= BAND_TOP; h++) m.add(u + ou, h, v + ov, Pal.white());
		// entries at the four quarter points
		boolean entry = Math.abs(cu) < 9 || Math.abs(cv) < 6 && Math.abs(cu) > 80;
		if (entry) for (int h = 2; h <= 5; h++) m.set(u, h, v, h == 5 ? Pal.frameSilver() : Pal.clearGlass());
	}

	/** One vaulted roof panel spanning u between the tracks and beyond, covering v0..v1. */
	void vault(Model m, int v0, int v1, boolean upper) {
		double rise = upper ? 18 : 14, base = upper ? TRACK + 3 : TRACK + 1, half = 82;
		for (int u = -82; u <= 81; u++) {
			double cu = u + 0.5;
			int h = (int) Math.floor(base + rise * Math.sqrt(Math.max(0, 1 - (cu / half) * (cu / half))) - (Math.abs(cu) > TRACK_U ? 0 : 0));
			for (int v = v0; v <= v1; v++) {
				m.set(u, h, v, Pal.roofWhite());
				boolean rib = Math.floorMod(v - v0, 6) == 0;
				if (rib) m.set(u, h + 1, v, Pal.roofWhiteSlab());
				if (v == v0 || v == v1) {
					m.set(u, h + 1, v, Pal.frameSilver());
					// end glazing hanging below the vault edge
					for (int k = Math.max(CROWN_TOP + 2, h - 6); k < h; k++)
						m.set(u, k, v, Math.floorMod(u, 8) == 0 ? Pal.frameSilver() : Pal.glass(GlassTint.TEAL));
				}
			}
			// trusses under the vault every 10 m
			for (int v = v0 + 3; v <= v1 - 3; v += 10) {
				m.set(u, h - 1, v, Pal.truss());
				if (Math.floorMod(u, 4) == 0) m.set(u, h - 2, v, Pal.truss());
				m.set(u, h - 3, v, Pal.frameSteel());
			}
		}
	}

	static boolean aisle(double u, double v, int n) {
		double a = Math.atan2(v - HOME_V, u);
		double sector = Math.PI * 2 / n;
		double off = Math.abs(((a + sector / 2) % sector + sector) % sector - sector / 2);
		return off * Math.hypot(u, v - HOME_V) < 0.8;
	}

	static boolean vomitory(double u, double v) {
		double a = Math.atan2(v - HOME_V, u);
		double sector = Math.PI * 2 / 14;
		double off = Math.abs(((a + sector / 4) % sector + sector) % sector - sector / 2);
		return off * Math.hypot(u, v - HOME_V) < 1.6;
	}

	static Pal.Seat seatColor(double u, double v, int r) {
		return (r % 9 == 8) ? Pal.Seat.TEAL : Pal.Seat.NAVY;
	}
}
