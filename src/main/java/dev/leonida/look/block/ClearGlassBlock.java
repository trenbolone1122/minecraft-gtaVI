package dev.leonida.look.block;

import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** See-through glass (lobbies, storefront doors): translucent, hides faces against itself like vanilla glass. */
public class ClearGlassBlock extends TransparentBlock {
	public ClearGlassBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}
}
