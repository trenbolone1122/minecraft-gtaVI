package dev.leonida.test.vehicle;

import dev.leonida.core.api.MapQuery;
import dev.leonida.test.TestFilter;
import dev.leonida.vehicle.VehicleApi;
import dev.leonida.vehicle.VehicleEntity;
import dev.leonida.vehicle.VehicleSpec;
import dev.leonida.vehicle.VehicleTypes;
import dev.leonida.vehicle.ai.LaneDriver;
import dev.leonida.vehicle.ai.TrafficManager;
import dev.leonida.road.api.RoadLanes;
import dev.leonida.road.net.RoadNetwork;
import dev.leonida.world.WorldModule;
import net.minecraft.util.RandomSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Client game tests for the vehicle module. Owned by the vehicle agent. Run: tools/pod-test.sh vehicle */
public class VehicleClientTest implements FabricClientGameTest {
	private static final int Y = 150;          // top surface of the test pad
	private static final int WATER_X = 70, WATER_Z = 15;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("vehicle")) return;
		MapQuery.install(new TestMap());
		TrafficManager.enabled = false;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			context.runOnClient(mc -> mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN));
			cmd(sp, "forceload add -48 -128 100 168");
			context.waitTicks(80);
			buildPad(sp);
			cmd(sp, "gamemode spectator @a");
			cmd(sp, "tp @a 0 160 0");
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender();
			// MVP2 bug fixes: one focused pass, no render loops. The full visual suite is opt-in.
			if (!Boolean.getBoolean("leonida.vehicle.full")) {
				bugfixes(context, sp);
				return;
			}

			// 1. every vehicle on its own, three-quarter front view
			for (VehicleSpec spec : VehicleTypes.SPECS.values()) {
				boolean boat = spec.isBoat();
				double x = boat ? WATER_X : 0, y = boat ? Y - 0.6 : Y, z = boat ? WATER_Z : 10;
				int id = spawn(sp, spec.id(), x, y, z, 0, false);
				if (!spec.police()) sp.getServer().runOnServer(s -> vehicle(s, id).setColor(spec.paints()[0]));
				double d = spec.length() * 0.48 + 1.4;
				double cx = x - d * 0.75, cz = z + d * 0.85, cy = Y + 0.9 + spec.height() * 0.5;
				cmd(sp, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", cx, cy, cz, x, Y + spec.height() * 0.4, z));
				context.waitTicks(12);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("vehicle-" + spec.id());
				// rear three-quarter as well
				cmd(sp, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", x + d * 0.75, cy, z - d * 0.85, x, Y + spec.height() * 0.4, z));
				context.waitTicks(6);
				context.takeScreenshot("vehicle-" + spec.id() + "-rear");
				discard(sp, id);
			}

			// 2. line-up per class, one per reference sheet, then the same line-up in other paints
			String[][] classes = {
				{"dominator", "dominator", "gauntlet_hellfire", "tulip", "ganado", "buffalo", "voodoo"},
				{"coquette_d10", "coquette_d10", "cheetah", "tempesta", "infernus_classic", "adder", "sentinel_classic_cabrio", "elegy"},
				{"utility", "stanier", "caracara", "bison", "granger", "dubsta", "speedo", "stockade", "granger_taxi"},
				{"police", "police_cruiser", "police_suv", "buffalo_police", "bison_police", "police_riot"},
				{"bikes", "sanchez", "enduro", "zombie", "alvino_v1", "lido"},
				{"boats", "airboat_09", "seashark", "clarion", "squalo", "lure_predator", "kayak"}};
			// flatten a strip across road and kerbs for the line-ups
			cmd(sp, "fill -9 150 -38 9 150 -22 minecraft:air");
			for (String[] cls : classes) {
				boolean boats = cls[0].equals("boats");
				for (int pass = 0; pass < 2; pass++) {
					List<Integer> ids = new ArrayList<>();
					double total = 0;
					for (int k = 1; k < cls.length; k++) total += gap(VehicleTypes.SPECS.get(cls[k]));
					double x = (boats ? WATER_X : 0) - total / 2, z = boats ? WATER_Z : -30;
					for (int k = 1; k < cls.length; k++) {
						VehicleSpec spec = VehicleTypes.SPECS.get(cls[k]);
						x += gap(spec) / 2;
						int id = spawn(sp, spec.id(), x, boats ? Y - 0.6 : Y, z, 30, false);
						ids.add(id);
						final int pi = pass, kk = k;
						if (!spec.police()) sp.getServer().runOnServer(s -> {
							int[] pal = spec.paints();
							vehicle(s, id).setColor(pi == 0 || pal.length == 1 ? pal[0] : pal[1 + (kk % (pal.length - 1))]);
						});
						x += gap(spec) / 2;
					}
					double cx = boats ? WATER_X : 0;
					double d = Math.max(7, total * 0.46 + 2.5);
					cmd(sp, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", cx, Y + 1.8 + d * 0.1, z + d, cx, Y + 0.6, z));
					context.waitTicks(12);
					sp.getConnection().waitForChunksRender();
					context.takeScreenshot("lineup-" + cls[0] + (pass == 0 ? "" : "-paint"));
					if (pass == 0) {
						cmd(sp, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", cx, Y + 1.8 + d * 0.1, z - d, cx, Y + 0.6, z));
						context.waitTicks(8);
						context.takeScreenshot("lineup-" + cls[0] + "-rear");
					}
					for (int id : ids) discard(sp, id);
				}
			}

			// 3. police at night in both liveries: siren, light bars and headlights
			cmd(sp, "time set midnight");
			cmd(sp, "effect give @a minecraft:night_vision 60 0 true");
			List<Integer> cops = new ArrayList<>();
			String[] pol = {"police_cruiser", "police_suv", "buffalo_police", "bison_police", "police_riot"};
			for (int row = 0; row < 2; row++) {
				for (int k = 0; k < pol.length; k++) {
					int id = spawn(sp, pol[k], -14 + k * 4.0 + row * 2, Y, 10 - row * 9, 0, false);
					cops.add(id);
					final boolean sheriff = row == 1;
					sp.getServer().runOnServer(s -> {
						VehicleEntity v = vehicle(s, id);
						VehicleApi.setLivery(v, sheriff);
						v.setSiren(true);
						v.setHeadlights(true);
					});
				}
			}
			cmd(sp, "tp @a -11 154 22 facing -4 150.5 6");
			context.waitTicks(12);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("police-night");
			context.waitTicks(4);
			context.takeScreenshot("police-night-2");
			cmd(sp, "tp @a 4 152.4 16 facing -4 150.8 9");
			context.waitTicks(6);
			context.takeScreenshot("police-night-vcpd-close");
			cmd(sp, "tp @a 6 152.4 6 facing -2 150.8 0");
			context.waitTicks(6);
			context.takeScreenshot("police-night-sheriff-close");
			cmd(sp, "time set noon");
			cmd(sp, "effect clear @a minecraft:night_vision");
			context.waitTicks(4);
			cmd(sp, "tp @a -11 154 22 facing -4 150.5 6");
			context.waitTicks(6);
			context.takeScreenshot("police-day");
			for (int id : cops) discard(sp, id);

			// 4. theft: locked car prompt, slim jim progress, smash, carjack
			cmd(sp, "gamemode survival @a");
			cmd(sp, "effect give @a minecraft:resistance 600 10 true");
			int locked = spawn(sp, "stanier", 0, Y, 10, 0, true);
			face(sp, 2.9, Y, 10.4, 0.6, Y + 0.9, 10.2);
			context.waitTicks(15);
			context.takeScreenshot("theft-prompt-locked");
			context.getInput().pressKey(o -> o.keyUse);
			context.waitTicks(40);
			context.takeScreenshot("theft-slimjim-progress");
			context.waitTicks(60);
			log(sp, "after slim jim: riding=" + riding(sp));
			context.takeScreenshot("theft-slimjim-done");
			dismount(sp);
			discard(sp, locked);

			int smash = spawn(sp, "dominator", 0, Y, 10, 0, true);
			face(sp, 2.9, Y, 9.8, 0.6, Y + 0.9, 9.8);
			context.waitTicks(12);
			context.getInput().holdKey(o -> o.keyShift);
			context.waitTicks(3);
			context.getInput().pressKey(o -> o.keyUse);
			context.waitTicks(3);
			context.getInput().releaseKey(o -> o.keyShift);
			context.waitTicks(6);
			context.takeScreenshot("theft-smash");
			context.getInput().pressKey(o -> o.keyUse);
			context.waitTicks(8);
			log(sp, "after smash: riding=" + riding(sp));
			context.takeScreenshot("theft-smash-enter");
			dismount(sp);
			face(sp, 4.5, Y, 13.5, 0, Y + 0.8, 10);
			context.waitTicks(5);
			context.takeScreenshot("theft-smash-broken-window");
			discard(sp, smash);

			int npc = spawn(sp, "granger_taxi", 0, Y, 10, 0, false);
			sp.getServer().runOnServer(s -> vehicle(s, npc).setNpcDriver(true));
			face(sp, 3.0, Y, 10.6, 0.6, Y + 0.9, 10.4);
			context.waitTicks(12);
			context.takeScreenshot("theft-carjack-prompt");
			context.getInput().pressKey(o -> o.keyUse);
			context.waitTicks(10);
			log(sp, "after carjack: riding=" + riding(sp));
			context.takeScreenshot("theft-carjacked");
			dismount(sp);
			discard(sp, npc);

			// 4b. riders: player on a motorcycle, NPC rider on a dirt bike, player in a sedan
			cmd(sp, "gamemode creative @a");
			int moto = spawn(sp, "alvino_v1", 2, Y, 10, 0, false);
			int dirt = spawn(sp, "sanchez", -2.5, Y, 12, 0, false);
			sp.getServer().runOnServer(s -> {
				vehicle(s, dirt).setNpcDriver(true);
				s.getPlayerList().getPlayers().get(0).startRiding(vehicle(s, moto), true, true);
			});
			context.waitTicks(15);
			context.runOnClient(mc -> { mc.player.setYRot(150); mc.player.setXRot(15); });
			context.waitTicks(3);
			context.takeScreenshot("rider-alvino");
			dismount(sp);
			discard(sp, moto);
			discard(sp, dirt);
			int sed = spawn(sp, "stanier", 2, Y, 10, 0, false);
			int npcSed = spawn(sp, "granger", -3, Y, 4, 0, false);
			sp.getServer().runOnServer(s -> {
				vehicle(s, npcSed).setNpcDriver(true);
				s.getPlayerList().getPlayers().get(0).startRiding(vehicle(s, sed), true, true);
			});
			context.waitTicks(15);
			context.runOnClient(mc -> { mc.player.setYRot(160); mc.player.setXRot(12); });
			context.waitTicks(3);
			context.takeScreenshot("rider-stanier");
			context.runOnClient(mc -> { mc.player.setYRot(0); mc.player.setXRot(12); });
			context.waitTicks(3);
			context.takeScreenshot("rider-stanier-behind");
			dismount(sp);
			discard(sp, sed);
			discard(sp, npcSed);

			// 5. driving on the road, kerb, and a crash into a wall
			cmd(sp, "fill -6 150 40 6 152 41 minecraft:stone_bricks");
			int car = spawn(sp, "coquette_d10", 2, Y, -60, 0, false);
			cmd(sp, "tp @a 2 150 -58");
			context.waitTicks(5);
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = s.getPlayerList().getPlayers().get(0);
				p.startRiding(vehicle(s, car), true, true);
			});
			context.waitTicks(10);
			log(sp, "driving riding=" + riding(sp));
			context.getInput().holdKey(o -> o.keyUp);
			context.waitTicks(45);
			context.takeScreenshot("drive-road");
			log(sp, "drive speed " + info(sp, car));
			// symmetric steering check: same pulse right then left, yaw change should match in size
			float y0 = yaw(sp, car);
			context.getInput().holdKey(o -> o.keyRight);
			context.waitTicks(6);
			context.getInput().releaseKey(o -> o.keyRight);
			context.waitTicks(14);
			float y1 = yaw(sp, car);
			context.takeScreenshot("drive-steer");
			context.getInput().holdKey(o -> o.keyLeft);
			context.waitTicks(6);
			context.getInput().releaseKey(o -> o.keyLeft);
			context.waitTicks(14);
			float y2 = yaw(sp, car);
			log(sp, String.format(Locale.ROOT, "steer right pulse dyaw=%.1f left pulse dyaw=%.1f net=%.1f", Mth.wrapDegrees(y1 - y0), Mth.wrapDegrees(y2 - y1), Mth.wrapDegrees(y2 - y0)));
			log(sp, "after steer " + info(sp, car));
			// line the car back up on the lane so the crash run hits the wall (server and client)
			sp.getServer().runOnServer(s -> {
				VehicleEntity v = vehicle(s, car);
				v.setYRot(0);
				v.setPos(2, v.getY(), Math.min(v.getZ(), 10));
			});
			context.runOnClient(mc -> {
				if (mc.player.getVehicle() instanceof VehicleEntity v) {
					v.setYRot(0);
					v.setPos(2, v.getY(), Math.min(v.getZ(), 10));
				}
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
			});
			// a row of villagers across the whole carriageway and glass panes in front of the wall
			for (int vx = -4; vx <= 4; vx++) cmd(sp, "summon minecraft:villager " + vx + " 150 28 {NoAI:1b,Health:2f}");
			cmd(sp, "fill -6 150 38 6 152 38 minecraft:glass_pane");
			int waited = 0;
			boolean hitShot = false;
			while (waited < 200 && !(crashed(sp, car) && carZ(sp, car) > 33)) {
				context.waitTicks(1);
				waited++;
				if (!hitShot && villagers(sp) < 9) {
					hitShot = true;
					context.waitTicks(2);
					context.takeScreenshot("crash-ragdoll");
					log(sp, "villager hit at " + info(sp, car) + " alive=" + villagers(sp) + " nearby " + entitiesNear(sp, 2, 150, 30, 16));
				}
			}
			context.waitTicks(1);
			context.takeScreenshot("crash-debris");
			log(sp, "at crash nearby " + entitiesNear(sp, 2, 150, 38, 10));
			context.waitTicks(3);
			context.takeScreenshot("crash");
			log(sp, "villagers alive after run " + villagers(sp));
			log(sp, "after crash " + info(sp, car));
			context.getInput().releaseKey(o -> o.keyUp);
			context.waitTicks(30);
			context.takeScreenshot("crash-after");
			context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			dismount(sp);
			// look at the wreck from outside, heavily damaged so it smokes
			sp.getServer().runOnServer(s -> vehicle(s, car).setHealth(vehicle(s, car).spec.maxHealth() * 0.2f));
			context.waitTicks(20);
			log(sp, "wreck " + info(sp, car));
			cmd(sp, "gamemode spectator @a");
			cmd(sp, "tp @a -4 152.5 30 facing 2 150.8 37");
			context.waitTicks(30);
			context.takeScreenshot("crash-damaged");
			discard(sp, car);
			cmd(sp, "kill @e[type=minecraft:villager]");

			// 5b. the same crash seen from the kerb: an NPC car at full throttle through villagers into glass and a wall
			cmd(sp, "fill -6 150 38 6 152 41 minecraft:air");
			cmd(sp, "fill -6 150 40 6 152 41 minecraft:stone_bricks");
			cmd(sp, "fill -6 150 39 6 152 39 minecraft:glass");
			for (int vx = -1; vx <= 1; vx++) cmd(sp, "summon minecraft:villager " + vx + " 150 26 {NoAI:1b,Health:2f,Rotation:[90f,0f]}");
			cmd(sp, "tp @a 11 152.2 27 facing 1 150.8 33");
			int sideCar = spawn(sp, "buffalo", 0, Y, -50, 0, false);
			sp.getServer().runOnServer(s -> {
				VehicleEntity v = vehicle(s, sideCar);
				v.setNpcDriver(true);
				v.setDriver(veh -> new dev.leonida.vehicle.VehicleControls(1, 0, false));
			});
			context.waitTicks(2);
			sp.getConnection().waitForChunksRender();
			boolean ragShot = false;
			int hitTick = -1;
			for (int t = 0; t < 160; t++) {
				context.waitTicks(1);
				if (!ragShot && villagers(sp) < 3) {
					ragShot = true;
					context.waitTicks(2);
					context.takeScreenshot("crash-side-ragdoll");
					log(sp, "side run villager hit " + info(sp, sideCar) + " alive=" + villagers(sp));
					context.waitTicks(5);
					context.takeScreenshot("crash-side-ragdoll-2");
				}
				if (crashed(sp, sideCar) && carZ(sp, sideCar) > 33) { hitTick = t; break; }
			}
			context.takeScreenshot("crash-side-debris");
			context.waitTicks(3);
			context.takeScreenshot("crash-side-debris-2");
			log(sp, "side crash at tick " + hitTick + " impact=" + VehicleEntity.lastCrashImpact + " " + info(sp, sideCar) + " glass left=" + sp.getServer().computeOnServer(s -> {
				int n = 0;
				for (int x = -6; x <= 6; x++) for (int y = 150; y <= 152; y++) if (s.overworld().getBlockState(new BlockPos(x, y, 39)).is(net.minecraft.world.level.block.Blocks.GLASS)) n++;
				return n;
			}) + "/39 villagers alive=" + villagers(sp));
			context.waitTicks(20);
			context.takeScreenshot("crash-side-after");
			discard(sp, sideCar);
			cmd(sp, "kill @e[type=minecraft:villager]");
			cmd(sp, "gamemode creative @a");

			// 6. a boat on the water with the player driving
			int boat = spawn(sp, "clarion", WATER_X, Y - 0.6, WATER_Z - 10, 0, false);
			sp.getServer().runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(vehicle(s, boat), true, true));
			context.waitTicks(10);
			context.getInput().holdKey(o -> o.keyUp);
			context.waitTicks(40);
			context.takeScreenshot("boat-driving");
			context.getInput().releaseKey(o -> o.keyUp);
			log(sp, "boat " + info(sp, boat));
			dismount(sp);
			discard(sp, boat);

			// 7. ambient traffic on the test road: a creative, flying player (traffic ignores spectators)
			TrafficManager.enabled = true;
			cmd(sp, "gamemode creative @a");
			cmd(sp, "fill -6 150 38 6 152 41 minecraft:air");
			cmd(sp, "tp @a 12 158 20 facing 0 150 45");
			context.runOnClient(mc -> { mc.player.getAbilities().flying = true; mc.player.onUpdateAbilities(); });
			context.waitTicks(5);
			for (int k = 0; k < 8; k++) {
				cmd(sp, "execute as @a at @s run vehicle traffic spawn");
				context.waitTicks(5);
			}
			context.waitTicks(80);
			sp.getConnection().waitForChunksRender();
			log(sp, "traffic count " + trafficSummary(sp));
			cmd(sp, "tp @a 12 158 20 facing 0 150 45");
			context.waitTicks(3);
			context.takeScreenshot("traffic");
			cmd(sp, "tp @a 12 158 20 facing 0 150 -5");
			context.waitTicks(3);
			context.takeScreenshot("traffic-back");
			context.waitTicks(60);
			log(sp, "traffic count later " + trafficSummary(sp));
			for (int shot = 0; shot < 3; shot++) {
				final int n = shot;
				double[] at = sp.getServer().computeOnServer(s -> {
					List<VehicleEntity> vs = new ArrayList<>();
					for (Entity e : s.overworld().getAllEntities()) if (e instanceof VehicleEntity v) vs.add(v);
					if (vs.isEmpty()) return null;
					VehicleEntity v = vs.get(n % vs.size());
					Vec3 f = Vec3.directionFromRotation(0, v.getYRot());
					return new double[] {v.getX(), v.getY(), v.getZ(), f.x, f.z};
				});
				if (at == null) break;
				double tx = at[0] + at[3] * 9, tz = at[2] + at[4] * 9;
				cmd(sp, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", tx + 4, at[1] + 2.5, tz, at[0], at[1] + 0.8, at[2]));
				context.waitTicks(4);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("traffic-car-" + shot);
			}
			TrafficManager.enabled = false;
			for (Entity e : sp.getServer().computeOnServer(s -> { List<Entity> l = new ArrayList<>(); for (Entity e2 : s.overworld().getAllEntities()) if (e2 instanceof VehicleEntity) l.add(e2); return l; }))
				sp.getServer().runOnServer(s -> e.discard());

			// 8. lane traffic on the real Leonida map at a signalled downtown junction
			realMapTraffic(context, sp);
			// 8b. highway traffic at speed on an elevated stretch
			highwayTraffic(context, sp);
			// 9. story vehicles parked at Jason's house (landmark spots)
			jasonHouse(context, sp);
		}
	}

	private static void realMapTraffic(ClientGameTestContext context, TestSingleplayerContext sp) {
		RoadNetwork net = RoadNetwork.get();
		dev.leonida.road.net.Segment boulevard = net.segments.get(1677);
		double[] bp = boulevard.at(boulevard.length / 2);
		RoadNetwork.Node node = null;
		for (RoadNetwork.Node n : net.nodesNear(bp[0], bp[1], 600))
			if (n.signalled && n.arms.size() == 4 && (node == null || Math.hypot(n.x - bp[0], n.z - bp[1]) < Math.hypot(node.x - bp[0], node.z - bp[1]))) node = n;
		if (node == null) { log(sp, "lane traffic: no signalled junction found"); return; }
		final RoadNetwork.Node jn = node;
		RoadLanes.LanePos c0 = RoadLanes.nearest(jn.x, jn.h + 63, jn.z, 30);
		double roadY = c0 == null ? 64 : c0.y();
		log(sp, String.format(Locale.ROOT, "lane traffic junction node=%d at %.0f,%.0f roadY=%.1f arms=%d", jn.id, jn.x, jn.z, roadY, jn.arms.size()));
		cmd(sp, "gamemode creative @a");
		cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.1f %.1f %.1f 0 90", jn.x, roadY + 40, jn.z));
		context.runOnClient(mc -> { mc.player.getAbilities().flying = true; mc.player.onUpdateAbilities(); });
		context.waitTicks(40);
		sp.getConnection().waitForChunksRender();
		context.waitTicks(40);
		cmd(sp, "time set noon");
		List<Integer> ids = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.getLevel(WorldModule.LEONIDA);
			List<Integer> out = new ArrayList<>();
			RandomSource rnd = RandomSource.create(7);
			String[] types = {"stanier", "granger", "buffalo", "bison", "granger_taxi", "dominator", "tulip", "speedo"};
			int k = 0;
			for (RoadNetwork.Arm a : jn.arms) {
				for (double d : new double[] {30, 48, 75, 100}) {
					double px = jn.x + Math.cos(a.angle) * d, pz = jn.z + Math.sin(a.angle) * d;
					for (RoadLanes.LanePos lp : RoadLanes.lanesNear(px, pz, 6)) {
						double dot = Math.cos(lp.heading()) * -Math.cos(a.angle) + Math.sin(lp.heading()) * -Math.sin(a.angle);
						if (dot < 0.8 || lp.lane() != 0) continue;
						VehicleEntity v = TrafficManager.spawnOnLane(level, lp, VehicleTypes.get(types[k++ % types.length]), rnd);
						if (v != null) out.add(v.getId());
						break;
					}
				}
			}
			return out;
		});
		log(sp, "lane traffic spawned " + ids.size());
		java.util.Map<Integer, Double> lastYaw = new java.util.HashMap<>(), maxRate = new java.util.HashMap<>();
		for (int t = 0; t <= 760; t += 20) {
			log(sp, "lane t=" + t + laneSummary(sp, ids));
			if (t == 140) {
				cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.1f %.1f %.1f 0 90", jn.x, roadY + 45, jn.z));
				context.waitTicks(2);
				context.takeScreenshot("lane-junction-aerial");
				shootStopped(context, sp, ids, "lane-red-stop");
			}
			if (t == 700) shootStopped(context, sp, ids, "lane-red-stop-2");
			if (t == 380 || t == 640) {
				cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.1f %.1f %.1f 0 90", jn.x, roadY + 45, jn.z));
				context.waitTicks(2);
				context.takeScreenshot("lane-junction-aerial-" + t);
			}
			for (int q = 0; q < 4; q++) {
				context.waitTicks(5);
				sampleYaw(sp, ids, lastYaw, maxRate);
			}
		}
		log(sp, "lane yaw rate max deg/5ticks" + sp.getServer().computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			for (int id : ids) {
				Entity e = s.getLevel(WorldModule.LEONIDA).getEntity(id);
				int lc = e instanceof VehicleEntity v && v.getDriver() instanceof LaneDriver d ? d.laneChanges : -1;
				b.append(String.format(Locale.ROOT, " #%d:%.1f lc=%d", id, maxRate.getOrDefault(id, 0.0), lc));
			}
			return b.toString();
		}));
		// a moving car seen from behind, in its lane
		Integer mover = sp.getServer().computeOnServer(s -> {
			for (int id : ids) {
				Entity e = s.getLevel(WorldModule.LEONIDA).getEntity(id);
				if (e instanceof VehicleEntity v && v.serverSpeed() > 4 && Math.hypot(v.getX() - jn.x, v.getZ() - jn.z) < 70) return id;
			}
			return null;
		});
		if (mover != null) {
			double[] at = laneCar(sp, mover);
			cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f",
					at[0] - at[3] * 9, at[1] + 3.5, at[2] - at[4] * 9, at[0] + at[3] * 6, at[1] + 0.5, at[2] + at[4] * 6));
			context.waitTicks(3);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("lane-follow");
		}
	}

	private static void highwayTraffic(ClientGameTestContext context, TestSingleplayerContext sp) {
		RoadNetwork net = RoadNetwork.get();
		dev.leonida.road.net.Segment best = null;
		double bestScore = Double.MAX_VALUE;
		for (dev.leonida.road.net.Segment sg : net.segments) {
			if (!sg.highway || sg.length < 180) continue;
			double[] m = sg.at(sg.length / 2);
			double elev = m[2];
			double score = Math.hypot(m[0] + 712, m[1] - 491) - elev * 40; // prefer raised decks near downtown
			if (score < bestScore) { bestScore = score; best = sg; }
		}
		if (best == null) { log(sp, "highway: no highway segment"); return; }
		final dev.leonida.road.net.Segment hw = best;
		double[] mid = hw.at(hw.length / 2);
		log(sp, String.format(Locale.ROOT, "highway seg=%d len=%.0f lanes=%d mid=%.0f,%.0f elev=%.1f", hw.id, hw.length, hw.lanesPerSide, mid[0], mid[1], mid[2]));
		double[] st0 = hw.at(40);
		cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.1f %.1f %.1f 0 90", st0[0], 63 + mid[2] + 30, st0[1]));
		context.waitTicks(60);
		sp.getConnection().waitForChunksRender();
		List<Integer> ids = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.getLevel(WorldModule.LEONIDA);
			List<Integer> out = new ArrayList<>();
			RandomSource rnd = RandomSource.create(11);
			String[] types = {"buffalo", "dominator", "granger", "stanier", "tempesta", "bison"};
			int k = 0;
			for (double st : new double[] {20, 45, 70}) {
				double[] c = hw.at(st);
				for (RoadLanes.LanePos lp : RoadLanes.lanesNear(c[0], c[1], 14)) {
					if (lp.segment() != hw.id || !lp.forward()) continue;
					if (lp.lane() != (k % hw.lanesPerSide)) continue;
					VehicleEntity v = TrafficManager.spawnOnLane(level, lp, VehicleTypes.get(types[k % types.length]), rnd);
					k++;
					if (v != null) out.add(v.getId());
					break;
				}
				for (RoadLanes.LanePos lp : RoadLanes.lanesNear(c[0], c[1], 14)) {
					if (lp.segment() != hw.id || lp.forward() || lp.lane() != (k % hw.lanesPerSide)) continue;
					VehicleEntity v = TrafficManager.spawnOnLane(level, lp, VehicleTypes.get(types[k % types.length]), rnd);
					k++;
					if (v != null) out.add(v.getId());
					break;
				}
			}
			return out;
		});
		log(sp, "highway spawned " + ids.size());
		if (ids.isEmpty()) return;
		int lead = ids.get(0);
		for (int t = 0; t <= 400; t += 20) {
			log(sp, "highway t=" + t + laneSummary(sp, ids) + highwayY(sp, ids));
			double[] at = laneCar(sp, lead);
			// chase camera behind the lead car
			cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f",
					at[0] - at[3] * 11, at[1] + 4, at[2] - at[4] * 11, at[0] + at[3] * 10, at[1] + 0.5, at[2] + at[4] * 10));
			if (t == 100 || t == 240 || t == 400) {
				context.waitTicks(3);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("highway-chase-" + t);
				cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f",
						at[0] + at[4] * 14, at[1] + 2, at[2] - at[3] * 14, at[0], at[1] + 0.5, at[2]));
				context.waitTicks(2);
				context.takeScreenshot("highway-side-" + t);
				context.waitTicks(15);
			} else context.waitTicks(20);
		}
	}

	private static String highwayY(TestSingleplayerContext sp, List<Integer> ids) {
		return sp.getServer().computeOnServer(s -> {
			StringBuilder b = new StringBuilder(" deckDy:");
			for (int id : ids) {
				Entity e = s.getLevel(WorldModule.LEONIDA).getEntity(id);
				if (!(e instanceof VehicleEntity v)) continue;
				RoadLanes.LanePos lp = RoadLanes.nearest(v.getX(), v.getY(), v.getZ(), 8);
				b.append(String.format(Locale.ROOT, " %.1f%s", lp == null ? 99 : v.getY() - lp.y(), lp != null && lp.highway() ? "h" : ""));
			}
			return b.toString();
		});
	}

	private static void jasonHouse(ClientGameTestContext context, TestSingleplayerContext sp) {
		dev.leonida.landmark.LandmarkSpots.Spot car = null, bike = null;
		for (dev.leonida.landmark.LandmarkSpots.Spot s : dev.leonida.landmark.LandmarkSpots.all()) {
			if (s.id().equals("jason_house/car")) car = s;
			if (s.id().equals("jason_house/bike")) bike = s;
		}
		if (car == null) { log(sp, "jason house: no spot"); return; }
		final double cx = car.x(), cz = car.z();
		cmd(sp, "time set 1000");
		cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.1f 120 %.1f", cx - 9, cz + 8));
		context.waitTicks(60);
		sp.getConnection().waitForChunksRender();
		context.waitTicks(60);
		String found = sp.getServer().computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			ServerLevel level = s.getLevel(WorldModule.LEONIDA);
			for (Entity e : level.getAllEntities())
				if (e instanceof VehicleEntity v) for (String t : v.entityTags()) if (t.startsWith(dev.leonida.vehicle.ai.SpotVehicles.TAG))
					b.append(String.format(Locale.ROOT, " [%s %s at %.1f,%.1f,%.1f yaw=%.0f color=%06x locked=%s owner=%s]", t, v.spec.id(), v.getX(), v.getY(), v.getZ(),
							v.getYRot(), v.getColor(), v.isLocked(), v.getOwner()));
			return b.toString();
		});
		log(sp, "jason house spot vehicles:" + found);
		double gy = sp.getServer().computeOnServer(s -> (double) s.getLevel(WorldModule.LEONIDA).getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(cx), (int) Math.floor(cz)));
		// like the reference photo: rear three-quarter from the south-west, nose to the right
		cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", cx - 7, gy + 1.6, cz + 5.5, cx + 0.5, gy + 0.8, cz));
		context.waitTicks(10);
		sp.getConnection().waitForChunksRender();
		context.takeScreenshot("jason-house-ganado");
		cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", cx + 6, gy + 1.8, cz + 5, cx, gy + 0.8, cz));
		context.waitTicks(6);
		context.takeScreenshot("jason-house-ganado-front");
		cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", cx - 16, gy + 4, cz + 14, cx - 6, gy + 4, cz - 4));
		context.waitTicks(6);
		context.takeScreenshot("jason-house-wide");
		if (bike != null) {
			cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", bike.x() + 3.5, gy + 1.4, bike.z() + 3.5, bike.x(), gy + 0.6, bike.z()));
			context.waitTicks(6);
			context.takeScreenshot("jason-house-enduro");
		}
	}

	private static void shootStopped(ClientGameTestContext context, TestSingleplayerContext sp, List<Integer> ids, String name) {
		Integer stopped = sp.getServer().computeOnServer(s -> {
			for (int id : ids) {
				Entity e = s.getLevel(WorldModule.LEONIDA).getEntity(id);
				if (e instanceof VehicleEntity v && v.getDriver() instanceof LaneDriver d && d.lastLight == RoadLanes.Light.RED && v.serverSpeed() < 0.5) return id;
			}
			return null;
		});
		if (stopped == null) { log(sp, "no car waiting at red for " + name); return; }
		double[] at = laneCar(sp, stopped);
		// behind and to the right of the waiting car, looking past it at the junction
		cmd(sp, String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f",
				at[0] - at[3] * 10 - at[4] * 4, at[1] + 3, at[2] - at[4] * 10 + at[3] * 4, at[0] + at[3] * 8, at[1] + 1, at[2] + at[4] * 8));
		context.waitTicks(3);
		context.takeScreenshot(name);
	}

	private static void sampleYaw(TestSingleplayerContext sp, List<Integer> ids, java.util.Map<Integer, Double> lastYaw, java.util.Map<Integer, Double> maxRate) {
		sp.getServer().runOnServer(s -> {
			for (int id : ids) {
				Entity e = s.getLevel(WorldModule.LEONIDA).getEntity(id);
				if (!(e instanceof VehicleEntity v) || v.serverSpeed() < 1) { lastYaw.remove(id); continue; }
				Double prev = lastYaw.put(id, (double) v.getYRot());
				if (prev != null) maxRate.merge(id, Math.abs(Mth.wrapDegrees(v.getYRot() - prev)), Math::max);
			}
		});
	}

	private static double[] laneCar(TestSingleplayerContext sp, int id) {
		return sp.getServer().computeOnServer(s -> {
			VehicleEntity v = (VehicleEntity) s.getLevel(WorldModule.LEONIDA).getEntity(id);
			Vec3 f = Vec3.directionFromRotation(0, v.getYRot());
			return new double[] {v.getX(), v.getY(), v.getZ(), f.x, f.z};
		});
	}

	private static String laneSummary(TestSingleplayerContext sp, List<Integer> ids) {
		return sp.getServer().computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			for (int id : ids) {
				Entity e = s.getLevel(WorldModule.LEONIDA).getEntity(id);
				if (!(e instanceof VehicleEntity v)) { b.append(" [gone]"); continue; }
				RoadLanes.LanePos lp = RoadLanes.nearest(v.getX(), v.getY(), v.getZ(), 8);
				double off = lp == null ? -1 : Math.hypot(lp.x() - v.getX(), lp.z() - v.getZ());
				String light = "-", stop = "";
				if (v.getDriver() instanceof LaneDriver d) {
					light = d.lastLight == null ? "-" : d.lastLight.name().substring(0, 1);
					if (!Double.isNaN(d.lastStopDistance)) stop = String.format(Locale.ROOT, "@%.1f", d.lastStopDistance);
					if (!d.holdReason.isEmpty()) stop += " " + d.holdReason;
				}
				String lane = v.getDriver() instanceof LaneDriver d && d.track() != null
						? String.format(Locale.ROOT, " L%d%s%s", d.track().lane(), d.nextTurn > 0 ? "R" : d.nextTurn < 0 ? "Lt" : "S", d.laneChanges > 0 ? " lc" + d.laneChanges : "") : "";
				b.append(String.format(Locale.ROOT, " [%s#%d v=%.1f off=%.2f%s %s%s]", v.spec.id(), v.getId(), v.serverSpeed(), off, lane, light, stop));
			}
			return b.toString();
		});
	}

	// ------------------------------------------------------------------ MVP2 bug fixes

	private static void bugfixes(ClientGameTestContext context, TestSingleplayerContext sp) {
		boolean ok1 = groundCourse(context, sp);
		boolean ok2 = dismountKeys(context, sp);
		boolean ok3 = npcDriver(context, sp);
		log(sp, "BUGFIX RESULT ground=" + (ok1 ? "PASS" : "FAIL") + " dismount=" + (ok2 ? "PASS" : "FAIL") + " npcDriver=" + (ok3 ? "PASS" : "FAIL"));
	}

	private static void slab(TestSingleplayerContext sp, int x0, int y, int z0, int x1, int z1) {
		cmd(sp, "fill " + x0 + " " + y + " " + z0 + " " + x1 + " " + y + " " + z1 + " leonida:asphalt_slab[type=bottom]");
	}

	/** (a) 200+ blocks over slab steps, a half-block junction pad and an elevated deck on slab ramps. */
	private static boolean groundCourse(ClientGameTestContext context, TestSingleplayerContext sp) {
		int y = Y; // first block layer above the road top
		// slab bumps: alternating 4 blocks slab / 4 blocks road
		for (int z = -100; z < -60; z += 8) slab(sp, -5, y, z, 5, z + 3);
		// half-block junction pad spanning the whole road
		slab(sp, -5, y, -40, 5, -20);
		// elevated deck: slab ramp up 5 blocks, a one-block-thick deck with air below, ramp down
		for (int k = 0; k < 10; k++) {
			int z0 = -10 + 3 * k, z1 = z0 + 2, top2 = k + 1; // height in half blocks
			if (top2 / 2 > 0) fill(sp, -5, y, z0, 5, y + top2 / 2 - 1, z1, "gray_concrete");
			if (top2 % 2 == 1) slab(sp, -5, y + top2 / 2, z0, 5, z1);
		}
		fill(sp, -5, y + 4, 20, 5, y + 4, 50, "gray_concrete");
		for (int k = 0; k < 10; k++) {
			int z0 = 51 + 3 * k, z1 = z0 + 2, top2 = 9 - k;
			if (top2 / 2 > 0) fill(sp, -5, y, z0, 5, y + top2 / 2 - 1, z1, "gray_concrete");
			if (top2 % 2 == 1) slab(sp, -5, y + top2 / 2, z0, 5, z1);
		}
		cmd(sp, "gamemode creative @a");
		int car = spawn(sp, "stanier", -2.5, Y, -110, 0, false);
		context.waitTicks(10);
		sp.getServer().runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(vehicle(s, car), true, true));
		context.waitTicks(10);
		double minGap = 9, maxGap = -9, worstSink = 0;
		int samples = 0, embedded = 0, ticks = 0;
		boolean pressing = false;
		double startZ = carZ(sp, car), z = startZ;
		String worstAt = "";
		while (z < 105 && ticks < 1400) {
			double speed = sp.getServer().computeOnServer(s -> vehicle(s, car).serverSpeed());
			if (!pressing && speed < 9) { context.getInput().holdKey(o -> o.keyUp); pressing = true; }
			else if (pressing && speed > 12) { context.getInput().releaseKey(o -> o.keyUp); pressing = false; }
			context.waitTick();
			ticks++;
			double[] r = context.computeOnClient(mc -> {
				VehicleEntity v = (VehicleEntity) mc.level.getEntity(car);
				if (v == null) return new double[] {Double.NaN, Double.NaN, 0, 0, 0};
				double top = VehicleEntity.surfaceTop(mc.level, v.getX(), v.getZ(), (int) Math.floor(v.getY() + 0.5), v.spec.width());
				boolean emb = !mc.level.noCollision(v, v.getBoundingBox().deflate(0.12, 0.0, 0.12).move(0, 0.03, 0).setMaxY(v.getY() + 0.6));
				return new double[] {v.wheelGapMin, v.wheelGapMax, v.getY() - top, emb ? 1 : 0, v.getZ()};
			});
			z = r[4];
			if (!Double.isNaN(r[0])) {
				samples++;
				if (r[0] < minGap) { minGap = r[0]; worstAt = String.format(Locale.ROOT, "min at z=%.1f", z); }
				maxGap = Math.max(maxGap, r[1]);
			}
			if (r[2] < worstSink && r[2] > -3) worstSink = r[2];
			if (r[3] > 0) embedded++;
		}
		if (pressing) context.getInput().releaseKey(o -> o.keyUp);
		double dist = z - startZ;
		boolean ok = dist >= 200 && samples > 100 && minGap >= -0.01 && maxGap <= 0.15 && worstSink >= -0.02 && embedded == 0;
		log(sp, String.format(Locale.ROOT, "ground course: dist=%.1f ticks=%d samples=%d wheelGap[min=%.3f max=%.3f] (%s) bodyBelowTop=%.3f embeddedTicks=%d end=%s -> %s",
				dist, ticks, samples, minGap, maxGap, worstAt, worstSink, embedded, info(sp, car), ok ? "PASS" : "FAIL"));
		dismount(sp);
		discard(sp, car);
		cmd(sp, "fill -5 150 -100 5 160 90 minecraft:air");
		context.waitTicks(5);
		return ok;
	}

	/** (b) Shift and F each get the player out of a car, a bike and a boat; F on foot gets back in. */
	private static boolean dismountKeys(ClientGameTestContext context, TestSingleplayerContext sp) {
		cmd(sp, "gamemode creative @a");
		boolean all = true;
		String[][] cases = {{"stanier", "0", "20"}, {"sanchez", "-3", "30"}, {"police_cruiser", "3", "40"}, {"seashark", "W", "0"}};
		for (String[] c : cases) {
			boolean boat = c[1].equals("W");
			double x = boat ? WATER_X : Double.parseDouble(c[1]), z = boat ? WATER_Z : Double.parseDouble(c[2]);
			int id = spawn(sp, c[0], x, boat ? Y - 0.6 : Y, z, 0, false);
			context.waitTicks(10);
			for (String key : new String[] {"shift", "F"}) {
				sp.getServer().runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(vehicle(s, id), true, true));
				context.waitTicks(10);
				String before = riding(sp);
				if (key.equals("shift")) {
					context.getInput().holdKey(o -> o.keyShift);
					context.waitTicks(8);
					context.getInput().releaseKey(o -> o.keyShift);
				} else {
					context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_F));
				}
				context.waitTicks(6);
				String after = riding(sp);
				boolean ok = !before.equals("none") && after.equals("none");
				all &= ok;
				log(sp, "dismount " + c[0] + " by " + key + ": before=" + before + " after=" + after + " -> " + (ok ? "PASS" : "FAIL"));
			}
			// F on foot, standing next to it, gets back in
			context.waitTicks(4);
			context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(com.mojang.blaze3d.platform.InputConstants.KEY_F));
			context.waitTicks(6);
			log(sp, "enter " + c[0] + " by F: riding=" + riding(sp));
			dismount(sp);
			context.waitTicks(2);
			discard(sp, id);
		}
		log(sp, "dismount keys -> " + (all ? "PASS" : "FAIL"));
		return all;
	}

	/** (c) side view of an NPC-driven car: a seated pedestrian driver and a lane-traffic car. */
	private static boolean npcDriver(ClientGameTestContext context, TestSingleplayerContext sp) {
		cmd(sp, "gamemode spectator @a");
		int car = spawn(sp, "stanier", -3, Y, 0, 90, false);
		int sedan = spawn(sp, "ganado", 4, Y, 0, 90, false);
		String res = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			VehicleEntity v = vehicle(s, car), g = vehicle(s, sedan);
			StringBuilder b = new StringBuilder();
			boolean ok = true;
			for (VehicleEntity e : new VehicleEntity[] {v, g}) {
				var ped = dev.leonida.gameplay.npc.PedestrianSpawner.spawn(level, e.position());
				if (ped == null || !ped.startRiding(e, true, false)) return "FAIL could not seat";
				ped.setNoAi(true);
				e.positionRider(ped);
				double roof = e.getY() + e.spec.height();
				double sc = ped.getScale();
				double head = ped.getY() + 2.0 * sc + 0.03 * sc; // humanoid model top incl. hat layer (model is 32 px at scale 1)
				double legs = ped.getY() + 0.75 * sc - 0.14 * sc; // seated: legs fold forward at hip height
				ok &= head <= roof - 0.12 && legs >= e.getY() + 0.28 && sc > 0.6;
				b.append(String.format(Locale.ROOT, "%s scale=%.2f feet=%.2f legsBottom=%.2f headTop=%.2f roof=%.2f; ", e.spec.id(), sc, ped.getY() - e.getY(), legs - e.getY(), head - e.getY(), roof - e.getY()));
			}
			return (ok ? "PASS " : "FAIL ") + b;
		});
		context.waitTicks(10);
		// side view from the +z side, camera at window height
		cmd(sp, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", 0.5, Y + 0.5, 9.5, 0.5, Y + 0.9, 0.0));
		context.waitTicks(15);
		sp.getConnection().waitForChunksRender();
		context.takeScreenshot("npc-driver-side");
		log(sp, "npc driver: " + res);
		discard(sp, car);
		discard(sp, sedan);
		return res.startsWith("PASS");
	}

	// ------------------------------------------------------------------ helpers

	private static void buildPad(TestSingleplayerContext sp) {
		int y = Y - 1;
		cmd(sp, "time set noon");
		cmd(sp, "weather clear");
		// a wide apron along the whole road so traffic U-turns never drop off a floating strip
		fill(sp, -16, y + 1, -120, 16, y + 8, 160, "air");
		fill(sp, -16, y, -120, 16, y, 160, "smooth_stone");
		fill(sp, -40, y + 1, -40, 40, y + 12, 60, "air");
		fill(sp, -40, y, -40, 40, y, 60, "grass_block");
		fill(sp, -5, y, -120, 5, y, 160, "gray_concrete");
		fill(sp, -5, y + 1, -120, 5, y + 8, 160, "air");
		fill(sp, 0, y, -120, 0, y, 160, "yellow_concrete");
		fill(sp, -9, y, -40, -6, y, 60, "smooth_stone");
		fill(sp, 6, y, -40, 9, y, 60, "smooth_stone");
		fill(sp, -9, y + 1, -40, -6, y + 1, 60, "smooth_stone_slab");
		fill(sp, 6, y + 1, -40, 9, y + 1, 60, "smooth_stone_slab");
		for (int z = -120; z <= 160; z += 6) fill(sp, 0, y, z, 0, y, z + 2, "white_concrete");
		// water basin for boats
		fill(sp, 44, y - 8, -12, 96, y + 4, 42, "smooth_sandstone");
		fill(sp, 45, y - 6, -11, 95, y - 1, 41, "water");
		fill(sp, 45, y, -11, 95, y + 4, 41, "air");
	}

	/** /fill split into slices along z so each stays under the block limit. */
	private static void fill(TestSingleplayerContext sp, int x0, int y0, int z0, int x1, int y1, int z1, String block) {
		int perZ = (x1 - x0 + 1) * (y1 - y0 + 1);
		int step = Math.max(1, 30000 / perZ);
		for (int z = z0; z <= z1; z += step) {
			cmd(sp, "fill " + x0 + " " + y0 + " " + z + " " + x1 + " " + y1 + " " + Math.min(z1, z + step - 1) + " minecraft:" + block);
		}
	}

	/** Teleports the player to (x,y,z) looking at (tx,ty,tz) from eye height. */
	private static void face(TestSingleplayerContext sp, double x, double y, double z, double tx, double ty, double tz) {
		double dx = tx - x, dy = ty - (y + 1.62), dz = tz - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		cmd(sp, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
	}

	private static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}

	private static void log(TestSingleplayerContext sp, String msg) {
		System.out.println("[leonida-test] vehicle: " + msg);
	}

	private static VehicleEntity vehicle(MinecraftServer s, int id) {
		return (VehicleEntity) s.overworld().getEntity(id);
	}

	private static int spawn(TestSingleplayerContext sp, String type, double x, double y, double z, float yaw, boolean locked) {
		return sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			VehicleEntity v = VehicleApi.spawn(level, type, new Vec3(x, y, z), yaw);
			if (v == null) throw new IllegalStateException("could not spawn " + type);
			v.setLocked(locked);
			v.setOwner(null);
			return v.getId();
		});
	}

	private static void discard(TestSingleplayerContext sp, int id) {
		sp.getServer().runOnServer(s -> {
			Entity e = s.overworld().getEntity(id);
			if (e != null) {
				e.ejectPassengers();
				e.discard();
			}
		});
	}

	private static String riding(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(s -> {
			ServerPlayer p = s.getPlayerList().getPlayers().get(0);
			return String.valueOf(p.getVehicle() == null ? "none" : p.getVehicle().getType().toShortString());
		});
	}

	private static void dismount(TestSingleplayerContext sp) {
		sp.getServer().runOnServer(s -> s.getPlayerList().getPlayers().get(0).stopRiding());
	}

	private static String info(TestSingleplayerContext sp, int id) {
		return sp.getServer().computeOnServer(s -> {
			VehicleEntity v = vehicle(s, id);
			if (v == null) return "gone";
			return String.format(Locale.ROOT, "yaw=%.0f pos=%.1f,%.1f,%.1f speed=%.1f health=%.0f/%.0f", v.getYRot(), v.getX(), v.getY(), v.getZ(), v.serverSpeed(),
					v.getHealth(), v.spec.maxHealth());
		});
	}

	/** Lane width a vehicle needs in the angled line-up (yaw 30). */
	private static double gap(VehicleSpec spec) {
		return spec.width() * 0.87 + spec.length() * 0.5 + 0.5;
	}

	private static String trafficSummary(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(s -> {
			StringBuilder b = new StringBuilder();
			int n = 0;
			for (Entity e : s.overworld().getAllEntities()) if (e instanceof VehicleEntity v) {
				n++;
				b.append(String.format(Locale.ROOT, " %s@%.0f,%.0f,%.0f", v.spec.id(), v.getX(), v.getY(), v.getZ()));
			}
			return n + b.toString();
		});
	}

	private static float yaw(TestSingleplayerContext sp, int id) {
		return sp.getServer().computeOnServer(s -> vehicle(s, id).getYRot());
	}

	private static double carZ(TestSingleplayerContext sp, int id) {
		return sp.getServer().computeOnServer(s -> vehicle(s, id) == null ? 99.0 : vehicle(s, id).getZ());
	}

	private static int villagers(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(s -> {
			int n = 0;
			for (Entity e : s.overworld().getAllEntities()) if (e instanceof net.minecraft.world.entity.npc.villager.Villager && e.isAlive()) n++;
			return n;
		});
	}

	private static String entitiesNear(TestSingleplayerContext sp, double x, double y, double z, double r) {
		return sp.getServer().computeOnServer(s -> {
			java.util.Map<String, Integer> m = new java.util.TreeMap<>();
			for (Entity e : s.overworld().getEntities((Entity) null, new net.minecraft.world.phys.AABB(x - r, y - r, z - r, x + r, y + r, z + r), e -> true))
				m.merge(e.getType().toShortString(), 1, Integer::sum);
			return m.toString();
		});
	}

	private static boolean crashed(TestSingleplayerContext sp, int id) {
		return sp.getServer().computeOnServer(s -> {
			VehicleEntity v = vehicle(s, id);
			return v == null || v.getHealth() < v.spec.maxHealth() - 1;
		});
	}

	/** A straight two-lane road along x=-5..5 for the traffic and routing code. */
	static final class TestMap implements MapQuery {
		public Surface surfaceAt(int x, int z) {
			if (x >= 44 && x <= 96 && z >= -12 && z <= 42) return Surface.WATER;
			if (Math.abs(x) <= 5 && z >= -120 && z <= 160) return Surface.ROAD;
			if (Math.abs(x) <= 9 && z >= -40 && z <= 60) return Surface.SIDEWALK;
			return Surface.LAND;
		}

		public int groundY(int x, int z) { return Y; }
		public int mapColor(int x, int z) { return 0xFF6A8F5A; }

		public List<BlockPos> route(BlockPos from, BlockPos to) {
			List<BlockPos> out = new ArrayList<>();
			int dir = to.getZ() >= from.getZ() ? 1 : -1;
			int x = 0; // road centre line; the traffic driver applies the lane offset
			out.add(from);
			for (int z = from.getZ(); dir > 0 ? z <= to.getZ() : z >= to.getZ(); z += 8 * dir) out.add(new BlockPos(x, Y, z));
			out.add(new BlockPos(x, Y, to.getZ()));
			return out;
		}

		public String placeName(int x, int z) { return "Test Strip"; }
	}
}
