package dev.leonida.vicecity.gen;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Small reusable props for landmark sites (palms, lamps, stairs, interior lights). Model coordinates. */
public final class Props {
	private Props() {}

	/** Royal or coconut palm: grey trunk and a frond crown. */
	public static void palm(Model m, int u, int h, int v, int height, long seed) {
		BlockState log = Pal.palmLog(), leaf = Pal.hedge();
		int lean = (int) (seed & 3);
		int du = lean == 1 ? 1 : lean == 2 ? -1 : 0, dv = lean == 3 ? 1 : 0;
		int tu = u, tv = v;
		for (int i = 0; i < height; i++) {
			if (i == height * 2 / 3 && lean != 0) {
				tu += du;
				tv += dv;
			}
			m.set(tu, h + i, tv, log);
		}
		int top = h + height;
		m.set(tu, top, tv, leaf);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			int a = d.getStepX(), b = -d.getStepZ();
			for (int k = 1; k <= 3; k++) m.add(tu + a * k, top - (k == 3 ? 1 : 0), tv + b * k, leaf);
			m.add(tu + a + b, top, tv + b - a, leaf);
			m.add(tu + a * 2 + b * 2, top - 1, tv + b * 2 - a * 2, leaf);
		}
	}

	/** Straight staircase climbing in direction {@code dir} (model space) from floor h0 to floor h1, width w. Carves headroom. */
	public static void stairRun(Model m, int u, int v, Direction dir, int h0, int h1, int w, BlockState step, BlockState fill) {
		int du = dir.getStepX(), dv = -dir.getStepZ();
		int pu = -dv, pv = du; // perpendicular
		for (int k = 0; k < h1 - h0; k++) {
			for (int i = 0; i < w; i++) {
				int cu = u + du * k + pu * i, cv = v + dv * k + pv * i;
				for (int h = h0; h < h0 + k; h++) m.set(cu, h, cv, fill);
				m.set(cu, h0 + k, cv, step);
				for (int h = h0 + k + 1; h <= h0 + k + 3; h++) m.set(cu, h, cv, Pal.AIR);
			}
		}
	}

	/** Grid of invisible light blocks over a rectangle at height h, only into empty or air cells. */
	public static void lights(Model m, int u0, int v0, int u1, int v1, int h, int step, int level) {
		for (int u = u0; u <= u1; u += step)
			for (int v = v0; v <= v1; v += step) {
				BlockState b = m.get(u, h, v);
				if (b == null || b.isAir()) m.set(u, h, v, Pal.light(level));
			}
	}

	/**
	 * Floodlight poles on an elliptical ring (radii ru, rv) round the model origin: a steel pole, a glowing head and a
	 * light 15 cell beside it. A pole only goes where the column has paving and is clear above.
	 */
	public static void floodRing(Model m, double ru, double rv, int n, int height) {
		BlockState pole = net.minecraft.world.level.block.Blocks.IRON_BARS.defaultBlockState();
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			int u = (int) Math.floor(ru * Math.cos(a)), v = (int) Math.floor(rv * Math.sin(a));
			int base = m.get(u, 1, v) != null ? 2 : m.get(u, 0, v) != null ? 1 : m.get(u, -1, v) != null ? 0 : Integer.MIN_VALUE;
			if (base == Integer.MIN_VALUE) continue;
			boolean clear = true;
			for (int h = base; h <= base + height + 1; h++) {
				BlockState b = m.get(u, h, v);
				if (b != null && !b.isAir()) clear = false;
			}
			if (!clear) continue;
			for (int h = base; h < base + height; h++) m.set(u, h, v, pole);
			m.set(u, base + height, v, Pal.lamp());
			m.set(u, base + height + 1, v, Pal.light(15));
		}
	}

	/** Sign text on a vertical plane. The text runs along +a = (au, av) from (u, v), bottom row at h. */
	public static void sign(Model m, String text, int scale, int u, int h, int v, int au, int av, BlockState lit) {
		Font.draw(text, scale, (a, b) -> m.set(u + au * a, h + b, v + av * a, lit));
	}
}
