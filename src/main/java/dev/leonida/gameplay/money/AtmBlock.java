package dev.leonida.gameplay.money;

import dev.leonida.core.PlayerState;
import dev.leonida.core.api.CrimeEvents;
import dev.leonida.core.api.CrimeEvents.Crime;
import dev.leonida.gameplay.util.HudBridge;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Street ATM. Use it to check your cash; beat it open (several hits) to spill $200 to $500,
 * which is property damage and robbery. Repairs itself after five minutes.
 */
public class AtmBlock extends HorizontalDirectionalBlock {
	public static final BooleanProperty BROKEN = BooleanProperty.create("broken");
	private static final Map<BlockPos, Integer> HITS = new HashMap<>();

	public AtmBlock(Properties p) {
		super(p);
		registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(BROKEN, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
		b.add(FACING, BROKEN);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel && player instanceof ServerPlayer sp) {
			if (state.getValue(BROKEN)) HudBridge.toast(sp, "This ATM is out of order.");
			else HudBridge.toast(sp, "Bank of Leonida ATM. Cash on hand: $" + String.format("%,d", PlayerState.get(sp).money()) + ". Hit it to break it open.");
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
		if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp) || state.getValue(BROKEN)) return;
		int hits = HITS.merge(pos.immutable(), 1, Integer::sum);
		sl.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4f, 1.6f);
		sl.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.1);
		if (hits == 1) CrimeEvents.fire(sp, Crime.PROPERTY_DAMAGE, pos);
		if (hits >= 6) {
			HITS.remove(pos);
			sl.setBlock(pos, state.setValue(BROKEN, true), Block.UPDATE_ALL);
			Vec3 front = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(state.getValue(FACING).getUnitVec3i()).scale(0.8));
			Cash.drop(sl, front, 200 + sl.getRandom().nextInt(31) * 10);
			sl.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
			CrimeEvents.fire(sp, Crime.ROBBERY, pos);
			sl.scheduleTick(pos, this, 20 * 60 * 5);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(BROKEN)) level.setBlock(pos, state.setValue(BROKEN, false), Block.UPDATE_ALL);
	}
}
