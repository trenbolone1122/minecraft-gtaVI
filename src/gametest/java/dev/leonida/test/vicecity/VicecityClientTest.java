package dev.leonida.test.vicecity;

import dev.leonida.test.TestFilter;
import dev.leonida.vicecity.ViceLandmarks;
import dev.leonida.vicecity.gen.Site;
import dev.leonida.world.WorldModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;

/**
 * Client game tests for the vicecity module. Owned by the vicecity agent. Run: tools/gpu-test.sh vicecity
 * Creates the real Leonida world, lets world generation build each landmark at its GTADB map position, and shoots the
 * site's camera presets (day, plus one night shot). Every wait is capped at 60 s per shot.
 */
public class VicecityClientTest implements FabricClientGameTest {
	/** Sites shot by default (comma ids). */
	static final String SITES = "L196,L438"; // full set: L187,L196,L232,L36,L379,L438,L437,L37
	/**
	 * Sites whose camera is chosen from the real world: {target h above datum, camera h above ground, minimum distance
	 * from the site origin, 1 = camera must stand on dry land}. The camera stays outside every claim, the line of sight
	 * to the target must be free of occluding blocks until it enters the site's own claim, and it looks at the origin.
	 */
	static final java.util.Map<String, double[]> AUTO = java.util.Map.of(
			"L196", new double[] {18, 40, 170, 0}, // Fleeca Field from about 170 m, 40 m up
			"L438", new double[] {30, 1.8, 45, 1}, // Skyviews wheel from the nearest dry promenade spot, looking up
			"L437", new double[] {21, 8, 48, 0}); // Palaestra Tower from the street
	static final String DIM = "execute in leonida:world/leonida run ";

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("vicecity")) return;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			context.getInput().pressKey(o -> o.keyToggleGui);
			context.runOnClient(mc -> mc.options.renderDistance().set(16));
			cmd(sp, "gamemode spectator @a");
			cmd(sp, "time set noon");
			cmd(sp, "gamerule advance_time false");
			cmd(sp, "weather clear");
			for (String id : SITES.split(",")) {
				Site s = ViceLandmarks.INSTANCE.site(id);
				if (s == null) {
					log("no site " + id);
					continue;
				}
				// forceload the claim so world generation builds the whole landmark
				int x0 = s.minX, z0 = s.minZ, x1 = s.maxX, z1 = s.maxZ;
				cmd(sp, DIM + "forceload add " + x0 + " " + z0 + " " + x1 + " " + z1);
				double[] c = s.toWorld(0, 0);
				cmd(sp, DIM + "tp @a " + (int) c[0] + " 160 " + (int) c[1]);
				int cx0 = x0 >> 4, cx1 = x1 >> 4, cz0 = z0 >> 4, cz1 = z1 >> 4, total = (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
				int full = 0;
				for (int i = 0; i < 120 && full < total; i++) {
					context.waitTicks(10);
					full = sp.getServer().computeOnServer(srv -> {
						ServerLevel lvl = srv.getLevel(WorldModule.LEONIDA);
						int n = 0;
						for (int a = cx0; a <= cx1; a++)
							for (int b = cz0; b <= cz1; b++)
								if (lvl.getChunkSource().getChunkNow(a, b) != null) n++;
						return n;
					});
				}
				int datum = s.lastDatum;
				log(id + " " + s.name + ": " + full + "/" + total + " chunks, datum " + datum + ", origin " + (int) c[0] + "," + (int) c[1]);
				if (datum == Integer.MIN_VALUE) datum = 64;
				// one day shot (photo view) and one night shot (night view, or photo at night)
				Site.View day = null, nite = null;
				for (Site.View v : s.views()) {
					if (v.name().equals("photo")) day = v;
					if (v.name().equals("night")) nite = v;
				}
				if (day == null) day = s.views().get(0);
				if (nite == null) nite = day;
				double[] a = AUTO.get(id);
				if (a != null) {
					final int dat = datum;
					double[] cam = sp.getServer().computeOnServer(srv -> autoCam(srv.getLevel(WorldModule.LEONIDA), s, dat, a));
					if (cam == null) {
						log(id + " autocam: no clear camera found, using preset");
					} else {
						log(String.format(java.util.Locale.ROOT, "%s autocam: cam %.1f %.1f %.1f -> %.1f %.1f %.1f, ground %d, dist %.0f", id,
								cam[0], cam[1], cam[2], cam[3], cam[4], cam[5], (int) cam[6], cam[7]));
						shootAt(context, sp, s, cam, "vc-" + id + "-day");
						cmd(sp, "time set 18500");
						shootAt(context, sp, s, cam, "vc-" + id + "-night");
						cmd(sp, "time set noon");
						cmd(sp, DIM + "forceload remove all");
						continue;
					}
				}
				shoot(context, sp, s, day, datum, "vc-" + id + "-day");
				cmd(sp, "time set 18500");
				shoot(context, sp, s, nite, datum, "vc-" + id + "-night");
				cmd(sp, "time set noon");
				cmd(sp, DIM + "forceload remove all");
			}
		}
	}

	static void shoot(ClientGameTestContext context, TestSingleplayerContext sp, Site s, Site.View v, int datum, String name) {
		double[] p = s.toWorld(v.u(), v.v()), t = s.toWorld(v.tu(), v.tv());
		cmd(sp, DIM + "tp @a " + fmt(p[0]) + " " + fmt(datum + v.h()) + " " + fmt(p[1]) + " facing " + fmt(t[0]) + " " + fmt(datum + v.th()) + " " + fmt(t[1]));
		waitRendered(context, 60);
		context.takeScreenshot(name);
	}

	static void shootAt(ClientGameTestContext context, TestSingleplayerContext sp, Site s, double[] c, String name) {
		cmd(sp, DIM + "tp @a " + fmt(c[0]) + " " + fmt(c[1]) + " " + fmt(c[2]) + " facing " + fmt(c[3]) + " " + fmt(c[4]) + " " + fmt(c[5]));
		waitClientChunks(context, s, c[0], c[2], 60, name);
		waitRendered(context, 60);
		context.takeScreenshot(name);
	}

	/**
	 * hasRenderedAllSections only covers chunks the client already received; after a long teleport the server is still
	 * streaming far chunks, so wait until the client holds every claim chunk within its view distance.
	 */
	static void waitClientChunks(ClientGameTestContext context, Site s, double cx, double cz, int maxSeconds, String name) {
		int r = 15, px = (int) Math.floor(cx) >> 4, pz = (int) Math.floor(cz) >> 4;
		int[] res = {0, 0};
		for (int i = 0; i < maxSeconds * 2; i++) {
			res = context.computeOnClient(mc -> {
				int have = 0, want = 0;
				for (int a = s.minX >> 4; a <= s.maxX >> 4; a++)
					for (int b = s.minZ >> 4; b <= s.maxZ >> 4; b++) {
						if (Math.max(Math.abs(a - px), Math.abs(b - pz)) > r) continue;
						want++;
						if (mc.level != null && mc.level.hasChunk(a, b)) have++;
					}
				return new int[] {have, want};
			});
			if (i == 0) log(name + " client chunks at teleport: " + res[0] + "/" + res[1]);
			if (res[0] >= res[1]) break;
			context.waitTicks(10);
		}
		log(name + " client chunks before shot: " + res[0] + "/" + res[1]);
	}

	static boolean anyClaim(int x, int z) {
		for (Site o : ViceLandmarks.INSTANCE.sites().values()) if (o.claims(x, z)) return true;
		for (var o : dev.leonida.landmark.LandmarkGen.SITES) if (o.claims(x, z)) return true;
		return false;
	}

	/** Returns {cam x,y,z, target x,y,z, ground y, distance} or null. Runs on the server thread. */
	static double[] autoCam(ServerLevel lvl, Site s, int datum, double[] a) {
		double[] o = s.toWorld(0, 0), f = s.toWorld(0, -1);
		double base = Math.atan2(f[1] - o[1], f[0] - o[0]); // the site's front (photo side, -v)
		double tx = o[0], ty = datum + a[0], tz = o[1];
		double[] best = null;
		for (int k = 0; k < 24; k++) {
			// try the front first, then alternate left and right of it
			double ang = base + ((k + 1) / 2) * (Math.PI / 12) * (k % 2 == 0 ? 1 : -1);
			double dx = Math.cos(ang), dz = Math.sin(ang);
			for (double d = 8; d <= 260; d += 4) {
				double x = o[0] + dx * d, z = o[1] + dz * d;
				int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
				if (d < a[2] || anyClaim(bx, bz)) continue;
				int g = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
				if (a[3] > 0 && !lvl.getFluidState(new net.minecraft.core.BlockPos(bx, g - 1, bz)).isEmpty()) continue;
				double y = g + a[1];
				if (!clear(lvl, s, x, y, z, tx, ty, tz)) continue;
				double[] c = {x, y, z, tx, ty, tz, g, d};
				if (a[3] == 0) return c; // first clear heading from the front
				if (best == null || d < best[7]) best = c; // dry-land sites: the nearest clear spot over all headings
				break;
			}
		}
		return best;
	}

	/** True when nothing occluding lies on the line from the camera until it enters the site's own claim. */
	static boolean clear(ServerLevel lvl, Site s, double x0, double y0, double z0, double x1, double y1, double z1) {
		double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
		var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		for (double t = 0; t < len; t += 0.5) {
			double x = x0 + (x1 - x0) * t / len, y = y0 + (y1 - y0) * t / len, z = z0 + (z1 - z0) * t / len;
			int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
			if (s.claims(bx, bz)) return true;
			pos.set(bx, (int) Math.floor(y), bz);
			if (lvl.getBlockState(pos).canOcclude()) return false;
		}
		return true;
	}

	static void waitRendered(ClientGameTestContext context, int maxSeconds) {
		int stable = 0;
		for (int i = 0; i < maxSeconds * 2 && stable < 3; i++) {
			context.waitTicks(10);
			boolean ok = context.computeOnClient(mc -> mc.level != null && mc.levelRenderer.hasRenderedAllSections());
			stable = ok ? stable + 1 : 0;
		}
		context.waitTicks(20);
	}

	static String fmt(double d) {
		return String.format(java.util.Locale.ROOT, "%.1f", d);
	}

	static void log(String s) {
		System.out.println("[leonida-test] vicecity " + s);
	}

	static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}
}
