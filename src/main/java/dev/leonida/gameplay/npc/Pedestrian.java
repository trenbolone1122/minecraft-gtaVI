package dev.leonida.gameplay.npc;

import dev.leonida.core.PlayerState;
import dev.leonida.core.api.CrimeEvents;
import dev.leonida.core.api.CrimeEvents.Crime;
import dev.leonida.core.api.MapQuery;
import dev.leonida.gameplay.GameplayRegistry;
import dev.leonida.gameplay.money.Cash;
import dev.leonida.gameplay.util.Compat;
import dev.leonida.gameplay.wanted.CrimeRules;
import dev.leonida.gameplay.wanted.WantedSystem;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Ambient Leonida local. Walks the sidewalks, and when something happens: runs, cowers, phones the
 * police (the report lands after the call, only if the witness survives it), or pulls out the phone
 * and films the chaos, which feeds the player's LIVE viewer count.
 */
public class Pedestrian extends LeonidaNpc {
	public enum State { WALK, FLEE, CALL, FILM, COWER, HANDS_UP }

	protected State state = State.WALK;
	protected int stateTicks;
	protected Vec3 dangerPos;
	protected UUID culprit;
	protected Crime pendingReport;
	protected double reportDistance;
	protected boolean reportVehicle;
	/** Ambient peds spawned by the spawner may despawn; placed or command-spawned ones stay. */
	public boolean ambient;

