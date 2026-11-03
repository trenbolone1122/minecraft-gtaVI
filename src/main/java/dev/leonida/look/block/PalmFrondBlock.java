package dev.leonida.look.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One long drooping palm frond placed next to a palm crown. {@link #FACING} points away from the crown (the frond's
 * base sits on the opposite face); with {@link #DIAGONAL} it points between facing and its clockwise neighbour, for the
 * diagonal neighbour of the crown (facing north + diagonal = north-east). No collision.
 */
public class PalmFrondBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty DIAGONAL = BooleanProperty.create("diagonal");
	private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 0.75, 1);

	public PalmFrondBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DIAGONAL, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, DIAGONAL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		if (!state.getValue(DIAGONAL)) return state.rotate(mirror.getRotation(state.getValue(FACING)));
		// diagonal NE mirrored: keep it on the mirrored diagonal
		Direction f = state.getValue(FACING), c = f.getClockWise();
		Direction mf = mirror.mirror(f), mc = mirror.mirror(c);
		return state.setValue(FACING, mc.getClockWise() == mf ? mc : mf);
	}
}
