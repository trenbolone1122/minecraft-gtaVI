package dev.leonida.look.block;

import java.util.Map;
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
 * A block oriented by a horizontal facing or a horizontal axis, with an optional custom shape. Shapes are given for
 * facing north (attached to the wall on the south side, sticking out to the north) or for axis z.
 *
 * <ul>
 * <li>{@link Mode#WALL}: facing = direction pointing away from the wall (eyebrows, fins, awnings, railings, speed lines).
 *     When placed by hand: the clicked face, else the side facing the player.</li>
 * <li>{@link Mode#TRAVEL}: facing = direction of travel or the side a line sits on (road arrows, edge lines).</li>
 * <li>{@link Mode#AXIS}: axis = x or z, the direction the line, barrier or arm runs.</li>
 * <li>{@link Mode#WALL_TUBE}: WALL facing plus {@link #VERTICAL} (neon tubes run along the wall, flat or upright).</li>
 * </ul>
 */
public class OrientedBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	public static final BooleanProperty VERTICAL = BooleanProperty.create("vertical");

	public enum Mode { WALL, TRAVEL, AXIS, WALL_TUBE }

	private static Mode pendingMode;

	private final Mode mode;
	private final Map<Direction, VoxelShape> shapes;
	private final Map<Direction, VoxelShape> verticalShapes;

	/**
	 * @param shapeNorth shape facing north (null = full cube)
	 * @param verticalShapeNorth WALL_TUBE only: shape when vertical
	 */
	public OrientedBlock(Mode mode, VoxelShape shapeNorth, VoxelShape verticalShapeNorth, BlockBehaviour.Properties properties) {
		super(withMode(mode, properties));
		this.mode = mode;
		this.shapes = shapeNorth == null ? null : Shapes.rotateHorizontal(shapeNorth);
		this.verticalShapes = verticalShapeNorth == null ? null : Shapes.rotateHorizontal(verticalShapeNorth);
		BlockState def = stateDefinition.any();
		if (mode == Mode.AXIS) def = def.setValue(AXIS, Direction.Axis.X);
		else def = def.setValue(FACING, Direction.NORTH);
		if (mode == Mode.WALL_TUBE) def = def.setValue(VERTICAL, false);
		registerDefaultState(def);
	}

	// createBlockStateDefinition runs inside the Block constructor, before fields are set
	private static BlockBehaviour.Properties withMode(Mode mode, BlockBehaviour.Properties p) {
		pendingMode = mode;
		return p;
	}

	public Mode mode() {
		return mode;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		Mode m = pendingMode;
		if (m == Mode.AXIS) builder.add(AXIS);
		else builder.add(FACING);
		if (m == Mode.WALL_TUBE) builder.add(VERTICAL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		BlockState s = defaultBlockState();
		Direction look = ctx.getHorizontalDirection();
		Direction clicked = ctx.getClickedFace();
		switch (mode) {
			case AXIS -> s = s.setValue(AXIS, look.getAxis());
			case TRAVEL -> s = s.setValue(FACING, look);
			case WALL, WALL_TUBE -> s = s.setValue(FACING, clicked.getAxis().isHorizontal() ? clicked : look.getOpposite());
		}
		if (mode == Mode.WALL_TUBE) s = s.setValue(VERTICAL, ctx.getNearestLookingDirection().getAxis() == Direction.Axis.Y);
		return s;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		if (shapes == null) return Shapes.block();
		Direction d = mode == Mode.AXIS ? (state.getValue(AXIS) == Direction.Axis.Z ? Direction.NORTH : Direction.EAST) : state.getValue(FACING);
		if (mode == Mode.WALL_TUBE && state.getValue(VERTICAL) && verticalShapes != null) return verticalShapes.get(d);
		return shapes.get(d);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		if (mode == Mode.AXIS) {
			return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
					? state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : state;
		}
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		if (mode == Mode.AXIS) return state;
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}
}
