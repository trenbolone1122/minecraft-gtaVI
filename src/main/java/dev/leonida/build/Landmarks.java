package dev.leonida.build;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hand-designed landmarks based on the official videos. Keys agreed with the world module:
 * jason_house, lucia_home, el_gas_station, club_vice, gym, office_floor, motel_room.
 */
final class Landmarks {
	private Landmarks() {}

	static boolean build(Site s) {
		switch (s.landmark) {
			case "jason_house" -> jasonHouse(s);
			case "lucia_home" -> luciaHome(s);
			case "el_gas_station" -> foodMart(s);
			case "club_vice" -> clubVice(s);
			case "gym" -> gym(s);
			case "office_floor" -> officeFloor(s);
			case "motel_room" -> Kinds.motel(s, true);
			default -> { return false; }
		}
		return true;
	}

	// ------------------------------------------------------------ Jason's stilt house (Trailer 2 opening)

	/** Weathered teal two-storey clapboard house on stilts with a wrap-around deck, outside stair and a tin roof. */
	static void jasonHouse(Site s) {
		int W = s.W, D = s.D, y = s.baseY;
		// sandy yard with grass patches
		for (int x = s.cx0; x <= s.cx1; x++) for (int z = s.cz0; z <= s.cz1; z++)
			if (s.inLot(x, z)) {
				int hsh = Math.floorMod((x * 73856093) ^ (z * 19349663) ^ (x / 3 * 83492791) ^ (z / 3 * 2971215), 10);
				s.set(x, y - 1, z, B.of(hsh < 3 ? "grass_block" : (hsh == 3 ? "coarse_dirt" : "sand")));
			}
		int hw = Math.min(14, W - 6), hd = Math.min(10, D - 7);
		if (hw < 7 || hd < 6) { Kinds.house(s); return; }
		int hx0 = (W - hw) / 2 + 1, hx1 = hx0 + hw - 1, hz0 = Math.max(4, (D - hd) / 2 + 1), hz1 = hz0 + hd - 1;
		int lift = 3;
		int deckY = y + lift - 1;
		// deck: two in front, one around, on stilts
		int dx0 = hx0 - 1, dx1 = hx1 + 2, dz0 = hz0 - 3, dz1 = hz1 + 1;
		s.fill(dx0, deckY, dz0, dx1, deckY, dz1, B.of("spruce_planks"));
		for (int x = dx0; x <= dx1; x += 3) for (int z = dz0; z <= dz1; z += 3) s.fill(x, y, z, x, deckY - 1, z, B.axis("stripped_spruce_log", Direction.Axis.Y));
		for (int x = dx0; x <= dx1; x++) { s.set(x, deckY + 1, dz0, B.of("spruce_fence")); s.set(x, deckY + 1, dz1, B.of("spruce_fence")); }
		for (int z = dz0; z <= dz1; z++) { s.set(dx0, deckY + 1, z, B.of("spruce_fence")); s.set(dx1, deckY + 1, z, B.of("spruce_fence")); }
		// outside stair up the right side of the deck, from the front yard
		int sx = dx1 + 1;
		for (int k = 0; k < lift; k++) {
			s.set(sx, y + k, dz0 + 1 + lift - k, B.stair("spruce_stairs", Direction.NORTH, false));
			s.set(sx + 1, y + k + 1, dz0 + 1 + lift - k, B.of("spruce_fence"));
		}
		s.set(dx1, deckY + 1, dz0 + 1, B.AIR);
		s.set(dx1, deckY + 1, dz0 + 2, B.AIR);
		// house body
		Style st = new Style();
		Style.common(s, st);
		st.wall = B.of("waxed_oxidized_cut_copper");
		st.accent = B.of("white_concrete");
		st.trim = B.of("white_concrete");
		st.glass = B.of("glass_pane");
		st.roof = B.of("spruce_planks");
		st.floor = B.of("spruce_planks");
		st.base = B.of("spruce_planks");
		st.inWall = B.of("white_terracotta");
		st.cabinet = "green_terracotta";
		st.sofa = "brown"; st.bed = "light_gray"; st.rug = "orange";
		st.doorName = "spruce_door"; st.wood = "spruce";
		Shell.Spec sp = new Shell.Spec(s);
		sp.x0 = hx0; sp.z0 = hz0; sp.x1 = hx1; sp.z1 = hz1;
		sp.lift = lift;
		sp.floors = 2;
		sp.parapet = 0;
		sp.minRoom = 3; sp.maxRoom = 7;
		sp.groundBig = new String[] {"living", "kitchen", "dining"};
		sp.groundSmall = new String[] {"bath"};
		sp.upperBig = new String[] {"bedroom", "bedroom", "living"};
		sp.upperSmall = new String[] {"bath"};
		Shell.Result r = Shell.build(s, st, sp);
		Deco.gable(s, hx0, hz0, hx1, hz1, r.roofY + 1, "polished_andesite_stairs", st.wall);
		// yellow ladder against the front wall up to the roof (Jason fixing the roof)
		int lx = hx0 + 2;
		for (int k = 1; k <= 8; k++) s.set(lx, deckY + k, hz0 - 1, B.face("ladder", Direction.NORTH));
		// satellite dish on the deck corner
		s.set(dx0 + 1, deckY + 1, dz0 + 1, B.of("spruce_fence"));
		s.set(dx0 + 1, deckY + 2, dz0 + 1, B.of("daylight_detector"));
		// deck furniture: chairs, cooler, string light
		s.set(hx1 - 1, deckY + 1, dz0 + 1, B.stair("spruce_stairs", Direction.SOUTH, false));
		s.set(hx1 - 2, deckY + 1, dz0 + 1, B.of("barrel"));
		for (int x = dx0 + 2; x < dx1; x += 3) s.set(x, deckY + 3, dz0, B.face("lantern", Direction.DOWN));
		// junk under the house
		s.set(hx0 + 1, y, hz0 + 2, B.of("barrel"));
		s.set(hx0 + 2, y, hz0 + 2, B.of("cauldron"));
		s.set(hx1 - 1, y, hz1 - 1, B.of("chest"));
		s.set(hx1 - 2, y, hz1 - 1, B.of("composter"));
		// palms and chain link fence around the lot
		Deco.palm(s, 1, y, 1);
		Deco.palm(s, W - 2, y, D - 2);
		if (D > hz1 + 4) Deco.palm(s, 2, y, D - 3);
		for (int x = 0; x < W; x++) s.set(x, y, D - 1, B.of("iron_bars"));
		for (int z = 0; z < D; z++) { s.set(0, y, z, B.of("iron_bars")); s.set(W - 1, y, z, B.of("iron_bars")); }
		// gravel driveway in front for the truck
		s.fill(W / 2 - 1, y - 1, 0, W / 2 + 2, y - 1, dz0 - 1, B.of("gravel"));
	}

