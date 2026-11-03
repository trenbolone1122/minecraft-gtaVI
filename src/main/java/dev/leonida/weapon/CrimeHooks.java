package dev.leonida.weapon;

import dev.leonida.core.api.CrimeEvents;
import dev.leonida.weapon.api.WeaponApi;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;

/** Turns hits and kills by players into CrimeEvents ASSAULT, KILL_CIVILIAN and KILL_COP. */
public final class CrimeHooks {
	private CrimeHooks() {}

	private static final Map<UUID, Long> LAST_ASSAULT = new HashMap<>();

	public static boolean isCop(Entity e) {
		if (WeaponApi.copPredicate != null && WeaponApi.copPredicate.test(e)) return true;
		if (e.entityTags().contains("leonida_cop")) return true;
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
		return path.contains("police") || path.contains("cop") || path.contains("officer") || path.contains("swat");
	}

	/** People who count for crimes: players, villagers, and leonida mobs that are not hostile. */
	public static boolean isPerson(Entity e) {
		if (e instanceof Enemy) return false;
		if (e instanceof Player || e instanceof Npc) return true;
		if (isCop(e)) return true;
		return e instanceof Mob && BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace().equals("leonida")
				&& !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().startsWith("vehicle")
				&& !BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().startsWith("aircraft");
	}

	static void onAssault(ServerPlayer p, LivingEntity victim) {
		if (victim == p || !isPerson(victim)) return;
		long now = p.level().getGameTime();
		Long last = LAST_ASSAULT.get(p.getUUID());
		if (last != null && now - last < 10 && now >= last) return;
		LAST_ASSAULT.put(p.getUUID(), now);
		CrimeEvents.fire(p, CrimeEvents.Crime.ASSAULT, victim.blockPosition());
	}

	static void init() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp && entity instanceof LivingEntity le) onAssault(sp, le);
			return InteractionResult.PASS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer sp) || entity == sp) return;
			if (isCop(entity)) CrimeEvents.fire(sp, CrimeEvents.Crime.KILL_COP, entity.blockPosition());
			else if (isPerson(entity)) CrimeEvents.fire(sp, CrimeEvents.Crime.KILL_CIVILIAN, entity.blockPosition());
		});
	}

	static void forget(UUID id) {
		LAST_ASSAULT.remove(id);
	}
}
