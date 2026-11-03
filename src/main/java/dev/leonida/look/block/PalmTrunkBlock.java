package dev.leonida.look.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Leaning palm trunk segment: a vertical post drawn {@code 4 * shift} pixels toward {@link #FACING}. Stack segments with a
 * growing horizontal offset (shift 0..3, then move one column over and restart at 0) for a curved coconut palm trunk.
 */
public class PalmTrunkBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final IntegerProperty SHIFT = IntegerProperty.create("shift", 0, 3);

	private final double half;

	public PalmTrunkBlock(int widthPx, BlockBehaviour.Properties properties) {
		super(properties);
		this.half = widthPx / 2.0;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHIFT, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, SHIFT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		Direction d = state.getValue(FACING);
		double off = 4.0 * state.getValue(SHIFT);
		double cx = 8 + d.getStepX() * off, cz = 8 + d.getStepZ() * off;
		double x0 = Math.max(0, cx - half), x1 = Math.min(16, cx + half);
		double z0 = Math.max(0, cz - half), z1 = Math.min(16, cz + half);
		return Shapes.box(x0 / 16, 0, z0 / 16, x1 / 16, 1, z1 / 16);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
