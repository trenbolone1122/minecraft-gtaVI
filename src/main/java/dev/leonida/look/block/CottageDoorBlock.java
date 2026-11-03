package dev.leonida.look.block;

import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/** Real openable wooden door (vanilla door behaviour, oak sounds). */
public class CottageDoorBlock extends DoorBlock {
	public CottageDoorBlock(BlockBehaviour.Properties properties) {
		super(BlockSetType.OAK, properties);
	}
}
