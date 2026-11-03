package dev.leonida.core.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Lets the HUD show ammo without depending on the weapon module. The weapon module installs it. */
public interface AmmoProvider {
	/** Return null when the stack is not a gun. */
	Ammo ammo(Player player, ItemStack stack);

	record Ammo(int clip, int reserve, String weaponName) {}

	AmmoProvider NONE = (p, s) -> null;

	final class Holder {
		private static volatile AmmoProvider instance = NONE;
		private Holder() {}
	}

	static AmmoProvider get() { return Holder.instance; }

	static void install(AmmoProvider a) { Holder.instance = a; }
}
