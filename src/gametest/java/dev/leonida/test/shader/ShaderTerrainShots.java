package dev.leonida.test.shader;

import dev.leonida.core.api.MapQuery;
import dev.leonida.shader.DhDefaults;
import dev.leonida.world.WorldModule;
import dev.leonida.world.data.CwFeatures;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;

/**
 * Leonida Shaders on the real generated map: frame time per profile (LOW, MEDIUM, HIGH) at a downtown street camera
 * and an aerial camera at y 200, then Distant Horizons LODs from y 200 over Vice Beach looking west at downtown.
 */
final class ShaderTerrainShots {
	private ShaderTerrainShots() {}

	private static void log(String s) {
		System.out.println("[leonida-test] shader terrain: " + s);
	}

	static void run(ClientGameTestContext context) {
		MapQuery q = MapQuery.get();
		if (q == MapQuery.FLAT) {
			log("MapQuery not installed, skipping terrain shots");
			return;
		}
		log("Distant Horizons: loaded=" + DhDefaults.dhLoaded() + " defaults " + DhDefaults.status() + " now " + DhDefaults.describe());
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				mc.options.renderDistance().set(12);
				mc.options.framerateLimit().set(260);
				mc.options.enableVsync().set(false);
				mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
			});
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("weather clear");
			setProfile(context, "HIGH");
			// landmarks at 100 to 200 m with Distant Horizons on: no LOD overdraw, neon hue at night
			landmark(context, sp, "L438", 200, 0, 30, 30, new int[] {6000, 18000}, "shader-dh-wheel-200m-east");
			landmark(context, sp, "L196", 0, 176, 40, 18, new int[] {6000}, "shader-dh-fleeca-176m-south");
		}
	}

	private static void landmark(ClientGameTestContext context, TestSingleplayerContext sp, String id, int dx, int dz, int camH,
			int targetH, int[] times, String name) {
		dev.leonida.vicecity.gen.Site s = dev.leonida.vicecity.ViceLandmarks.INSTANCE.site(id);
		if (s == null) {
			log("no site " + id);
			return;
		}
		double[] c = s.toWorld(0, 0);
		String dim = "execute in leonida:world/leonida run ";
		int cx = (int) c[0], cz = (int) c[1];
		int x0 = Math.min(s.minX, cx + dx) - 16, x1 = Math.max(s.maxX, cx + dx) + 16;
		int z0 = Math.min(s.minZ, cz + dz) - 16, z1 = Math.max(s.maxZ, cz + dz) + 16;
		sp.getServer().runCommand(dim + "forceload add " + x0 + " " + z0 + " " + x1 + " " + z1);
		sp.getServer().runCommand(dim + "tp @a " + (cx + dx) + " 160 " + (cz + dz));
		int cx0 = x0 >> 4, cx1 = x1 >> 4, cz0 = z0 >> 4, cz1 = z1 >> 4, total = (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
		int full = 0;
		for (int i = 0; i < 240 && full < total; i++) {
			context.waitTicks(10);
			full = sp.getServer().computeOnServer(srv -> {
				ServerLevel lvl = srv.getLevel(WorldModule.LEONIDA);
				int n = 0;
				for (int a = cx0; a <= cx1; a++)
					for (int b = cz0; b <= cz1; b++)
						if (lvl.getChunkSource().getChunkNow(a, b) != null) n++;
				return n;
			});
		}
		int datum = s.lastDatum == Integer.MIN_VALUE ? 64 : s.lastDatum;
		sp.getServer().runCommand(dim + "tp @a " + (cx + dx) + " " + (datum + camH) + " " + (cz + dz) + " facing " + cx + " " + (datum + targetH) + " " + cz);
		// near sections build and DH fills its LODs around the camera
		context.waitTicks(1200);
		for (int t : times) {
			sp.getServer().runCommand("time set " + t);
			context.waitTicks(60);
			String shot = name + (t >= 13000 ? "-night" : "-day");
			context.takeScreenshot(shot);
			log("landmark " + id + " " + s.name + ": " + full + "/" + total + " chunks, camera " + (cx + dx) + "," + (datum + camH) + "," + (cz + dz)
				+ " -> " + cx + "," + (datum + targetH) + "," + cz + ", shot " + shot + ", " + DhDefaults.describe());
		}
	}

	private static final String[] PROFILES = {"LOW", "MEDIUM", "HIGH"};

	private static void perProfile(ClientGameTestContext context, TestSingleplayerContext sp, View v, String tag) {
		for (String p : PROFILES) {
			setProfile(context, p);
			double ms = frameTime(context);
			context.takeScreenshot("shader-perf-" + tag + "-" + p.toLowerCase());
			log("perf " + tag + " " + p + ": " + fmt(ms) + " ms per frame (" + fmt(1000.0 / ms) + " fps) at " + v);
		}
	}

	/** Same values as profile.X in shaders.properties, queued the way the Iris options screen does it, then reload. */
	private static void setProfile(ClientGameTestContext context, String profile) {
		java.util.Map<String, String> o = new java.util.HashMap<>();
		switch (profile) {
			case "LOW" -> { o.put("shadowMapResolution", "1024"); o.put("shadowDistance", "48.0"); o.put("SHADOW_SAMPLES", "4"); o.put("SSR", "false"); o.put("SSR_STEPS", "10"); o.put("CLOUD_OCTAVES", "3"); o.put("COLORED_SHADOWS", "false"); o.put("WAVING_PLANTS", "false"); }
			case "MEDIUM" -> { o.put("shadowMapResolution", "1536"); o.put("shadowDistance", "80.0"); o.put("SHADOW_SAMPLES", "8"); o.put("SSR", "true"); o.put("SSR_STEPS", "16"); o.put("CLOUD_OCTAVES", "3"); o.put("COLORED_SHADOWS", "true"); o.put("WAVING_PLANTS", "true"); }
			default -> { o.put("shadowMapResolution", "2048"); o.put("shadowDistance", "128.0"); o.put("SHADOW_SAMPLES", "12"); o.put("SSR", "true"); o.put("SSR_STEPS", "20"); o.put("CLOUD_OCTAVES", "4"); o.put("COLORED_SHADOWS", "true"); o.put("WAVING_PLANTS", "true"); }
		}
		o.put("CLOUDS", "true");
		o.put("BLOOM", "true");
		String err = context.computeOnClient(mc -> {
			try {
				Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
				@SuppressWarnings("unchecked")
				java.util.Map<String, String> queue = (java.util.Map<String, String>) iris.getMethod("getShaderPackOptionQueue").invoke(null);
				queue.putAll(o);
				iris.getMethod("reload").invoke(null);
				return "";
			} catch (ReflectiveOperationException | RuntimeException e) {
				return e.toString();
			}
		});
		if (!err.isEmpty()) log("profile " + profile + " failed: " + err);
		context.waitTicks(60);
	}

	/** Average frame time in ms over about 4 s, from the client's per-second frame counter. */
	private static double frameTime(ClientGameTestContext context) {
		context.waitTicks(40);
		int sum = 0;
		for (int i = 0; i < 4; i++) {
			context.waitTicks(20);
			sum += context.computeOnClient(mc -> mc.getFps());
		}
		return 1000.0 / Math.max(sum / 4.0, 0.1);
	}

	private static String fmt(double d) {
		return String.format(java.util.Locale.ROOT, "%.1f", d);
	}

	private record View(int x, int y, int z, int yaw, int pitch) {}

	/** Teleport, forceload the view and wait until chunks are generated on the server and compiled on the client. */
	private static void prepare(ClientGameTestContext context, TestSingleplayerContext sp, View v) {
		MapQuery q = MapQuery.get();
		double ground = q.groundY(v.x, v.z);
		double horiz = v.pitch > 2 ? Math.min(160, Math.max(0, (v.y - ground) / Math.tan(Math.toRadians(v.pitch)))) : 160;
		double yr = Math.toRadians(v.yaw);
		int tx = (int) Math.round(v.x - Math.sin(yr) * horiz), tz = (int) Math.round(v.z + Math.cos(yr) * horiz);
		int r = 96;
		int cx0 = (Math.min(v.x, tx) - r) >> 4, cx1 = (Math.max(v.x, tx) + r) >> 4;
		int cz0 = (Math.min(v.z, tz) - r) >> 4, cz1 = (Math.max(v.z, tz) + r) >> 4;
		while ((cx1 - cx0 + 1) * (cz1 - cz0 + 1) > 196) {
			if (cx1 - cx0 > cz1 - cz0) { cx0++; cx1--; } else { cz0++; cz1--; }
		}
		final int fx0 = cx0, fx1 = cx1, fz0 = cz0, fz1 = cz1, total = (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload remove all");
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload add " + (fx0 << 4) + " " + (fz0 << 4) + " " + (fx1 << 4) + " " + (fz1 << 4));
		sp.getServer().runCommand("execute in leonida:world/leonida run tp @a " + v.x + " " + v.y + " " + v.z + " " + v.yaw + " " + v.pitch);
		long t0 = System.currentTimeMillis();
		int full = 0;
		for (int i = 0; i < 360 && full < total; i++) {
			context.waitTicks(10);
			full = sp.getServer().computeOnServer(s -> {
				ServerLevel lvl = s.getLevel(WorldModule.LEONIDA);
				int n = 0;
				for (int a = fx0; a <= fx1; a++)
					for (int b = fz0; b <= fz1; b++)
						if (lvl.getChunkSource().getChunkNow(a, b) != null) n++;
				return n;
			});
		}
		int stable = 0;
		for (int i = 0; i < 60 && stable < 2; i++) {
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
		log("view " + v + ": " + full + "/" + total + " chunks, compiled=" + (stable >= 2) + " in " + (System.currentTimeMillis() - t0) + " ms");
		context.waitTicks(40);
	}

	private static int[] findBeach(MapQuery q, int x, int z) {
		int lastSand = x;
		for (int dx = 0; dx < 600; dx++) {
			MapQuery.Surface s = q.surfaceAt(x + dx, z);
			if (s == MapQuery.Surface.BEACH) lastSand = x + dx;
			if (s == MapQuery.Surface.OCEAN) return new int[] {Math.max(x, lastSand - 4), z};
		}
		return new int[] {x + 80, z};
	}

	/** Middle of a straight road piece near (cx, cz) at least 40 m long, looking along it. Returns {x, z, yaw}. */
	private static int[] roadView(int cx, int cz, boolean northSouth) {
		CwFeatures f = CwFeatures.get();
		double bestScore = Double.MAX_VALUE;
		int[] best = {cx, cz, 0};
		for (CwFeatures.Road r : f.roads) {
			if (r.kind() != 0) continue;
			float[] p = r.xz();
			for (int i = 0; i + 3 < p.length; i += 2) {
				double ax = p[i], az = p[i + 1], dx = p[i + 2] - ax, dz = p[i + 3] - az, len = Math.hypot(dx, dz);
				if (len < 40) continue;
				if (northSouth && Math.abs(dz) < 2 * Math.abs(dx)) continue;
				double d = Math.hypot(ax + dx / 2 - cx, az + dz / 2 - cz);
				if (d < bestScore) {
					bestScore = d;
					int yaw = (int) Math.round(Math.toDegrees(Math.atan2(-dx, dz)));
					best = new int[] {(int) Math.round(ax + dx * 0.25), (int) Math.round(az + dz * 0.25), yaw};
				}
			}
		}
		return best;
	}
}
