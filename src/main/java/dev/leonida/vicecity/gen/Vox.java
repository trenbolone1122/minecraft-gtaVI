package dev.leonida.vicecity.gen;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Sparse world-space block buffer for one landmark site, bucketed by chunk. A site is built into a Vox once
 * (deterministically), then every chunk generation call copies only its own bucket, so writes are always clipped to
 * the chunk being generated.
 */
public final class Vox {
	private final Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<BlockState>> chunks = new Long2ObjectOpenHashMap<>();
	public int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
	private long count;

	private static int key(int x, int y, int z) {
		return ((y + 2048) << 8) | ((z & 15) << 4) | (x & 15);
	}

	public void set(int x, int y, int z, BlockState s) {
		if (s == null) return;
		long ck = ChunkPos.pack(x >> 4, z >> 4);
		Int2ObjectOpenHashMap<BlockState> m = chunks.get(ck);
		if (m == null) chunks.put(ck, m = new Int2ObjectOpenHashMap<>());
		if (m.put(key(x, y, z), s) == null) count++;
		if (x < minX) minX = x;
		if (x > maxX) maxX = x;
		if (z < minZ) minZ = z;
		if (z > maxZ) maxZ = z;
		if (y < minY) minY = y;
		if (y > maxY) maxY = y;
	}

	public BlockState get(int x, int y, int z) {
		Int2ObjectOpenHashMap<BlockState> m = chunks.get(ChunkPos.pack(x >> 4, z >> 4));
		return m == null ? null : m.get(key(x, y, z));
	}

	public boolean touches(int chunkX, int chunkZ) {
		return chunks.containsKey(ChunkPos.pack(chunkX, chunkZ));
	}

	public long count() {
		return count;
	}

	public Iterable<Long> chunkKeys() {
		return chunks.keySet();
	}

	/** Visit every block (world x, y, z). Used by the offline checks. */
	public interface Visitor {
		void visit(int x, int y, int z, BlockState s);
	}

	public void each(Visitor v) {
		for (var e : chunks.long2ObjectEntrySet()) {
			ChunkPos cp = ChunkPos.unpack(e.getLongKey());
			int bx = cp.x() << 4, bz = cp.z() << 4;
			for (var f : e.getValue().int2ObjectEntrySet()) {
				int k = f.getIntKey();
				v.visit(bx + (k & 15), (k >>> 8) - 2048, bz + ((k >> 4) & 15), f.getValue());
			}
		}
	}

	static boolean needsShape(BlockState s) {
		Block b = s.getBlock();
		return b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock;
	}

	/** Copy this chunk's bucket into a generating chunk. */
	public void apply(ChunkAccess chunk) {
		ChunkPos cp = chunk.getPos();
		Int2ObjectOpenHashMap<BlockState> m = chunks.get(ChunkPos.pack(cp.x(), cp.z()));
		if (m == null) return;
		int bx = cp.getMinBlockX(), bz = cp.getMinBlockZ();
		int minY = chunk.getMinY(), maxY = minY + chunk.getHeight() - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Int2ObjectMap.Entry<BlockState> e : m.int2ObjectEntrySet()) {
			int k = e.getIntKey();
			int y = (k >> 8) - 2048;
			if (y < minY || y > maxY) continue;
			pos.set(bx + (k & 15), y, bz + ((k >> 4) & 15));
			BlockState s = e.getValue();
			chunk.setBlockState(pos, s, 0);
			if (needsShape(s)) chunk.markPosForPostProcessing(pos.immutable());
		}
	}

	/** Copy everything into a live level (debug command and game tests). */
	public void applyLevel(ServerLevel level) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		java.util.List<BlockPos> shapes = new java.util.ArrayList<>();
		int minY = level.getMinY(), maxY = minY + level.getHeight() - 1;
		for (Long2ObjectMap.Entry<Int2ObjectOpenHashMap<BlockState>> c : chunks.long2ObjectEntrySet()) {
			long ck = c.getLongKey();
			int bx = ChunkPos.getX(ck) << 4, bz = ChunkPos.getZ(ck) << 4;
			for (Int2ObjectMap.Entry<BlockState> e : c.getValue().int2ObjectEntrySet()) {
				int k = e.getIntKey();
				int y = (k >> 8) - 2048;
				if (y < minY || y > maxY) continue;
				pos.set(bx + (k & 15), y, bz + ((k >> 4) & 15));
				BlockState s = e.getValue();
				level.setBlock(pos, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				if (needsShape(s)) shapes.add(pos.immutable());
			}
		}
		for (BlockPos p : shapes) {
			BlockState s = level.getBlockState(p);
			BlockState u = Block.updateFromNeighbourShapes(s, level, p);
			if (u != s) level.setBlock(p, u, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
	}
}
