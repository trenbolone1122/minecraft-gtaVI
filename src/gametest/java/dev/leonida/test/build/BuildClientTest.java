package dev.leonida.test.build;

import dev.leonida.test.TestFilter;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Client game tests for the build module. Owned by the build agent. Run: tools/pod-test.sh build */
public class BuildClientTest implements FabricClientGameTest {
	private static final String[] KINDS = {"house", "apartment", "tower", "shop", "gas_station", "motel", "club", "office", "warehouse",
		"bank", "hotel", "mall", "police", "hospital", "marina", "gun_shop"};
	private static final String[] LANDMARKS = {"jason_house", "lucia_home", "el_gas_station", "club_vice", "gym", "office_floor", "motel_room"};

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("build")) return;
		realMap(context);
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> {
			ui.setAllowCommands(true);
			for (WorldCreationUiState.WorldTypeEntry e : ui.getNormalPresetList()) if (e.preset() != null && e.preset().is(WorldPresets.FLAT)) ui.setWorldType(e);
			for (WorldCreationUiState.WorldTypeEntry e : ui.getAltPresetList()) if (e.preset() != null && e.preset().is(WorldPresets.FLAT)) ui.setWorldType(e);
		}).create()) {
			sp.getConnection().waitForChunksRender();
			context.getInput().pressKey(o -> o.keyToggleGui);
			cmd(sp, "time set noon");
			cmd(sp, "weather clear");
			cmd(sp, "gamerule advance_time false");
			cmd(sp, "gamemode spectator @a");
			// LOOK.md street rows: downtown and Ocean Drive, day, dusk and night
			cmd(sp, "tp @a 0 -60 -200");
			context.waitTicks(5);
			cmd(sp, "execute as @p at @p run leonida_build street downtown");
			cmd(sp, "tp @a 0 -60 -420");
			context.waitTicks(5);
			cmd(sp, "execute as @p at @p run leonida_build street oceandrive");
			context.waitTicks(20);
			context.runOnClient(mc -> mc.options.fov().set(70));
			String[][] times = {{"day", "noon"}, {"dusk", "12900"}, {"night", "midnight"}};
			for (String[] t : times) {
				cmd(sp, "time set " + t[1]);
				for (int v : new int[] {0, 1, 2, 3}) view(context, sp, "downtown", v, t[0]);
				for (int v : new int[] {0, 1}) view(context, sp, "oceandrive", v, t[0]);
			}
			cmd(sp, "time set noon");
			cmd(sp, "tp @a 0 -60 0");
			context.waitTicks(5);
			cmd(sp, "execute as @p at @p run leonida_build showcase");
			context.waitTicks(20);
			for (String n : KINDS) shoot(context, sp, n, 2);
			for (String n : LANDMARKS) shoot(context, sp, n, 4);
			// elevator: stand on the tower's ground floor pad and jump
			cmd(sp, "gamemode survival @a");
			cmd(sp, "execute as @p run leonida_build tppad tower");
			context.waitTicks(10);
			double y0 = py(sp);
			context.getInput().holdKeyFor(o -> o.keyJump, 3);
			context.waitTicks(15);
			double y1 = py(sp);
			System.out.println("[leonida-build-test] elevator jump: y " + y0 + " -> " + y1 + (y1 > y0 + 2 ? " OK" : " FAILED"));
			context.runOnClient(mc -> mc.player.setXRot(10f));
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("build-elevator-up");
			context.getInput().holdShift();
			context.waitTicks(5);
			context.getInput().releaseShift();
			context.waitTicks(10);
			double y2 = py(sp);
			System.out.println("[leonida-build-test] elevator sneak: y " + y1 + " -> " + y2 + (y2 < y1 - 2 ? " OK" : " FAILED"));
		}
	}

	/** Buildings at their real map positions: downtown (-276, 470) and Ocean Drive (2030, -1000), day and night. */
	private static void realMap(ClientGameTestContext context) {
		dev.leonida.core.api.MapQuery q = dev.leonida.core.api.MapQuery.get();
		if (q == dev.leonida.core.api.MapQuery.FLAT) { System.out.println("[leonida-build-test] real map: MapQuery not installed, skipped"); return; }
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			context.getInput().pressKey(o -> o.keyToggleGui);
			context.runOnClient(mc -> { mc.options.renderDistance().set(16); mc.options.fov().set(70); });
			cmd(sp, "gamemode spectator @a");
			cmd(sp, "gamerule advance_time false");
			cmd(sp, "weather clear");
			int[][] cams = {
				// x, z, dy above ground, yaw, pitch
				{-276, 470, 2, 0, -12}, {-276, 470, 2, 90, -12}, {-276, 470, 2, 180, -12}, {-276, 470, 2, -90, -12}, {-356, 390, 90, -45, 30},
				{2030, -1000, 2, 0, -5}, {2030, -1000, 2, 90, -5}, {2030, -1000, 2, 180, -5}, {2030, -1000, 2, -90, -5}, {1960, -1070, 70, -45, 30}};
			String[] names = {"downtown-n", "downtown-w", "downtown-s", "downtown-e", "downtown-aerial", "oceandrive-n", "oceandrive-w", "oceandrive-s", "oceandrive-e", "oceandrive-aerial"};
			for (String[] t : new String[][] {{"day", "noon"}, {"night", "midnight"}}) {
				cmd(sp, "time set " + t[1]);
				for (int i = 0; i < cams.length; i++) {
					int[] c = cams[i];
					if (t[0].equals("night") && i % 5 == 1) continue;
					int g = q.groundY(c[0], c[1]);
					int cx = c[0] >> 4, cz = c[1] >> 4;
					cmd(sp, "execute in leonida:world/leonida run forceload add " + ((cx - 6) << 4) + " " + ((cz - 6) << 4) + " " + ((cx + 6) << 4) + " " + ((cz + 6) << 4));
					cmd(sp, "execute in leonida:world/leonida run tp @a " + c[0] + " " + (g + c[2]) + " " + c[1] + " " + c[3] + " " + c[4]);
					for (int k = 0; k < 120; k++) {
						context.waitTicks(10);
						boolean ok = context.computeOnClient(mc -> mc.level != null && mc.levelRenderer.hasRenderedAllSections());
						if (ok && k > 6) break;
					}
					context.waitTicks(20);
					System.out.println("[leonida-build-test] real map " + names[i] + " at " + c[0] + "," + (g + c[2]) + "," + c[1]);
					context.takeScreenshot("build-map-" + names[i] + "-" + t[0]);
					cmd(sp, "execute in leonida:world/leonida run forceload remove all");
				}
			}
		}
	}

	private static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}

	private static double py(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(server -> {
			ServerPlayer p = server.getPlayerList().getPlayers().get(0);
			return p.getY();
		});
	}

	private static void view(ClientGameTestContext context, TestSingleplayerContext sp, String name, int v, String time) {
		cmd(sp, "execute as @p run leonida_build view " + name + " " + v);
		context.waitTicks(10);
		sp.getConnection().waitForChunksRender();
		context.waitTicks(5);
		context.takeScreenshot("build-street-" + name + "-" + v + "-" + time);
	}

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext sp, String name, int rooms) {
		cmd(sp, "gamemode spectator @a");
		cmd(sp, "execute as @p run leonida_build tp " + name);
		context.waitTicks(10);
		sp.getConnection().waitForChunksRender();
		context.runOnClient(mc -> mc.options.fov().set(70));
		context.waitTicks(5);
		context.takeScreenshot("build-ext-" + name);
		context.runOnClient(mc -> mc.options.fov().set(85));
		int n = sp.getServer().computeOnServer(server -> dev.leonida.build.BuildCommand.roomCount(name));
		System.out.println("[leonida-build-test] " + name + " rooms: " + n);
		for (int i = 0; i < Math.min(rooms, n); i++) {
			int idx = i == 0 ? 0 : i * n / Math.min(rooms, n);
			cmd(sp, "execute as @p run leonida_build tproom " + name + " " + idx);
			context.waitTicks(8);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("build-int-" + name + "-" + i);
		}
	}
}
