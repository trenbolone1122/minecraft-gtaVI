package dev.leonida.weapon.shop;

import dev.leonida.LeonidaMod;
import dev.leonida.weapon.WeaponNet;
import java.util.function.Function;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Gun shop blocks and weapon pickups. Ids for other modules (build interiors place them by id):
 * leonida:weapon/gun_counter, leonida:weapon/gun_rack, leonida:weapon/ammo_box, entity leonida:weapon/pickup.
 */
public final class WeaponShop {
	private WeaponShop() {}

	public static Block GUN_COUNTER;
	public static Block GUN_RACK;
	public static Block AMMO_BOX;
	public static final ResourceKey<EntityType<?>> PICKUP_KEY = ResourceKey.create(Registries.ENTITY_TYPE, LeonidaMod.id("weapon/pickup"));
	public static final EntityType<WeaponPickupEntity> PICKUP = Registry.register(BuiltInRegistries.ENTITY_TYPE, PICKUP_KEY,
			EntityType.Builder.<WeaponPickupEntity>of(WeaponPickupEntity::new, MobCategory.MISC).noLootTable().sized(0.6f, 0.6f)
					.clientTrackingRange(8).updateInterval(4).build(PICKUP_KEY));

	private static Block block(String path, Function<BlockBehaviour.Properties, Block> f, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, LeonidaMod.id("weapon/" + path));
		Block b = Registry.register(BuiltInRegistries.BLOCK, key, f.apply(props.setId(key)));
		ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, LeonidaMod.id("weapon/" + path));
		Item item = Registry.register(BuiltInRegistries.ITEM, ik, new BlockItem(b, new Item.Properties().setId(ik).useBlockDescriptionPrefix()));
		dev.leonida.weapon.WeaponItems.ALL.put(path, item);
		return b;
	}

	public static void init() {
		GUN_COUNTER = block("gun_counter", ShopBlock::counter, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
				.strength(-1f, 3600000f).sound(SoundType.GLASS).noOcclusion());
		GUN_RACK = block("gun_rack", ShopBlock::rack, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
				.strength(-1f, 3600000f).sound(SoundType.WOOD).noOcclusion());
		AMMO_BOX = block("ammo_box", AmmoBoxBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN)
				.strength(-1f, 3600000f).sound(SoundType.METAL).noOcclusion());
		ServerPlayNetworking.registerGlobalReceiver(WeaponNet.Buy.TYPE, (p, ctx) -> Shop.buy(ctx.player(), p.pos(), p.index()));
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof net.minecraft.world.entity.player.Player || !(entity.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
			var held = entity.getMainHandItem();
			if (held.getItem() instanceof dev.leonida.weapon.GunItem) {
				WeaponPickupEntity.drop(sl, entity.position().add(0, 0.3, 0), held.copyWithCount(1), false);
			}
		});
	}
}
