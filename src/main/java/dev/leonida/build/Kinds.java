package dev.leonida.build;

import dev.leonida.core.api.BuildingGenerator.Kind;
import dev.leonida.look.LookBlocks.Window;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Exterior and interior recipes per building kind. */
final class Kinds {
	private Kinds() {}

	static void build(Site s) {
		if (s.landmark != null && !s.landmark.isEmpty() && Landmarks.build(s)) return;
		Kind k = s.kind;
		boolean highRise = k == Kind.TOWER || k == Kind.OFFICE || k == Kind.APARTMENT || k == Kind.HOTEL;
		if (highRise && (s.landmark == null || s.landmark.isEmpty())) {
			// very large lots become a glazed podium (mall) instead of one giant slab
			if (s.W > 64 || s.D > 64) { mall(s); return; }
			if (s.axisAligned && Math.max(s.floors, s.heightM / 3) >= 10 && Tower.build(s)) return;
		}
		switch (k) {
			case HOUSE -> house(s);
			case APARTMENT -> apartment(s);
			case TOWER, LANDMARK -> tower(s);
			case SHOP -> shop(s);
			case GAS_STATION -> gasStation(s);
			case MOTEL -> motel(s, false);
			case CLUB -> club(s);
			case OFFICE -> office(s);
			case WAREHOUSE -> warehouse(s);
			case BANK -> bank(s);
			case HOTEL -> hotel(s);
			case MALL -> mall(s);
			case POLICE -> police(s);
			case HOSPITAL -> hospital(s);
			case MARINA -> marina(s);
		}
	}

	/** LOOK.md facade: the map's style when given, else the kind default. */
	static Facade.Look look(Site s, Shell.Spec sp, int def) {
		sp.look = Facade.look(s, s.style >= 0 ? s.style : def);
		return sp.look;
	}

	// ------------------------------------------------------------------ houses

	static void house(Site s) {
		Style st = Style.pastel(s);
		st.wall = B.c(s.pick(new String[] {"white", "pink", "light_blue", "yellow", "white", "lime", "orange"}), s.chance(0.5) ? "concrete" : "terracotta");
		st.trim = B.of("white_concrete");
		st.accent = B.of(s.pick(new String[] {"white_concrete", "smooth_quartz", "light_gray_concrete"}));
		st.glass = B.of("glass_pane");
		Deco.grass(s, s.baseY);
		int W = s.W, D = s.D;
		if (W < 9 || D < 9) {
			Shell.Spec sp = new Shell.Spec(s);
			sp.groundBig = new String[] {"studio"};
			sp.maxRoom = 20;
			look(s, sp, 3);
			Shell.build(s, st, sp);
			return;
		}
		int front = Math.min(5, Math.max(2, D / 5));
		boolean pool = D >= 22 && W >= 12;
		boolean drive = W >= 14;
		int back = pool ? Math.min(9, D / 3) : Math.max(2, D / 6);
		int hd = Math.min(16, D - front - back);
		int side = drive ? 5 : 1;
		boolean driveLeft = s.chance(0.5);
		int hx0 = driveLeft ? side : 1, hx1 = Math.min(W - 1 - (driveLeft ? 1 : side), hx0 + 17);
		int hz0 = front, hz1 = front + hd - 1;
		int floors = Math.min(2, s.floors);
		Shell.Spec sp = new Shell.Spec(s);
		sp.x0 = hx0; sp.z0 = hz0; sp.x1 = hx1; sp.z1 = hz1;
		sp.floors = floors;
		sp.minRoom = 3; sp.maxRoom = 7;
		sp.groundBig = floors == 1 ? new String[] {"living", "kitchen", "bedroom", "bedroom", "dining"} : new String[] {"living", "kitchen", "dining", "living"};
		sp.groundSmall = new String[] {"bath"};
		sp.upperBig = new String[] {"bedroom", "bedroom", "bedroom", "living"};
		sp.upperSmall = new String[] {"bath"};
		s.chance(0.45);
		look(s, sp, 3);
		Shell.Result res = Shell.build(s, st, sp);
		// front path and porch light
		int dx = res.doorX;
		for (int z = 0; z < hz0; z++) s.set(dx, s.baseY - 1, z, B.of("smooth_stone"));
		s.set(dx + 1, s.baseY + 2, hz0 - 1, B.face("lantern", Direction.DOWN));
		// driveway
		if (drive) {
			int x0 = driveLeft ? 1 : W - 4, x1 = x0 + 2;
			s.fill(x0, s.baseY - 1, 0, x1, s.baseY - 1, hz1, B.of("light_gray_concrete"));
		}
		// hedges along sides and back
		for (int z = hz0; z < D; z++) { s.set(0, s.baseY, z, B.leaves("oak_leaves")); s.set(W - 1, s.baseY, z, B.leaves("oak_leaves")); }
		for (int x = 0; x < W; x++) s.set(x, s.baseY, D - 1, B.leaves("oak_leaves"));
		// palms in front yard corners
		if (front >= 3) {
			Deco.palm(s, driveLeft ? W - 3 : 2, s.baseY, 1);
		}
		if (pool) {
			int px0 = hx0 + 1, px1 = Math.min(hx1 - 1, px0 + 7), pz0 = hz1 + 3, pz1 = Math.min(D - 3, pz0 + 3);
			if (pz1 > pz0) {
				Deco.pool(s, px0, pz0, px1, pz1, s.baseY);
				for (int x = px0; x <= px1; x += 2) s.set(x, s.baseY, pz0 - 2, B.slab("white_wool_slab", false));
				s.set(px1 + 1, s.baseY, pz1 + 1, B.of("oak_fence"));
				s.set(px1 + 1, s.baseY + 1, pz1 + 1, B.of("oak_fence"));
				s.set(px1 + 1, s.baseY + 2, pz1 + 1, B.of(s.pick(new String[] {"white_wool", "cyan_wool", "pink_wool", "yellow_wool"})));
				Deco.palm(s, px0 - 1 < 1 ? px1 + 2 : px0 - 1, s.baseY, pz1 + 1);
			}
		}
	}

	// ------------------------------------------------------------------ apartments / art deco

	static void apartment(Site s) {
		Style st = Style.pastel(s);
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = Math.max(2, Math.min(s.floors, 12));
		sp.windows = s.chance(0.5) ? Shell.Windows.BAND : Shell.Windows.PUNCHED;
		sp.elevator = sp.floors >= 4;
		sp.minRoom = 4; sp.maxRoom = 8;
		sp.groundBig = new String[] {"lobby", "studio", "studio", "studio"};
		sp.groundSmall = new String[] {"storage"};
		sp.upperBig = new String[] {"studio"};
		sp.upperSmall = new String[] {"bath"};
		sp.parapet = 2;
		sp.doubleDoor = true;
		sp.entranceDoor = "birch_door";
		look(s, sp, Math.max(s.floors, s.heightM / 3) <= 6 ? 2 : 1);
		Shell.build(s, st, sp);
	}

