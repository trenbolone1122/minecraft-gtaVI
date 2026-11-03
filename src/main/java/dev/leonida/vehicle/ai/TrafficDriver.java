package dev.leonida.vehicle.ai;

import dev.leonida.core.api.MapQuery;
import dev.leonida.vehicle.VehicleControls;
import dev.leonida.vehicle.VehicleEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/** Ambient traffic: drives along MapQuery routes to random road destinations, keeps to the right lane. */
public class TrafficDriver extends PathDriver {
	private double cruise = -1;
	private int replanCooldown;
	private int honkCooldown;

	@Override
	public String kind() { return "traffic"; }

	/** Optional: start with a known path. */
	public void setRoute(List<BlockPos> route, double y) {
		path = lanePath(route, TrafficManager.LANE_OFFSET, y);
		idx = 0;
	}

	@Override
	public VehicleControls drive(VehicleEntity v) {
		if (cruise < 0) cruise = Math.min(v.spec.maxSpeed() * 0.5, 11 + v.getRandom().nextFloat() * 4);
		if (replanCooldown > 0) replanCooldown--;
		if (pathDone()) {
			if (replanCooldown == 0) {
				replanCooldown = 40;
				plan(v);
			}
			if (pathDone()) return VehicleControls.BRAKE;
		}
		VehicleControls c = followPath(v, cruise);
		if (blockedTicks > 60 && --honkCooldown <= 0) {
			honkCooldown = 50 + v.getRandom().nextInt(40);
			Player p = v.level().getNearestPlayer(v, 10);
			if (p != null) v.honk();
		}
		return c;
	}

	@Override
	protected void onStuck(VehicleEntity v) {
		if (v.getRandom().nextInt(3) == 0) path = null;
	}

	private void plan(VehicleEntity v) {
		MapQuery map = MapQuery.get();
		RandomSource rnd = v.getRandom();
		BlockPos from = v.blockPosition();
		for (int i = 0; i < 24; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2;
			double dist = 80 + rnd.nextDouble() * 200;
			int x = from.getX() + (int) (Math.cos(ang) * dist), z = from.getZ() + (int) (Math.sin(ang) * dist);
			if (!TrafficManager.isRoad(map.surfaceAt(x, z))) continue;
			List<BlockPos> route = map.route(from, new BlockPos(x, from.getY(), z));
			if (route.size() >= 2) {
				path = lanePath(route, TrafficManager.LANE_OFFSET, v.getY());
				idx = 0;
				if (!path.isEmpty()) return;
			}
		}
		path = null;
	}
}
