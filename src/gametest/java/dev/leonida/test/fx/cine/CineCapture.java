package dev.leonida.test.fx.cine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.leonida.core.api.MapQuery;
import dev.leonida.world.WorldModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Deterministic cinematic capture for demo videos. Test source set only: nothing here ships to players.
 *
 * <p>Shots come from shots.json next to this class (edit it, no code changes). Ticks are frozen with /tick freeze;
 * every frame the camera (and the filmed vehicle) is placed from a Catmull-Rom spline with an ease-in/ease-out time
 * curve, the frame is rendered with an explicit partial tick (60 fps = 1/3 tick per frame) and saved as PNG; every
 * third frame the world advances by exactly one tick (/tick step 1), so traffic, peds, water and the sun move at real
 * speed. Frames are encoded by ffmpeg (libx264 high, yuv420p, CRF from the shot list) into one MP4 per shot.
 */
public final class CineCapture {
	private CineCapture() {}

	private record Key(double t, Vec3 pos, double yaw, double pitch, double fov, boolean hasYaw, boolean absY, Vec3 look, boolean lookAbsY) {
		Key(double t, Vec3 pos, double yaw, double pitch, double fov, boolean hasYaw) {
			this(t, pos, yaw, pitch, fov, hasYaw, true, null, true);
		}
	}

	private record Shot(String name, String mode, long time, String weather, double duration, int rd, boolean relativeY, int lodWait,
			int preloadTimeout, boolean snapToRoad, double brightness, double exposure, String entity, String driver, double back, double up, Vec3 seat, List<Key> keys,
			String character, double camDist, boolean ease, JsonArray chasers, JsonObject fire, String outName, JsonArray actors, JsonArray props) {}

	/** Camera (or vehicle) state at one instant. */
	private record Pose(Vec3 pos, double yaw, double pitch, double fov) {}

	/**
	 * Run-time override for a shot list option: system property leonida.cine.<key>, else env LEONIDA_CINE_<KEY>
	 * (tools/record-trailer.ps1 sets the env form). Keys: shots, run (comma list), out, ffmpeg, rd, dh, cache.
	 */
	static String cfg(String key) {
		String v = System.getProperty("leonida.cine." + key);
		if (v == null || v.isBlank()) v = System.getenv("LEONIDA_CINE_" + key.toUpperCase(Locale.ROOT));
		return v == null || v.isBlank() ? null : v.trim();
	}

	private static List<String> runList(JsonObject root) {
		List<String> run = new ArrayList<>();
		String o = cfg("run");
		if (o != null) {
			for (String n : o.split("[,\\s]+"))
				if (!n.isBlank()) run.add(n);
		} else if (root.has("run")) {
			for (JsonElement e : root.getAsJsonArray("run")) run.add(e.getAsString());
		}
		return run;
	}

	public static Path shotFile() {
		String prop = cfg("shots");
		if (prop != null) return Path.of(prop);
		Path dir = FabricLoader.getInstance().getGameDir().toAbsolutePath();
		for (Path d = dir; d != null; d = d.getParent()) {
			Path f = d.resolve("src/gametest/java/dev/leonida/test/fx/cine/shots.json");
			if (Files.exists(f)) return f;
		}
		return dir.resolve("shots.json");
	}

	/** True when shots.json names shots to render (the fx test then renders those instead of its normal checks). */
	public static boolean requested() {
		try {
			JsonObject root = JsonParser.parseString(Files.readString(shotFile())).getAsJsonObject();
			return !runList(root).isEmpty();
		} catch (Exception e) {
			return false;
		}
	}