	public Pedestrian(EntityType<? extends Pedestrian> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 12).add(Attributes.MOVEMENT_SPEED, 0.23).add(Attributes.FOLLOW_RANGE, 24);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(4, new CrossRoadGoal(this));
		goalSelector.addGoal(5, new SidewalkStrollGoal(this));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6f) {
			@Override
			public boolean canUse() { return state == State.WALK && super.canUse(); }
		});
		goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	@Override
	public SkinFamily skinFamily() {
		return SkinFamily.PEDESTRIAN;
	}

	public boolean isClerk() {
		return false;
	}

	public State state() {
		return state;
	}

	public void setState(State s, int ticks) {
		state = s;
		stateTicks = ticks;
		ItemStack phone = (s == State.CALL || s == State.FILM) ? new ItemStack(GameplayRegistry.PHONE) : ItemStack.EMPTY;
		if (!ItemStack.isSameItem(getItemBySlot(EquipmentSlot.MAINHAND), phone) || phone.isEmpty() != getItemBySlot(EquipmentSlot.MAINHAND).isEmpty())
			setItemSlot(EquipmentSlot.MAINHAND, phone);
		setShiftKeyDown(s == State.COWER);
		setNpcPose(switch (s) {
			case CALL -> POSE_PHONE_CALL;
			case FILM -> POSE_FILMING;
			case HANDS_UP -> POSE_HANDS_UP;
			case COWER -> POSE_COWER;
			default -> POSE_NONE;
		});
		if (s != State.WALK && s != State.FLEE) getNavigation().stop();
	}

	/**
	 * Saw a crime. Returns true if this witness will phone it in.
	 */
	public boolean witness(ServerPlayer p, Crime crime, Vec3 at, boolean wantReport) {
		if (!isAlive()) return false;
		if (state == State.CALL && pendingReport != null) return true;
		culprit = p.getUUID();
		dangerPos = at;
		double dist = distanceTo(p);
		boolean scary = CrimeRules.scary(crime);
		if (wantReport) {
			pendingReport = crime;
			reportDistance = dist;
			reportVehicle = p.getVehicle() != null;
			if (scary && dist < 16) setState(State.FLEE, 50 + getRandom().nextInt(50));
			else setState(State.CALL, 100 + getRandom().nextInt(100));
			return true;
		}
		if (!scary || (dist > 10 && getRandom().nextFloat() < 0.4f)) setState(State.FILM, 240 + getRandom().nextInt(240));
		else if (dist < 6 && getRandom().nextBoolean()) setState(State.COWER, 80 + getRandom().nextInt(60));
		else setState(State.FLEE, 100 + getRandom().nextInt(80));
		return false;
	}

	/** Heard gunfire or an explosion but did not necessarily see who. */
	public void hearDanger(Vec3 at, ServerPlayer p) {
		if (state != State.WALK) return;
		dangerPos = at;
		culprit = p.getUUID();
		float r = getRandom().nextFloat();
		if (r < 0.6f) setState(State.FLEE, 80 + getRandom().nextInt(80));
		else if (r < 0.8f && distanceToSqr(at) > 15 * 15) setState(State.FILM, 200 + getRandom().nextInt(200));
		else setState(State.COWER, 60 + getRandom().nextInt(40));
	}

	protected ServerPlayer culpritPlayer() {
		if (culprit == null || !(level() instanceof ServerLevel sl)) return null;
		return sl.getServer().getPlayerList().getPlayer(culprit);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (stateTicks > 0) stateTicks--;
		ServerPlayer c = culpritPlayer();
		switch (state) {
			case WALK -> { }
			case FLEE -> {
				setSprinting(true);
				if (dangerPos != null && (getNavigation().isDone() || tickCount % 20 == 0)) {
					Vec3 away = LandRandomPos.getPosAway(this, 16, 6, dangerPos);
					if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 1.7);
				}
				if (stateTicks <= 0) {
					setSprinting(false);
					if (pendingReport != null) setState(State.CALL, 80 + getRandom().nextInt(80));
					else setState(State.WALK, 0);
				}
			}
			case CALL -> {
				if (c != null) getLookControl().setLookAt(c);
				if (stateTicks <= 0) {
					if (pendingReport != null && c != null) WantedSystem.report(c, pendingReport, reportDistance, reportVehicle);
					pendingReport = null;
					setState(State.WALK, 0);
				}
			}
			case FILM -> {
				if (c != null) {
					getLookControl().setLookAt(c);
					if (tickCount % 20 == 0 && distanceToSqr(c) < 64 * 64) {
						int wanted = PlayerState.get(c).wanted();
						WantedSystem.addViewers(c, (150 + getRandom().nextInt(900)) * (1 + wanted));
					}
					// Gun in my face: stop filming and run.
					if (distanceToSqr(c) < 5 * 5 && Compat.isArmed(c)) setState(State.FLEE, 80);
				}
				if (stateTicks <= 0) setState(State.WALK, 0);
			}
			case COWER -> {
				if (stateTicks <= 0) setState(pendingReport != null ? State.CALL : State.FLEE, 80);
			}
			case HANDS_UP -> {
				if (c != null) getLookControl().setLookAt(c);
				if (stateTicks <= 0) setState(State.WALK, 0);
			}
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && isAlive()) {
			if (source.getEntity() instanceof ServerPlayer p) {
				// Bare-handed or non-weapon hits: the weapon module reports its own hits.
				if (source.getDirectEntity() == p && !Compat.isGun(p, p.getMainHandItem()) && !isLeonidaWeapon(p.getMainHandItem()))
					CrimeEvents.fire(p, Crime.ASSAULT, blockPosition());
				culprit = p.getUUID();
				dangerPos = p.position();
				if (pendingReport == null) {
					pendingReport = Crime.ASSAULT;
					reportDistance = distanceTo(p);
					reportVehicle = p.getVehicle() != null;
				}
				setState(State.FLEE, 80 + getRandom().nextInt(60));
			} else if (state == State.WALK) {
				dangerPos = position().add(getRandom().nextGaussian(), 0, getRandom().nextGaussian());
				setState(State.FLEE, 60);
			}
		}
		return hurt;
	}

	private static boolean isLeonidaWeapon(ItemStack s) {
		return !s.isEmpty() && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString().startsWith("leonida:weapon/");
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		if (getRandom().nextFloat() < 0.5f) Cash.drop(level, position().add(0, 0.3, 0), 10 + getRandom().nextInt(6) * 10);
	}

	@Override
	public void checkDespawn() {
		if (!ambient || isPersistenceRequired()) return;
		Player p = level().getNearestPlayer(this, -1);
		if (p == null || p.distanceToSqr(this) > 112 * 112) discard();
	}

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putBoolean("leonida_ambient", ambient);
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
		super.readAdditionalSaveData(in);
		ambient = in.getBooleanOr("leonida_ambient", false);
	}

	/** Random walks that prefer sidewalks, then parks and beaches, and avoid roads. */
	static final class SidewalkStrollGoal extends Goal {
		private final Pedestrian ped;
		private Vec3 target;

		SidewalkStrollGoal(Pedestrian ped) {
			this.ped = ped;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (ped.state != State.WALK || ped.getRandom().nextInt(50) != 0) return false;
			target = LandRandomPos.getPos(ped, 14, 4, pos -> switch (MapQuery.get().surfaceAt(pos.getX(), pos.getZ())) {
				case SIDEWALK -> 10;
				case PARK, BEACH -> 5;
				case LAND -> 2;
				case ROAD, RUNWAY, RAIL -> -6;
				case HIGHWAY -> -12;
				default -> -1;
			});
			return target != null;
		}

		@Override
		public boolean canContinueToUse() {
			return ped.state == State.WALK && !ped.getNavigation().isDone();
		}

		@Override
		public void start() {
			ped.getNavigation().moveTo(target.x, target.y, target.z, 0.9);
		}
	}
}
