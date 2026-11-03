package dev.leonida.gameplay;

import dev.leonida.LeonidaMod;
import dev.leonida.gameplay.money.AtmBlock;
import dev.leonida.gameplay.money.RegisterBlock;
import dev.leonida.gameplay.money.RegisterBlockEntity;
import dev.leonida.gameplay.npc.Pedestrian;
import dev.leonida.gameplay.npc.PoliceOfficer;
import dev.leonida.gameplay.npc.StoreClerk;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Everything the gameplay module registers. Ids live under leonida:gameplay/... */
public final class GameplayRegistry {
	private GameplayRegistry() {}

	public static final EntityType<Pedestrian> PEDESTRIAN = entity("pedestrian", EntityType.Builder.<Pedestrian>of(Pedestrian::new, MobCategory.CREATURE));
	public static final EntityType<StoreClerk> CLERK = entity("clerk", EntityType.Builder.<StoreClerk>of(StoreClerk::new, MobCategory.CREATURE));
	public static final EntityType<PoliceOfficer> POLICE = entity("police_officer", EntityType.Builder.<PoliceOfficer>of(PoliceOfficer::new, MobCategory.CREATURE));

	public static final Item PHONE = item("phone", Item::new, new Item.Properties().stacksTo(1));
	public static final Item CASH = item("cash", Item::new, new Item.Properties().stacksTo(64));

	public static final Block ATM = block("atm", AtmBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1f, 3600000f).sound(SoundType.METAL).noOcclusion());
	public static final Block REGISTER = block("register", RegisterBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1f, 3600000f).sound(SoundType.METAL).noOcclusion());
	public static final BlockEntityType<RegisterBlockEntity> REGISTER_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			LeonidaMod.id("gameplay/register"), new BlockEntityType<>(RegisterBlockEntity::new, Set.of(REGISTER)));

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> entity(String name, EntityType.Builder<T> b) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, LeonidaMod.id("gameplay/" + name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, b.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build(key));
	}

	private static Item item(String name, Function<Item.Properties, Item> f, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, LeonidaMod.id("gameplay/" + name));
		return Registry.register(BuiltInRegistries.ITEM, key, f.apply(props.setId(key)));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> f, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, LeonidaMod.id("gameplay/" + name));
		Block b = Registry.register(BuiltInRegistries.BLOCK, key, f.apply(props.setId(key)));
		ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, LeonidaMod.id("gameplay/" + name));
		Registry.register(BuiltInRegistries.ITEM, ik, new BlockItem(b, new Item.Properties().setId(ik).useBlockDescriptionPrefix()));
		return b;
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(PEDESTRIAN, Pedestrian.createAttributes());
		FabricDefaultAttributeRegistry.register(CLERK, Pedestrian.createAttributes());
		FabricDefaultAttributeRegistry.register(POLICE, PoliceOfficer.createAttributes());
	}
}
