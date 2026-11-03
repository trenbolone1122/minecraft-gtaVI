package dev.leonida.aircraft.entity;

import dev.leonida.aircraft.AircraftHooks;
import dev.leonida.aircraft.AircraftModule;
import dev.leonida.aircraft.AircraftSpec;
import dev.leonida.aircraft.net.AircraftControlPayload;
import dev.leonida.core.api.CrimeEvents;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A flyable plane or helicopter. The flight model is adapted from Immersive Aircraft (VehicleEntity, EngineVehicle,
 * AircraftEntity, AirplaneEntity, Rotorcraft and GyrodyneEntity; GPL-3.0, by Luke100000), trimmed to what Leonida
 * needs: no fuel, inventory or upgrades. Physics runs on the pilot's client, or on the server when the aircraft has no
 * player pilot (parked, or flown by the built-in autopilot that police and news helicopters use).
 */
public class AircraftEntity extends VehicleEntity {
	private static final EntityDataAccessor<Float> ENGINE = SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> SMOKE = SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> LIGHT = SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> LIGHT_TARGET = SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> AUTOPILOT = SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(AircraftEntity.class, EntityDataSerializers.INT);

	public final AircraftSpec spec;

	// controls, smoothed like IA's pressingInterpolatedX/Y/Z
	protected float inputX, inputY, inputZ;
	protected float smoothX, smoothY, smoothZ;
	private float enginePower;
	private float lastY;
	private float inWaterLevel;
	public float roll, prevRoll;
	public float engineRotation, prevEngineRotation;
	/** 0 in the air, 1 parked: blends in the tail dragger nose-up attitude (spec.groundPitch). */
	public float groundTilt, prevGroundTilt;
	private int lastSentHash;

	// server side
	private @Nullable UUID owner;
	/** Id of the parked aircraft spawn point this came from, cleared once someone takes it. */
	public @Nullable String spawnPoint;
	public final Autopilot autopilot = new Autopilot(this);
	private @Nullable BlockPos lightPos;

	public AircraftEntity(EntityType<? extends AircraftEntity> type, Level level, AircraftSpec spec) {
		super(type, level);
		this.spec = spec;
		if (!level.isClientSide() && spec.variants > 1) setVariant(random.nextInt(spec.variants));
		groundTilt = prevGroundTilt = 1f;
	}

	public int getVariant() {
		return entityData.get(VARIANT);
	}

	public void setVariant(int v) {
		entityData.set(VARIANT, Mth.clamp(v, 0, spec.variants - 1));
	}

	/** Applies the parked nose-up attitude to a model space point (seats, lights, smoke follow the model). */
	public Vec3 tiltLocal(Vec3 local, float tilt) {
		if (spec.groundPitch == 0 || tilt <= 0) return local;
		double a = Math.toRadians(spec.groundPitch * tilt);
		double z = local.z - spec.groundPivotZ;
		double c = Math.cos(a), sn = Math.sin(a);
		return new Vec3(local.x, local.y * c + z * sn, spec.groundPivotZ + z * c - local.y * sn);
	}

