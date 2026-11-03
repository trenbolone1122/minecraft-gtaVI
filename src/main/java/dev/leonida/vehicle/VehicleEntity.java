package dev.leonida.vehicle;

import dev.leonida.core.api.CrimeEvents;
import dev.leonida.core.api.CrimeEvents.Crime;
import dev.leonida.vehicle.ai.VehicleDriver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One drivable vehicle (car, bike or boat). Physics run on whichever side is authoritative: the
 * driving player's client, or the server for AI traffic, police and parked cars.
 */
public class VehicleEntity extends Entity {
	private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_CONTROL = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_PICK = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_HURT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
	/** Front door events: left count in the low 16 bits, right count in the high 16; each bump swings that door. */
	private static final EntityDataAccessor<Integer> DATA_DOORS = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);

	public static final int F_LOCKED = 1, F_WINDOW_BROKEN = 2, F_HEADLIGHTS = 4, F_SIREN = 8, F_NPC_DRIVER = 16, F_WRECKED = 32,
			F_BURNING = 64, F_ALARM = 128, F_ENGINE = 256;

	public static final int PICK_TICKS = 80;
	private static final double G = 0.08;

	/** Installed by the client: input of the local player when driving. */
	public static Supplier<VehicleControls> clientInput = () -> VehicleControls.NONE;
	/** Installed by the client: send control bits / crash reports to the server. */
	public static ClientBridge clientBridge = new ClientBridge() {};

	public interface ClientBridge {
		default void sendControls(VehicleEntity v, int bits) {}
		default void sendCrash(VehicleEntity v, float impact) {}
	}

	public final VehicleSpec spec;

	// physics state
	private float steer;           // -1..1 smoothed
	private float yawDelta;        // last tick yaw change, applied to passengers
	private boolean skidding;
	private int lastSentControl = -1;
	private int crashCooldown;
	private int airTicks;

	// server state
	@Nullable private VehicleDriver driver;
	private boolean traffic;       // ambient traffic or parked filler, may despawn
	private boolean touchedByPlayer;
	@Nullable private UUID owner;
	@Nullable private UUID picker;
	private int pickTicks;
	private int alarmTicks;
	private int wreckTicks;
	private double serverSpeed;    // m/s estimated from position deltas
	private Vec3 lastServerPos;
	private final Map<Integer, Integer> hitCooldown = new HashMap<>();
	private int recklessCooldown;

	// client visual state
	public float wheelSpin, wheelSpinO;
	public float bodyPitch, bodyPitchO, bodyRoll, bodyRollO;
	public float suspension, suspensionO;
	public float visualSteer, visualSteerO;
	public float clientSpeed;      // m/s, signed forward speed
	public boolean clientSkid;
	private double lastClientY;

	public VehicleEntity(EntityType<?> type, Level level) {
		super(type, level);
		this.spec = VehicleTypes.BY_TYPE.getOrDefault(type, VehicleTypes.STANIER);
		this.blocksBuilding = true;
		this.lastServerPos = Vec3.ZERO;
	}

	// ---------------------------------------------------------------- data

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		b.define(DATA_HEALTH, 100f);
		b.define(DATA_COLOR, 0xFFFFFF);
		b.define(DATA_FLAGS, 0);
		b.define(DATA_CONTROL, 0);
		b.define(DATA_PICK, 0);
		b.define(DATA_HURT, 0);
		b.define(DATA_DOORS, 0);
	}

	public float getHealth() { return entityData.get(DATA_HEALTH); }
	public void setHealth(float h) { entityData.set(DATA_HEALTH, Mth.clamp(h, 0, spec.maxHealth())); }
	public float healthFraction() { return getHealth() / spec.maxHealth(); }
	public int getColor() { return entityData.get(DATA_COLOR); }
	public void setColor(int c) { entityData.set(DATA_COLOR, c & 0xFFFFFF); }
	public int getControlBits() { return entityData.get(DATA_CONTROL); }
	public int getPickProgress() { return entityData.get(DATA_PICK); }
	public int getHurtTime() { return entityData.get(DATA_HURT); }

	public boolean flag(int f) { return (entityData.get(DATA_FLAGS) & f) != 0; }

	public void setFlag(int f, boolean on) {
		int v = entityData.get(DATA_FLAGS);
		entityData.set(DATA_FLAGS, on ? v | f : v & ~f);
	}

	public boolean isLocked() { return flag(F_LOCKED); }
	public void setLocked(boolean b) { setFlag(F_LOCKED, b); }
	public boolean isWrecked() { return flag(F_WRECKED); }
	public boolean hasNpcDriver() { return flag(F_NPC_DRIVER); }
	public void setNpcDriver(boolean b) { setFlag(F_NPC_DRIVER, b); }
	public boolean isSirenOn() { return flag(F_SIREN); }
	public void setSiren(boolean b) { setFlag(F_SIREN, b && spec.police()); }
	public boolean headlightsOn() { return flag(F_HEADLIGHTS); }
	public void setHeadlights(boolean b) { setFlag(F_HEADLIGHTS, b); }
	public boolean isPolice() { return spec.police(); }
	public boolean isTraffic() { return traffic; }
	public void setTraffic(boolean b) { traffic = b; }
	public boolean touchedByPlayer() { return touchedByPlayer; }
	@Nullable public VehicleDriver getDriver() { return driver; }
	public void setDriver(@Nullable VehicleDriver d) { driver = d; }
	@Nullable public UUID getOwner() { return owner; }
	public void setOwner(@Nullable UUID u) { owner = u; }
	public float steer() { return steer; }
	public boolean isSkidding() { return skidding; }

	/** Driving with an engine running: a player, an NPC or an AI driver at the wheel. */
	public boolean engineRunning() {
		return !isWrecked() && (getControllingPassenger() != null || hasNpcDriver() || flag(F_ENGINE));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		setHealth(in.getFloatOr("Health", spec.maxHealth()));
		setColor(in.getIntOr("Color", spec.paints()[0]));
		entityData.set(DATA_FLAGS, in.getIntOr("Flags", 0) & ~(F_ALARM | F_SIREN));
		traffic = in.getBooleanOr("Traffic", false);
		touchedByPlayer = in.getBooleanOr("Touched", false);
		owner = in.read("Owner", UUIDUtil.CODEC).orElse(null);
		if (hasNpcDriver() && !level().isClientSide()) driver = dev.leonida.vehicle.ai.LaneDriver.available(level())
				? new dev.leonida.vehicle.ai.LaneDriver(random.nextInt(), 0.85) : new dev.leonida.vehicle.ai.TrafficDriver();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		out.putFloat("Health", getHealth());
		out.putInt("Color", getColor());
		out.putInt("Flags", entityData.get(DATA_FLAGS));
		out.putBoolean("Traffic", traffic);
		out.putBoolean("Touched", touchedByPlayer);
		if (owner != null) out.store("Owner", UUIDUtil.CODEC, owner);
	}

	/** Call after spawning: full health and a random paint from the model's palette. */
	public void initFresh() {
		setHealth(spec.maxHealth());
		int[] p = spec.paints();
		setColor(p[random.nextInt(p.length)]);
	}

	// ---------------------------------------------------------------- entity basics

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public float maxUpStep() { return spec.stepHeight(); }

	@Override
	public boolean isPickable() { return !isRemoved(); }

	@Override
	public boolean isPushable() { return false; }

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) { return true; }

	@Override
	public boolean canCollideWith(Entity e) {
		return (e.canBeCollidedWith(this) || e.isPushable()) && !isPassengerOfSameVehicle(e);
	}

	@Override
	protected double getDefaultGravity() { return G; }

	@Override
	public boolean shouldRender(double x, double y, double z) {
		double d = distanceToSqr(x, y, z);
		return d < 160 * 160;
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		Entity first = getFirstPassenger();
		return first instanceof Player p ? p : null;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return getPassengers().size() < spec.seatCount() && !isWrecked();
	}

	public Vec3 forward() {
		float r = getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
	}

	public Vec3 right() {
		float r = getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
	}

	/** Local (x right, y up, z forward) to world. */
	public Vec3 toWorld(Vec3 local) {
		return position().add(right().scale(local.x)).add(0, local.y, 0).add(forward().scale(local.z));
	}

	private int shiftTicks;

	// ---------------------------------------------------------------- door swing (server bumps, client animates)

	static final int DOOR_OPEN = 7, DOOR_HOLD = 14, DOOR_CLOSE = 7;
	static final float DOOR_DEG = 70;
	private int lastDoors = Integer.MIN_VALUE;
	private final int[] doorAge = {-1, -1};

	/** Swings the front door on the side of seat {@code seat} (cars only). */
	private void swingDoor(int seat) {
		if (seat < 0 || spec.isBike() || spec.isBoat() || spec.seats().isEmpty()) return;
		Vec3 sv = spec.seats().get(Math.min(seat, spec.seatCount() - 1));
		int v = entityData.get(DATA_DOORS);
		int shift = sv.x < 0 ? 0 : 16;
		int count = ((v >>> shift) + 1) & 0xFFFF;
		entityData.set(DATA_DOORS, (v & ~(0xFFFF << shift)) | (count << shift));
	}

	private void clientDoors() {
		int v = entityData.get(DATA_DOORS);
		if (lastDoors != Integer.MIN_VALUE && v != lastDoors) {
			for (int i = 0; i < 2; i++) {
				if (((v >>> (i * 16)) & 0xFFFF) == ((lastDoors >>> (i * 16)) & 0xFFFF)) continue;
				int age = doorAge[i];
				if (age < 0 || age >= DOOR_OPEN + DOOR_HOLD + DOOR_CLOSE) doorAge[i] = 0;
				else if (age >= DOOR_OPEN + DOOR_HOLD) doorAge[i] = Math.round(DOOR_OPEN * (1 - (age - DOOR_OPEN - DOOR_HOLD) / (float) DOOR_CLOSE));
				else if (age >= DOOR_OPEN) doorAge[i] = DOOR_OPEN;
			}
		}
		lastDoors = v;
		for (int i = 0; i < 2; i++) {
			if (doorAge[i] >= 0 && ++doorAge[i] > DOOR_OPEN + DOOR_HOLD + DOOR_CLOSE) doorAge[i] = -1;
		}
	}

	/** Client: front door opening angle in degrees for side -1 (left) or +1 (right). */
	public float doorAngle(int side, float pt) {
		int age = doorAge[side < 0 ? 0 : 1];
		if (age < 0 || isWrecked()) return 0;
		float t = age + pt, f;
		if (t < DOOR_OPEN) f = t / DOOR_OPEN;
		else if (t < DOOR_OPEN + DOOR_HOLD) f = 1;
		else f = 1 - Math.min(1, (t - DOOR_OPEN - DOOR_HOLD) / DOOR_CLOSE);
		f = f * f * (3 - 2 * f);
		return f * DOOR_DEG;
	}

	/** Gets a rider out at the door side (F key, shift backstop). */
	public void exitRider(ServerPlayer p) {
		if (p.getVehicle() != this) return;
		p.stopRiding();
		p.setShiftKeyDown(false);
	}

	private static final net.minecraft.resources.Identifier SEAT_SCALE = dev.leonida.LeonidaMod.id("vehicle_seat_scale");

	/** Humanoid mobs (not players) are seated by the hip and scaled to fit the cabin. */
	private static boolean seatsMob(Entity e) {
		return e instanceof LivingEntity && !(e instanceof Player);
	}

	private boolean roofed() {
		return !spec.isBike() && !spec.isBoat();
	}

	/** Hip height above the body origin: above the sills in cars so the folded legs stay inside. */
	private double mobHip(Vec3 seat) {
		return roofed() ? Math.max(seat.y + 0.1, 0.40) : seat.y + 0.1;
	}

	/** Clearance under the nominal roof height: the roof panel itself plus the skin hat layer. */
	private static final double ROOF_PANEL = 0.16;

	/** Model scale that puts the head top just under the roof (2.0 blocks of model above the feet at scale 1). */
	public double mobSeatScale() {
		if (!roofed() || spec.seats().isEmpty()) return 0.9375;
		double hip = mobHip(spec.seats().get(0));
		return Mth.clamp((spec.height() - ROOF_PANEL - hip) / 1.25, 0.62, 0.9375);
	}

	@Override
	public Vec3 getPassengerRidingPosition(Entity passenger) {
		int idx = Math.max(0, getPassengers().indexOf(passenger));
		Vec3 seat = spec.seats().get(Math.min(idx, spec.seatCount() - 1));
		return toWorld(seat);
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction move) {
		// Seats are tuned for players (vehicle attachment 0.6 above the feet, model scaled 0.9375). Mobs such as
		// pedestrians and officers have no such attachment, so they used to ride 0.6 higher with the head through
		// the roof and the legs under the sills. Seat them by the hip instead, scaled so a humanoid model
		// (hip at 0.75 x scale, head top at 2.0 x scale) fits between the floor and the roof.
		if (seatsMob(passenger)) {
			int idx = Math.max(0, getPassengers().indexOf(passenger));
			Vec3 seat = spec.seats().get(Math.min(idx, spec.seatCount() - 1));
			double sc = ((LivingEntity) passenger).getScale();
			Vec3 hip = toWorld(new Vec3(seat.x, mobHip(seat), seat.z));
			move.accept(passenger, hip.x, hip.y - 0.75 * sc, hip.z);
		} else {
			Vec3 pos = getPassengerRidingPosition(passenger);
			Vec3 att = passenger.getVehicleAttachmentPoint(this);
			move.accept(passenger, pos.x - att.x, pos.y - att.y, pos.z - att.z);
		}
		if (yawDelta != 0) {
			passenger.setYRot(passenger.getYRot() + yawDelta);
			passenger.setYHeadRot(passenger.getYHeadRot() + yawDelta);
			if (passenger instanceof LivingEntity le) le.setYBodyRot(getYRot());
		} else if (passenger instanceof LivingEntity le) {
			le.setYBodyRot(getYRot());
		}
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		int idx = Math.max(0, getPassengers().indexOf(passenger));
		Vec3 seat = spec.seats().get(Math.min(idx, spec.seatCount() - 1));
		double side = seat.x <= 0 ? -1 : 1;
		double out = spec.width() / 2 + 0.6;
		Vec3[] tries = {
				new Vec3(side * out, 0, seat.z), new Vec3(-side * out, 0, seat.z),
				new Vec3(0, 0, -spec.length() / 2 - 0.7), new Vec3(0, 0, spec.length() / 2 + 0.7)};
		for (Vec3 t : tries) {
			Vec3 w = toWorld(t);
			for (double dy : new double[] {0, 1, -1}) {
				Vec3 c = w.add(0, dy, 0);
				if (DismountHelper.canDismountTo(level(), c, passenger, Pose.STANDING)) return c;
			}
		}
		return super.getDismountLocationForPassenger(passenger);
	}

	@Override
	protected void addPassenger(Entity passenger) {
		super.addPassenger(passenger);
		if (!level().isClientSide()) swingDoor(getPassengers().indexOf(passenger));
		if (!level().isClientSide() && seatsMob(passenger)) {
			var scale = ((LivingEntity) passenger).getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
			if (scale != null) scale.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
					SEAT_SCALE, mobSeatScale() - 1, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (!level().isClientSide() && passenger instanceof ServerPlayer sp) {
			VehicleModule.onPlayerEnter(sp, this);
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		int seatIdx = getPassengers().indexOf(passenger);
		super.removePassenger(passenger);
		if (!level().isClientSide()) swingDoor(seatIdx);
		if (!level().isClientSide() && passenger instanceof LivingEntity le) {
			var scale = le.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
			if (scale != null) scale.removeModifier(SEAT_SCALE);
		}
		if (!level().isClientSide() && passenger instanceof ServerPlayer sp) {
			VehicleModule.onPlayerExit(sp, this);
		}
	}

	// ---------------------------------------------------------------- interaction and theft

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isPassenger() || isWrecked()) return InteractionResult.PASS;
		if (level().isClientSide()) return InteractionResult.SUCCESS;
		ServerPlayer sp = (ServerPlayer) player;
		touchedByPlayer = true;
		if (isLocked()) {
			if (player.isSecondaryUseActive()) smashWindow(sp);
			else startSlimJim(sp);
			return InteractionResult.SUCCESS;
		}
		if (hasNpcDriver() || (getFirstPassenger() instanceof Mob)) {
			carjack(sp);
			return InteractionResult.SUCCESS;
		}
		if (player.startRiding(this)) return InteractionResult.SUCCESS;
		return InteractionResult.PASS;
	}

	private void startSlimJim(ServerPlayer p) {
		if (picker != null && !picker.equals(p.getUUID())) return;
		if (picker == null) {
			picker = p.getUUID();
			pickTicks = 0;
			playSound(VehicleSounds.SLIMJIM, 0.6f, 1f);
		}
	}

	private void tickSlimJim(ServerLevel level) {
		if (picker == null) return;
		Player p = level.getPlayerByUUID(picker);
		if (p == null || !p.isAlive() || p.isPassenger() || p.distanceToSqr(this) > 4.5 * 4.5 || !isLocked()) {
			picker = null;
			entityData.set(DATA_PICK, 0);
			return;
		}
		pickTicks++;
		if (pickTicks % 30 == 0) playSound(VehicleSounds.SLIMJIM, 0.6f, 0.9f + random.nextFloat() * 0.2f);
		entityData.set(DATA_PICK, pickTicks * 100 / PICK_TICKS);
		if (pickTicks >= PICK_TICKS) {
			picker = null;
			entityData.set(DATA_PICK, 0);
			setLocked(false);
			playSound(VehicleSounds.DOOR, 0.8f, 1.1f);
			if (p instanceof ServerPlayer sp) sp.startRiding(this);
		}
	}

	private void smashWindow(ServerPlayer p) {
		picker = null;
		entityData.set(DATA_PICK, 0);
		setFlag(F_WINDOW_BROKEN, true);
		setLocked(false);
		playSound(VehicleSounds.GLASS, 1.6f, 1f);
		alarmTicks = 20 * 15;
		setFlag(F_ALARM, true);
		if (level() instanceof ServerLevel sl) {
			Vec3 w = toWorld(new Vec3(-spec.width() / 2, 1.0, 0.1));
			sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), w.x, w.y, w.z, 30, 0.3, 0.3, 0.3, 0.1);
		}
		// No auto-enter: the player is sneaking, which would dismount at once. Next use click gets in.
	}

	private void carjack(ServerPlayer p) {
		Vec3 door = toWorld(new Vec3(-spec.width() / 2 - 0.8, 0, spec.seats().get(0).z));
		if (hasNpcDriver()) {
			setNpcDriver(false);
			driver = null;
			VehicleApi.ejectNpcDriver(this, door, p);
		}
		for (Entity e : List.copyOf(getPassengers())) {
			if (e instanceof Mob) {
				e.stopRiding();
				e.setPos(door);
				e.push(right().scale(-0.4).add(0, 0.25, 0));
			}
		}
		playSound(VehicleSounds.DOOR, 1f, 1f);
		traffic = false;
		CrimeEvents.fire(p, Crime.CARJACK, blockPosition());
		owner = p.getUUID();
		p.startRiding(this);
	}

	/** Server: a player became the driver. Fires car theft when the car is not theirs. */
	void onDriverEnter(ServerPlayer p) {
		touchedByPlayer = true;
		traffic = false;
		driver = null;
		setFlag(F_ENGINE, false);
		if (owner == null || !owner.equals(p.getUUID())) {
			owner = p.getUUID();
			CrimeEvents.fire(p, Crime.CAR_THEFT, blockPosition());
		}
		playSound(VehicleSounds.START, 0.8f, 1f);
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (isInvulnerableToBase(source) || isRemoved()) return false;
		if (source.getEntity() instanceof Player p && p.getAbilities().instabuild && source.isDirect() && p.isShiftKeyDown()) {
			discard();
			return true;
		}
		float dmg = amount;
		if (source.isDirect() && source.getEntity() instanceof Player) dmg *= 0.25f;
		if (source.getEntity() instanceof Player) touchedByPlayer = true;
		applyDamage(level, dmg);
		return true;
	}

	public void applyDamage(ServerLevel level, float dmg) {
		if (isWrecked() || dmg <= 0) return;
		setHealth(getHealth() - dmg);
		entityData.set(DATA_HURT, 10);
		if (healthFraction() < 0.12f) setFlag(F_BURNING, true);
		if (getHealth() <= 0) explode(level);
	}

	public void explode(ServerLevel level) {
		if (isWrecked()) return;
		setHealth(0);
		setFlag(F_WRECKED, true);
		setFlag(F_BURNING, true);
		setFlag(F_SIREN, false);
		setFlag(F_HEADLIGHTS, false);
		setNpcDriver(false);
		driver = null;
		wreckTicks = 0;
		ejectPassengers();
		level.explode(this, getX(), getY() + 0.8, getZ(), 2.5f, Level.ExplosionInteraction.NONE);
		setDeltaMovement(getDeltaMovement().add(0, 0.35, 0));
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void tick() {
		if (entityData.get(DATA_HURT) > 0 && !level().isClientSide()) entityData.set(DATA_HURT, entityData.get(DATA_HURT) - 1);
		float yawBefore = getYRot();
		super.tick();
		if (crashCooldown > 0) crashCooldown--;

		if (canSimulateMovement() && level().isClientSide() && !level().hasChunkAt(blockPosition())) {
			// the client has no terrain here yet (slow chunk loading): hold still instead of falling through nothing
			setDeltaMovement(Vec3.ZERO);
		} else if (canSimulateMovement()) {
			VehicleControls c = currentControls();
			if (!spec.isBoat()) unembed();
			simulate(c);
			if (level().isClientSide()) {
				int bits = c.bits();
				if (bits != lastSentControl) {
					lastSentControl = bits;
					clientBridge.sendControls(this, bits);
				}
			} else {
				entityData.set(DATA_CONTROL, c.bits());
			}
		} else {
			setDeltaMovement(Vec3.ZERO);
		}
		yawDelta = Mth.wrapDegrees(getYRot() - yawBefore);
		applyEffectsFromBlocks();

		if (level() instanceof ServerLevel sl) serverTick(sl);
		else clientTick();
	}

	private VehicleControls currentControls() {
		if (isWrecked()) return VehicleControls.BRAKE;
		if (level().isClientSide()) return getControllingPassenger() != null ? clientInput.get() : VehicleControls.NONE;
		if (driver != null) return driver.drive(this);
		if (getFirstPassenger() == null) return VehicleControls.BRAKE; // parked
		return VehicleControls.BRAKE;
	}

	/** Water surface height at the hull, or NaN when not in water. */
	private double waterSurface() {
		BlockPos base = BlockPos.containing(getX(), getY() - 0.6, getZ());
		double top = Double.NaN;
		for (int i = 0; i < 4; i++) {
			BlockPos p = base.above(i);
			FluidState fs = level().getFluidState(p);
			if (fs.is(FluidTags.WATER)) top = p.getY() + fs.getHeight(level(), p);
			else if (!Double.isNaN(top)) break;
		}
		return top;
	}

	private void simulate(VehicleControls c) {
		final double dt = 0.05;
		Vec3 v = getDeltaMovement();
		Vec3 f = forward(), r = right();
		double vF = (v.x * f.x + v.z * f.z) * 20.0;
		double vR = (v.x * r.x + v.z * r.z) * 20.0;
		double vy = v.y;
		float yaw = getYRot();

		float throttle = c.throttle(), steerIn = c.steer();
		boolean hand = c.handbrake();
		double water = waterSurface();
		double sub = Double.isNaN(water) ? -1 : water - getY();
		boolean drowned = !spec.isBoat() && sub > 1.1;
		if (drowned || isWrecked()) { throttle = 0; steerIn = 0; }

		float rate = steerIn == 0 ? 0.22f : (steerIn * steer < 0 ? 0.3f : 0.12f); // counter-steer recentres fast
		steer += Mth.clamp(steerIn - steer, -rate, rate);
		skidding = false;

		if (spec.isBoat()) {
			if (sub > -0.2) {
				double draft = VehicleTypes.isAirboat(spec) ? 0.25 : 0.4;
				vy += (sub - draft + 0.2) * 0.09;
				vy *= 0.72;
				vF = drive(vF, throttle, dt, 1.0);
				vF -= Math.signum(vF) * Math.min(Math.abs(vF), (0.6 + 0.012 * vF * vF) * dt);
				double turn = steer * spec.maxSteerDeg() * 0.11 * Mth.clamp(Math.abs(vF) / 8.0 + 0.25, 0.25, 1.0) * (vF < -0.3 ? -1 : 1);
				yaw += (float) turn;
				double g = spec.grip() * (hand ? 0.4 : 1.0);
				vR -= Math.signum(vR) * Math.min(Math.abs(vR), g * dt);
				if (Math.abs(vF) > 6 && random.nextInt(2) == 0 && level().isClientSide()) wakeParticles(water);
			} else if (onGround()) {
				boolean air = VehicleTypes.isAirboat(spec);
				vF = drive(vF, air ? throttle * 0.4f : 0, dt, 0.4);
				vF *= air ? 0.93 : 0.6;
				vR *= 0.5;
				yaw += (float) (steer * (air ? 2.0 : 0.6) * Mth.clamp(Math.abs(vF) / 4.0, 0, 1));
				vy -= G;
			} else {
				vy -= G;
				vy *= 0.98;
			}
		} else if (onGround() || airTicks < 2) {
			double speedFactor = 1.0 / (1.0 + Math.abs(vF) / 20.0);
			double steerAngle = steer * spec.maxSteerDeg() * speedFactor * Mth.DEG_TO_RAD;
			vF = drive(vF, throttle, dt, 1.0);
			// rolling resistance, engine braking, air drag
			double resist = (throttle == 0 ? 2.0 : 0.4) + 0.0022 * vF * vF;
			vF -= Math.signum(vF) * Math.min(Math.abs(vF), resist * dt);
			if (hand) vF -= Math.signum(vF) * Math.min(Math.abs(vF), 6.5 * dt);
			if (drowned) vF *= 0.8;

			double yawRate = vF * Math.tan(steerAngle) / spec.wheelbase(); // rad/s
			double g = spec.grip() * (hand ? 0.28 : 1.0);
			if (Math.abs(vF) > 1) {
				double maxRate = g * 1.25 / Math.abs(vF);
				yawRate = Mth.clamp(yawRate, -maxRate, maxRate);
			}
			if (hand && Math.abs(vF) > 6) yawRate += steer * 1.7 * Math.signum(vF);
			yaw += (float) (yawRate * dt * Mth.RAD_TO_DEG);

			// re-project world velocity on the new heading: this is where slip comes from
			float yr = yaw * Mth.DEG_TO_RAD;
			Vec3 nf = new Vec3(-Mth.sin(yr), 0, Mth.cos(yr)), nr = new Vec3(-Mth.cos(yr), 0, -Mth.sin(yr));
			Vec3 hv = f.scale(vF).add(r.scale(vR));
			vF = hv.x * nf.x + hv.z * nf.z;
			vR = hv.x * nr.x + hv.z * nr.z;
			if (!hand && Math.abs(vR) <= g * dt * 1.2) {
				double mag = Math.sqrt(vF * vF + 0.7 * vR * vR);
				vF = vF >= 0 ? mag : -mag;
				vR = 0;
			} else {
				vR -= Math.signum(vR) * Math.min(Math.abs(vR), g * dt);
				if (Math.abs(vR) > 2.0) skidding = true;
			}
			if (hand && Math.abs(vF) > 4) skidding = true;
			f = nf;
			r = nr;
			vy = onGround() ? -G : vy - G;
		} else {
			vy -= G;
			vy *= 0.98;
			vF *= 0.998;
		}

		vF = Mth.clamp(vF, -spec.maxReverse() * 1.3, spec.maxSpeed() * 1.4);
		Vec3 hv = f.scale(vF / 20.0).add(r.scale(vR / 20.0));
		Vec3 motion = new Vec3(hv.x, vy, hv.z);

		float oldYaw = getYRot();
		setYRot(yaw);
		if (!spec.isBoat() && probeBlocked(position())) setYRot(oldYaw);
		setDeltaMovement(motion);

		Vec3 before = position();
		double speedBefore = Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 20.0;
		move(MoverType.SELF, motion);
		boolean frontHit = false;
		if (probeBlocked(position())) {
			// full revert: keeping a stepped-down y at the old x/z would leave the box inside the higher block
			setPos(before.x, before.y, before.z);
			frontHit = true;
		}
		Vec3 moved = position().subtract(before);
		double speedAfter = Math.sqrt(moved.x * moved.x + moved.z * moved.z) * 20.0;
		if ((horizontalCollision || frontHit) && speedBefore > 2.0) {
			double impact = Math.max(0, speedBefore - speedAfter);
			// bounce back a little
			Vec3 bounce = new Vec3(-motion.x * 0.25, getDeltaMovement().y, -motion.z * 0.25);
			if (speedAfter > speedBefore * 0.5) bounce = new Vec3(moved.x * 0.9, getDeltaMovement().y, moved.z * 0.9);
			setDeltaMovement(bounce);
			if (impact > 6 && crashCooldown == 0) {
				crashCooldown = 10;
				reportCrash((float) impact);
			}
		} else {
			// step-ups cost a bit of speed (kerbs and block edges)
			if (moved.y > 0.45 && onGround()) setDeltaMovement(getDeltaMovement().multiply(0.82, 1, 0.82));
		}
		airTicks = onGround() ? 0 : airTicks + 1;
	}

	/**
	 * Vanilla collision ignores shapes a box already overlaps, so a vehicle whose lower body ends up inside a road
	 * block (spawned at an approximate lane height, a slab step, a reverted move) would sink through it. Lift it
	 * to the first free height instead (at most 1.25 blocks).
	 */
	private void unembed() {
		AABB box = getBoundingBox();
		AABB low = new AABB(box.minX + 0.15, box.minY + 0.02, box.minZ + 0.15, box.maxX - 0.15, box.minY + 0.9, box.maxZ - 0.15);
		if (level().noBlockCollision(this, low)) return;
		for (double dy = 0.0625; dy <= 1.25; dy += 0.0625) {
			if (level().noBlockCollision(this, low.move(0, dy, 0))) {
				setPos(getX(), getY() + dy, getZ());
				Vec3 d = getDeltaMovement();
				if (d.y < 0) setDeltaMovement(d.x, 0, d.z);
				return;
			}
		}
	}

	/**
	 * Collision top of the ground under a footprint (centre and four corners of a width x width square) near
	 * {@code nearY}: the highest top found scanning from 2 blocks above down to 4 below. Used to place vehicles on
	 * the real surface (slabs, junction pads, decks) instead of an approximate lane or heightmap height.
	 */
	public static double surfaceTop(Level level, double x, double z, double nearY, double width) {
		double h = width / 2 - 0.05, best = Double.NaN;
		double[][] pts = {{0, 0}, {-h, -h}, {h, -h}, {-h, h}, {h, h}};
		for (double[] o : pts) {
			int bx = Mth.floor(x + o[0]), bz = Mth.floor(z + o[1]);
			for (int by = Mth.floor(nearY + 2); by >= Mth.floor(nearY - 4); by--) {
				BlockPos bp = new BlockPos(bx, by, bz);
				var shape = level.getBlockState(bp).getCollisionShape(level, bp);
				if (shape.isEmpty()) continue;
				double top = by + shape.max(net.minecraft.core.Direction.Axis.Y);
				if (Double.isNaN(best) || top > best) best = top;
				break;
			}
		}
		return Double.isNaN(best) ? nearY : best;
	}

	/** Forward speed integration for throttle, brake and reverse. */
	private double drive(double vF, float throttle, double dt, double scale) {
		double acc = spec.accel() * scale, brk = spec.brake();
		if (throttle > 0) {
			if (vF < -0.5) vF = Math.min(0, vF + brk * dt);
			else {
				double k = 1 - (vF / spec.maxSpeed()) * (vF / spec.maxSpeed());
				vF += acc * Math.max(0, k) * throttle * dt;
			}
		} else if (throttle < 0) {
			if (vF > 0.5) vF = Math.max(0, vF - brk * dt);
			else {
				double k = 1 - (vF / spec.maxReverse()) * (vF / spec.maxReverse());
				vF -= acc * 0.7 * Math.max(0, k) * dt;
			}
		}
		return vF;
	}

	/** Front and rear bumper boxes above step height: true if they intersect blocks (walls). */
	private boolean probeBlocked(Vec3 pos) {
		double half = spec.length() / 2 - spec.width() / 2;
		if (half < 0.2) return false;
		double hw = spec.width() / 2 * 0.9;
		float r = getYRot() * Mth.DEG_TO_RAD;
		double fx = -Mth.sin(r), fz = Mth.cos(r);
		double minY = pos.y + spec.stepHeight() + 0.05, maxY = pos.y + Math.max(spec.height(), spec.stepHeight() + 0.3);
		for (int s = -1; s <= 1; s += 2) {
			double cx = pos.x + fx * half * s, cz = pos.z + fz * half * s;
			AABB box = new AABB(cx - hw, minY, cz - hw, cx + hw, maxY, cz + hw);
			if (!level().noBlockCollision(this, box)) return true;
		}
		return false;
	}

	private void reportCrash(float impact) {
		playSound(VehicleSounds.CRASH, Math.min(1.5f, impact / 15f), 0.9f + random.nextFloat() * 0.2f);
		if (level().isClientSide()) clientBridge.sendCrash(this, impact);
		else if (level() instanceof ServerLevel sl) onCrash(sl, impact);
	}

	/** Impact speed of the most recent server-side crash (tests and debugging). */
	public static volatile float lastCrashImpact;

	/** Server: a crash of the given impact speed (m/s) happened. */
	public void onCrash(ServerLevel level, float impact) {
		lastCrashImpact = impact;
		float dmg = (impact - 6) * 2.4f * (float) Math.pow(1500.0 / spec.mass(), 0.3);
		applyDamage(level, dmg);
		// fx module: flying debris at the bumper and any glass in the way shatters. The car may already have
		// bounced back off the obstacle, so search up to two blocks ahead of the bumper for what it hit.
		Vec3 f = forward();
		Vec3 bumper = position().add(f.scale(spec.length() / 2 + 0.3)).add(0, 0.6, 0);
		Vec3 nose = bumper;
		for (double ahead = 0; ahead <= 2.0; ahead += 0.5) {
			BlockPos bp = BlockPos.containing(bumper.add(f.scale(ahead)));
			if (!level.getBlockState(bp).getCollisionShape(level, bp).isEmpty()) { nose = bumper.add(f.scale(ahead)); break; }
		}
		if (impact > 8) dev.leonida.fx.FxApi.debrisBurst(level, nose, impact / 5f, false);
		if (impact > 6) {
			java.util.Set<BlockPos> done = new java.util.HashSet<>();
			Vec3 side = right();
			for (double ahead = 0; ahead <= 2.0; ahead += 0.5)
				for (double lat = -spec.width() / 2; lat <= spec.width() / 2 + 1e-3; lat += spec.width() / 2)
					for (int dy = 0; dy <= Math.ceil(spec.height()); dy++) {
						BlockPos bp = BlockPos.containing(bumper.add(f.scale(ahead)).add(side.scale(lat))).above(dy);
						if (done.add(bp) && dev.leonida.fx.FxApi.isGlass(level.getBlockState(bp))) dev.leonida.fx.FxApi.shatterGlass(level, bp, f.scale(impact / 10.0));
					}
		}
		// shove and damage a vehicle in front of us
		Vec3 dir = getDeltaMovement().horizontalDistanceSqr() > 1e-4 ? forward() : forward();
		AABB area = getBoundingBox().inflate(spec.length() / 2 + 0.6, 0.2, spec.length() / 2 + 0.6);
		for (VehicleEntity other : level.getEntitiesOfClass(VehicleEntity.class, area, o -> o != this && !o.isRemoved())) {
			Vec3 d = other.position().subtract(position());
			double massRatio = spec.mass() / (spec.mass() + other.spec.mass());
			Vec3 push = d.normalize().scale(impact / 20.0 * massRatio * 1.4);
			if (other.canSimulateMovement()) other.setDeltaMovement(other.getDeltaMovement().add(push.x, 0.05, push.z));
			other.applyDamage(level, dmg * (float) massRatio);
			other.touchedByPlayer |= getControllingPassenger() instanceof Player;
			if (getControllingPassenger() instanceof ServerPlayer sp) reckless(sp);
		}
		if (dmg > 2 && getControllingPassenger() instanceof ServerPlayer sp && sp.level().getGameTime() % 2 == 0) {
			sp.hurtServer(level, level.damageSources().flyIntoWall(), Math.max(0, (impact - 14) * 0.5f));
		}
	}

	void reckless(ServerPlayer sp) {
		if (recklessCooldown > 0) return;
		recklessCooldown = 200;
		CrimeEvents.fire(sp, Crime.RECKLESS_DRIVING, blockPosition());
	}

	private void serverTick(ServerLevel level) {
		// Shift exit backstop: vanilla dismounts in Player.rideTick; if a rider is still seated after holding
		// sneak for 3 ticks, let them out here (logged, so tests show which path did it).
		for (Entity e : List.copyOf(getPassengers())) {
			if (!(e instanceof ServerPlayer sp)) continue;
			if (sp.getLastClientInput().shift() || sp.isShiftKeyDown()) {
				if (++shiftTicks >= 3) {
					shiftTicks = 0;
					dev.leonida.LeonidaMod.LOG.info("vehicle: shift exit backstop for {}", spec.id());
					exitRider(sp);
				}
			} else shiftTicks = 0;
		}
		Vec3 pos = position();
		if (lastServerPos != Vec3.ZERO) {
			double inst = Math.sqrt(pos.subtract(lastServerPos).horizontalDistanceSqr()) * 20.0;
			serverSpeed = serverSpeed * 0.6 + inst * 0.4;
		}
		Vec3 vel = pos.subtract(lastServerPos);
		lastServerPos = pos;
		if (recklessCooldown > 0) recklessCooldown--;
		tickSlimJim(level);

		if (alarmTicks > 0 && --alarmTicks == 0) setFlag(F_ALARM, false);

		if (flag(F_BURNING)) {
			if (!isWrecked()) {
				if (tickCount % 10 == 0) applyDamage(level, spec.maxHealth() * 0.02f);
			} else if (++wreckTicks > 20 * 20) {
				setFlag(F_BURNING, false);
			}
		}
		if (isWrecked() && ++wreckTicks > 20 * 90 && level.getNearestPlayer(this, 24) == null) discard();

		// sink a car that drove into deep water
		if (!spec.isBoat() && isInWater() && waterSurface() - getY() > 1.1 && tickCount % 20 == 0) {
			applyDamage(level, 4);
			if (getControllingPassenger() == null) setFlag(F_ENGINE, false);
		}

		if (serverSpeed > 4.5) hitEntities(level, vel);
		if (getControllingPassenger() instanceof ServerPlayer sp && serverSpeed > 9 && tickCount % 10 == 0) {
			var s = dev.leonida.core.api.MapQuery.get().surfaceAt(getBlockX(), getBlockZ());
			if (s == dev.leonida.core.api.MapQuery.Surface.SIDEWALK || s == dev.leonida.core.api.MapQuery.Surface.PARK) reckless(sp);
		}
		if (!hitCooldown.isEmpty() && tickCount % 20 == 0) hitCooldown.entrySet().removeIf(e -> e.getValue() < tickCount);
	}

	private void hitEntities(ServerLevel level, Vec3 vel) {
		double reach = spec.length() / 2 + 0.4;
		AABB area = getBoundingBox().inflate(reach, 0, reach);
		Vec3 f = forward(), r = right();
		LivingEntity drv = getControllingPassenger();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, x -> x.isAlive() && !x.isPassenger() && x != drv)) {
			Vec3 d = e.position().subtract(position());
			double lz = d.x * f.x + d.z * f.z, lx = d.x * r.x + d.z * r.z;
			double halfW = spec.width() / 2 + e.getBbWidth() / 2 + 0.1;
			if (Math.abs(lz) > spec.length() / 2 + e.getBbWidth() / 2 || Math.abs(lx) > halfW) continue;
			if (d.y > spec.height() || d.y < -1.5) continue;
			Integer cd = hitCooldown.get(e.getId());
			if (cd != null && cd > tickCount) continue;
			hitCooldown.put(e.getId(), tickCount + 15);
			float dmg = (float) ((serverSpeed - 3) * 0.9);
			DamageSource src = VehicleModule.vehicleDamage(level, this, drv);
			Vec3 dir = vel.horizontalDistanceSqr() > 1e-6 ? vel.normalize() : f;
			e.hurtServer(level, src, dmg);
			if (e.isDeadOrDying() && !(e instanceof Player)) dev.leonida.fx.FxApi.ragdoll(e, vel.scale(20 * 0.8)); // vel is per tick
			e.push(dir.x * serverSpeed * 0.05, 0.3 + serverSpeed * 0.012, dir.z * serverSpeed * 0.05);
			e.syncVelocity = true;
			if (drv instanceof ServerPlayer sp && !(e instanceof Enemy)) {
				CrimeEvents.fire(sp, Crime.HIT_PEDESTRIAN, blockPosition());
			}
			if (e instanceof Player) touchedByPlayer = true;
		}
	}

	private void clientTick() {
		clientDoors();
		wheelSpinO = wheelSpin;
		bodyPitchO = bodyPitch;
		bodyRollO = bodyRoll;
		suspensionO = suspension;
		visualSteerO = visualSteer;

		Vec3 d = new Vec3(getX() - xo, 0, getZ() - zo);
		Vec3 f = forward();
		double sp = (d.x * f.x + d.z * f.z) * 20.0;
		clientSpeed = (float) sp;
		wheelSpin += (float) (sp / 20.0 / 0.35);

		float targetSteer;
		if (isLocalInstanceAuthoritative()) {
			targetSteer = steer;
			clientSkid = skidding;
		} else {
			VehicleControls c = VehicleControls.fromBits(getControlBits());
			targetSteer = c.steer();
			double lat = Math.abs(d.x * right().x + d.z * right().z) * 20.0;
			clientSkid = lat > 2.5 || (c.handbrake() && Math.abs(sp) > 4);
		}
		visualSteer += (targetSteer - visualSteer) * 0.3f;

		// Body pose from the four wheel contacts against the real collision shapes: the pitch and roll planes go
		// through the highest contact of each axle and side, and the body is lifted so that no wheel ends up
		// below the surface under it (slab steps, junction pads, deck joints). No smoothing that could lag a step.
		float tp = 0, tr = 0;
		float lift = 0;
		if (!spec.isBoat() && onGround()) {
			double hl = spec.wheelbase() / 2, hw = spec.isBike() ? 0 : spec.width() / 2 - 0.2;
			double fl = wheelGround(hl, -hw), fr = wheelGround(hl, hw), rl = wheelGround(-hl, -hw), rr = wheelGround(-hl, hw);
			double hf = Math.max(fl, fr), hb = Math.max(rl, rr), hL = Math.max(fl, rl), hR = Math.max(fr, rr);
			tp = (float) (Math.atan2(hb - hf, spec.wheelbase()) * Mth.RAD_TO_DEG);
			tr = spec.isBike() ? 0 : (float) (Math.atan2(hL - hR, spec.width() - 0.4) * Mth.RAD_TO_DEG);
			tp = Mth.clamp(tp, -20, 20);
			tr = Mth.clamp(tr, -15, 15);
			double pf = Math.sin(-tp * Mth.DEG_TO_RAD) * hl, pr = spec.isBike() ? 0 : Math.sin(tr * Mth.DEG_TO_RAD) * hw;
			double[] g = {fl, fr, rl, rr};
			double[] t = {pf + pr, pf - pr, -pf + pr, -pf - pr};
			double need = -10;
			for (int i = 0; i < 4; i++) need = Math.max(need, g[i] - t[i]);
			lift = (float) Math.max(0, need);
			double lo = 10, hi = -10;
			for (int i = 0; i < 4; i++) {
				double gap = lift + t[i] - g[i];
				lo = Math.min(lo, gap);
				hi = Math.max(hi, gap);
			}
			wheelGapMin = lo;
			wheelGapMax = hi;
		} else {
			wheelGapMin = wheelGapMax = Double.NaN;
		}
		prevClientSpeed = sp;
		if (spec.isBike()) tr = visualSteer * Mth.clamp((float) Math.abs(sp) / 12f, 0, 1) * 22f; // lean about the tyre contact line
		if (spec.isBoat() && isInWater()) {
			tp -= (float) Mth.clamp(sp * 0.25, -2, 6) + Mth.sin(tickCount * 0.15f) * 1.2f;
			tr += Mth.cos(tickCount * 0.11f) * 1.5f;
		}
		bodyPitch = tp;
		bodyRoll = tr;
		suspension = lift;
		lastClientY = getY();

		particles();
	}

	private double prevClientSpeed;

	/** Lowest and highest gap between a rendered wheel contact and the collision top under it (client, for tests). */
	public double wheelGapMin = Double.NaN, wheelGapMax = Double.NaN;

	/** Collision top under a wheel relative to the body origin, limited to what the wheel can actually climb. */
	private double wheelGround(double along, double side) {
		double h = groundAt(along, side);
		if (h < -0.9) return 0; // nothing under this wheel within reach (overhanging a ledge): rest on the body
		return Math.min(h, spec.stepHeight());
	}

	/** Height of the ground under a local point (x forward-offset along the body, z sideways). */
	private double groundAt(double along, double side) {
		Vec3 p = position().add(forward().scale(along)).add(right().scale(side));
		BlockPos bp = BlockPos.containing(p.x, getY() + 0.55, p.z);
		for (int i = 0; i < 3; i++) {
			var shape = level().getBlockState(bp).getCollisionShape(level(), bp);
			if (!shape.isEmpty()) return bp.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) - getY();
			bp = bp.below();
		}
		return -1.0;
	}

	private void particles() {
		Level l = level();
		float hp = healthFraction();
		Vec3 engine = toWorld(new Vec3(0, spec.height() * 0.7, spec.length() / 2 - 0.8));
		if (flag(F_BURNING)) {
			for (int i = 0; i < 2; i++) l.addParticle(ParticleTypes.FLAME, engine.x + rnd(0.5), engine.y + rnd(0.2), engine.z + rnd(0.5), 0, 0.05, 0);
			if (tickCount % 2 == 0) l.addParticle(ParticleTypes.LARGE_SMOKE, engine.x, engine.y + 0.4, engine.z, 0, 0.08, 0);
		} else if (hp < 0.3f) {
			if (tickCount % 2 == 0) l.addParticle(ParticleTypes.LARGE_SMOKE, engine.x + rnd(0.3), engine.y, engine.z + rnd(0.3), 0, 0.06, 0);
		} else if (hp < 0.6f) {
			if (tickCount % 4 == 0) l.addParticle(ParticleTypes.SMOKE, engine.x + rnd(0.3), engine.y, engine.z + rnd(0.3), 0, 0.04, 0);
		}
		if (isWrecked()) {
			if (tickCount % 3 == 0) l.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 1.2, getZ(), 0, 0.05, 0);
			return;
		}
		if (engineRunning() && !spec.isBoat() && tickCount % 6 == 0) {
			Vec3 ex = toWorld(new Vec3(spec.isBike() ? 0.2 : 0.5, 0.3, -spec.length() / 2 - 0.05));
			l.addParticle(ParticleTypes.SMOKE, ex.x, ex.y, ex.z, 0, 0.01, 0);
		}
		if (clientSkid && onGround() && !spec.isBoat()) {
			for (int s = -1; s <= 1; s += 2) {
				Vec3 w = toWorld(new Vec3(s * (spec.width() / 2 - 0.25), 0.1, -spec.wheelbase() / 2));
				if (random.nextInt(2) == 0) l.addParticle(ParticleTypes.CLOUD, w.x, w.y, w.z, rnd(0.02), 0.02, rnd(0.02));
			}
		}
	}

	private void wakeParticles(double water) {
		Vec3 back = toWorld(new Vec3(rnd(spec.width() / 2), 0, -spec.length() / 2));
		level().addParticle(ParticleTypes.SPLASH, back.x, water + 0.1, back.z, rnd(0.1), 0.1, rnd(0.1));
		level().addParticle(ParticleTypes.BUBBLE, back.x, water - 0.2, back.z, 0, 0.05, 0);
	}

	private double rnd(double s) { return (random.nextDouble() - 0.5) * 2 * s; }

	public void playSound(SoundEvent s, float vol, float pitch) {
		if (!isSilent()) level().playSound(null, getX(), getY(), getZ(), s, SoundSource.NEUTRAL, vol, pitch);
	}

	public double serverSpeed() { return serverSpeed; }

	/** Forward speed in m/s from the current motion (valid on the authoritative side). */
	public double forwardSpeed() {
		Vec3 v = getDeltaMovement();
		Vec3 f = forward();
		return (v.x * f.x + v.z * f.z) * 20.0;
	}

	/** Server: set control bits reported by the driving client. */
	public void setClientControls(int bits) { entityData.set(DATA_CONTROL, bits & 31); }

	public void honk() {
		playSound(VehicleSounds.HORN, 2.0f, spec.isBike() ? 1.3f : spec.mass() > 2300 ? 0.85f : 1.0f);
	}
}
