package dev.leonida.landmark;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.leonida.core.api.GenTerrain;
import dev.leonida.core.api.LandmarkGenerator;
import dev.leonida.core.api.RoadGenerator;
import dev.leonida.landmark.gen.Canvas;
import dev.leonida.landmark.gen.FakeTerrain;
import dev.leonida.landmark.gen.Site;
import dev.leonida.world.WorldModule;
import dev.leonida.world.data.CwFeatures;
import dev.leonida.world.data.CwTerrain;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Debug command: {@code /leonida_landmark place <site> [base]} builds a site into the live level through the same
 * chunk-clipped path as world generation, on a fake flat shoreline terrain (flat test worlds);
 * {@code /leonida_landmark view <site> <name>} moves the player to a camera preset (real terrain in the Leonida
 * dimension); {@code /leonida_landmark check <site>} verifies a generated site in the Leonida dimension; {@code list}.
 */
public final class LandmarkCommand {
	private LandmarkCommand() {}

	/** Terrain each site was last placed with (views need its ground). */
	private static final Map<String, GenTerrain> PLACED = new HashMap<>();

	/** Fake shoreline per site (through a point, normal pointing to land), from the baked ground mask. */
	static FakeTerrain fakeFor(Site s, int base) {
		return switch (s.id) {
			// shore near Jason's house runs SW to NE about 13 m west of the house (ground mask 4_8)
			case "jason_house" -> new FakeTerrain(base, -2364, 5520, 135, 105);
			// Brian's basin lies west of the barn, bulkhead about 16 m west of the west wall
			case "brians_marina" -> new FakeTerrain(base, -2586, 5850, 1, 0);
			default -> new FakeTerrain(base, s.minX - 8, s.minZ, 1, 0);
		};
	}