	// ------------------------------------------------------------ Lucia's home (Trailer 2 interiors)

	/** Small dusty-pink stucco bungalow: living room with long sofa, rattan chair by the window, green kitchen. */
	static void luciaHome(Site s) {
		int W = s.W, D = s.D, y = s.baseY;
		Deco.grass(s, y);
		int hw = Math.min(16, W - 2), hd = Math.min(11, D - 4);
		if (hw < 10 || hd < 7) { Kinds.house(s); return; }
		int hx0 = (W - hw) / 2, hx1 = hx0 + hw - 1, hz0 = Math.max(2, D - hd - 2), hz1 = hz0 + hd - 1;
		Style st = new Style();
		Style.common(s, st);
		st.wall = B.of("pink_terracotta");
		st.accent = B.of("white_terracotta");
		st.trim = B.of("white_concrete");
		st.glass = B.of("glass_pane");
		st.roof = B.of("smooth_stone");
		st.floor = B.of("stripped_oak_wood");
		st.base = B.of("smooth_stone");
		st.inWall = B.of("white_terracotta");
		Shell.Spec sp = new Shell.Spec(s);
		sp.x0 = hx0; sp.z0 = hz0; sp.x1 = hx1; sp.z1 = hz1;
		sp.floors = 1;
		sp.planGround = false;
		sp.entranceDoor = "oak_door";
		Shell.Result r = Shell.build(s, st, sp);
		s.bounds(hx0, hz0, hx1, hz1);
		int ix0 = hx0 + 1, ix1 = hx1 - 1, iz0 = hz0 + 1, iz1 = hz1 - 1;
		int split = ix0 + (ix1 - ix0) * 3 / 5;   // living | kitchen + bedroom
		int bsplit = iz0 + (iz1 - iz0) / 2;
		for (int z = iz0; z <= iz1; z++) s.fill(split, y, z, split, y + 2, z, st.inWall);
		for (int x = split + 1; x <= ix1; x++) s.fill(x, y, bsplit, x, y + 2, bsplit, st.inWall);
		s.door(split, y, iz0 + 1, "oak_door", Direction.EAST, false);
		s.door(split + 2, y, bsplit, "oak_door", Direction.SOUTH, false);
		List<int[]> doors = List.of(new int[] {r.doorX, hz0}, new int[] {split, iz0 + 1}, new int[] {split + 2, bsplit});
		// living room
		Room living = new Room(ix0, iz0, split - 1, iz1, y, 4);
		living.blockDoors(doors);
		Interiors.floor(s, living, B.of("stripped_oak_wood"));
		Interiors.rug(s, living, "white");
		living.type = "lucia_living";
		BuildCommand.record(s, living);
		// long sofa against the back wall, coffee table, lamp
		int sz = iz1;
		for (int x = ix0 + 1; x <= Math.min(split - 2, ix0 + 4); x++) { s.set(x, y, sz, B.stair("light_gray_wool_stairs", Direction.SOUTH, false)); living.take(x, sz); }
		for (int x = ix0 + 2; x <= Math.min(split - 3, ix0 + 3); x++) { s.set(x, y, sz - 2, B.trap("dark_oak_trapdoor", Direction.NORTH, false, true)); living.take(x, sz - 2); }
		s.set(ix0 + 3, y + 1, sz - 2, B.of("potted_white_tulip"));
		s.set(ix0, y, sz, B.slab("dark_oak_slab", true)); s.set(ix0, y + 1, sz, B.of("lantern")); living.take(ix0, sz);
		// rattan chair by the front window
		s.set(ix0, y, iz0 + 1, B.stair("bamboo_mosaic_stairs", Direction.WEST, false)); living.take(ix0, iz0 + 1);
		s.set(ix0, y, iz0, B.of("potted_fern")); living.take(ix0, iz0);
		// TV on a low unit facing the sofa
		for (int x = ix0 + 1; x <= Math.min(split - 2, ix0 + 3); x++) {
			if (!living.free(s, x, iz0)) continue;
			s.set(x, y, iz0, B.slab("spruce_slab", true)); s.set(x, y + 1, iz0, B.of("black_concrete")); living.take(x, iz0);
		}
		Interiors.shelves(s, living, st, 2);
		Interiors.plant(s, living);
		s.set((ix0 + split) / 2, y + 2, (iz0 + iz1) / 2, B.hanging("lantern"));
		// kitchen with green cabinets (front right)
		Room kitchen = new Room(split + 1, iz0, ix1, bsplit - 1, y, 4);
		kitchen.blockDoors(doors);
		kitchen.type = "kitchen";
		BuildCommand.record(s, kitchen);
		Interiors.checker(s, kitchen, B.of("white_concrete"), B.of("light_gray_concrete"));
		st.cabinet = "green_terracotta"; st.wood = "oak";
		Interiors.kitchen(s, kitchen, st);
		Interiors.lights(s, kitchen, Interiors.rodDown());
		// bedroom (back right)
		Room bed = new Room(split + 1, bsplit + 1, ix1, iz1, y, 4);
		bed.blockDoors(doors);
		bed.type = "bedroom";
		BuildCommand.record(s, bed);
		Interiors.floor(s, bed, B.of("oak_planks"));
		Interiors.bed(s, bed, "magenta", bed.w() >= 4);
		Interiors.wardrobe(s, bed);
		Interiors.plant(s, bed);
		Interiors.lights(s, bed, B.hanging("lantern"));
		s.clearBounds();
		// porch, path, palm, chain link
		for (int z = 0; z < hz0; z++) s.set(r.doorX, y - 1, z, B.of("smooth_stone"));
		s.set(r.doorX + 1, y + 2, hz0 - 1, B.face("lantern", Direction.DOWN));
		Deco.palm(s, 1, y, 1);
		for (int x = 0; x < W; x++) if (x != r.doorX) s.set(x, y, 0, B.of("iron_bars"));
	}

