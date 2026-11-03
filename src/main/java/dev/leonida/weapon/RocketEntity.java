package dev.leonida.weapon;

import dev.leonida.core.api.CrimeEvents;
import dev.leonida.fx.FxApi;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** RPG rocket: flies almost straight, leaves a smoke trail, explodes on impact without breaking blocks. */
public class RocketEntity extends ThrowableItemProjectile {
	public static final float POWER = 3.6f;

	public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
		super(type, level);
	}

	public RocketEntity(Level level, LivingEntity owner, ItemStack stack) {
		super(WeaponItems.ROCKET, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return WeaponItems.AMMO.get(GunType.RPG);
	}

	@Override
	protected double getDefaultGravity() {
		return 0.004;
	}

	@Override
	protected float getAirDrag() {
		return 1.0f;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			Vec3 v = getDeltaMovement();
			for (int i = 0; i < 3; i++) {
				double k = i / 3.0;
				double x = getX() - v.x * k, y = getY() - v.y * k, z = getZ() - v.z * k;
				// Keep the thick smoke away from the shooter's face for the first ticks.
				level().addParticle(tickCount > 7 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, x, y, z, 0, 0.01, 0);
				if (tickCount > 1) level().addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
			}
		} else if (tickCount > 200) {
			explode();
		}
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide()) explode();
	}

	private void explode() {
		if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
		Vec3 p = position();
		sl.explode(this, p.x, p.y, p.z, POWER, false, Level.ExplosionInteraction.NONE);
		// visual debris from the surrounding blocks (world unchanged) and shattered glass, from the fx module
		FxApi.debrisBurst(sl, p, POWER * 1.5f, false);
		FxApi.shatterGlassAround(sl, p, 5.0f);
		// fireball: flash, several blast cores, fast flames, embers and a dark smoke column
		sl.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFB347), p.x, p.y + 0.5, p.z, 2, 0.2, 0.2, 0.2, 0);
		sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		sl.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.8, p.z, 10, 1.6, 1.0, 1.6, 0);
		sl.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.5, p.z, 140, 0.8, 0.6, 0.8, 0.22);
		sl.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 30, 0.8, 0.4, 0.8, 0.4);
		sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.8, p.z, 40, 1.4, 1.0, 1.4, 0.04);
		sl.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, p.x, p.y + 1.0, p.z, 14, 1.0, 0.6, 1.0, 0.03);
		for (ServerPlayer near : sl.players()) {
			if (near.distanceToSqr(p) < 96 * 96) ServerPlayNetworking.send(near, new WeaponNet.Blast(p, POWER));
		}
		sl.playSound(null, p.x, p.y, p.z, WeaponSounds.EXPLOSION_FAR, SoundSource.BLOCKS, 8f, 0.9f);
		if (getOwner() instanceof ServerPlayer sp) CrimeEvents.fire(sp, CrimeEvents.Crime.EXPLOSION, blockPosition());
		discard();
	}
}