	/** Neon band above the ground floor, central fin above the roof line and a neon parapet. */
	static void artDecoCrown(Site s, Style st, Shell.Spec sp, Shell.Result r) {
		s.bounds(sp.x0, sp.z0, sp.x1, sp.z1);
		if (sp.floors > 1) Deco.neonFront(s, sp.x0 + 1, sp.x1 - 1, sp.levelY(s, 1) - 1, st.neon);
		Deco.neonRing(s, r.roofY + sp.parapet, st.neon, sp.x0, sp.z0, sp.x1, sp.z1);
		s.clearBounds();
		int fx = r.doorX;
		int fz = Shell.frontZ(s, fx, sp);
		if (fz >= 0) {
			for (int y = s.baseY + sp.groundH; y <= r.roofY + 4; y++) s.set(fx, y, fz, (y & 1) == 0 ? st.accent : st.neon);
			s.set(fx, r.roofY + 5, fz, B.of("end_rod"));
		}
	}

	// ------------------------------------------------------------------ towers

	static void tower(Site s) {
		Style st = new Style();
		Style.common(s, st);
		String g = s.pick(new String[] {"light_blue_stained_glass", "cyan_stained_glass", "glass", "light_gray_stained_glass", "blue_stained_glass", "tinted_glass"});
		st.glass = B.of(g);
		st.wall = B.of(s.pick(new String[] {"white_concrete", "light_gray_concrete", "smooth_quartz", "gray_concrete"}));
		st.trim = B.of(s.pick(new String[] {"smooth_quartz", "white_concrete", "light_gray_concrete", "polished_andesite", "iron_block"}));
		st.accent = st.trim;
		st.roof = B.of("gray_concrete");
		st.base = B.of("polished_andesite");
		st.neon = B.of(s.pick(B.NEONS));
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = Math.max(3, s.floors);
		sp.windows = Shell.Windows.CURTAIN;
		sp.elevator = true;
		boolean condo = (s.id & 1) == 0;
		sp.minRoom = 4; sp.maxRoom = condo ? 8 : 14;
		sp.groundBig = new String[] {"lobby", "lobby", "hall"};
		sp.groundSmall = new String[] {"storage"};
		sp.upperBig = condo ? new String[] {"studio", "living", "bedroom", "studio", "kitchen"} : new String[] {"office", "office", "meeting", "office"};
		sp.upperSmall = new String[] {"bath", "storage"};
		sp.storefront = true;
		sp.doubleDoor = true;
		sp.entranceDoor = "waxed_copper_door";
		sp.groundH = 5;
		look(s, sp, condo ? 1 : 0);
		Shell.build(s, st, sp);
	}

	static void office(Site s) {
		Style st = Style.pastel(s);
		st.wall = B.of(s.pick(new String[] {"white_concrete", "light_gray_concrete", "smooth_sandstone", "calcite", "polished_andesite"}));
		st.glass = B.of(s.pick(new String[] {"light_blue_stained_glass", "glass", "cyan_stained_glass"}));
		st.accent = B.of(s.pick(new String[] {"gray_concrete", "smooth_quartz", "light_blue_concrete"}));
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = Math.max(2, Math.min(s.floors, 20));
		sp.windows = Shell.Windows.BAND;
		sp.elevator = sp.floors >= 3;
		sp.minRoom = 4; sp.maxRoom = 14;
		sp.groundBig = new String[] {"lobby", "office", "meeting"};
		sp.groundSmall = new String[] {"bath"};
		sp.upperBig = new String[] {"office", "office", "meeting", "office"};
		sp.upperSmall = new String[] {"bath", "storage"};
		sp.doubleDoor = true;
		sp.entranceDoor = "birch_door";
		look(s, sp, Math.max(s.floors, s.heightM / 4) > 8 ? 0 : 4);
		Shell.build(s, st, sp);
	}

	static void hotel(Site s) {
		Style st = Style.pastel(s);
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = Math.max(3, Math.min(s.floors, 30));
		sp.windows = Shell.Windows.BAND;
		sp.elevator = true;
		sp.minRoom = 4; sp.maxRoom = 7;
		sp.groundBig = new String[] {"lobby", "dining", "living", "kitchen"};
		sp.groundSmall = new String[] {"bath", "storage"};
		sp.upperBig = new String[] {"hotel"};
		sp.upperSmall = new String[] {"storage"};
		sp.parapet = 2;
		sp.doubleDoor = true;
		sp.storefront = true;
		sp.groundH = 5;
		sp.entranceDoor = "birch_door";
		look(s, sp, Math.max(s.floors, s.heightM / 3) <= 6 ? 2 : 1);
		Shell.build(s, st, sp);
	}

	// ------------------------------------------------------------------ shops

	/** Debug: force the next shop's type (showcase). */
	static final ThreadLocal<String> FORCE_SHOP = new ThreadLocal<>();

	static final String[] SHOPS = {"convenience", "diner", "clothes", "pharmacy", "electronics", "bar", "laundromat", "gun_shop"};

	static void shop(Site s) {
		Style st = Style.pastel(s);
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = Math.min(3, s.floors);
		sp.groundH = 5;
		sp.storefront = true;
		sp.planGround = false;
		sp.doubleDoor = s.W >= 8;
		sp.entranceDoor = "birch_door";
		sp.upperBig = new String[] {"studio"};
		sp.upperSmall = new String[] {"bath"};
		look(s, sp, ((s.id >>> 5) & 1) == 0 ? 2 : 3);
		Shell.Result r = Shell.build(s, st, sp);
		String type = FORCE_SHOP.get() != null ? FORCE_SHOP.get() : SHOPS[(int) Math.floorMod(s.id >>> 3, SHOPS.length)];
		shopInterior(s, st, sp, r, type);
	}