	// ------------------------------------------------------------ Extended Look robbery: gas station + Food Mart

	static void foodMart(Site s) {
		int W = s.W, D = s.D, y = s.baseY;
		if (W < 16 || D < 16) { Kinds.gasStation(s); return; }
		Deco.asphalt(s, 0, 0, W - 1, D - 1, y);
		Style st = new Style();
		Style.common(s, st);
		st.wall = B.of("white_concrete");
		st.accent = B.of("light_gray_concrete");
		st.trim = B.of("blue_concrete");
		st.glass = B.of("glass");
		st.roof = B.of("smooth_stone");
		st.floor = B.of("smooth_quartz");
		st.inWall = B.of("white_concrete");
		st.doorName = "birch_door";
		int storeD = Math.min(14, D / 2);
		Shell.Spec sp = new Shell.Spec(s);
		sp.x0 = 2; sp.x1 = Math.min(W - 3, 2 + Math.max(14, W * 2 / 3)); sp.z0 = D - storeD; sp.z1 = D - 1;
		sp.floors = 1; sp.groundH = 5; sp.storefront = true; sp.planGround = false;
		sp.entranceDoor = "birch_door"; sp.doubleDoor = true;
		Shell.Result r = Shell.build(s, st, sp);
		s.bounds(sp.x0, sp.z0, sp.x1, sp.z1);
		// fascia: blue band with red stripe, like the Extended Look storefront
		for (int x = sp.x0; x <= sp.x1; x++) {
			int fz = Shell.frontZ(s, x, sp);
			if (fz < 0) continue;
			s.set(x, y + 3, fz, B.of("red_concrete"));
			s.set(x, y + 4, fz, B.of("light_blue_concrete"));
			s.set(x, y + 5, fz, B.of("light_blue_concrete"));
		}
		// teal stripe around the inside walls
		Deco.neonRing(s, y + 2, B.of("cyan_terracotta"), sp.x0, sp.z0 + 1, sp.x1, sp.z1);
		int ix0 = sp.x0 + 1, ix1 = sp.x1 - 1, iz0 = sp.z0 + 1, iz1 = sp.z1 - 1;
		// back room
		int bz = iz1 - 2;
		for (int x = ix0; x <= ix1; x++) s.fill(x, y, bz, x, y + 3, bz, st.inWall);
		s.door(ix1 - 1, y, bz, "birch_door", Direction.SOUTH, false);
		Room store = new Room(ix0, bz + 1, ix1, iz1, y, 5);
		store.type = "storage";
		store.blockDoors(List.of(new int[] {ix1 - 1, bz}));
		Interiors.furnish(s, store, st);
		Room shop = new Room(ix0, iz0, ix1, bz - 1, y, 5);
		shop.blockDoors(List.of(new int[] {r.doorX, sp.z0}, new int[] {r.doorX - 1, sp.z0}, new int[] {ix1 - 1, bz}));
		Interiors.floor(s, shop, B.of("smooth_quartz"));
		shop.type = "food_mart";
		BuildCommand.record(s, shop);
		// left wall: glass fridges with green/blue signage boards above
		for (int z = iz0 + 1; z <= bz - 2; z++) {
			s.set(ix0, y, z, B.of("iron_block"));
			s.set(ix0, y + 1, z, ((z & 1) == 0) ? B.of("sea_lantern") : B.of("light_blue_stained_glass"));
			s.set(ix0, y + 2, z, ((z & 1) == 0) ? B.of("sea_lantern") : B.of("light_blue_stained_glass"));
			s.set(ix0, y + 3, z, (z % 4 < 2) ? B.of("verdant_froglight") : B.of("blue_concrete"));
			shop.take(ix0, z);
		}
		// chip racks: colourful rows on the right
		for (int x = ix0 + 3; x <= ix1 - 3; x += 3) for (int z = iz0 + 2; z <= bz - 3; z++) {
			if (!shop.free(s, x, z)) continue;
			shop.take(x, z);
			String c = Style.FABRIC[Math.floorMod(x * 5 + z * 7, Style.FABRIC.length)];
			s.set(x, y, z, B.of("white_concrete"));
			s.set(x, y + 1, z, B.c(c, "glazed_terracotta"));
			s.set(x, y + 2, z, B.c(Style.FABRIC[Math.floorMod(x + z * 3, Style.FABRIC.length)], "wool_slab"));
		}
		// counter at the back with register and cigarettes shelf
		for (int x = ix1 - 5; x <= ix1 - 1; x++) {
			s.set(x, y, bz - 2, B.of("polished_deepslate"));
			shop.take(x, bz - 2); shop.take(x, bz - 1);
			s.set(x, y + 1, bz, B.face("spruce_shelf", Direction.NORTH));
			s.set(x, y + 2, bz, B.face("spruce_shelf", Direction.NORTH));
		}
		s.set(ix1 - 4, y + 1, bz - 2, B.register(Direction.NORTH));
		s.set(ix1 - 2, y + 1, bz - 2, B.of("candle"));
		// ATM and drinks cooler by the door, cafe table
		int ax = Math.min(ix1, r.doorX + 2);
		s.set(ax, y, iz0, B.atm(Direction.SOUTH)); shop.take(ax, iz0);
		s.set(ax + 1, y, iz0, B.of("blue_concrete")); s.set(ax + 1, y + 1, iz0, B.of("sea_lantern")); shop.take(ax + 1, iz0);
		int tx = Math.max(ix0 + 2, r.doorX - 3);
		s.set(tx, y, iz0 + 1, B.of("dark_oak_fence")); s.set(tx, y + 1, iz0 + 1, B.of("white_carpet"));
		s.set(tx + 1, y, iz0 + 1, B.stair("birch_stairs", Direction.EAST, false));
		Interiors.lights(s, shop, B.of("sea_lantern"));
		s.clearBounds();
		// canopy with four pumps and red bollards
		int cz1 = sp.z0 - 3;
		if (cz1 >= 7) {
			int cx0 = 1, cx1 = W - 2, cz0 = Math.max(1, cz1 - 9);
			Deco.canopy(s, cx0, cz0, cx1, cz1, y, 5, B.of("white_concrete"), B.of("red_concrete"), B.of("sea_lantern"));
			int mz = (cz0 + cz1) / 2;
			for (int x = cx0 + 3; x <= cx1 - 3; x += 5) {
				s.fill(x - 1, y - 1, mz - 1, x + 1, y - 1, mz + 1, B.of("smooth_stone"));
				Deco.pump(s, x, y, mz, Direction.NORTH);
				Deco.bollard(s, x - 1, y, mz - 1);
				Deco.bollard(s, x + 1, y, mz + 1);
			}
		}
		for (int x = sp.x0; x <= sp.x1; x += 3) if (Math.abs(x - r.doorX) > 1) Deco.bollard(s, x, y, sp.z0 - 1);
		Deco.pylon(s, W - 2, 1, y, 7, B.of("red_concrete"), B.of("sea_lantern"));
		// payphone and ice chest by the wall
		s.set(sp.x1 + 1 < W ? sp.x1 + 1 : sp.x0 - 1, y, sp.z0 + 1, B.of("white_concrete"));
	}

