package dev.leonida.vicecity.site;

import dev.leonida.look.LookBlocks.Accent;
import dev.leonida.look.LookBlocks.Awning;
import dev.leonida.look.LookBlocks.Neon;
import dev.leonida.look.LookBlocks.Pastel;
import dev.leonida.vicecity.gen.Font;
import dev.leonida.vicecity.gen.Model;
import dev.leonida.vicecity.gen.Pal;
import dev.leonida.vicecity.gen.Props;
import dev.leonida.vicecity.gen.Site;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One Ocean Drive (Shore Dr) Art Deco hotel. Tripartite stucco facade facing the ocean: a central bay that steps
 * forward and rises above the parapet as a fin or tower, side bays with eyebrow ledges over every window, speed lines
 * at the corners, an open porch or cafe terrace at grade with awnings, neon tubes tracing the porch and the tower, and
 * the hotel name in neon (horizontal on the tower or stacked on a vertical blade). Ground floor lobby, floor plates and
 * lights inside.
 *
 * Model frame: u along the drive (north), front (-v) faces the ocean.
 */
public final class DecoHotel extends Site {
	public enum Sign { NONE, TOWER, BLADE, PORCH }

	public record Spec(String id, String name, String sign, double mapX, double mapY, int width, int depth, int floors,
		Pastel body, Pastel bay, Accent trim, Neon neon, Awning awning, Sign signType, int tower, boolean round,
		boolean portholes, boolean terrace) {}

	static final double ROW_THETA = Math.PI / 2 - Math.atan2(107, 674); // Ocean Drive runs about 9 degrees east of north

	final Spec s;
	final int hw, hd, top;

	public DecoHotel(Spec s) {
		super(s.id(), s.name(), s.mapX(), s.mapY(), ROW_THETA);
		this.s = s;
		hw = s.width() / 2;
		hd = s.depth() / 2;
		top = 5 + 3 * (s.floors() - 1);
		claimLocalRect(-hw, -hd - 9, s.width() - hw, s.depth() - hd);
		int dist = Math.max(30, top + 20);
		view("front", 0, 2 + top * 0.45, -hd - dist, 0, top * 0.55, 0);
		view("photo", -dist * 0.6, 3, -hd - dist * 0.8, 0, top * 0.6, -hd);
		view("night", dist * 0.5, 2, -hd - dist * 0.7, 0, top * 0.5, -hd);
		view("lobby", 0, 2.6, -hd + 1, 0, 2, hd);
		seal();
	}

