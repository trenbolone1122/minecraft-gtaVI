package dev.leonida.test.road;

import dev.leonida.core.api.RoadGenerator;
import dev.leonida.road.net.Props;
import dev.leonida.road.net.RoadNetwork;
import dev.leonida.road.net.Segment;
import dev.leonida.test.TestFilter;
import dev.leonida.world.WorldModule;
import net.minecraft.server.level.ServerLevel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Client game tests for the road module. Owned by the road agent. Run: tools/pod-test.sh road */
public class RoadClientTest implements FabricClientGameTest {
	private static void log(String s) {
		System.out.println("[leonida-test] road: " + s);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("road")) return;
		long t0 = System.currentTimeMillis();
		RoadNetwork net = RoadNetwork.get();
		log("network " + net.segments.size() + " segments " + net.nodes.size() + " nodes, " + (System.currentTimeMillis() - t0) + " ms");
		if (net.segments.size() < 2000) throw new AssertionError("road network too small");
		RoadGenerator g = RoadGenerator.get();
		if (g == RoadGenerator.NONE) throw new AssertionError("RoadGenerator not installed");
		Segment boulevard = net.segments.get(1677);
		double[] p = boulevard.at(boulevard.length / 2);
		int bx = (int) Math.floor(p[0]), bz = (int) Math.floor(p[1]);
		log("boulevard claims=" + g.claims(bx, bz) + " deckY=" + g.deckY(bx, bz) + " h=" + p[2]);
		if (!g.claims(bx, bz)) throw new AssertionError("boulevard centre not claimed");

		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			context.runOnClient(mc -> mc.options.renderDistance().set(10));
			sp.getServer().runCommand("gamemode spectator @a");
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("weather clear");

