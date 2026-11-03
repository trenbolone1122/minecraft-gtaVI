package dev.leonida.test.gameplay;

import dev.leonida.test.TestFilter;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import dev.leonida.core.api.MapQuery;
import net.minecraft.client.CameraType;

/** Client game tests for the gameplay module. Owned by the gameplay agent. Run: tools/pod-test.sh gameplay */
public class GameplayClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("gameplay")) return;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			TestServerContext server = sp.getServer();
			cmd(server, "gamerule send_command_feedback false");
			cmd(server, "gamerule sendCommandFeedback false");
			cmd(server, "difficulty normal");
			cmd(server, "time set noon");
			cmd(server, "weather clear");
			cmd(server, "gamemode survival @a");
			cmd(server, "effect give @a minecraft:resistance 600 3 true");
			player(server, "gta peds off");
			player(server, "gta money 1200");
			cmd(server, "tp @a 0 -60 0 0 5");
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender();

			// Pedestrians walking, then fleeing and calling/filming after gunfire.
			player(server, "gta spawn ped 10");
			context.waitTicks(60);
			context.takeScreenshot("gameplay-01-peds");
			player(server, "gta crime GUNSHOT");
			context.waitTicks(20);
			context.takeScreenshot("gameplay-02-peds-fleeing");
			context.waitTicks(80);
			context.takeScreenshot("gameplay-03-peds-phones");
			context.waitTicks(160);
			context.takeScreenshot("gameplay-04-witness-report");

			// Robbery: armed player aims at the clerk.
			player(server, "gta clearpolice");
			cmd(server, "kill @e[type=leonida:gameplay/pedestrian]");
			cmd(server, "kill @e[type=minecraft:item]");
			cmd(server, "tp @a 40 -60 0 0 8");
			cmd(server, "item replace entity @a weapon.mainhand with leonida:weapon/pistol");
			context.waitTicks(20);
			player(server, "gta spawn clerk");
			context.waitTicks(60);
			context.takeScreenshot("gameplay-05-robbery-hands-up");
			debug(server, "robbery");
			context.waitTicks(100);
			context.takeScreenshot("gameplay-06-robbery-cash");
			context.waitTicks(120);
			context.takeScreenshot("gameplay-07-robbery-done");

			// Stars rising.
			player(server, "gta clearpolice");
			cmd(server, "kill @e[type=leonida:gameplay/clerk]");
			cmd(server, "tp @a 80 -60 0 0 5");
			cmd(server, "item replace entity @a weapon.mainhand with minecraft:air");
			context.waitTicks(20);
			player(server, "gta wanted 1");
			context.waitTicks(25);
			context.takeScreenshot("gameplay-08-stars-1");
			player(server, "gta wanted 3");
			context.waitTicks(25);
			context.takeScreenshot("gameplay-09-stars-3");

			// Police chase, seen from behind the player.
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			player(server, "gta clearpolice");
			player(server, "gta wanted 2");
			player(server, "gta spawn vcpd 2");
			player(server, "gta spawn sheriff 1");
			cmd(server, "tp @a 80 -60 -6 0 10");
			context.waitTicks(30);
			context.takeScreenshot("gameplay-10-police-chase");
			debug(server, "chase");
			context.waitTicks(60);
			context.takeScreenshot("gameplay-11-police-arrest");
			context.waitTicks(120);
			context.takeScreenshot("gameplay-12-busted");

			// Armed patrol officers (weapon module pistols/shotguns) at three stars.
			player(server, "gta clearpolice");
			cmd(server, "tp @a 100 -60 0 0 5");
			context.waitTicks(20);
			player(server, "gta wanted 3");
			player(server, "gta spawn vcpd 3");
			cmd(server, "tp @a 100 -60 -10 0 8");
			context.waitTicks(30);
			context.takeScreenshot("gameplay-13-vcpd-shooting");

			// Heavy response: SWAT shooting at five stars.
			player(server, "gta clearpolice");
			cmd(server, "tp @a 120 -60 0 0 5");
			context.waitTicks(20);
			player(server, "gta wanted 5");
			player(server, "gta spawn swat 3");
			cmd(server, "tp @a 120 -60 -8 0 10");
			context.waitTicks(40);
			context.takeScreenshot("gameplay-14-swat");
			context.waitTicks(60);
			context.takeScreenshot("gameplay-15-swat-shooting");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

			// Police arriving by themselves: they must appear off-screen and walk in.
			player(server, "gta clearpolice");
			cmd(server, "tp @a 160 -60 0 180 0");
			context.waitTicks(20);
			player(server, "gta wanted 2");
			context.waitTicks(40);
			debug(server, "arrival-spawn");
			context.waitTicks(160);
			debug(server, "arrival-later");
			context.takeScreenshot("gameplay-16-police-arriving");
			player(server, "gta clearpolice");

			// Street with a crosswalk: pedestrians walk to it, wait at the curb, cross.
			MapQuery previous = MapQuery.get();
			MapQuery.install(new StreetMap(previous));
			cmd(server, "fill 200 -61 0 260 -61 3 minecraft:smooth_stone");
			cmd(server, "fill 200 -61 4 260 -61 11 minecraft:gray_concrete");
			cmd(server, "fill 200 -61 12 260 -61 15 minecraft:smooth_stone");
			for (int x = 228; x <= 232; x += 2) cmd(server, "fill " + x + " -61 4 " + x + " -61 11 minecraft:white_concrete");
			cmd(server, "kill @e[type=leonida:gameplay/pedestrian]");
			cmd(server, "gamemode spectator @a");
			cmd(server, "tp @a 230 -47 -7 0 52");
			context.waitTicks(20);
			for (int i = 0; i < 8; i++) cmd(server, "execute positioned " + (214 + i * 4) + " -60 1.5 run summon leonida:gameplay/pedestrian");
			context.waitTicks(400);
			context.takeScreenshot("gameplay-17-crosswalk");
			context.waitTicks(300);
			context.takeScreenshot("gameplay-18-crosswalk-later");
			server.runOnServer(ms -> {
				var p = ms.getPlayerList().getPlayers().get(0);
				int near = 0, far = 0, road = 0;
				for (var e : p.level().getEntitiesOfClass(dev.leonida.gameplay.npc.Pedestrian.class, p.getBoundingBox().inflate(64))) {
					double z = e.getZ();
					if (z < 4) near++; else if (z < 12) road++; else far++;
					System.out.println("[leonida-test] gameplay   crosswalk ped at " + e.position());
				}
				System.out.println("[leonida-test] gameplay crosswalk near=" + near + " road=" + road + " far=" + far);
			});
			MapQuery.install(previous);
			cmd(server, "gamemode survival @a");

			// Pursuit vehicles: cruiser with its crew at 2 stars, Police Maverick at 3+, riot van at 4+.
			cmd(server, "kill @e[type=leonida:gameplay/pedestrian]");
			player(server, "gta peds off");
			cmd(server, "effect give @a minecraft:resistance 9999 255 true");
			cmd(server, "effect give @a minecraft:regeneration 9999 5 true");
			cmd(server, "tp @a 300 -60 0 0 10");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(20);
			player(server, "gta wanted 2 true");
			for (int i = 0; i < 2; i++) {
				context.waitTicks(40);
				vehicles(server, "cars-2-t" + i);
			}
			lookAtNearestVehicle(server, false);
			context.waitTicks(5);
			context.takeScreenshot("gameplay-19-cruiser-chase");
			for (int i = 2; i < 5; i++) {
				context.waitTicks(40);
				vehicles(server, "cars-2-t" + i);
			}
			lookAtNearestVehicle(server, false);
			context.waitTicks(5);
			context.takeScreenshot("gameplay-20-cruiser-crew-out");
			debug(server, "cruiser");

			player(server, "gta wanted 4 true");
			for (int i = 0; i < 6; i++) {
				context.waitTicks(50);
				vehicles(server, "cars-4-t" + i);
			}
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			lookAtNearestVehicle(server, true);
			context.waitTicks(5);
			context.takeScreenshot("gameplay-21-police-maverick");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			lookAtNearestRiot(server);
			context.waitTicks(5);
			context.takeScreenshot("gameplay-22-swat-riot-van");
			player(server, "gta wanted 0");
			player(server, "gta clearpolice");
			context.waitTicks(20);

			// Traffic with people in it: a civilian car gets a driver seated out of view.
			cmd(server, "tp @a 360 -60 0 0 10");
			context.waitTicks(10);
			server.runOnServer(ms -> {
				var p = ms.getPlayerList().getPlayers().get(0);
				var v = dev.leonida.vehicle.ai.TrafficManager.spawnDriving(p.level(), p.position().add(0, 0, -40), p.getRandom());
				System.out.println("[leonida-test] gameplay traffic car " + v);
			});
			player(server, "gta peds 4");
			context.waitTicks(100);
			vehicles(server, "traffic");
			cmd(server, "tp @a 360 -60 -32 180 15");
			context.waitTicks(20);
			context.takeScreenshot("gameplay-23-traffic-driver");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		}
	}

	/** Test street: sidewalk z 0..3, road z 4..11 (crosswalk stripes at x 228..232), sidewalk z 12..15, around x 200..260. */
	private record StreetMap(MapQuery base) implements MapQuery {
		public Surface surfaceAt(int x, int z) {
			if (x < 200 || x > 260 || z < 0 || z > 15) return base.surfaceAt(x, z);
			return z <= 3 || z >= 12 ? Surface.SIDEWALK : Surface.ROAD;
		}
		public int groundY(int x, int z) { return base.groundY(x, z); }
		public int mapColor(int x, int z) { return base.mapColor(x, z); }
		public java.util.List<net.minecraft.core.BlockPos> route(net.minecraft.core.BlockPos a, net.minecraft.core.BlockPos b) { return base.route(a, b); }
		public String placeName(int x, int z) { return base.placeName(x, z); }
	}

	private static void vehicles(TestServerContext server, String label) {
		server.runOnServer(ms -> {
			var p = ms.getPlayerList().getPlayers().get(0);
			for (var e : p.level().getEntities((net.minecraft.world.entity.Entity) null, p.getBoundingBox().inflate(160, 80, 160),
					e -> e instanceof dev.leonida.vehicle.VehicleEntity || e.getType().toString().contains("aircraft"))) {
				StringBuilder sb = new StringBuilder();
				for (var r : e.getPassengers()) sb.append(r.getType()).append(' ');
				System.out.println("[leonida-test] gameplay " + label + " vehicle " + e.getType() + " at " + e.position() + " dist=" + (int) e.distanceTo(p)
						+ (e instanceof dev.leonida.vehicle.VehicleEntity v ? " spec=" + v.spec.id() + " sheriff=" + dev.leonida.vehicle.VehicleApi.isSheriff(v) : "")
						+ " riders=[" + sb + "]");
			}
		});
	}

	private static void lookAt(net.minecraft.server.level.ServerPlayer p, net.minecraft.world.phys.Vec3 t) {
		net.minecraft.world.phys.Vec3 d = t.subtract(p.getEyePosition());
		float yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90f;
		float pitch = (float) -(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
		p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, pitch);
	}

	private static void lookAtNearestVehicle(TestServerContext server, boolean aircraft) {
		server.runOnServer(ms -> {
			var p = ms.getPlayerList().getPlayers().get(0);
			var list = p.level().getEntities((net.minecraft.world.entity.Entity) null, p.getBoundingBox().inflate(160, 80, 160),
					e -> aircraft ? e.getType().toString().contains("aircraft") : e instanceof dev.leonida.vehicle.VehicleEntity);
			list.stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p))).ifPresent(e -> lookAt(p, e.position()));
		});
	}

	private static void lookAtNearestRiot(TestServerContext server) {
		server.runOnServer(ms -> {
			var p = ms.getPlayerList().getPlayers().get(0);
			p.level().getEntitiesOfClass(dev.leonida.vehicle.VehicleEntity.class, p.getBoundingBox().inflate(160, 80, 160),
					v -> v.spec == dev.leonida.vehicle.VehicleTypes.POLICE_RIOT).stream()
					.min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p))).ifPresent(e -> lookAt(p, e.position()));
		});
	}

	private static void debug(TestServerContext server, String label) {
		server.runOnServer(ms -> {
			var p = ms.getPlayerList().getPlayers().get(0);
			var st = dev.leonida.core.PlayerState.get(p);
			System.out.println("[leonida-test] gameplay " + label + " player=" + p.position() + " wanted=" + st.wanted() + " flags=" + st.knownFlags()
					+ " money=" + st.money() + " searching=" + dev.leonida.gameplay.wanted.WantedSystem.isSearching(p)
					+ " lethal=" + dev.leonida.gameplay.wanted.WantedSystem.lethalForce(p) + " armed=" + dev.leonida.gameplay.util.Compat.isArmed(p));
			for (var e : p.level().getEntitiesOfClass(dev.leonida.gameplay.npc.LeonidaNpc.class, p.getBoundingBox().inflate(64)))
				System.out.println("[leonida-test] gameplay   npc " + e.getType() + " at " + e.position() + " pose=" + e.npcPose() + " dist=" + e.distanceTo(p));
		});
	}

	private static void cmd(TestServerContext server, String c) {
		server.runCommand(c);
	}

	private static void player(TestServerContext server, String c) {
		server.runCommand("execute as @p at @p run " + c);
	}
}
