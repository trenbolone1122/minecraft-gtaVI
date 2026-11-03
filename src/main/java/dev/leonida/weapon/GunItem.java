package dev.leonida.weapon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A gun. Firing, aiming and reloading are driven by key input and packets, not by vanilla use. */
public class GunItem extends Item {
	public final GunType type;

	public GunItem(GunType type, Properties props) {
		super(props);
		this.type = type;
	}

	public static int clip(ItemStack stack) {
		return stack.getOrDefault(WeaponItems.CLIP, 0);
	}

	public static long reloadEnd(ItemStack stack) {
		return stack.getOrDefault(WeaponItems.RELOAD_END, 0L);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return InteractionResult.FAIL; // right mouse aims instead
	}

	@Override
	public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
		return false;
	}
}
