package dev.leonida.road.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Asphalt with one road marking painted at any angle (generated textures, tools/roadbake/decals.py).
 * {@code a}: line angle within the quarter turn (16 steps of 5.625 degrees), {@code r}: quarter turns
 * (model y rotation), {@code o}: offset of the line from the block centre along its normal (1/16 m, or
 * 0.05 m pattern phase for zebra), {@code half}: bottom slab instead of a full block.
 */
public class DecalBlock extends Block {
	public static final IntegerProperty A = IntegerProperty.create("a", 0, 15);
	public static final IntegerProperty R = IntegerProperty.create("r", 0, 3);
	public static final BooleanProperty HALF = BooleanProperty.create("half");
	private static final VoxelShape SLAB = Block.box(0, 0, 0, 16, 8, 16);
	private static int pendingOffsets;

	public final IntegerProperty offset;
	public final int offsets;
	/** Offset step in metres. */
	public final double step;

	public DecalBlock(int offsets, double step, BlockBehaviour.Properties p) {
		super(pending(offsets, p));
		this.offsets = offsets;
		this.step = step;
		this.offset = prop(offsets);
		registerDefaultState(stateDefinition.any());
	}

	private static final java.util.Map<Integer, IntegerProperty> PROPS = new java.util.concurrent.ConcurrentHashMap<>();

	private static IntegerProperty prop(int offsets) {
		return PROPS.computeIfAbsent(offsets, n -> IntegerProperty.create("o", 0, n - 1));
	}

	private static BlockBehaviour.Properties pending(int offsets, BlockBehaviour.Properties p) {
		pendingOffsets = offsets;
		return p;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(A, R, HALF, prop(pendingOffsets));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(HALF) ? SLAB : Shapes.block();
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return state.getValue(HALF);
	}
}
