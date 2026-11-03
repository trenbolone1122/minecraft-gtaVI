package dev.leonida.gameplay.npc;

import dev.leonida.core.api.CrimeEvents;
import dev.leonida.core.api.CrimeEvents.Crime;
import dev.leonida.gameplay.money.Cash;
import dev.leonida.gameplay.util.HudBridge;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Convenience store / gas station clerk behind the register. Hold them at gunpoint and they put
 * their hands up and empty the register onto the counter in bundles; look away too long and they
 * duck and call the police. Every robbery is reported once the clerk is free to dial.
 */
public class StoreClerk extends Pedestrian {
	public static final int ROB_TICKS = 160;

	private BlockPos home;
	private UUID robber;
	private int robProgress, lostAim, paidOut, total;
	private long robbedUntil;

	public StoreClerk(EntityType<? extends StoreClerk> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
	}

	@Override
	public SkinFamily skinFamily() {
		return SkinFamily.CLERK;
	}

	@Override
	public boolean isClerk() {
		return true;
	}

	public void setHome(BlockPos p) {
		home = p.immutable();
	}

	public BlockPos home() {
		return home;
	}

	public boolean beingRobbed() {
		return robber != null;
	}

	public boolean canBeRobbed() {
		return robber == null && isAlive() && level().getGameTime() >= robbedUntil && (state == State.WALK || state == State.FILM);
	}

	public void startRobbery(ServerPlayer p) {
		robber = p.getUUID();
		culprit = p.getUUID();
		dangerPos = p.position();
		robProgress = 0;
		lostAim = 0;
		paidOut = 0;
		total = 400 + getRandom().nextInt(13) * 100;
		setState(State.HANDS_UP, 20 * 60);
		playSound(SoundEvents.VILLAGER_NO, 1f, 1.1f);
		CrimeEvents.fire(p, Crime.ROBBERY, blockPosition());
		HudBridge.toast(p, "Keep your aim on the clerk while they empty the register.");
	}

	@Override
	public boolean witness(ServerPlayer p, Crime crime, Vec3 at, boolean wantReport) {
		if (robber != null) {
			// Busy with hands up; dial after the robber leaves.
			pendingReport = Crime.ROBBERY;
			reportDistance = distanceTo(p);
			reportVehicle = false;
			return true;
		}
		return super.witness(p, crime, at, wantReport);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (robber != null) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(robber);
			if (p == null || !p.isAlive() || distanceToSqr(p) > 16 * 16) {
				endRobbery(false);
			} else {
				getLookControl().setLookAt(p);
				getNavigation().stop();
				if (dev.leonida.gameplay.money.Robbery.isAimingAt(p, this, 25)) {
					robProgress++;
					lostAim = Math.max(0, lostAim - 1);
				} else if (++lostAim > 60) {
					endRobbery(false);
				}
				if (robber != null && robProgress > 0 && robProgress % 40 == 0) {
					int chunk = robProgress >= ROB_TICKS ? total - paidOut : total / 4;
					if (chunk > 0) {
						paidOut += chunk;
						Vec3 toward = p.position().subtract(position()).normalize().scale(0.25);
						Cash.drop(level, getEyePosition().add(toward.scale(2)), chunk).setDeltaMovement(toward.x, 0.2, toward.z);
						playSound(SoundEvents.ITEM_PICKUP, 0.8f, 0.7f);
					}
				}
				if (robber != null && robProgress >= ROB_TICKS) {
					HudBridge.mission(p, "STORE ROBBED", "$" + String.format("%,d", total) + " on the counter");
					endRobbery(true);
				}
			}
			return;
		}
		super.customServerAiStep(level);
		if (state == State.WALK && home != null && distanceToSqr(Vec3.atBottomCenterOf(home)) > 2.5 && (getNavigation().isDone() || tickCount % 40 == 0))
			getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.8);
	}

	private void endRobbery(boolean finished) {
		robber = null;
		robbedUntil = level().getGameTime() + 20 * 60 * 10;
		if (pendingReport == null) {
			pendingReport = Crime.ROBBERY;
			reportDistance = 4;
		}
		setState(State.COWER, finished ? 60 : 30);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		if (home != null) out.putIntArray("leonida_home", new int[]{home.getX(), home.getY(), home.getZ()});
		out.putInt("leonida_robbed_until_s", (int) Math.max(0, (robbedUntil - level().getGameTime()) / 20));
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		in.getIntArray("leonida_home").ifPresent(a -> { if (a.length == 3) home = new BlockPos(a[0], a[1], a[2]); });
		robbedUntil = level().getGameTime() + in.getIntOr("leonida_robbed_until_s", 0) * 20L;
	}
}
