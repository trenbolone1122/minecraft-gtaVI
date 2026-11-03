package dev.leonida.look.block;

import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Plain slab (public constructor so the registry can make one per material). */
public class LookSlab extends SlabBlock {
	public LookSlab(BlockBehaviour.Properties properties) {
		super(properties);
	}
}
