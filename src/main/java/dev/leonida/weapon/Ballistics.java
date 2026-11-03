package dev.leonida.weapon;

import dev.leonida.core.api.CrimeEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server side shooting: hitscan with spread, damage falloff, headshots, impacts, rockets, reloads.
 * Hitscan and damage handling adapted from the ideas in Just Enough Guns (GPL, see NOTICE.d/weapon.md).
 */
public final class Ballistics {
	private Ballistics() {}

	private static final Map<UUID, Long> LAST_SHOT = new HashMap<>();
	private static final Map<UUID, Long> LAST_GUNSHOT_CRIME = new HashMap<>();

	public static DamageSource bullet(ServerLevel level, Entity shooter) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(WeaponItems.BULLET), shooter, shooter);
	}

	/** Tries to fire the stack held by shooter in direction dir. Returns true when a round left the barrel. */
	public static boolean tryFire(ServerLevel level, LivingEntity shooter, ItemStack stack, Vec3 dir, boolean aiming) {
		GunType t = WeaponItems.gunOf(stack);
		if (t == null) return false;
		long now = level.getGameTime();
		if (GunItem.reloadEnd(stack) > 0) return false;
		Long last = LAST_SHOT.get(shooter.getUUID());
		if (last != null && now - last < t.fireDelay - 1 && now >= last) return false;
		int clip = GunItem.clip(stack);
		if (clip <= 0) {
			LAST_SHOT.put(shooter.getUUID(), now);
			level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), WeaponSounds.EMPTY, SoundSource.PLAYERS, 0.8f, 1.3f);
			if (shooter instanceof ServerPlayer sp) startReload(sp, stack);
			return false;
		}
		LAST_SHOT.put(shooter.getUUID(), now);
		stack.set(WeaponItems.CLIP, clip - 1);
		fireRound(level, shooter, t, dir, aiming);
		GunAudio.afterShot(level, shooter, t, clip - 1 > 0);
		if (shooter instanceof ServerPlayer sp) {
			CrimeEvents.fire(sp, CrimeEvents.Crime.GUNSHOT, sp.blockPosition());
			LAST_GUNSHOT_CRIME.put(sp.getUUID(), now);
			if (clip - 1 == 0 && reserve(sp, t) > 0) startReload(sp, stack);
		}
		return true;
	}

	static void fireRound(ServerLevel level, LivingEntity shooter, GunType t, Vec3 dir, boolean aiming) {
		RandomSource rnd = shooter.getRandom();
		Vec3 eye = shooter.getEyePosition();
		Vec3 muzzle = muzzle(shooter, dir);
		float spread = aiming ? t.adsSpread : t.hipSpread;
		if (shooter.getDeltaMovement().horizontalDistanceSqr() > 0.01) spread *= 1.5f;
		if (shooter.isCrouching()) spread *= 0.7f;
		SoundSource src = shooter instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
		level.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), WeaponSounds.FIRE[t.ordinal()], src,
				t == GunType.SNIPER || t == GunType.RPG ? 6f : 4f, 0.94f + rnd.nextFloat() * 0.12f);

		if (t == GunType.RPG) {
			Vec3 d = spreadDir(dir, spread, rnd);
			RocketEntity rocket = new RocketEntity(level, shooter, new ItemStack(WeaponItems.AMMO.get(GunType.RPG)));
			rocket.setPos(muzzle.x, muzzle.y, muzzle.z);
			rocket.shoot(d.x, d.y, d.z, 2.2f, 0f);
			level.addFreshEntity(rocket);
			broadcast(level, shooter, new WeaponNet.ShotFx(shooter.getId(), t.ordinal(), muzzle, List.of(), new byte[0]));
			return;
		}

		Map<Entity, float[]> hits = new LinkedHashMap<>();
		List<Vec3> ends = new ArrayList<>();
		byte[] kinds = new byte[t.pellets];
		int impactSounds = 0;
		for (int i = 0; i < t.pellets; i++) {
			Vec3 d = spreadDir(dir, spread, rnd);
			Vec3 end = eye.add(d.scale(t.range));
			BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
			if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();
			EntityHitResult eh = ProjectileUtil.getEntityHitResult(shooter, eye, end, new AABB(eye, end).inflate(1.0),
					e -> !e.isSpectator() && e.isPickable() && e != shooter && !shooter.isPassengerOfSameVehicle(e), eye.distanceToSqr(end));
			if (eh != null) {
				Vec3 at = eh.getLocation();
				Entity e = eh.getEntity();
				float dmg = t.damage * t.falloff(eye.distanceTo(at));
				boolean head = e instanceof LivingEntity le && at.y >= le.getEyeY() - 0.22;
				if (head) dmg *= t.headshotMul;
				float[] acc = hits.computeIfAbsent(e, k -> new float[2]);
				acc[0] += dmg;
				if (head) acc[1] = 1;
				ends.add(at);
				kinds[i] = 2;
			} else if (bh.getType() != HitResult.Type.MISS) {
				ends.add(end);
				kinds[i] = 1;
				if (impactSounds++ < 2) {
					BlockState st = level.getBlockState(bh.getBlockPos());
					level.playSound(null, end.x, end.y, end.z, impactSound(st), SoundSource.BLOCKS, 0.7f, 0.9f + rnd.nextFloat() * 0.2f);
				}
			} else {
				ends.add(end);
				kinds[i] = 0;
			}
		}
		DamageSource src2 = bullet(level, shooter);
		for (Map.Entry<Entity, float[]> h : hits.entrySet()) {
			Entity e = h.getKey();
			boolean wasAlive = e.isAlive();
			e.setInvulnerableTime(0);
			boolean hurt = e.hurtServer(level, src2, h.getValue()[0]);
			if (e instanceof LivingEntity le && hurt) {
				level.playSound(null, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), WeaponSounds.HIT_ENTITY, SoundSource.PLAYERS, 0.8f, 1f);
				if (shooter instanceof ServerPlayer sp) {
					ServerPlayNetworking.send(sp, new WeaponNet.HitConfirm(wasAlive && !le.isAlive(), h.getValue()[1] > 0));
					if (le.isAlive()) CrimeHooks.onAssault(sp, le);
				}
			}
		}
		broadcast(level, shooter, new WeaponNet.ShotFx(shooter.getId(), t.ordinal(), muzzle, ends, kinds));
	}

	static SoundEvent impactSound(BlockState st) {
		SoundType s = st.getSoundType();
		if (s == SoundType.METAL || s == SoundType.IRON || s == SoundType.COPPER || s == SoundType.CHAIN || s == SoundType.ANVIL) return WeaponSounds.HIT_METAL;
		if (s == SoundType.WOOD || s == SoundType.BAMBOO_WOOD || s == SoundType.CHERRY_WOOD || s == SoundType.NETHER_WOOD) return WeaponSounds.HIT_WOOD;
		return WeaponSounds.HIT_STONE;
	}

	/** World position of the muzzle for effects: in front of the eyes, a little right and down (the gun hand). */
	public static Vec3 muzzle(LivingEntity shooter, Vec3 dir) {
		Vec3 up = new Vec3(0, 1, 0);
		Vec3 right = dir.cross(up);
		if (right.lengthSqr() < 1e-4) right = new Vec3(1, 0, 0);
		right = right.normalize();
		Vec3 realUp = right.cross(dir).normalize();
		return shooter.getEyePosition().add(dir.scale(0.9)).add(right.scale(0.28)).add(realUp.scale(-0.2));
	}

	static Vec3 spreadDir(Vec3 dir, float degrees, RandomSource rnd) {
		if (degrees <= 0) return dir.normalize();
		Vec3 d = dir.normalize();
		Vec3 a = Math.abs(d.y) < 0.99 ? d.cross(new Vec3(0, 1, 0)).normalize() : new Vec3(1, 0, 0);
		Vec3 b = a.cross(d).normalize();
		double r = Math.tan(Math.toRadians(degrees)) * Math.sqrt(rnd.nextDouble());
		double ang = rnd.nextDouble() * Math.PI * 2;
		return d.add(a.scale(Math.cos(ang) * r)).add(b.scale(Math.sin(ang) * r)).normalize();
	}

	static void broadcast(ServerLevel level, Entity src, WeaponNet.ShotFx fx) {
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(src) < 160 * 160) ServerPlayNetworking.send(p, fx);
		}
	}

	// ---- ammo and reloading ----

	public static int reserve(Player p, GunType t) {
		var ammo = WeaponItems.AMMO.get(t);
		int n = 0;
		for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.is(ammo)) n += s.getCount();
		return n;
	}

	private static int takeAmmo(Player p, GunType t, int want) {
		var ammo = WeaponItems.AMMO.get(t);
		int got = 0;
		for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
			if (got >= want) break;
			if (s.is(ammo)) {
				int k = Math.min(s.getCount(), want - got);
				s.shrink(k);
				got += k;
			}
		}
		return got;
	}

	public static void startReload(ServerPlayer p, ItemStack stack) {
		GunType t = WeaponItems.gunOf(stack);
		if (t == null || GunItem.reloadEnd(stack) > 0 || GunItem.clip(stack) >= t.magazine) return;
		if (!p.hasInfiniteMaterials() && reserve(p, t) <= 0) return;
		ServerLevel level = p.level();
		stack.set(WeaponItems.RELOAD_END, level.getGameTime() + t.reloadTicks);
		level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), WeaponSounds.reloadStart(t), SoundSource.PLAYERS, 1f, 1f);
	}

	private static final Map<UUID, Integer> LAST_SLOT = new HashMap<>();

	static void tickPlayer(ServerPlayer p) {
		var inv = p.getInventory();
		int sel = inv.getSelectedSlot();
		Integer prev = LAST_SLOT.put(p.getUUID(), sel);
		if (prev != null && prev != sel) {
			// switching weapons cancels a running reload
			for (ItemStack s : inv.getNonEquipmentItems()) if (s.has(WeaponItems.RELOAD_END)) s.remove(WeaponItems.RELOAD_END);
		}
		ItemStack held = p.getMainHandItem();
		long end = GunItem.reloadEnd(held);
		if (end <= 0) return;
		long now = p.level().getGameTime();
		GunType t = WeaponItems.gunOf(held);
		if (t == null) { held.remove(WeaponItems.RELOAD_END); return; }
		if (now < end && end - now <= t.reloadTicks) return;
		int clip = GunItem.clip(held);
		int need = t.magazine - clip;
		int got = p.hasInfiniteMaterials() ? need : takeAmmo(p, t, need);
		held.set(WeaponItems.CLIP, clip + got);
		held.remove(WeaponItems.RELOAD_END);
		p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), WeaponSounds.reloadEnd(t), SoundSource.PLAYERS, 1f, 1f);
	}

	static void forget(UUID id) {
		LAST_SHOT.remove(id);
		LAST_SLOT.remove(id);
		LAST_GUNSHOT_CRIME.remove(id);
	}

	// ---- NPC use ----

	private static final Map<UUID, Long> NPC_RELOAD = new HashMap<>();

	/** Fires the mob's main hand gun at target. Infinite reserve, automatic reloads. */
	public static boolean npcShoot(net.minecraft.world.entity.Mob mob, LivingEntity target, float inaccuracyDeg) {
		if (!(mob.level() instanceof ServerLevel level)) return false;
		ItemStack stack = mob.getMainHandItem();
		GunType t = WeaponItems.gunOf(stack);
		if (t == null) return false;
		long now = level.getGameTime();
		Long r = NPC_RELOAD.get(mob.getUUID());
		if (r != null) {
			if (now < r) return false;
			NPC_RELOAD.remove(mob.getUUID());
			stack.set(WeaponItems.CLIP, t.magazine);
		}
		if (GunItem.clip(stack) <= 0) {
			NPC_RELOAD.put(mob.getUUID(), now + t.reloadTicks);
			level.playSound(null, mob.getX(), mob.getEyeY(), mob.getZ(), WeaponSounds.reloadStart(t), SoundSource.HOSTILE, 1f, 1f);
			return false;
		}
		Vec3 aim = target.getEyePosition().add(0, -0.35, 0).subtract(mob.getEyePosition()).normalize();
		aim = spreadDir(aim, inaccuracyDeg, mob.getRandom());
		mob.lookAt(target, 60f, 60f);
		return tryFire(level, mob, stack, aim, false);
	}
}