	// ------------------------------------------------------------ club

	static void clubVice(Site s) {
		Kinds.club(s);
		// extra: magenta entrance canopy and palms with uplights
		int W = s.W;
		int cx = W / 2;
		// rope line along the forecourt and a tall neon blade sign
		for (int x = 1; x < cx - 4; x += 2) { s.set(x, s.baseY, 2, B.of("gold_block")); s.set(x + 1, s.baseY, 2, B.axis("iron_chain", Direction.Axis.X)); }
		s.fill(W - 2, s.baseY, 1, W - 2, s.baseY + 9, 1, B.of("polished_blackstone_wall"));
		s.fill(W - 2, s.baseY + 5, 2, W - 2, s.baseY + 11, 2, B.of("pearlescent_froglight"));
		s.fill(W - 2, s.baseY + 5, 3, W - 2, s.baseY + 11, 3, B.of("magenta_stained_glass"));
	}

	// ------------------------------------------------------------ gym

	static void gym(Site s) {
		Style st = Style.pastel(s);
		st.wall = B.of("white_concrete");
		st.accent = B.of("red_concrete");
		st.glass = B.of("glass");
		st.neon = B.of("sea_lantern");
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = 1; sp.groundH = 6; sp.storefront = true; sp.planGround = false;
		sp.doubleDoor = true; sp.entranceDoor = "birch_door";
		Shell.Result r = Shell.build(s, st, sp);
		int y = s.baseY;
		Room g = new Room(sp.x0 + 1, sp.z0 + 1, sp.x1 - 1, sp.z1 - 1, y, 6);
		g.blockDoors(List.of(new int[] {r.doorX, sp.z0}, new int[] {r.doorX - 1, sp.z0}));
		g.type = "gym";
		BuildCommand.record(s, g);
		// rubber floor with stripes
		for (int x = g.x0; x <= g.x1; x++) for (int z = g.z0; z <= g.z1; z++) s.set(x, y - 1, z, (z % 6 == 0) ? B.of("yellow_concrete") : B.of("gray_concrete"));
		// boxing ring in a back corner
		int rx0 = g.x1 - 6, rz0 = g.z1 - 6;
		if (rx0 > g.x0 + 3 && rz0 > g.z0 + 3) {
			s.fill(rx0, y, rz0, rx0 + 6, y, rz0 + 6, B.slab("smooth_stone_slab", false));
			for (int x = rx0; x <= rx0 + 6; x++) for (int z = rz0; z <= rz0 + 6; z++) {
				g.take(x, z); g.take(x - 1, z); g.take(x, z - 1);
				boolean edgeX = x == rx0 || x == rx0 + 6, edgeZ = z == rz0 || z == rz0 + 6;
				if (edgeX && edgeZ) s.fill(x, y + 1, z, x, y + 3, z, B.of("red_concrete"));
				else if (edgeX || edgeZ) { s.set(x, y + 2, z, B.of("iron_bars")); s.set(x, y + 3, z, B.of("iron_bars")); }
				else s.set(x, y + 1, z, B.of(((x + z) & 1) == 0 ? "blue_carpet" : "light_blue_carpet"));
			}
		}
		// dumbbell rack along a wall: heavy cores on slabs
		Room.Slot rack = g.wall(s, Math.min(6, g.w() - 4), 1);
		if (rack != null) for (int i = 0; i < Math.min(6, g.w() - 4); i++) {
			s.set(Room.ax(rack, i, 0), y, Room.az(rack, i, 0), B.slab("polished_blackstone_slab", true));
			s.set(Room.ax(rack, i, 0), y + 1, Room.az(rack, i, 0), B.of("heavy_core"));
		}
		// weight benches: bench (slab) with bar on posts (walls + chain)
		for (int i = 0; i < 4; i++) {
			int[] p = g.center(s, 1, 3);
			if (p == null) break;
			s.set(p[0], y, p[1], B.of("polished_blackstone_wall"));
			s.set(p[0], y, p[1] + 1, B.slab("black_wool_slab", false));
			s.set(p[0], y, p[1] + 2, B.slab("black_wool_slab", false));
			s.set(p[0], y + 1, p[1], B.axis("iron_chain", Direction.Axis.X));
		}
		// treadmills by the front windows
		for (int x = g.x0 + 1; x <= g.x1 - 1; x += 2) {
			if (!g.free(s, x, g.z0) || !g.free(s, x, g.z0 + 1)) continue;
			g.take(x, g.z0); g.take(x, g.z0 + 1);
			s.set(x, y, g.z0, B.of("polished_blackstone_wall"));
			s.set(x, y + 1, g.z0, B.trap("iron_trapdoor", Direction.SOUTH, true, false));
			s.set(x, y, g.z0 + 1, B.of("black_carpet"));
		}
		// mirror wall (glass) on one side
		for (int z = g.z0 + 2; z <= g.z1 - 2; z++) if (g.free(s, g.x0, z)) s.fill(g.x0, y + 1, z, g.x0, y + 3, z, B.of("light_gray_stained_glass"));
		Interiors.lights(s, g, B.of("sea_lantern"));
		s.set(g.x0 + 1, y, g.z0 + 1, B.of("iron_block"));
		s.set(g.x0 + 1, y + 1, g.z0 + 1, B.of("light_blue_stained_glass"));
		Deco.neonFront(s, 1, s.W - 2, r.roofY, B.of("redstone_block"));
		Deco.neonFront(s, 2, s.W - 3, r.roofY - 1, st.neon);
	}

