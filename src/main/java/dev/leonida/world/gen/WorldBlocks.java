package dev.leonida.world.gen;

import dev.leonida.look.LookBlocks;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Every block the world generator places, in one place (Leonida look blocks with vanilla fallbacks). */
public final class WorldBlocks {
	private WorldBlocks() {}

	public static final BlockState AIR = Blocks.AIR.defaultBlockState();
	public static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
	public static final BlockState STONE = Blocks.STONE.defaultBlockState();
	public static final BlockState LIMESTONE = Blocks.CALCITE.defaultBlockState();
	public static final BlockState WATER = Blocks.WATER.defaultBlockState();

	public static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	public static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	public static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
	public static final BlockState MUD = Blocks.MUD.defaultBlockState();

	public static BlockState beachSand() { return LookBlocks.BEACH_SAND.defaultBlockState(); }
	public static BlockState wetSand() { return LookBlocks.WET_SAND.defaultBlockState(); }
	public static BlockState seabedSand() { return LookBlocks.SEABED_SAND.defaultBlockState(); }
	public static BlockState coralStone() { return LookBlocks.CORAL_STONE.defaultBlockState(); }
	public static BlockState concrete() { return LookBlocks.CONCRETE.defaultBlockState(); }
	/** Open paved ground (plazas, lots): the map's mid-grey paving (#787c79), not the light kerbside sidewalk. */
	public static BlockState paving(int x, int z) {
		double h = Hash.h01(x, z, 31);
		return h < 0.62 ? Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY).defaultBlockState() : h < 0.9 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
	}

	public static final BlockState SHORT_GRASS = Blocks.SHORT_GRASS.defaultBlockState();
	public static final BlockState FERN = Blocks.FERN.defaultBlockState();
	public static final BlockState BUSH = Blocks.BUSH.defaultBlockState();
	public static final BlockState LILY = Blocks.LILY_PAD.defaultBlockState();
	public static final BlockState SEAGRASS = Blocks.SEAGRASS.defaultBlockState();

	public static final BlockState ROYAL_PALM_TRUNK = Blocks.STRIPPED_BIRCH_LOG.defaultBlockState();
	public static final BlockState ROYAL_PALM_SHAFT = Blocks.MOSS_BLOCK.defaultBlockState();
	public static final BlockState COCONUT_TRUNK = Blocks.STRIPPED_JUNGLE_LOG.defaultBlockState();
	public static final BlockState PALM_FROND = leaves(Blocks.JUNGLE_LEAVES);
	public static final BlockState COCONUT = Blocks.COCOA.defaultBlockState();
	public static final BlockState MANGROVE_LOG = Blocks.MANGROVE_LOG.defaultBlockState();
	public static final BlockState MANGROVE_ROOTS = Blocks.MANGROVE_ROOTS.defaultBlockState();
	public static final BlockState MANGROVE_LEAVES = leaves(Blocks.MANGROVE_LEAVES);
	public static final BlockState CYPRESS_LOG = Blocks.SPRUCE_LOG.defaultBlockState();
	public static final BlockState CYPRESS_LEAVES = leaves(Blocks.OAK_LEAVES);
	public static final BlockState PINE_LOG = Blocks.SPRUCE_LOG.defaultBlockState();
	public static final BlockState PINE_LEAVES = leaves(Blocks.SPRUCE_LEAVES);
	public static final BlockState OAK_LOG = Blocks.OAK_LOG.defaultBlockState();
	public static final BlockState OAK_LEAVES = leaves(Blocks.OAK_LEAVES);
	public static final BlockState SHRUB = leaves(Blocks.AZALEA_LEAVES);

	/** Placeholder building blocks (only while no BuildingGenerator is installed). */
	public static final BlockState PH_WALL = Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
	public static final BlockState PH_GLASS = Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState();
	public static final BlockState PH_ROOF = Blocks.SMOOTH_STONE.defaultBlockState();

	private static BlockState leaves(Block b) {
		return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	}
}
