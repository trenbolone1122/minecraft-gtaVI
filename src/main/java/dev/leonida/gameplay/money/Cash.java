package dev.leonida.gameplay.money;

import dev.leonida.core.PlayerState;
import dev.leonida.gameplay.GameplayRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Cash pickups: a bundle of bills lying in the world. Walking over it adds the money straight to
 * PlayerState (never to the inventory). One item in the stack is worth $10.
 */
public final class Cash {
	private Cash() {}

	public static final int DOLLARS_PER_ITEM = 10;

	/** Drops a bundle worth roughly this many dollars; returns the last entity spawned. */
	public static ItemEntity drop(ServerLevel level, Vec3 pos, int dollars) {
		int items = Math.max(1, dollars / DOLLARS_PER_ITEM);
		ItemEntity last = null;
		while (items > 0) {
			int n = Math.min(items, 64);
			items -= n;
			ItemEntity e = new ItemEntity(level, pos.x, pos.y, pos.z, new ItemStack(GameplayRegistry.CASH, n));
			e.setNeverPickUp();
			e.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * 0.15, 0.2, (level.getRandom().nextDouble() - 0.5) * 0.15);
			level.addFreshEntity(e);
			last = e;
		}
		return last;
	}

	/** Called every few ticks per player: collect nearby cash bundles. */
	public static void collect(ServerPlayer p) {
		if (p.isSpectator() || !p.isAlive()) return;
		ServerLevel level = p.level();
		long gained = 0;
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(1.2, 0.6, 1.2),
				ie -> ie.isAlive() && ie.getItem().is(GameplayRegistry.CASH))) {
			gained += (long) e.getItem().getCount() * DOLLARS_PER_ITEM;
			e.discard();
		}
		if (gained > 0) {
			PlayerState s = PlayerState.get(p);
			PlayerState.set(p, s.withMoney(s.money() + gained));
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 0.6f + level.getRandom().nextFloat() * 0.2f);
		}
	}
}
