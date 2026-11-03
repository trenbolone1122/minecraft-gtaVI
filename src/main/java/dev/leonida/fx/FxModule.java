package dev.leonida.fx;

import dev.leonida.fx.net.DebrisPayload;
import dev.leonida.fx.net.RagdollPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server and common setup for the fx module. Owned by the fx agent. */
public final class FxModule {
	public static final Logger LOG = LoggerFactory.getLogger("leonida/fx");

	private FxModule() {}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(DebrisPayload.TYPE, DebrisPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RagdollPayload.TYPE, RagdollPayload.CODEC);
		ServerLivingEntityEvents.AFTER_DEATH.register(FxModule::onDeath);
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		FxApi.ragdoll(entity, deathImpulse(entity, source));
	}

	/** Push a body gets when it dies: away from whatever killed it, harder for explosions. */
	static Vec3 deathImpulse(LivingEntity entity, DamageSource source) {
		Vec3 from = source.getSourcePosition();
		if (from == null && source.getEntity() != null) from = source.getEntity().position();
		if (from == null) return Vec3.ZERO;
		Vec3 d = entity.position().add(0, entity.getBbHeight() * 0.5, 0).subtract(from);
		double dist = d.length();
		if (dist < 1.0E-3) return new Vec3(0, 2, 0);
		boolean blast = source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
		double strength = blast ? Math.max(4.0, 14.0 - dist * 1.5) : 3.0;
		Vec3 dir = d.scale(1.0 / dist);
		return new Vec3(dir.x * strength, Math.max(dir.y * strength, 0) + (blast ? 4.0 : 1.0), dir.z * strength);
	}

	/** Called from the explosion mixin before the blocks are destroyed. */
	public static void onExplosionBlocks(ServerLevel level, Vec3 center, float radius, List<BlockPos> targets) {
		if (targets.isEmpty()) return;
		List<BlockPos> ps = new ArrayList<>(Math.min(targets.size(), DebrisPayload.MAX_BLOCKS));
		List<BlockState> ss = new ArrayList<>(ps.size());
		for (BlockPos p : targets) {
			BlockState s = level.getBlockState(p);
			if (s.isAir() || !s.getFluidState().isEmpty() && s.canBeReplaced()) continue;
			ps.add(p);
			ss.add(s);
			if (ps.size() >= DebrisPayload.MAX_BLOCKS) break;
		}
		FxApi.send(level, center, radius, 0, ps, ss);
	}
}
