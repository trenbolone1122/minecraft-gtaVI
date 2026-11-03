package dev.leonida.test.hud;

import dev.leonida.fx.FxConfig;
import dev.leonida.gameplay.npc.PedestrianSpawner;
import dev.leonida.gameplay.police.PoliceDispatch;
import dev.leonida.hud.LeonidaSettings;
import dev.leonida.hud.Minimap;
import dev.leonida.hud.SettingsScreen;
import dev.leonida.test.TestFilter;
import dev.leonida.vehicle.ai.TrafficManager;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.Screen;

/** Leonida Settings screen: every control changes the real target value. Run: tools/pod-test.sh settings */
public class SettingsClientTest implements FabricClientGameTest {
	private static void check(boolean ok, String what) {
		System.out.println("[leonida-test] settings " + what + ": " + (ok ? "OK" : "FAILED"));
		if (!ok) throw new AssertionError("settings: " + what);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("settings")) return;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			context.runOnClient(mc -> mc.gui.setScreen(new SettingsScreen(null)));
			context.waitTicks(10);
			context.runOnClient(mc -> {
				SettingsScreen s = (SettingsScreen) mc.gui.screen();
				LeonidaSettings cfg = LeonidaSettings.get();
				s.change("preset", false); s.change("preset", false); // Low
				check(cfg.preset == LeonidaSettings.Level.LOW && TrafficManager.density == 5 && PoliceDispatch.maxUnits == 6, "preset low sets traffic " + TrafficManager.density + " police " + PoliceDispatch.maxUnits);

				int t0 = TrafficManager.density; s.change("traffic", true);
				check(TrafficManager.density > t0, "traffic density " + t0 + " -> " + TrafficManager.density);
				int p0 = PedestrianSpawner.density; s.change("peds", true);
				check(PedestrianSpawner.density > p0, "ped density " + p0 + " -> " + PedestrianSpawner.density);
				int c0 = PoliceDispatch.maxUnits; s.change("police", true);
				check(PoliceDispatch.maxUnits == c0 + 2, "police cap " + c0 + " -> " + PoliceDispatch.maxUnits);
				FxConfig.Quality q0 = FxConfig.get().quality; s.change("effects", true);
				check(FxConfig.get().quality.ordinal() == q0.ordinal() + 1, "fx quality " + q0 + " -> " + FxConfig.get().quality);
				boolean d0 = FxConfig.get().miningDebris; s.change("debris", true);
				check(FxConfig.get().miningDebris != d0, "fx debris " + d0 + " -> " + FxConfig.get().miningDebris);
				boolean m0 = cfg.minimap; s.change("minimap", true);
				check(cfg.minimap != m0, "minimap " + m0 + " -> " + cfg.minimap);
				s.change("minimap", true);
				int r0 = cfg.minimapRate; s.change("minimap_rate", true);
				check(cfg.minimapRate > r0, "minimap rate " + r0 + " -> " + cfg.minimapRate);
				boolean b0 = LeonidaSettings.buildCacheEnabled(); s.change("buildings", true);
				check(LeonidaSettings.buildCacheEnabled() != b0, "build cache " + b0 + " -> " + LeonidaSettings.buildCacheEnabled());
				boolean h0 = cfg.distantHorizons; Boolean dh0 = LeonidaSettings.dhRendering(); s.change("dh", true);
				Boolean dh1 = LeonidaSettings.dhRendering();
				check(cfg.distantHorizons != h0 && (dh1 == null || !dh1.equals(dh0)), "DH flag " + h0 + " -> " + cfg.distantHorizons + " (DH config " + dh0 + " -> " + dh1 + ")");
				int rad0 = cfg.dhRadius; s.change("dh_radius", true);
				check(cfg.dhRadius > rad0, "DH radius " + rad0 + " -> " + cfg.dhRadius + " (DH config " + LeonidaSettings.dhRadiusNow() + ")");
				s.change("shader", true); s.change("shader", true); // High
				String txt;
				try { txt = Files.readString(LeonidaSettings.shaderOptionsFile()); } catch (Exception e) { txt = ""; }
				check(cfg.shaderProfile == LeonidaSettings.Level.HIGH && txt.contains("shadowMapResolution=2048"), "shader profile HIGH written to " + LeonidaSettings.shaderOptionsFile().getFileName());
				s.change("shader", false); // back to Medium for the screenshot
				check(Files.exists(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("leonida.json")), "saved config/leonida.json");
			});
			long n0 = context.computeOnClient(mc -> Minimap.rasterCount);
			context.setScreen(() -> (Screen) null);
			context.waitTicks(40);
			long n1 = context.computeOnClient(mc -> Minimap.rasterCount);
			check(n1 > n0, "minimap redraws " + (n1 - n0) + " in 40 ticks at " + LeonidaSettings.get().minimapRate + " per second");
			context.runOnClient(mc -> mc.gui.setScreen(new SettingsScreen(null)));
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("leonida-settings").withSize(1920, 1080));
			context.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(null, mc.options)));
			context.waitTicks(10);
			context.takeScreenshot(TestScreenshotOptions.of("leonida-options-button"));
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}
}
