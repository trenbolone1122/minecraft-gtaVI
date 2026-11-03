package dev.leonida.test.fx;

import dev.leonida.fx.FxApi;
import dev.leonida.fx.FxClient;
import dev.leonida.fx.FxConfig;
import dev.leonida.fx.look.LeonidaLook;
import dev.leonida.test.TestFilter;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Client game tests for the fx module. Owned by the fx agent. Run: tools/pod-test.sh fx
 *
 * Builds a small Leonida street scene, then takes before (quality off) and after (quality medium) screenshots at
 * noon, sunset and night, a debris burst mid-air and settled, a glass shatter and ragdolls.
 */
public class FxClientTest implements FabricClientGameTest {
	static final String MODE = "aerial";

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("fx")) return;
		// MODE: "aerial" (fog check over downtown, fast), "terrain" (spots on the real map), "flat" (street scene,
		// debris, glass, ragdolls) or "all". System property leonida.fx.mode overrides.
		String mode = System.getProperty("leonida.fx.mode", MODE);
		// cinematic capture: when cine/shots.json names shots in "run", render those instead of the checks below
		if (dev.leonida.test.fx.cine.CineCapture.requested()) {
			dev.leonida.test.fx.cine.CineCapture.run(context);
			return;
		}
		if (mode.equals("aerial") || mode.equals("all")) FxTerrainShots.aerial(context);
		if (mode.equals("terrain") || mode.equals("all")) FxTerrainShots.run(context);
		if (!mode.equals("flat") && !mode.equals("all")) return;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			TestServerContext server = sp.getServer();
			BlockPos o = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).blockPosition());
			int x = o.getX(), y = o.getY() + 1, z = o.getZ();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @a");
			buildScene(server, x, y, z);
			// camera east of the scene, looking west (sunset side), slightly down
			server.runCommand(String.format("tp @a %d.5 %d %d.5 90 12", x + 6, y + 3, z));
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
				// the test harness turns clouds off; players have them on by default
				mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
			});
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender();

			shotPair(context, server, "noon", 6000);
			shotPair(context, server, "sunset", 12150);
			shotPair(context, server, "night", 18000);

			// the beach and the ocean west of the street: foam, reflections, sun glitter
			server.runCommand(String.format("tp @a %d.5 %d %d.5 90 16", x - 36, y + 5, z));
			context.waitTicks(60); // let the ocean chunks load and the foam scan run (every 20 ticks)
			sp.getConnection().waitForChunksRender();
			shotPair(context, server, "water-noon", 6000);
			int water = context.computeOnClient(mc -> dev.leonida.fx.look.WaterFoam.waterCells());
			System.out.println("[leonida-test] fx water cells scanned for foam: " + water);
			// foam overlay alone (grade off) to check it composes cleanly with vanilla water
			context.runOnClient(mc -> FxConfig.get().gradeStrength = 0.0F);
			context.waitTicks(4);
			context.takeScreenshot("fx-water-noon-foam-only");
			context.runOnClient(mc -> FxConfig.get().gradeStrength = 1.0F);
			shotPair(context, server, "water-sunset", 11900);
			shotPair(context, server, "water-night", 18000);
			server.runCommand(String.format("tp @a %d.5 %d %d.5 90 12", x + 6, y + 3, z));
			context.waitTicks(20);

			// physics at golden hour (looks nicest), full quality
			server.runCommand("time set 11600");
			setQuality(context, FxConfig.Quality.MEDIUM);
			context.waitTicks(10);
			// debris: blast the tower in front of the camera
			server.runOnServer(s -> FxApi.debrisBurst(s.overworld(), new Vec3(x - 6.5, y + 1.5, z + 3.5), 4.0F));
			context.waitTicks(5);
			int flying = context.computeOnClient(mc -> FxClient.debris().size());
			System.out.println("[leonida-test] fx debris pieces after burst: " + flying);
			if (flying < 20) throw new AssertionError("debris burst spawned only " + flying + " pieces");
			context.takeScreenshot("fx-debris-midair");
			context.waitTicks(100);
			int sleeping = context.computeOnClient(mc -> (int) FxClient.debris().pieces().stream().filter(p -> p.sleeping).count());
			int total = context.computeOnClient(mc -> FxClient.debris().size());
			System.out.println("[leonida-test] fx debris after 5 s: " + total + " pieces, " + sleeping + " at rest");
			context.takeScreenshot("fx-debris-settled");

			// glass shatters into shards
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				for (int dz = -2; dz <= 2; dz++) {
					for (int dy = 0; dy <= 2; dy++) {
						FxApi.shatterGlass(level, new BlockPos(x - 10, y + dy, z - 4 + dz), new Vec3(6, 1, 0));
					}
				}
			});
			context.waitTicks(4);
			context.takeScreenshot("fx-glass-shatter");

			// ragdolls: a zombie (humanoid ragdoll) and a pig (rigid body) killed by a blast next to them
			server.runCommand("kill @e[type=!player]");
			context.runOnClient(mc -> FxClient.debris().clear());
			server.runCommand(String.format("summon zombie %d.5 %d %d.5 {NoAI:1b,Rotation:[90f,0f]}", x - 2, y, z - 1));
			server.runCommand(String.format("summon pig %d.5 %d %d.5 {NoAI:1b}", x - 2, y, z + 2));
			context.waitTicks(10);
			server.runCommand(String.format("damage @e[type=zombie,limit=1] 100 minecraft:explosion at %d %d %d", x - 1, y, z - 1));
			server.runCommand(String.format("damage @e[type=pig,limit=1] 100 minecraft:explosion at %d %d %d", x - 1, y, z + 1));
			context.waitTicks(3);
			int bodies = context.computeOnClient(mc -> FxClient.ragdolls().bodies().size());
			System.out.println("[leonida-test] fx ragdolls: " + bodies);
			if (bodies < 2) throw new AssertionError("expected 2 ragdolls, got " + bodies);
			context.takeScreenshot("fx-ragdoll-flying");
			context.waitTicks(50);
			context.takeScreenshot("fx-ragdoll-rest");
			String info = context.computeOnClient(mc -> {
				StringBuilder sb = new StringBuilder();
				for (var b : FxClient.ragdolls().bodies()) {
					if (b.ragdoll != null) {
						double[] pts = new double[dev.leonida.fx.physics.Ragdoll.N * 3];
						b.ragdoll.interpolated(1.0F, pts);
						sb.append(" humanoid pts=");
						for (int i = 0; i < pts.length; i += 3) sb.append(String.format("(%.2f %.2f %.2f)", pts[i], pts[i + 1], pts[i + 2]));
					} else {
						sb.append(String.format(" rigid at (%.2f %.2f %.2f)", b.rigid.pos.x, b.rigid.pos.y, b.rigid.pos.z));
					}
				}
				return sb.toString();
			});
			System.out.println("[leonida-test] fx ragdoll rest:" + info + " origin " + x + " " + y + " " + z);
			server.runCommand(String.format("tp @a %d.5 %d %d.5 90 35", x + 1, y + 2, z));
			context.waitTicks(5);
			context.takeScreenshot("fx-ragdoll-close");
		}
	}

	private static void shotPair(ClientGameTestContext context, TestServerContext server, String name, int time) {
		server.runCommand("time set " + time);
		setQuality(context, FxConfig.Quality.OFF);
		context.waitTicks(12);
		context.takeScreenshot("fx-" + name + "-before");
		setQuality(context, FxConfig.Quality.MEDIUM);
		context.waitTicks(12);
		String phase = context.computeOnClient(mc -> String.format("day %.2f golden %.2f night %.2f active %s", LeonidaLook.day(), LeonidaLook.golden(),
				LeonidaLook.night(), LeonidaLook.active()));
		System.out.println("[leonida-test] fx " + name + ": " + phase);
		context.takeScreenshot("fx-" + name + "-after");
	}

	private static void setQuality(ClientGameTestContext context, FxConfig.Quality q) {
		context.runOnClient(mc -> FxConfig.get().quality = q);
	}

	/** A small street west of the origin: plaza, teal pool, pink building with windows and neon, a palm, a tower. */
	private static void buildScene(TestServerContext s, int x, int y, int z) {
		cmd(s, "fill %d %d %d %d %d %d air", x - 40, y, z - 16, x + 8, y + 14, z + 16);
		cmd(s, "fill %d %d %d %d %d %d smooth_sandstone", x - 40, y - 1, z - 16, x + 8, y - 1, z + 16);
		cmd(s, "fill %d %d %d %d %d %d light_gray_concrete", x - 40, y - 1, z - 2, x + 8, y - 1, z + 1);
		// pool
		cmd(s, "fill %d %d %d %d %d %d prismarine_bricks", x - 16, y - 3, z + 6, x - 9, y - 1, z + 13);
		cmd(s, "fill %d %d %d %d %d %d water", x - 15, y - 2, z + 7, x - 10, y - 1, z + 12);
		// pink building with glass and neon trim
		cmd(s, "fill %d %d %d %d %d %d pink_concrete hollow", x - 28, y, z - 12, x - 20, y + 8, z - 4);
		cmd(s, "fill %d %d %d %d %d %d glass", x - 20, y + 2, z - 11, x - 20, y + 3, z - 5);
		cmd(s, "fill %d %d %d %d %d %d glass", x - 20, y + 5, z - 11, x - 20, y + 6, z - 5);
		cmd(s, "fill %d %d %d %d %d %d magenta_glazed_terracotta", x - 20, y + 8, z - 12, x - 20, y + 8, z - 4);
		cmd(s, "fill %d %d %d %d %d %d sea_lantern", x - 19, y + 7, z - 12, x - 19, y + 7, z - 4);
		cmd(s, "fill %d %d %d %d %d %d pearlescent_froglight", x - 19, y + 1, z - 12, x - 19, y + 1, z - 12);
		cmd(s, "fill %d %d %d %d %d %d pearlescent_froglight", x - 19, y + 1, z - 4, x - 19, y + 1, z - 4);
		// second building, cyan, with orange neon
		cmd(s, "fill %d %d %d %d %d %d cyan_terracotta hollow", x - 34, y, z + 2, x - 26, y + 11, z + 10);
		cmd(s, "fill %d %d %d %d %d %d ochre_froglight", x - 25, y + 10, z + 2, x - 25, y + 10, z + 10);
		cmd(s, "fill %d %d %d %d %d %d light_blue_stained_glass", x - 26, y + 2, z + 3, x - 26, y + 8, z + 9);
		// glass wall to shatter
		cmd(s, "fill %d %d %d %d %d %d glass", x - 10, y, z - 6, x - 10, y + 2, z - 2);
		// palm
		cmd(s, "fill %d %d %d %d %d %d jungle_log", x - 6, y, z + 8, x - 6, y + 6, z + 8);
		cmd(s, "fill %d %d %d %d %d %d jungle_leaves[persistent=true]", x - 8, y + 6, z + 6, x - 4, y + 6, z + 10);
		cmd(s, "fill %d %d %d %d %d %d jungle_leaves[persistent=true]", x - 7, y + 7, z + 7, x - 5, y + 7, z + 9);
		// street lamps
		for (int i = 0; i < 4; i++) {
			int lx = x - 4 - i * 8;
			cmd(s, "fill %d %d %d %d %d %d iron_bars", lx, y, z - 3, lx, y + 3, z - 3);
			cmd(s, "setblock %d %d %d glowstone", lx, y + 4, z - 3);
		}
		// beach and a shallow ocean further west
		cmd(s, "fill %d %d %d %d %d %d air", x - 100, y, z - 30, x - 41, y + 14, z - 1);
		cmd(s, "fill %d %d %d %d %d %d air", x - 100, y, z, x - 41, y + 14, z + 30);
		cmd(s, "fill %d %d %d %d %d %d sand", x - 48, y - 2, z - 30, x - 41, y - 1, z + 30);
		cmd(s, "fill %d %d %d %d %d %d sand", x - 100, y - 5, z - 30, x - 49, y - 5, z + 30);
		cmd(s, "fill %d %d %d %d %d %d water", x - 100, y - 4, z - 30, x - 49, y - 1, z + 30);
		cmd(s, "fill %d %d %d %d %d %d water", x - 52, y - 1, z - 6, x - 47, y - 1, z + 8);
		// neon on the pink building at night
		cmd(s, "fill %d %d %d %d %d %d magenta_stained_glass", x - 20, y + 4, z - 12, x - 20, y + 4, z - 4);
		cmd(s, "fill %d %d %d %d %d %d pearlescent_froglight", x - 21, y + 4, z - 11, x - 21, y + 4, z - 5);
		// debris tower: mixed materials
		cmd(s, "fill %d %d %d %d %d %d bricks", x - 8, y, z + 2, x - 5, y + 3, z + 5);
		cmd(s, "fill %d %d %d %d %d %d orange_terracotta", x - 8, y + 1, z + 2, x - 5, y + 1, z + 5);
		cmd(s, "fill %d %d %d %d %d %d white_concrete", x - 8, y + 3, z + 2, x - 5, y + 3, z + 5);
	}

	private static void cmd(TestServerContext s, String fmt, Object... args) {
		s.runCommand(String.format(fmt, args));
	}
}