	static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("leonida_landmark").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("list").executes(LandmarkCommand::list))
				.then(Commands.literal("place").then(Commands.argument("site", StringArgumentType.word())
						.executes(c -> place(c, -61))
						.then(Commands.argument("base", IntegerArgumentType.integer(-64, 300)).executes(c -> place(c, IntegerArgumentType.getInteger(c, "base"))))))
				.then(Commands.literal("check").then(Commands.argument("site", StringArgumentType.word()).executes(LandmarkCommand::check)))
				.then(Commands.literal("view").then(Commands.argument("site", StringArgumentType.word())
						.then(Commands.argument("name", StringArgumentType.word()).executes(LandmarkCommand::view)))));
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		StringBuilder sb = new StringBuilder("landmarks:");
		for (Site s : LandmarkGen.SITES) {
			sb.append(' ').append(s.id).append(" [");
			for (Site.View v : s.views()) sb.append(v.name()).append(' ');
			sb.append(']');
		}
		c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
		return 1;
	}

	private static int place(CommandContext<CommandSourceStack> c, int base) {
		String id = StringArgumentType.getString(c, "site");
		Site s = LandmarkGen.byId(id);
		if (s == null) {
			c.getSource().sendFailure(Component.literal("unknown site " + id));
			return 0;
		}
		ServerLevel level = c.getSource().getLevel();
		FakeTerrain t = fakeFor(s, base);
		prepare(level, s, t, 6);
		long t0 = System.nanoTime();
		place(level, s, t);
		PLACED.put(id, t);
		long ms = (System.nanoTime() - t0) / 1_000_000;
		c.getSource().sendSuccess(() -> Component.literal("placed " + id + " in " + ms + " ms"), false);
		System.out.println("[leonida-landmark] placed " + id + " in " + ms + " ms, ground y " + s.groundY(t));
		return 1;
	}

	/** Builds a site chunk by chunk (same code path as generation). */
	public static void place(ServerLevel level, Site s, GenTerrain t) {
		for (int cx = s.minX >> 4; cx <= s.maxX >> 4; cx++)
			for (int cz = s.minZ >> 4; cz <= s.maxZ >> 4; cz++) {
				Canvas.LevelCanvas canvas = new Canvas.LevelCanvas(level, cx, cz);
				s.build(canvas, t);
				canvas.finish();
			}
	}

	/** Shapes the flat test world to the fake terrain (land, beach, shallow water) around a site. */
	static void prepare(ServerLevel level, Site s, FakeTerrain t, int margin) {
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		BlockState water = Blocks.WATER.defaultBlockState();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = s.minX - margin; x <= s.maxX + margin; x++)
			for (int z = s.minZ - margin; z <= s.maxZ + margin; z++) {
				int g = t.groundY(x, z), w = t.waterY(x, z);
				int bottom = level.getMinY() + 1;
				for (int y = bottom; y <= t.seaLevel() + 24; y++) {
					BlockState b;
					if (y <= g) b = y == g ? (w == Integer.MIN_VALUE ? (t.shore(x, z) < 3 ? Blocks.SAND : Blocks.GRASS_BLOCK) : Blocks.SAND).defaultBlockState()
							: Blocks.DIRT.defaultBlockState();
					else if (w != Integer.MIN_VALUE && y <= w) b = water;
					else b = Blocks.AIR.defaultBlockState();
					level.setBlock(p.set(x, y, z), b, flags);
				}
			}
	}

	private static int view(CommandContext<CommandSourceStack> c) {
		String id = StringArgumentType.getString(c, "site"), name = StringArgumentType.getString(c, "name");
		Site s = LandmarkGen.byId(id);
		ServerPlayer pl = c.getSource().getPlayer();
		if (s == null || pl == null) return 0;
		GenTerrain t = PLACED.containsKey(id) ? PLACED.get(id) : leonida(c.getSource().getLevel()) ? CwTerrain.get() : fakeFor(s, -61);
		for (Site.View v : s.views()) {
			if (!v.name().equals(name)) continue;
			double x = s.originX() + v.x(), y = s.groundY(t) + v.y(), z = s.originZ() + v.z();
			pl.teleportTo(c.getSource().getLevel(), x, y - 1.62, z, java.util.Set.of(), v.yaw(), v.pitch(), false);
			c.getSource().sendSuccess(() -> Component.literal("view " + id + " " + name + " fov " + v.fov()), false);
			return 1;
		}
		c.getSource().sendFailure(Component.literal("no view " + name));
		return 0;
	}

	static boolean leonida(ServerLevel level) {
		return level.dimension() == WorldModule.LEONIDA;
	}

	/**
	 * Server-side site check in the generated Leonida world: replays the site against the real terrain and compares
	 * every block it writes with the level (generated at its coordinates), counts claimed columns that a road, a generic
	 * building, or another landmark also uses (no overlap), and measures each declared footprint (size and height).
	 */
	private static int check(CommandContext<CommandSourceStack> c) {
		String id = StringArgumentType.getString(c, "site");
		Site s = LandmarkGen.byId(id);
		ServerLevel level = c.getSource().getLevel();
		if (s == null || !leonida(level)) {
			c.getSource().sendFailure(Component.literal(s == null ? "unknown site " + id : "run in leonida:world/leonida"));
			return 0;
		}
		String r = check(level, s);
		c.getSource().sendSuccess(() -> Component.literal(r), false);
		System.out.println("[leonida-landmark] " + r);
		return r.contains(" PASS ") ? 1 : 0;
	}

	public static String check(ServerLevel level, Site s) {
		GenTerrain t = CwTerrain.get();
		int gy = s.groundY(t);
		long total = 0, same = 0;
		StringBuilder miss = new StringBuilder();
		for (int cx = s.minX >> 4; cx <= s.maxX >> 4; cx++)
			for (int cz = s.minZ >> 4; cz <= s.maxZ >> 4; cz++) {
				Canvas.Recording rec = new Canvas.Recording(level, cx, cz);
				s.build(rec, t);
				for (Map.Entry<BlockPos, BlockState> e : rec.blocks.entrySet()) {
					total++;
					BlockState have = level.getBlockState(e.getKey());
					if (have.getBlock() == e.getValue().getBlock()) same++;
					else if (miss.length() < 300) miss.append(' ').append(e.getKey().toShortString()).append('=').append(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(have.getBlock()).getPath());
				}
			}
		int claimed = 0, roads = 0, buildings = 0, others = 0;
		CwFeatures f = CwFeatures.get();
		RoadGenerator rg = RoadGenerator.get();
		for (int x = s.minX; x <= s.maxX; x++)
			for (int z = s.minZ; z <= s.maxZ; z++) {
				if (!s.claims(x, z)) continue;
				claimed++;
				if (rg.claims(x, z)) roads++;
				CwFeatures.Building b = f.buildingAt(x, z);
				if (b != null && !b.landmarkClaimed()) buildings++;
				boolean other = false;
				for (LandmarkGenerator g : LandmarkGenerator.all()) if (!(g instanceof LandmarkGen) && g.claims(x, z)) other = true;
				for (Site o : LandmarkGen.SITES) if (o != s && o.claims(x, z)) other = true;
				if (other) others++;
			}
		double match = total == 0 ? 0 : (double) same / total;
		boolean ok = total > 0 && match >= 0.97 && roads == 0 && buildings == 0 && others == 0;
		StringBuilder fp = new StringBuilder();
		for (Site.Footprint p : s.footprints()) {
			int[] m = measure(level, p, gy);
			int ew = p.x1() - p.x0() + 1, el = p.z1() - p.z0() + 1;
			boolean good = m != null && Math.abs(m[0] - ew) <= 1 && Math.abs(m[1] - el) <= 1 && Math.abs(m[2] - p.height()) <= 1;
			ok &= good;
			fp.append(String.format(java.util.Locale.ROOT, " | %s %s: %s expected %dx%dx%d", p.name(), good ? "ok" : "BAD",
					m == null ? "not found" : String.format(java.util.Locale.ROOT, "%dx%dx%d at %d,%d (L/W %.2f H/W %.2f)", m[0], m[1], m[2], m[3], m[4],
							(double) Math.max(m[0], m[1]) / Math.min(m[0], m[1]), (double) m[2] / Math.min(m[0], m[1])), ew, el, p.height()));
		}
		return String.format(java.util.Locale.ROOT, "CHECK %s %s ground y %d, blocks %d/%d match (%.1f%%), claimed %d columns, overlap roads %d buildings %d landmarks %d%s%s",
				s.id, ok ? "PASS" : "FAIL", gy, same, total, match * 100, claimed, roads, buildings, others, fp, match < 0.97 ? " | first misses:" + miss : "");
	}

	/** Width (x), length (z), height above ground, and min x / min z of a footprint's signature blocks, or null. */
	static int[] measure(ServerLevel level, Site.Footprint p, int gy) {
		var wall = p.wall().getBlock();
		var roof = p.roof().getBlock();
		int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE, top = Integer.MIN_VALUE;
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (int x = p.x0() - p.slack(); x <= p.x1() + p.slack(); x++)
			for (int z = p.z0() - p.slack(); z <= p.z1() + p.slack(); z++) {
				boolean inside = x >= p.x0() && x <= p.x1() && z >= p.z0() && z <= p.z1();
				for (int y = gy + 1; y <= gy + p.height() + 4; y++) {
					var b = level.getBlockState(q.set(x, y, z)).getBlock();
					if (b == wall) {
						x0 = Math.min(x0, x);
						x1 = Math.max(x1, x);
						z0 = Math.min(z0, z);
						z1 = Math.max(z1, z);
					}
					if (inside && (b == wall || b == roof)) top = Math.max(top, y);
				}
			}
		if (x0 == Integer.MAX_VALUE) return null;
		return new int[] {x1 - x0 + 1, z1 - z0 + 1, top - gy, x0, z0}; // block top (y + 1) above the ground surface (gy + 1)
	}

	/** Fov of a view (tests set the client fov to match the reference shot). */
	public static int fov(String site, String name) {
		Site s = LandmarkGen.byId(site);
		if (s != null) for (Site.View v : s.views()) if (v.name().equals(name)) return v.fov();
		return 70;
	}
}
