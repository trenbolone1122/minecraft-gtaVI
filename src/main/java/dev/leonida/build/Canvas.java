package dev.leonida.build;

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
 * Block sink clipped to one chunk column. Building code writes in world coordinates and every write
 * outside the clip is dropped, so the same deterministic code can run once per chunk.
 */
abstract class Canvas {
	final int minX, minZ, maxX, maxZ;
	final int minY, maxY;

	Canvas(int minX, int minZ, int maxX, int maxZ, int minY, int maxY) {
		this.minX = minX; this.minZ = minZ; this.maxX = maxX; this.maxZ = maxZ; this.minY = minY; this.maxY = maxY;
	}

	final boolean contains(int x, int y, int z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
	}

	abstract void put(BlockPos pos, BlockState s);

	abstract BlockState read(BlockPos pos);

	/** Write only if the cell is air or replaceable (wall attachments). */
	void putIfFree(BlockPos pos, BlockState s) {
		BlockState cur = read(pos);
		if (!cur.isAir() && !cur.canBeReplaced()) return;
		put(pos, s);
	}

	void finish() {}

	/** Blocks whose final look depends on neighbours (panes, fences, walls, stairs, chests). */
	static boolean needsShapeUpdate(BlockState s) {
		Block b = s.getBlock();
		return b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock || b instanceof FenceGateBlock || b instanceof ChestBlock;
	}

	/** Chunk generation sink (ProtoChunk during NOISE). */
	static final class ChunkCanvas extends Canvas {
		private final ChunkAccess chunk;

		ChunkCanvas(ChunkAccess chunk) {
			super(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ(), chunk.getPos().getMaxBlockX(), chunk.getPos().getMaxBlockZ(),
				chunk.getMinY(), chunk.getMinY() + chunk.getHeight() - 1);
			this.chunk = chunk;
		}

		@Override
		void put(BlockPos pos, BlockState s) {
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
		BlockState read(BlockPos pos) {
			return chunk.getBlockState(pos);
		}
	}

	/** Live level sink used by the debug command and game tests; same chunk clipping as generation. */
	static final class LevelCanvas extends Canvas {
		private final ServerLevel level;
		private final List<BlockPos> shapes = new ArrayList<>();

		LevelCanvas(ServerLevel level, int chunkX, int chunkZ) {
			super(chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15, level.getMinY(), level.getMinY() + level.getHeight() - 1);
			this.level = level;
		}

		@Override
		void put(BlockPos pos, BlockState s) {
			level.setBlock(pos, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			if (needsShapeUpdate(s)) shapes.add(pos.immutable());
		}

		@Override
		BlockState read(BlockPos pos) {
			return level.getBlockState(pos);
		}

		@Override
		void finish() {
			for (BlockPos p : shapes) {
				BlockState s = level.getBlockState(p);
				BlockState u = Block.updateFromNeighbourShapes(s, level, p);
				if (u != s) level.setBlock(p, u, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			shapes.clear();
		}
	}
}
