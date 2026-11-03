// Offline MapQuery check for Ocean Drive in the world roads: run with the main runtime classpath (see tools/worldgen/oceandrive_world.py).
import dev.leonida.core.api.MapQuery.Surface;
import dev.leonida.world.map.LeonidaMapQuery;
import java.util.EnumMap;
import java.util.Map;

public class ODCheck {
	public static void main(String[] a) {
		double sx = 1938.03, sz = -246.18, k = (2047.25 - 1938.03) / (-934.13 + 246.18);
		LeonidaMapQuery q = new LeonidaMapQuery();
		int[] offs = {0, -3, 3, -5, 5, -8, 8};
		int n = 0, centreRoad = 0, laneRoad = 0, sideSidewalk = 0;
		Map<Surface, Integer> bad = new EnumMap<>(Surface.class);
		for (int z = -930; z <= -250; z += 5) {
			double c = sx + (z - sz) * k;
			n++;
			Surface s0 = q.surfaceAt((int) Math.floor(c), z);
			if (s0 == Surface.ROAD) centreRoad++; else bad.merge(s0, 1, Integer::sum);
			boolean lanes = true;
			for (int o : new int[] {-5, -3, 3, 5}) lanes &= q.surfaceAt((int) Math.floor(c + o), z) == Surface.ROAD;
			if (lanes) laneRoad++;
			if (q.surfaceAt((int) Math.floor(c - 8), z) == Surface.SIDEWALK && q.surfaceAt((int) Math.floor(c + 8), z) == Surface.SIDEWALK) sideSidewalk++;
		}
		System.out.println("points " + n + ", ROAD at centre " + centreRoad + ", ROAD at +-3 and +-5 " + laneRoad + ", SIDEWALK at +-8 " + sideSidewalk + (bad.isEmpty() ? "" : " non-road at centre " + bad));
		var r = q.route(new net.minecraft.core.BlockPos(1940, 70, -260), new net.minecraft.core.BlockPos(2045, 70, -925));
		double worst = 0;
		for (var p : r) {
			double c = sx + (p.getZ() - sz) * k;
			worst = Math.max(worst, Math.abs(p.getX() - c) * Math.cos(Math.atan(k)));
		}
		System.out.println("route south end to north end: " + r.size() + " points, max distance from the drive's centre line " + String.format("%.1f", worst) + " m");
	}
}