	/** Open-plan ground floor of a shop: counter, product rows, back room. */
	static void shopInterior(Site s, Style st, Shell.Spec sp, Shell.Result r, String type) {
		int x0 = sp.x0 + 1, x1 = sp.x1 - 1, z0 = sp.z0 + 1, z1 = sp.z1 - 1, y = s.baseY, h = sp.groundH;
		if (x1 - x0 < 2 || z1 - z0 < 3) return;
		s.bounds(sp.x0, sp.z0, sp.x1, sp.z1);
		Plan.Core core = r.core;
		int backZ = z1 - 3;
		boolean backRoom = z1 - z0 >= 9;
		if (core.stairs) backZ = core.kz - 1; // shaft occupies the back strip
		Room shop = new Room(x0, z0, x1, backRoom ? backZ - 1 : z1, y, h);
		shop.type = "shop";
		BuildCommand.record(s, shop);
		if (backRoom && !core.stairs) {
			for (int x = x0; x <= x1; x++) s.fill(x, y, backZ, x, y + h - 2, backZ, st.inWall);
			s.door(x1 - 1, y, backZ, st.doorName, Direction.SOUTH, false);
			Room store = new Room(x0, backZ + 1, x1, z1, y, h);
			store.type = "storage";
			store.blockDoors(java.util.List.of(new int[] {x1 - 1, backZ}));
			Interiors.furnish(s, store, st);
			if (type.equals("gun_shop")) for (int i = 0; i < 2; i++) {
				Room.Slot a = store.wall(s, 1, 1);
				if (a == null) break;
				s.set(a.x(), y, a.z(), B.ammoBox(a.in()));
				store.take(a.x(), a.z());
			}
		} else if (core.stairs) {
			for (int x = x0; x <= x1; x++) if (x < core.kx - 1 || x > core.kx + core.width()) s.fill(x, y, backZ, x, y + h - 2, backZ, st.inWall);
			s.fill(core.kx + 1, y, backZ, core.kx + 1, y + 1, backZ, B.AIR);
			if (core.elevator) s.fill(core.kx + 4, y, backZ, core.kx + 5, y + 1, backZ, B.AIR);
		} else if (core.ladder) {
			shop.take(core.lx, core.lz);
			shop.take(core.lx + core.ladderFacing.getStepX(), core.lz);
		}
		shop.blockDoors(java.util.List.of(new int[] {r.doorX, sp.z0}, new int[] {r.doorX - 1, sp.z0}, new int[] {core.kx + 1, backZ}, new int[] {x1 - 1, backZ}));
		if (core.stairs) for (int x = core.kx; x <= core.kx + 5; x++) shop.take(x, backZ - 1);
		switch (type) {
			case "diner" -> {
				Interiors.checker(s, shop, B.of("white_concrete"), B.of("black_concrete"));
				Room.Slot c = shop.wall(s, Math.min(6, shop.w() - 2), 1);
				if (c != null) for (int i = 0; i < Math.min(6, shop.w() - 2); i++) {
					s.set(Room.ax(c, i, 0), y, Room.az(c, i, 0), B.of("red_terracotta"));
					int sx = Room.ax(c, i, 1), sz = Room.az(c, i, 1);
					if (shop.free(s, sx, sz)) { s.set(sx, y, sz, B.slab("red_wool_slab", false)); shop.take(sx, sz); }
				}
				for (int i = 0; i < 4; i++) booth(s, shop, st);
				Interiors.lights(s, shop, B.of("pearlescent_froglight"));
			}
			case "clothes" -> {
				Interiors.floor(s, shop, B.of("polished_diorite"));
				for (int x = shop.x0 + 2; x <= shop.x1 - 2; x += 3)
					for (int z = shop.z0 + 2; z <= shop.z1 - 2; z += 3)
						if (shop.free(s, x, z)) { s.set(x, y, z, B.of(Style.FABRIC[s.pick(Style.FABRIC.length)] + "_banner")); shop.take(x, z); }
				counter(s, shop, 3);
				Interiors.plant(s, shop); Interiors.plant(s, shop);
				Interiors.lights(s, shop, Interiors.rodDown());
			}
			case "pharmacy", "convenience" -> {
				Interiors.floor(s, shop, B.of(type.equals("pharmacy") ? "white_concrete" : "smooth_quartz"));
				fridges(s, shop);
				aisles(s, shop, type.equals("pharmacy"));
				counter(s, shop, 3);
				Interiors.lights(s, shop, B.of("sea_lantern"));
			}
			case "electronics" -> {
				Interiors.floor(s, shop, B.of("gray_concrete"));
				for (int i = 0; i < 5; i++) Interiors.tv(s, shop, i % 2 == 0);
				counter(s, shop, 3);
				aisles(s, shop, false);
				Interiors.lights(s, shop, B.of("sea_lantern"));
			}
			case "bar" -> {
				Interiors.floor(s, shop, B.of("dark_oak_planks"));
				bar(s, shop, st);
				for (int i = 0; i < 3; i++) booth(s, shop, st);
				Interiors.lights(s, shop, B.hanging("lantern"));
			}
			case "gun_shop" -> gunShop(s, shop);
			default -> { // laundromat
				Interiors.checker(s, shop, B.of("white_concrete"), B.of("light_blue_concrete"));
				for (int i = 0; i < 6; i++) {
					Room.Slot w = shop.wall(s, 1, 1);
					if (w == null) break;
					s.set(w.x(), y, w.z(), B.face("blast_furnace", w.in()));
					s.set(w.x(), y + 1, w.z(), B.face("furnace", w.in()));
				}
				Interiors.lights(s, shop, B.of("sea_lantern"));
			}
		}
		s.clearBounds();
	}

	/** Ammu-Nation style gun shop: glass gun counters with a clerk aisle behind, gun racks on the walls, ammo crates. */
	static void gunShop(Site s, Room shop) {
		int y = shop.y;
		Interiors.checker(s, shop, B.of("gray_concrete"), B.of("light_gray_concrete"));
		int len = Math.min(5, Math.max(r3(shop) , 3));
		Room.Slot c = null;
		while (len >= 3 && (c = shop.wall(s, len, 3)) == null) len--;
		if (c != null) {
			for (int i = 0; i < len; i++) {
				// rack on the wall behind the clerk aisle, counter in front of it
				s.set(Room.ax(c, i, 0), y + 1, Room.az(c, i, 0), B.gunRack(c.in()));
				s.set(Room.ax(c, i, 0), y + 2, Room.az(c, i, 0), B.gunRack(c.in()));
				s.set(Room.ax(c, i, 2), y, Room.az(c, i, 2), B.gunCounter(c.in()));
			}
		}
		for (int i = 0; i < 3; i++) {
			Room.Slot w = shop.wall(s, 2, 1);
			if (w == null) break;
			for (int k = 0; k < 2; k++) {
				s.set(Room.ax(w, k, 0), y + 1, Room.az(w, k, 0), B.gunRack(w.in()));
				s.set(Room.ax(w, k, 0), y + 2, Room.az(w, k, 0), B.gunRack(w.in()));
			}
		}
		Room.Slot a = shop.wall(s, 1, 1);
		if (a != null) s.set(a.x(), y, a.z(), B.ammoBox(a.in()));
		Interiors.lights(s, shop, B.of("sea_lantern"));
	}

	private static int r3(Room r) { return Math.max(r.w(), r.d()) - 4; }

