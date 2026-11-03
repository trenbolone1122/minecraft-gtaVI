package dev.leonida.gameplay.money;

import dev.leonida.gameplay.npc.StoreClerk;
import dev.leonida.gameplay.util.Compat;
import dev.leonida.gameplay.util.HudBridge;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Store hold-ups: aim a gun at a clerk to start one. */
public final class Robbery {
	private Robbery() {}

	private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

	/** True if the player looks at the target's chest within maxDegrees and nothing blocks the view. */
	public static boolean isAimingAt(ServerPlayer p, LivingEntity target, double maxDegrees) {
		if (!Compat.isArmed(p)) return false;
		Vec3 eye = p.getEyePosition();
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.65, 0).subtract(eye);
		double dist = to.length();
		if (dist > 14 || dist < 0.01) return false;
		double cos = p.getLookAngle().dot(to.scale(1 / dist));
		// Allow a wider cone up close, where the body fills more of the view.
		double allowed = Math.toRadians(maxDegrees) + Math.atan2(0.4, dist);
		return cos >= Math.cos(allowed) && p.hasLineOfSight(target);
	}

	public static void tick(ServerPlayer p) {
		if (p.isSpectator() || !p.isAlive()) return;
		boolean armed = Compat.isArmed(p);
		for (StoreClerk clerk : p.level().getEntitiesOfClass(StoreClerk.class, p.getBoundingBox().inflate(12), StoreClerk::isAlive)) {
			if (!clerk.canBeRobbed()) continue;
			if (armed && isAimingAt(p, clerk, 10)) {
				clerk.startRobbery(p);
				return;
			}
			if (clerk.distanceToSqr(p) < 7 * 7) {
				long now = p.level().getGameTime();
				Long last = LAST_HINT.get(p.getUUID());
				if (last == null || now - last > 20 * 45) {
					LAST_HINT.put(p.getUUID(), now);
					HudBridge.toast(p, armed ? "Aim your gun at the clerk to hold up the store." : "Equip a gun and aim at the clerk to rob the store.");
				}
			}
		}
	}
}