	// ------------------------------------------------------------ office floor (Trailer 2 office)

	static void officeFloor(Site s) {
		Style st = new Style();
		Style.common(s, st);
		st.glass = B.of("gray_stained_glass");
		st.wall = B.of("black_concrete");
		st.trim = B.of("polished_blackstone");
		st.accent = st.trim;
		st.roof = B.of("gray_concrete");
		st.base = B.of("polished_blackstone");
		st.inWall = B.of("gray_stained_glass");
		st.neon = B.of("redstone_block");
		Shell.Spec sp = new Shell.Spec(s);
		sp.floors = Math.max(4, s.floors);
		sp.windows = Shell.Windows.CURTAIN;
		sp.elevator = true;
		sp.minRoom = 4; sp.maxRoom = 16;
		sp.groundBig = new String[] {"lobby", "meeting"};
		sp.groundSmall = new String[] {"storage"};
		sp.upperBig = new String[] {"trading", "trading", "meeting", "office"};
		sp.upperSmall = new String[] {"bath", "storage"};
		sp.storefront = true; sp.groundH = 5; sp.doubleDoor = true; sp.entranceDoor = "dark_oak_door";
		Shell.Result r = Shell.build(s, st, sp);
		Deco.neonRing(s, r.roofY, B.of("redstone_block"), sp.x0, sp.z0, sp.x1, sp.z1);
		Deco.acUnits(s, sp.x0, sp.z0, sp.x1, sp.z1, r.roofY + 1);
	}

