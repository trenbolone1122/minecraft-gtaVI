package dev.leonida.vicecity.site;

import dev.leonida.look.LookBlocks.GlassTint;
import dev.leonida.vicecity.gen.Bowl;
import dev.leonida.vicecity.gen.Model;
import dev.leonida.vicecity.gen.Pal;
import dev.leonida.vicecity.gen.Props;
import dev.leonida.vicecity.gen.Site;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sahara Arena (gtadb L187, real Kaseya Center, 601 Biscayne Blvd). Layout from our pipeline's authored-stadiums.js
 * (centre, west-facing angular metal frontage with diagonal light ribbons, curved three-level entry glazing, tall
 * glazed side bay, elliptical rear envelope, shallow dome), heights raised to the real arena's scale. Interior: lower
 * bowl hugging the court, suites ring, upper tier, two concourse levels, court with lines and baskets, centre-hung
 * scoreboard.
 *
 * Model frame (as the pipeline): u along the front, v into the building, front at v = -60 (model south), rotated
 * -90 degrees so the front faces west onto Biscayne Blvd.
 */
public final class SaharaArena extends Site {
	static final double RX = 59, RY = 57; // envelope
	static final int WALL_TOP = 34, ROOF = 35;
	// front frontage anchors (u, v, top h); pipeline heights 35.4..39.4 scaled to the real arena
	static final double[][] ANCHORS = {{-54, -44, 41}, {-18, -59, 44}, {23, -57, 45}, {54, -43, 42}};
	static final double RIB_K = (44 - 12) / (38.4 - 12);

	public SaharaArena() {
		super("L187", "Sahara Arena", -307, 431.5, -Math.PI / 2);
		claimFootprints();
		claimLocalEllipse(66, 64);
		claimLocalRect(-20, -70, 20, -55);
		// cameras: gamePhoto is a night aerial from the south-west across Biscayne Blvd
		view("front", -30, 25, -125, 0, 20, -50);
		view("photo", -70, 60, -125, 0, 18, -35);
		view("plaza", -10, 2, -82, 0, 18, -55);
		view("court", -40, 22, 0, 0, 2, 0);
		view("concourse", 30, 3, -36, 50, 3, 10);
		view("rear", 70, 30, 100, 0, 15, 0);
		seal();
	}

	static double frontCurve(double u) {
		return -60 + 0.0052 * u * u;
	}

	/** Frontage face v at (u, h): polyline through the anchors, leaning out 0.038 per metre above 10 m. */
	static double faceV(double u, double h) {
		int i = 0;
		while (i < ANCHORS.length - 2 && u > ANCHORS[i + 1][0]) i++;
		double[] a = ANCHORS[i], c = ANCHORS[i + 1];
		double t = (u - a[0]) / (c[0] - a[0]);
		return a[1] + (c[1] - a[1]) * t - 0.038 * (h - 10);
	}

	static double faceTop(double u) {
		int i = 0;
		while (i < ANCHORS.length - 2 && u > ANCHORS[i + 1][0]) i++;
		double[] a = ANCHORS[i], c = ANCHORS[i + 1];
		double t = Math.max(0, Math.min(1, (u - a[0]) / (c[0] - a[0])));
		return a[2] + (c[2] - a[2]) * t;
	}

	static boolean inEnv(double u, double v, double shrink) {
		double a = RX - shrink, b = RY - shrink;
		return (u * u) / (a * a) + (v * v) / (b * b) <= 1;
	}

	/** Lower bowl tread top for row r. */
	static int lowerTread(int r) {
		return 1 + r + r / 3;
	}

	static int upperTread(int q) {
		return 23 + q + q / 2;
	}

