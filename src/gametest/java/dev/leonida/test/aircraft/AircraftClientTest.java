package dev.leonida.test.aircraft;

import dev.leonida.aircraft.AircraftClient;
import dev.leonida.aircraft.AircraftModule;
import dev.leonida.aircraft.entity.AircraftEntity;
import dev.leonida.aircraft.entity.ParachuteEntity;
import dev.leonida.core.api.CrimeEvents;
import dev.leonida.test.TestFilter;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/** Client game tests for the aircraft module. Owned by the aircraft agent. Run: tools/pod-test.sh aircraft */
public class AircraftClientTest implements FabricClientGameTest {
	private static final String[] TYPES = {"western_duster", "floatplane", "dodo", "shamal", "maverick", "police_maverick", "news_maverick", "buzzard"};

	private static int distance(String t) {
		return switch (t) {
			case "floatplane", "dodo", "shamal" -> 20;
			case "western_duster" -> 15;
			default -> 14;
		};
	}
	private static final AtomicInteger THEFTS = new AtomicInteger();

	private static void log(String s) {
		System.out.println("[leonida-test] aircraft: " + s);
	}

	private static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("aircraft")) return;
		CrimeEvents.CRIME.register((player, crime, pos) -> {
			if (crime == CrimeEvents.Crime.AIRCRAFT_THEFT) THEFTS.incrementAndGet();
		});
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			cmd(sp, "gamerule advance_time false");
			cmd(sp, "gamerule advance_weather false");
			cmd(sp, "weather clear");
			cmd(sp, "time set noon");
			cmd(sp, "gamemode creative @a");

			// ---------------- on the ground
			int x = 0;
			for (String t : TYPES) {
				cmd(sp, "kill @e[type=!player]");
				cmd(sp, "execute as @a at @s run tp @s " + x + " ~ 0 0 12");
				context.waitTicks(5);
				cmd(sp, "execute as @a at @s rotated ~ 0 positioned ^ ^ ^" + distance(t) + " rotated ~-70 0 run leonida_aircraft spawn " + t + " 0");
				context.waitTicks(25);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("aircraft-ground-" + t);
				sp.getServer().runOnServer(server -> {
					ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
					for (AircraftEntity a : p.level().getEntitiesOfClass(AircraftEntity.class, p.getBoundingBox().inflate(40))) {
						log("ground " + t + ": aircraft at " + a.position() + " onGround=" + a.onGround() + " box=" + a.getBoundingBox() + ", player at " + p.position());
					}
				});
				x += 60;
			}

			// ---------------- paint schemes side by side
			for (String t : new String[] {"western_duster", "floatplane", "maverick"}) {
				cmd(sp, "kill @e[type=!player]");
				cmd(sp, "execute as @a at @s run tp @s " + x + " ~ 0 0 20");
				context.waitTicks(5);
				int gap = t.equals("floatplane") ? 34 : t.equals("western_duster") ? 22 : 16;
				for (int v = 0; v < 3; v++) {
					cmd(sp, "execute as @a at @s rotated ~ 0 positioned ^" + ((v - 1) * gap) + " ^ ^" + (gap + 8) + " rotated ~-150 0 run leonida_aircraft spawn " + t + " " + v);
				}
				context.waitTicks(25);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("aircraft-variants-" + t);
				x += 80;
			}

			// ---------------- theft event when a player takes the pilot seat
			int before = THEFTS.get();
			cmd(sp, "execute as @a run ride @s mount @e[type=leonida:aircraft/maverick,limit=1,sort=nearest]");
			context.waitTicks(5);
			log("theft events fired on boarding: " + (THEFTS.get() - before));
			cmd(sp, "execute as @a run ride @s dismount");
			context.waitTicks(5);

			// ---------------- parked aircraft spawn points (placement logic; the timed spawner needs the real map)
			for (String[] pt : new String[][] {{"vcia_fbo_2", "land"}, {"vice_beach_pd", "land"}, {"watson_seaplane_base", "water"}}) {
				cmd(sp, "kill @e[type=!player]");
				cmd(sp, "execute as @a at @s run tp @s " + x + " ~ 0 0 0");
				context.waitTicks(5);
				if (pt[1].equals("water")) {
					cmd(sp, "execute as @a at @s run fill ~-24 ~-2 ~6 ~24 ~-1 ~54 minecraft:water");
				}
				context.waitTicks(5);
				cmd(sp, "execute as @a at @s positioned ~ ~ ~30 run leonida_aircraft spawnpoint " + pt[0]);
				context.waitTicks(10);
				int n = sp.getServer().computeOnServer(server -> {
					ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
					return p.level().getEntitiesOfClass(AircraftEntity.class, p.getBoundingBox().inflate(120), a -> a.spawnPoint != null).size();
				});
				log("spawn point " + pt[0] + ": parked=" + n);
				cmd(sp, "execute as @a at @s run tp @s ~ ~12 ~-6 0 25");
				context.waitTicks(20);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("aircraft-spawnpoint-" + pt[0]);
				x += 160;
			}

			// ---------------- in flight (autopilot, the player rides along, chase camera)
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			for (String t : TYPES) {
				cmd(sp, "kill @e[type=!player]");
				cmd(sp, "execute as @a at @s run tp @s " + x + " ~ 0 0 0");
				context.waitTicks(5);
				cmd(sp, "execute as @a at @s run leonida_aircraft fly " + t + " true");
				context.waitTicks(2);
				cmd(sp, "execute as @a rotated as @e[type=leonida:aircraft/" + t + ",limit=1] run rotate @s ~ 12");
				context.waitTicks(t.equals("western_duster") ? 90 : 70);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("aircraft-flight-" + t);
				boolean riding = context.computeOnClient(mc -> mc.player.getVehicle() instanceof AircraftEntity);
				double alt = context.computeOnClient(mc -> mc.player.getY());
				log("flight " + t + ": riding=" + riding + " y=" + String.format("%.1f", alt));
				cmd(sp, "execute as @a run ride @s dismount");
				cmd(sp, "kill @e[type=!player]");
				cmd(sp, "execute as @a at @s run tp @s ~ -64 ~");
				cmd(sp, "execute as @a at @s run spreadplayers ~ ~ 0 1 false @s");
				context.waitTicks(10);
				x += 120;
			}

			// crop duster from the ground, to see the pink trail
			cmd(sp, "clear @a");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			cmd(sp, "kill @e[type=!player]");
			cmd(sp, "execute as @a at @s run tp @s " + x + " ~ 0 0 0");
			context.waitTicks(5);
			cmd(sp, "execute as @a at @s run leonida_aircraft fly western_duster false");
			context.waitTicks(120);
			cmd(sp, "execute as @a at @s run tp @s ~ ~ ~ facing entity @e[type=leonida:aircraft/western_duster,limit=1] feet");
			context.waitTicks(3);
			context.takeScreenshot("aircraft-flight-western_duster-trail");
			x += 120;

			// ---------------- cockpits and cabins in first person
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			for (String t : TYPES) {
				for (int seat : (t.equals("dodo") || t.equals("shamal") || t.equals("maverick")) ? new int[] {0, 2} : new int[] {0}) {
					cmd(sp, "kill @e[type=!player]");
					cmd(sp, "execute as @a at @s run tp @s " + x + " ~ 0 0 0");
					context.waitTicks(5);
					cmd(sp, "execute as @a at @s rotated 0 0 run leonida_aircraft spawn " + t + " 0");
					context.waitTicks(5);
					String sel = "@e[type=leonida:aircraft/" + t + ",limit=1,sort=nearest]";
					for (int i = 0; i < seat; i++) {
						cmd(sp, "execute as @a at @s run summon armor_stand ~ ~ ~ {Invisible:1b,Tags:[\"seatfill\"]}");
						cmd(sp, "execute as @a at @s run ride @e[tag=seatfill,limit=1] mount " + sel);
						cmd(sp, "tag @e[tag=seatfill] remove seatfill");
					}
					cmd(sp, "execute as @a at @s run ride @s mount " + sel);
					context.waitTicks(5);
					if (seat > 0) {
						cmd(sp, "execute as @a run leonida_aircraft seat " + seat);
						context.waitTicks(5);
					}
					cmd(sp, "execute as @a run rotate @s 0 10");
					context.waitTicks(20);
					sp.getConnection().waitForChunksRender();
					context.takeScreenshot("aircraft-" + (seat == 0 ? "cockpit-" : "cabin-") + t);
					boolean riding = context.computeOnClient(mc -> mc.player.getVehicle() instanceof AircraftEntity);
					int idx = context.computeOnClient(mc -> mc.player.getVehicle() == null ? -1 : mc.player.getVehicle().getPassengers().indexOf(mc.player));
					log((seat == 0 ? "cockpit " : "cabin ") + t + ": riding=" + riding + ", seat=" + idx);
					cmd(sp, "execute as @a run ride @s dismount");
					x += 40;
				}
			}
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

			// ---------------- police helicopter at night with its searchlight on the player
			cmd(sp, "kill @e[type=!player]");
			cmd(sp, "time set midnight");
			cmd(sp, "execute as @a at @s run tp @s " + x + " ~ 0 0 0");
			context.waitTicks(5);
			cmd(sp, "execute as @a at @s run leonida_aircraft police");
			context.waitTicks(220);
			cmd(sp, "execute as @a at @s run tp @s ~ ~ ~ facing entity @e[type=leonida:aircraft/police_maverick,limit=1] feet");
			context.waitTicks(3);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("aircraft-police-searchlight-up");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			cmd(sp, "execute as @a at @s run tp @s ~ ~ ~ ~180 -25");
			context.waitTicks(10);
			context.takeScreenshot("aircraft-police-searchlight-front");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			cmd(sp, "execute as @a at @s run tp @s ~ ~ ~ ~ 30");
			context.waitTicks(10);
			context.takeScreenshot("aircraft-police-searchlight-back");
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				BlockPos at = p.blockPosition();
				boolean lit = false;
				for (int dy = -1; dy <= 3; dy++) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
					if (p.level().getBlockState(at.offset(dx, dy, dz)).is(Blocks.LIGHT)) lit = true;
				}
				int helis = dev.leonida.aircraft.api.PoliceHelicopters.active(p.level()).size();
				log("police helicopters active=" + helis + ", light block near player=" + lit
						+ ", block light=" + p.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at));
			});
			// survive a save and reload: write the heli out, discard it, load a copy, it must find the player again
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				net.minecraft.server.level.ServerLevel lvl = p.level();
				AircraftEntity heli = dev.leonida.aircraft.api.PoliceHelicopters.active(lvl).getFirst();
				net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput
						.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, lvl.registryAccess());
				heli.saveWithoutId(out);
				net.minecraft.nbt.CompoundTag tag = out.buildResult();
				heli.discard();
				AircraftEntity copy = AircraftModule.type(AircraftModule.POLICE_MAVERICK).create(lvl, net.minecraft.world.entity.EntitySpawnReason.LOAD);
				copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, lvl.registryAccess(), tag));
				copy.setUUID(java.util.UUID.randomUUID());
				lvl.addFreshEntity(copy);
				log("police reload: saved target=" + tag.contains("PoliceTarget") + ", waiting for player=" + (copy.autopilot.pendingTarget != null));
			});
			context.waitTicks(60);
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
				AircraftEntity heli = dev.leonida.aircraft.api.PoliceHelicopters.active(p.level()).getFirst();
				log("police after reload: following player=" + (heli.autopilot.target == p) + ", searchlight=" + heli.isSearchlightOn());
			});
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			cmd(sp, "execute as @a at @s run tp @s ~ ~ ~ facing entity @e[type=leonida:aircraft/police_maverick,limit=1] feet");
			context.waitTicks(3);
			context.takeScreenshot("aircraft-police-searchlight-chase");
			cmd(sp, "leonida_aircraft police_dismiss");
			context.waitTicks(60);
			context.takeScreenshot("aircraft-police-leaving");

			// a second police helicopter on patrol, seen from a distance with its searchlight sweeping the ground
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			cmd(sp, "kill @e[type=!player]");
			cmd(sp, "execute as @a at @s run tp @s ~60 ~ ~ 0 0");
			context.waitTicks(5);
			cmd(sp, "execute as @a at @s positioned ~ ~ ~45 run leonida_aircraft fly police_maverick false");
			context.waitTicks(120);
			cmd(sp, "execute as @a at @s run tp @s ~ ~ ~ facing entity @e[type=leonida:aircraft/police_maverick,limit=1] feet");
			context.waitTicks(3);
			cmd(sp, "execute as @a at @s run tp @s ~ ~ ~ ~ ~15");
			context.waitTicks(3);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("aircraft-police-searchlight-patrol");

			// ---------------- parachute: freefall from high up, deploy with jump, glide down
			cmd(sp, "kill @e[type=!player]");
			cmd(sp, "time set noon");
			cmd(sp, "gamemode survival @a");
			cmd(sp, "clear @a");
			cmd(sp, "give @a leonida:aircraft/parachute");
			cmd(sp, "execute as @a at @s run tp @s " + (x + 200) + " ~80 0 0 20");
			context.waitTicks(30);
			boolean ff = context.computeOnClient(mc -> AircraftClient.freefall);
			log("freefall detected=" + ff);
			context.takeScreenshot("aircraft-parachute-freefall");
			context.getInput().holdKeyFor(o -> o.keyJump, 3);
			context.waitTicks(20);
			boolean open = context.computeOnClient(mc -> mc.player.getVehicle() instanceof ParachuteEntity);
			log("parachute open after jump key=" + open);
			if (!open) {
				cmd(sp, "execute as @a run leonida_aircraft parachute");
				context.waitTicks(20);
			}
			context.takeScreenshot("aircraft-parachute-open");
			context.getInput().holdKeyFor(o -> o.keyLeft, 30);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("aircraft-parachute-front");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			int waited = context.waitFor(mc -> mc.player.onGround() && !mc.player.isPassenger(), 1200);
			float health = context.computeOnClient(mc -> mc.player.getHealth());
			log("landed after " + waited + " ticks, health=" + health);
			context.waitTicks(10);
			context.takeScreenshot("aircraft-parachute-landed");
			log("parachute items left=" + context.computeOnClient(mc -> mc.player.getInventory().countItem(AircraftModule.PARACHUTE_ITEM)));
			context.runOnClient(mc -> {
				mc.options.setCameraType(CameraType.FIRST_PERSON);
			});
		}
	}
}
