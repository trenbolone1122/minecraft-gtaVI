package dev.leonida.test.fx;

import dev.leonida.core.api.MapQuery;
import dev.leonida.fx.FxConfig;
import dev.leonida.fx.look.LeonidaLook;
import dev.leonida.world.WorldModule;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;

/**
 * The fx look on the real Leonida map: before (quality off) and after (quality high) at noon, sunset and night,
 * at the spots world_builder picked (Ocean Drive, Vice Beach surf, open ocean, downtown).
 */
final class FxTerrainShots {
	private FxTerrainShots() {}

	/** name, x, y (Integer.MIN_VALUE = ground + 2), z, yaw, pitch */
	private static final Object[][] SPOTS = {
			{"ocean-drive", 2030, Integer.MIN_VALUE, -1000, 180, 4},
			{"beach", 2250, 66, -970, -90, 8},
			{"ocean", 2600, 70, -970, 90, 6},
			{"downtown", -276, 68, 470, 180, 3},
	};

	static void run(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
				mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
				mc.options.renderDistance().set(16);
			});
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("gamerule advance_weather false");
			sp.getServer().runCommand("weather clear");
			MapQuery q = MapQuery.get();
			for (Object[] s : SPOTS) {
				String name = (String) s[0];
				int x = (Integer) s[1], z = (Integer) s[3];
				int y = (Integer) s[2] == Integer.MIN_VALUE ? q.groundY(x, z) + 2 : (Integer) s[2];
				System.out.println("[leonida-test] fx terrain " + name + " at " + x + "," + y + "," + z + " surface=" + q.surfaceAt(x, z)
						+ " ground=" + q.groundY(x, z));
				go(context, sp, x, y, z, (Integer) s[4], (Integer) s[5], name);
				for (String[] t : new String[][] {{"noon", "6000"}, {"sunset", "11900"}, {"night", "18000"}}) {
					sp.getServer().runCommand("time set " + t[1]);
					context.runOnClient(mc -> FxConfig.get().quality = FxConfig.Quality.OFF);
					context.waitTicks(12);
					context.takeScreenshot("fx-terrain-" + name + "-" + t[0] + "-before");
					context.runOnClient(mc -> FxConfig.get().quality = FxConfig.Quality.HIGH);
					context.waitTicks(25);
					String info = context.computeOnClient(mc -> String.format("day %.2f golden %.2f night %.2f active %s foam cells %d",
							LeonidaLook.day(), LeonidaLook.golden(), LeonidaLook.night(), LeonidaLook.active(),
							dev.leonida.fx.look.WaterFoam.waterCells()));
					System.out.println("[leonida-test] fx terrain " + name + " " + t[0] + ": " + info);
					context.takeScreenshot("fx-terrain-" + name + "-" + t[0] + "-after");
				}
				sp.getServer().runCommand("execute in leonida:world/leonida run forceload remove all");
			}
			context.runOnClient(mc -> FxConfig.get().quality = FxConfig.Quality.MEDIUM);
		}
	}

	/**
	 * Aerial over downtown at y 200: vanilla (fx off) versus the fx look, at render distance 12 and 16. The city must
	 * stay visible out to the render distance (fog only at the edge, none extra at altitude).
	 */
	static void aerial(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
				mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
			});
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("gamerule advance_weather false");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("time set 6000");
			for (int rd : new int[] {12, 16}) {
				context.runOnClient(mc -> {
					mc.options.renderDistance().set(rd);
					mc.options.simulationDistance().set(Math.min(rd, 12));
					mc.options.broadcastOptions();
				});
				sp.getServer().runOnServer(server -> server.getPlayerList().setViewDistance(rd));
				go(context, sp, -500, 200, 1000, 180, 35, "aerial-rd" + rd); // world_builder's downtown aerial spot, clear of towers
				// wait until the client holds (nearly) every chunk inside the render distance, so the shot shows the full view
				long t0 = System.currentTimeMillis();
				float have = 0;
				for (int i = 0; i < 480 && have < 0.97F; i++) {
					context.waitTicks(10);
					have = context.computeOnClient(mc -> {
						int cx = -500 >> 4, cz = 1000 >> 4, n = 0, in = 0;
						for (int a = -rd; a <= rd; a++)
							for (int b = -rd; b <= rd; b++) {
								if (a * a + b * b > rd * rd) continue;
								n++;
								if (mc.level.getChunkSource().hasChunk(cx + a, cz + b)) in++;
							}
						return in / (float) n;
					});
				}
				for (int i = 0; i < 120; i++) {
					context.waitTicks(10);
					if (context.computeOnClient(mc -> mc.levelRenderer.hasRenderedAllSections())) break;
				}
				System.out.println(String.format("[leonida-test] fx aerial rd %d: %.0f%% of chunks in view after %d ms", rd, have * 100,
						System.currentTimeMillis() - t0));
				context.runOnClient(mc -> FxConfig.get().quality = FxConfig.Quality.OFF);
				context.waitTicks(12);
				context.takeScreenshot("fx-aerial-downtown-y200-rd" + rd + "-before");
				context.runOnClient(mc -> FxConfig.get().quality = FxConfig.Quality.HIGH);
				context.waitTicks(25);
				String info = context.computeOnClient(mc -> String.format("rd %d active %s camY %.0f", mc.options.getEffectiveRenderDistance(),
						LeonidaLook.active(), mc.gameRenderer.mainCamera().position().y));
				System.out.println("[leonida-test] fx aerial " + info);
				context.takeScreenshot("fx-aerial-downtown-y200-rd" + rd + "-after");
				sp.getServer().runCommand("execute in leonida:world/leonida run forceload remove all");
			}
			context.runOnClient(mc -> FxConfig.get().quality = FxConfig.Quality.MEDIUM);
		}
	}

	/** Forceload the view, teleport, wait until the chunks are generated and every section is compiled. */
	private static void go(ClientGameTestContext context, TestSingleplayerContext sp, int x, int y, int z, int yaw, int pitch, String name) {
		double yr = Math.toRadians(yaw);
		int tx = (int) Math.round(x - Math.sin(yr) * 120), tz = (int) Math.round(z + Math.cos(yr) * 120);
		int r = 96;
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
		for (int i = 0; i < 240 && stable < 2; i++) {
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
		System.out.println("[leonida-test] fx terrain " + name + ": " + full + "/" + total + " chunks, rendered " + (stable >= 2) + " after "
				+ (System.currentTimeMillis() - t0) + " ms");
		context.waitTicks(40);
	}
}
