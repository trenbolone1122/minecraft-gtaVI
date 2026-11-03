package dev.leonida.look.block;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Plain stairs (StairBlock's constructor is protected). */
public class LookStairs extends StairBlock {
	public LookStairs(BlockState base, BlockBehaviour.Properties properties) {
		super(base, properties);
	}
}
