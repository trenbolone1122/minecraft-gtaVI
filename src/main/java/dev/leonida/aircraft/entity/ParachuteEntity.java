package dev.leonida.aircraft.entity;

import dev.leonida.aircraft.AircraftHooks;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An open parachute carrying one player. The rider's client simulates it: slow descent, A and D turn, W dives
 * faster, S flares. It folds away as soon as it touches ground or water.
 */
public class ParachuteEntity extends Entity {
	public float roll, prevRoll;
	private int emptyTicks;

	public ParachuteEntity(EntityType<? extends ParachuteEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof Player p ? p : null;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().isEmpty();
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return Vec3.ZERO;
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return position();
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		moveFunction.accept(passenger, getX(), getY(), getZ());
		float dy = getYRot() - yRotO;
		passenger.setYRot(passenger.getYRot() + dy);
		passenger.setYHeadRot(passenger.getYHeadRot() + dy);
		if (passenger instanceof LivingEntity l) l.setYBodyRot(getYRot());
	}

	public float getRoll(float tickDelta) {
		return Mth.lerp(tickDelta, prevRoll, roll);
	}

	/** A canopy lands softly: never pass the fall it has accumulated on to the rider. */
	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, net.minecraft.world.damagesource.DamageSource damageSource) {
		return false;
	}

	@Override
	public void tick() {
		prevRoll = roll;
		resetFallDistance();
		super.tick();
		if (isLocalInstanceAuthoritative()) {
			if (interpolationHandler.hasActiveInterpolation()) interpolationHandler.cancel();
			float turn = 0, dive = 0;
			if (level().isClientSide() && getControllingPassenger() instanceof Player p && p.isLocalPlayer()) {
				AircraftHooks.PilotKeys k = AircraftHooks.pilotKeys.get();
				turn = (k.right() ? 1 : 0) - (k.left() ? 1 : 0);
				dive = (k.forward() ? 1 : 0) - (k.back() ? 1 : 0);
			}
			setYRot(getYRot() + turn * 3.5f);
			roll += (turn * 18f - roll) * 0.15f;
			double forward = 0.22 + dive * 0.12;
			double sink = -0.12 - Math.max(0, dive) * 0.08 + Math.max(0, -dive) * 0.04;
			Vec3 dir = Vec3.directionFromRotation(0, getYRot());
			Vec3 v = getDeltaMovement();
			double vy = v.y + (sink - v.y) * 0.15;
			Vec3 h = new Vec3(v.x, 0, v.z).lerp(dir.scale(forward), 0.08);
			setDeltaMovement(h.x, vy, h.z);
			move(MoverType.SELF, getDeltaMovement());
		} else {
			float dYaw = Mth.wrapDegrees(getYRot() - yRotO);
			roll += (Mth.clamp(dYaw / 3.5f, -1, 1) * 18f - roll) * 0.15f;
		}
		if (level() instanceof ServerLevel) {
			for (Entity e : getPassengers()) e.resetFallDistance();
			if (getPassengers().isEmpty()) {
				if (++emptyTicks > 2) discard();
			} else if (onGround() || isInWater() || isInLava()) {
				for (Entity e : getPassengers()) e.resetFallDistance();
				ejectPassengers();
				discard();
			}
		}
	}
}
