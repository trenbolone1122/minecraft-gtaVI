package dev.leonida.weapon.api;

import dev.leonida.weapon.Ballistics;
import dev.leonida.weapon.GunItem;
import dev.leonida.weapon.GunType;
import dev.leonida.weapon.WeaponItems;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * Public weapon API for other modules (gameplay arms police and SWAT with it).
 * Item ids: leonida:weapon/pistol, smg, rifle, shotgun, sniper, rpg, bat, knife.
 */
public final class WeaponApi {
	private WeaponApi() {}

	/** Optional: gameplay can install how cops are recognised (default: entity type id contains police/cop/officer/swat or tag leonida_cop). */
	public static volatile Predicate<Entity> copPredicate;

	/** A loaded gun stack. id is one of pistol, smg, rifle, shotgun, sniper, rpg. */
	public static ItemStack gun(String id) {
		for (GunType t : GunType.values()) {
			if (t.id.equals(id)) {
				ItemStack s = new ItemStack(WeaponItems.GUNS.get(t));
				s.set(WeaponItems.CLIP, t.magazine);
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Puts a loaded gun in the mob's main hand. On death it is left as a spinning weapon pickup, never as a plain item. */
	public static void arm(Mob mob, String id) {
		mob.setItemSlot(EquipmentSlot.MAINHAND, gun(id));
		mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
	}

	/**
	 * Spawns a floating GTA style pickup (gun, ammo, melee or armor). Walking into it collects it;
	 * a gun the player already carries gives ammo instead. Persistent pickups never despawn.
	 */
	public static Entity spawnPickup(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 pos, ItemStack stack, boolean persistent) {
		return dev.leonida.weapon.shop.WeaponPickupEntity.drop(level, pos, stack, persistent);
	}

	public static boolean isGun(ItemStack stack) {
		return stack.getItem() instanceof GunItem;
	}

	/** Effective range in blocks of the mob's held gun, 0 if none. */
	public static int range(LivingEntity e) {
		GunType t = WeaponItems.gunOf(e.getMainHandItem());
		return t == null ? 0 : t.range;
	}

	/**
	 * Fires the mob's held gun at target with full effects (sound, muzzle flash, tracer, impact, damage).
	 * Respects fire rate; reloads automatically with infinite reserve. Returns true when a shot was fired.
	 * Call it every tick while the mob wants to shoot. inaccuracy is extra spread in degrees (3 to 6 is fair).
	 */
	public static boolean npcShoot(Mob shooter, LivingEntity target, float inaccuracy) {
		return Ballistics.npcShoot(shooter, target, inaccuracy);
	}

	public static boolean npcShoot(Mob shooter, LivingEntity target) {
		return npcShoot(shooter, target, 4f);
	}
}
