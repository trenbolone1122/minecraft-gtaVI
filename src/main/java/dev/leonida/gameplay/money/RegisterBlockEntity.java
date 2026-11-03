package dev.leonida.gameplay.money;

import dev.leonida.gameplay.GameplayRegistry;
import dev.leonida.gameplay.npc.StoreClerk;
import dev.leonida.gameplay.util.SpawnFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/** Keeps a clerk behind the register while players are around, and refills the till. */
public class RegisterBlockEntity extends BlockEntity {
	private long nextClerk;
	private long emptiedAt = -1;

	public RegisterBlockEntity(BlockPos pos, BlockState state) {
		super(GameplayRegistry.REGISTER_BE, pos, state);
	}

	void markEmptied(long t) {
		emptiedAt = t;
		setChanged();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RegisterBlockEntity be) {
		long t = level.getGameTime();
		if ((t + pos.asLong()) % 40 != 0 || !(level instanceof ServerLevel sl)) return;
		if (state.getValue(RegisterBlock.EMPTY) && be.emptiedAt >= 0 && t - be.emptiedAt > 20 * 60 * 10) {
			sl.setBlock(pos, state.setValue(RegisterBlock.EMPTY, false), Block.UPDATE_ALL);
			be.emptiedAt = -1;
		}
		if (t < be.nextClerk || sl.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 32, false) == null) return;
		if (!sl.getEntitiesOfClass(StoreClerk.class, new AABB(pos).inflate(6), StoreClerk::isAlive).isEmpty()) return;
		Direction back = state.getValue(RegisterBlock.FACING).getOpposite();
		BlockPos stand = pos.relative(back);
		// Register sits on a counter: the clerk stands on the floor behind it.
		BlockPos spot = null;
		for (int dy = 0; dy >= -2 && spot == null; dy--) {
			BlockPos q = stand.above(dy);
			if (sl.getBlockState(q).isAir() && sl.getBlockState(q.above()).isAir() && !sl.getBlockState(q.below()).isAir()) spot = q;
		}
		if (spot == null) spot = SpawnFinder.ground(sl, stand.getX(), stand.getZ());
		if (spot == null) return;
		StoreClerk c = GameplayRegistry.CLERK.create(sl, EntitySpawnReason.EVENT);
		if (c == null) return;
		c.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, state.getValue(RegisterBlock.FACING).toYRot(), 0);
		c.setYHeadRot(state.getValue(RegisterBlock.FACING).toYRot());
		c.setHome(spot);
		c.setPersistenceRequired();
		sl.addFreshEntity(c);
		be.nextClerk = t + 20 * 60 * 5;
		be.setChanged();
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		out.putLong("next_clerk", nextClerk);
		out.putLong("emptied_at", emptiedAt);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		nextClerk = in.getLongOr("next_clerk", 0);
		emptiedAt = in.getLongOr("emptied_at", -1);
	}
}
