package dev.leonida.look.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Deck railing with picket balusters: connects like a fence, to itself and to solid faces. */
public class WoodRailingBlock extends FenceBlock {
	public WoodRailingBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public boolean connectsTo(BlockState state, boolean faceSolid, Direction direction) {
		return state.getBlock() instanceof WoodRailingBlock || super.connectsTo(state, faceSolid, direction);
	}
}