	@Override
	protected void build(Model m) {
		BlockState conc = Pal.concrete(), terr = Pal.terrazzo(), panel = Pal.panelLight(), panelDark = Pal.panelGrey();
		Bowl bowl = new Bowl(-64, -64, 64, 64, (u, v) -> {
			double du = Math.max(0, Math.abs(u) - 14), dv = Math.max(0, Math.abs(v) - 7);
			return Math.abs(u) <= 19 && Math.abs(v) <= 12 && du * du + dv * dv <= 25 + 1e-9 || Math.abs(u) <= 14 && Math.abs(v) <= 12
				|| Math.abs(u) <= 19 && Math.abs(v) <= 7;
		});

		// ---------------------------------------------------------------- plaza and podium
		for (int u = -72; u <= 72; u++)
			for (int v = -76; v <= 72; v++) {
				double cu = u + 0.5, cv = v + 0.5;
				double[] w = toMap(cu, cv);
				boolean claimed = claims((int) Math.floor(w[0]), (int) Math.floor(-w[1]));
				boolean pod = (cu * cu) / (62.4 * 62.4) + (cv * cv) / (60 * 60) <= 1;
				if (pod) {
					m.set(u, 0, v, conc);
					m.set(u, 1, v, terr);
				} else if (claimed) {
					m.set(u, -1, v, ((u ^ v) & 7) == 0 ? terr : Pal.sidewalk(u, v));
				}
			}
		// grand entry stair on the west front (u -15..15) from plaza (h -1) to podium top (h 1)
		for (int u = -16; u <= 16; u++) {
			m.set(u, 0, -64, Pal.concreteStairs(Direction.NORTH));
			m.set(u, 0, -63, conc);
			m.set(u, 1, -63, Pal.concreteStairs(Direction.NORTH));
			for (int v = -62; v <= -60; v++) {
				m.set(u, 0, v, conc);
				m.set(u, 1, v, terr);
			}
		}
		for (int v = -64; v <= -60; v++)
			for (int s : new int[] {-17, 17}) m.set(s, 2, v, Pal.frameSteel()); // handrail posts line
		// palms along the plaza edge and the boulevard
		long seed = 17;
		for (int u = -60; u <= 60; u += 10) {
			if (Math.abs(u) <= 20) continue;
			Props.palm(m, u, 0, -70, 9 + (int) (seed++ % 3), seed);
		}

		// ---------------------------------------------------------------- envelope, bowl and interior, per column
		for (int u = -60; u <= 60; u++)
			for (int v = -60; v <= 58; v++) {
				double cu = u + 0.5, cv = v + 0.5;
				if (!inEnv(cu, cv, 0)) continue;
				boolean wall = !inEnv(cu, cv, 1.6);
				boolean front = cv < faceV(cu, 12) + 0.5 || cv < frontCurve(cu) + 1;
				float s = bowl.s(u, v);
				if (wall) {
					if (front && cv < 0) continue; // front zone handled by the frontage
					// rear envelope: metal with fins every ~12 degrees, glazing on the south side bay
					double a = Math.atan2(cv / RY, cu / RX);
					boolean bay = cu > 0 && a > -0.71 && a < 0.29;
					for (int h = 2; h <= WALL_TOP; h++) {
						BlockState b;
						if (bay) b = (h - 2) % 4 == 0 ? Pal.spandrel(GlassTint.SLATE) : (h % 4 == 1 ? Pal.shopfront() : Pal.glassLit(GlassTint.TEAL));
						else b = (h == WALL_TOP || h == 2) ? panelDark : panel;
						m.set(u, h, v, b);
					}
					continue;
				}
				if (front && cv < 0) continue;
				// interior floor
				m.set(u, 1, v, s <= 0 ? Pal.courtApron() : terr);
				if (s <= 0) continue;
				int r = (int) (s / 2);
				if (s < 26) {
					// lower bowl
					int t = lowerTread(r);
					boolean aisle = aisle(cu, cv, 12);
					boolean vom = vomitory(cu, cv) && r <= 3;
					int base = r >= 4 ? Math.max(2, t - 1) : 2;
					for (int h = base; h <= t; h++) m.set(u, h, v, conc);
					if (r >= 4) for (int h = 2; h < t - 1; h++) m.set(u, h, v, h == 8 && s >= 16 ? terr : Pal.AIR);
					boolean inner = s - 2 * r < 1;
					if (vom) {
						for (int h = 2; h <= Math.max(4, t + 1); h++) m.set(u, h, v, Pal.AIR);
						continue;
					}
					if (aisle) {
						if (inner) m.set(u, t, v, Pal.concreteSlab());
						continue;
					}
					if (inner) m.set(u, t + 1, v, Pal.seat(seatColor(cu, cv), bowl.outward(u, v)));
					if (r == 12 && !inner) m.set(u, t + 1, v, Pal.railing(bowl.outward(u, v).getOpposite()));
				} else {
					// ring behind the lower bowl: ground concourse, mezzanine, suites, upper tier
					m.set(u, 8, v, terr);
					m.set(u, 17, v, terr);
					if (s < 28) {
						// cross aisle at the top of the lower bowl
						continue;
					}
					int q = (int) ((s - 28) / 2);
					int t = upperTread(q);
					boolean nearWall = !inEnv(cu, cv, 3.5);
					if (s < 29) for (int h = 18; h <= 20; h++) m.set(u, h, v, Pal.shopfront()); // suite glass front
					if (s < 35) {
						for (int h = 21; h <= t; h++) m.set(u, h, v, conc);
					} else {
						for (int h = Math.max(21, t - 1); h <= t; h++) m.set(u, h, v, conc);
					}
					if (nearWall) {
						m.set(u, t + 1, v, conc);
						continue;
					}
					boolean inner = (s - 28) - 2 * q < 1;
					if (aisle(cu, cv, 16)) {
						if (inner) m.set(u, t, v, Pal.concreteSlab());
					} else if (inner) m.set(u, t + 1, v, Pal.seat(Pal.Seat.BURGUNDY, bowl.outward(u, v)));
					if (q == 0 && s < 28.9) m.set(u, t + 1, v, Pal.railing(bowl.outward(u, v).getOpposite()));
				}
			}
		// stair cores between the concourse levels (four quadrants)
		for (int sx : new int[] {-1, 1}) {
			Props.stairRun(m, sx * 44, 22, Direction.NORTH, 1, 8, 3, terr, conc);
			Props.stairRun(m, sx * 44 - (sx > 0 ? 2 : 0), 32, Direction.SOUTH, 8, 17, 3, terr, conc);
		}

		// ---------------------------------------------------------------- court
		for (int u = -16; u <= 15; u++)
			for (int v = -9; v <= 8; v++) m.set(u, 1, v, ((u + 100) / 2 % 2 == 0) ? Pal.court() : Pal.courtDark());
		for (int u = -14; u <= 13; u++) {
			m.set(u, 2, -8, Pal.courtLine());
			m.set(u, 2, 7, Pal.courtLine());
		}
		for (int v = -8; v <= 7; v++) {
			m.set(-14, 2, v, Pal.courtLine());
			m.set(13, 2, v, Pal.courtLine());
			m.set(0, 2, v, Pal.courtLine());
		}
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2 / 24;
			m.set((int) Math.floor(0.5 + 2.2 * Math.cos(a)), 2, (int) Math.floor(2.2 * Math.sin(a)), Pal.courtLine());
		}
		for (int side : new int[] {-1, 1}) {
			int edge = side < 0 ? -14 : 13;
			for (int k = 1; k <= 5; k++)
				for (int v = -2; v <= 1; v++) m.set(edge - side * k, 2, v, Pal.courtPaint());
			for (int i = 0; i <= 20; i++) {
				double a = -Math.PI / 2 + i * Math.PI / 20;
				int au = (int) Math.floor(edge - side * 1.5 - side * 6.7 * Math.cos(a));
				int av = (int) Math.floor(6.7 * Math.sin(a));
				if (Math.abs(av) <= 6) m.set(au, 2, av, Pal.courtLine());
			}
			// basket stanchion, arm, backboard, rim
			int post = side < 0 ? -17 : 16;
			for (int h = 2; h <= 5; h++) m.set(post, h, 0, Pal.frameDark());
			m.set(post - side, 5, 0, Pal.frameDark());
			m.set(post - 2 * side, 5, 0, Pal.frameDark());
			for (int v = -1; v <= 0; v++)
				for (int h = 5; h <= 6; h++) m.set(edge + side, h, v, Pal.clearGlass());
			m.set(edge, 5, 0, Pal.chain());
		}