	// ------------------------------------------------------------ motel room (Extended Look pink room)

	static void motelRoom(Site s, Room r) {
		int y = r.y;
		// pink walls inside
		for (int x = r.x0 - 1; x <= r.x1 + 1; x++) for (int z = r.z0 - 1; z <= r.z1 + 1; z++) {
			if (r.contains(x, z)) continue;
			for (int k = 0; k < 3; k++) {
				BlockState cur = s.get(x, y + k, z);
				if (!cur.isAir() && !(cur.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) && !(cur.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock)) s.set(x, y + k, z, B.of("pink_concrete"));
			}
		}
		Interiors.floor(s, r, B.of("gray_concrete"));
		for (int x = r.x0; x <= r.x1; x++) for (int z = r.z0; z <= r.z1; z++) if (Math.floorMod(x * 3 + z * 5, 4) == 0) s.set(x, y, z, B.of("pink_carpet"));
		r.type = "motel_room";
		BuildCommand.record(s, r);
		Interiors.bed(s, r, "magenta", true);
		Interiors.tv(s, r, false);
		// mini fridge with a teddy bear on top
		Room.Slot f = r.wall(s, 1, 1);
		if (f != null) { s.set(f.x(), y, f.z(), B.of("white_concrete")); s.set(f.x(), y + 1, f.z(), B.slab("brown_wool_slab", false)); }
		// vanity mirror
		Room.Slot v = r.wall(s, 1, 1);
		if (v != null) { s.set(v.x(), y, v.z(), B.slab("pink_wool_slab", true)); s.set(v.x(), y + 1, v.z(), B.of("glass_pane")); }
		// pink LED strip under the ceiling along the walls
		for (int x = r.x0; x <= r.x1; x++) { s.set(x, y + 2, r.z0, B.of("pearlescent_froglight")); s.set(x, y + 2, r.z1, B.of("pearlescent_froglight")); }
		// magenta curtains on the window
		for (int x = r.x0 + 2; x <= r.x1; x++) if (s.get(x, y + 1, r.z0 - 1).getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock) s.set(x, y + 1, r.z0 - 1, B.of("magenta_stained_glass_pane"));
	}
}