	public static void run(ClientGameTestContext context) {
		// building cache (same effect as -Dleonida.build.cache=true): ~12x faster building generation, rare downtown block
		// differences are irrelevant for video. Set before the capture world exists so all its chunks use it.
		boolean cache = true;
		try {
			JsonObject r0 = JsonParser.parseString(Files.readString(shotFile())).getAsJsonObject();
			if (r0.has("buildCache")) cache = r0.get("buildCache").getAsBoolean();
			if (cfg("cache") != null) cache = Boolean.parseBoolean(cfg("cache"));
		} catch (Exception ignored) {
		}
		System.setProperty("leonida.build.cache", String.valueOf(cache));
		try {
			java.lang.reflect.Field f = Class.forName("dev.leonida.build.BuildCache").getDeclaredField("ENABLED");
			f.setAccessible(true);
			f.setBoolean(null, cache);
			log("building cache on: " + f.getBoolean(null));
		} catch (ReflectiveOperationException e) {
			log("building cache not available: " + e);
		}
		JsonObject root;
		try {
			root = JsonParser.parseString(Files.readString(shotFile())).getAsJsonObject();
		} catch (IOException e) {
			log("cannot read shot list " + shotFile() + ": " + e);
			return;
		}
		int width = cfg("width") != null ? Integer.parseInt(cfg("width")) : intOr(root, "width", 1920);
		int height = cfg("height") != null ? Integer.parseInt(cfg("height")) : intOr(root, "height", 1080);
		int fps = intOr(root, "fps", 60), crf = intOr(root, "crf", 16);
		Path out = FabricLoader.getInstance().getGameDir().toAbsolutePath().resolve(strOr(root, "output", "cine"));
		preview = root.has("preview") && root.get("preview").getAsBoolean();
		persistDir = cfg("out") != null ? cfg("out") : root.has("persistDir") ? root.get("persistDir").getAsString() : null;
		List<String> run = runList(root);
		List<Shot> shots = new ArrayList<>();
		for (JsonElement e : root.getAsJsonArray("shots")) {
			Shot s = parse(e.getAsJsonObject());
			if (run.contains(s.name)) shots.add(s);
		}
		log("shot list " + shotFile() + ": rendering " + shots.size() + " shot(s) at " + width + "x" + height + " " + fps + " fps");

		try (TestSingleplayerContext sp = openWorld(context)) {
			sp.getConnection().waitForChunksRender();
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
				mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.FANCY);
				mc.options.bobView().set(false);
				// fixed output size: render target resized once, so per-frame captures never resize (Iris reallocates on resize)
				mc.getWindow().setWidth(width);
				mc.getWindow().setHeight(height);
				mc.gameRenderer.resize(width, height);
			});
			irisHigh(context);
			boolean dh = cfg("dh") != null ? Boolean.parseBoolean(cfg("dh")) : !root.has("dhRendering") || root.get("dhRendering").getAsBoolean();
			context.runOnClient(mc -> distantHorizons(dh));
			String shaderInfo = context.computeOnClient(mc -> describeShaders());
			log("renderer: " + shaderInfo);
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand("gamerule advance_weather false");
			sp.getServer().runCommand("gamerule spawn_mobs false");
			for (Shot s : shots) {
				try {
					shoot(context, sp, s, out, width, height, fps, crf);
				} catch (Throwable t) {
					log("shot " + s.name + " failed: " + t);
					t.printStackTrace();
				} finally {
					sp.getServer().runCommand("tick unfreeze");
					if (thirdPerson) {
						sp.getServer().runCommand("execute as @a run ride @s dismount");
						sp.getServer().runCommand("attribute @a minecraft:camera_distance base reset");
						sp.getServer().runCommand("gamemode spectator @a");
						context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
						thirdPerson = riding = false;
					}
					sp.getServer().runCommand("kill @e[tag=leonida_cine]");
					sp.getServer().runCommand("execute in leonida:world/leonida run forceload remove all");
				}
			}
		}
	}

	private static boolean preview;
	private static String persistDir;

	private static void shoot(ClientGameTestContext context, TestSingleplayerContext sp, Shot s, Path out, int width, int height, int fps,
			int crf) throws IOException, InterruptedException {
		MapQuery q = MapQuery.get();
		easeOn = s.ease;
		List<Key> keys = new ArrayList<>();
		for (Key k0 : s.keys) {
			double x = s.snapToRoad ? roadCenterX(q, k0.pos.x, k0.pos.z) : k0.pos.x;
			double y = s.relativeY && !k0.absY ? q.groundY((int) Math.floor(x), (int) Math.floor(k0.pos.z)) + 1 + k0.pos.y : k0.pos.y;
			double yaw = k0.yaw, pitch = k0.pitch;
			boolean hasYaw = k0.hasYaw;
			if (k0.look != null) {
				// aim at a target point: yaw/pitch from the camera position (Minecraft convention, yaw 0 = +z)
				double ly = s.relativeY && !k0.lookAbsY ? q.groundY((int) Math.floor(k0.look.x), (int) Math.floor(k0.look.z)) + 1 + k0.look.y : k0.look.y;
				double dx = k0.look.x - x, dy = ly - y, dz = k0.look.z - k0.pos.z;
				yaw = Math.toDegrees(Math.atan2(-dx, dz));
				pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
				hasYaw = true;
			}
			keys.add(new Key(k0.t, new Vec3(x, y, k0.pos.z), yaw, pitch, k0.fov, hasYaw));
		}
		for (int i = 1; i < keys.size(); i++) {
			Key a = keys.get(i - 1), b = keys.get(i);
			double yw = b.yaw;
			while (yw - a.yaw > 180) yw -= 360;
			while (yw - a.yaw < -180) yw += 360;
			if (yw != b.yaw) keys.set(i, new Key(b.t, b.pos, yw, b.pitch, b.fov, b.hasYaw));
		}
		int frames = (int) Math.round(s.duration * fps);
		if (cfg("frames") != null) frames = Math.max(1, Math.min(frames, Integer.parseInt(cfg("frames")))); // smoke runs
		double ticksPerFrame = 20.0 / fps;
		log("shot " + s.name + " (" + s.mode + "): " + frames + " frames, time " + s.time + ", path start " + keys.get(0).pos + " end "
				+ keys.get(keys.size() - 1).pos);

		// --- setup: time, weather, render distance, preload everything around the whole path
		sp.getServer().runCommand("tick unfreeze");
		sp.getServer().runCommand("gamerule advance_time true");
		sp.getServer().runCommand("time set " + s.time);
		sp.getServer().runCommand("weather " + s.weather);
		context.runOnClient(mc -> {
			// per-shot game brightness (vanilla gamma option, 0..1; night shots read better around 0.8-1.0)
			if (s.brightness >= 0) mc.options.gamma().set(Mth.clamp(s.brightness, 0.0, 1.0));
			mc.options.renderDistance().set(s.rd);
			mc.options.broadcastOptions();
		});
		sp.getServer().runOnServer(server -> server.getPlayerList().setViewDistance(s.rd));
		Pose start = cameraPose(s, keys, 0, null);
		Vec3 sp0 = start.pos;
		sp.getServer().runCommand(String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f %.1f %.1f", sp0.x,
				sp0.y, sp0.z, start.yaw, start.pitch));
		forceloadPath(sp, keys, s.rd);
		if (!waitLoaded(context, sp, s, keys)) {
			log("shot " + s.name + ": SKIPPED, terrain not ready within " + s.preloadTimeout + " s");
			return;
		}
		warmUp(context, sp, s, keys);
		int lodWait = preview ? Math.min(s.lodWait, 6) : s.lodWait;
		if (lodWait > 0) {
			log("shot " + s.name + ": waiting " + lodWait + " s for Distant Horizons LODs");
			context.waitTicks(lodWait * 20);
		}

		// --- third person: the player is the actor (HD Jason/Lucia model), vanilla third-person camera behind them
		thirdPerson = s.mode.equals("thirdperson");
		riding = false;
		walkA = 0;
		walkPhase = 0;
		if (thirdPerson) {
			sp.getServer().runCommand("execute as @a run leonida character " + s.character + " instant");
			sp.getServer().runCommand("gamemode creative @a");
			sp.getServer().runCommand(String.format(Locale.ROOT, "attribute @a minecraft:camera_distance base set %.2f", s.camDist));
			context.waitTicks(10);
			context.runOnClient(mc -> {
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
				if (mc.player != null) {
					mc.player.getAbilities().mayfly = true;
					mc.player.getAbilities().flying = s.entity == null;
				}
			});
			log("shot " + s.name + ": third person as " + s.character + ", camera distance " + s.camDist);
		}

		// --- vehicle (follow / cockpit / boat / thirdperson with a vehicle)
		int vehicleId = -1;
		if (!s.mode.equals("free") && s.entity != null) {
			Pose v = vehiclePose(s, keys, 0);
			String passengers = s.driver != null ? ",Passengers:[{id:\"" + s.driver + "\",NoAI:1b,Silent:1b,Tags:[\"leonida_cine\"]}]" : "";
			sp.getServer().runCommand(String.format(Locale.ROOT,
					"execute in leonida:world/leonida run summon %s %.3f %.3f %.3f {NoGravity:1b,Invulnerable:1b,Tags:[\"leonida_cine\"],Rotation:[%.1ff,0f]%s}",
					s.entity, v.pos.x, v.pos.y, v.pos.z, v.yaw, passengers));
			context.waitTicks(5);
			vehicleId = sp.getServer().computeOnServer(server -> {
				ServerLevel lvl = server.getLevel(WorldModule.LEONIDA);
				for (Entity e : lvl.getAllEntities())
					if (e.entityTags().contains("leonida_cine") && !e.isPassenger()) return e.getId();
				return -1;
			});
			log("shot " + s.name + ": vehicle " + s.entity + " id " + vehicleId);
			if (thirdPerson && vehicleId >= 0) {
				// the player drives: ride the vehicle so the third-person camera trails behind and above the car
				sp.getServer().runCommand("execute as @a run ride @s mount @e[tag=leonida_cine,limit=1,sort=nearest]");
				context.waitTicks(5);
				riding = context.computeOnClient(mc -> mc.player != null && mc.player.getVehicle() != null);
				log("shot " + s.name + ": player riding: " + riding);
			}
		}
		spawnActors(context, sp, s, keys, q);
		chaserIds.clear();
		for (JsonElement ce : s.chasers) {
			// police (or any) vehicles trailing the lead car on the same route: lag seconds behind, side blocks to the right
			JsonObject c = ce.getAsJsonObject();
			Pose v = chaserPose(s, keys, 0, c);
			String drv = c.has("driver") ? c.get("driver").getAsString() : "minecraft:villager";
			sp.getServer().runCommand(String.format(Locale.ROOT,
					"execute in leonida:world/leonida run summon %s %.3f %.3f %.3f {NoGravity:1b,Invulnerable:1b,Tags:[\"leonida_cine\",\"leonida_cine_chaser\"],Rotation:[%.1ff,0f],Passengers:[{id:\"%s\",NoAI:1b,Silent:1b,Tags:[\"leonida_cine\"]}]}",
					c.get("entity").getAsString(), v.pos.x, v.pos.y, v.pos.z, v.yaw, drv));
			context.waitTicks(3);
			final double px = v.pos.x, pz = v.pos.z;
			int id = sp.getServer().computeOnServer(server -> {
				ServerLevel lvl = server.getLevel(WorldModule.LEONIDA);
				Entity best = null;
				for (Entity e : lvl.getAllEntities())
					if (e.entityTags().contains("leonida_cine_chaser") && !e.isPassenger() && !chaserIds.contains(e.getId())
							&& (best == null || e.distanceToSqr(px, e.getY(), pz) < best.distanceToSqr(px, best.getY(), pz))) best = e;
				if (best instanceof dev.leonida.vehicle.VehicleEntity ve) {
					ve.setSiren(true);
					ve.setHeadlights(true);
				}
				return best == null ? -1 : best.getId();
			});
			chaserIds.add(id);
		}
		if (!s.chasers.isEmpty()) log("shot " + s.name + ": chasers " + chaserIds + " (sirens on)");
		if (s.fire != null) {
			String gun = strOr(s.fire, "gun", "rifle");
			sp.getServer().runOnServer(server -> {
				for (net.minecraft.server.level.ServerPlayer pl : server.getPlayerList().getPlayers())
					pl.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, dev.leonida.weapon.api.WeaponApi.gun(gun));
			});
			context.waitTicks(3);
			log("shot " + s.name + ": armed with " + gun);
		}
		{
		}

		// --- capture: frozen world, exact per-frame placement and partial tick
		// the clock kept running during preload and warm-up: put the shot's time of day back right before freezing
		sp.getServer().runCommand("time set " + s.time);
		sp.getServer().runCommand("tick freeze");
		context.waitTicks(2);
		Path dir = out.resolve(s.name);
		deleteDir(dir);
		Files.createDirectories(dir);
		long renderNanos = 0, gateNanos = 0;
		int capped = 0;
		final int vid = vehicleId;
		if (preview) {
			// quick look before the full render: three frames placed directly (no tick stepping), no encode
			Path shots = FabricLoader.getInstance().getGameDir().resolve("screenshots");
			Files.createDirectories(shots);
			for (double fr : new double[] {0.1, 0.5, 0.9}) {
				double t = fr * s.duration;
				Pose cam = cameraPose(s, keys, t, null);
				Pose veh = vid >= 0 ? vehiclePose(s, keys, t) : null;
				if (vid >= 0) sp.getServer().runOnServer(server -> {
					Entity e = server.getLevel(WorldModule.LEONIDA).getEntity(vid);
					if (e != null) e.snapTo(veh.pos.x, veh.pos.y, veh.pos.z, (float) veh.yaw, (float) veh.pitch);
				});
				if (!riding) sp.getServer().runCommand(String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.3f %.3f %.3f", cam.pos.x,
						cam.pos.y - 1.62, cam.pos.z));
				sp.getServer().runCommand("tick step 1");
				context.waitTicks(3);
				context.runOnClient(mc -> place(mc, cam, veh, vid));
				context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "cine-%s-preview-%02d", s.name, (int) (fr * 100)))
						.withSize(width, height).withDestinationDir(shots).disableCounterPrefix());
			}
			sp.getServer().runCommand("tick unfreeze");
			log("shot " + s.name + ": preview written");
			return;
		}
		for (int f = 0; f < frames; f++) {
			double gameTicks = f * ticksPerFrame;
			int whole = (int) Math.floor(gameTicks + 1e-6);
			float partial = (float) (gameTicks - whole);
			if (f > 0 && whole > (int) Math.floor((f - 1) * ticksPerFrame + 1e-6)) {
				// one world tick: vehicle to its route position at the new tick, then step
				double tt = whole / 20.0;
				if (vid >= 0) {
					Pose v = vehiclePose(s, keys, tt);
					sp.getServer().runOnServer(server -> {
						Entity e = server.getLevel(WorldModule.LEONIDA).getEntity(vid);
						if (e != null) {
							e.snapTo(v.pos.x, v.pos.y, v.pos.z, (float) v.yaw, (float) v.pitch);
							e.setDeltaMovement(Vec3.ZERO);
						}
					});
				}
				Pose c = cameraPose(s, keys, tt, null);
				if (!riding) sp.getServer().runCommand(String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.3f %.3f %.3f", c.pos.x,
						c.pos.y - 1.62, c.pos.z));
				for (int ci = 0; ci < chaserIds.size(); ci++) {
					Pose cp = chaserPose(s, keys, tt, s.chasers.get(ci).getAsJsonObject());
					int cid = chaserIds.get(ci);
					sp.getServer().runOnServer(server -> {
						Entity e = server.getLevel(WorldModule.LEONIDA).getEntity(cid);
						if (e != null) {
							e.snapTo(cp.pos.x, cp.pos.y, cp.pos.z, (float) cp.yaw, (float) cp.pitch);
							e.setDeltaMovement(Vec3.ZERO);
						}
					});
				}
				if (s.fire != null && tt >= dblOr(s.fire, "from", 0.5) && tt <= dblOr(s.fire, "to", s.duration - 0.3)) {
					// real weapon fire from the player: muzzle flash, tracers and impacts come from the weapon module
					Vec3 fdir = Vec3.directionFromRotation((float) (c.pitch + dblOr(s.fire, "pitchOffset", -8)), (float) c.yaw);
					sp.getServer().runOnServer(server -> {
						for (net.minecraft.server.level.ServerPlayer pl : server.getPlayerList().getPlayers())
							dev.leonida.weapon.Ballistics.tryFire(server.getLevel(WorldModule.LEONIDA), pl, pl.getMainHandItem(), fdir, true);
					});
				}
				sp.getServer().runCommand("tick step 1");
				context.waitTick();
			}
			double t = f / (double) fps;
			if (thirdPerson && !riding) {
				// limb swing like vanilla: amplitude min(4 * blocks per tick, 1), phase advances by the amplitude every tick
				Vec3 a = sample(keys, t, s.duration).pos, b = sample(keys, Math.min(s.duration, t + 1.0 / fps), s.duration).pos;
				double perTick = a.subtract(b).horizontalDistance() * fps / 20.0;
				walkA = (float) Math.min(perTick * 4.0, 1.0);
				walkPhase += walkA * (float) ticksPerFrame;
			}
			Pose cam = cameraPose(s, keys, t, null);
			Pose veh = vid >= 0 ? vehiclePose(s, keys, t) : null;
			lastPartial = partial;
			List<Pose> cps = new ArrayList<>();
			for (int ci = 0; ci < chaserIds.size(); ci++) cps.add(chaserPose(s, keys, t, s.chasers.get(ci).getAsJsonObject()));
			List<Pose> aps = actorPoses(sp, s, keys, t, fps);
			context.runOnClient(mc -> {
				place(mc, cam, veh, vid);
				placeChasers(mc, cps);
				placeActors(mc, aps);
			});
			// per-frame gate: keep rendering (unsaved) until every visible section is meshed, max 2 s
			long g0 = System.nanoTime();
			int extra = gate(context, 2000);
			gateNanos += System.nanoTime() - g0;
			if (extra < 0) {
				capped++;
				log("shot " + s.name + ": frame " + f + " hit the 2 s terrain gate cap");
			}
			if (extra != 0) context.runOnClient(mc -> {
				place(mc, cam, veh, vid);
				placeChasers(mc, cps);
				placeActors(mc, aps);
			});
			long t0 = System.nanoTime();
			context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "frame_%05d", f)).withSize(width, height)
					.withDeltaTicks(partial).withDestinationDir(dir).disableCounterPrefix());
			renderNanos += System.nanoTime() - t0;
		}
		sp.getServer().runCommand("tick unfreeze");
		double msPerFrame = renderNanos / 1e6 / frames;
		log(String.format(Locale.ROOT, "shot %s: %d frames captured, %.1f ms per frame (render + readback + PNG), terrain gate %.1f ms per frame, %d frame(s) hit the cap",
				s.name, frames, msPerFrame, gateNanos / 1e6 / frames, capped));
		popCheck(s, dir, frames);

		// --- encode, verify, stills
		Path mp4 = out.resolve(s.name + ".mp4");
		String ffmpeg = tool("ffmpeg");
		int rc = exec(List.of(ffmpeg, "-y", "-loglevel", "error", "-framerate", String.valueOf(fps), "-i", dir.resolve("frame_%05d.png").toString(),
				"-vf", s.exposure != 0 ? String.format(Locale.ROOT, "format=gbrpf32le,exposure=exposure=%.3f,format=yuv420p", s.exposure)
						: "format=yuv420p",
				"-c:v", "libx264", "-profile:v", "high", "-preset", "slow", "-pix_fmt", "yuv420p", "-crf", String.valueOf(crf), "-r",
				String.valueOf(fps), "-movflags", "+faststart", mp4.toString()));
		log("shot " + s.name + ": ffmpeg exit " + rc + " -> " + mp4);
		exec(List.of(tool("ffprobe"), "-v", "error", "-select_streams", "v:0", "-count_frames", "-show_entries",
				"stream=codec_name,profile,width,height,pix_fmt,r_frame_rate,nb_read_frames:format=duration", "-of", "compact", mp4.toString()));
		Path shots = FabricLoader.getInstance().getGameDir().resolve("screenshots");
		Files.createDirectories(shots);
		if (Files.exists(mp4)) Files.copy(mp4, shots.resolve("cine-" + s.name + ".mp4"), StandardCopyOption.REPLACE_EXISTING);
		if (persistDir != null && Files.exists(mp4)) {
			// persistent copy outside the run dir, so later runs in this checkout cannot wipe it
			Path pd = Path.of(persistDir);
			Files.createDirectories(pd);
			Files.copy(mp4, pd.resolve(s.outName + ".mp4"), StandardCopyOption.REPLACE_EXISTING);
			Path mid = dir.resolve(String.format(Locale.ROOT, "frame_%05d.png", frames / 2));
			if (Files.exists(mid)) Files.copy(mid, pd.resolve(s.outName + "-still.png"), StandardCopyOption.REPLACE_EXISTING);
			log("shot " + s.name + ": saved " + pd.resolve(s.outName + ".mp4"));
		}
		for (double fr : new double[] {0.2, 0.5, 0.8}) {
			int f = Math.min(frames - 1, (int) (frames * fr));
			Path src = dir.resolve(String.format(Locale.ROOT, "frame_%05d.png", f));
			if (Files.exists(src)) Files.copy(src, shots.resolve(String.format(Locale.ROOT, "cine-%s-still-%03d.png", s.name, f)),
					StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Exact placement for this frame: current and previous positions equal, so any partial tick gives this pose. */
	private static void place(Minecraft mc, Pose cam, Pose veh, int vid) {
		LocalPlayer p = mc.player;
		if (p == null) return;
		double eye = p.getEyeHeight();
		p.setDeltaMovement(Vec3.ZERO);
		p.setPos(cam.pos.x, cam.pos.y - eye, cam.pos.z);
		p.xo = p.xOld = cam.pos.x;
		p.yo = p.yOld = cam.pos.y - eye;
		p.zo = p.zOld = cam.pos.z;
		p.setYRot((float) cam.yaw);
		p.setXRot((float) cam.pitch);
		p.yRotO = (float) cam.yaw;
		p.xRotO = (float) cam.pitch;
		p.setYHeadRot((float) cam.yaw);
		p.yHeadRotO = (float) cam.yaw;
		mc.options.fov().set(Mth.clamp((int) Math.round(cam.fov), 30, 110));
		if (thirdPerson) {
			p.yBodyRot = p.yBodyRotO = (float) cam.yaw;
			if (!riding) {
				p.getAbilities().flying = true;
				setWalk(p.walkAnimation, walkA, walkPhase);
			}
		}
		if (veh != null && mc.level != null) {
			Entity e = mc.level.getEntity(vid);
			if (e != null) {
				e.setPos(veh.pos);
				e.xo = e.xOld = veh.pos.x;
				e.yo = e.yOld = veh.pos.y;
				e.zo = e.zOld = veh.pos.z;
				e.setYRot((float) veh.yaw);
				e.yRotO = (float) veh.yaw;
				e.setXRot((float) veh.pitch);
				e.xRotO = (float) veh.pitch;
				for (Entity r : e.getPassengers()) {
					e.positionRider(r);
					r.xo = r.xOld = r.getX();
					r.yo = r.yOld = r.getY();
					r.zo = r.zOld = r.getZ();
				}
			}
		}
	}

	// ---------------------------------------------------------------- paths

	/** Ease-in/ease-out on the shot clock, then Catmull-Rom through the keys (by key time). */
	/**
	 * Reuse the clone's previous capture world (saves/cine-reuse, prepared by the launcher from the last run's save)
	 * so chunks generated for earlier shots load from disk instead of being generated again; else a fresh world.
	 */
	private static TestSingleplayerContext openWorld(ClientGameTestContext context) {
		Path save = FabricLoader.getInstance().getGameDir().resolve("saves").resolve("cine-reuse");
		if (Files.exists(save.resolve("level.dat"))) {
			try {
				Object ws = Class.forName("net.fabricmc.fabric.impl.client.gametest.world.TestWorldSaveImpl")
						.getConstructor(ClientGameTestContext.class, Path.class).newInstance(context, save);
				log("reusing world save " + save);
				return ((net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave) ws).open();
			} catch (ReflectiveOperationException e) {
				log("world reuse unavailable, creating a new world: " + e);
			}
		}
		return context.worldBuilder().create();
	}

	private static boolean easeOn = true;

	/** Like sample, but continues linearly before the start (chasers that trail the lead car). */
	private static Pose sampleExt(List<Key> keys, double t, double duration) {
		if (t >= 0) return sample(keys, t, duration);
		Pose a = sample(keys, 0, duration), b = sample(keys, 0.05, duration);
		Vec3 v = b.pos.subtract(a.pos).scale(1 / 0.05);
		return new Pose(a.pos.add(v.scale(t)), a.yaw, a.pitch, a.fov);
	}

	private static Pose sample(List<Key> keys, double t, double duration) {
		double u = Mth.clamp(t / Math.max(duration, 1e-6), 0.0, 1.0);
		double e = easeOn ? u * u * (3.0 - 2.0 * u) : u; // smoothstep ease
		double tt = keys.get(0).t + e * (keys.get(keys.size() - 1).t - keys.get(0).t);
		int i = 0;
		while (i < keys.size() - 2 && tt > keys.get(i + 1).t) i++;
		Key k1 = keys.get(i), k2 = keys.get(Math.min(i + 1, keys.size() - 1));
		Key k0 = keys.get(Math.max(i - 1, 0)), k3 = keys.get(Math.min(i + 2, keys.size() - 1));
		double span = k2.t - k1.t;
		double s = span <= 0 ? 0 : Mth.clamp((tt - k1.t) / span, 0.0, 1.0);
		Vec3 p = new Vec3(cr(k0.pos.x, k1.pos.x, k2.pos.x, k3.pos.x, s), cr(k0.pos.y, k1.pos.y, k2.pos.y, k3.pos.y, s),
				cr(k0.pos.z, k1.pos.z, k2.pos.z, k3.pos.z, s));
		double y1 = k1.yaw, y0 = unwrap(y1, k0.yaw), y2 = unwrap(y1, k2.yaw), y3 = unwrap(y2, k3.yaw);
		return new Pose(p, cr(y0, y1, y2, y3, s), cr(k0.pitch, k1.pitch, k2.pitch, k3.pitch, s), cr(k0.fov, k1.fov, k2.fov, k3.fov, s));
	}

	private static double cr(double p0, double p1, double p2, double p3, double t) {
		double t2 = t * t, t3 = t2 * t;
		return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
	}

	private static double unwrap(double ref, double a) {
		return ref + Mth.wrapDegrees(a - ref);
	}

	/** Vehicle on its route: position from the spline, heading and pitch from the path tangent. */
	private static Pose vehiclePose(Shot s, List<Key> keys, double t) {
		Pose a = sample(keys, t, s.duration);
		double dt = 0.05;
		Pose b = sample(keys, Math.min(s.duration, t + dt), s.duration);
		Pose c = t + dt > s.duration ? sample(keys, Math.max(0, t - dt), s.duration) : a;
		Vec3 d = t + dt > s.duration ? a.pos.subtract(c.pos) : b.pos.subtract(a.pos);
		double yaw = d.horizontalDistanceSqr() > 1e-8 ? Math.toDegrees(Math.atan2(-d.x, d.z)) : a.yaw;
		double pitch = d.lengthSqr() > 1e-8 ? -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.horizontalDistanceSqr()))) : 0;
		return new Pose(a.pos, yaw, pitch, a.fov);
	}

	private static Pose cameraPose(Shot s, List<Key> keys, double t, Object unused) {
		Pose k = sample(keys, t, s.duration);
		switch (s.mode) {
			case "free":
				return k;
			case "thirdperson": {
				// the player's eye (on foot) or the vehicle; vanilla third person puts the camera behind it
				Pose v = vehiclePose(s, keys, t);
				return new Pose(v.pos.add(0, s.entity == null ? 1.62 : 1.0, 0), v.yaw, k.pitch, k.fov);
			}
			case "3p-front": {
				// the actor (a mannequin of s.character) walks the key path; the camera leads it, looking back at the face
				Pose v = vehiclePose(s, keys, t);
				double yr = Math.toRadians(v.yaw);
				Vec3 fwd = new Vec3(-Math.sin(yr), 0, Math.cos(yr));
				Vec3 pos = v.pos.add(fwd.scale(s.camDist)).add(0, 1.55 + s.up - 2.5, 0);
				Vec3 d = v.pos.add(0, 1.5, 0).subtract(pos);
				double yaw = Math.toDegrees(Math.atan2(-d.x, d.z));
				double pitch = -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))) + k.pitch;
				return new Pose(pos, yaw, pitch, k.fov);
			}
			case "cockpit": {
				Pose v = vehiclePose(s, keys, t);
				Vec3 seat = rotY(s.seat, v.yaw);
				return new Pose(v.pos.add(seat), v.yaw, v.pitch + k.pitch, k.fov);
			}
			default: { // follow, boat: chase camera behind the vehicle, looking along its heading
				Pose v = vehiclePose(s, keys, t);
				double yr = Math.toRadians(v.yaw);
				Vec3 fwd = new Vec3(-Math.sin(yr), 0, Math.cos(yr));
				Vec3 pos = v.pos.subtract(fwd.scale(s.back)).add(0, s.up, 0);
				return new Pose(pos, v.yaw, k.pitch, k.fov);
			}
		}
	}

	/** Centre x of the road (ROAD/HIGHWAY cells) nearest to x along this z, within 48 blocks; x itself if none. */
	private static double roadCenterX(MapQuery q, double x, double z) {
		int zi = (int) Math.floor(z), xi = (int) Math.floor(x);
		for (int d = 0; d <= 48; d++) {
			for (int sgn : new int[] {1, -1}) {
				int cx = xi + sgn * d;
				if (!isRoad(q.surfaceAt(cx, zi))) continue;
				int a = cx, b = cx;
				while (a > cx - 40 && isRoad(q.surfaceAt(a - 1, zi))) a--;
				while (b < cx + 40 && isRoad(q.surfaceAt(b + 1, zi))) b++;
				return (a + b) / 2.0 + 0.5;
			}
		}
		return x;
	}

	private static boolean isRoad(MapQuery.Surface s) {
		return s == MapQuery.Surface.ROAD || s == MapQuery.Surface.HIGHWAY;
	}

	private static Vec3 rotY(Vec3 v, double yawDeg) {
		double r = Math.toRadians(yawDeg), c = Math.cos(r), sn = Math.sin(r);
		// entity local: +z forward, +x left (Minecraft yaw convention)
		return new Vec3(v.x * c - v.z * sn, v.y, v.x * sn + v.z * c);
	}

	// ---------------------------------------------------------------- loading

	private static void forceloadPath(TestSingleplayerContext sp, List<Key> keys, int rd) {
		double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (Key k : keys) {
			minX = Math.min(minX, k.pos.x);
			maxX = Math.max(maxX, k.pos.x);
			minZ = Math.min(minZ, k.pos.z);
			maxZ = Math.max(maxZ, k.pos.z);
		}
		int r = Math.min(rd * 16, 160);
		int x0 = ((int) minX - r) >> 4, x1 = ((int) maxX + r) >> 4, z0 = ((int) minZ - r) >> 4, z1 = ((int) maxZ + r) >> 4;
		while ((x1 - x0 + 1) * (z1 - z0 + 1) > 6400) {
			if (x1 - x0 > z1 - z0) { x0++; x1--; } else { z0++; z1--; }
		}
		// forceload accepts at most 256 chunks per command
		for (int a = x0; a <= x1; a += 16)
			for (int b = z0; b <= z1; b += 16)
				sp.getServer().runCommand(String.format(Locale.ROOT, "execute in leonida:world/leonida run forceload add %d %d %d %d", a << 4,
						b << 4, Math.min(a + 15, x1) << 4, Math.min(b + 15, z1) << 4));
	}

	/** Visit the path's key positions so the client receives every chunk along it, then wait until all sections are built. */
	private static boolean waitLoaded(ClientGameTestContext context, TestSingleplayerContext sp, Shot s, List<Key> keys) {
		long deadline = System.currentTimeMillis() + s.preloadTimeout * 1000L;
		long t0 = System.currentTimeMillis();
		List<Pose> stops = new ArrayList<>();
		for (int i = 0; i <= 4; i++) stops.add(cameraPose(s, keys, s.duration * i / 4.0, null));
		stops.add(stops.get(0));
		for (Pose p : stops) {
			sp.getServer().runCommand(String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f %.1f %.1f",
					p.pos.x, p.pos.y - 1.62, p.pos.z, p.yaw, p.pitch));
			int stable = 0;
			while (stable < 3) {
				if (System.currentTimeMillis() > deadline) return false;
				context.waitTicks(10);
				boolean ok = context.computeOnClient(mc -> {
					if (mc.level == null || mc.player == null) return false;
					int cx = mc.player.chunkPosition().x(), cz = mc.player.chunkPosition().z(), n = 0, in = 0, rd = s.rd;
					for (int a = -rd; a <= rd; a++)
						for (int b = -rd; b <= rd; b++) {
							if (a * a + b * b > rd * rd) continue;
							n++;
							if (mc.level.getChunkSource().hasChunk(cx + a, cz + b)) in++;
						}
					return in >= n * 0.98 && mc.levelRenderer.hasRenderedAllSections();
				});
				stable = ok ? stable + 1 : 0;
			}
		}
		log("shot " + s.name + ": path preloaded in " + (System.currentTimeMillis() - t0) + " ms");
		return true;
	}

	private static final List<Integer> chaserIds = new ArrayList<>();

	/** A trailing vehicle: the lead route lag seconds earlier, shifted side blocks to the right of the heading. */
	private static Pose chaserPose(Shot s, List<Key> keys, double t, JsonObject c) {
		double lag = dblOr(c, "lag", 0.6), side = dblOr(c, "side", 0), dt = 0.05;
		Pose a = sampleExt(keys, t - lag, s.duration), b = sampleExt(keys, t - lag + dt, s.duration);
		Vec3 d = b.pos.subtract(a.pos);
		double yaw = d.horizontalDistanceSqr() > 1e-8 ? Math.toDegrees(Math.atan2(-d.x, d.z)) : a.yaw;
		double yr = Math.toRadians(yaw);
		Vec3 right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
		return new Pose(a.pos.add(right.scale(side)), yaw, 0, a.fov);
	}

	// ---------------------------------------------------------------- actors (Jason / Lucia mannequins) and props
	// "3p-front" shots get an implicit actor of s.character on the key path. Extra actors: {"character", "side"
	// (blocks to the right of the main path, walks with it) or "keys" ([{t, pos | site+uv, y, yaw}], own path),
	// "board" (seconds: mount prop "prop" (index, default 0) then, which swings its front door)}.
	// Props: {"entity", "pos" [x, y above ground when relativeY, z], "yaw"}: parked vehicles, NoGravity.
	private record Actor(int id, String character, double side, List<Key> keys, double board, int prop, float[] walk) {}

	private static final List<Actor> actors = new ArrayList<>();
	private static final List<Integer> propIds = new ArrayList<>();
	private static final List<Pose> propPoses = new ArrayList<>();
	private static final java.util.Set<Integer> boarded = new java.util.HashSet<>();

	private static int spawnTagged(ClientGameTestContext context, TestSingleplayerContext sp, String cmd, String tag) {
		sp.getServer().runCommand(cmd);
		context.waitTicks(2);
		return sp.getServer().computeOnServer(server -> {
			ServerLevel lvl = server.getLevel(WorldModule.LEONIDA);
			for (Entity e : lvl.getAllEntities())
				if (e.entityTags().contains(tag)) return e.getId();
			return -1;
		});
	}

	private static void spawnActors(ClientGameTestContext context, TestSingleplayerContext sp, Shot s, List<Key> keys, MapQuery q) {
		actors.clear();
		propIds.clear();
		propPoses.clear();
		boarded.clear();
		for (int i = 0; i < s.props.size(); i++) {
			JsonObject p = s.props.get(i).getAsJsonObject();
			Vec3 v = vec(p, "pos");
			double yaw = dblOr(p, "yaw", 0);
			if (p.has("site")) {
				// site-relative: uv on the landmark, heading along +u, turned so the driver door faces +v (the buildings)
				dev.leonida.vicecity.gen.Site site = dev.leonida.vicecity.ViceLandmarks.INSTANCE.site(p.get("site").getAsString());
				JsonArray uv = p.getAsJsonArray("uv");
				if (site != null) {
					double[] w = site.toWorld(uv.get(0).getAsDouble(), uv.get(1).getAsDouble());
					double[] w1 = site.toWorld(uv.get(0).getAsDouble() + 1, uv.get(1).getAsDouble());
					double[] wv = site.toWorld(uv.get(0).getAsDouble(), uv.get(1).getAsDouble() + 1);
					yaw = Math.toDegrees(Math.atan2(-(w1[0] - w[0]), w1[1] - w[1]));
					double r = Math.toRadians(yaw);
					if (Math.cos(r) * (wv[0] - w[0]) + Math.sin(r) * (wv[1] - w[1]) < 0) yaw += 180;
					v = new Vec3(w[0], dblOr(p, "y", 0), w[1]);
				} else log("unknown site " + p.get("site").getAsString());
			}
			double y = s.relativeY ? q.groundY((int) Math.floor(v.x), (int) Math.floor(v.z)) + 1 + v.y : v.y;
			String tag = "leonida_cine_prop" + i;
			int id = spawnTagged(context, sp, String.format(Locale.ROOT,
					"execute in leonida:world/leonida run summon %s %.3f %.3f %.3f {NoGravity:1b,Invulnerable:1b,Tags:[\"leonida_cine\",\"%s\"],Rotation:[%.1ff,0f]}",
					p.get("entity").getAsString(), v.x, y, v.z, yaw, tag), tag);
			propIds.add(id);
			propPoses.add(new Pose(new Vec3(v.x, y, v.z), yaw, 0, 70));
			log("shot " + s.name + ": prop " + p.get("entity").getAsString() + " id " + id);
		}
		JsonArray list = new JsonArray();
		if (s.mode.equals("3p-front")) {
			JsonObject main = new JsonObject();
			main.addProperty("character", s.character);
			list.add(main);
		}
		list.addAll(s.actors);
		for (int i = 0; i < list.size(); i++) {
			JsonObject a = list.get(i).getAsJsonObject();
			String ch = strOr(a, "character", "lucia");
			List<Key> ak = null;
			int pi = intOr(a, "prop", 0);
			if (!a.has("keys") && a.has("board") && pi < propPoses.size()) {
				// walk up to the prop's driver door (left side) and arrive at the board time
				Pose pp = propPoses.get(pi);
				double r = Math.toRadians(pp.yaw);
				Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r)), left = new Vec3(Math.cos(r), 0, Math.sin(r));
				Vec3 door = pp.pos.add(left.scale(1.5)).add(fwd.scale(0.3));
				Vec3 from = door.add(fwd.scale(dblOr(a, "approach", 3.5))).add(left.scale(0.6));
				double tb = dblOr(a, "board", 1.5);
				ak = new ArrayList<>();
				ak.add(new Key(0, from, 0, 0, 70, false));
				ak.add(new Key(tb, door, 0, 0, 70, false));
				ak.add(new Key(s.duration, door, 0, 0, 70, false));
			} else if (a.has("keys")) {
				ak = new ArrayList<>();
				for (JsonElement e : a.getAsJsonArray("keys")) {
					JsonObject k = e.getAsJsonObject();
					double x, z;
					if (k.has("site")) {
						dev.leonida.vicecity.gen.Site site = dev.leonida.vicecity.ViceLandmarks.INSTANCE.site(k.get("site").getAsString());
						JsonArray uv = k.getAsJsonArray("uv");
						double[] w = site != null ? site.toWorld(uv.get(0).getAsDouble(), uv.get(1).getAsDouble()) : new double[] {0, 0};
						x = w[0];
						z = w[1];
					} else {
						JsonArray pa = k.getAsJsonArray("pos");
						x = pa.get(0).getAsDouble();
						z = pa.get(2).getAsDouble();
					}
					double y0 = k.has("pos") ? k.getAsJsonArray("pos").get(1).getAsDouble() : dblOr(k, "y", 0);
					double y = s.relativeY ? q.groundY((int) Math.floor(x), (int) Math.floor(z)) + 1 + y0 : y0;
					ak.add(new Key(k.get("t").getAsDouble(), new Vec3(x, y, z), dblOr(k, "yaw", 0), 0, 70, k.has("yaw")));
				}
			}
			Pose p0 = ak != null ? vehiclePose(s, ak, 0) : vehiclePose(s, keys, 0);
			String tag = "leonida_cine_actor" + i;
			String slim = ch.equals("lucia") ? ",model:\"slim\"" : "";
			int id = spawnTagged(context, sp, String.format(Locale.ROOT,
					"execute in leonida:world/leonida run summon mannequin %.3f %.3f %.3f {NoGravity:1b,Invulnerable:1b,immovable:true,hide_description:true,"
							+ "Tags:[\"leonida_cine\",\"%s\"],Rotation:[%.1ff,0f],profile:{texture:\"leonida:entity/character/%s/everyday\"%s}}",
					p0.pos.x, p0.pos.y, p0.pos.z, p0.yaw, tag, ch, slim), tag);
			actors.add(new Actor(id, ch, dblOr(a, "side", 0), ak, dblOr(a, "board", -1), intOr(a, "prop", 0), new float[2]));
			log("shot " + s.name + ": actor " + ch + " id " + id);
		}
	}

	private static Pose actorPath(Shot s, List<Key> keys, Actor a, double t) {
		Pose v = vehiclePose(s, a.keys != null ? a.keys : keys, t);
		if (a.keys != null && a.keys.get(0).hasYaw && v.pos.distanceTo(vehiclePose(s, a.keys, Math.min(s.duration, t + 0.1)).pos) < 1e-3)
			v = new Pose(v.pos, sample(a.keys, t, s.duration).yaw, 0, 70); // standing: key yaw
		if (a.side != 0) {
			double yr = Math.toRadians(v.yaw);
			v = new Pose(v.pos.add(-Math.cos(yr) * a.side, 0, -Math.sin(yr) * a.side), v.yaw, 0, 70);
		}
		return v;
	}

	/** Actor poses at time t (null for a boarded actor); boards actors whose time has come (server ride). */
	private static List<Pose> actorPoses(TestSingleplayerContext sp, Shot s, List<Key> keys, double t, int fps) {
		List<Pose> out = new ArrayList<>();
		for (int i = 0; i < actors.size(); i++) {
			Actor a = actors.get(i);
			if (a.board >= 0 && t >= a.board && !boarded.contains(i) && a.prop < propIds.size()) {
				sp.getServer().runCommand("ride @e[tag=leonida_cine_actor" + i + ",limit=1] mount @e[tag=leonida_cine_prop" + a.prop + ",limit=1]");
				boarded.add(i);
				log("shot " + s.name + ": " + a.character + " boards prop " + a.prop + " at " + String.format(Locale.ROOT, "%.2f", t) + " s");
			}
			if (boarded.contains(i)) {
				out.add(null);
				continue;
			}
			Pose p = actorPath(s, keys, a, t), n = actorPath(s, keys, a, Math.min(s.duration, t + 1.0 / fps));
			double perTick = p.pos.subtract(n.pos).horizontalDistance() * fps / 20.0;
			a.walk[0] = (float) Math.min(perTick * 4.0, 1.0);
			a.walk[1] += a.walk[0] * (20.0f / fps);
			out.add(p);
		}
		return out;
	}

	private static void placeActors(Minecraft mc, List<Pose> aps) {
		if (mc.level == null) return;
		for (int i = 0; i < aps.size() && i < actors.size(); i++) {
			Pose p = aps.get(i);
			Entity e = mc.level.getEntity(actors.get(i).id);
			if (p == null || e == null) continue;
			e.setPos(p.pos);
			e.xo = e.xOld = p.pos.x;
			e.yo = e.yOld = p.pos.y;
			e.zo = e.zOld = p.pos.z;
			e.setYRot((float) p.yaw);
			e.yRotO = (float) p.yaw;
			if (e instanceof net.minecraft.world.entity.LivingEntity le) {
				le.yBodyRot = le.yBodyRotO = (float) p.yaw;
				le.setYHeadRot((float) p.yaw);
				le.yHeadRotO = (float) p.yaw;
				setWalk(le.walkAnimation, actors.get(i).walk[0], actors.get(i).walk[1]);
			}
		}
	}

	private static void placeChasers(Minecraft mc, List<Pose> cps) {
		if (mc.level == null) return;
		for (int i = 0; i < cps.size() && i < chaserIds.size(); i++) {
			Entity e = mc.level.getEntity(chaserIds.get(i));
			if (e == null) continue;
			Pose v = cps.get(i);
			e.setPos(v.pos);
			e.xo = e.xOld = v.pos.x;
			e.yo = e.yOld = v.pos.y;
			e.zo = e.zOld = v.pos.z;
			e.setYRot((float) v.yaw);
			e.yRotO = (float) v.yaw;
			for (Entity r : e.getPassengers()) {
				e.positionRider(r);
				r.xo = r.xOld = r.getX();
				r.yo = r.yOld = r.getY();
				r.zo = r.zOld = r.getZ();
			}
		}
	}

	private static boolean thirdPerson, riding;
	private static float walkA, walkPhase;

	/** Exact limb swing for this frame, independent of partial tick: speed = amplitude, position(partial) = phase. */
	private static void setWalk(net.minecraft.world.entity.WalkAnimationState w, float amp, float phase) {
		try {
			Class<?> c = net.minecraft.world.entity.WalkAnimationState.class;
			for (String n : new String[] {"speedOld", "speed"}) {
				java.lang.reflect.Field fl = c.getDeclaredField(n);
				fl.setAccessible(true);
				fl.setFloat(w, amp);
			}
			java.lang.reflect.Field pos = c.getDeclaredField("position"), sc = c.getDeclaredField("positionScale");
			pos.setAccessible(true);
			sc.setAccessible(true);
			// position(partial) = (position - speed * (1 - partial)) * scale; the frame partial is applied by the screenshot, so
			// bake the phase at partial 1 and compensate below via the render partial being part of the phase already
			pos.setFloat(w, phase + amp * (1f - lastPartial));
			sc.setFloat(w, 1f);
		} catch (ReflectiveOperationException e) {
			w.setSpeed(amp);
		}
	}

	private static float lastPartial;

	/** Sodium's "every visible section is built and uploaded" flag; vanilla equivalent without Sodium. */
	private static boolean terrainComplete(Minecraft mc) {
		try {
			Class<?> c = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
			Object r = c.getMethod("instanceNullable").invoke(null);
			if (r != null) return (Boolean) c.getMethod("isTerrainRenderComplete").invoke(r);
		} catch (ClassNotFoundException e) {
			// no Sodium
		} catch (ReflectiveOperationException e) {
			// fall through
		}
		return mc.levelRenderer.hasRenderedAllSections();
	}

	/**
	 * Render (without saving) until the terrain is complete for the current camera. At least one frame is rendered so the
	 * renderer sees the new camera. Returns the extra frames rendered, or -1 when the cap was hit.
	 */
	private static int gate(ClientGameTestContext context, long capMs) {
		long end = System.currentTimeMillis() + capMs;
		int n = 0;
		do {
			context.waitTick();
			n++;
			if (context.computeOnClient(CineCapture::terrainComplete)) return n;
		} while (System.currentTimeMillis() < end);
		return -1;
	}

	/** Warm-up pass: fly the whole camera path (no frames saved) so every section the shot sees is generated, sent and meshed. */
	private static void warmUp(ClientGameTestContext context, TestSingleplayerContext sp, Shot s, List<Key> keys) {
		long t0 = System.currentTimeMillis();
		int steps = Math.max(8, (int) Math.ceil(s.duration * 4)), capped = 0;
		for (int pass = 0; pass < 2; pass++) {
			capped = 0;
			for (int i = 0; i <= steps; i++) {
				Pose p = cameraPose(s, keys, s.duration * i / steps, null);
				sp.getServer().runCommand(String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f %.1f %.1f",
						p.pos.x, p.pos.y - 1.62, p.pos.z, p.yaw, p.pitch));
				context.waitTicks(2);
				context.runOnClient(mc -> place(mc, p, null, -1));
				if (gate(context, pass == 0 ? 8000 : 3000) < 0) capped++;
			}
			if (capped == 0) break;
		}
		Pose p = cameraPose(s, keys, 0, null);
		sp.getServer().runCommand(String.format(Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f %.1f %.1f", p.pos.x,
				p.pos.y - 1.62, p.pos.z, p.yaw, p.pitch));
		context.runOnClient(mc -> place(mc, p, null, -1));
		gate(context, 8000);
		log("shot " + s.name + ": warm-up flight over " + (steps + 1) + " path points in " + (System.currentTimeMillis() - t0) + " ms, "
				+ capped + " point(s) still building at the cap");
	}

	/** Pop-in check: mean luminance difference between consecutive frames (every 4th pixel); spikes over the median are logged. */
	private static void popCheck(Shot s, Path dir, int frames) {
		try {
			double[] d = new double[frames];
			int[] prev = null;
			for (int f = 0; f < frames; f++) {
				java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(dir.resolve(String.format(Locale.ROOT, "frame_%05d.png", f)).toFile());
				int w = img.getWidth() / 4, h = img.getHeight() / 4;
				int[] cur = new int[w * h];
				for (int y = 0; y < h; y++)
					for (int x = 0; x < w; x++) {
						int c = img.getRGB(x * 4, y * 4);
						cur[y * w + x] = (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10;
					}
				if (prev != null && prev.length == cur.length) {
					long sum = 0;
					for (int i = 0; i < cur.length; i++) sum += Math.abs(cur[i] - prev[i]);
					d[f] = sum / (double) cur.length;
				}
				prev = cur;
			}
			double[] sorted = java.util.Arrays.copyOfRange(d, 1, frames);
			java.util.Arrays.sort(sorted);
			double med = sorted.length == 0 ? 0 : sorted[sorted.length / 2], max = sorted.length == 0 ? 0 : sorted[sorted.length - 1];
			StringBuilder spikes = new StringBuilder();
			for (int f = 2; f < frames - 1; f++) {
				// a spike: much larger than the median and than both neighbours (camera motion changes slowly)
				double n = Math.max(d[f - 1], d[f + 1]);
				if (d[f] > Math.max(2.5 * med, med + 1.5) && d[f] > 1.6 * n) spikes.append(' ').append(f).append(String.format(Locale.ROOT, "(%.2f)", d[f]));
			}
			log(String.format(Locale.ROOT, "shot %s: frame diff median %.2f max %.2f, pop-in spikes:%s", s.name, med, max,
					spikes.length() == 0 ? " none" : spikes.toString()));
		} catch (IOException | RuntimeException e) {
			log("shot " + s.name + ": pop check failed: " + e);
		}
	}

	// ---------------------------------------------------------------- helpers

	/** Leonida Shaders HIGH profile (same values as profile.HIGH in the pack's shaders.properties), then reload. */
	private static void irisHigh(ClientGameTestContext context) {
		java.util.Map<String, String> o = new java.util.HashMap<>();
		o.put("shadowMapResolution", "2048");
		o.put("shadowDistance", "128.0");
		o.put("SHADOW_SAMPLES", "12");
		o.put("SSR", "true");
		o.put("SSR_STEPS", "20");
		o.put("CLOUD_OCTAVES", "4");
		o.put("COLORED_SHADOWS", "true");
		o.put("WAVING_PLANTS", "true");
		o.put("CLOUDS", "true");
		o.put("BLOOM", "true");
		String err = context.computeOnClient(mc -> {
			try {
				Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
				// select and enable the bundled pack (the installer skips selection when iris.properties already exists)
				Object cfg = iris.getMethod("getIrisConfig").invoke(null);
				cfg.getClass().getMethod("setShaderPackName", String.class).invoke(cfg, "Leonida Shaders");
				cfg.getClass().getMethod("setShadersEnabled", boolean.class).invoke(cfg, true);
				cfg.getClass().getMethod("save").invoke(cfg);
				@SuppressWarnings("unchecked")
				java.util.Map<String, String> queue = (java.util.Map<String, String>) iris.getMethod("getShaderPackOptionQueue").invoke(null);
				queue.putAll(o);
				iris.getMethod("reload").invoke(null);
				return "ok";
			} catch (Throwable e) {
				return e.toString();
			}
		});
		log("iris profile HIGH: " + err);
		context.waitTicks(60);
	}

	/** Distant Horizons LOD rendering for the capture (players start with it OFF; the video wants it ON). Not saved. */
	private static void distantHorizons(boolean on) {
		if (!FabricLoader.getInstance().isModLoaded("distanthorizons")) return;
		try {
			Object e = Class.forName("com.seibel.distanthorizons.core.config.Config$Client").getField("quickEnableRendering").get(null);
			e.getClass().getMethod("set", Object.class).invoke(e, Boolean.valueOf(on));
			log("distant horizons rendering " + (on ? "ON" : "OFF"));
		} catch (Throwable t) {
			log("could not set distant horizons rendering: " + t);
		}
	}

	private static String describeShaders() {
		StringBuilder b = new StringBuilder("window " + Minecraft.getInstance().getWindow().getWidth() + "x" + Minecraft.getInstance().getWindow().getHeight());
		try {
			Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object inst = api.getMethod("getInstance").invoke(null);
			b.append(", iris pack in use: ").append(api.getMethod("isShaderPackInUse").invoke(inst));
		} catch (Throwable t) {
			b.append(", iris not loaded");
		}
		b.append(", distant horizons: ").append(FabricLoader.getInstance().isModLoaded("distanthorizons"));
		try {
			b.append(" ").append(dev.leonida.shader.DhDefaults.describe());
		} catch (Throwable t) {
			// shader module helper unavailable
		}
		return b.toString();
	}

	private static Shot parse(JsonObject o) {
		List<Key> keys = new ArrayList<>();
		if (o.has("orbit")) {
			// slow aerial orbit around a landmark: keys on a circle in site space, all aimed at the centre
			JsonObject ob = o.getAsJsonObject("orbit");
			JsonArray gen = new JsonArray();
			int n = 6;
			double dur = dblOr(o, "duration", 5), r = dblOr(ob, "radius", 120), h = dblOr(ob, "height", 60), lh = dblOr(ob, "lookHeight", 15);
			double a0 = dblOr(ob, "fromDeg", 0), a1 = dblOr(ob, "toDeg", 40);
			for (int i = 0; i <= n; i++) {
				double a = Math.toRadians(a0 + (a1 - a0) * i / n);
				JsonObject k = new JsonObject();
				k.addProperty("t", dur * i / n);
				k.addProperty("site", ob.get("site").getAsString());
				JsonArray uv = new JsonArray();
				uv.add(r * Math.sin(a));
				uv.add(-r * Math.cos(a));
				k.add("uv", uv);
				k.addProperty("y", h);
				JsonArray l = new JsonArray();
				l.add(0);
				l.add(lh);
				l.add(0);
				k.add("lookUv", l);
				k.addProperty("fov", dblOr(ob, "fov", 60));
				gen.add(k);
			}
			o.add("keys", gen);
		}
		for (JsonElement e : o.getAsJsonArray("keys")) {
			JsonObject k = e.getAsJsonObject();
			Vec3 pos;
			Vec3 look = null;
			boolean absY = false, lookAbsY = false;
			double fov = dblOr(k, "fov", 70);
			if (k.has("view")) {
				// named camera preset of a landmark: "vc:<id>:<view>" (vicecity sites) or "lm:<id>:<view>" (landmark module)
				String[] v = k.get("view").getAsString().split(":");
				double[] cam = viewCamera(v[0], v[1], v[2]);
				if (cam == null) {
					log("unknown view " + k.get("view").getAsString());
					cam = new double[] {0, 100, 0, 0, 100, 10};
				}
				Vec3 off = vec(k, "offset"), loff = vec(k, "lookOffset");
				pos = new Vec3(cam[0], cam[1], cam[2]).add(off);
				look = new Vec3(cam[3], cam[4], cam[5]).add(loff);
				absY = lookAbsY = true;
			} else if (k.has("site")) {
				// landmark-relative: model u (along the site), v (out of its front), y; e.g. Ocean Drive hotels "L42"
				dev.leonida.vicecity.gen.Site site = dev.leonida.vicecity.ViceLandmarks.INSTANCE.site(k.get("site").getAsString());
				JsonArray uv = k.getAsJsonArray("uv");
				double[] w = site != null ? site.toWorld(uv.get(0).getAsDouble(), uv.get(1).getAsDouble()) : new double[] {0, 0};
				if (site == null) log("unknown site " + k.get("site").getAsString());
				pos = new Vec3(w[0], dblOr(k, "y", 0), w[1]);
				if (k.has("lookUv") && site != null) {
					JsonArray l = k.getAsJsonArray("lookUv"); // [u, h above ground, v]
					double[] lw = site.toWorld(l.get(0).getAsDouble(), l.get(2).getAsDouble());
					look = new Vec3(lw[0], l.get(1).getAsDouble(), lw[1]);
				}
			} else {
				JsonArray p = k.getAsJsonArray("pos");
				pos = new Vec3(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble());
			}
			if (k.has("look")) {
				JsonArray l = k.getAsJsonArray("look"); // world x, y (above ground when relativeY), z
				look = new Vec3(l.get(0).getAsDouble(), l.get(1).getAsDouble(), l.get(2).getAsDouble());
				lookAbsY = false;
			}
			if (k.has("absY")) absY = k.get("absY").getAsBoolean();
			keys.add(new Key(k.get("t").getAsDouble(), pos, dblOr(k, "yaw", 0), dblOr(k, "pitch", 0), fov, k.has("yaw"), absY, look, lookAbsY));
		}
		JsonObject follow = o.has("follow") ? o.getAsJsonObject("follow") : new JsonObject();
		Vec3 seat = new Vec3(0, 1.4, 0.3);
		if (o.has("seat")) {
			JsonArray a = o.getAsJsonArray("seat");
			seat = new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
		}
		return new Shot(o.get("name").getAsString(), strOr(o, "mode", "free"), (long) dblOr(o, "time", 6000), strOr(o, "weather", "clear"),
				dblOr(o, "duration", 5), (cfg("rd") != null ? Integer.parseInt(cfg("rd")) : intOr(o, "renderDistance", 32)), o.has("relativeY") && o.get("relativeY").getAsBoolean(),
				intOr(o, "lodWaitSeconds", 20), intOr(o, "preloadTimeoutSeconds", 600), o.has("snapToRoad") && o.get("snapToRoad").getAsBoolean(),
				dblOr(o, "brightness", -1), dblOr(o, "exposure", 0), o.has("entity") ? o.get("entity").getAsString() : null,
				o.has("driver") ? o.get("driver").getAsString() : null, dblOr(follow, "back", 7), dblOr(follow, "up", 2.5), seat, keys,
				strOr(o, "character", "jason"), dblOr(o, "cameraDistance", o.has("entity") ? 6.0 : 3.5), !o.has("ease") || o.get("ease").getAsBoolean(),
				o.has("chasers") ? o.getAsJsonArray("chasers") : new JsonArray(), o.has("fire") ? o.getAsJsonObject("fire") : null,
				strOr(o, "outName", o.get("name").getAsString()), o.has("actors") ? o.getAsJsonArray("actors") : new JsonArray(),
				o.has("props") ? o.getAsJsonArray("props") : new JsonArray());
	}

	private static Vec3 vec(JsonObject o, String k) {
		if (!o.has(k)) return Vec3.ZERO;
		JsonArray a = o.getAsJsonArray(k);
		return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
	}

	/** World camera {x, y, z, tx, ty, tz} of a landmark camera preset; ground from the map query when the site has not generated yet. */
	private static double[] viewCamera(String kind, String id, String name) {
		MapQuery q = MapQuery.get();
		if (kind.equals("vc")) {
			dev.leonida.vicecity.gen.Site s = dev.leonida.vicecity.ViceLandmarks.INSTANCE.site(id);
			if (s == null) return null;
			for (dev.leonida.vicecity.gen.Site.View v : s.views()) {
				if (!v.name().equals(name)) continue;
				double[] o = s.toWorld(0, 0);
				int datum = s.lastDatum != Integer.MIN_VALUE ? s.lastDatum : q.groundY((int) Math.floor(o[0]), (int) Math.floor(o[1])) + 1;
				double[] p = s.toWorld(v.u(), v.v()), t = s.toWorld(v.tu(), v.tv());
				return new double[] {p[0], datum + v.h(), p[1], t[0], datum + v.th(), t[1]};
			}
		} else {
			dev.leonida.landmark.gen.Site s = dev.leonida.landmark.LandmarkGen.byId(id);
			if (s == null) return null;
			for (dev.leonida.landmark.gen.Site.View v : s.views()) {
				if (!v.name().equals(name)) continue;
				int ox = s.originX(), oz = s.originZ();
				int g = Math.max(q.groundY(ox, oz) + 1, 64);
				double x = ox + v.x(), y = g + v.y(), z = oz + v.z();
				double yaw = Math.toRadians(v.yaw()), pitch = Math.toRadians(v.pitch());
				double d = 20;
				return new double[] {x, y, z, x - Math.sin(yaw) * Math.cos(pitch) * d, y - Math.sin(pitch) * d, z + Math.cos(yaw) * Math.cos(pitch) * d};
			}
		}
		return null;
	}

	private static int intOr(JsonObject o, String k, int d) {
		return o.has(k) ? o.get(k).getAsInt() : d;
	}

	private static double dblOr(JsonObject o, String k, double d) {
		return o.has(k) ? o.get(k).getAsDouble() : d;
	}

	private static String strOr(JsonObject o, String k, String d) {
		return o.has(k) ? o.get(k).getAsString() : d;
	}

	private static String tool(String name) {
		// leonida.cine.ffmpeg / LEONIDA_CINE_FFMPEG: full path to ffmpeg(.exe); ffprobe is looked up next to it
		String ff = cfg("ffmpeg");
		if (ff != null) {
			Path fp = Path.of(ff);
			if (name.equals("ffmpeg")) return fp.toString();
			Path dir = fp.toAbsolutePath().getParent();
			String ext = fp.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe") ? ".exe" : "";
			if (dir != null && Files.exists(dir.resolve(name + ext))) return dir.resolve(name + ext).toString();
		}
		Path p = Path.of("/opt/ffmpeg/bin", name);
		return Files.exists(p) ? p.toString() : name;
	}

	private static int exec(List<String> cmd) throws IOException, InterruptedException {
		Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		String outText = new String(p.getInputStream().readAllBytes());
		if (!p.waitFor(20, TimeUnit.MINUTES)) p.destroyForcibly();
		for (String line : outText.split("\n"))
			if (!line.isBlank()) log("  " + line);
		return p.exitValue();
	}

	private static void deleteDir(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		try (var walk = Files.walk(dir)) {
			walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
		}
	}

	private static void log(String msg) {
		System.out.println("[leonida-test] fx cine " + msg);
	}
}
