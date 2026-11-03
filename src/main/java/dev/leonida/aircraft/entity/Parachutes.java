package dev.leonida.aircraft.entity;

import dev.leonida.aircraft.AircraftModule;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server rules for parachutes: bail-out grants, and deploying from freefall. */
public final class Parachutes {
	private Parachutes() {}

	public static boolean hasParachute(ServerPlayer player) {
		return player.getInventory().contains(s -> s.is(AircraftModule.PARACHUTE_ITEM));
	}

	/** Everyone who bails out of an aircraft gets a parachute if they do not have one. */
	public static void grantForBailout(ServerPlayer player) {
		if (!hasParachute(player)) {
			player.getInventory().add(new ItemStack(AircraftModule.PARACHUTE_ITEM));
		}
		player.sendOverlayMessage(Component.translatable("message.leonida.aircraft.bailout"));
	}

	/** Open a parachute for the player. Returns false (with a message) when they cannot. */
	public static boolean deploy(ServerPlayer player) {
		if (player.isPassenger() || player.onGround() || player.isInWater() || player.isFallFlying()) return false;
		boolean creative = player.getAbilities().instabuild;
		if (!creative) {
			Inventory inv = player.getInventory();
			int slot = -1;
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (inv.getItem(i).is(AircraftModule.PARACHUTE_ITEM)) {
					slot = i;
					break;
				}
			}
			if (slot < 0) {
				player.sendOverlayMessage(Component.translatable("message.leonida.aircraft.no_parachute"));
				return false;
			}
			inv.getItem(slot).shrink(1);
		}
		ParachuteEntity chute = new ParachuteEntity(AircraftModule.PARACHUTE, player.level());
		chute.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
		Vec3 v = player.getDeltaMovement();
		chute.setDeltaMovement(v.x * 0.5, Math.max(v.y, -0.6), v.z * 0.5);
		player.level().addFreshEntity(chute);
		player.resetFallDistance();
		return player.startRiding(chute, true, true);
	}
}
