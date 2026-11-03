package dev.leonida.vicecity.site;

import dev.leonida.look.LookBlocks.Neon;
import dev.leonida.vicecity.gen.Model;
import dev.leonida.vicecity.gen.Pal;
import dev.leonida.vicecity.gen.Site;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Observation wheel on the Bayside waterfront (GTADB L438, real Skyviews Miami at Bayside Marketplace). About 61 m
 * tall: a 28 m radius double rim on a hub 33 m up, carried by two A-frame leg pairs, 32 glass gondolas, a boarding
 * pavilion at the foot. The trailer night shot (L438) shows a white neon rim and alternating red and white neon spokes
 * seen from the bay, so the wheel plane runs along u and its lit face (-v) looks east over Biscayne Bay.
 */
public final class SkyviewsWheel extends Site {
	static final int HUB = 33, R = 28, SPOKES = 16, GONDOLAS = 32;

	public SkyviewsWheel() {
		super("L438", "Skyviews Observation Wheel", 33, 137, Math.PI / 2);
		claimFootprints();
		claimLocalRect(-33, -12, 33, 12);
		view("front", -20, 8, -95, 0, 30, 0);
		view("photo", 10, 3, -140, 0, 30, 0);
		view("night", 15, 3, -120, 0, 32, 0);
		view("base", -14, 3, -24, 0, 12, 0);
		seal();
	}

	@Override
	protected void build(Model m) {
		BlockState white = Pal.white(), steel = Pal.frameSilver(), conc = Pal.concrete();
		BlockState pave = Pal.terrazzo(), glass = Pal.clearGlass(), cap = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
		// plaza
		for (int u = -33; u <= 33; u++)
			for (int v = -12; v <= 12; v++) m.set(u, -1, v, ((u ^ v) & 7) == 0 ? pave : Pal.sidewalk(u, v));
		// double rim: structure at v = -1 and v = 1, white neon facing the bay on the front rim, lacing between rims
		for (int i = 0; i < 720; i++) {
			double a = i * Math.PI * 2 / 720;
			double cu = R * Math.cos(a), ch = HUB + R * Math.sin(a);
			int u = (int) Math.floor(cu), h = (int) Math.floor(ch);
			m.set(u, h, -1, white);
			m.set(u, h, 1, white);
			if (i % 6 == 0) m.set(u, h, 0, steel);
			boolean vertical = Math.abs(Math.cos(a)) > 0.7;
			m.set(u, h, -2, Pal.neon(Neon.WHITE, Direction.SOUTH, vertical));
		}
		// spokes from the hub to the rim on both faces; front spokes carry alternating red and white neon
		for (int k = 0; k < SPOKES; k++) {
			double a = k * Math.PI * 2 / SPOKES;
			double eu = (R - 0.5) * Math.cos(a), eh = HUB + 0.5 + (R - 0.5) * Math.sin(a);
			m.line(0.5, HUB + 0.5, -1.5, eu, eh, -0.5, 0, steel);
			m.line(0.5, HUB + 0.5, 2.5, eu, eh, 1.5, 0, steel);
			boolean vertical = Math.abs(Math.sin(a)) > 0.7;
			Neon n = (k & 1) == 0 ? Neon.PINK : Neon.WHITE;
			for (int s = 3; s < R - 1; s++) {
				int u = (int) Math.floor(0.5 + s * Math.cos(a)), h = (int) Math.floor(HUB + 0.5 + s * Math.sin(a));
				if (m.get(u, h, -1) == null) m.set(u, h, -1, steel);
				m.set(u, h, -2, Pal.neon(n, Direction.SOUTH, vertical));
			}
		}
		// hub drum and axle
		for (int u = -3; u <= 3; u++)
			for (int h = -3; h <= 3; h++)
				if (u * u + h * h <= 10) for (int v = -2; v <= 2; v++) m.set(u, HUB + h, v, v == -2 ? Pal.ledWhite() : white);
		m.box(0, HUB, -5, 0, HUB, 4, steel);
		// two A-frame leg pairs (front at v -5, back at v 4) from the hub down to footings at u +-15
		for (int v : new int[] {-5, 4}) {
			m.line(0.5, HUB, v + 0.5, -14.5, 0, v + 0.5, 1, white);
			m.line(0.5, HUB, v + 0.5, 15.5, 0, v + 0.5, 1, white);
			m.line(-7, HUB / 2.0, v + 0.5, 8, HUB / 2.0, v + 0.5, 0, steel); // tie beam
			m.box(-17, 0, v - 1, -13, 0, v + 1, conc);
			m.box(14, 0, v - 1, 18, 0, v + 1, conc);
		}
		// gondolas hang under the rim: glass capsule, white cap and floor
		for (int g = 0; g < GONDOLAS; g++) {
			double a = g * Math.PI * 2 / GONDOLAS + 0.1;
			int u = (int) Math.floor(0.5 + (R + 1.5) * Math.cos(a)), h = (int) Math.floor(HUB + 0.5 + (R + 1.5) * Math.sin(a));
			int top = h - 1, low = h - 3;
			if (low < 2) continue; // the boarding pavilion covers the lowest cars
			m.set(u, h, 0, steel);
			for (int du = -1; du <= 1; du++)
				for (int dv = -1; dv <= 1; dv++) {
					m.set(u + du, top, dv, cap);
					m.set(u + du, low, dv, cap);
					m.set(u + du, low + 1, dv, (du == 0 && dv == 0) ? Pal.light(10) : glass);
				}
		}
		// boarding pavilion under the wheel foot: white box with a glass front and a ticket canopy toward the bay
		m.box(-8, 0, -5, 8, 0, 5, Pal.tile());
		for (int u = -8; u <= 8; u++)
			for (int v = -5; v <= 5; v++)
				for (int h = 1; h <= 3; h++) {
					boolean edge = u == -8 || u == 8 || v == -5 || v == 5;
					if (!edge) continue;
					m.set(u, h, v, v == -5 && Math.abs(u) < 7 && h <= 2 ? Pal.shopfront() : white);
				}
		m.box(-9, 4, -7, 9, 4, 6, Pal.whiteSlab());
		for (int u = -6; u <= 6; u++) m.set(u, 3, -6, Pal.neon(Neon.PINK, Direction.SOUTH, false));
	}
}
