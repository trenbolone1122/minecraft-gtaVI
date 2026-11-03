package dev.leonida.road.block;

import dev.leonida.LeonidaMod;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Blocks the road module adds on top of the look palette (ids under {@code leonida:road/}). */
public final class RoadBlocks {
	private RoadBlocks() {}

	public static final DecalBlock LINE_WHITE = decal("decal_line", 14, 1 / 16.0);
	public static final DecalBlock LINE_YELLOW = decal("decal_line_yellow", 14, 1 / 16.0);
	public static final DecalBlock LINE_YELLOW_DOUBLE = decal("decal_line_yellow2", 15, 1 / 16.0);
	public static final DecalBlock STOP_BAR = decal("decal_stop_bar", 17, 1 / 16.0);
	public static final DecalBlock ZEBRA = decal("decal_zebra", 24, 0.05);

	/** Galvanised steel pole for streetlights and mast-arm signals (arms and heads are drawn by the client). */
	public static final Block POLE = reg("pole", p -> new PoleBlock(p),
			BlockBehaviour.Properties.of().mapColor(MapColor.METAL).sound(SoundType.METAL).strength(3f, 6f).noOcclusion(), true);

	public static void init() {}

	private static BlockBehaviour.Properties asphalt() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).sound(SoundType.STONE).strength(1.5f, 6f).requiresCorrectToolForDrops();
	}

	private static DecalBlock decal(String name, int offsets, double step) {
		return reg(name, p -> new DecalBlock(offsets, step, p), asphalt(), false);
	}

	static <B extends Block> B reg(String name, Function<BlockBehaviour.Properties, B> f, BlockBehaviour.Properties props, boolean item) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, LeonidaMod.id("road/" + name));
		B b = Registry.register(BuiltInRegistries.BLOCK, key, f.apply(props.setId(key)));
		if (item) {
			ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, LeonidaMod.id("road/" + name));
			Registry.register(BuiltInRegistries.ITEM, ik, new BlockItem(b, new Item.Properties().setId(ik).useBlockDescriptionPrefix()));
		}
		return b;
	}
}
