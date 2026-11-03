package dev.leonida.vehicle.ai;

import dev.leonida.vehicle.VehicleControls;
import dev.leonida.vehicle.VehicleEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared steering for AI drivers: pure-pursuit path following, obstacle braking, unsticking. */
public abstract class PathDriver implements VehicleDriver {
	protected List<Vec3> path = null;
	protected int idx;
	private int stuckTicks, reverseTicks;
	private float reverseSteer;
	protected int blockedTicks;

	/** Build a lane path from road waypoints: shifted to the right of the travel direction. */
	protected static List<Vec3> lanePath(List<BlockPos> route, double lane, double y) {
		List<Vec3> out = new ArrayList<>();
		if (route.size() < 2) return out;
		// densify long straight segments so steering stays smooth
		List<Vec3> pts = new ArrayList<>();
		for (int i = 0; i < route.size(); i++) {
			Vec3 p = Vec3.atBottomCenterOf(route.get(i));
			if (!pts.isEmpty()) {
				Vec3 prev = pts.get(pts.size() - 1);
				double d = Math.sqrt(p.subtract(prev).horizontalDistanceSqr());
				int n = (int) (d / 8);
				for (int k = 1; k < n; k++) pts.add(prev.lerp(p, k / (double) n));
			}
			pts.add(p);
		}
		for (int i = 0; i < pts.size(); i++) {
			Vec3 a = pts.get(Math.max(0, i - 1)), b = pts.get(Math.min(pts.size() - 1, i + 1));
			Vec3 dir = new Vec3(b.x - a.x, 0, b.z - a.z);
			if (dir.lengthSqr() < 1e-6) continue;
			dir = dir.normalize();
			Vec3 right = new Vec3(-dir.z, 0, dir.x);
			Vec3 p = pts.get(i).add(right.scale(lane));
			out.add(new Vec3(p.x, y, p.z));
		}
		return out;
	}

	protected boolean pathDone() { return path == null || idx >= path.size(); }

	/** Steering and desired speed toward the path; returns controls. */
	protected VehicleControls followPath(VehicleEntity v, double cruise) {
		Vec3 pos = v.position();
		while (idx < path.size() && horiz(path.get(idx), pos) < 3.5) idx++;
		if (idx >= path.size()) return VehicleControls.BRAKE;
		double speed = v.forwardSpeed();
		double look = 4 + Math.abs(speed) * 0.55;
		Vec3 target = lookahead(pos, look);
		double curve = 0;
		if (idx + 1 < path.size()) {
			Vec3 a = path.get(idx).subtract(pos), b = path.get(Math.min(path.size() - 1, idx + 2)).subtract(path.get(idx));
			if (a.horizontalDistanceSqr() > 1 && b.horizontalDistanceSqr() > 1) {
				double cos = (a.x * b.x + a.z * b.z) / Math.sqrt(a.horizontalDistanceSqr() * b.horizontalDistanceSqr());
				curve = Math.acos(Mth.clamp(cos, -1, 1));
			}
		}
		double desired = cruise * Mth.clamp(1 - curve / 1.6, 0.35, 1);
		return steerTo(v, target, desired, true);
	}

	private Vec3 lookahead(Vec3 pos, double dist) {
		Vec3 last = path.get(idx);
		double acc = horiz(last, pos);
		if (acc >= dist) return last;
		for (int i = idx + 1; i < path.size(); i++) {
			Vec3 p = path.get(i);
			double seg = horiz(p, last);
			if (acc + seg >= dist) return last.lerp(p, (dist - acc) / Math.max(seg, 1e-3));
			acc += seg;
			last = p;
		}
		return last;
	}

	/** Pure pursuit toward a point with a desired speed, plus obstacle braking and unsticking. */
	protected VehicleControls steerTo(VehicleEntity v, Vec3 target, double desired, boolean avoid) {
		Vec3 pos = v.position();
		Vec3 d = target.subtract(pos);
		Vec3 f = v.forward(), r = v.right();
		double lz = d.x * f.x + d.z * f.z, lx = d.x * r.x + d.z * r.z;
		double L = Math.max(2, Math.sqrt(lx * lx + lz * lz));
		double alpha = Math.atan2(lx, lz);
		double maxSteer = v.spec.maxSteerDeg() * Mth.DEG_TO_RAD;
		double steerAng = Math.atan(2 * v.spec.wheelbase() * Math.sin(alpha) / L);
		float steer = (float) Mth.clamp(steerAng / maxSteer, -1, 1);
		if (lz < 0) {
			steer = lx >= 0 ? 1 : -1;
			desired = Math.min(desired, 5);
		}
		double speed = v.forwardSpeed();

		if (reverseTicks > 0) {
			reverseTicks--;
			return new VehicleControls(-1, reverseSteer, false);
		}

		if (avoid) {
			double obs = obstacleDistance(v, 4 + Math.max(0, speed) * 1.3);
			if (obs < Double.MAX_VALUE) {
				desired = Math.min(desired, Math.max(0, (obs - 3.5) * 0.9));
				blockedTicks++;
			} else blockedTicks = 0;
		}

		float throttle;
		if (desired < 0.3) throttle = speed > 0.3 ? -1 : 0;
		else if (speed < desired - 0.8) throttle = 1;
		else if (speed > desired + 1.5) throttle = -1;
		else throttle = 0;

		// unstick: pressing the gas but not moving
		if (throttle > 0 && Math.abs(speed) < 0.4) {
			if (++stuckTicks > 50) {
				stuckTicks = 0;
				reverseTicks = 30;
				reverseSteer = -steer;
				onStuck(v);
			}
		} else stuckTicks = 0;
		return new VehicleControls(throttle, steer, desired < 0.3 && Math.abs(speed) < 0.5);
	}

	protected void onStuck(VehicleEntity v) {}

	/** Distance to the nearest entity or wall in the driving lane ahead, or MAX_VALUE. */
	protected double obstacleDistance(VehicleEntity v, double range) {
		Vec3 f = v.forward(), r = v.right();
		Vec3 pos = v.position();
		double half = v.spec.length() / 2;
		AABB box = v.getBoundingBox().inflate(range + half, 0.5, range + half);
		double best = Double.MAX_VALUE;
		double laneHalf = v.spec.width() / 2 + 0.7;
		for (Entity e : v.level().getEntities(v, box, e -> (e instanceof LivingEntity || e instanceof VehicleEntity) && !v.hasPassenger(e) && e.isAlive() && !ignore(e))) {
			Vec3 d = e.position().subtract(pos);
			double lz = d.x * f.x + d.z * f.z, lx = d.x * r.x + d.z * r.z;
			double ew = e instanceof VehicleEntity ve ? ve.spec.width() / 2 : e.getBbWidth() / 2;
			double el = e instanceof VehicleEntity ve ? ve.spec.length() / 2 : e.getBbWidth() / 2;
			if (lz <= 0 || Math.abs(lx) > laneHalf + ew) continue;
			double dist = lz - half - el;
			if (dist < range) best = Math.min(best, Math.max(0, dist));
		}
		// walls (taller than a step) right in front
		for (int i = 1; i <= 2; i++) {
			Vec3 p = pos.add(f.scale(half + i));
			BlockPos bp = BlockPos.containing(p.x, pos.y + v.spec.stepHeight() + 0.2, p.z);
			if (!v.level().getBlockState(bp).getCollisionShape(v.level(), bp).isEmpty()) best = Math.min(best, i - 0.5);
		}
		return best;
	}

	protected boolean ignore(Entity e) { return false; }

	protected static double horiz(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