	static void counter(Site s, Room r, int len) {
		Room.Slot c = r.wall(s, len, 2);
		if (c == null) return;
		for (int i = 0; i < len; i++) {
			s.set(Room.ax(c, i, 1), r.y, Room.az(c, i, 1), B.of(i == 0 ? "smooth_quartz" : "polished_deepslate"));
			if (i == 0) s.set(Room.ax(c, i, 1), r.y + 1, Room.az(c, i, 1), B.trap("dark_oak_trapdoor", c.in(), true, false));
		}
		// cash register on the counter facing the customers; the clerk stands behind it
		if (len >= 3) {
			s.set(Room.ax(c, 1, 1), r.y + 1, Room.az(c, 1, 1), B.register(c.in()));
			s.set(Room.ax(c, 1, 0), r.y, Room.az(c, 1, 0), B.AIR);
			s.set(Room.ax(c, 1, 0), r.y + 1, Room.az(c, 1, 0), B.AIR);
		}
		s.set(Room.ax(c, len - 1, 0), r.y, Room.az(c, len - 1, 0), B.face("oak_shelf", c.in()));
	}

	static void fridges(Site s, Room r) {
		Room.Slot f = null;
		int len = Math.min(8, Math.max(r.w(), r.d()) - 2);
		while (len >= 2 && (f = r.wall(s, len, 1)) == null) len--;
		if (f == null) return;
		for (int i = 0; i < len; i++) {
			int x = Room.ax(f, i, 0), z = Room.az(f, i, 0);
			BlockState door = (i & 1) == 0 ? B.of("sea_lantern") : B.of("light_blue_stained_glass");
			s.set(x, r.y, z, B.of("iron_block"));
			s.set(x, r.y + 1, z, door);
			s.set(x, r.y + 2, z, (i % 4 < 2) ? B.of("verdant_froglight") : B.of("blue_concrete"));
		}
	}

	static void aisles(Site s, Room r, boolean white) {
		for (int x = r.x0 + 2; x <= r.x1 - 2; x += 3) {
			for (int z = r.z0 + 2; z <= r.z1 - 2; z++) {
				if (!r.free(s, x, z)) continue;
				r.take(x, z);
				String c = Style.FABRIC[Math.floorMod(x * 7 + z * 13 + (int) s.id, Style.FABRIC.length)];
				s.set(x, r.y, z, white ? B.of("white_concrete") : B.c(c, "glazed_terracotta"));
				s.set(x, r.y + 1, z, white ? B.of("birch_shelf") : B.c(Style.FABRIC[Math.floorMod(x * 5 + z * 3, Style.FABRIC.length)], "glazed_terracotta"));
			}
		}
	}

	static void booth(Site s, Room r, Style st) {
		Room.Slot b = r.wall(s, 1, 3);
		if (b == null) return;
		String c = st.sofa;
		s.set(Room.ax(b, 0, 0), r.y, Room.az(b, 0, 0), B.stair(c + "_wool_stairs", b.in().getOpposite(), false));
		s.set(Room.ax(b, 0, 1), r.y, Room.az(b, 0, 1), B.of("dark_oak_fence"));
		s.set(Room.ax(b, 0, 1), r.y + 1, Room.az(b, 0, 1), B.of("white_carpet"));
		s.set(Room.ax(b, 0, 2), r.y, Room.az(b, 0, 2), B.stair(c + "_wool_stairs", b.in(), false));
	}

	static void bar(Site s, Room r, Style st) {
		int len = Math.min(7, Math.max(3, r.w() - 3));
		Room.Slot c = r.wall(s, len, 3);
		if (c == null) return;
		for (int i = 0; i < len; i++) {
			s.set(Room.ax(c, i, 0), r.y, Room.az(c, i, 0), B.face(st.wood + "_shelf", c.in()));
			s.set(Room.ax(c, i, 0), r.y + 1, Room.az(c, i, 0), B.of(i % 2 == 0 ? "brewing_stand" : "decorated_pot"));
			s.set(Room.ax(c, i, 1), r.y, Room.az(c, i, 1), B.of("air"));
			s.set(Room.ax(c, i, 2), r.y, Room.az(c, i, 2), B.of("polished_deepslate"));
			if (i % 2 == 0) s.set(Room.ax(c, i, 2), r.y + 1, Room.az(c, i, 2), B.of("potted_fern"));
			int sx = Room.ax(c, i, 3), sz = Room.az(c, i, 3);
			if (r.free(s, sx, sz) && i % 2 == 1) { s.set(sx, r.y, sz, B.of("dark_oak_fence")); }
		}
		for (int i = 0; i < len; i++) s.set(Room.ax(c, i, 2), r.y + 2, Room.az(c, i, 2), B.hanging("lantern"));
	}

	// ------------------------------------------------------------------ gas station

	static void gasStation(Site s) {
		Style st = Style.pastel(s);
		st.wall = B.of("white_concrete");
		st.accent = B.of(s.pick(new String[] {"red_concrete", "blue_concrete", "lime_concrete", "orange_concrete"}));
		int W = s.W, D = s.D;
		Deco.asphalt(s, 0, 0, W - 1, D - 1, s.baseY);
		int storeD = Math.min(12, Math.max(7, D / 2));
		Shell.Spec sp = new Shell.Spec(s);
		sp.x0 = 1; sp.x1 = Math.min(W - 2, 1 + Math.max(8, W * 2 / 3));
		sp.z0 = D - storeD; sp.z1 = D - 1;
		sp.floors = 1; sp.groundH = 5; sp.storefront = true; sp.planGround = false;
		sp.entranceDoor = "birch_door"; sp.doubleDoor = true;
		BlockState gasAccent = st.accent;
		look(s, sp, 3);
		Shell.Result r = Shell.build(s, st, sp);
		st.accent = gasAccent;
		shopInterior(s, st, sp, r, "convenience");
		// canopy and pumps
		int cz1 = sp.z0 - 3;
		if (cz1 >= 6) {
			int cx0 = 1, cx1 = W - 2, cz0 = Math.max(1, cz1 - 9);
			Deco.canopy(s, cx0, cz0, cx1, cz1, s.baseY, 5, B.of("white_concrete"), st.accent, B.of("sea_lantern"));
			int mz = (cz0 + cz1) / 2;
			for (int x = cx0 + 3; x <= cx1 - 3; x += 4) {
				s.fill(x - 1, s.baseY - 1, mz - 1, x + 1, s.baseY - 1, mz + 1, B.of("smooth_stone"));
				Deco.pump(s, x, s.baseY, mz, Direction.NORTH);
				Deco.bollard(s, x - 1, s.baseY, mz - 1);
				Deco.bollard(s, x + 1, s.baseY, mz + 1);
			}
		}
		Deco.pylon(s, W - 2, 1, s.baseY, 6, st.accent, B.of("sea_lantern"));
		for (int x = sp.x0; x <= sp.x1; x += 4) Deco.bollard(s, x, s.baseY, sp.z0 - 1);
		// ATM on the store front, screen to the forecourt
		if (sp.x1 + 1 < W) s.set(sp.x1 + 1, s.baseY, sp.z0 + 1, B.atm(Direction.NORTH));
		else s.set(sp.x0 + 1, s.baseY, sp.z0 - 1, B.atm(Direction.NORTH));
	}