	// ---------------------------------------------------------------- data

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(ENGINE, 0.0f);
		builder.define(SMOKE, true);
		builder.define(LIGHT, false);
		builder.define(LIGHT_TARGET, -1);
		builder.define(AUTOPILOT, false);
		builder.define(VARIANT, 0);
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		out.storeNullable("Owner", UUIDUtil.CODEC, owner);
		out.putBoolean("Smoke", isSmokeOn());
		out.putBoolean("Searchlight", isSearchlightOn());
		out.putBoolean("PoliceUnit", autopilot.policeUnit);
		if (autopilot.policeUnit) {
			out.putString("PoliceMode", autopilot.mode.name());
			out.storeNullable("PoliceTarget", UUIDUtil.CODEC, autopilot.savedTarget());
			out.putDouble("PoliceRadius", autopilot.savedRadius());
			out.putDouble("PoliceHeight", autopilot.height);
			out.putDouble("PoliceSpeed", autopilot.speed);
		}
		out.putInt("Variant", getVariant());
		if (spawnPoint != null) out.putString("SpawnPoint", spawnPoint);
		if (lightPos != null) out.store("LightPos", BlockPos.CODEC, lightPos);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		owner = in.read("Owner", UUIDUtil.CODEC).orElse(null);
		setSmoke(in.getBooleanOr("Smoke", true));
		setSearchlight(in.getBooleanOr("Searchlight", false));
		autopilot.policeUnit = in.getBooleanOr("PoliceUnit", false);
		setVariant(in.getIntOr("Variant", getVariant()));
		spawnPoint = in.getString("SpawnPoint").orElse(null);
		lightPos = in.read("LightPos", BlockPos.CODEC).orElse(null);
		if (autopilot.policeUnit) {
			// keep chasing the same player after a reload; anything else flies off and despawns
			java.util.UUID t = in.read("PoliceTarget", UUIDUtil.CODEC).orElse(null);
			if (t != null && "ORBIT".equals(in.getStringOr("PoliceMode", ""))) {
				autopilot.yieldsToPlayer = true;
				autopilot.resume(t, in.getDoubleOr("PoliceRadius", 18), in.getDoubleOr("PoliceHeight", 25),
						in.getDoubleOr("PoliceSpeed", 0.45));
			} else {
				autopilot.leave();
			}
		}
	}

	public float getEngineTarget() {
		return entityData.get(ENGINE);
	}

	public void setEngineTarget(float v) {
		entityData.set(ENGINE, Mth.clamp(v, 0f, 1f));
	}

	public float getEnginePower() {
		return enginePower;
	}

	/** Skip the spool-up, for aircraft spawned already flying. */
	public void startEngineNow() {
		setEngineTarget(1f);
		enginePower = 1f;
	}

	public boolean isSmokeOn() {
		return entityData.get(SMOKE);
	}

	public void setSmoke(boolean v) {
		entityData.set(SMOKE, v);
	}

	public boolean isSearchlightOn() {
		return entityData.get(LIGHT);
	}

	public void setSearchlight(boolean v) {
		entityData.set(LIGHT, v);
	}

	/** Entity the searchlight tracks, or -1 for a fixed beam angled ahead and down. */
	public int getSearchlightTarget() {
		return entityData.get(LIGHT_TARGET);
	}

	public void setSearchlightTarget(int id) {
		entityData.set(LIGHT_TARGET, id);
	}

	public boolean isAutopiloted() {
		return entityData.get(AUTOPILOT);
	}

	void setAutopilotFlag(boolean v) {
		entityData.set(AUTOPILOT, v);
	}

	public @Nullable UUID getOwner() {
		return owner;
	}

	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
	}

	public float getRoll(float tickDelta) {
		return Mth.lerp(tickDelta, prevRoll, roll);
	}

	public float getEngineRotation(float tickDelta) {
		return Mth.lerp(tickDelta, prevEngineRotation, engineRotation);
	}

	// ---------------------------------------------------------------- vehicle basics

	@Override
	protected Item getDropItem() {
		return AircraftModule.item(spec);
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(getDropItem());
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < spec.seats.size();
	}

	/**
	 * Move a passenger to another occupied seat index (passengers swap places). Returns false if the seat is out
	 * of range. Used by the seat command; the change reaches clients with the next passenger sync.
	 */
	public boolean moveToSeat(Entity passenger, int seat) {
		java.util.List<Entity> list = new java.util.ArrayList<>(getPassengers());
		int from = list.indexOf(passenger);
		if (from < 0 || seat < 0 || seat >= list.size() || seat >= spec.seats.size()) return false;
		java.util.Collections.swap(list, from, seat);
		((dev.leonida.aircraft.mixin.EntityPassengersAccessor) this).leonida$setPassengers(com.google.common.collect.ImmutableList.copyOf(list));
		return true;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		if (isAutopiloted()) return null;
		return getFirstPassenger() instanceof Player p ? p : null;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isSecondaryUseActive() || hasPassenger(player)) return InteractionResult.PASS;
		if (!canAddPassenger(player)) return InteractionResult.PASS;
		if (!level().isClientSide()) {
			return player.startRiding(this, false, true) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		if (level() instanceof ServerLevel && passenger instanceof ServerPlayer sp && getFirstPassenger() == sp) {
			if (isAutopiloted() && autopilot.yieldsToPlayer) {
				autopilot.stop();
			}
			if (!isAutopiloted()) {
				if (owner == null || !owner.equals(sp.getUUID())) {
					CrimeEvents.fire(sp, CrimeEvents.Crime.AIRCRAFT_THEFT, blockPosition());
					owner = sp.getUUID();
					spawnPoint = null;
				}
				sp.sendOverlayMessage(Component.translatable(spec.isHelicopter()
						? "message.leonida.aircraft.controls_heli" : "message.leonida.aircraft.controls_plane"));
			}
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (level() instanceof ServerLevel && passenger instanceof ServerPlayer sp && heightAboveGround(8) >= 8) {
			Parachutes.grantForBailout(sp);
		}
	}

	/** Distance to the first solid block below, capped at max. */
	public double heightAboveGround(int max) {
		Vec3 from = position();
		BlockHitResult hit = level().clip(new ClipContext(from, from.add(0, -max, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
		return hit.getType() == HitResult.Type.MISS ? max : from.y - hit.getLocation().y;
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		Vec3 side = new Vec3(-(getBbWidth() * 0.5 + 0.8), 0, 0).yRot(-getYRot() * Mth.DEG_TO_RAD);
		Vec3 candidate = position().add(side);
		if (level().noCollision(passenger, passenger.getBoundingBox().move(candidate.subtract(passenger.position())))) {
			return candidate;
		}
		return super.getDismountLocationForPassenger(passenger);
	}

	// ---------------------------------------------------------------- seats

	public Matrix4f getVehicleTransform() {
		Matrix4f m = new Matrix4f();
		m.rotateY(-getYRot() * Mth.DEG_TO_RAD);
		m.rotateX(getXRot() * Mth.DEG_TO_RAD);
		m.rotateZ(roll * Mth.DEG_TO_RAD);
		return m;
	}

	public Vec3 transform(Vec3 local) {
		Vector3f v = getVehicleTransform().transformPosition(new Vector3f((float) local.x, (float) local.y, (float) local.z));
		return new Vec3(v.x, v.y, v.z);
	}

	public Vec3 getForward() {
		return transform(new Vec3(0, 0, 1));
	}

	public Vec3 getTop() {
		return transform(new Vec3(0, 1, 0));
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		int i = Math.max(0, getPassengers().indexOf(passenger));
		Vec3 seat = spec.seats.get(Math.min(i, spec.seats.size() - 1));
		return transform(tiltLocal(seat, groundTilt));
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		float dy = getYRot() - yRotO;
		if (dy != 0) {
			passenger.setYRot(passenger.getYRot() + dy);
			passenger.setYHeadRot(passenger.getYHeadRot() + dy);
		}
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		if (getHurtTime() > 0) setHurtTime(getHurtTime() - 1);
		if (getDamage() > 0) setDamage(getDamage() - 1);
		prevRoll = roll;
		prevEngineRotation = engineRotation;
		prevGroundTilt = groundTilt;
		boolean parked = onGround() && !wasTouchingWater && getDeltaMovement().horizontalDistanceSqr() < 0.04;
		groundTilt = Mth.approach(groundTilt, parked ? 1f : 0f, 0.06f);

		super.tick();

		Vec3 v = getDeltaMovement();
		if (Double.isNaN(v.x) || Double.isNaN(v.y) || Double.isNaN(v.z)) setDeltaMovement(Vec3.ZERO);
		inWaterLevel = wasTouchingWater ? Math.min(1f, inWaterLevel + 0.05f) : Math.max(0f, inWaterLevel - 0.05f);

		boolean authoritative = isLocalInstanceAuthoritative();
		if (authoritative) {
			if (interpolationHandler.hasActiveInterpolation()) interpolationHandler.cancel();
			readInputs();
		}

		// engine spools toward its target everywhere, so all clients animate the same
		float rate = spec.isHelicopter() ? 0.015f : 0.04f;
		enginePower += Mth.clamp(getEngineTarget() - enginePower, -rate, rate);
		engineRotation += enginePower * (spec.isHelicopter() ? 0.9f : 1.3f);

		if (authoritative) {
			if (isAutopiloted() && !level().isClientSide()) {
				autopilot.tick();
			} else {
				smoothX += (inputX - smoothX) * 0.25f;
				smoothY += (inputY - smoothY) * 0.25f;
				smoothZ += (inputZ - smoothZ) * 0.25f;
				updateVelocity();
				updateController();
				move(MoverType.SELF, getDeltaMovement());
			}
			applyEffectsFromBlocks();
		}

		updateRoll(authoritative && !isAutopiloted());

		if (level().isClientSide()) {
			clientEffects();
		} else {
			serverSearchlight();
		}
	}

	private void readInputs() {
		inputX = inputY = inputZ = 0;
		if (level().isClientSide() && getControllingPassenger() instanceof Player p && p.isLocalPlayer()) {
			AircraftHooks.PilotKeys k = AircraftHooks.pilotKeys.get();
			inputX = (k.left() ? 1 : 0) - (k.right() ? 1 : 0);
			if (spec.isHelicopter()) {
				inputY = (k.up() ? 1 : 0) - (k.down() ? 1 : 0);
				inputZ = (k.forward() ? 1 : 0) - (k.back() ? 1 : 0);
			} else {
				inputY = (k.forward() ? 1 : 0) - (k.back() ? 1 : 0);
				inputZ = (k.down() ? 1 : 0) - (k.up() ? 1 : 0);
			}
		}
	}

	/** Pilot's client sends throttle and toggles when they change. */
	public void sendControls() {
		int h = Float.hashCode(getEngineTarget()) * 31 + (isSmokeOn() ? 2 : 0) + (isSearchlightOn() ? 1 : 0);
		if (h != lastSentHash) {
			lastSentHash = h;
			level().sendPacketToServer(new ServerboundCustomPayloadPacket(
					new AircraftControlPayload(getId(), getEngineTarget(), isSmokeOn(), isSearchlightOn())));
		}
	}

	private void updateRoll(boolean fromInput) {
		if (fromInput) {
			roll = onGround() ? roll * 0.9f : -smoothX * spec.rollFactor * (1f - inWaterLevel);
		} else {
			float dYaw = Mth.wrapDegrees(getYRot() - yRotO);
			float target = onGround() ? 0f : Mth.clamp(dYaw / Math.max(0.5f, spec.yawSpeed), -1f, 1f) * spec.rollFactor;
			roll += (target - roll) * 0.2f;
		}
	}

	// ---------------------------------------------------------------- flight model

	protected float vehicleGravity() {
		float g = -0.04f;
		if (spec.isHelicopter()) g *= 1f - enginePower;
		return g;
	}

	private void applyFriction() {
		float decay = 1f - spec.friction;
		float gravity = vehicleGravity();
		if (isInLava()) {
			gravity *= 0.125f;
			decay = 0.75f;
		} else if (wasTouchingWater) {
			if (spec.floats) {
				double h = getFluidHeight(FluidTags.WATER);
				gravity = (float) Mth.clamp((h - 0.15) * 0.12, -0.04, 0.06);
				decay = 0.97f;
			} else {
				gravity *= 0.25f;
				decay = 0.8f;
			}
		} else if (onGround()) {
			decay = isVehicle() ? spec.groundFriction : 0.75f;
		}
		Vec3 v = getDeltaMovement();
		float hd = spec.horizontalDecay, vd = spec.verticalDecay;
		double vy = v.y * decay * vd + gravity;
		if (wasTouchingWater && spec.floats) vy = v.y * 0.8 + gravity;
		setDeltaMovement(v.x * decay * hd, vy, v.z * decay * hd);
		float rf = decay * 0.97f;
		smoothX *= rf;
		smoothZ *= rf;
	}

	private void convertPower(Vec3 direction) {
		if (spec.isHelicopter()) {
			Vec3 vel = getDeltaMovement().multiply(1, 0, 1);
			if (vel.lengthSqr() < 1.0e-8) return;
			double drag = Math.abs(direction.dot(vel.normalize()));
			Vec3 nv = vel.normalize().lerp(direction, spec.lift).scale(vel.length() * (drag * spec.friction + (1 - spec.friction)));
			setDeltaMovement(nv.x, getDeltaMovement().y, nv.z);
		} else {
			Vec3 vel = getDeltaMovement();
			if (vel.lengthSqr() < 1.0e-8) return;
			double drag = Math.abs(direction.dot(vel.normalize()));
			float lift = spec.lift * (1f - inWaterLevel * (spec.floats ? 0.5f : 1f));
			setDeltaMovement(vel.normalize().lerp(direction, lift).scale(vel.length() * (drag * spec.friction + (1 - spec.friction))));
		}
	}

	private void updateVelocity() {
		Vec3 direction = getForward();
		float diff = (float) (lastY - getY());
		if (!spec.isHelicopter() && lastY != 0 && spec.glideFactor > 0 && diff != 0) {
			setDeltaMovement(getDeltaMovement().add(direction.scale(diff * spec.glideFactor * (1 - Math.abs(direction.y)))));
		}
		lastY = (float) getY();
		convertPower(direction);
		applyFriction();
		if (onGround()) {
			setXRot(getXRot() * 0.9f);
		} else if (!wasTouchingWater) {
			float wind = (float) (0.1 + getDeltaMovement().length()) * spec.wind
					* (1f + level().getRainLevel(0f) + level().getThunderLevel(0f));
			float nx = (float) (cosNoise(tickCount / 20.0 / spec.mass) * wind);
			float nz = (float) (cosNoise(tickCount / 21.0 / spec.mass) * wind);
			setXRot(getXRot() + nx);
			setYRot(getYRot() + nz);
			setDeltaMovement(getDeltaMovement().add(nx * 0.005, 0, nz * 0.005));
		}
	}

	private static double cosNoise(double time) {
		double value = 0;
		for (int i = 0; i < 5; i++) {
			value += Math.cos(time);
			time *= 1.3;
		}
		return value;
	}

	private void updateController() {
		if (!isVehicle()) {
			setEngineTarget(Math.max(0f, getEngineTarget() - 0.02f));
			return;
		}
		// rotation (AircraftEntity)
		setYRot(getYRot() - spec.yawSpeed * smoothX);
		if (!onGround() || spec.isHelicopter()) {
			if (!onGround()) setXRot(getXRot() + spec.pitchSpeed * smoothZ);
		}
		setXRot(Mth.clamp(getXRot() * (1f - spec.stabilizer), -80f, 80f));

		if (spec.isHelicopter()) {
			// Rotorcraft / gyrodyne: rotor spins up automatically while piloted
			setEngineTarget(1f);
			if (onGround() && enginePower > 0.9f && inputY > 0) {
				setDeltaMovement(getDeltaMovement().add(0, 0.05, 0));
			}
			float power = enginePower * spec.verticalSpeed * smoothY;
			Vec3 top = getTop();
			setDeltaMovement(getDeltaMovement().add(top.scale(power)));
			// hold altitude when no vertical input
			if (inputY == 0 && enginePower > 0.95f && !onGround()) {
				setDeltaMovement(getDeltaMovement().multiply(1, 0.85, 1));
			}
			float sin = Mth.sin(getXRot() * Mth.DEG_TO_RAD);
			float thrust = enginePower * enginePower * spec.engineSpeed * sin;
			if (onGround()) {
				thrust = 0.03f / (1f + (float) getDeltaMovement().length() * 5f) * smoothZ * enginePower;
				setDeltaMovement(getDeltaMovement().add(getForward().multiply(1, 0, 1).scale(thrust)));
				setYRot(getYRot());
			} else {
				setDeltaMovement(getDeltaMovement().add(getForward().scale(thrust)));
			}
		} else {
			// AirplaneEntity: W/S throttle, S at idle brakes
			float brake = 0;
			if (inputY != 0) setEngineTarget(getEngineTarget() + 0.04f * inputY);
			if (inputY < 0 && getEngineTarget() <= 0.001f) brake = 1f;
			if (brake > 0) setDeltaMovement(getDeltaMovement().scale(Mth.lerp(brake, 1f, spec.brakeFactor)));
			Vec3 direction = getForward();
			float thrust = (float) (Math.pow(enginePower, 2) * spec.engineSpeed * 1.25);
			float maxSpeed = spec.engineSpeed * 500f;
			double forwardSpeed = Math.max(0, getDeltaMovement().dot(direction)) * 20;
			thrust *= (float) Mth.clamp((1 - forwardSpeed / maxSpeed) / 0.25, 0, 1);
			if (onGround()) thrust *= 0.75f;
			if (wasTouchingWater && !spec.floats) thrust = 0;
			setDeltaMovement(getDeltaMovement().add(direction.scale(thrust)));
		}
		if (level().isClientSide()) sendControls();
	}

	// ---------------------------------------------------------------- effects

	private void clientEffects() {
		if (spec.smokeTrail && isSmokeOn() && enginePower > 0.3f && !onGround() && !wasTouchingWater) {
			Vec3 back = getDeltaMovement().scale(-0.5);
			for (int i = 0; i < 16; i++) {
				double side = (random.nextDouble() * 2 - 1) * spec.smokeOffset.x;
				Vec3 at = position().add(transform(new Vec3(side, spec.smokeOffset.y, spec.smokeOffset.z)));
				double s = 0.6;
				level().addParticle(new DustParticleOptions(i % 2 == 0 ? 0xFF6EC7 : 0xFF9AD8, 4.0f),
						at.x + back.x * i * 0.2 + (random.nextDouble() - 0.5) * s,
						at.y + back.y * i * 0.2 + (random.nextDouble() - 0.5) * s,
						at.z + back.z * i * 0.2 + (random.nextDouble() - 0.5) * s, 0, -0.02, 0);
			}
		}
	}

	/** Where the searchlight hits, or null when off. Same rule on both sides, so the beam and the light agree. */
	public @Nullable Vec3 getSearchlightSpot(float tickDelta) {
		if (!spec.searchlight || !isSearchlightOn()) return null;
		int id = getSearchlightTarget();
		if (id >= 0) {
			Entity e = level().getEntity(id);
			if (e != null) return e.getPosition(tickDelta);
		}
		Vec3 from = getPosition(tickDelta).add(transform(spec.lightPivot));
		Vec3 dir = transform(new Vec3(0, -1, 1).normalize());
		BlockHitResult hit = level().clip(new ClipContext(from, from.add(dir.scale(80)), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
		return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
	}

	private void serverSearchlight() {
		if (!spec.searchlight) return;
		if (tickCount % 4 != 0) return;
		Vec3 spot = getSearchlightSpot(1f);
		BlockPos want = null;
		if (spot != null) {
			BlockPos p = BlockPos.containing(spot.x, spot.y + 0.5, spot.z);
			for (int i = 0; i < 3 && want == null; i++) {
				BlockState s = level().getBlockState(p.above(i));
				if (s.isAir() || s.is(Blocks.LIGHT)) want = p.above(i);
			}
		}
		if (want != null && lightPos != null && want.distManhattan(lightPos) <= 1 && level().getBlockState(lightPos).is(Blocks.LIGHT)) {
			return;
		}
		clearLight();
		if (want != null && level().getBlockState(want).isAir()) {
			level().setBlock(want, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15), 3);
			lightPos = want;
		}
	}

	private void clearLight() {
		if (lightPos != null) {
			if (level().getBlockState(lightPos).is(Blocks.LIGHT)) {
				level().setBlock(lightPos, Blocks.AIR.defaultBlockState(), 3);
			}
			lightPos = null;
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (!level().isClientSide()) clearLight();
		super.remove(reason);
	}

	/** Seats in world space, for tests and the renderer. */
	public List<Vec3> seatPositions() {
		return spec.seats.stream().map(s -> position().add(transform(s))).toList();
	}
}