		// ---------------------------------------------------------------- front: curved entry glazing and lobby
		for (int u = -54; u <= 53; u++) {
			double cu = u + 0.5;
			int gv = (int) Math.floor(frontCurve(cu));
			int envV = (int) Math.ceil(-RY * Math.sqrt(Math.max(0, 1 - cu * cu / (RX * RX))));
			int fv = (int) Math.floor(faceV(cu, 12));
			int back = Math.max(envV + 2, fv + 2);
			// lobby floor and galleries
			for (int v = gv; v <= back + 1; v++) {
				m.set(u, 1, v, terr);
				for (int h = 2; h <= 11; h++) m.set(u, h, v, Pal.AIR);
				if (v >= gv + 1 && v <= gv + 4) {
					m.set(u, 5, v, Pal.whiteSlabTop());
					m.set(u, 8, v, Pal.whiteSlabTop());
				}
				m.set(u, 12, v, Pal.white()); // lobby ceiling
				m.set(u, ROOF, v, Pal.roofGravel()); // flat roof behind the frontage
			}
			// glazing: three levels with floor frames at 5, 8, 11 and mullions every 3 m
			for (int v = gv; v <= gv; v++)
				for (int h = 2; h <= 11; h++) {
					BlockState g;
					if (h == 5 || h == 8 || h == 11) g = Pal.frameSteel();
					else if (Math.floorMod(u + 54, 3) == 0) g = Pal.frameSilver();
					else g = (Math.floorMod(u + 54, 24) == 6) ? Pal.shopfront() : Pal.glassLit(GlassTint.TEAL);
					m.set(u, h, v, g);
				}
			// fill gaps where the glazing curve steps by more than one block
			double next = frontCurve(cu + 1);
			int nv = (int) Math.floor(next);
			for (int v = Math.min(gv, nv) + 1; v < Math.max(gv, nv); v++)
				for (int h = 2; h <= 11; h++) m.add(u, h, v, Pal.glassLit(GlassTint.TEAL));
		}
		// entrance doors (clear glass) at the centre of the curve
		for (int u = -6; u <= 5; u++)
			for (int h = 2; h <= 4; h++) m.set(u, h, (int) Math.floor(frontCurve(u + 0.5)), Pal.clearGlass());
		// lobby stairs up to the galleries, visible through the glass
		Props.stairRun(m, -30, (int) Math.floor(frontCurve(-30)) + 5, Direction.EAST, 1, 5, 2, terr, Pal.white());
		Props.stairRun(m, 24, (int) Math.floor(frontCurve(24)) + 5, Direction.WEST, 1, 5, 2, terr, Pal.white());

