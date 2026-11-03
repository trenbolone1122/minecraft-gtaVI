package dev.leonida.gameplay.npc;

import dev.leonida.core.PlayerState;
import dev.leonida.core.api.CrimeEvents;
import dev.leonida.core.api.CrimeEvents.Crime;
import dev.leonida.gameplay.money.Cash;
import dev.leonida.gameplay.util.Compat;
import dev.leonida.gameplay.wanted.WantedSystem;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Leonida police. Patrol officers try to cuff low-level suspects and shoot once things turn lethal,
 * SWAT (4 stars) and the heavy response (6 stars) carry rifles and shotguns, take more punishment
 * and shoot straighter. While the wanted system is in its search phase they sweep the search area.
 * Behaviour adapted in spirit from Police NPC (MIT); written fresh for the wanted system.
 */
public class PoliceOfficer extends LeonidaNpc {
	public static final int PATROL = 0, SWAT = 1, HEAVY = 2;
	/** VCPD (white and blue, inside Vice City) or Leonard County Sheriff (green, outside the city). */
	public static final int VCPD = 0, SHERIFF = 1;
	private static final EntityDataAccessor<Integer> RANK = SynchedEntityData.defineId(PoliceOfficer.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> AGENCY = SynchedEntityData.defineId(PoliceOfficer.class, EntityDataSerializers.INT);

	private UUID target;
	private int shootCooldown = 20, burst, idleTicks, fireWindow;
	private Vec3 sweepTarget;

	public PoliceOfficer(EntityType<? extends PoliceOfficer> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.MOVEMENT_SPEED, 0.31).add(Attributes.FOLLOW_RANGE, 64).add(Attributes.ARMOR, 2);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(RANK, PATROL);
		b.define(AGENCY, VCPD);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
	}

	public int rank() {
		return entityData.get(RANK);
	}

	public void setRank(int r) {
		entityData.set(RANK, r);
		double hp = r == PATROL ? 24 : r == SWAT ? 40 : 60;
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		getAttribute(Attributes.ARMOR).setBaseValue(r == PATROL ? 2 : 8);
		setHealth((float) hp);
		String gun = r == PATROL ? (getRandom().nextInt(4) == 0 ? "shotgun" : "pistol") : r == SWAT ? (getRandom().nextBoolean() ? "smg" : "rifle") : (getRandom().nextBoolean() ? "shotgun" : "rifle");
		if (!Compat.weaponArm(this, gun)) setItemSlot(EquipmentSlot.MAINHAND, Compat.item("leonida:weapon/" + gun));
	}

	public int agency() {
		return entityData.get(AGENCY);
	}

	public void setAgency(int a) {
		entityData.set(AGENCY, a);
	}

	@Override
	public SkinFamily skinFamily() {
		return rank() == PATROL ? SkinFamily.POLICE : SkinFamily.SWAT;
	}

	public void setTargetPlayer(ServerPlayer p) {
		target = p == null ? null : p.getUUID();
		idleTicks = 0;
	}

	public boolean targets(ServerPlayer p) {
		return target != null && target.equals(p.getUUID());
	}

	public ServerPlayer targetPlayer() {
		if (target == null || !(level() instanceof ServerLevel sl)) return null;
		return sl.getServer().getPlayerList().getPlayer(target);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		ServerPlayer p = targetPlayer();
		if (p != null && (!p.isAlive() || p.level() != level || p.isSpectator() || PlayerState.get(p).wanted() <= 0 || distanceToSqr(p) > 200 * 200)) {
			target = null;
			p = null;
		}
		if (isPassenger()) {
			// Riding in a police car: watch the suspect, the vehicle module does the driving.
			if (p != null) getLookControl().setLookAt(p, 30f, 30f);
			return;
		}
		if (p == null) {
			standDownStep(level);
			return;
		}
		idleTicks = 0;
		if (dev.leonida.gameplay.police.PoliceDispatch.isBusted(p)) {
			getNavigation().stop();
			getLookControl().setLookAt(p);
			setNpcPose(POSE_AIMING);
			return;
		}
		if (WantedSystem.isSearching(p)) {
			sweepStep(level, p);
			return;
		}
		double dist = distanceTo(p);
		boolean los = hasLineOfSight(p);
		boolean lethal = WantedSystem.lethalForce(p);
		getLookControl().setLookAt(p, 30, 30);
		if (!lethal) {
			// Cuff them: run in, gun drawn when close.
			setNpcPose(dist < 10 && los ? POSE_AIMING : POSE_NONE);
			setSprinting(dist > 4);
			if (dist > 1.2) getNavigation().moveTo(p, dist > 6 ? 1.3 : 1.0);
			else getNavigation().stop();
			return;
		}
		double range = rank() == PATROL ? 30 : 38;
		if (los && dist < range) {
			setNpcPose(POSE_AIMING);
			setSprinting(false);
			if (dist > (rank() == PATROL ? 14 : 18)) getNavigation().moveTo(p, 1.0);
			else if (dist < 5) {
				Vec3 away = LandRandomPos.getPosAway(this, 6, 3, p.position());
				if (away != null && tickCount % 20 == 0) getNavigation().moveTo(away.x, away.y, away.z, 1.0);
			} else getNavigation().stop();
			if (Compat.holdsWeaponGun(this)) fireWithWeaponModule(p);
			else if (--shootCooldown <= 0) shoot(level, p, dist);
		} else {
			setNpcPose(POSE_NONE);
			setSprinting(true);
			if (tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(p, 1.3);
		}
	}

	private void sweepStep(ServerLevel level, ServerPlayer p) {
		setNpcPose(POSE_NONE);
		setSprinting(false);
		Vec3 c = WantedSystem.tracker(p).searchCenter;
		if (sweepTarget == null || getNavigation().isDone() || tickCount % 160 == 0) {
			double r = Math.min(WantedSystem.searchRadius(PlayerState.get(p).wanted()), 40);
			double a = getRandom().nextDouble() * Math.PI * 2, d = getRandom().nextDouble() * r;
			sweepTarget = c.add(Math.cos(a) * d, 0, Math.sin(a) * d);
			getNavigation().moveTo(sweepTarget.x, sweepTarget.y, sweepTarget.z, 1.0);
		}
	}

	private void standDownStep(ServerLevel level) {
		setNpcPose(POSE_NONE);
		setSprinting(false);
		idleTicks++;
		Player near = level.getNearestPlayer(this, -1);
		if (near == null || (idleTicks > 200 && near.distanceToSqr(this) > 40 * 40) || idleTicks > 1200) {
			discard();
			return;
		}
		if (getNavigation().isDone() && idleTicks % 40 == 1) {
			Vec3 away = LandRandomPos.getPosAway(this, 24, 6, near.position());
			if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 0.9);
		}
	}

