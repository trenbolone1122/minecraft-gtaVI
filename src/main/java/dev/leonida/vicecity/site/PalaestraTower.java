package dev.leonida.vicecity.site;

import dev.leonida.look.LookBlocks.GlassTint;
import dev.leonida.look.LookBlocks.Neon;
import dev.leonida.look.LookBlocks.Pastel;
import dev.leonida.vicecity.gen.Model;
import dev.leonida.vicecity.gen.Pal;
import dev.leonida.vicecity.gen.Site;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Palaestra Tower (GTADB L437, real Sears / Carnival Tower, 1300 Biscayne Blvd). A 1929 Art Deco tower: a four-storey
 * cream stucco base with a fluted corner, and an octagonal shaft rising to about 40 m, crowned by a lit cornice. The
 * trailer night shot (L437) shows the shaft glowing pale cyan between white pilasters, lamps on the crown, and a big
 * painted mural on the base wall that faces the I-395 ramps (west).
 */
public final class PalaestraTower extends Site {
	static final int BASE_TOP = 17, SHAFT_TOP = 37;

	public PalaestraTower() {
		super("L437", "Palaestra Tower", -452.23, 677.99, 0);
		claimFootprints();
		claimLocalRect(-12, -10, 11, 10);
		view("front", -48, 14, -40, 0, 22, 0);
		view("photo", -55, 34, 12, 0, 20, 0);
		view("night", -45, 30, -20, 0, 24, 0);
		view("street", 10, 2, -30, 0, 14, 0);
		seal();
	}

	/** Octagon cell test: 13 x 13 square with 4-block chamfers, centred on (cu, cv). */
	static boolean oct(int u, int v) {
		return Math.abs(u) <= 6 && Math.abs(v) <= 6 && Math.abs(u) + Math.abs(v) <= 9;
	}

	@Override
	protected void build(Model m) {
		BlockState cream = Pal.stucco(Pastel.CREAM), white = Pal.white(), win = Pal.decoWindow();
		BlockState lit = Pal.glassLit(GlassTint.TEAL), pave = Pal.terrazzo();
		// forecourt
		for (int u = -12; u <= 11; u++)
			for (int v = -10; v <= 10; v++) m.set(u, -1, v, ((u ^ v) & 7) == 0 ? pave : Pal.sidewalk(u, v));
		// base: u -9..9, v -8..8, four floors, ribbon of deco windows per floor, shopfront at street level
		for (int h = 0; h <= BASE_TOP; h++)
			for (int u = -9; u <= 9; u++)
				for (int v = -8; v <= 8; v++) {
					boolean edge = u == -9 || u == 9 || v == -8 || v == 8;
					if (!edge) {
						m.set(u, h, v, h == 0 || h % 4 == 0 ? Pal.concrete() : Pal.AIR);
						continue;
					}
					boolean corner = (u == -9 || u == 9) && (v == -8 || v == 8);
					int along = (u == -9 || u == 9) ? v : u;
					BlockState s = cream;
					if (!corner && h >= 1 && h <= 2 && Math.abs(along) < 7 && u != -9) s = Pal.shopfront();
					else if (!corner && h > 4 && h < BASE_TOP - 1 && h % 4 != 0 && (h % 4) != 1 && Math.abs(along) % 3 != 0 && u != -9) s = win;
					m.set(u, h, v, s);
				}
		// fluted south-east corner: vertical ribs climbing past the parapet
		for (int h = 0; h <= BASE_TOP + 3; h++)
			for (int k = 0; k < 3; k++) {
				m.set(9 - k * 2, h, -9, white);
				m.set(10, h, -8 + k * 2, white);
			}
		// parapet with a white coping
		for (int u = -9; u <= 9; u++) {
			m.set(u, BASE_TOP + 1, -8, white);
			m.set(u, BASE_TOP + 1, 8, white);
		}
		for (int v = -8; v <= 8; v++) {
			m.set(-9, BASE_TOP + 1, v, white);
			m.set(9, BASE_TOP + 1, v, white);
		}
		mural(m);
		// octagonal shaft centred on (2, 1): pilaster at every chamfer and every face centre, lit glass between
		int cu = 2, cv = 1;
		for (int h = BASE_TOP + 1; h <= SHAFT_TOP; h++)
			for (int u = -7; u <= 7; u++)
				for (int v = -7; v <= 7; v++) {
					if (!oct(u, v)) continue;
					boolean edge = !oct(u + 1, v) || !oct(u - 1, v) || !oct(u, v + 1) || !oct(u, v - 1);
					if (!edge) {
						m.set(cu + u, h, cv + v, h % 4 == 2 ? Pal.concrete() : Pal.AIR);
						continue;
					}
					boolean chamferCorner = Math.abs(u) + Math.abs(v) == 9 && (Math.abs(u) == 6 || Math.abs(v) == 6);
					boolean faceCentre = (u == 0 || v == 0);
					boolean pil = chamferCorner || faceCentre;
					m.set(cu + u, h, cv + v, pil ? white : (h % 4 == 1 ? cream : lit));
				}
		// crown: projecting cornice with lamps, crenellated parapet, small stepped lantern
		for (int u = -8; u <= 8; u++)
			for (int v = -8; v <= 8; v++) {
				boolean in = Math.abs(u) <= 7 && Math.abs(v) <= 7 && Math.abs(u) + Math.abs(v) <= 11;
				boolean inner = oct(u, v);
				if (!in) continue;
				m.set(cu + u, SHAFT_TOP + 1, cv + v, inner ? Pal.roofGravel() : Pal.whiteSlab());
				if (!inner && ((u + v) & 1) == 0) m.set(cu + u, SHAFT_TOP + 2, cv + v, Pal.lamp());
				if (inner && !oct(u + 1, v) | !oct(u - 1, v) | !oct(u, v + 1) | !oct(u, v - 1)) m.set(cu + u, SHAFT_TOP + 2, cv + v, white);
			}
		m.box(cu - 2, SHAFT_TOP + 2, cv - 2, cu + 2, SHAFT_TOP + 4, cv + 2, cream);
		m.box(cu - 1, SHAFT_TOP + 5, cv - 1, cu + 1, SHAFT_TOP + 5, cv + 1, white);
		// neon cyan tubes down the face-centre pilasters
		for (int h = BASE_TOP + 2; h <= SHAFT_TOP; h++) {
			m.set(cu, h, cv - 7, Pal.neon(Neon.CYAN, Direction.SOUTH, true));
			m.set(cu - 7, h, cv, Pal.neon(Neon.CYAN, Direction.WEST, true));
			m.set(cu + 7, h, cv, Pal.neon(Neon.CYAN, Direction.EAST, true));
			m.set(cu, h, cv + 7, Pal.neon(Neon.CYAN, Direction.NORTH, true));
		}
	}