	// ------------------------------------------------------------------ motel

	static void motel(Site s, boolean landmarkRoom) {
		Style st = Style.pastel(s);
		int tone = (int) (Facade.mix(s.id * 31 + 7) >>> 36);
		st.wall = Palette.pastel(tone, false);
		st.trim = Palette.trim();
		BlockState motelWin = Palette.window(Window.DECO);
		int W = s.W, D = s.D;
		Deco.asphalt(s, 0, 0, W - 1, D - 1, s.baseY);
		int rd = Math.min(7, Math.max(5, D / 2));
		int floors = Math.min(2, Math.max(1, s.floors));
		int z0 = D - rd, z1 = D - 1;
		Deco.parking(s, 1, 1, W - 2, Math.min(z0 - 3, 6), s.baseY);
		int roomW = 5;
		int n = Math.max(1, (W - 2) / roomW);
		int yTop = s.baseY + floors * 4 - 1;
		s.bounds(1, z0, W - 2, z1);
		s.fill(1, s.baseY - 3, z0, W - 2, s.baseY - 2, z1, B.of("smooth_stone"));
		s.fill(1, s.baseY, z0, W - 2, yTop + 3, z1, B.AIR);
		for (int f = 0; f < floors; f++) {
			int y = s.baseY + f * 4;
			s.fill(1, y - 1, z0, W - 2, y - 1, z1, f == 0 ? B.of("smooth_stone") : st.trim);
			for (int i = 0; i < n; i++) {
				int rx0 = 1 + i * roomW, rx1 = (i == n - 1) ? W - 2 : rx0 + roomW;
				// walls
				for (int x = rx0; x <= rx1; x++) for (int z = z0; z <= z1; z++) {
					boolean wall = x == rx0 || x == rx1 || z == z0 || z == z1;
					if (wall) s.fill(x, y, z, x, y + 2, z, st.wall);
				}
				int dx = rx0 + 1;
				s.door(dx, y, z0, "spruce_door", Direction.SOUTH, false);
				s.set(rx0 + 3, y + 1, z0, motelWin);
				if (rx0 + 4 < rx1) s.set(rx0 + 4, y + 1, z0, motelWin);
				if (z1 - z0 >= 4) s.set(rx0 + 2, y + 1, z1, motelWin);
				s.set(dx + 1, y + 2, z0 - 1, B.face("lantern", Direction.DOWN));
				boolean special = landmarkRoom && f == 0 && i == n - 1;
				Room r = new Room(rx0 + 1, z0 + 1, rx1 - 1, z1 - 1, y, 4);
				r.blockDoors(java.util.List.of(new int[] {dx, z0}));
				if (special) Landmarks.motelRoom(s, r);
				else {
					r.type = "hotel";
					Interiors.furnish(s, r, st);
				}
			}
		}
		s.fill(1, yTop, z0, W - 2, yTop, z1, B.of("smooth_stone"));
		for (int x = 1; x <= W - 2; x++) {
			for (int z : new int[] {z0, z1}) { s.set(x, yTop + 1, z, st.wall); s.set(x, yTop + 2, z, Palette.parapetCap()); }
		}
		for (int z = z0 + 1; z < z1; z++) for (int x : new int[] {1, W - 2}) { s.set(x, yTop + 1, z, st.wall); s.set(x, yTop + 2, z, Palette.parapetCap()); }
		s.clearBounds();
		// neon tube along the roof edge and an eyebrow over the doors
		int nc = Math.floorMod(tone, 16) == 4 ? 4 : (tone & 7) == 4 ? 6 : tone & 7;
		for (int x = 1; x <= W - 2; x++) {
			s.setFree(x, yTop, z0 - 1, Palette.neon(nc, Direction.NORTH, false));
			if (floors == 1) s.setFree(x, s.baseY + 3, z0 - 1, Palette.eyebrow(tone >> 4, Direction.NORTH));
		}
		// upper walkway with railing and stair
		if (floors == 2 && z0 - 2 >= 0) {
			int wy = s.baseY + 3;
			s.fill(1, wy, z0 - 2, W - 2, wy, z0 - 1, B.slab("smooth_stone_slab", true));
			for (int x = 1; x <= W - 2; x++) s.set(x, wy + 1, z0 - 2, B.of("iron_bars"));
			for (int x = 1; x <= W - 2; x += 5) s.fill(x, s.baseY, z0 - 2, x, wy - 1, z0 - 2, B.of("quartz_pillar"));
			for (int k = 0; k < 4; k++) s.set(W - 2 - k, s.baseY + k, z0 - 3 >= 0 ? z0 - 3 : 0, B.stair("smooth_quartz_stairs", Direction.EAST, false));
		}
		Deco.pylon(s, 2, 1, s.baseY, 7, B.of("pink_concrete"), B.of("pearlescent_froglight"));
	}

	// ------------------------------------------------------------------ club

