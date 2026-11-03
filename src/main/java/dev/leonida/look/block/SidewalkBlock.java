package dev.leonida.look.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Sidewalk concrete as a slab (type bottom, top or double = full block). Joints every 1.5 m both ways: on a 1 m grid
 * that repeats every 3 blocks, so {@link #JX} and {@link #JZ} (x mod 3, z mod 3) pick which part of the joint
 * pattern the block shows. Set automatically on hand placement; world gen should use
 * {@code LookBlocks.sidewalk(x, z, type)}.
 */
public class SidewalkBlock extends SlabBlock {
	public static final IntegerProperty JX = IntegerProperty.create("jx", 0, 2);
	public static final IntegerProperty JZ = IntegerProperty.create("jz", 0, 2);

	public SidewalkBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(JX, 0).setValue(JZ, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(JX, JZ);
	}

	public BlockState at(BlockState s, int x, int z) {
		return s.setValue(JX, Math.floorMod(x, 3)).setValue(JZ, Math.floorMod(z, 3));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		BlockState s = super.getStateForPlacement(ctx);
		if (s == null) return null;
		BlockPos p = ctx.getClickedPos();
		return at(s, p.getX(), p.getZ());
	}
}
