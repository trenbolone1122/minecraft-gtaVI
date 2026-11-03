package dev.leonida.vehicle.ai;

import dev.leonida.core.api.MapQuery;
import dev.leonida.vehicle.VehicleControls;
import dev.leonida.vehicle.VehicleEntity;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Police pursuit: follows roads toward the target when far, drives straight at it when close,
 * rams a moving target and stops next to a stopped one (boxing in). Null target parks the car.
 */
public class PursuitDriver extends PathDriver {
	@Nullable private Entity target;
	private int repath;
	private Vec3 lastTargetPos;
	private boolean arrivedFired;
	@Nullable private Consumer<VehicleEntity> onArrive;

	public PursuitDriver(@Nullable Entity target) { this.target = target; }

	public void setTarget(@Nullable Entity t) {
		target = t;
		path = null;
		arrivedFired = false;
	}

	@Nullable public Entity target() { return target; }

	public void onArrive(@Nullable Consumer<VehicleEntity> c) { onArrive = c; }

	@Override
	public VehicleControls drive(VehicleEntity v) {
		Entity t = target;
		if (t != null && (t.isRemoved() || !t.isAlive() || t.level() != v.level())) target = t = null;
		if (t == null) {
			v.setSiren(false);
			return VehicleControls.BRAKE;
		}
		Entity tv = t.getRootVehicle();
		Vec3 tp = tv.position();
		Vec3 tvel = lastTargetPos == null ? Vec3.ZERO : tp.subtract(lastTargetPos).scale(20);
		lastTargetPos = tp;
		double tSpeed = Math.sqrt(tvel.horizontalDistanceSqr());
		double dist = horiz(tp, v.position());

		if (dist > 45) {
			if (--repath <= 0 || pathDone()) {
				repath = 60;
				List<BlockPos> route = MapQuery.get().route(v.blockPosition(), tv.blockPosition());
				path = route.size() > 2 ? lanePath(route, 0.0, v.getY()) : null;
				idx = 0;
			}
			if (!pathDone()) return followPath(v, v.spec.maxSpeed() * 0.85);
		}
		double lead = Math.min(1.5, dist / Math.max(8, v.forwardSpeed() + 5));
		Vec3 aim = tp.add(tvel.scale(lead));
		double desired;
		if (dist < 9 && tSpeed < 2.5) {
			if (!arrivedFired && onArrive != null) {
				arrivedFired = true;
				onArrive.accept(v);
			}
			desired = dist < 6 ? 0 : 3;
		} else {
			arrivedFired = false;
			desired = Math.min(v.spec.maxSpeed() * 0.95, dist * 1.4 + tSpeed);
		}
		return steerTo(v, aim, desired, dist > 14);
	}

	@Override
	protected boolean ignore(Entity e) {
		return target != null && (e == target || e == target.getRootVehicle());
	}
}
