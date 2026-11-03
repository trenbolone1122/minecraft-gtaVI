package dev.leonida.test.world;

import dev.leonida.core.api.BuildingGenerator;
import dev.leonida.core.api.MapQuery;
import dev.leonida.test.TestFilter;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import dev.leonida.world.WorldModule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Client game tests for the world module. Owned by the world agent. Run: tools/pod-test.sh world */
public class WorldClientTest implements FabricClientGameTest {
	private static void log(String s) { System.out.println("[leonida-test] world: " + s); }

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("world")) return;
		MapQuery q = MapQuery.get();
		log("MapQuery " + q.getClass().getName());
		if (q == MapQuery.FLAT) throw new AssertionError("MapQuery not installed");

		// map queries
		int[][] probes = {{-760, 500}, {2038, -970}, {2120, -970}, {-473, -1219}, {-568, -5628}};
		for (int[] p : probes)
			log("at " + p[0] + "," + p[1] + " surface=" + q.surfaceAt(p[0], p[1]) + " groundY=" + q.groundY(p[0], p[1])
					+ " color=" + Integer.toHexString(q.mapColor(p[0], p[1])) + " place=" + q.placeName(p[0], p[1]));
		long t = System.currentTimeMillis();
		List<BlockPos> route = q.route(new BlockPos(-760, 64, 500), new BlockPos(1450, 64, -1000));
		log("route downtown to Vice Beach: " + route.size() + " points in " + (System.currentTimeMillis() - t) + " ms");
		if (route.size() < 2) throw new AssertionError("no GPS route");
		for (BuildingGenerator.Kind k : new BuildingGenerator.Kind[] {BuildingGenerator.Kind.POLICE, BuildingGenerator.Kind.HOSPITAL,
				BuildingGenerator.Kind.SHOP, BuildingGenerator.Kind.GAS_STATION})
			log("nearest " + k + " from downtown: " + q.nearest(k, -760, 500));

		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				mc.options.renderDistance().set(32);
			});
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("weather clear");

			int[] beach = findBeach(q, 2038, -970);
			shot(context, sp, beach[0], q.groundY(beach[0], beach[1]) + 2, beach[1], -90, 5, "world-beach");
			int[] od = roadView(2030, -1000, true);
			shot(context, sp, od[0], q.groundY(od[0], od[1]) + 2, od[1], od[2], 5, "world-ocean-drive");
			shot(context, sp, 1850, 150, -760, -120, 40, "world-aerial-vice-beach");
			shot(context, sp, -100, 160, 700, 90, 5, "world-downtown-skyline");
			int[] street = roadView(-400, 600, false);
			shot(context, sp, street[0], q.groundY(street[0], street[1]) + 2, street[1], street[2], 4, "world-downtown-street");
			shot(context, sp, -480, 170, -5540, 135, 40, "world-highway-interchange");
			shot(context, sp, -500, 200, 1000, 180, 40, "world-aerial-downtown");
			double[] avg = dev.leonida.world.gen.GenStats.averages();
			log(String.format("chunk gen avg ms: terrain %.2f roads %.2f buildings %.2f landmarks %.2f decoration %.2f", avg[0], avg[1], avg[2], avg[3], avg[4]));
		}
	}

	/** Dry sand a few blocks before the water, scanning east from (x, z). */
	private static int[] findBeach(MapQuery q, int x, int z) {
		int lastSand = x;
		for (int dx = 0; dx < 600; dx++) {
			MapQuery.Surface s = q.surfaceAt(x + dx, z);
			if (s == MapQuery.Surface.BEACH) lastSand = x + dx;
			if (s == MapQuery.Surface.OCEAN) return new int[] {Math.max(x, lastSand - 6), z};
		}
		return new int[] {x + 80, z};
	}

	/**
	 * Camera on a roads.json centreline near (cx, cz): the middle of a straight piece at least 40 m long, yaw along it.
	 * northSouth prefers pieces running mostly north-south. Returns {x, z, yaw}.
	 */
	private static int[] roadView(int cx, int cz, boolean northSouth) {
		dev.leonida.world.data.CwFeatures f = dev.leonida.world.data.CwFeatures.get();
		double bestScore = Double.MAX_VALUE;
		int[] best = {cx, cz, 0};
		for (dev.leonida.world.data.CwFeatures.Road r : f.roads) {
			if (r.kind() != 0) continue;
			float[] p = r.xz();
			for (int i = 0; i + 3 < p.length; i += 2) {
				double ax = p[i], az = p[i + 1], dx = p[i + 2] - ax, dz = p[i + 3] - az, len = Math.hypot(dx, dz);
				if (len < 40) continue;
				if (northSouth && Math.abs(dz) < 2 * Math.abs(dx)) continue;
				double mx = ax + dx / 2, mz = az + dz / 2, d = Math.hypot(mx - cx, mz - cz);
				if (d < bestScore) {
					bestScore = d;
					// Stand a quarter in, looking towards the far end.
					int yaw = (int) Math.round(Math.toDegrees(Math.atan2(-dx, dz)));
					best = new int[] {(int) Math.round(ax + dx * 0.25), (int) Math.round(az + dz * 0.25), yaw};
				}
			}
		}
		log("road view near " + cx + "," + cz + " -> " + best[0] + "," + best[1] + " yaw " + best[2]);
		return best;
	}

	/** Centre of a north-south road near (cx, cz): scans west to east for the widest road run. */
	private static int[] findStreetNS(MapQuery q, int cx, int cz) {
		for (int dz = 0; dz < 200; dz += 8)
			for (int sgn = -1; sgn <= 1; sgn += 2) {
				int z = cz + sgn * dz;
				for (int x = cx - 120; x < cx + 120; x++) {
					if (q.surfaceAt(x, z) != MapQuery.Surface.ROAD) continue;
					int w = 0;
					while (w < 40 && q.surfaceAt(x + w, z) == MapQuery.Surface.ROAD) w++;
					int mid = x + w / 2;
					int run = 0;
					while (run < 60 && q.surfaceAt(mid, z - run) == MapQuery.Surface.ROAD) run++;
					if (w >= 5 && run >= 60) return new int[] {mid, z};
					x += w;
				}
			}
		return new int[] {cx, cz};
	}

	/** A road column near the given point plus a yaw that looks along the road. */
	private static int[] findStreet(MapQuery q, int cx, int cz) {
		for (int r = 0; r < 300; r++)
			for (int dx = -r; dx <= r; dx++)
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					int x = cx + dx, z = cz + dz;
					if (q.surfaceAt(x, z) != MapQuery.Surface.ROAD) continue;
					int runX = 0, runZ = 0;
					while (runX < 60 && q.surfaceAt(x + runX, z) == MapQuery.Surface.ROAD) runX++;
					while (runZ < 60 && q.surfaceAt(x, z + runZ) == MapQuery.Surface.ROAD) runZ++;
					if (runX >= 60) return new int[] {x, z, -90};
					if (runZ >= 60) return new int[] {x, z, 0};
				}
		return new int[] {cx, cz, 0};
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, int x, int y, int z, int yaw, int pitch, String name) {
		try {
			shotImpl(context, sp, x, y, z, yaw, pitch, name);
		} catch (RuntimeException | AssertionError e) {
			log(name + ": shot failed, continuing: " + e);
		}
	}

	private static void shotImpl(ClientGameTestContext context, TestSingleplayerContext sp, int x, int y, int z, int yaw, int pitch, String name) {
		int rd = name.contains("aerial") || name.contains("skyline") ? 32 : 20;
		context.runOnClient(mc -> mc.options.renderDistance().set(rd));
		// Forceload the area the camera looks at and wait until every chunk there is FULL before shooting.
		MapQuery q = MapQuery.get();
		double ground = q.groundY(x, z);
		double horiz = pitch > 2 ? Math.min(160, Math.max(0, (y - ground) / Math.tan(Math.toRadians(pitch)))) : 200;
		horiz = Math.min(horiz, 200);
		double yr = Math.toRadians(yaw);
		int tx = (int) Math.round(x - Math.sin(yr) * horiz), tz = (int) Math.round(z + Math.cos(yr) * horiz);
		int r = 112;
		int cx0 = (Math.min(x, tx) - r) >> 4, cx1 = (Math.max(x, tx) + r) >> 4;
		int cz0 = (Math.min(z, tz) - r) >> 4, cz1 = (Math.max(z, tz) + r) >> 4;
		while ((cx1 - cx0 + 1) * (cz1 - cz0 + 1) > 256) {
			if (cx1 - cx0 > cz1 - cz0) { cx0++; cx1--; } else { cz0++; cz1--; }
		}
		final int fx0 = cx0, fx1 = cx1, fz0 = cz0, fz1 = cz1, total = (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload add " + (fx0 << 4) + " " + (fz0 << 4) + " " + (fx1 << 4) + " " + (fz1 << 4));
		sp.getServer().runCommand("execute in leonida:world/leonida run tp @a " + x + " " + y + " " + z + " " + yaw + " " + pitch);
		long t0 = System.currentTimeMillis();
		int full = 0;
		for (int i = 0; i < 120; i++) {
			context.waitTicks(10);
			full = sp.getServer().computeOnServer(s -> {
				ServerLevel lvl = s.getLevel(WorldModule.LEONIDA);
				int n = 0;
				for (int a = fx0; a <= fx1; a++)
					for (int b = fz0; b <= fz1; b++)
						if (lvl.getChunkSource().getChunkNow(a, b) != null) n++;
				return n;
			});
			if (full >= total) break;
		}
		long genMs = System.currentTimeMillis() - t0;
		log(name + ": " + full + "/" + total + " chunks FULL in " + genMs + " ms (" + (full > 0 ? genMs / full : -1) + " ms/chunk wall)");
		if (full < total) log(name + ": WARNING only " + full + "/" + total + " chunks generated, shooting anyway");
		boolean rendered = false;
		// Client side: all chunks of the forceloaded view area received, then every section compiled (twice in a row).
		int stable = 0;
		for (int i = 0; i < 120 && stable < 2; i++) {
			context.waitTicks(10);
			boolean ok = context.computeOnClient(mc -> {
				if (mc.level == null) return false;
				for (int a = fx0; a <= fx1; a++)
					for (int b = fz0; b <= fz1; b++)
						if (!mc.level.getChunkSource().hasChunk(a, b)) return false;
				return mc.levelRenderer.hasRenderedAllSections();
			});
			stable = ok ? stable + 1 : 0;
		}
		rendered = stable >= 2;
		log(name + ": sections compiled=" + rendered + " after " + (System.currentTimeMillis() - t0) + " ms");
		context.waitTicks(40);
		log("shot " + name + " at " + x + "," + y + "," + z + " rendered=" + rendered);
		context.takeScreenshot(name);
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload remove all");
	}
}
