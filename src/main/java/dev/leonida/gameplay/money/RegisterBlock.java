package dev.leonida.gameplay.money;

import dev.leonida.core.api.CrimeEvents;
import dev.leonida.core.api.CrimeEvents.Crime;
import dev.leonida.gameplay.GameplayRegistry;
import dev.leonida.gameplay.npc.StoreClerk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cash register for store counters. FACING points at the customer side; the clerk it keeps staffed
 * stands on the opposite side. Build or world can place "leonida:gameplay/register" in shops and
 * gas stations; with no clerk around, smash it (hit it) to take the till.
 */
public class RegisterBlock extends HorizontalDirectionalBlock implements EntityBlock {
	public static final BooleanProperty EMPTY = BooleanProperty.create("empty");
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 8, 14);

	public RegisterBlock(Properties p) {
		super(p);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(EMPTY, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
		b.add(FACING, EMPTY);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RegisterBlockEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide() || type != GameplayRegistry.REGISTER_BE) return null;
		return (BlockEntityTicker<T>) (BlockEntityTicker<RegisterBlockEntity>) RegisterBlockEntity::serverTick;
	}

	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || state.getValue(EMPTY)) return;
		boolean clerkHere = !sl.getEntitiesOfClass(StoreClerk.class, new net.minecraft.world.phys.AABB(pos).inflate(5), StoreClerk::isAlive).isEmpty();
		if (clerkHere) return; // the clerk empties it for you, at gunpoint
		sl.setBlock(pos, state.setValue(EMPTY, true), Block.UPDATE_ALL);
		Vec3 front = Vec3.atCenterOf(pos).add(state.getValue(FACING).getUnitVec3().scale(0.7));
		Cash.drop(sl, front, 80 + sl.getRandom().nextInt(23) * 10);
		sl.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1f, 1.3f);
		CrimeEvents.fire(sp, Crime.ROBBERY, pos);
		if (sl.getBlockEntity(pos) instanceof RegisterBlockEntity be) be.markEmptied(sl.getGameTime());
	}
}
