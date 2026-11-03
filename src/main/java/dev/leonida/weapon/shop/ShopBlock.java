package dev.leonida.weapon.shop;

import dev.leonida.weapon.WeaponNet;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ammu-Nation fixtures. The counter is a glass display case (FACING = customer side); the rack is a
 * thin wall panel of guns (FACING = the way it faces, its back against the wall). Both open the shop.
 */
public class ShopBlock extends HorizontalDirectionalBlock {
	private final boolean rack;
	private static final VoxelShape RACK_N = Block.box(0, 0, 14, 16, 16, 16);
	private static final VoxelShape RACK_S = Block.box(0, 0, 0, 16, 16, 2);
	private static final VoxelShape RACK_W = Block.box(14, 0, 0, 16, 16, 16);
	private static final VoxelShape RACK_E = Block.box(0, 0, 0, 2, 16, 16);

	public ShopBlock(Properties p, boolean rack) {
		super(p);
		this.rack = rack;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public static ShopBlock counter(Properties p) { return new ShopBlock(p, false); }
	public static ShopBlock rack(Properties p) { return new ShopBlock(p, true); }

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
		b.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		Direction d = ctx.getHorizontalDirection().getOpposite();
		if (rack && ctx.getClickedFace().getAxis().isHorizontal()) d = ctx.getClickedFace();
		return defaultBlockState().setValue(FACING, d);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		if (!rack) return Shapes.block();
		return switch (state.getValue(FACING)) {
			case SOUTH -> RACK_S;
			case WEST -> RACK_W;
			case EAST -> RACK_E;
			default -> RACK_N;
		};
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer sp) ServerPlayNetworking.send(sp, new WeaponNet.OpenShop(pos.immutable()));
		return InteractionResult.SUCCESS;
	}
}
