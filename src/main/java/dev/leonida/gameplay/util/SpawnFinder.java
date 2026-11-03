package dev.leonida.gameplay.util;

import dev.leonida.core.api.MapQuery;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Finds standable ground positions in loaded chunks around a point. */
public final class SpawnFinder {
	private SpawnFinder() {}

	/** Ground position you can stand on at this column, or null (water, unloaded, no headroom). */
	public static BlockPos ground(ServerLevel level, int x, int z) {
		if (!level.hasChunkAt(x, z)) return null;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos pos = new BlockPos(x, y, z);
		BlockState below = level.getBlockState(pos.below());
		if (!below.isSolidRender() && !below.isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) return null;
		if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.below()).isEmpty()) return null;
		if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) return null;
		return pos;
	}

	/**
	 * Random ground position in a ring around center, preferring the given surfaces. Tries a number
	 * of samples and returns the best scoring one, or null.
	 */
	public static BlockPos ring(ServerLevel level, Vec3 center, double minR, double maxR, int tries, Predicate<BlockPos> accept, MapQuery.Surface... preferred) {
		RandomSource r = level.getRandom();
		BlockPos best = null;
		int bestScore = Integer.MIN_VALUE;
		for (int i = 0; i < tries; i++) {
			double a = r.nextDouble() * Math.PI * 2, d = minR + r.nextDouble() * (maxR - minR);
			int x = (int) Math.floor(center.x + Math.cos(a) * d), z = (int) Math.floor(center.z + Math.sin(a) * d);
			BlockPos g = ground(level, x, z);
			if (g == null || Math.abs(g.getY() - center.y) > 24 || (accept != null && !accept.test(g))) continue;
			MapQuery.Surface s = MapQuery.get().surfaceAt(x, z);
			int score = 0;
			for (int k = 0; k < preferred.length; k++) if (preferred[k] == s) score = 10 - k;
			if (s == MapQuery.Surface.BUILDING || s == MapQuery.Surface.OCEAN || s == MapQuery.Surface.WATER) score -= 20;
			if (score > bestScore) {
				bestScore = score;
				best = g;
				if (preferred.length > 0 && preferred[0] == s) break;
			}
		}
		return best;
	}
}
