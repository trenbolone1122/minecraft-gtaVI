package dev.leonida.aircraft.item;

import dev.leonida.aircraft.entity.Parachutes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A packed parachute. Carry it and press jump while skydiving, or use it in mid-air. */
public class ParachuteItem extends Item {
	public ParachuteItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.onGround() || player.isPassenger()) return InteractionResult.PASS;
		if (player instanceof ServerPlayer sp) {
			return Parachutes.deploy(sp) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}
		return InteractionResult.SUCCESS;
	}
}
