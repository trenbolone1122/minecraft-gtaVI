package dev.leonida.landmark.gen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The one place where landmark code picks blocks. Every material is a look block name ({@code leonida:look/<name>},
 * owned by the material agent) with a vanilla fallback, resolved by id at first use. When the look module adds a
 * requested material it is picked up automatically with no change here.
 */
public final class KeysPalette {
	private KeysPalette() {}

	private static final Map<String, BlockState> CACHE = new ConcurrentHashMap<>();

	/** Look block by short name, else the vanilla fallback id. */
	public static BlockState of(String look, String fallback) {
		return CACHE.computeIfAbsent(look + "|" + fallback, k -> {
			if (look != null) {
				Block b = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("leonida", "look/" + look));
				if (b != null && b != Blocks.AIR) return b.defaultBlockState();
			}
			Block f = BuiltInRegistries.BLOCK.getValue(Identifier.parse(fallback));
			return f == null ? Blocks.STONE.defaultBlockState() : f.defaultBlockState();
		});
	}

	/** True when the look block exists (used to choose shapes, e.g. a real railing block vs a fence). */
	public static boolean hasLook(String look) {
		Block b = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("leonida", "look/" + look));
		return b != null && b != Blocks.AIR;
	}

	public static BlockState v(String vanilla) {
		return of(null, vanilla);
	}

	// ---------------------------------------------------------------- Keys wood houses (Jason's house, stilt houses, shacks)

	/** Weathered sea-green board-and-batten siding; variant picks a texture mix so long walls do not tile. */
	/** Exterior wall board, teal outside and cream paneling inside when the lined variant exists; facing = out. */
	/** Lined siding mixing the faded and the plain sea-green boards (r picks one), so walls show sun-bleached streaks. */
	public static BlockState sidingLined(net.minecraft.core.Direction out, double r) {
		if (r < 0.45 && hasLook("siding_teal_lined")) return B.facing(of("siding_teal_lined", "minecraft:warped_planks"), out);
		return sidingLined(out);
	}

	public static BlockState sidingLined(net.minecraft.core.Direction out) {
		if (hasLook("siding_teal_lined_faded")) return B.facing(of("siding_teal_lined_faded", "minecraft:warped_planks"), out);
		if (!hasLook("siding_teal_lined")) return siding(0);
		return B.facing(of("siding_teal_lined", "minecraft:warped_planks"), out);
	}

	public static BlockState siding(double r) {
		if (hasLook("siding_teal_weathered")) return of("siding_teal_weathered", "minecraft:warped_planks");
		return r < 0.86 ? v("minecraft:warped_planks") : v("minecraft:stripped_warped_hyphae");
	}

	public static BlockState sidingDark() {
		return of("siding_teal_weathered_dark", "minecraft:oxidized_cut_copper");
	}

	/** Bleached grey post or piling (pillar, axis property). */
	public static BlockState post() {
		return of("weathered_wood", "minecraft:pale_oak_wood");
	}

	/** Beam, rim joist, corner board (pillar, set axis). */
	/** Slender stilt post (about 25 cm), for the house stilts. */
	public static BlockState stilt() {
		// pale oak fence reads as white stone at a distance; spruce is clearly wood until the weathered post exists
		return of("weathered_wood_fence", "minecraft:spruce_fence");
	}

	public static BlockState beam() {
		return of("weathered_wood", "minecraft:pale_oak_wood");
	}

	public static BlockState braceStairs() {
		return of("weathered_wood_stairs", "leonida:look/wood_brown_stairs");
	}

	public static BlockState weatheredSlab() {
		return of("weathered_wood_slab", "minecraft:spruce_slab");
	}

	public static BlockState deck() {
		return v("minecraft:spruce_planks"); // look wood_deck renders pale grey like stone bricks at a distance
	}

	public static BlockState deckSlab() {
		return v("minecraft:spruce_slab");
	}

	public static BlockState deckStairs() {
		return v("minecraft:spruce_stairs");
	}

	public static BlockState railing() {
		if (hasLook("wood_railing_weathered")) return of("wood_railing_weathered", "minecraft:spruce_fence");
		return of("wood_railing", "minecraft:spruce_fence");
	}

	public static BlockState shutter() {
		return of("shutter_grey", "minecraft:dark_oak_trapdoor");
	}

	public static BlockState window() {
		return of("window_cottage", "minecraft:glass_pane");
	}

	public static BlockState doorCottage() {
		// look door_cottage is a static panel; players must be able to walk in, so an openable door until a real one exists
		return of("door_cottage_openable", "minecraft:warped_door");
	}

	public static BlockState lattice() {
		return of("lattice_panel", "minecraft:birch_trapdoor");
	}

	public static BlockState blind() {
		return of("bamboo_blind", "minecraft:bamboo_trapdoor");
	}

	public static BlockState tarp() {
		return of("tarp_blue", "minecraft:blue_carpet");
	}

	public static BlockState shingle() {
		return of("shingle", "minecraft:deepslate_tiles");
	}

	public static BlockState shingleSlab() {
		return of("shingle_slab", "minecraft:deepslate_tile_slab");
	}

	public static BlockState fasciaSlab() {
		return of("trim_white_slab", "minecraft:smooth_quartz_slab");
	}

	public static BlockState ridgeSlab() {
		return of("metal_roof_slab", "minecraft:polished_andesite_slab");
	}

	public static BlockState shellGravel() {
		return of("oyster_shell_gravel", "minecraft:calcite");
	}

	public static BlockState sand() {
		return of("beach_sand", "minecraft:sand");
	}

	public static BlockState acUnit() {
		return of("ac_unit", "minecraft:iron_block");
	}

	public static BlockState pole() {
		return of("utility_pole", "minecraft:spruce_fence");
	}

	public static BlockState poleCrossarm() {
		return of("utility_pole_crossarm", "minecraft:spruce_fence");
	}

	public static BlockState sofa() {
		return of("tropical_fabric_stairs", "leonida:look/tropical_fabric");
	}

	public static BlockState sofaSlab() {
		return of("tropical_fabric_slab", "leonida:look/tropical_fabric");
	}

	public static BlockState interiorWall() {
		return of("panel_cream", "minecraft:pale_oak_planks");
	}

	/** Floor framing (rim joists, girders): brown wood with visible grain, pillar (set axis). */
	public static BlockState joist() {
		return v("minecraft:stripped_spruce_log");
	}

	/** Off-white trim (corner boards, casings), peeling where the look block exists. */
	public static BlockState trim() {
		if (hasLook("trim_white_peeling")) return of("trim_white_peeling", "minecraft:smooth_quartz");
		return of("trim_white", "minecraft:smooth_quartz");
	}

	/** Knee brace stairs under the floor framing (matches {@link #joist}). */
	public static BlockState braceWood() {
		return v("minecraft:spruce_stairs");
	}

	public static BlockState coffeeTable() {
		return of("glass_coffee_table", "minecraft:dark_oak_slab");
	}

	public static BlockState fridge() {
		return of("fridge_white", "minecraft:white_concrete");
	}

	public static boolean hasFridge() {
		return hasLook("fridge_white");
	}

	public static BlockState cooler() {
		return of("cooler_white", "minecraft:white_shulker_box");
	}

	public static BlockState bucket() {
		return of("bucket_white", "minecraft:cauldron");
	}

	public static BlockState hoseReel() {
		return of("hose_reel", "minecraft:green_carpet");
	}

	public static BlockState crate() {
		return v("minecraft:composter");
	}

	/** Coconut palm trunk segment (look palm when present, jungle log otherwise). */
	public static BlockState palmTrunk(net.minecraft.core.Direction lean, int shift) {
		if (hasLook("palm_trunk_coconut")) return dev.leonida.look.LookBlocks.palmTrunkCoconut(lean, shift);
		return B.axis(v("minecraft:jungle_log"), net.minecraft.core.Direction.Axis.Y);
	}

	public static boolean hasPalm() {
		return hasLook("palm_crown_coconut");
	}

	public static BlockState palmCrown() {
		return of("palm_crown_coconut", "minecraft:jungle_leaves");
	}

	public static BlockState palmFrond(net.minecraft.core.Direction d, boolean diagonal) {
		return dev.leonida.look.LookBlocks.palmFrond(dev.leonida.look.LookBlocks.Palm.COCONUT, d, diagonal);
	}

	public static BlockState hardwood() {
		return v("minecraft:spruce_planks");
	}

	public static BlockState kitchenCabinet() {
		return of("cabinet_green", "minecraft:lime_terracotta");
	}

	public static BlockState counter() {
		return v("minecraft:smooth_quartz");
	}

	public static BlockState tile() {
		return of("tile_floor", "minecraft:white_concrete");
	}

	public static BlockState concrete() {
		return of("concrete", "minecraft:light_gray_concrete");
	}

	// ---------------------------------------------------------------- marinas and boat yards (Brian's Boat Works)

	/** Dry-stack shed cladding: blue-teal corrugated metal (L3269 photo). */
	public static BlockState shedWall() {
		return of("metal_blue_grey", "minecraft:light_blue_terracotta");
	}

	/** White corrugated roof sheet (L326 and L1421 photos). */
	public static BlockState roofWhite() {
		return of("metal_silver", "minecraft:white_concrete");
	}

	/** Half step for the low-pitch white roofs. */
	public static BlockState roofWhiteSlab() {
		return of("roof_membrane_white_slab", "minecraft:smooth_quartz_slab");
	}

	/** Translucent roof skylight strip. */
	public static BlockState skylight() {
		return v("minecraft:light_gray_stained_glass");
	}

	/** Inside rack posts and arms (galvanised steel). */
	public static BlockState rackSteel() {
		return of("frame_steel", "minecraft:light_gray_concrete");
	}

	/** Outdoor rent-a-rack steel (darker painted steel, L3269). */
	public static BlockState rackDark() {
		return of("frame_dark", "minecraft:gray_concrete");
	}

	public static BlockState truss() {
		return of("steel_truss_white", "minecraft:iron_bars");
	}

	public static BlockState asphalt(double r) {
		if (r < 0.55) return of("asphalt_worn", "minecraft:gray_concrete_powder");
		if (r < 0.8) return of("asphalt", "minecraft:gray_concrete");
		return of("asphalt_patched", "minecraft:gray_concrete");
	}

	public static BlockState hull() {
		return v("minecraft:smooth_quartz");
	}

	public static BlockState hullSlab() {
		return v("minecraft:smooth_quartz_slab");
	}

	public static BlockState hullStairs() {
		return v("minecraft:smooth_quartz_stairs");
	}

	/** Boat covers seen on the racks: khaki canvas, grey, navy. */
	public static BlockState boatCover(double r) {
		if (r < 0.45) return v("minecraft:smooth_sandstone");
		if (r < 0.75) return v("minecraft:light_gray_wool");
		return v("minecraft:blue_wool");
	}

	public static BlockState signRed() {
		return v("minecraft:red_concrete");
	}

	public static BlockState signWhite() {
		return v("minecraft:white_concrete");
	}

	/** White clapboard of the Boat Works office (L1421). */
	public static BlockState clapboardWhite() {
		return of("trim_white", "minecraft:white_concrete");
	}

	public static BlockState roofWhiteStairs() {
		return v("minecraft:smooth_quartz_stairs");
	}

	public static BlockState houseWindow() {
		return of("window_house", "minecraft:glass");
	}

	public static BlockState rollUpWhite() {
		return of("roll_up_door_white", "minecraft:white_concrete");
	}

	public static BlockState workshopWall() {
		return of("metal_light", "minecraft:light_gray_concrete");
	}

	public static BlockState picket() {
		return v("minecraft:birch_fence");
	}

	// ---------------------------------------------------------------- roadside motel (Starlet Motel)

	/** Lower walls of the Starlet: rough tan-grey brick (L304 photo). */
	public static BlockState motelBrick() {
		return v("minecraft:mud_bricks");
	}

	/** Weathered vertical board gable ends. */
	public static BlockState gableBoards() {
		return of("weathered_wood", "minecraft:stripped_spruce_wood");
	}

	/** Blue-teal standing seam metal roof. */
	public static BlockState motelRoof() {
		return v("minecraft:waxed_oxidized_cut_copper");
	}

	public static BlockState motelRoofStairs() {
		return v("minecraft:waxed_oxidized_cut_copper_stairs");
	}

	public static BlockState motelRoofSlab() {
		return v("minecraft:waxed_oxidized_cut_copper_slab");
	}

	/** Dark slate face of the roadside sign. */
	public static BlockState signPanel() {
		return v("minecraft:cyan_terracotta");
	}

	/** Warm white lit letters and marquee bulbs. */
	public static BlockState signLight() {
		return v("minecraft:ochre_froglight");
	}

	public static BlockState bulbs() {
		return v("minecraft:glowstone");
	}

	/** Thin red sign poles. */
	public static BlockState signPole() {
		return v("minecraft:red_nether_brick_wall");
	}

	public static BlockState poolTile() {
		return v("minecraft:light_blue_terracotta");
	}

	public static BlockState poolCoping() {
		return v("minecraft:terracotta");
	}

	/** Green artificial turf around the pool (L1159). */
	public static BlockState turf() {
		return of("turf_grass_dark", "minecraft:moss_block");
	}

	public static BlockState poolFence() {
		return v("minecraft:iron_bars");
	}

	public static BlockState blockHut() {
		return of("stucco_off_white_grimy", "minecraft:white_terracotta");
	}

	public static BlockState lounger() {
		return v("minecraft:smooth_quartz_slab");
	}
}