	@Override
	protected void build(Model m) {
		int u0 = -hw, u1 = s.width() - hw - 1, v0 = -hd, v1 = s.depth() - hd - 1;
		int bay = Math.max(4, s.width() / 4); // central bay half width
		BlockState body = Pal.stucco(s.body()), bayB = Pal.stucco(s.bay()), white = Pal.trimWhite();
		Direction out = Direction.SOUTH; // model -v (the ocean side) after rotation
		// ---------------------------------------------------------------- lot: sidewalk, terrace
		for (int u = u0; u <= u1; u++)
			for (int v = v0 - 9; v <= v1; v++) m.set(u, 0, v, v < v0 - 6 ? Pal.sidewalk(u, v) : Pal.terrazzo());
		if (s.terrace()) {
			for (int u = u0; u <= u1; u++) m.set(u, 1, v0 - 6, (u & 3) == 0 ? white : Pal.parapetSlab());
			for (int u = u0 + 2; u <= u1 - 2; u += 4)
				for (int k : new int[] {-4, -2}) {
					m.set(u, 1, v0 + k, Pal.fence());
					m.set(u, 2, v0 + k, Pal.tableTop());
				}
		}
		// ---------------------------------------------------------------- shell
		for (int h = 1; h <= top; h++) {
			boolean slab = h == 5 || (h > 5 && (h - 5) % 3 == 0);
			for (int u = u0; u <= u1; u++)
				for (int v = v0 - 1; v <= v1; v++) {
					boolean central = Math.abs(u + 0.5) < bay;
					if (v == v0 - 1) {
						if (central) m.set(u, h, v, centralBay(u, h, bay, bayB, white));
						continue;
					}
					if (s.round() && (u == u0 || u == u1) && v == v0) continue; // chamfered corner
					boolean edge = u == u0 || u == u1 || v == v1 || (v == v0 && !central)
						|| (s.round() && v == v0 + 1 && (u == u0 || u == u1));
					if (!edge) {
						m.set(u, h, v, slab || h == 1 ? Pal.terrazzo() : Pal.AIR);
						continue;
					}
					m.set(u, h, v, wall(u, v, h, u0, u1, v0, v1, body, white));
				}
		}
		m.set(0, 1, v0, Pal.AIR);
		// ---------------------------------------------------------------- eyebrows, speed lines, porch
		for (int lvl = 0; lvl < s.floors(); lvl++) {
			int wh = lvl == 0 ? 4 : 5 + 3 * lvl - 0; // eyebrow just above each window head
			for (int u = u0 + 1; u <= u1 - 1; u++) {
				if (Math.abs(u + 0.5) < bay + 1) continue;
				if (lvl > 0 || !s.terrace()) m.set(u, wh, v0 - 1, Pal.eyebrow(s.trim(), out));
			}
		}
		for (int u : new int[] {u0 + 1, u1 - 1})
			for (int h = 6; h <= top; h += 3) m.add(u, h + 1, v0 - 1, Pal.speedLine(s.trim(), out));
		// porch: awning or deep canopy across the frontage, neon tube along its edge
		for (int u = u0; u <= u1; u++) {
			if (Math.abs(u + 0.5) < bay && s.signType() == Sign.PORCH) continue;
			m.set(u, 4, v0 - 2, s.terrace() ? Pal.awning(s.awning(), out) : Pal.trimWhiteSlab());
			m.set(u, 4, v0 - 3, Pal.neon(s.neon(), out, false));
		}
		// ---------------------------------------------------------------- central tower / fin above the roof
		for (int h = top + 1; h <= top + s.tower(); h++)
			for (int u = -bay + 1; u < bay - 1; u++) {
				boolean mid = Math.abs(u + 0.5) < 1.6;
				int w2 = bay - 1 - (h - top) / 3; // stepped top
				if (Math.abs(u + 0.5) > w2 && !mid) continue;
				for (int v = v0 - 1; v <= v0 + 2; v++) m.set(u, h, v, mid && h > top + s.tower() - 2 ? white : bayB);
			}
		if (s.tower() > 0) for (int h = 2; h <= top + s.tower(); h++) m.set(0, h, v0 - 2, h < 5 ? Pal.AIR : Pal.fin(s.trim(), out));
		// ---------------------------------------------------------------- roof
		for (int u = u0; u <= u1; u++)
			for (int v = v0; v <= v1; v++) {
				m.set(u, top + 1, v, (u == u0 || u == u1 || v == v0 || v == v1) ? Pal.parapet() : Pal.roofGravel());
			}
		for (int u = u0 + 2; u <= u1 - 2; u += 5) m.set(u, top + 2, v1 - 3, Pal.acUnit());
		m.box(u1 - 5, top + 2, v1 - 6, u1 - 2, top + 4, v1 - 3, body);
		// ---------------------------------------------------------------- the name in neon
		if (!s.sign().isEmpty()) {
			switch (s.signType()) {
				case TOWER -> {
					int w = Font.width(s.sign(), 1);
					if (w <= bay * 2 + 2 && s.tower() >= 8) neonText(m, s.sign(), -w / 2, top + s.tower() - 9, v0 - 2, out);
					else if (w <= s.width()) neonText(m, s.sign(), -w / 2, top - 8, v0 - 2, out);
					else for (int h = 5; h <= top + s.tower(); h++) { // name too wide for the facade: neon edges on the fin
						m.set(-1, h, v0 - 2, Pal.neon(s.neon(), out, true));
						m.set(1, h, v0 - 2, Pal.neon(s.neon(), out, true));
					}
				}
				case BLADE -> {
					int bu = u1 - 1, n = s.sign().replace(" ", "").length();
					int hTop = Math.min(top + s.tower(), 4 + n * 8 + 2);
					for (int h = 5; h <= hTop; h++) {
						for (int u = bu - 4; u <= bu + 0; u++) m.set(u, h, v0 - 1, Pal.black());
					}
					String t = s.sign().replace(" ", "");
					for (int i = 0; i < t.length(); i++) {
						int base = hTop - 8 * (i + 1);
						neonText(m, String.valueOf(t.charAt(i)), bu - 4, base, v0 - 2, out);
					}
				}
				case PORCH -> {
					int w = Font.width(s.sign(), 1);
					neonText(m, s.sign(), -w / 2, 5, v0 - 2, out);
				}
				default -> {}
			}
		}
		// ---------------------------------------------------------------- lobby and lights
		for (int u = -3; u <= 3; u++) m.set(u, 2, v1 - 4, white);
		Props.palm(m, u0 + 1, 1, v0 - 8, 7 + (s.floors() & 1), s.id().hashCode());
		Props.palm(m, u1 - 1, 1, v0 - 8, 8, s.id().hashCode() + 3L);
		Props.lights(m, u0 + 2, v0 + 2, u1 - 2, v1 - 2, 3, 6, 12);
		for (int h = 7; h < top; h += 3) Props.lights(m, u0 + 2, v0 + 2, u1 - 2, v1 - 2, h, 7, 11);
	}