	static void club(Site s) {
		Style st = new Style();
		Style.common(s, st);
		st.wall = B.of(s.pick(new String[] {"black_concrete", "purple_concrete", "gray_concrete", "magenta_terracotta"}));
		st.trim = B.of("black_concrete");
		st.accent = B.of("polished_blackstone");
		st.glass = B.of("purple_stained_glass");
		st.roof = B.of("black_concrete");
		st.base = B.of("polished_blackstone");
		st.neon = B.of(s.chance(0.6) ? "pearlescent_froglight" : "sea_lantern");
		st.inWall = B.of("black_concrete");
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = 1; sp.groundH = 6; sp.windows = Shell.Windows.SOLID; sp.planGround = false;
		sp.doubleDoor = true; sp.entranceDoor = "dark_oak_door";
		if (s.D >= 14) { sp.z0 = 4; Deco.asphalt(s, 0, 0, s.W - 1, 3, s.baseY); }
		Shell.Result r = Shell.build(s, st, sp);
		clubInterior(s, st, sp, r);
		// front: neon columns, glass block band, magenta entrance canopy, palms
		s.bounds(sp.x0, sp.z0, sp.x1, sp.z1);
		int fz0 = Shell.frontZ(s, r.doorX, sp);
		for (int x = sp.x0 + 1; x < sp.x1; x++) {
			int fz = Shell.frontZ(s, x, sp);
			if (fz < 0 || Math.abs(x - r.doorX + 0.5) < 2) continue;
			int t = x - sp.x0;
			if (t % 4 == 0) s.fill(x, s.baseY, fz, x, s.baseY + 4, fz, st.neon);
			else if (t % 4 == 2) s.fill(x, s.baseY + 1, fz, x, s.baseY + 3, fz, st.glass);
			s.set(x, s.baseY + 5, fz, B.of(((t / 2) & 1) == 0 ? "magenta_concrete" : "purple_concrete"));
		}
		s.clearBounds();
		if (fz0 > 1) {
			for (int x = r.doorX - 3; x <= r.doorX + 2; x++) for (int z = 0; z < fz0; z++) s.set(x, s.baseY + 4, z, B.of(z == 0 ? "pearlescent_froglight" : "magenta_concrete"));
			for (int z = 1; z < fz0; z++) { s.set(r.doorX - 3, s.baseY, z, B.of("polished_blackstone_wall")); s.set(r.doorX + 2, s.baseY, z, B.of("polished_blackstone_wall")); }
			Deco.palm(s, r.doorX - 6, s.baseY, 1);
			Deco.palm(s, r.doorX + 5, s.baseY, 1);
		}
		Deco.neonRing(s, r.roofY + 1, B.of("sea_lantern"), sp.x0, sp.z0, sp.x1, sp.z1);
		// look neon tubes: band above the entrance, verticals at the front corners
		int nc0 = (int) (Facade.mix(s.id) >>> 40) & 15, nc = nc0 == 4 ? 4 : (nc0 & 7) == 4 ? 6 : nc0 & 7;
		for (int x = sp.x0; x <= sp.x1; x++) {
			int fzz = Shell.frontZ(s, x, sp);
			if (fzz < 1) continue;
			s.setFree(x, s.baseY + 5, fzz - 1, Palette.neon(nc, Direction.NORTH, false));
			if (x == sp.x0 + 1 || x == sp.x1 - 1) for (int y = s.baseY; y < s.baseY + 5; y++) s.setFree(x, y, fzz - 1, Palette.neon(nc + 1, Direction.NORTH, true));
		}
		// red carpet and rope at the door
		int fz = Shell.frontZ(s, r.doorX, sp);
		if (fz > 0) for (int z = 0; z < fz; z++) { s.set(r.doorX, s.baseY, z, B.of("red_carpet")); s.set(r.doorX - 1, s.baseY, z, B.of("red_carpet")); }
	}

	static void clubInterior(Site s, Style st, Shell.Spec sp, Shell.Result r) {
		int x0 = sp.x0 + 1, x1 = sp.x1 - 1, z0 = sp.z0 + 1, z1 = sp.z1 - 1, y = s.baseY;
		s.bounds(sp.x0, sp.z0, sp.x1, sp.z1);
		Room room = new Room(x0, z0, x1, z1, y, sp.groundH);
		room.blockDoors(java.util.List.of(new int[] {r.doorX, sp.z0}, new int[] {r.doorX - 1, sp.z0}));
		room.type = "club";
		BuildCommand.record(s, room);
		Interiors.floor(s, room, B.of("polished_blackstone"));
		// dance floor: coloured glass lit from below
		int dw = Math.max(3, room.w() / 2), dd = Math.max(3, room.d() / 2);
		int dx0 = room.cx() - dw / 2, dz0 = room.cz() - dd / 2;
		String[] cols = {"magenta", "cyan", "purple", "pink", "blue", "lime"};
		for (int x = dx0; x < dx0 + dw; x++) for (int z = dz0; z < dz0 + dd; z++) {
			if (!room.contains(x, z)) continue;
			room.take(x, z);
			s.set(x, y - 1, z, B.c(cols[Math.floorMod(x * 3 + z * 5, cols.length)], "stained_glass"));
			s.set(x, y - 2, z, B.of("pearlescent_froglight"));
		}
		for (int x = dx0 - 1; x <= dx0 + dw; x++) { room.take(x, dz0 - 1); room.take(x, dz0 + dd); }
		for (int z = dz0 - 1; z <= dz0 + dd; z++) { room.take(dx0 - 1, z); room.take(dx0 + dw, z); }
		// DJ booth at the back wall
		int bx = room.cx();
		for (int dx = -2; dx <= 2; dx++) {
			s.set(bx + dx, y, z1, B.of(dx == 0 ? "jukebox" : "note_block"));
			s.set(bx + dx, y + 1, z1, dx == 0 ? B.trap("dark_oak_trapdoor", Direction.SOUTH, true, false) : B.of("black_concrete"));
			room.take(bx + dx, z1);
			room.take(bx + dx, z1 - 1);
		}
		s.fill(bx - 3, y, z1, bx - 3, y + 2, z1, B.of("black_concrete"));
		s.fill(bx + 3, y, z1, bx + 3, y + 2, z1, B.of("black_concrete"));
		bar(s, room, st);
		for (int i = 0; i < 4; i++) Interiors.sofa(s, room, s.pick(new String[] {"purple", "magenta", "black", "red"}), 3);
		// lights: coloured spots in the ceiling
		for (int x = x0 + 1; x <= x1 - 1; x += 3) for (int z = z0 + 1; z <= z1 - 1; z += 3)
			s.set(x, r.roofY, z, B.of(((x + z) & 1) == 0 ? "pearlescent_froglight" : "sea_lantern"));
		s.clearBounds();
	}

	// ------------------------------------------------------------------ warehouse

	static void warehouse(Site s) {
		Style st = new Style();
		Style.common(s, st);
		st.wall = B.of(s.pick(new String[] {"light_gray_concrete", "white_concrete", "smooth_stone", "cyan_terracotta", "light_gray_terracotta"}));
		st.trim = B.of("gray_concrete");
		st.accent = B.of("gray_concrete");
		st.glass = B.of("glass");
		st.roof = B.of("light_gray_concrete");
		st.floor = B.of("smooth_stone");
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = 1; sp.groundH = 8; sp.windows = Shell.Windows.SOLID; sp.planGround = false;
		sp.entranceDoor = "spruce_door";
		look(s, sp, 5);
		Shell.Result r = Shell.build(s, st, sp);
		int y = s.baseY;
		s.bounds(sp.x0, sp.z0, sp.x1, sp.z1);
		// racks
		Room room = new Room(sp.x0 + 1, sp.z0 + 1, sp.x1 - 1, sp.z1 - 1, y, sp.groundH);
		for (int x = room.x0 + 2; x <= room.x1 - 2; x += 4) {
			for (int z = room.z0 + 3; z <= room.z1 - 2; z++) {
				for (int k = 0; k < 5; k += 2) {
					s.set(x, y + k, z, (z - room.z0) % 4 == 0 ? B.of("spruce_fence") : B.of(s.chance(0.6) ? "barrel" : "spruce_planks"));
					s.set(x, y + k + 1, z, B.slab("spruce_slab", false));
				}
			}
		}
		for (int x = room.x0 + 1; x <= room.x1 - 1; x += 5) for (int z = room.z0 + 1; z <= room.z1 - 1; z += 5) s.set(x, r.roofY - 1, z, Interiors.rodDown());
		s.clearBounds();
	}

