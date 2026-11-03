package dev.leonida.look.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Opaque curtain wall or window pane. Full cube, so towers cull like stone. The model has a glow overlay that the
 * client keeps only at night (see dev.leonida.look.client.NightWindows); {@link #GLOW} forces it on or off.
 */
public class WindowBlock extends Block {
	public static final EnumProperty<Glow> GLOW = EnumProperty.create("glow", Glow.class);

	private final WindowKind kind;

	public WindowBlock(WindowKind kind, BlockBehaviour.Properties properties) {
		super(properties);
		this.kind = kind;
		registerDefaultState(stateDefinition.any().setValue(GLOW, Glow.AUTO));
	}

	public WindowKind kind() {
		return kind;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(GLOW);
	}
}
