package dev.leonida.aircraft.entity;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Server-side kinematic pilot. Circles a point or a target entity at a set height, or flies away and despawns.
 * Used by police and news helicopters, and by the game tests for in-flight shots.
 */
public final class Autopilot {
	public enum Mode { OFF, ORBIT, LEAVE }

	private final AircraftEntity aircraft;
	public Mode mode = Mode.OFF;
	public @Nullable Entity target;
	public Vec3 center = Vec3.ZERO;
	public double radius = 18, height = 25, speed = 0.5;
	/** Police units are flagged so a reloaded world does not keep orphaned helicopters around. */
	public boolean policeUnit;
	/** When a player takes the pilot seat, they take over (and it counts as theft). */
	public boolean yieldsToPlayer = true;
	/** Face the target instead of the direction of travel (helicopters only). */
	public boolean faceTarget = true;
	/** Target saved with the world; resolved again once that player is back in this level. */
	public @Nullable UUID pendingTarget;
	private int pendingTicks;
	private double angle;
	private int leaveTicks;
	private Vec3 leaveDir = new Vec3(1, 0, 0);

	Autopilot(AircraftEntity aircraft) {
		this.aircraft = aircraft;
	}

	public void orbit(@Nullable Entity target, Vec3 center, double radius, double height, double speed) {
		this.mode = Mode.ORBIT;
		this.pendingTarget = null;
		this.target = target;
		this.center = center;
		this.radius = radius;
		this.height = height;
		this.speed = speed;
		Vec3 rel = aircraft.position().subtract(anchor());
		this.angle = Math.atan2(rel.z, rel.x);
		aircraft.setAutopilotFlag(true);
		aircraft.setEngineTarget(1f);
	}

	public void leave() {
		this.mode = Mode.LEAVE;
		this.pendingTarget = null;
		this.target = null;
		this.leaveTicks = 0;
		Vec3 f = aircraft.getForward().multiply(1, 0, 1);
		this.leaveDir = f.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : f.normalize();
		aircraft.setSearchlight(false);
		aircraft.setSearchlightTarget(-1);
		aircraft.setAutopilotFlag(true);
		aircraft.setEngineTarget(1f);
	}

	public void stop() {
		mode = Mode.OFF;
		pendingTarget = null;
		target = null;
		policeUnit = false;
		aircraft.setAutopilotFlag(false);
	}

	private Vec3 anchor() {
		return target != null ? target.position() : center;
	}

	/** The UUID to save for the current target (also while still waiting for it after a reload). */
	public @Nullable UUID savedTarget() {
		if (pendingTarget != null) return pendingTarget;
		return mode == Mode.ORBIT && target instanceof ServerPlayer p ? p.getUUID() : null;
	}

	/** After a reload: hover where we are and pick the saved target up again when it shows up. */
	public void resume(UUID target, double radius, double height, double speed) {
		this.mode = Mode.ORBIT;
		this.target = null;
		this.center = aircraft.position().subtract(0, height, 0);
		this.radius = 0.1;
		this.height = height;
		this.speed = speed;
		this.pendingTarget = target;
		this.pendingTicks = 0;
		aircraft.setAutopilotFlag(true);
		aircraft.setEngineTarget(1f);
		pendingRadius = radius;
	}

	private double pendingRadius = 18;

	public double savedRadius() {
		return pendingTarget != null ? pendingRadius : radius;
	}

	private void resolvePending() {
		if (pendingTarget == null || aircraft.level().getServer() == null) return;
		ServerPlayer p = aircraft.level().getServer().getPlayerList().getPlayer(pendingTarget);
		if (p != null && p.isAlive() && p.level() == aircraft.level()) {
			orbit(p, p.position(), pendingRadius, height, speed);
			aircraft.setSearchlight(true);
			aircraft.setSearchlightTarget(p.getId());
		} else if (++pendingTicks > 600) {
			leave();
		}
	}

	void tick() {
		if (mode == Mode.OFF) {
			aircraft.setAutopilotFlag(false);
			return;
		}
		if (pendingTarget != null) resolvePending();
		if (aircraft.getEnginePower() < 0.6f && mode == Mode.ORBIT) {
			// spool up first, gently lift off
			aircraft.setDeltaMovement(0, aircraft.getEnginePower() * 0.05, 0);
			aircraft.move(MoverType.SELF, aircraft.getDeltaMovement());
			return;
		}
		if (mode == Mode.ORBIT && target != null && (!target.isAlive() || target.level() != aircraft.level())) {
			if (policeUnit) {
				leave();
			} else {
				center = target.position();
				target = null;
			}
		}
		Vec3 pos = aircraft.position();
		Vec3 vel = aircraft.getDeltaMovement();
		Vec3 desired;
		Vec3 lookAt;
		if (mode == Mode.LEAVE) {
			leaveTicks++;
			desired = pos.add(leaveDir.scale(8)).add(0, leaveTicks < 100 ? 2 : 0.5, 0);
			lookAt = desired;
			if (leaveTicks > 400) {
				aircraft.discard();
				return;
			}
		} else {
			Vec3 a = anchor();
			angle += speed / Math.max(4, radius);
			desired = new Vec3(a.x + Math.cos(angle) * radius, a.y + height, a.z + Math.sin(angle) * radius);
			lookAt = aircraft.spec.isHelicopter() && faceTarget ? a
					: new Vec3(a.x + Math.cos(angle + 0.5) * radius, desired.y, a.z + Math.sin(angle + 0.5) * radius);
		}

		if (aircraft.spec.isHelicopter()) {
			Vec3 d = desired.subtract(pos);
			Vec3 want = d.scale(0.08);
			double max = mode == Mode.LEAVE ? 0.9 : Math.max(0.9, speed * 1.5);
			if (want.length() > max) want = want.normalize().scale(max);
			vel = vel.lerp(want, 0.12);
			aircraft.setDeltaMovement(vel);
			Vec3 to = lookAt.subtract(pos);
			float wantYaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
			aircraft.setYRot(Mth.approachDegrees(aircraft.getYRot(), wantYaw, 5f));
			Vec3 fwd = Vec3.directionFromRotation(0, aircraft.getYRot());
			double forwardSpeed = vel.dot(fwd);
			float wantPitch = (float) Mth.clamp(forwardSpeed * 22, -15, 20);
			aircraft.setXRot(Mth.approach(aircraft.getXRot(), wantPitch, 1.5f));
		} else {
			Vec3 to = desired.subtract(pos);
			float wantYaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
			aircraft.setYRot(Mth.approachDegrees(aircraft.getYRot(), wantYaw, aircraft.spec.yawSpeed * 0.8f));
			float wantPitch = (float) Mth.clamp(-(desired.y - pos.y) * 2.5, -20, 20);
			aircraft.setXRot(Mth.approach(aircraft.getXRot(), wantPitch, 1f));
			double s = Math.max(0.7, speed * 1.6);
			vel = aircraft.getForward().scale(s);
			aircraft.setDeltaMovement(vel);
		}
		aircraft.move(MoverType.SELF, aircraft.getDeltaMovement());
	}
}
