package dev.leonida.look.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Unoriented block with a fixed shape (utility pole, AC unit, parapet cap). */
public class ShapedBlock extends Block {
	private final VoxelShape shape;

	public ShapedBlock(VoxelShape shape, BlockBehaviour.Properties properties) {
		super(properties);
		this.shape = shape;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return shape;
	}
}