		// ---------------------------------------------------------------- the angular metal frontage
		for (int h = 12; h <= 45; h++) {
			for (int i = 0; i < ANCHORS.length - 1; i++) {
				double[] a = ANCHORS[i], c = ANCHORS[i + 1];
				if (h > Math.max(a[2], c[2]) + 0.5) continue;
				double ua = a[0], uc = c[0];
				// clip the segment to where the top is above h
				double ta = a[2], tc = c[2];
				if (ta < h) ua = a[0] + (c[0] - a[0]) * (h - ta) / (tc - ta);
				if (tc < h) uc = a[0] + (c[0] - a[0]) * (h - ta) / (tc - ta);
				m.hline(ua, faceV(ua, h), uc, faceV(uc, h), h, h, (h - 12) % 7 == 6 ? panelDark : panel);
			}
		}
		// parapet cap along the top edge and closing walls at both ends
		for (int u = -54; u <= 53; u++) {
			double cu = u + 0.5;
			int top = (int) Math.floor(faceTop(cu));
			m.set(u, top + 1, (int) Math.floor(faceV(cu, top)), Pal.frameSilver());
		}
		for (int end = 0; end < 2; end++) {
			double[] a = ANCHORS[end == 0 ? 0 : 3];
			double ev = -RY * Math.sqrt(Math.max(0, 1 - a[0] * a[0] / (RX * RX)));
			for (int h = 12; h <= (int) a[2]; h++) m.hline(a[0], faceV(a[0], h), a[0] * 1.02, ev + 2, h, h, panel);
		}
		// light ribbons (original branching layout, after the game night photo)
		double[][][] ribbons = {
			{{-51, 12}, {-43, 29}, {-38, 21}, {-31, 36}}, {{-46, 34}, {-24, 14}, {-3, 36}}, {{-32, 26}, {-16, 34}, {1, 25}, {8, 12}},
			{{4, 38}, {10, 28}, {27, 36}}, {{22, 12}, {34, 24}, {44, 36}}, {{12, 17}, {29, 29}, {48, 16}}, {{-48, 17}, {-39, 15}, {-30, 22}}};
		dev.leonida.look.LookBlocks.Led[] ribColor = {dev.leonida.look.LookBlocks.Led.GOLD, dev.leonida.look.LookBlocks.Led.WARM_WHITE, dev.leonida.look.LookBlocks.Led.CYAN, dev.leonida.look.LookBlocks.Led.WARM_WHITE, dev.leonida.look.LookBlocks.Led.GOLD, dev.leonida.look.LookBlocks.Led.CYAN, dev.leonida.look.LookBlocks.Led.GOLD};
		for (int k = 0; k < ribbons.length; k++) {
			double[][] p = ribbons[k];
			for (int i = 1; i < p.length; i++) {
				double u0 = p[i - 1][0], h0 = 12 + (p[i - 1][1] - 12) * RIB_K, u1 = p[i][0], h1 = 12 + (p[i][1] - 12) * RIB_K;
				int n = (int) Math.ceil(Math.max(Math.abs(u1 - u0), Math.abs(h1 - h0)) * 2);
				for (int j = 0; j <= n; j++) {
					double t = (double) j / n, uu = u0 + (u1 - u0) * t, hh = h0 + (h1 - h0) * t;
					int h = (int) Math.floor(hh);
					if (h < 12 || h > faceTop(uu)) continue;
					boolean vert = Math.abs(h1 - h0) > Math.abs(u1 - u0);
					m.set((int) Math.floor(uu), h, (int) Math.floor(faceV(uu, h)) - 1, Pal.ledRibbon(ribColor[k], Direction.SOUTH, vert));
				}
			}
		}
		// name sign above the entrance: warm-white letters on a dark band
		String name = "SAHARA ARENA";
		int w = dev.leonida.vicecity.gen.Font.width(name, 1);
		int su = -w / 2 - 2;
		for (int u = su - 1; u <= su + w; u++)
			for (int h = 13; h <= 21; h++) m.set(u, h, (int) Math.floor(faceV(u + 0.5, h)) - 1, Pal.frameDark());
		dev.leonida.vicecity.gen.Font.draw(name, 1, (a, b) -> {
			int u = su + a, h = 14 + b;
			m.set(u, h, (int) Math.floor(faceV(u + 0.5, h)) - 2, Pal.ledWhite());
		});