	// ------------------------------------------------------------------ bank

	static void bank(Site s) {
		Style st = new Style();
		Style.common(s, st);
		st.wall = B.of(s.pick(new String[] {"smooth_quartz", "smooth_sandstone", "calcite", "white_concrete"}));
		st.trim = B.of("quartz_bricks");
		st.accent = B.axis("quartz_pillar", Direction.Axis.Y);
		st.glass = B.of("glass_pane");
		st.roof = B.of("smooth_stone");
		st.floor = B.of("polished_diorite");
		st.base = B.of("polished_andesite");
		st.neon = B.of("gold_block");
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = Math.min(4, Math.max(1, s.floors));
		sp.groundH = 6;
		sp.planGround = false;
		sp.doubleDoor = true; sp.entranceDoor = "dark_oak_door";
		sp.upperBig = new String[] {"office", "meeting"};
		sp.upperSmall = new String[] {"bath"};
		sp.elevator = sp.floors >= 3;
		sp.parapet = 2;
		look(s, sp, 4);
		Shell.Result r = Shell.build(s, st, sp);
		int y = s.baseY;
		s.bounds(sp.x0, sp.z0, sp.x1, sp.z1);
		Room hall = new Room(sp.x0 + 1, sp.z0 + 1, sp.x1 - 1, sp.z1 - 1, y, sp.groundH);
		hall.blockDoors(java.util.List.of(new int[] {r.doorX, sp.z0}, new int[] {r.doorX - 1, sp.z0}));
		Interiors.checker(s, hall, B.of("polished_diorite"), B.of("polished_andesite"));
		// ATMs against the front wall inside the lobby
		for (int x = hall.x0; x <= Math.min(hall.x0 + 1, hall.x1); x++) if (hall.free(s, x, hall.z0)) { s.set(x, y, hall.z0, B.atm(Direction.SOUTH)); hall.take(x, hall.z0); }
		// teller line across the room
		int tz = hall.z0 + Math.max(3, hall.d() / 2);
		if (r.core.stairs) tz = Math.min(tz, r.core.kz - 3);
		for (int x = hall.x0; x <= hall.x1; x++) {
			boolean gap = x == hall.x1;
			if (gap) continue;
			s.set(x, y, tz, B.of("dark_oak_planks"));
			s.set(x, y + 1, tz, (x - hall.x0) % 4 == 1 ? B.register(Direction.NORTH) : B.of("iron_bars"));
			hall.take(x, tz); hall.take(x, tz + 1);
		}
		// vault behind
		int vx0 = hall.x0, vx1 = Math.min(hall.x1 - 2, hall.x0 + 5), vz0 = tz + 2, vz1 = Math.min(hall.z1, tz + 6);
		if (r.core.stairs) vz1 = Math.min(vz1, r.core.kz - 2);
		if (vz1 - vz0 >= 2 && vx1 - vx0 >= 3) {
			for (int x = vx0; x <= vx1; x++) for (int z = vz0; z <= vz1; z++) {
				boolean wall = x == vx1 || z == vz0;
				if (wall) s.fill(x, y, z, x, y + 3, z, B.of("iron_block"));
				else if (z == vz1 || x == vx0) s.fill(x, y, z, x, y + 1, z, B.of("gold_block"));
				else s.set(x, y - 1, z, B.of("iron_block"));
			}
			s.door(vx0 + 2, y, vz0, "iron_door", Direction.SOUTH, false);
			s.set(vx0 + 3, y + 1, vz0 - 1, B.button("stone_button", Direction.NORTH));
		}
		for (int i = 0; i < 2; i++) Interiors.sofa(s, hall, "red", 3);
		Interiors.palm(s, hall); Interiors.palm(s, hall);
		for (int x = hall.x0 + 1; x <= hall.x1 - 1; x += 4) for (int z = hall.z0 + 1; z <= hall.z1 - 1; z += 4) s.set(x, y + sp.groundH - 2, z, B.hanging("lantern"));
		s.clearBounds();
	}

	// ------------------------------------------------------------------ mall

	static void mall(Site s) {
		Style st = Style.pastel(s);
		st.glass = B.of("glass");
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = 1; sp.groundH = 7; sp.storefront = true; sp.planGround = false;
		sp.doubleDoor = true; sp.entranceDoor = "birch_door";
		look(s, sp, 4);
		Shell.Result r = Shell.build(s, st, sp);
		int y = s.baseY;
		s.bounds(sp.x0, sp.z0, sp.x1, sp.z1);
		int x0 = sp.x0 + 1, x1 = sp.x1 - 1, z0 = sp.z0 + 1, z1 = sp.z1 - 1;
		int mid = r.doorX;
		Room atrium = new Room(Math.max(x0, mid - 2), z0, Math.min(x1, mid + 2), z1, y, sp.groundH);
		Interiors.checker(s, new Room(x0, z0, x1, z1, y, 4), B.of("smooth_quartz"), B.of("polished_diorite"));
		// shops along both sides of the atrium
		int shopDepth = 6;
		for (int side = 0; side < 2; side++) {
			int sx0 = side == 0 ? x0 : atrium.x1 + 1, sx1 = side == 0 ? atrium.x0 - 1 : x1;
			if (sx1 - sx0 < 4) continue;
			for (int z = z0 + 2; z + shopDepth <= z1; z += shopDepth + 1) {
				int fx = side == 0 ? sx1 : sx0;
				// storefront wall with glass and door
				for (int zz = z; zz < z + shopDepth; zz++) s.fill(fx, y, zz, fx, y + 3, zz, B.of("glass"));
				s.fill(sx0, y, z - 1, sx1, y + 3, z - 1, st.inWall);
				s.fill(sx0, y, z + shopDepth, sx1, y + 3, z + shopDepth, st.inWall);
				s.fill(fx, y + 4, z, fx, y + 4, z + shopDepth - 1, B.of(s.pick(B.NEONS)));
				s.door(fx, y, z + shopDepth / 2, "birch_door", side == 0 ? Direction.EAST : Direction.WEST, false);
				int ix0 = side == 0 ? sx0 : sx0 + 1, ix1 = side == 0 ? sx1 - 1 : sx1;
				Room shop = new Room(ix0, z, ix1, z + shopDepth - 1, y, 5);
				shop.blockDoors(java.util.List.of(new int[] {fx, z + shopDepth / 2}));
				switch (s.pick(4)) {
					case 0 -> { Interiors.floor(s, shop, B.of("polished_diorite")); for (int i = 0; i < 4; i++) { Room.Slot b = shop.wall(s, 1, 1); if (b != null) s.set(b.x(), y, b.z(), B.of(s.pick(Style.FABRIC) + "_banner")); } counter(s, shop, 2); }
					case 1 -> { fridges(s, shop); aisles(s, shop, false); counter(s, shop, 2); }
					case 2 -> { for (int i = 0; i < 3; i++) Interiors.tv(s, shop, false); counter(s, shop, 2); }
					default -> { for (int i = 0; i < 2; i++) booth(s, shop, st); counter(s, shop, 3); }
				}
				Interiors.lights(s, shop, B.of("sea_lantern"));
			}
		}
		// fountain and palms in the atrium
		int fz = (z0 + z1) / 2;
		s.fill(mid - 1, y, fz - 1, mid + 1, y, fz + 1, B.of("smooth_quartz_slab"));
		s.set(mid, y, fz, B.cauldron(3));
		for (int z = z0 + 3; z < z1; z += 6) { Deco.palm(s, atrium.x0, y, z); Deco.palm(s, atrium.x1, y, z); }
		for (int z = z0; z <= z1; z += 4) s.set(mid, r.roofY, z, B.of("glass"));
		s.clearBounds();
	}

