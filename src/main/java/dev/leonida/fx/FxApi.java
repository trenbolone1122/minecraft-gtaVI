package dev.leonida.fx;

import dev.leonida.fx.net.DebrisPayload;
import dev.leonida.fx.net.RagdollPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Public physics API of the fx module. Call it from the server thread. All simulation happens on clients
 * (debris and ragdolls are cosmetic), the server only decides what breaks and tells nearby players.
 *
 * <ul>
 *   <li>{@link #debrisBurst(ServerLevel, Vec3, float)}: break weak blocks around a point and throw them as debris.</li>
 *   <li>{@link #debrisBurst(ServerLevel, Vec3, float, boolean)}: same, or only throw pieces of the blocks around
 *   the point without changing the world (vehicle crash scrape, bullet impact).</li>
 *   <li>{@link #debrisFromBlocks(ServerLevel, Vec3, float, Map)}: blocks you already removed yourself.</li>
 *   <li>{@link #shatterGlass(ServerLevel, BlockPos, Vec3)}: break a glass block (or pane) into shards.</li>
 *   <li>{@link #shatterGlassAround(ServerLevel, Vec3, float)}: shatter all glass in a radius (rockets, crashes).</li>
 *   <li>{@link #ragdoll(LivingEntity)} and {@link #ragdoll(LivingEntity, Vec3)}: ragdoll an entity. Every living
 *   entity that dies already ragdolls automatically, use this to add a push or to knock a living NPC over.</li>
 * </ul>
 */
public final class FxApi {
	/** Players farther than this from the effect do not get it. */
	public static final double SEND_RADIUS = 160.0;

	private FxApi() {}

	/** Breaks weak blocks in a radius scaled by {@code power} (TNT is 4) and throws them as tumbling debris. */
	public static int debrisBurst(ServerLevel level, Vec3 pos, float power) {
		return debrisBurst(level, pos, power, true);
	}

	/**
	 * @param breakBlocks true: remove the blocks (no drops) and throw them. false: purely visual, pieces of the
	 *                    surrounding blocks fly off but the world is unchanged.
	 * @return number of blocks used as debris material
	 */
	public static int debrisBurst(ServerLevel level, Vec3 pos, float power, boolean breakBlocks) {
		RandomSource random = level.getRandom();
		float radius = Math.max(1.0F, Math.min(8.0F, power * (breakBlocks ? 0.7F : 0.5F)));
		int r = (int) Math.ceil(radius);
		BlockPos center = BlockPos.containing(pos);
		List<BlockPos> picked = new ArrayList<>();
		List<BlockState> states = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (d > radius * (0.65 + 0.35 * random.nextFloat())) continue;
					m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
					BlockState state = level.getBlockState(m);
					if (!isDebrisMaterial(level, m, state, breakBlocks)) continue;
					if (!breakBlocks && random.nextFloat() > 0.35F) continue;
					picked.add(m.immutable());
					states.add(state);
					if (picked.size() >= DebrisPayload.MAX_BLOCKS) break;
				}
			}
		}
		if (breakBlocks) {
			for (BlockPos p : picked) {
				level.removeBlock(p, false);
			}
		}
		send(level, pos, power, breakBlocks ? 0 : DebrisPayload.FLAG_VISUAL_ONLY, picked, states);
		return picked.size();
	}

	/** Throws debris for blocks the caller already removed. Keys are positions, values the old states. */
	public static void debrisFromBlocks(ServerLevel level, Vec3 center, float power, Map<BlockPos, BlockState> removed) {
		List<BlockPos> ps = new ArrayList<>(removed.size());
		List<BlockState> ss = new ArrayList<>(removed.size());
		for (Map.Entry<BlockPos, BlockState> e : removed.entrySet()) {
			if (e.getValue().isAir()) continue;
			ps.add(e.getKey());
			ss.add(e.getValue());
		}
		send(level, center, power, 0, ps, ss);
	}

	/**
	 * Breaks the glass block at {@code pos} into shards that fly along {@code push} (blocks per second, may be
	 * null). Does nothing for non glass blocks. Returns true when something shattered.
	 */
	public static boolean shatterGlass(ServerLevel level, BlockPos pos, Vec3 push) {
		BlockState state = level.getBlockState(pos);
		if (!isGlass(state)) return false;
		level.removeBlock(pos, false);
		level.playSound(null, pos, state.getSoundType().getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		Vec3 c = Vec3.atCenterOf(pos);
		Vec3 from = push == null ? c.add(0, 0.1, 0) : c.subtract(push.normalize().scale(0.6));
		float power = push == null ? 1.0F : (float) Math.min(6.0, push.length() / 3.0);
		send(level, from, power, DebrisPayload.FLAG_SHATTER, List.of(pos), List.of(state));
		return true;
	}

	/**
	 * Shatters every glass block within {@code radius} of {@code center}; shards fly outward from the center.
	 * Use it for rockets, grenades and vehicle crashes even when your explosion does not break blocks.
	 * Returns the number of blocks shattered (capped at {@link DebrisPayload#MAX_BLOCKS}).
	 */
	public static int shatterGlassAround(ServerLevel level, Vec3 center, float radius) {
		int r = (int) Math.ceil(Math.min(12.0F, radius));
		BlockPos c = BlockPos.containing(center);
		List<BlockPos> positions = new java.util.ArrayList<>();
		List<BlockState> states = new java.util.ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			if (positions.size() >= DebrisPayload.MAX_BLOCKS) break;
			if (Vec3.atCenterOf(pos).distanceToSqr(center) > radius * radius) continue;
			BlockState state = level.getBlockState(pos);
			if (!isGlass(state)) continue;
			positions.add(pos.immutable());
			states.add(state);
		}
		if (positions.isEmpty()) return 0;
		for (BlockPos pos : positions) level.removeBlock(pos, false);
		level.playSound(null, c, states.get(0).getSoundType().getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS, 2.0F, 0.8F);
		send(level, center, Math.min(8.0F, 2.0F + radius), DebrisPayload.FLAG_SHATTER, positions, states);
		return positions.size();
	}

	/** Ragdolls the entity with no extra push. */
	public static void ragdoll(LivingEntity entity) {
		ragdoll(entity, Vec3.ZERO);
	}

	/**
	 * Ragdolls the entity. If it is dead, the body ragdolls until it despawns. If it is alive, clients show a
	 * ragdoll for about two seconds in place of the model (the caller should stop its AI meanwhile).
	 *
	 * @param impulse velocity in blocks per second added to the body (for example away from a car or a blast)
	 */
	public static void ragdoll(LivingEntity entity, Vec3 impulse) {
		if (!(entity.level() instanceof ServerLevel level)) return;
		int ticks = entity.isDeadOrDying() ? 0 : 40;
		RagdollPayload payload = new RagdollPayload(entity.getId(), (float) impulse.x, (float) impulse.y, (float) impulse.z, ticks);
		for (ServerPlayer p : PlayerLookup.around(level, entity.position(), SEND_RADIUS)) {
			if (ServerPlayNetworking.canSend(p, RagdollPayload.TYPE)) ServerPlayNetworking.send(p, payload);
		}
	}

	/** True for glass blocks and panes (anything with the glass sound). */
	public static boolean isGlass(BlockState state) {
		return state.getSoundType() == SoundType.GLASS;
	}

	static boolean isDebrisMaterial(ServerLevel level, BlockPos pos, BlockState state, boolean mustBeBreakable) {
		if (state.isAir() || !state.getFluidState().isEmpty() && state.canBeReplaced()) return false;
		if (state.hasBlockEntity()) return false;
		if (!mustBeBreakable) return state.isSolidRender() || isGlass(state);
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0F && hardness < 20.0F;
	}

	static void send(ServerLevel level, Vec3 center, float power, int flags, List<BlockPos> ps, List<BlockState> ss) {
		if (ps.isEmpty()) return;
		int n = Math.min(ps.size(), DebrisPayload.MAX_BLOCKS);
		long[] pos = new long[n];
		int[] st = new int[n];
		for (int i = 0; i < n; i++) {
			pos[i] = ps.get(i).asLong();
			st[i] = Block.getId(ss.get(i));
		}
		DebrisPayload payload = new DebrisPayload(center.x, center.y, center.z, power, flags, pos, st);
		for (ServerPlayer p : PlayerLookup.around(level, center, SEND_RADIUS)) {
			if (ServerPlayNetworking.canSend(p, DebrisPayload.TYPE)) ServerPlayNetworking.send(p, payload);
		}
	}
}
