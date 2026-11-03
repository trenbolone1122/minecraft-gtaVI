package dev.leonida.weapon;

import com.mojang.serialization.Codec;
import dev.leonida.LeonidaMod;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;

/** Registers weapon items, data components, the rocket entity and the bullet damage type key. */
public final class WeaponItems {
	private WeaponItems() {}

	/** Rounds currently in the magazine. */
	public static final DataComponentType<Integer> CLIP = component("weapon/clip",
			DataComponentType.<Integer>builder().persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT).ignoreSwapAnimation());
	/** Game time at which the running reload finishes, 0 when not reloading. */
	public static final DataComponentType<Long> RELOAD_END = component("weapon/reload_end",
			DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).ignoreSwapAnimation());

	public static final Map<GunType, GunItem> GUNS = new EnumMap<>(GunType.class);
	public static final Map<GunType, Item> AMMO = new EnumMap<>(GunType.class);
	public static final Map<String, Item> ALL = new LinkedHashMap<>();
	public static Item BAT;
	public static Item KNIFE;

	public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE, LeonidaMod.id("weapon/bullet"));

	public static final ResourceKey<EntityType<?>> ROCKET_KEY = ResourceKey.create(Registries.ENTITY_TYPE, LeonidaMod.id("weapon/rocket"));
	public static final EntityType<RocketEntity> ROCKET = Registry.register(BuiltInRegistries.ENTITY_TYPE, ROCKET_KEY,
			EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC).noLootTable().sized(0.3f, 0.3f)
					.clientTrackingRange(8).updateInterval(1).build(ROCKET_KEY));

	private static <T> DataComponentType<T> component(String path, DataComponentType.Builder<T> b) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, LeonidaMod.id(path), b.build());
	}

	private static <T extends Item> T item(String path, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, LeonidaMod.id("weapon/" + path));
		T item = factory.apply(props.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		ALL.put(path, item);
		return item;
	}

	static void init() {
		for (GunType t : GunType.values()) {
			GUNS.put(t, item(t.id, p -> new GunItem(t, p), new Item.Properties().stacksTo(1).component(CLIP, t.magazine)));
		}
		BAT = item("bat", Item::new, new Item.Properties().sword(ToolMaterial.DIAMOND, 3.0f, -2.8f));
		KNIFE = item("knife", Item::new, new Item.Properties().sword(ToolMaterial.IRON, 2.5f, -2.0f));
		String[] ammoIds = {"ammo_pistol", "ammo_smg", "ammo_rifle", "ammo_shotgun", "ammo_sniper", "ammo_rocket"};
		for (GunType t : GunType.values()) {
			int stack = t == GunType.RPG ? 16 : 64;
			AMMO.put(t, item(ammoIds[t.ordinal()], Item::new, new Item.Properties().stacksTo(stack)));
		}
		ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, LeonidaMod.id("weapon/weapons"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.leonida.weapon"))
				.icon(() -> new ItemStack(GUNS.get(GunType.RIFLE)))
				.displayItems((params, out) -> ALL.values().forEach(i -> out.accept(new ItemStack(i))))
				.build());
	}

	public static GunType gunOf(ItemStack stack) {
		return stack.getItem() instanceof GunItem g ? g.type : null;
	}
}