	/** Painted mural on the west wall (u = -10): pink field, red frame, a sun, a palm and a figure in blue. */
	static void mural(Model m) {
		BlockState pink = conc(DyeColor.PINK), red = conc(DyeColor.RED), yellow = conc(DyeColor.YELLOW), blue = conc(DyeColor.BLUE);
		BlockState cyan = conc(DyeColor.CYAN), green = conc(DyeColor.GREEN), brown = conc(DyeColor.BROWN), white = conc(DyeColor.WHITE);
		for (int v = -7; v <= 7; v++)
			for (int h = 2; h <= 16; h++) {
				boolean frame = v == -7 || v == 7 || h == 2 || h == 16;
				BlockState s = frame ? red : pink;
				int dv = v - 3, dh = h - 12;
				if (!frame && dv * dv + dh * dh <= 5) s = yellow; // sun
				if (!frame && h >= 13 && h <= 14 && v >= -6 && v <= 6 && v % 2 == 0) s = white; // title bar
				if (!frame && v == -3 && h >= 4 && h <= 11) s = brown; // palm trunk
				if (!frame && h >= 10 && h <= 12 && Math.abs(v + 3) + Math.abs(h - 11) * 2 <= 4 && v != -3) s = green; // fronds
				if (!frame && v >= 1 && v <= 4 && h >= 3 && h <= 9 && Math.abs(v - 2.5) * 2 + Math.max(0, h - 8) <= 4) s = blue; // figure
				if (!frame && h == 3 && v <= 0) s = cyan; // sea
				m.set(-10, h, v, s);
			}
		for (int v = -6; v <= 6; v += 4) m.set(-11, 0, v, Pal.light(14));
	}

	static BlockState conc(DyeColor c) {
		return Blocks.CONCRETE.pick(c).defaultBlockState();
	}
}