			// kerb straightness measured on generated blocks (no renders): a Vice Beach street by Ocean Dr and a downtown street
			double d1 = kerbDeviation(context, sp, "vice-beach-1048", true, 1932, -934.1, 0.0, 9);
			double d2 = kerbDeviation(context, sp, "downtown-1651", false, 505, -710.4, -0.1658, 9);
			// authored Ocean Drive: kerbs both sides along the hotel strip, straight, clear of every landmark claim
			String od = oceanDriveCheck(context, sp);
			double d3 = kerbDeviation(context, sp, "ocean-drive-south", false, -650, OD_SX + (-650 - OD_SZ) * OD_K, OD_K, 8);
			double d4 = kerbDeviation(context, sp, "ocean-drive-north", false, -900, OD_SX + (-900 - OD_SZ) * OD_K, OD_K, 8);
			// one street level view looking north up Ocean Drive (the row runs 9 degrees east of north)
			double vz = -400, vx = OD_SX + (vz - OD_SZ) * OD_K;
			shot(context, sp, vx, 66.5, vz, 189.0f, 4, "road-ocean-drive-north");
			if (d1 > 1.0 || d2 > 1.0 || d3 > 1.0 || d4 > 1.0) throw new AssertionError("kerb deviation over 1 block: " + d1 + ", " + d2 + ", " + d3 + ", " + d4);
			if (od != null) throw new AssertionError(od);
		}
	}

	/**
	 * Lateral deviation (blocks) of both kerb lines over 100 columns along the main axis, read from the
	 * generated world: per column the kerb block nearest the road centre on each side, residual range
	 * from the best fitting straight line. Centre line c(a) = c0 + k (a - a0).
	 */
	private static double kerbDeviation(ClientGameTestContext context, TestSingleplayerContext sp, String name, boolean ew, int a0,
			double c0, double k, int reach) {
		int cMin = (int) Math.floor(Math.min(c0, c0 + k * 100)) - reach - 4, cMax = (int) Math.ceil(Math.max(c0, c0 + k * 100)) + reach + 4;
		int x0 = ew ? a0 : cMin, x1 = ew ? a0 + 100 : cMax, z0 = ew ? cMin : a0, z1 = ew ? cMax : a0 + 100;
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload add " + x0 + " " + z0 + " " + x1 + " " + z1);
		int cx0 = x0 >> 4, cx1 = x1 >> 4, cz0 = z0 >> 4, cz1 = z1 >> 4, total = (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
		int full = 0;
		for (int i = 0; i < 360 && full < total; i++) {
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
		double worst = sp.getServer().computeOnServer(srv -> {
			ServerLevel lvl = srv.getLevel(WorldModule.LEONIDA);
			net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
			double w = 0;
			StringBuilder sb = new StringBuilder();
			for (int side = -1; side <= 1; side += 2) {
				java.util.List<double[]> pts = new java.util.ArrayList<>();
				for (int a = a0; a < a0 + 100; a++) {
					double c = c0 + k * (a - a0);
					Integer found = null;
					for (int d = reach - 3; d <= reach + 3 && found == null; d++) {
						int b = (int) Math.floor(c) + side * d;
						for (int y = 60; y < 90 && found == null; y++) {
							pos.set(ew ? a : b, y, ew ? b : a);
							if (lvl.getBlockState(pos).is(dev.leonida.look.LookBlocks.KERB)) found = b;
						}
					}
					if (found != null) pts.add(new double[] {a, found});
				}
				double dev = range(pts);
				sb.append(" side ").append(side).append(": ").append(pts.size()).append(" columns, deviation ").append(String.format(java.util.Locale.ROOT, "%.2f", dev));
				if (pts.size() < 60) dev = 99;
				if (dev > 0.99) {
					StringBuilder pl = new StringBuilder();
					for (double[] q : pts) pl.append((int) q[0]).append(':').append((int) q[1]).append(' ');
					log("kerb " + name + " side " + side + " points " + pl);
				}
				w = Math.max(w, dev);
			}
			log("kerb " + name + sb);
			return w;
		});
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload remove all");
		return worst;
	}

	// Ocean Drive centre line in block coords: south end node and dx/dz (from tools/roadbake/oceandrive.py)
	static final double OD_SX = 1938.03, OD_SZ = -246.18, OD_NZ = -934.13, OD_K = (2047.25 - 1938.03) / (-934.13 + 246.18);
	static final double[] OD_JUNCTIONS = {-309.27, -761.21, -934.13};
	static final java.util.Set<Integer> OD_SEGS = java.util.Set.of(1332, 1306, 1078);

	/**
	 * Walk Ocean Drive row by row (block z) in bands: both kerbs present, carriageway blocks between them,
	 * and no Ocean Drive column (kerb, sidewalk or carriageway) inside any vicecity landmark claim. Returns
	 * null on pass, else the failure text.
	 */
	private static String oceanDriveCheck(ClientGameTestContext context, TestSingleplayerContext sp) {
		int rows = 0, both = 0, road = 0, overlap = 0;
		java.util.Set<String> hitSites = new java.util.TreeSet<>();
		RoadNetwork rn = RoadNetwork.get();
		for (int bz = (int) Math.floor(OD_NZ) + 4; bz < (int) OD_SZ - 2; bz += 96) {
			final int z0 = bz, z1 = Math.min((int) OD_SZ - 3, bz + 95);
			int x0 = (int) Math.floor(OD_SX + (z0 - OD_SZ) * OD_K) - 24, x1 = (int) Math.ceil(OD_SX + (z1 - OD_SZ) * OD_K) + 24;
			loadBox(context, sp, Math.min(x0, x1), z0, Math.max(x0, x1), z1);
			int[] r = sp.getServer().computeOnServer(srv -> {
				ServerLevel lvl = srv.getLevel(WorldModule.LEONIDA);
				net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
				var sites = dev.leonida.vicecity.ViceLandmarks.INSTANCE.sites().values();
				int[] acc = new int[4];
				for (int z = z0; z <= z1; z++) {
					boolean nearJ = false;
					for (double jz : OD_JUNCTIONS) if (Math.abs(z - jz) < 16) nearJ = true;
					double c = OD_SX + (z - OD_SZ) * OD_K;
					for (int x = (int) Math.floor(c) - 14; x <= (int) Math.floor(c) + 14; x++) {
						boolean mine = false;
						for (RoadNetwork.Hit h : rn.hitsAt(x, z))
							if (OD_SEGS.contains(h.seg.id) && dev.leonida.road.gen.RoadGen.zone(h) != dev.leonida.road.gen.RoadGen.Zone.VERGE) mine = true;
						if (!mine) continue;
						for (var site : sites) if (site.claims(x, z)) { acc[3]++; synchronized (hitSites) { hitSites.add(site.id); } }
					}
					if (nearJ) continue;
					acc[0]++;
					boolean w = false, e = false;
					for (int d = 5; d <= 9; d++)
						for (int y = 60; y < 80; y++) {
							pos.set((int) Math.floor(c) - d, y, z);
							if (lvl.getBlockState(pos).is(dev.leonida.look.LookBlocks.KERB)) w = true;
							pos.set((int) Math.floor(c) + d, y, z);
							if (lvl.getBlockState(pos).is(dev.leonida.look.LookBlocks.KERB)) e = true;
						}
					if (w && e) acc[1]++;
					// carriageway: the top solid block at the centre column is a road block (asphalt or a marking)
					for (int y = 79; y >= 60; y--) {
						pos.set((int) Math.floor(c), y, z);
						var st = lvl.getBlockState(pos);
						if (st.isAir()) continue;
						String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
						if (id.startsWith("leonida:") && !st.is(dev.leonida.look.LookBlocks.KERB) && !st.is(dev.leonida.look.LookBlocks.SIDEWALK)) acc[2]++;
						break;
					}
				}
				return acc;
			});
			rows += r[0]; both += r[1]; road += r[2]; overlap += r[3];
			sp.getServer().runCommand("execute in leonida:world/leonida run forceload remove all");
		}
		log("ocean drive: " + rows + " rows checked, kerbs both sides " + both + ", carriageway at centre " + road + ", claim overlaps "
			+ overlap + (hitSites.isEmpty() ? "" : " " + hitSites));
		if (rows < 500) return "ocean drive: only " + rows + " rows";
		if (both < rows * 0.98 || road < rows * 0.98) return "ocean drive: kerbs " + both + " / road " + road + " of " + rows + " rows";
		if (overlap > 0) return "ocean drive overlaps landmark claims " + hitSites;
		return null;
	}

	private static void loadBox(ClientGameTestContext context, TestSingleplayerContext sp, int x0, int z0, int x1, int z1) {
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload add " + x0 + " " + z0 + " " + x1 + " " + z1);
		int cx0 = x0 >> 4, cx1 = x1 >> 4, cz0 = z0 >> 4, cz1 = z1 >> 4, total = (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
		int full = 0;
		for (int i = 0; i < 360 && full < total; i++) {
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
	}

	private static double range(java.util.List<double[]> pts) {
		int n = pts.size();
		if (n < 2) return 99;
		double ma = 0, mb = 0;
		for (double[] q : pts) { ma += q[0]; mb += q[1]; }
		ma /= n; mb /= n;
		double saa = 0, sab = 0;
		for (double[] q : pts) { saa += (q[0] - ma) * (q[0] - ma); sab += (q[0] - ma) * (q[1] - mb); }
		double kk = saa == 0 ? 0 : sab / saa, best = 1e9;
		// width of the narrowest straight band holding every kerb point (minimax line, slope searched around
		// the least squares slope): the honest lateral deviation, which least squares overstates on staircases
		for (int i = -400; i <= 400; i++) {
			double k = kk + i * 0.00005, lo = 1e9, hi = -1e9;
			for (double[] q : pts) { double r = q[1] - k * (q[0] - ma); lo = Math.min(lo, r); hi = Math.max(hi, r); }
			best = Math.min(best, hi - lo);
		}
		return best;
	}

	private static Props.Pole nearest(Props props, double x, double z, Props.Kind kind) {
		Props.Pole best = null;
		double bd = Double.MAX_VALUE;
		for (Props.Pole p : props.poles) {
			if (p.kind != kind) continue;
			double d = (p.x - x) * (p.x - x) + (p.z - z) * (p.z - z);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	/** Camera on the road: station fraction f, lateral offset (blocks, + = right), height above the surface, back along the road. */
	private static void along(ClientGameTestContext context, TestSingleplayerContext sp, RoadNetwork net, int seg, double f, double side,
			double up, double back, int pitch, String name) {
		Segment s = net.segments.get(seg);
		double[] c = s.at(s.length * f + back);
		double[] t = s.at(s.length * f);
		double nx = -Math.sin(c[3]), nz = Math.cos(c[3]);
		double x = c[0] + nx * side, z = c[1] + nz * side;
		double y = 62 + 1 + c[2] + up;
		if (side > 15) y = 62 + 1 + Math.max(0, c[4]) + up;
		double dx = t[0] - x, dz = t[1] - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		shot(context, sp, x, y, z, yaw, pitch, name);
	}

	private static void above(ClientGameTestContext context, TestSingleplayerContext sp, RoadNetwork net, int seg, double f, double up, String name) {
		Segment s = net.segments.get(seg);
		double[] c = s.at(s.length * f);
		double[] b = s.at(s.length * f - 20);
		double x = c[0] - Math.cos(c[3]) * 18, z = c[1] - Math.sin(c[3]) * 18;
		float yaw = (float) Math.toDegrees(Math.atan2(-(c[0] - x), c[1] - z));
		shot(context, sp, x, 63 + c[2] + up, z, yaw, 50, name);
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, double x, double y, double z, float yaw, int pitch, String name) {
		int r = 48;
		int cx0 = ((int) Math.floor(x) - r) >> 4, cx1 = ((int) Math.floor(x) + r) >> 4;
		int cz0 = ((int) Math.floor(z) - r) >> 4, cz1 = ((int) Math.floor(z) + r) >> 4;
		final int fx0 = cx0, fx1 = cx1, fz0 = cz0, fz1 = cz1, total = (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload add " + (fx0 << 4) + " " + (fz0 << 4) + " " + (fx1 << 4) + " " + (fz1 << 4));
		sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "execute in leonida:world/leonida run tp @a %.2f %.2f %.2f %.1f %d", x, y, z, yaw, pitch));
		int full = 0;
		for (int i = 0; i < 360 && full < total; i++) {
			context.waitTicks(10);
			full = sp.getServer().computeOnServer(srv -> {
				ServerLevel lvl = srv.getLevel(WorldModule.LEONIDA);
				int n = 0;
				for (int a = fx0; a <= fx1; a++)
					for (int b = fz0; b <= fz1; b++)
						if (lvl.getChunkSource().getChunkNow(a, b) != null) n++;
				return n;
			});
		}
		boolean rendered = false;
		for (int i = 0; i < 1 && !rendered; i++) {
			try {
				sp.getConnection().waitForChunksRender();
				rendered = true;
			} catch (AssertionError e) {
				log(name + ": render wait timed out, retrying");
			}
		}
		context.waitTicks(rendered ? 40 : 100);
		log("shot " + name + " at " + (int) x + "," + (int) y + "," + (int) z + " chunks " + full + "/" + total + " rendered=" + rendered);
		context.takeScreenshot(name);
		sp.getServer().runCommand("execute in leonida:world/leonida run forceload remove all");
	}
}
