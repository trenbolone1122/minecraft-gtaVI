package dev.leonida.landmark.gen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Block sink clipped to one chunk column. Landmark code writes in world coordinates and every write outside the clip
 * is dropped, so the same deterministic code runs once per chunk during generation (and in the debug command).
 */
public abstract class Canvas {
	public final int minX, minZ, maxX, maxZ, minY, maxY;

	protected Canvas(int minX, int minZ, int maxX, int maxZ, int minY, int maxY) {
		this.minX = minX;
		this.minZ = minZ;
		this.maxX = maxX;
		this.maxZ = maxZ;
		this.minY = minY;
		this.maxY = maxY;
	}

	public final boolean contains(int x, int y, int z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
	}

	/** True when the box (inclusive, world x/z) touches this canvas. */
	public final boolean intersects(int x0, int z0, int x1, int z1) {
		return x1 >= minX && x0 <= maxX && z1 >= minZ && z0 <= maxZ;
	}

	public final void set(int x, int y, int z, BlockState s) {
		if (contains(x, y, z)) put(new BlockPos(x, y, z), s);
	}

	public final BlockState get(int x, int y, int z) {
		return read(new BlockPos(x, y, z));
	}

	protected abstract void put(BlockPos pos, BlockState s);

	protected abstract BlockState read(BlockPos pos);

	public void finish() {}

	/** Blocks whose final look depends on neighbours (panes, fences, walls, stairs, chests). */
	static boolean needsShapeUpdate(BlockState s) {
		Block b = s.getBlock();
		return b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock || b instanceof FenceGateBlock
				|| b instanceof ChestBlock;
	}

	/** Chunk generation sink (proto chunk). Block entities get a DUMMY tag so vanilla creates them on load. */
	public static final class ChunkCanvas extends Canvas {
		private final ChunkAccess chunk;

		public ChunkCanvas(ChunkAccess chunk) {
			super(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ(), chunk.getPos().getMaxBlockX(), chunk.getPos().getMaxBlockZ(),
					chunk.getMinY(), chunk.getMinY() + chunk.getHeight() - 1);
			this.chunk = chunk;
		}

		@Override
		protected void put(BlockPos pos, BlockState s) {
			BlockState old = chunk.setBlockState(pos, s, 0);
			if (s.hasBlockEntity()) {
				CompoundTag tag = new CompoundTag();
				tag.putInt("x", pos.getX());
				tag.putInt("y", pos.getY());
				tag.putInt("z", pos.getZ());
				tag.putString("id", "DUMMY");
				chunk.setBlockEntityNbt(tag);
			} else if (old != null && old.hasBlockEntity()) {
				chunk.removeBlockEntity(pos);
			}
			if (needsShapeUpdate(s)) chunk.markPosForPostProcessing(pos.immutable());
		}

		@Override
		protected BlockState read(BlockPos pos) {
			return chunk.getBlockState(pos);
		}
	}

	/** Live level sink for the debug command and game tests; same chunk clipping as generation. */
	public static final class LevelCanvas extends Canvas {
		private final ServerLevel level;
		private final List<BlockPos> shapes = new ArrayList<>();

		public LevelCanvas(ServerLevel level, int chunkX, int chunkZ) {
			super(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15, level.getMinY(), level.getMinY() + level.getHeight() - 1);
			this.level = level;
		}

		@Override
		protected void put(BlockPos pos, BlockState s) {
			level.setBlock(pos, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			if (needsShapeUpdate(s)) shapes.add(pos.immutable());
		}

		@Override
		protected BlockState read(BlockPos pos) {
			return level.getBlockState(pos);
		}

		@Override
		public void finish() {
			for (BlockPos p : shapes) {
				BlockState s = level.getBlockState(p);
				BlockState u = Block.updateFromNeighbourShapes(s, level, p);
				if (u != s) level.setBlock(p, u, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			shapes.clear();
		}
	}

	/** Records what a site would write into one chunk without touching the level (server-side generation check). */
	public static final class Recording extends Canvas {
		private final ServerLevel level;
		public final java.util.Map<BlockPos, BlockState> blocks = new java.util.LinkedHashMap<>();

		public Recording(ServerLevel level, int chunkX, int chunkZ) {
			super(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15, level.getMinY(), level.getMinY() + level.getHeight() - 1);
			this.level = level;
		}

		@Override
		protected void put(BlockPos pos, BlockState s) {
			blocks.put(pos.immutable(), s);
		}

		@Override
		protected BlockState read(BlockPos pos) {
			BlockState s = blocks.get(pos);
			return s != null ? s : level.getBlockState(pos);
		}
	}
}
