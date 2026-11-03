package dev.leonida.weapon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Gunshot audio beyond the bang itself: a muffled, echoing distant report for players far away
 * (placed in the direction of the shooter), the pump or bolt cycling after a shotgun or sniper shot,
 * and brass casings hitting the ground a moment later.
 */
public final class GunAudio {
	private GunAudio() {}

	/** Distant report: heard from NEAR to FAR blocks. */
	public static final double NEAR = 40, FAR = 320;

	private record Pending(LivingEntity at, SoundEvent sound, float volume, float pitch, long due) {}

	private static final List<Pending> PENDING = new ArrayList<>();

	/** Called by Ballistics right after the bang. {@code cycle} is false when the gun just ran dry. */
	static void afterShot(ServerLevel level, LivingEntity shooter, GunType t, boolean cycle) {
		RandomSource r = shooter.getRandom();
		distant(level, shooter, t, r);
		long now = level.getGameTime();
		switch (t) {
			case SHOTGUN -> {
				if (cycle) queue(shooter, WeaponSounds.PUMP, 0.9f, 1.05f, now + 7);
				queue(shooter, WeaponSounds.casing(r), 0.32f, 0.7f + r.nextFloat() * 0.1f, now + (cycle ? 16 : 12));
			}
			case SNIPER -> {
				if (cycle) queue(shooter, WeaponSounds.BOLT, 0.8f, 1.1f, now + 9);
				queue(shooter, WeaponSounds.casing(r), 0.32f, 0.85f + r.nextFloat() * 0.1f, now + (cycle ? 18 : 12));
			}
			case RPG -> {}
			default -> {
				// automatics only tinkle now and then so bursts do not turn into a wind chime
				if (!t.automatic || r.nextInt(3) == 0)
					queue(shooter, WeaponSounds.casing(r), t == GunType.PISTOL ? 0.28f : 0.24f, 1.0f + r.nextFloat() * 0.25f, now + 9 + r.nextInt(5));
			}
		}
	}

	private static void queue(LivingEntity at, SoundEvent s, float v, float p, long due) {
		PENDING.add(new Pending(at, s, v, p, due));
	}

	private static void distant(ServerLevel level, LivingEntity shooter, GunType t, RandomSource r) {
		Vec3 src = shooter.getEyePosition();
		SoundEvent far = WeaponSounds.FIRE_FAR[t.ordinal()];
		double reach = t == GunType.SNIPER || t == GunType.RPG ? FAR * 1.3 : FAR;
		for (ServerPlayer p : level.players()) {
			if (p == shooter) continue;
			double d = p.getEyePosition().distanceTo(src);
			if (d < NEAR || d > reach) continue;
			Vec3 dir = src.subtract(p.getEyePosition()).normalize();
			Vec3 at = p.getEyePosition().add(dir.scale(10));
			float v = (float) Math.max(0.12, 1.1 * (1 - (d - NEAR) / (reach - NEAR)));
			// blend in around NEAR so the close bang and the far report overlap smoothly
			if (d < NEAR * 1.5) v *= (float) ((d - NEAR) / (NEAR * 0.5));
			p.connection.send(new ClientboundSoundPacket(Holder.direct(far), SoundSource.PLAYERS, at.x, at.y, at.z, v,
					0.92f + r.nextFloat() * 0.1f, r.nextLong()));
		}
	}

	static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (!(p.at.level() instanceof ServerLevel sl) || p.at.isRemoved()) { it.remove(); continue; }
			if (sl.getGameTime() < p.due) continue;
			it.remove();
			sl.playSound(null, p.at.getX(), p.sound == WeaponSounds.PUMP || p.sound == WeaponSounds.BOLT ? p.at.getEyeY() : p.at.getY(), p.at.getZ(),
					p.sound, p.at instanceof net.minecraft.world.entity.player.Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, p.volume, p.pitch);
		}
	}
}
