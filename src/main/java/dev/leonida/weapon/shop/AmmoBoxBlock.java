package dev.leonida.weapon.shop;

import dev.leonida.weapon.GunType;
import dev.leonida.weapon.WeaponItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Green ammo crate: use it for two magazines for the gun in hand (pistol ammo otherwise). Refills after five minutes. */
public class AmmoBoxBlock extends HorizontalDirectionalBlock {
	public static final BooleanProperty FULL = BooleanProperty.create("full");
	public static final int REFILL_TICKS = 20 * 60 * 5;
	private static final VoxelShape X = Block.box(1, 0, 4, 15, 8, 12);
	private static final VoxelShape Z = Block.box(4, 0, 1, 12, 8, 15);

	public AmmoBoxBlock(Properties p) {
		super(p);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FULL, true));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
		b.add(FACING, FULL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(FACING).getAxis() == Direction.Axis.Z ? X : Z;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
		if (!state.getValue(FULL)) {
			sl.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.6f, 1.4f);
			return InteractionResult.SUCCESS;
		}
		GunType t = WeaponItems.gunOf(player.getMainHandItem());
		if (t == null) t = GunType.PISTOL;
		Shop.give(player, new ItemStack(WeaponItems.AMMO.get(t), Shop.packSize(t)));
		sl.setBlock(pos, state.setValue(FULL, false), Block.UPDATE_ALL);
		sl.scheduleTick(pos, this, REFILL_TICKS);
		sl.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1f, 0.9f);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(FULL)) level.setBlock(pos, state.setValue(FULL, true), Block.UPDATE_ALL);
	}
}
