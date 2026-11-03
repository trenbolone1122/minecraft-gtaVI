package dev.leonida.test.look;

import dev.leonida.core.api.BuildingGenerator;
import dev.leonida.core.api.MapQuery;
import dev.leonida.look.LookBlocks;
import dev.leonida.look.LookBlocks.Neon;
import dev.leonida.world.WorldModule;
import dev.leonida.world.data.CwFeatures;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * District night neon on the real map (run with tools/gpu-test.sh lookneon). Counts look neon tubes and glow blocks
 * per km2 in generated sample areas (Vice Beach, downtown, a suburb), then takes night shots with the shader pack on
 * HIGH: aerial over Vice Beach at y 150, Ocean Drive street, downtown street, downtown skyline.
 */
final class LookNeonShots {
	private LookNeonShots() {}

	private static void log(String s) { System.out.println("[leonida-test] lookneon: " + s); }

	static void run(ClientGameTestContext context, boolean counts, boolean shots) {
		MapQuery q = MapQuery.get();
		if (q == MapQuery.FLAT) { log("MapQuery not installed"); return; }
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
				mc.options.renderDistance().set(24);
			});
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("gamerule spawn_mobs false");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("time set 18000");
			if (shots) setHigh(context);

			// 1. counts: 12 x 12 chunk samples (192 m square)
			int[] sub = counts ? suburb() : new int[] {0, 0};
			int[][] samples = {{1940, -1060}, {-500, 420}, {sub[0], sub[1]}};
			String[] names = {"vice-beach", "downtown", "suburb"};
			if (counts) for (int i = 0; i < samples.length; i++) count(context, sp, names[i], samples[i][0], samples[i][1]);

			// 2. night shots
			if (!shots) return;
			int[] od = roadView(2030, -1000, true);
			shot(context, sp, 1850, 150, -760, -120, 40, "lookneon-aerial-vice-beach");
			shot(context, sp, od[0], q.groundY(od[0], od[1]) + 2, od[1], od[2], 4, "lookneon-ocean-drive");
			int[] st = roadView(-400, 600, false);
			shot(context, sp, st[0], q.groundY(st[0], st[1]) + 2, st[1], st[2], 6, "lookneon-downtown-street");
			shot(context, sp, -100, 160, 700, 90, 5, "lookneon-downtown-skyline");
		}
	}

	/** 12 x 12 chunk area with the most houses, away from Vice Beach and downtown. */
	private static int[] suburb() {
		CwFeatures f = CwFeatures.get();
		int best = -1, bx = -2000, bz = 0;
		for (int x = -4000; x <= 1300; x += 192) for (int z = -4000; z <= 4000; z += 192) {
			if (x >= -1350 && x <= 500 && z >= -100 && z <= 1450) continue;
			int n = 0;
			for (int a = 0; a < 12; a += 2) for (int b = 0; b < 12; b += 2)
				for (CwFeatures.Building bl : f.buildingsInChunk((x >> 4) + a, (z >> 4) + b))
					if (bl.kind() == BuildingGenerator.Kind.HOUSE) n++;
			if (n > best) { best = n; bx = x; bz = z; }
		}
		log("suburb sample at " + bx + "," + bz + " (" + best + " sampled houses)");
		return new int[] {bx, bz};
	}

	private static void count(ClientGameTestContext context, TestSingleplayerContext sp, String name, int x, int z) {
		int cx0 = x >> 4, cz0 = z >> 4, cx1 = cx0 + 11, cz1 = cz0 + 11;
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload remove all");
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload add " + (cx0 << 4) + " " + (cz0 << 4) + " " + (cx1 << 4) + " " + (cz1 << 4));
		long t0 = System.currentTimeMillis();
		int full = 0;
		for (int i = 0; i < 360 && full < 144; i++) {
			context.waitTicks(10);
			full = sp.getServer().computeOnServer(s -> {
				ServerLevel lvl = s.getLevel(WorldModule.LEONIDA);
				int n = 0;
				for (int a = cx0; a <= cx1; a++) for (int b = cz0; b <= cz1; b++) if (lvl.getChunkSource().getChunkNow(a, b) != null) n++;
				return n;
			});
		}
		final int loaded = full;
		long[] c = sp.getServer().computeOnServer(s -> {
			ServerLevel lvl = s.getLevel(WorldModule.LEONIDA);
			Set<Block> tubes = new HashSet<>();
			for (Neon n : Neon.values()) tubes.add(LookBlocks.neon(n));
			Set<Block> glow = Set.of(Blocks.SEA_LANTERN, Blocks.PEARLESCENT_FROGLIGHT, Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT);
			long nt = 0, ng = 0;
			for (int a = cx0; a <= cx1; a++) for (int b = cz0; b <= cz1; b++) {
				LevelChunk ch = lvl.getChunkSource().getChunkNow(a, b);
				if (ch == null) continue;
				for (LevelChunkSection sec : ch.getSections()) {
					if (sec.hasOnlyAir()) continue;
					if (!sec.maybeHas(st -> tubes.contains(st.getBlock()) || glow.contains(st.getBlock()))) continue;
					for (int yy = 0; yy < 16; yy++) for (int zz = 0; zz < 16; zz++) for (int xx = 0; xx < 16; xx++) {
						Block bl = sec.getBlockState(xx, yy, zz).getBlock();
						if (tubes.contains(bl)) nt++;
						else if (glow.contains(bl)) ng++;
					}
				}
			}
			return new long[] {nt, ng};
		});
		double km2 = loaded * 256.0 / 1_000_000.0;
		log(String.format(java.util.Locale.ROOT, "count %s at %d,%d: %d/144 chunks, neon tubes %d, glow blocks %d, tubes per km2 %.0f, tubes+glow per km2 %.0f (%d ms)",
				name, x, z, loaded, c[0], c[1], c[0] / Math.max(km2, 1e-9), (c[0] + c[1]) / Math.max(km2, 1e-9), System.currentTimeMillis() - t0));
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, int x, int y, int z, int yaw, int pitch, String name) {
		int cx = x >> 4, cz = z >> 4;
		double yr = Math.toRadians(yaw);
		int tx = (int) Math.round(x - Math.sin(yr) * 160), tz = (int) Math.round(z + Math.cos(yr) * 160);
		int ax = Math.min(cx, tx >> 4) - 3, bx = Math.max(cx, tx >> 4) + 3, az = Math.min(cz, tz >> 4) - 3, bz = Math.max(cz, tz >> 4) + 3;
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload remove all");
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload add " + (ax << 4) + " " + (az << 4) + " " + (bx << 4) + " " + (bz << 4));
		sp.getServer().runCommand("execute in leonida:world/leonida run tp @a " + x + " " + y + " " + z + " " + yaw + " " + pitch);
		final int fx0 = ax, fx1 = bx, fz0 = az, fz1 = bz, total = (bx - ax + 1) * (bz - az + 1);
		int full = 0;
		for (int i = 0; i < 240 && full < total; i++) {
			context.waitTicks(10);
			full = sp.getServer().computeOnServer(s -> {
				ServerLevel lvl = s.getLevel(WorldModule.LEONIDA);
				int n = 0;
				for (int a = fx0; a <= fx1; a++) for (int b = fz0; b <= fz1; b++) if (lvl.getChunkSource().getChunkNow(a, b) != null) n++;
				return n;
			});
		}
		int stable = 0;
		for (int i = 0; i < 180 && stable < 3; i++) {
			context.waitTicks(10);
			boolean ok = context.computeOnClient(mc -> mc.levelRenderer.hasRenderedAllSections());
			stable = ok ? stable + 1 : 0;
		}
		context.waitTicks(200);
		log("view " + name + ": " + full + "/" + total + " chunks, compiled=" + (stable >= 3));
		context.takeScreenshot(name);
		log("shot " + name + " at " + x + "," + y + "," + z + " yaw " + yaw + " pitch " + pitch);
	}

	/** Shader pack profile HIGH (same values as ShaderTerrainShots), queued into Iris and reloaded. */
	private static void setHigh(ClientGameTestContext context) {
		java.util.Map<String, String> o = new java.util.HashMap<>();
		o.put("shadowMapResolution", "2048"); o.put("shadowDistance", "128.0"); o.put("SHADOW_SAMPLES", "12"); o.put("SSR", "true");
		o.put("SSR_STEPS", "20"); o.put("CLOUD_OCTAVES", "4"); o.put("COLORED_SHADOWS", "true"); o.put("WAVING_PLANTS", "true");
		o.put("CLOUDS", "true"); o.put("BLOOM", "true");
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
		log("shader profile HIGH " + (err.isEmpty() ? "set" : "failed: " + err));
		context.waitTicks(60);
	}

	/** Camera on a straight roads.json piece near (cx, cz), a quarter in, looking along it. Returns {x, z, yaw}. */
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
					best = new int[] {(int) Math.round(ax + dx * 0.25), (int) Math.round(az + dz * 0.25), (int) Math.round(Math.toDegrees(Math.atan2(-dx, dz)))};
				}
			}
		}
		return best;
	}
}
