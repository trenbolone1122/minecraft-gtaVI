package dev.leonida.build;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Generic multi-storey building: structure, facade windows, roof, entrance, core and furnished floors. */
final class Shell {
	private Shell() {}

	enum Windows { PUNCHED, BAND, CURTAIN, SOLID }

	static final class Spec {
		int x0, z0, x1, z1;
		int floors = 1, h = 4, groundH = 4;
		/** Raise the whole building (stilt houses). */
		int lift = 0;
		Windows windows = Windows.PUNCHED;
		boolean storefront;
		boolean elevator;
		int minRoom = 3, maxRoom = 7;
		String[] groundBig = {"living"}, groundSmall = {};
		String[] upperBig = {"bedroom"}, upperSmall = {};
		String entranceDoor;
		boolean doubleDoor;
		int parapet = 1;
		boolean furnish = true;
		/** When false the ground floor is left empty for a custom interior. */
		boolean planGround = true;
		/** LOOK.md facade (styles 0..5); null = legacy facade. */
		Facade.Look look;

		Spec(Site s) { x0 = 0; z0 = 0; x1 = s.W - 1; z1 = s.D - 1; }

		int levelY(Site s, int f) { return s.baseY + lift + (f == 0 ? 0 : groundH + (f - 1) * h); }

		int height(int f) { return f == 0 ? groundH : h; }

		int roofY(Site s) { return levelY(s, floors) - 1; }
	}

	/** Built result for decorators: the plans per floor. */
	static final class Result {
		final List<Plan> plans = new ArrayList<>();
		Plan.Core core;
		int roofY;
		int doorX;
	}

	static Result build(Site s, Style st, Spec sp) {
		Result res = new Result();
		Facade.Look L = sp.look;
		if (L != null) { Facade.configure(s, L, sp); Facade.apply(L, st); }
		s.bounds(sp.x0, sp.z0, sp.x1, sp.z1);
		int roofY = sp.roofY(s);
		res.roofY = roofY;
		// structure
		int by = s.baseY + sp.lift;
		if (sp.lift == 0) s.fill(sp.x0, by - 3, sp.z0, sp.x1, by - 2, sp.z1, st.base);
		s.fill(sp.x0, by, sp.z0, sp.x1, roofY + 4, sp.z1, B.AIR);
		int doorX = (sp.x0 + sp.x1 + 1) / 2;
		res.doorX = doorX;
		for (int f = 0; f < sp.floors; f++) {
			int y0 = sp.levelY(s, f), fh = sp.height(f);
			for (int x = Math.max(sp.x0, s.cx0); x <= Math.min(sp.x1, s.cx1); x++) {
				for (int z = Math.max(sp.z0, s.cz0); z <= Math.min(sp.z1, s.cz1); z++) {
					if (!s.in(x, z)) continue;
					boolean e = s.edge(x, z);
					s.set(x, y0 - 1, z, e ? (f == 0 ? st.base : L != null ? Facade.slabEdge(s, L, x, z) : st.trim) : st.floor);
					if (!e) continue;
					for (int r = 0; r < fh - 1; r++) s.set(x, y0 + r, z, L != null ? Facade.block(s, L, sp, x, z, f, r, fh) : facade(s, st, sp, x, z, f, r, fh));
				}
			}
		}
		// roof
		for (int x = Math.max(sp.x0, s.cx0); x <= Math.min(sp.x1, s.cx1); x++) {
			for (int z = Math.max(sp.z0, s.cz0); z <= Math.min(sp.z1, s.cz1); z++) {
				if (!s.in(x, z)) continue;
				boolean e = s.edge(x, z);
				s.set(x, roofY, z, e ? (L != null ? Facade.roofEdge(s, L, x, z) : st.trim) : st.roof);
				if (e && L == null) for (int k = 1; k <= sp.parapet; k++) s.set(x, roofY + k, z, k == sp.parapet ? st.trim : st.wall);
			}
		}
		// entrance
		List<int[]> entrances = new ArrayList<>();
		int fz = frontZ(s, doorX, sp);
		if (fz >= 0) {
			String dn = sp.entranceDoor != null ? sp.entranceDoor : st.doorName;
			s.door(doorX, by, fz, dn, Direction.SOUTH, false);
			entrances.add(new int[] {doorX, fz});
			if (sp.doubleDoor && s.in(doorX - 1, fz)) { s.door(doorX - 1, by, fz, dn, Direction.SOUTH, true); entrances.add(new int[] {doorX - 1, fz}); }
			if (sp.groundH > 3) s.set(doorX, by + 2, fz, st.glass);
			// steps / landing outside are drawn by kind decorators
		}
		// core and floors
		Plan.Core core = Plan.core(s, sp.x0 + 1, sp.z0 + 1, sp.x1 - 1, sp.z1 - 1, sp.floors, sp.elevator);
		res.core = core;
		for (int f = 0; f < sp.floors; f++) {
			int y0 = sp.levelY(s, f), fh = sp.height(f);
			if (f == 0 && !sp.planGround) { res.plans.add(new Plan()); continue; }
			Plan p = Plan.layout(s, sp.x0 + 1, sp.z0 + 1, sp.x1 - 1, sp.z1 - 1, core, y0, fh, sp.minRoom, sp.maxRoom, st.inWall, st.doorName,
				f == 0 ? entrances : List.of());
			res.plans.add(p);
			if (sp.furnish) {
				Interiors.assign(p.rooms, f == 0 ? sp.groundBig : sp.upperBig, f == 0 ? sp.groundSmall : sp.upperSmall);
				for (Room r : p.rooms) Interiors.furnish(s, r, st);
			}
		}
		for (int f = 0; f < sp.floors; f++) {
			int y0 = sp.levelY(s, f);
			BlockState shaftWall = st.inWall;
			Plan.buildCore(s, core, f, sp.floors, y0, sp.height(f), shaftWall, B.of("polished_andesite"));
		}
		if (L != null) { Facade.nightPanels(s, L, sp); Facade.relief(s, L, sp, res); Facade.roof(s, L, sp, res); Facade.nightNeon(s, L, sp, res); }
		s.clearBounds();
		return res;
	}

	/** z of the front wall at column x (first cell inside from the street), or -1. */
	static int frontZ(Site s, int x, Spec sp) {
		for (int z = sp.z0; z <= Math.min(sp.z1, sp.z0 + 6); z++) if (s.in(x, z)) return s.edge(x, z) ? z : -1;
		return -1;
	}

	static BlockState facade(Site s, Style st, Spec sp, int x, int z, int f, int r, int fh) {
		if (s.corner(x, z)) return st.accent;
		Direction out = s.outward(x, z);
		int t = (out == Direction.NORTH || out == Direction.SOUTH) ? x : z;
		boolean front = out == Direction.NORTH;
		if (f == 0 && sp.storefront && front) {
			if (t % 5 == 0) return st.accent;
			return r == fh - 2 && fh > 4 ? st.accent : B.of("glass");
		}
		switch (sp.windows) {
			case CURTAIN -> { return t % 4 == 0 ? st.trim : st.glass; }
			case BAND -> {
				if (r == 0) return st.wall;
				return t % 4 == 0 ? st.accent : st.glass;
			}
			case SOLID -> { return r == fh - 2 && t % 3 == 1 ? st.glass : st.wall; }
			default -> {
				if (r >= 1 && r <= Math.max(1, fh - 2) && t % 3 != 0) return st.glass;
				return st.wall;
			}
		}
	}
}