	BlockState centralBay(int u, int h, int bay, BlockState bayB, BlockState white) {
		if (h <= 4) return Math.abs(u + 0.5) < 2 ? Pal.AIR : (h == 4 ? white : Pal.shopfront());
		boolean slab = (h - 5) % 3 == 0;
		if (Math.abs(u + 0.5) < 1.6) return slab ? white : Pal.clearGlass(); // glass block stair strip
		if (Math.abs(u + 0.5) > bay - 1) return bayB;
		if (s.portholes() && !slab && (h - 5) % 3 == 1 && Math.floorMod(u, 3) == 0) return Pal.porthole();
		return slab ? white : ((h - 5) % 3 == 2 ? bayB : Pal.decoWindow());
	}

	BlockState wall(int u, int v, int h, int u0, int u1, int v0, int v1, BlockState body, BlockState white) {
		boolean front = v == v0 || (s.round() && v == v0 + 1 && (u == u0 || u == u1));
		if (h <= 4) {
			if (front) return h == 4 ? white : (Math.floorMod(u, 4) == 0 ? white : Pal.shopfront());
			return h == 4 ? white : body;
		}
		int k = (h - 5) % 3;
		if (k == 0) return front ? white : body;
		// windows: pairs of 2 every 4 blocks on the front, single every 4 on the sides and back
		int along = front || v == v1 ? u : v;
		boolean win = front ? Math.floorMod(along, 4) != 0 && Math.floorMod(along, 4) != 1 : Math.floorMod(along, 4) == 2;
		if (front && s.round() && (u == u0 || u == u1)) win = true; // wrap-around corner glass
		return win && k == 1 || win && k == 2 && front ? Pal.decoWindow() : body;
	}

	void neonText(Model m, String text, int u, int h, int v, Direction out) {
		java.util.Set<Long> px = new java.util.HashSet<>();
		Font.draw(text, 1, (a, b) -> px.add(((long) a << 32) | (b & 0xffffffffL)));
		for (long p : px) {
			int a = (int) (p >> 32), b = (int) p;
			boolean vert = px.contains(((long) a << 32) | ((b + 1) & 0xffffffffL)) || px.contains(((long) a << 32) | ((b - 1) & 0xffffffffL));
			m.set(u + a, h + b, v, Pal.neon(s.neon(), out, vert));
		}
	}
}
