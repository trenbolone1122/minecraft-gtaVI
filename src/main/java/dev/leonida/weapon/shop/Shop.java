package dev.leonida.weapon.shop;

import dev.leonida.core.PlayerState;
import dev.leonida.weapon.GunType;
import dev.leonida.weapon.WeaponItems;
import dev.leonida.weapon.WeaponNet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Ammu-Nation catalog and purchases. The list is shared by server (prices, checks) and client (menu). */
public final class Shop {
	private Shop() {}

	public enum Kind { GUN, MELEE, AMMO, ARMOR }

	/** One row in the shop. {@code gun} is set for guns and ammo. */
	public record Entry(String key, Kind kind, GunType gun, int price, Supplier<ItemStack> stack) {}

	private static List<Entry> catalog;

	/** Rounds in one ammo pack (also what a gun comes with and what an ammo box hands out). */
	public static int packSize(GunType t) {
		return switch (t) {
			case RPG -> 2;
			case SNIPER -> 10;
			default -> t.magazine * 2;
		};
	}

	public static int gunPrice(GunType t) {
		return switch (t) {
			case PISTOL -> 500;
			case SMG -> 1500;
			case RIFLE -> 3000;
			case SHOTGUN -> 1250;
			case SNIPER -> 5000;
			case RPG -> 10000;
		};
	}

	public static int ammoPrice(GunType t) {
		return switch (t) {
			case PISTOL -> 60;
			case SMG -> 100;
			case RIFLE -> 150;
			case SHOTGUN -> 80;
			case SNIPER -> 150;
			case RPG -> 500;
		};
	}

	public static List<Entry> catalog() {
		if (catalog == null) {
			List<Entry> l = new ArrayList<>();
			for (GunType t : GunType.values()) l.add(new Entry(t.id, Kind.GUN, t, gunPrice(t), () -> new ItemStack(WeaponItems.GUNS.get(t))));
			l.add(new Entry("bat", Kind.MELEE, null, 50, () -> new ItemStack(WeaponItems.BAT)));
			l.add(new Entry("knife", Kind.MELEE, null, 100, () -> new ItemStack(WeaponItems.KNIFE)));
			for (GunType t : GunType.values()) l.add(new Entry("ammo_" + t.id, Kind.AMMO, t, ammoPrice(t), () -> new ItemStack(WeaponItems.AMMO.get(t), packSize(t))));
			l.add(new Entry("armor", Kind.ARMOR, null, 500, Shop::armor));
			catalog = List.copyOf(l);
		}
		return catalog;
	}

	public static ItemStack armor() {
		ItemStack s = new ItemStack(Items.IRON_CHESTPLATE);
		s.set(DataComponents.CUSTOM_NAME, Component.translatable("gui.leonida.weapon.shop.armor"));
		return s;
	}

	/** True if the player already carries this gun (guns are bought once, then ammo). */
	public static boolean owns(net.minecraft.world.entity.player.Player p, GunType t) {
		for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (WeaponItems.gunOf(s) == t) return true;
		return false;
	}

	/** Adds to the inventory; whatever does not fit drops at the player's feet. */
	public static void give(net.minecraft.world.entity.player.Player p, ItemStack s) {
		p.getInventory().add(s);
		if (!s.isEmpty()) p.drop(s, false, net.minecraft.util.Prediction.SERVER_ONLY);
	}

	static boolean isShop(ServerPlayer p, BlockPos pos) {
		return p.distanceToSqr(Vec3.atCenterOf(pos)) < 8 * 8 && p.level().getBlockState(pos).getBlock() instanceof ShopBlock;
	}

	/** Server side of a purchase. Replies with a ShopResult message key. */
	public static void buy(ServerPlayer p, BlockPos pos, int index) {
		List<Entry> cat = catalog();
		if (index < 0 || index >= cat.size() || !isShop(p, pos) || !p.isAlive()) return;
		Entry e = cat.get(index);
		boolean free = p.hasInfiniteMaterials();
		PlayerState st = PlayerState.get(p);
		String result;
		if (e.kind == Kind.GUN && owns(p, e.gun)) {
			result = "owned";
		} else if (!free && st.money() < e.price) {
			result = "money";
		} else {
			if (!free) PlayerState.set(p, st.withMoney(st.money() - e.price));
			ItemStack s = e.stack.get();
			if (e.kind == Kind.ARMOR && p.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
				p.setItemSlot(EquipmentSlot.CHEST, s);
			} else {
				give(p, s);
			}
			if (e.kind == Kind.GUN) give(p, new ItemStack(WeaponItems.AMMO.get(e.gun), packSize(e.gun)));
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 1f, 1.1f);
			result = "bought";
		}
		ServerPlayNetworking.send(p, new WeaponNet.ShopResult(index, result));
	}
}
