package dev.leonida.build;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.leonida.core.api.BuildingGenerator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Debug command: /leonida_build place|showcase|tp|tproom|list. Places buildings into the live
 * level through the same chunk-clipped code path as world generation.
 */
public final class BuildCommand {
	private BuildCommand() {}

	/** Recorded room of a placed building: world bounds, floor y, type. */
	record RoomRec(String type, int x0, int z0, int x1, int z1, int y) {}

	/** Showcase entry: name, footprint and recorded rooms. */
	static final class Entry {
		final String name;
		final int x0, z0, x1, z1, y, floors;
		final Map<String, RoomRec> rooms = new LinkedHashMap<>();
		Entry(String name, int x0, int z0, int x1, int z1, int y, int floors) { this.name = name; this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.y = y; this.floors = floors; }

		/** Rooms with hand-designed landmark programs first, then by area. */
		List<RoomRec> sorted() {
			List<RoomRec> l = new ArrayList<>(rooms.values());
			l.sort(java.util.Comparator.comparingInt((RoomRec r) -> FEATURED.contains(r.type) ? 0 : 1)
				.thenComparingInt(r -> -(r.x1 - r.x0 + 1) * (r.z1 - r.z0 + 1)));
			return l;
		}
	}

	private static final java.util.Set<String> FEATURED = java.util.Set.of("motel_room", "lucia_living", "food_mart", "gym", "club", "trading", "shop", "jason_kitchen");

	static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
	private static final ThreadLocal<Entry> RECORDING = new ThreadLocal<>();

	/** Number of recorded rooms of a placed building (game tests). */
	public static int roomCount(String name) {
		Entry e = ENTRIES.get(name);
		return e == null ? 0 : e.rooms.size();
	}

