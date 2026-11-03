package dev.leonida.test.hud;

import dev.leonida.core.PlayerState;
import dev.leonida.core.api.AmmoProvider;
import dev.leonida.core.api.MapQuery;
import dev.leonida.hud.Blips;
import dev.leonida.hud.HudNet;
import dev.leonida.hud.MapScreen;
import dev.leonida.hud.Phone;
import dev.leonida.test.TestFilter;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.server.level.ServerPlayer;

/** Client game tests for the hud module. Owned by the hud agent. Run: tools/pod-test.sh hud */
public class HudClientTest implements FabricClientGameTest {
	private static String weapon = "pistol";
	private static int clip = 17, reserve = 80;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("hud")) return;
		if (AmmoProvider.get() == AmmoProvider.NONE)
			AmmoProvider.install((p, s) -> s.isEmpty() ? null : new AmmoProvider.Ammo(clip, reserve, weapon));
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			sp.getServer().runCommand("time set 6000");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamerule doDaylightCycle false");
			sp.getServer().runCommand("give @a minecraft:crossbow");
			context.runOnClient(mc -> {
				double x = mc.player.getX(), z = mc.player.getZ();
				Blips.put("test_home", Blips.Type.HOME, x + 50, z + 40);
				Blips.put("test_gun", Blips.Type.GUN, x - 70, z - 260);
				Blips.put("test_shop", Blips.Type.SHOP, x + 140, z - 30);
				Blips.put("test_bar", Blips.Type.BAR, x - 20, z + 120);
				Blips.put("test_mission", Blips.Type.MISSION, x + 300, z + 200);
			});
			context.waitTicks(80);
			shot(context, "hud-idle");

			server(sp, p -> {
				PlayerState s = PlayerState.get(p);
				PlayerState.set(p, s.withWanted(4).withKnownFlags(PlayerState.KNOWN_FACE | PlayerState.KNOWN_CLOTHES | PlayerState.KNOWN_PERSON | PlayerState.KNOWN_VEHICLE)
						.withLiveViewers(51000).withMoney(s.money() + 1240));
			});
			context.waitTicks(60);
			shot(context, "hud-wanted");
			server(sp, p -> {
				HudNet.searching(p, true);
				PlayerState.set(p, PlayerState.get(p).withWanted(2).withKnownFlags(PlayerState.KNOWN_CLOTHES | PlayerState.KNOWN_VEHICLE));
			});
			context.waitTicks(30);
			shot(context, "hud-search");

			weapon = "rifle";
			clip = 36;
			reserve = 268;
			server(sp, p -> {
				HudNet.searching(p, false);
				PlayerState.set(p, PlayerState.get(p).withWanted(0).withKnownFlags(0).withLiveViewers(0));
				HudNet.waypoint(p, (int) p.getX() + 260, (int) p.getZ() - 420);
			});
			context.waitTicks(60);
			shot(context, "hud-gps");

			server(sp, p -> {
				HudNet.text(p, "Lucia", "GUYS EVERYWHERE");
				HudNet.toast(p, "Aim at the clerk to hold up the store");
			});
			context.waitTicks(20);
			server(sp, p -> HudNet.text(p, "Jason", "I see them. Get to the car, I'll swing around the back."));
			context.waitTicks(15);
			shot(context, "hud-text");

			server(sp, p -> {
				HudNet.mission(p, "STORE ROBBED", "+$1,240");
				PlayerState.set(p, PlayerState.get(p).withMoney(PlayerState.get(p).money() + 1240));
			});
			context.waitTicks(20);
			shot(context, "hud-mission");
			context.waitTicks(120);

			context.setScreen(MapScreen::new);
			context.waitTicks(80);
			shot(context, "hud-map");
			context.setScreen(() -> null);

			context.setScreen(Phone::new);
			context.waitTicks(20);
			shot(context, "hud-phone");
			context.setScreen(() -> new Phone(Phone.App.MESSAGES));
			context.waitTicks(10);
			shot(context, "hud-phone-messages");
			context.setScreen(() -> new Phone(Phone.App.BLAST));
			context.waitTicks(10);
			shot(context, "hud-phone-blast");
			context.setScreen(() -> null);

			server(sp, HudNet::busted);
			context.waitTicks(30);
			shot(context, "hud-busted");
			context.waitTicks(130);
			context.runOnClient(mc -> dev.leonida.hud.HudClient.handle(new HudNet.Event(HudNet.WASTED, "", "", 0, 0)));
			context.waitTicks(30);
			shot(context, "hud-wasted");
			context.waitTicks(140);

			leonida(context, sp);
		}
	}

	/** Real Leonida map: full map at three zooms and two GUI scales (1920x1080), minimap over a Keys bridge and Vice Beach. */
	private static void leonida(ClientGameTestContext context, TestSingleplayerContext sp) {
		if (MapQuery.get() == MapQuery.FLAT) throw new AssertionError("real MapQuery not installed");
		sp.getServer().runCommand("gamemode creative @a");
		context.runOnClient(mc -> { Blips.clear(); mc.options.renderDistance().set(8); mc.getWindow().setWindowed(1920, 1080); });
		context.waitTicks(20);
		context.runOnClient(mc -> System.out.println("[hud test] window " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()));
		// Vice Beach / Ocean Drive, heading north, GPS to downtown
		tp(context, sp, 2030, -1000, 180, -276, 470);
		shot1080(context, "hud-leonida-minimap-vice-beach");
		String[] names = { "city", "district", "street" };
		double[] zooms = { 12, 3, 0.6 };
		for (int scale : new int[] { 2, 4 }) {
			context.runOnClient(mc -> { mc.options.guiScale().set(scale); mc.resizeGui(); });
			for (int i = 0; i < 3; i++) {
				double z = zooms[i];
				context.runOnClient(mc -> MapScreen.setZoom(z));
				context.setScreen(MapScreen::new);
				int waited = 0;
				context.waitTicks(10);
				while (waited < 600 && context.computeOnClient(mc -> MapScreen.lastMisses) != 0) { context.waitTicks(10); waited += 10; }
				int miss = context.computeOnClient(mc -> MapScreen.lastMisses);
				if (miss != 0) throw new AssertionError("full map texture incomplete at zoom " + names[i] + " gui " + scale + ": " + miss + " empty texels");
				System.out.println("[hud test] full map " + names[i] + " gui " + scale + " complete after " + waited + " ticks");
				shot1080(context, "hud-leonida-map-" + names[i] + "-gui" + scale);
			}
			context.setScreen(() -> null);
		}
		context.runOnClient(mc -> { mc.options.guiScale().set(0); mc.resizeGui(); });
		// southernmost bridge (Keys): a road sample over water
		int[] br = findBridge();
		System.out.println("[hud test] keys bridge at " + br[0] + ", " + br[1]);
		tp(context, sp, br[0], br[1], 180, 2030, -1000);
		shot1080(context, "hud-leonida-minimap-keys-bridge");
	}

	private static int[] findBridge() {
		MapQuery q = MapQuery.get();
		int[] best = { 0, 0 };
		int bestZ = Integer.MIN_VALUE;
		for (int z = -9000; z <= 9000; z += 24)
			for (int x = -9000; x <= 9000; x += 24) {
				int c = q.mapColor(x, z);
				if ((c == 0xFFE2E2DC || c == 0xFFE6D990) && z > bestZ) {
					bestZ = z; best = new int[] { x, z };
				}
			}
		if (bestZ == Integer.MIN_VALUE) throw new AssertionError("no bridge over water found");
		return best;
	}

	private static void shot1080(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1920, 1080));
	}

	private static void tp(ClientGameTestContext context, TestSingleplayerContext sp, int x, int z, int yaw, int wx, int wz) {
		int y = MapQuery.get().groundY(x, z) + 2;
		sp.getServer().runCommand("execute in leonida:world/leonida run tp @a " + x + " " + y + " " + z + " " + yaw + " 10");
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload add " + (x - 48) + " " + (z - 48) + " " + (x + 48) + " " + (z + 48));
		context.waitTicks(40);
		server(sp, p -> { p.getAbilities().flying = true; p.onUpdateAbilities(); HudNet.waypoint(p, wx, wz); });
		context.waitTicks(200);
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.takeScreenshot(name);
	}

	private static void server(TestSingleplayerContext sp, Consumer<ServerPlayer> action) {
		sp.getServer().runOnServer(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) action.accept(p);
		});
	}
}
