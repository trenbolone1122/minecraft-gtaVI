package dev.leonida.test.vicecity;

import dev.leonida.core.api.MapQuery.Surface;
import dev.leonida.vicecity.ViceLandmarks;
import dev.leonida.vicecity.gen.Site;
import dev.leonida.vicecity.gen.Vox;
import dev.leonida.vicecity.site.Sites;
import dev.leonida.world.data.CwTerrain;
import dev.leonida.world.map.LeonidaMapQuery;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;

/**
 * Offline server-side check of vicecity sites (no client, no GPU): builds each site exactly as world generation does
 * (same terrain, same datum, same voxel cache), then reports position, size, block count, and problems: blocks
 * outside the claimed chunks (they would never generate), overlaps with other landmark claims, roads and water under
 * the claim. Run on the build machine with the runtime classpath:
 *   java -cp <classes:resources:mc-common:libs> dev.leonida.test.vicecity.VicecityCheck [ids...]
 */
public final class VicecityCheck {
	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		unfreeze();
		ViceLandmarks reg = ViceLandmarks.INSTANCE;
		Sites.registerAll(reg);
		CwTerrain t = CwTerrain.get();
		LeonidaMapQuery q = new LeonidaMapQuery();
		List<String> ids = new ArrayList<>(List.of(args));
		if (ids.isEmpty()) ids.addAll(reg.sites().keySet());
		int fails = 0;
		for (String id : ids) {
			Site s = reg.site(id);
			if (s == null) {
				System.out.println("CHECK " + id + " MISSING");
				fails++;
				continue;
			}
			long t0 = System.currentTimeMillis();
			Vox v = s.vox(t);
			int datum = s.lastDatum;
			double c = Math.cos(s.theta), sn = Math.sin(s.theta);
			double[] o = s.toWorld(0, 0);
			double[] ext = {1e9, -1e9, 1e9, -1e9};
			long[] outside = {0}, offIndex = {0};
			int[] layers = new int[40];
			v.each((x, y, z, st) -> {
				double dx = x + 0.5 - o[0], dy = -(z + 0.5 - o[1]); // map frame offsets
				double u = dx * c + dy * sn, w = -dx * sn + dy * c;
				ext[0] = Math.min(ext[0], u);
				ext[1] = Math.max(ext[1], u);
				ext[2] = Math.min(ext[2], w);
				ext[3] = Math.max(ext[3], w);
				if (x < s.minX - 16 || x > s.maxX + 16 || z < s.minZ - 16 || z > s.maxZ + 16) offIndex[0]++;
				else if (!s.claims(x, z)) outside[0]++;
				int l = (y - datum) / 10;
				if (l >= 0 && l < layers.length) layers[l]++;
			});
			// claim cells: water, roads, overlaps with other sites (vicecity and landmark module)
			int cells = 0, water = 0, road = 0, overlap = 0;
			StringBuilder ov = new StringBuilder();
			for (int x = s.minX; x <= s.maxX; x += 2)
				for (int z = s.minZ; z <= s.maxZ; z += 2) {
					if (!s.claims(x, z)) continue;
					cells++;
					if (t.waterY(x, z) != Integer.MIN_VALUE) water++;
					if (q.surfaceAt(x, z) == Surface.ROAD) road++;
					for (Site o2 : reg.sites().values())
						if (o2 != s && o2.claims(x, z)) {
							overlap++;
							if (ov.indexOf(o2.id + " ") < 0) ov.append(o2.id).append(' ');
						}
					for (var l2 : dev.leonida.landmark.LandmarkGen.SITES)
						if (l2.claims(x, z)) {
							overlap++;
							if (ov.indexOf(l2.id + " ") < 0) ov.append(l2.id).append(' ');
						}
				}
			boolean ok = v.count() > 0 && offIndex[0] == 0 && overlap == 0;
			if (!ok) fails++;
			StringBuilder ly = new StringBuilder();
			for (int i = 0; i < layers.length; i++) if (layers[i] > 0) ly.append(i * 10).append(':').append(layers[i]).append(' ');
			System.out.printf(java.util.Locale.ROOT,
				"CHECK %s %s %s | origin world %.1f,%.1f map %.1f,%.1f theta %.3f | claim x %d..%d z %d..%d | datum %d | blocks %d in %d ms | "
					+ "size u %.0f..%.0f (%.0f m) v %.0f..%.0f (%.0f m) h %d m | outsideClaim %d offIndex %d | water %d%% road %d%% of %d cells | overlap %d [%s] | layers %s%n",
				ok ? "PASS" : "FAIL", s.id, s.name, o[0], o[1], s.mapX, s.mapY, s.theta, s.minX, s.maxX, s.minZ, s.maxZ, datum, v.count(),
				System.currentTimeMillis() - t0, ext[0], ext[1], ext[1] - ext[0], ext[2], ext[3], ext[3] - ext[2], v.maxY - datum, outside[0],
				offIndex[0], cells == 0 ? 0 : water * 100 / cells, cells == 0 ? 0 : road * 100 / cells, cells, overlap, ov.toString().trim(), ly);
		}
		System.out.println("CHECK DONE fails " + fails);
		System.exit(fails == 0 ? 0 : 1);
	}

	/** Let mod blocks register outside Fabric (the loader normally does this). */
	static void unfreeze() throws Exception {
		Field frozen = MappedRegistry.class.getDeclaredField("frozen");
		Field holders = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
		frozen.setAccessible(true);
		holders.setAccessible(true);
		for (var r : BuiltInRegistries.REGISTRY) {
			if (!(r instanceof MappedRegistry<?> m)) continue;
			frozen.setBoolean(m, false);
			if (holders.get(m) == null) holders.set(m, new IdentityHashMap<>());
		}
	}
}
