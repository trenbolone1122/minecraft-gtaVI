package dev.leonida.build;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

/**
 * Tower elevators: a heavy weighted pressure plate on a lodestone is a lift pad. Jump on it to go
 * one floor up, sneak on it to go one floor down (next pad in the same column).
 */
final class Elevator {
	private Elevator() {}

	private static final int RANGE = 96;
	private static final Map<UUID, double[]> LAST = new HashMap<>();   // last y, cooldown ticks

	static void tick(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			double[] st = LAST.computeIfAbsent(p.getUUID(), k -> new double[] {p.getY(), 0, 0});
			if (st[1] > 0) { st[1]--; st[0] = p.getY(); continue; }
			ServerLevel level = p.level();
			BlockPos feet = BlockPos.containing(p.getX(), p.getY() + 0.05, p.getZ());
			boolean onPad = isPad(level, feet);
			boolean wasOnPadBelow = st[2] > 0 && isPad(level, feet.below());
			Integer target = null;
			if (onPad && p.isShiftKeyDown()) target = find(level, feet, -1);
			else if ((onPad || wasOnPadBelow) && p.getY() - st[0] > 0.2 && !p.onGround()) target = find(level, onPad ? feet : feet.below(), +1);
			st[2] = onPad ? 1 : 0;
			st[0] = p.getY();
			if (target != null) {
				p.teleportTo(feet.getX() + 0.5, target + 0.1, feet.getZ() + 0.5);
				p.setDeltaMovement(0, 0, 0);
				level.playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 0.6f, 1.4f);
				st[1] = 10;
				st[0] = target + 0.1;
				st[2] = 1;
			}
		}
		if (LAST.size() > 256) LAST.clear();
	}

	static boolean isPad(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE) && level.getBlockState(pos.below()).is(Blocks.LODESTONE);
	}

	/** Y of the next pad above (dir 1) or below (dir -1) in the same column, or null. */
	static Integer find(ServerLevel level, BlockPos from, int dir) {
		BlockPos.MutableBlockPos m = from.mutable();
		for (int i = 2; i < RANGE; i++) {
			m.setY(from.getY() + dir * i);
			if (m.getY() < level.getMinY() || m.getY() > level.getMaxY()) return null;
			if (isPad(level, m)) return m.getY();
		}
		return null;
	}
}