	// ------------------------------------------------------------------ civic

	static void police(Site s) {
		Style st = new Style();
		Style.common(s, st);
		st.wall = B.of("white_concrete");
		st.trim = B.of("blue_concrete");
		st.accent = B.of("blue_concrete");
		st.glass = B.of("glass_pane");
		st.roof = B.of("gray_concrete");
		st.floor = B.of("smooth_stone");
		st.base = B.of("polished_andesite");
		st.neon = B.of("sea_lantern");
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = Math.min(4, Math.max(2, s.floors));
		sp.minRoom = 3; sp.maxRoom = 7;
		sp.groundBig = new String[] {"lobby", "office", "cell", "cell", "cell", "meeting"};
		sp.groundSmall = new String[] {"storage"};
		sp.upperBig = new String[] {"office", "meeting", "office", "storage"};
		sp.upperSmall = new String[] {"bath"};
		sp.doubleDoor = true; sp.entranceDoor = "spruce_door";
		sp.elevator = sp.floors >= 3;
		look(s, sp, 4);
		Shell.Result r = Shell.build(s, st, sp);
		// cells get bars on their room walls
		for (Room rm : r.plans.get(0).rooms) if (rm.type.equals("cell")) {
			for (int x = rm.x0; x <= rm.x1; x++) { s.set(x, rm.y + 1, rm.z0 - 1, B.of("iron_bars")); }
		}
		Deco.neonFront(s, 2, s.W - 3, sp.levelY(s, 1) - 1, B.of("blue_concrete"));
		Deco.helipad(s, s.W / 2, s.D / 2, r.roofY + 1);
	}

	static void hospital(Site s) {
		Style st = new Style();
		Style.common(s, st);
		st.wall = B.of("white_concrete");
		st.trim = B.of("light_gray_concrete");
		st.accent = B.of("light_blue_concrete");
		st.glass = B.of("light_blue_stained_glass");
		st.roof = B.of("light_gray_concrete");
		st.floor = B.of("white_concrete");
		st.base = B.of("polished_andesite");
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = Math.max(2, Math.min(s.floors, 12));
		sp.windows = Shell.Windows.BAND;
		sp.elevator = true;
		sp.minRoom = 4; sp.maxRoom = 8;
		sp.groundBig = new String[] {"lobby", "ward", "office", "ward"};
		sp.groundSmall = new String[] {"bath", "storage"};
		sp.upperBig = new String[] {"ward"};
		sp.upperSmall = new String[] {"bath", "office"};
		sp.doubleDoor = true; sp.entranceDoor = "birch_door";
		look(s, sp, Math.max(s.floors, s.heightM / 3) >= 6 ? 1 : 4);
		Shell.Result r = Shell.build(s, st, sp);
		// red cross on the front above the entrance
		int cx = r.doorX, fz = Shell.frontZ(s, cx, sp);
		int cy = r.roofY - 3;
		if (fz >= 0 && cy > s.baseY + 5) {
			for (int k = -1; k <= 1; k++) { s.set(cx + k, cy, fz, B.of("red_concrete")); s.set(cx, cy + k, fz, B.of("red_concrete")); }
		}
		Deco.helipad(s, (sp.x0 + sp.x1) / 2, (sp.z0 + sp.z1) / 2, r.roofY + 1);
	}

	static void marina(Site s) {
		Style st = new Style();
		Style.common(s, st);
		st.wall = Palette.siding(((s.id >>> 4) & 1) == 0);
		st.trim = Palette.trim();
		st.accent = Palette.trim();
		st.glass = Palette.cottageWindow();
		st.roof = B.of("spruce_planks");
		st.floor = B.of("spruce_planks");
		st.base = B.of("spruce_planks");
		int W = s.W, D = s.D;
		// deck over the whole lot
		s.fill(0, s.baseY - 1, 0, W - 1, s.baseY - 1, D - 1, B.of("spruce_planks"));
		Shell.Spec sp = new Shell.Spec(s);
		sp.x0 = 1; sp.z0 = 1; sp.x1 = Math.min(W - 2, 12); sp.z1 = Math.min(D - 2, 10);
		sp.floors = 1;
		sp.groundBig = new String[] {"living", "storage"};
		sp.groundSmall = new String[] {"bath"};
		sp.parapet = 0;
		Shell.Result r = Shell.build(s, st, sp);
		Deco.gable(s, sp.x0, sp.z0, sp.x1, sp.z1, r.roofY + 1, "spruce_stairs", st.wall);
		for (int x = 0; x < W; x += 3) { s.set(x, s.baseY, D - 1, B.of("spruce_fence")); }
		for (int x = 0; x < W; x += 6) for (int z = sp.z1 + 2; z < D - 1; z += 4) {
			s.set(x, s.baseY, z, B.of("spruce_fence"));
			s.set(x, s.baseY + 1, z, B.of("lantern"));
		}
		for (int i = 0; i < 3; i++) s.set(sp.x1 + 1 + i, s.baseY, 0, B.of("barrel"));
	}
}
