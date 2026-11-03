package dev.leonida.hud;

import dev.leonida.core.api.MapQuery;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/** GPS route to the waypoint, recomputed in the background from {@link MapQuery#route}. */
public final class Route {
	private Route() {}

	private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "leonida-hud-route");
		t.setDaemon(true);
		return t;
	});
	private static volatile List<BlockPos> route = List.of();
	private static Future<?> pending;
	private static long lastCompute;
	private static BlockPos lastFrom;
	private static volatile boolean dirty = true;

	public static void invalidate() { dirty = true; }

	/** Current route polyline (player end first), empty if no waypoint. */
	public static List<BlockPos> get() { return route; }

	/** Route length in blocks from the player along the polyline. */
	public static double lengthFrom(double px, double pz) {
		List<BlockPos> r = route;
		if (r.size() < 2) return 0;
		// skip to the closest point, then sum
		int best = 0;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < r.size(); i++) {
			double dx = r.get(i).getX() + 0.5 - px, dz = r.get(i).getZ() + 0.5 - pz;
			double d = dx * dx + dz * dz;
			if (d < bestD) { bestD = d; best = i; }
		}
		double len = Math.sqrt(bestD);
		for (int i = best; i + 1 < r.size(); i++) {
			BlockPos a = r.get(i), b = r.get(i + 1);
			len += Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
		}
		return len;
	}

	/** Client tick: recompute when needed; clears the waypoint on arrival. */
	public static void tick() {
		Player p = Minecraft.getInstance().player;
		double[] wp = Blips.waypoint();
		if (p == null || wp == null) {
			route = List.of();
			return;
		}
		if (Math.hypot(wp[0] - p.getX(), wp[1] - p.getZ()) < 12) {
			Blips.clearWaypoint();
			route = List.of();
			return;
		}
		long now = System.currentTimeMillis();
		BlockPos from = p.blockPosition();
		boolean moved = lastFrom == null || lastFrom.distSqr(from) > 64;
		if ((dirty || (moved && now - lastCompute > 1000)) && (pending == null || pending.isDone())) {
			dirty = false;
			lastCompute = now;
			lastFrom = from;
			MapQuery q = MapColors.onLeonida() ? MapQuery.get() : MapQuery.FLAT;
			BlockPos to = new BlockPos((int) Math.floor(wp[0]), q.groundY((int) wp[0], (int) wp[1]), (int) Math.floor(wp[1]));
			pending = EXEC.submit(() -> {
				List<BlockPos> r;
				try {
					r = q.route(from, to);
				} catch (RuntimeException e) {
					r = List.of();
				}
				if (r == null || r.size() < 2) r = List.of(from, to);
				route = List.copyOf(r);
			});
		}
	}
}