		// ---------------------------------------------------------------- roof
		for (int u = -59; u <= 59; u++)
			for (int v = -57; v <= 57; v++) {
				double cu = u + 0.5, cv = v + 0.5;
				if (!inEnv(cu, cv, 0)) continue;
				if (cv < faceV(cu, 30) + 1 && cv < 0) continue;
				double r2 = cu * cu / (RX * RX) + cv * cv / (RY * RY);
				int h = ROOF + (int) Math.floor(4.2 * (1 - r2));
				m.set(u, h, v, Pal.roof());
				m.set(u, h - 1, v, Pal.roof());
				for (int k = WALL_TOP + 1; k < h - 1; k++) if (!inEnv(cu, cv, 1.6)) m.set(u, k, v, Pal.panelGrey());
			}
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI * 2 / 16;
			for (double r = 0.05; r < 0.97; r += 0.01) {
				double cu = RX * r * Math.cos(a), cv = RY * r * Math.sin(a);
				int h = ROOF + (int) Math.floor(4.2 * (1 - r * r)) - 2;
				m.set((int) Math.floor(cu), h, (int) Math.floor(cv), Pal.truss());
				if (i % 2 == 0) m.set((int) Math.floor(cu), h + 3, (int) Math.floor(cv), Pal.frameSilver()); // standing seams
			}
		}
		// rooftop plant
		int[][] plant = {{-25, 18}, {-11, 23}, {6, 25}, {21, 18}};
		for (int[] p : plant) {
			int h = ROOF + 4;
			m.box(p[0] - 4, h, p[1] - 2, p[0] + 4, h + 2, p[1] + 2, Pal.metalSilver());
			for (int k = -4; k <= 4; k += 2) m.set(p[0] + k, h + 3, p[1], Pal.acUnit());
		}

		// ---------------------------------------------------------------- scoreboard and lights
		for (int u = -5; u <= 4; u++)
			for (int v = -4; v <= 3; v++)
				for (int h = 24; h <= 28; h++) {
					boolean shell = u == -5 || u == 4 || v == -4 || v == 3 || h == 24 || h == 28;
					if (!shell) continue;
					boolean face = h >= 25 && h <= 27 && ((v == -4 || v == 3) && u > -5 && u < 4 || (u == -5 || u == 4) && v > -4 && v < 3);
					m.set(u, h, v, face ? (h == 26 ? Pal.ledCyan() : Pal.screen()) : Pal.frameDark());
				}
		for (int[] p : new int[][] {{-4, -3}, {3, -3}, {-4, 2}, {3, 2}})
			for (int h = 29; h <= ROOF + 2; h++) m.set(p[0], h, p[1], Pal.chain());
		Props.floodRing(m, 71, 69, 28, 9); // plaza floodlights
		Props.lights(m, -48, -44, 48, 44, 31, 8, 15);
		Props.lights(m, -52, -50, 52, 50, 6, 7, 13);
		Props.lights(m, -52, -50, 52, 50, 14, 7, 13);
		Props.lights(m, -52, -50, 52, 50, 19, 7, 12);
		for (int u = -50; u <= 50; u += 12) Props.lights(m, u, -60, u, -40, 9, 6, 14);
		for (int u = -16; u <= 16; u += 8) for (int v = -10; v <= 10; v += 10) m.set(u, 30, v, Pal.light(15));

		// ---------------------------------------------------------------- rear service doors and side entries
		for (int x : new int[] {-12, 0, 12}) {
			int bv = (int) Math.floor(RY * Math.sqrt(1 - (x + 0.5) * (x + 0.5) / (RX * RX))) - 1;
			for (int u = x - 3; u <= x + 3; u++) {
				for (int h = 2; h <= 6; h++)
					for (int v = bv - 1; v <= bv + 1; v++) m.set(u, h, v, v == bv + 1 ? Pal.AIR : Pal.AIR);
				for (int h = 2; h <= 6; h++) m.set(u, h, bv + 1, dev.leonida.look.LookBlocks.ROLL_UP_DOOR.defaultBlockState());
				m.set(u, 7, bv + 2, Pal.whiteSlab());
			}
		}
		for (int side : new int[] {-1, 1}) {
			int eu = side < 0 ? -59 : 58;
			for (int v = -3; v <= 3; v++) {
				for (int h = 2; h <= 5; h++) m.set(eu, h, v, Pal.clearGlass());
				for (int d = 0; d <= 3; d++) m.set(eu + side * d, 7, v, Pal.whiteSlab());
			}
		}
	}

	static boolean aisle(double u, double v, int n) {
		double a = Math.atan2(v, u);
		double sector = Math.PI * 2 / n;
		double off = Math.abs(((a + sector / 2) % sector + sector) % sector - sector / 2);
		return off * Math.hypot(u, v) < 0.8;
	}

	static boolean vomitory(double u, double v) {
		double a = Math.atan2(v, u);
		double sector = Math.PI / 4;
		double off = Math.abs(((a) % sector + sector) % sector - sector / 2);
		return off * Math.hypot(u, v) < 1.6;
	}

	static Pal.Seat seatColor(double u, double v) {
		double a = Math.atan2(v, u);
		int sec = (int) Math.floor((a + Math.PI) / (Math.PI * 2 / 40));
		return sec % 5 == 0 ? Pal.Seat.BLACK : Pal.Seat.BURGUNDY;
	}
}