	/**
	 * The weapon module paces fire rate and reloads itself and wants a call every tick while
	 * shooting; we add the rhythm: patrol fire in short volleys with pauses, SWAT longer bursts.
	 */
	private void fireWithWeaponModule(ServerPlayer p) {
		int r = rank();
		int on = r == PATROL ? 16 : r == SWAT ? 28 : 40, off = r == PATROL ? 34 : r == SWAT ? 26 : 16;
		fireWindow = (fireWindow + 1) % (on + off);
		if (fireWindow >= on) return;
		float spread = r == PATROL ? 6f : 4f;
		if (p.isSprinting()) spread += 2f;
		if (p.getVehicle() != null) spread += 2f;
		Compat.weaponNpcShoot(this, p, spread);
	}

	private void shoot(ServerLevel level, ServerPlayer p, double dist) {
		int r = rank();
		// Built-in hitscan when the weapon module is not present.
		if (r == SWAT) {
			if (++burst < 3) shootCooldown = 4;
			else { burst = 0; shootCooldown = 24 + getRandom().nextInt(16); }
		} else if (r == HEAVY) shootCooldown = 10 + getRandom().nextInt(8);
		else shootCooldown = 22 + getRandom().nextInt(18);
		double acc = (r == PATROL ? 0.5 : r == SWAT ? 0.6 : 0.7) * Math.max(0.15, 1 - dist / 45);
		if (p.isSprinting()) acc *= 0.7;
		if (p.getVehicle() != null) acc *= 0.6;
		if (p.isCrouching()) acc *= 0.8;
		Vec3 from = getEyePosition();
		Vec3 aim = p.position().add(0, p.getBbHeight() * 0.6, 0);
		boolean hit = getRandom().nextDouble() < acc && WantedSystem.clearLine(level, from, aim, this);
		Vec3 end = hit ? aim : aim.add(getRandom().nextGaussian() * 1.5, getRandom().nextGaussian() * 1.0, getRandom().nextGaussian() * 1.5);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 1.2f, 1.6f + getRandom().nextFloat() * 0.3f);
		Vec3 dir = end.subtract(from);
		double len = dir.length();
		Vec3 step = dir.scale(1 / Math.max(len, 0.01));
		Vec3 muzzle = from.add(step.scale(0.8));
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.02, 0.02, 0.02, 0.01);
		for (double t = 1.5; t < len; t += 1.5) {
			Vec3 q = from.add(step.scale(t));
			level.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
		}
		if (hit) {
			float dmg = r == PATROL ? 3f : r == SWAT ? 4f : 5f;
			if (p.getVehicle() != null) dmg *= 0.6f;
			p.hurtServer(level, damageSources().mobAttack(this), dmg);
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (source.getEntity() instanceof PoliceOfficer) return false;
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() instanceof ServerPlayer p && !p.isCreative()) {
			WantedSystem.markLethal(p);
			if (source.getDirectEntity() == p && !Compat.isGun(p, p.getMainHandItem())
					&& !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString().startsWith("leonida:weapon/"))
				CrimeEvents.fire(p, Crime.ASSAULT, blockPosition());
			if (target == null) target = p.getUUID();
		}
		return hurt;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		if (killedByPlayer) Cash.drop(level, position().add(0, 0.3, 0), 20 + getRandom().nextInt(9) * 10);
	}

	@Override
	public void checkDespawn() {
		Player p = level().getNearestPlayer(this, -1);
		if (p == null || p.distanceToSqr(this) > 220 * 220) discard();
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putInt("leonida_rank", rank());
		out.putInt("leonida_agency", agency());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		entityData.set(RANK, in.getIntOr("leonida_rank", PATROL));
		entityData.set(AGENCY, in.getIntOr("leonida_agency", VCPD));
	}
}
