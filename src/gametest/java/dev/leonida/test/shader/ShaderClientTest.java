package dev.leonida.test.shader;

import dev.leonida.shader.ShaderApi;
import dev.leonida.shader.ShaderInstaller;
import dev.leonida.shader.ShaderModule;
import dev.leonida.test.TestFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Client game tests for the shader module. Owned by the shader agent. Run: tools/pod-test.sh shader
 *
 * Checks the pack was copied to shaderpacks and selected, that Iris compiled and uses it, then builds a small Leonida
 * street (glass tower, pastel Deco hotel with neon, road, beach and water, palms, a character) and takes screenshots at
 * noon, golden hour, dusk and night looking west into the sunset, a water close-up, and a shaders-off comparison.
 */
public class ShaderClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("shader")) return;
		Path packDir = FabricLoader.getInstance().getGameDir().resolve("shaderpacks").resolve(ShaderModule.PACK_NAME);
		boolean installed = Files.isRegularFile(packDir.resolve("shaders/shaders.properties"));
		System.out.println("[leonida-test] shader installer: " + ShaderInstaller.lastResult);
		System.out.println("[leonida-test] shader pack installed at " + packDir + ": " + installed);
		boolean vkFallback = dev.leonida.shader.early.ShaderGlGuard.vulkanFallbackActive();
		System.out.println("[leonida-test] shader OpenGL guard: " + dev.leonida.shader.early.ShaderGlGuard.lastResult() + " vulkanFallback=" + vkFallback);

		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			TestServerContext server = sp.getServer();
			String renderer = context.computeOnClient(mc -> com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().name() + " / " + com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().backendName());
			boolean irisLoaded = ShaderApi.irisLoaded();
			boolean inUse = context.computeOnClient(mc -> ShaderApi.shaderPackInUse());
			String pack = context.computeOnClient(mc -> ShaderApi.currentPackName());
			System.out.println("[leonida-test] shader renderer=" + renderer + " iris=" + irisLoaded + " packInUse=" + inUse + " pack='" + pack + "'");

			BlockPos o = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).blockPosition());
			int x = o.getX(), y = o.getY(), z = o.getZ();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @a");
			buildStreet(server, x, y, z);
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			// camera at the east end of the street looking west, where the sun sets
			String cam = String.format("tp @a %d.5 %d %d.5 90 -4", x + 8, y + 3, z + 1);
			server.runCommand(cam);
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender();

			shot(context, server, "shader-noon", 6000);
			shot(context, server, "shader-golden-hour", 11300);
			shot(context, server, "shader-sunset", 11850);
			shot(context, server, "shader-dusk", 12450);
			shot(context, server, "shader-blue-hour", 12900);
			shot(context, server, "shader-night", 18000);

			// water close up at golden hour: depth colour, foam, sun glitter, reflections of the tower
			server.runCommand(String.format("tp @a %d.5 %d %d.5 110 22", x + 2, y + 5, z + 4));
			context.waitTicks(10);
			shot(context, server, "shader-water-golden", 11300);
			// character rim light against the low sun
			server.runCommand(String.format("tp @a %d.5 %d %d.5 90 2", x + 1, y + 2, z + 1));
			context.waitTicks(10);
			shot(context, server, "shader-rim-golden", 11500);
			server.runCommand(cam);
			context.waitTicks(10);

			int fps = context.computeOnClient(mc -> mc.getFps());
			System.out.println("[leonida-test] shader fps with pack on: " + fps);

			// comparison: shaders off (vanilla renderer plus the fx colour grade)
			boolean off = context.computeOnClient(mc -> setShaders(false));
			context.waitTicks(20);
			shot(context, server, "shader-off-golden-hour", 11300);
			shot(context, server, "shader-off-night", 18000);
			context.computeOnClient(mc -> setShaders(true));
			context.waitTicks(20);
			System.out.println("[leonida-test] shader toggle via IrisApi worked: " + off);

			if (!installed) throw new AssertionError("pack not copied to " + packDir);
			if (vkFallback) {
				// OpenGL unavailable: the guard started the game on Vulkan with Iris in its safe mode. Reaching
				// this line without a crash is the point of the test.
				System.out.println("[leonida-test] shader: no OpenGL, game survived on " + renderer + " with shaders off");
				return;
			}
			if (!inUse || !pack.contains(ShaderModule.PACK_NAME)) {
				throw new AssertionError("Leonida Shaders not active in Iris (inUse=" + inUse + ", pack='" + pack + "'), see the log for shader compile errors");
			}
		}
		// the same pack on the real generated map
		ShaderTerrainShots.run(context);
	}

	private static void shot(ClientGameTestContext context, TestServerContext server, String name, int time) {
		server.runCommand("time set " + time);
		context.waitTicks(15);
		context.takeScreenshot(name);
	}

	private static boolean setShaders(boolean on) {
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object inst = api.getMethod("getInstance").invoke(null);
			Object cfg = api.getMethod("getConfig").invoke(inst);
			cfg.getClass().getMethod("setShadersEnabledAndApply", boolean.class).invoke(cfg, on);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.out.println("[leonida-test] shader toggle failed: " + e);
			return false;
		}
	}

	private static String pick(String leonida, String vanilla) {
		return BuiltInRegistries.BLOCK.containsKey(Identifier.parse(leonida)) ? leonida : vanilla;
	}

	private static void fill(TestServerContext s, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
		s.runCommand(String.format("fill %d %d %d %d %d %d %s", x1, y1, z1, x2, y2, z2, block));
	}

	private static void buildStreet(TestServerContext s, int x, int y, int z) {
		String towerGlass = pick("leonida:look/glass_teal", "minecraft:light_blue_stained_glass");
		String spandrel = pick("leonida:look/spandrel_teal", "minecraft:cyan_terracotta");
		String frame = pick("leonida:look/frame_silver", "minecraft:light_gray_concrete");
		String stucco = pick("leonida:look/stucco_pink", "minecraft:pink_concrete");
		String trim = pick("leonida:look/trim_white", "minecraft:white_concrete");
		String window = pick("leonida:look/window_deco", "minecraft:black_stained_glass");
		String neonPink = pick("leonida:look/neon_pink", "minecraft:pearlescent_froglight");
		String neonCyan = pick("leonida:look/neon_cyan", "minecraft:sea_lantern");
		String asphalt = pick("leonida:look/asphalt", "minecraft:black_concrete");
		String sand = pick("leonida:look/beach_sand", "minecraft:sand");
		// clear the air and lay a flat base
		for (int yy = y; yy < y + 44; yy += 8) fill(s, x - 44, yy, z - 18, x + 10, Math.min(yy + 7, y + 43), z + 18, "minecraft:air");
		fill(s, x - 44, y - 8, z - 18, x + 10, y - 1, z + 18, "minecraft:stone");
		fill(s, x - 44, y - 1, z - 18, x + 10, y - 1, z + 18, "minecraft:smooth_stone");
		// road with lines and sidewalks
		fill(s, x - 44, y - 1, z - 3, x + 10, y - 1, z + 3, asphalt);
		for (int xx = x - 44; xx <= x + 10; xx += 4) fill(s, xx, y - 1, z, xx + 1, y - 1, z, "minecraft:yellow_concrete");
		fill(s, x - 44, y - 1, z - 5, x + 10, y - 1, z - 4, "minecraft:light_gray_concrete");
		fill(s, x - 44, y - 1, z + 4, x + 10, y - 1, z + 5, "minecraft:light_gray_concrete");
		// beach and sea to the south, getting deeper away from the road
		fill(s, x - 44, y - 1, z + 6, x + 10, y - 1, z + 8, sand);
		for (int zz = z + 9; zz <= z + 18; zz++) {
			int depth = Math.min(1 + (zz - z - 9), 7);
			fill(s, x - 44, y - 1 - depth, zz, x + 10, y - 2 - depth, zz, sand);
			fill(s, x - 44, y - depth, zz, x + 10, y - 1, zz, "minecraft:water");
		}
		// glass curtain wall tower to the north west
		int tx = x - 32, tz = z - 15;
		fill(s, tx, y, tz, tx + 7, y + 38, tz + 7, towerGlass);
		fill(s, tx + 1, y, tz + 1, tx + 6, y + 38, tz + 6, "minecraft:air");
		for (int yy = y + 3; yy <= y + 38; yy += 4) fill(s, tx, yy, tz, tx + 7, yy, tz + 7, spandrel);
		for (int[] c : new int[][] {{0, 0}, {7, 0}, {0, 7}, {7, 7}}) fill(s, tx + c[0], y, tz + c[1], tx + c[0], y + 38, tz + c[1], frame);
		fill(s, tx, y + 39, tz, tx + 7, y + 40, tz + 7, frame);
		// pastel Deco hotel with white trim, eyebrow band and neon
		int hx = x - 20, hz = z - 15;
		fill(s, hx, y, hz, hx + 10, y + 12, hz + 8, stucco);
		fill(s, hx + 1, y, hz + 1, hx + 9, y + 11, hz + 7, "minecraft:air");
		for (int yy = y + 2; yy <= y + 10; yy += 3) {
			for (int xx = hx + 1; xx <= hx + 9; xx += 2) s.runCommand(String.format("setblock %d %d %d %s", xx, yy, hz + 8, window));
			fill(s, hx, yy + 1, hz + 8, hx + 10, yy + 1, hz + 8, trim);
		}
		fill(s, hx, y + 12, hz, hx + 10, y + 13, hz + 8, trim);
		fill(s, hx + 5, y + 1, hz + 9, hx + 5, y + 12, hz + 9, trim);
		fill(s, hx + 1, y + 13, hz + 9, hx + 9, y + 13, hz + 9, neonPink);
		fill(s, hx + 4, y + 2, hz + 10, hx + 4, y + 11, hz + 10, neonCyan);
		fill(s, hx + 6, y + 2, hz + 10, hx + 6, y + 11, hz + 10, neonPink);
		// palms along the beach
		for (int px : new int[] {x - 6, x - 16, x - 26}) {
			fill(s, px, y, z + 6, px, y + 7, z + 6, "minecraft:jungle_log");
			fill(s, px - 2, y + 7, z + 5, px + 2, y + 7, z + 7, "minecraft:jungle_leaves[persistent=true]");
			fill(s, px - 1, y + 8, z + 5, px + 1, y + 8, z + 7, "minecraft:jungle_leaves[persistent=true]");
		}
		// street lamps
		for (int lx : new int[] {x - 4, x - 20, x - 36}) {
			fill(s, lx, y, z - 4, lx, y + 4, z - 4, "minecraft:iron_bars");
			s.runCommand(String.format("setblock %d %d %d minecraft:sea_lantern", lx, y + 5, z - 4));
		}
		// a character to show the rim light
		s.runCommand(String.format("summon minecraft:villager %d.5 %d %d.5 {NoAI:1b,Rotation:[-90f,0f],VillagerData:{type:\"minecraft:desert\"}}", x - 4, y, z + 1));
		s.runCommand(String.format("summon minecraft:husk %d.5 %d %d.5 {NoAI:1b,Rotation:[-90f,0f]}", x - 5, y, z + 3));
	}
}