	static void record(Site s, Room r) {
		Entry e = RECORDING.get();
		if (e == null) return;
		int ax = s.wx(r.x0, r.z0), az = s.wz(r.x0, r.z0), bx = s.wx(r.x1, r.z1), bz = s.wz(r.x1, r.z1);
		RoomRec rec = new RoomRec(r.type, Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz), r.y);
		e.rooms.putIfAbsent(rec.x0 + "," + rec.y + "," + rec.z0, rec);
	}

	/** Showcase program: name, kind, width, depth, floors, landmark. */
	private record Spec(String name, BuildingGenerator.Kind kind, int w, int d, int floors, String landmark) {}

	private static final List<Spec> KINDS = List.of(
		new Spec("house", BuildingGenerator.Kind.HOUSE, 20, 24, 2, null),
		new Spec("apartment", BuildingGenerator.Kind.APARTMENT, 16, 14, 4, null),
		new Spec("tower", BuildingGenerator.Kind.TOWER, 18, 18, 12, null),
		new Spec("shop", BuildingGenerator.Kind.SHOP, 12, 12, 1, null),
		new Spec("gas_station", BuildingGenerator.Kind.GAS_STATION, 30, 26, 1, null),
		new Spec("motel", BuildingGenerator.Kind.MOTEL, 30, 16, 2, null),
		new Spec("club", BuildingGenerator.Kind.CLUB, 20, 18, 1, null),
		new Spec("office", BuildingGenerator.Kind.OFFICE, 18, 16, 6, null),
		new Spec("warehouse", BuildingGenerator.Kind.WAREHOUSE, 24, 20, 1, null),
		new Spec("bank", BuildingGenerator.Kind.BANK, 18, 14, 2, null),
		new Spec("hotel", BuildingGenerator.Kind.HOTEL, 20, 16, 6, null),
		new Spec("mall", BuildingGenerator.Kind.MALL, 40, 30, 2, null),
		new Spec("police", BuildingGenerator.Kind.POLICE, 22, 16, 2, null),
		new Spec("hospital", BuildingGenerator.Kind.HOSPITAL, 24, 18, 4, null),
		new Spec("marina", BuildingGenerator.Kind.MARINA, 24, 16, 1, null),
		new Spec("gun_shop", BuildingGenerator.Kind.SHOP, 14, 16, 1, null));

	private static final List<Spec> LANDMARKS = List.of(
		new Spec("jason_house", BuildingGenerator.Kind.LANDMARK, 26, 24, 2, "jason_house"),
		new Spec("lucia_home", BuildingGenerator.Kind.LANDMARK, 20, 17, 1, "lucia_home"),
		new Spec("el_gas_station", BuildingGenerator.Kind.LANDMARK, 30, 30, 1, "el_gas_station"),
		new Spec("club_vice", BuildingGenerator.Kind.LANDMARK, 22, 18, 1, "club_vice"),
		new Spec("gym", BuildingGenerator.Kind.LANDMARK, 20, 18, 1, "gym"),
		new Spec("office_floor", BuildingGenerator.Kind.LANDMARK, 18, 16, 6, "office_floor"),
		new Spec("motel_room", BuildingGenerator.Kind.LANDMARK, 30, 16, 2, "motel_room"));

	static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("leonida_build").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("place")
				.then(Commands.argument("kind", StringArgumentType.word())
					.then(Commands.argument("w", IntegerArgumentType.integer(5, 96))
						.then(Commands.argument("d", IntegerArgumentType.integer(5, 96))
							.then(Commands.argument("floors", IntegerArgumentType.integer(1, 80))
								.executes(c -> place(c, null))
								.then(Commands.argument("landmark", StringArgumentType.word()).executes(c -> place(c, StringArgumentType.getString(c, "landmark")))))))))
			.then(Commands.literal("showcase").executes(BuildCommand::showcase))
			.then(Commands.literal("street").then(Commands.argument("name", StringArgumentType.word()).executes(BuildCommand::street)))
			.then(Commands.literal("view").then(Commands.argument("name", StringArgumentType.word())
				.then(Commands.argument("preset", IntegerArgumentType.integer(0, 3)).executes(BuildCommand::view))))
			.then(Commands.literal("list").executes(BuildCommand::list))
			.then(Commands.literal("hash").then(Commands.argument("cx", IntegerArgumentType.integer()).then(Commands.argument("cz", IntegerArgumentType.integer())
				.then(Commands.argument("n", IntegerArgumentType.integer(1, 32)).executes(BuildCommand::hash)))))
			.then(Commands.literal("tp").then(Commands.argument("name", StringArgumentType.word()).executes(BuildCommand::tp)))
			.then(Commands.literal("tppad").then(Commands.argument("name", StringArgumentType.word()).executes(BuildCommand::tpPad)))
			.then(Commands.literal("tproom").then(Commands.argument("name", StringArgumentType.word())
				.executes(c -> tpRoom(c, 0))
				.then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(c -> tpRoom(c, IntegerArgumentType.getInteger(c, "index")))))));
	}

	/** Debug: block-state hash of an n x n chunk square (benchmark identity checks). Logs one line per chunk and a total. */
	private static int hash(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		int cx0 = IntegerArgumentType.getInteger(c, "cx"), cz0 = IntegerArgumentType.getInteger(c, "cz"), n = IntegerArgumentType.getInteger(c, "n");
		long total = 1469598103934665603L;
		BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
		for (int cx = cx0; cx < cx0 + n; cx++) for (int cz = cz0; cz < cz0 + n; cz++) {
			net.minecraft.world.level.chunk.LevelChunk ch = level.getChunk(cx, cz);
			long h = 1469598103934665603L;
			for (int y = level.getMinY(); y < level.getMinY() + level.getHeight(); y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
				h = (h ^ net.minecraft.world.level.block.Block.getId(ch.getBlockState(mp.set((cx << 4) + x, y, (cz << 4) + z)))) * 1099511628211L;
			}
			System.out.println("[leonida-build-hash] chunk " + cx + "," + cz + " " + Long.toHexString(h));
			total = (total ^ h) * 1099511628211L;
		}
		long t = total;
		System.out.println("[leonida-build-hash] total " + Long.toHexString(t));
		c.getSource().sendSuccess(() -> Component.literal("hash " + Long.toHexString(t)), false);
		return 1;
	}

	private static int place(CommandContext<CommandSourceStack> c, String landmark) {
		CommandSourceStack src = c.getSource();
		String k = StringArgumentType.getString(c, "kind").toUpperCase(Locale.ROOT);
		BuildingGenerator.Kind kind;
		try { kind = BuildingGenerator.Kind.valueOf(k); } catch (IllegalArgumentException e) { src.sendFailure(Component.literal("unknown kind " + k)); return 0; }
		BlockPos p = BlockPos.containing(src.getPosition());
		int w = IntegerArgumentType.getInteger(c, "w"), dd = IntegerArgumentType.getInteger(c, "d");
		String name = landmark != null ? landmark : k.toLowerCase(Locale.ROOT) + "_" + ENTRIES.size();
		place(src.getLevel(), name, kind, p.getX() + 2, p.getZ() + 4, w, dd, p.getY(), IntegerArgumentType.getInteger(c, "floors"), landmark);
		src.sendSuccess(() -> Component.literal("placed " + name), false);
		return 1;
	}

	static Entry place(ServerLevel level, String name, BuildingGenerator.Kind kind, int x0, int z0, int w, int d, int y, int floors, String landmark) {
		return place(level, name, kind, x0, z0, w, d, y, floors, landmark, BuildingGenerator.Footprint.STYLE_AUTO, 0);
	}

	static Entry place(ServerLevel level, String name, BuildingGenerator.Kind kind, int x0, int z0, int w, int d, int y, int floors, String landmark,
			int style, int height) {
		int x1 = x0 + w - 1, z1 = z0 + d - 1;
		long id = (long) name.hashCode() * 31L + x0 * 7919L + z0;
		BuildingGenerator.Footprint fp = style >= 0
			? new BuildingGenerator.Footprint(id, Geo.rect(x0, z0, x1, z1), List.of(), y, floors, height, style, kind, landmark)
			: new BuildingGenerator.Footprint(id, Geo.rect(x0, z0, x1, z1), y, floors, kind, landmark);
		Entry e = new Entry(name, x0, z0, x1, z1, y, floors);
		ENTRIES.put(name, e);
		List<Canvas> canvases = new ArrayList<>();
		RECORDING.set(e);
		Site.FORCE_FRONT.set(Direction.NORTH);
		if (name.equals("gun_shop")) Kinds.FORCE_SHOP.set("gun_shop");
		try {
			for (int cx = (x0 - 3) >> 4; cx <= (x1 + 3) >> 4; cx++) for (int cz = (z0 - 3) >> 4; cz <= (z1 + 3) >> 4; cz++) {
				Canvas cv = new Canvas.LevelCanvas(level, cx, cz);
				Site s = Site.create(cv, fp);
				if (s == null) continue;
				Kinds.build(s);
				canvases.add(cv);
			}
		} finally {
			RECORDING.remove();
			Site.FORCE_FRONT.remove();
			Kinds.FORCE_SHOP.remove();
		}
		for (Canvas cv : canvases) cv.finish();
		return e;
	}

	private static int showcase(CommandContext<CommandSourceStack> c) {
		CommandSourceStack src = c.getSource();
		BlockPos p = BlockPos.containing(src.getPosition());
		int y = p.getY();
		int x = p.getX() + 4;
		for (Spec s : KINDS) {
			place(src.getLevel(), s.name, s.kind, x, p.getZ() + 20, s.w, s.d, y, s.floors, s.landmark);
			x += s.w + 14;
		}
		x = p.getX() + 4;
		for (Spec s : LANDMARKS) {
			place(src.getLevel(), s.name, s.kind, x, p.getZ() + 90, s.w, s.d, y, s.floors, s.landmark);
			x += s.w + 14;
		}
		src.sendSuccess(() -> Component.literal("showcase placed: " + ENTRIES.size() + " buildings"), false);
		return 1;
	}

	/** Street demo rows: style, kind, width, depth, height in blocks. */
	private record Lot(int style, BuildingGenerator.Kind kind, int w, int d, int h) {}

	private static final List<Lot> DOWNTOWN = List.of(
		new Lot(1, BuildingGenerator.Kind.TOWER, 20, 20, 64), new Lot(0, BuildingGenerator.Kind.TOWER, 22, 22, 110),
		new Lot(4, BuildingGenerator.Kind.OFFICE, 16, 18, 29), new Lot(1, BuildingGenerator.Kind.TOWER, 18, 18, 82),
		new Lot(0, BuildingGenerator.Kind.TOWER, 20, 20, 78), new Lot(2, BuildingGenerator.Kind.SHOP, 12, 14, 10),
		new Lot(1, BuildingGenerator.Kind.APARTMENT, 18, 16, 46));
	private static final List<Lot> OCEAN_DRIVE = List.of(
		new Lot(2, BuildingGenerator.Kind.HOTEL, 14, 16, 13), new Lot(2, BuildingGenerator.Kind.HOTEL, 12, 16, 10),
		new Lot(2, BuildingGenerator.Kind.HOTEL, 16, 18, 16), new Lot(2, BuildingGenerator.Kind.SHOP, 10, 14, 7),
		new Lot(2, BuildingGenerator.Kind.HOTEL, 14, 16, 13), new Lot(3, BuildingGenerator.Kind.SHOP, 12, 14, 7),
		new Lot(2, BuildingGenerator.Kind.HOTEL, 13, 16, 19), new Lot(2, BuildingGenerator.Kind.HOTEL, 12, 16, 10));

	/** A row of buildings on one side of a street, road and sidewalks in front: downtown or oceandrive. */
	private static int street(CommandContext<CommandSourceStack> c) {
		CommandSourceStack src = c.getSource();
		String name = StringArgumentType.getString(c, "name");
		boolean ocean = name.equals("oceandrive");
		List<Lot> lots = ocean ? OCEAN_DRIVE : DOWNTOWN;
		ServerLevel level = src.getLevel();
		BlockPos p = BlockPos.containing(src.getPosition());
		int y = p.getY(), z0 = p.getZ() + 24, x = p.getX() + 4, gap = ocean ? 1 : 5;
		int x0 = x, maxH = 0;
		for (int i = 0; i < lots.size(); i++) {
			Lot l = lots.get(i);
			int floors = Math.max(1, l.h / (l.style == 0 || l.style == 4 ? 4 : 3));
			place(level, name + "_" + i, l.kind, x, z0, l.w, l.d, y, floors, null, l.style, l.h);
			maxH = Math.max(maxH, l.h);
			x += l.w + gap;
		}
		int x1 = x - gap - 1;
		// sidewalk (3), road (10), far sidewalk (3), then beach for Ocean Drive
		net.minecraft.world.level.block.state.BlockState walk = net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState();
		net.minecraft.world.level.block.state.BlockState road = B.of("black_concrete");
		net.minecraft.world.level.block.state.BlockState line = B.of("yellow_concrete");
		net.minecraft.world.level.block.state.BlockState sand = net.minecraft.world.level.block.Blocks.SAND.defaultBlockState();
		try {
			walk = dev.leonida.look.LookBlocks.sidewalk(0, 0, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE);
			road = dev.leonida.look.LookBlocks.asphalt(dev.leonida.look.LookBlocks.Asphalt.values()[0]).defaultBlockState();
			sand = dev.leonida.look.LookBlocks.BEACH_SAND.defaultBlockState();
		} catch (RuntimeException ignored) {}
		for (int xx = x0 - 8; xx <= x1 + 8; xx++) {
			for (int k = 1; k <= 16; k++) {
				int zz = z0 - k;
				net.minecraft.world.level.block.state.BlockState b = k <= 3 || k >= 14 ? walk : (k == 8 && (xx & 3) < 2 ? line : road);
				level.setBlock(new BlockPos(xx, y - 1, zz), b, 2);
			}
			if (ocean) for (int k = 17; k <= 34; k++) level.setBlock(new BlockPos(xx, y - 1, z0 - k), sand, 2);
		}
		// palms on both sidewalks
		for (int xx = x0 - 4; xx <= x1 + 4; xx += ocean ? 9 : 12) { palm(level, xx, y, z0 - 2, 10 + (xx & 3)); if (Math.abs(xx + 4 - (x0 - 6)) > 5) palm(level, xx + 4, y, z0 - 15, 11 + (xx & 1)); }
		Entry e = new Entry(name, x0, z0, x1, z0 + 20, y, Math.max(1, maxH / 4));
		ENTRIES.put(name, e);
		src.sendSuccess(() -> Component.literal("street " + name + " placed: x " + x0 + ".." + x1 + " z " + z0), false);
		return 1;
	}

	private static final int[][] PALM_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

	/** Royal palm in the world module's shape: slim pale trunk, green crown shaft, arching drooping fronds. */
	static void palm(ServerLevel level, int x, int y, int z, int h) {
		net.minecraft.world.level.block.state.BlockState trunk = B.of("stripped_birch_log"), shaft = B.of("moss_block"), leaf = B.leaves("jungle_leaves");
		for (int k = 0; k < h - 2; k++) level.setBlock(new BlockPos(x, y + k, z), trunk, 2);
		level.setBlock(new BlockPos(x, y + h - 2, z), shaft, 2);
		level.setBlock(new BlockPos(x, y + h - 1, z), shaft, 2);
		int top = y + h, len = 5;
		level.setBlock(new BlockPos(x, top + 1, z), leaf, 2);
		int[] dy = {1, 0, 0, -1, -1, -2, -3};
		for (int i = 0; i < PALM_DIRS.length; i++) {
			int dx = PALM_DIRS[i][0], dz = PALM_DIRS[i][1];
			int l = i < 4 ? len : len - 1;
			if (((x * 31 + z * 17 + i) & 7) == 0) l--;
			for (int k = 1; k <= l; k++) {
				int yy = top + dy[Math.min(k - 1, dy.length - 1)];
				level.setBlock(new BlockPos(x + dx * k, yy, z + dz * k), leaf, 2);
				if (k == l && k > 2) level.setBlock(new BlockPos(x + dx * k, yy - 1, z + dz * k), leaf, 2);
			}
		}
	}

	/** Camera presets for street rows: 0 street level along the row, 1 across the street, 2 high oblique, 3 low looking up. */
	private static int view(CommandContext<CommandSourceStack> c) {
		Entry e = ENTRIES.get(StringArgumentType.getString(c, "name"));
		ServerPlayer pl = c.getSource().getPlayer();
		if (e == null || pl == null) return 0;
		int preset = IntegerArgumentType.getInteger(c, "preset");
		int w = e.x1 - e.x0 + 1, h = e.floors * 4;
		double cx, cy, cz, tx, ty, tz;
		switch (preset) {
			case 0 -> { cx = e.x0 - 6; cy = e.y + 2.2; cz = e.z0 - 11; tx = e.x0 + 40; ty = e.y + 8; tz = e.z0 + 1; }
			case 1 -> { cx = e.x0 + 40; cy = e.y + 14; cz = e.z0 - 34; tx = cx + 4; ty = e.y + Math.min(h, 80) * 0.4; tz = e.z0 + 8; }
			case 2 -> { cx = e.x0 - 30; cy = e.y + 70; cz = e.z0 - 55; tx = e.x0 + 40; ty = e.y + 25; tz = e.z0 + 10; }
			default -> { cx = e.x0 + 18; cy = e.y + 1.7; cz = e.z0 - 15; tx = e.x0 + 28; ty = e.y + 45; tz = e.z0 + 10; }
		}
		float yaw = (float) Math.toDegrees(Math.atan2(-(tx - cx), tz - cz));
		float pitch = (float) Math.toDegrees(Math.atan2(cy - ty, Math.hypot(tx - cx, tz - cz)));
		pl.teleportTo(c.getSource().getLevel(), cx, cy - 1.62, cz, java.util.Set.of(), yaw, pitch, false);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		StringBuilder sb = new StringBuilder();
		for (Entry e : ENTRIES.values()) sb.append(e.name).append('(').append(e.rooms.size()).append(") ");
		c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
		return ENTRIES.size();
	}

	/** Stand in front of the street side, looking at the building. */
	private static int tp(CommandContext<CommandSourceStack> c) {
		Entry e = ENTRIES.get(StringArgumentType.getString(c, "name"));
		ServerPlayer pl = c.getSource().getPlayer();
		if (e == null || pl == null) return 0;
		int w = e.x1 - e.x0 + 1, d = e.z1 - e.z0 + 1;
		int h = e.floors * 4 + 3;
		double size = Math.max(w * 0.75, h * 0.7) + 6;
		double cx = (e.x0 + e.x1) / 2.0 + 0.5 - w * 0.25;
		double cz = e.z0 - size;
		double eye = e.y + Math.min(h * 0.45, 4 + size * 0.15);
		double tx = (e.x0 + e.x1) / 2.0 + 0.5, tz = e.z0 + d * 0.4, ty = e.y + h * 0.4;
		float yaw = (float) Math.toDegrees(Math.atan2(-(tx - cx), tz - cz));
		float pitch = (float) Math.toDegrees(Math.atan2(eye - ty, Math.hypot(tx - cx, tz - cz)));
		pl.teleportTo(c.getSource().getLevel(), cx, eye - 1.62, cz, java.util.Set.of(), yaw, pitch, false);
		return 1;
	}

	/** Stand on the lowest elevator pad of a building. */
	private static int tpPad(CommandContext<CommandSourceStack> c) {
		Entry e = ENTRIES.get(StringArgumentType.getString(c, "name"));
		ServerPlayer pl = c.getSource().getPlayer();
		if (e == null || pl == null) return 0;
		ServerLevel level = c.getSource().getLevel();
		for (int y = e.y - 1; y < e.y + 8; y++) for (int x = e.x0; x <= e.x1; x++) for (int z = e.z0; z <= e.z1; z++) {
			BlockPos p = new BlockPos(x, y, z);
			if (Elevator.isPad(level, p)) {
				pl.teleportTo(level, x + 0.5, y + 0.1, z + 0.5, java.util.Set.of(), pl.getYRot(), 0f, false);
				c.getSource().sendSuccess(() -> Component.literal("pad at " + p.toShortString()), false);
				return y;
			}
		}
		c.getSource().sendFailure(Component.literal("no pad"));
		return 0;
	}

	/** Float in a corner of the n-th recorded room (spectator), looking across it. */
	private static int tpRoom(CommandContext<CommandSourceStack> c, int idx) {
		Entry e = ENTRIES.get(StringArgumentType.getString(c, "name"));
		ServerPlayer pl = c.getSource().getPlayer();
		if (e == null || pl == null || e.rooms.isEmpty()) return 0;
		List<RoomRec> rooms = e.sorted();
		RoomRec r = rooms.get(Math.floorMod(idx, rooms.size()));
		ServerLevel level = c.getSource().getLevel();
		int bx = r.x0, bz = r.z0;
		double best = Double.MAX_VALUE;
		for (int x = r.x0; x <= r.x1; x++) for (int z = r.z0; z <= r.z1; z++) {
			BlockPos f = new BlockPos(x, r.y + 1, z);
			if (!level.getBlockState(f).getCollisionShape(level, f).isEmpty() || !level.getBlockState(f.above()).getCollisionShape(level, f.above()).isEmpty()) continue;
			if (!level.getBlockState(f.above()).isAir() || !level.getBlockState(f).isAir()) continue;
			double dist = Math.hypot(x - r.x0, z - r.z0);
			if (dist < best) { best = dist; bx = x; bz = z; }
		}
		double tx = (r.x0 + r.x1) / 2.0 + 0.5 + (r.x1 - r.x0) * 0.25, tz = (r.z0 + r.z1) / 2.0 + 0.5 + (r.z1 - r.z0) * 0.25;
		float yaw = (float) Math.toDegrees(Math.atan2(-(tx - bx - 0.5), tz - bz - 0.5));
		float pitch = (float) Math.toDegrees(Math.atan2(1.4, Math.max(1.0, Math.hypot(tx - bx - 0.5, tz - bz - 0.5))));
		pl.teleportTo(level, bx + 0.5, r.y + 0.35, bz + 0.5, java.util.Set.of(), yaw, pitch, false);
		c.getSource().sendSuccess(() -> Component.literal("room " + r.type), false);
		return 1;
	}
}
