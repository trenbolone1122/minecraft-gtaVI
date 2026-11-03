package dev.leonida.vicecity.gen;

import dev.leonida.look.LookBlocks;
import dev.leonida.look.block.Glow;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The single palette of the vicecity module: every block a landmark uses comes from here, so materials can be swapped
 * in one place (LookBlocks where they exist, vanilla fallbacks until the look module ships the requested materials).
 * Lazy: only touch after LookModule.init().
 */
public final class Pal {
	private Pal() {}

	public static final BlockState AIR = Blocks.AIR.defaultBlockState();

	// structure and paving
	public static BlockState concrete() { return LookBlocks.CONCRETE.defaultBlockState(); }
	public static BlockState concreteSlab() { return slab(LookBlocks.CONCRETE_SLAB, SlabType.BOTTOM); }
	public static BlockState concreteSlabTop() { return slab(LookBlocks.CONCRETE_SLAB, SlabType.TOP); }
	public static BlockState concreteStairs(Direction facing) { return stairs(LookBlocks.CONCRETE_STAIRS, facing, Half.BOTTOM); }
	public static BlockState darkConcrete() { return Blocks.CONCRETE.pick(DyeColor.GRAY).defaultBlockState(); }
	public static BlockState black() { return Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState(); }
	public static BlockState foundation() { return Blocks.SMOOTH_STONE.defaultBlockState(); }
	public static BlockState terrazzo() { return LookBlocks.TERRAZZO.defaultBlockState(); }
	public static BlockState tile() { return LookBlocks.TILE_FLOOR.defaultBlockState(); }
	public static BlockState marble() { return LookBlocks.MARBLE.defaultBlockState(); }
	public static BlockState sidewalk(int x, int z) { return LookBlocks.sidewalk(x, z, SlabType.DOUBLE); }
	public static BlockState white() { return LookBlocks.TRIM_WHITE.defaultBlockState(); }
	public static BlockState whiteSlab() { return slab(LookBlocks.TRIM_WHITE_SLAB, SlabType.BOTTOM); }
	public static BlockState whiteSlabTop() { return slab(LookBlocks.TRIM_WHITE_SLAB, SlabType.TOP); }
	public static BlockState whiteStairs(Direction f, boolean top) { return stairs(LookBlocks.TRIM_WHITE_STAIRS, f, top ? Half.TOP : Half.BOTTOM); }
	public static BlockState quartz() { return Blocks.SMOOTH_QUARTZ.defaultBlockState(); }

	// metal and cladding
	public static BlockState panelLight() { return LookBlocks.ARENA_PANEL_SILVER.defaultBlockState(); }
	public static BlockState panelGrey() { return LookBlocks.panel(LookBlocks.OfficeGrey.GREY).defaultBlockState(); }
	public static BlockState panelBlue() { return LookBlocks.panel(LookBlocks.OfficeGrey.BLUE).defaultBlockState(); }
	public static BlockState metalSilver() { return LookBlocks.corrugated(LookBlocks.Metal.SILVER).defaultBlockState(); }
	public static BlockState metalLight() { return LookBlocks.corrugated(LookBlocks.Metal.LIGHT).defaultBlockState(); }
	public static BlockState metalBlueGrey() { return LookBlocks.corrugated(LookBlocks.Metal.BLUE_GREY).defaultBlockState(); }
	public static BlockState frameSilver() { return LookBlocks.frame(LookBlocks.Frame.SILVER).defaultBlockState(); }
	public static BlockState frameSteel() { return LookBlocks.frame(LookBlocks.Frame.STEEL).defaultBlockState(); }
	public static BlockState frameDark() { return LookBlocks.frame(LookBlocks.Frame.DARK).defaultBlockState(); }
	public static BlockState truss() { return LookBlocks.truss(LookBlocks.Truss.WHITE).defaultBlockState(); }
	public static BlockState trussGrey() { return LookBlocks.truss(LookBlocks.Truss.GREY).defaultBlockState(); }
	public static BlockState chain() { return Blocks.IRON_CHAIN.defaultBlockState(); }
	public static BlockState roof() { return LookBlocks.MEMBRANE_ROOF.defaultBlockState(); }
	public static BlockState roofWhite() { return LookBlocks.ROOF_MEMBRANE_WHITE.defaultBlockState(); }
	public static BlockState roofWhiteSlab() { return slab(LookBlocks.ROOF_MEMBRANE_WHITE_SLAB, SlabType.BOTTOM); }
	public static BlockState arenaWhite() { return LookBlocks.ARENA_PANEL_WHITE.defaultBlockState(); }
	public static BlockState roofGravel() { return LookBlocks.GRAVEL_ROOF.defaultBlockState(); }
	public static BlockState acUnit() { return LookBlocks.AC_UNIT.defaultBlockState(); }
	public static BlockState parapet() { return LookBlocks.PARAPET_CAP.defaultBlockState(); }

	// glass
	/** Temporary switch: look glass/window models fail to bake on main (reported to material_artist); vanilla stand-ins until fixed. */
	static final boolean LOOK_GLASS = true;

	static BlockState vanillaGlass(LookBlocks.GlassTint t) {
		DyeColor c = switch (t.name()) {
			case "TEAL" -> DyeColor.CYAN;
			case "NAVY" -> DyeColor.BLUE;
			case "GREEN" -> DyeColor.GREEN;
			case "GRAPHITE" -> DyeColor.BLACK;
			case "SLATE" -> DyeColor.GRAY;
			default -> DyeColor.LIGHT_BLUE;
		};
		return Blocks.STAINED_GLASS.pick(c).defaultBlockState();
	}

	public static BlockState glass(LookBlocks.GlassTint t) { return LOOK_GLASS ? LookBlocks.glass(t).defaultBlockState() : vanillaGlass(t); }
	public static BlockState glassLit(LookBlocks.GlassTint t) { return LOOK_GLASS ? LookBlocks.window(LookBlocks.glass(t), Glow.ON) : vanillaGlass(t); }
	public static BlockState glassDark(LookBlocks.GlassTint t) { return LOOK_GLASS ? LookBlocks.window(LookBlocks.glass(t), Glow.OFF) : vanillaGlass(t); }
	public static BlockState spandrel(LookBlocks.GlassTint t) { return LookBlocks.spandrel(t).defaultBlockState(); }
	public static BlockState clearGlass() { return LookBlocks.CLEAR_GLASS.defaultBlockState(); }
	public static BlockState railing(Direction out) { return LookBlocks.onWall(LookBlocks.GLASS_RAILING, out); }
	public static BlockState shopfront() { return LOOK_GLASS ? LookBlocks.window(LookBlocks.window(LookBlocks.Window.SHOPFRONT), Glow.ON) : Blocks.GLASS.defaultBlockState(); }
	public static BlockState condoWindow() { return LookBlocks.window(LookBlocks.Window.CONDO).defaultBlockState(); }
	public static BlockState decoWindow() { return LookBlocks.window(LookBlocks.Window.DECO).defaultBlockState(); }
	public static BlockState ribbonWindow() { return LookBlocks.window(LookBlocks.Window.RIBBON).defaultBlockState(); }

	// stucco and Deco
	public static BlockState trimWhite() { return LookBlocks.TRIM_WHITE.defaultBlockState(); }
	public static BlockState trimWhiteSlab() { return slab(LookBlocks.TRIM_WHITE_SLAB, SlabType.BOTTOM); }
	public static BlockState parapetSlab() { return slab(LookBlocks.TRIM_WHITE_SLAB, SlabType.BOTTOM); }
	public static BlockState fence() { return Blocks.DARK_OAK_FENCE.defaultBlockState(); }
	public static BlockState tableTop() { return Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState(); }
	public static BlockState porthole() { return LookBlocks.WINDOW_PORTHOLE.defaultBlockState(); }
	public static BlockState stucco(LookBlocks.Pastel p) { return LookBlocks.stucco(p).defaultBlockState(); }
	public static BlockState stuccoGrimy(LookBlocks.Pastel p) { return LookBlocks.stuccoGrimy(p).defaultBlockState(); }
	public static BlockState condo(LookBlocks.CondoWhite c) { return LookBlocks.condo(c).defaultBlockState(); }
	public static BlockState condoSlab(LookBlocks.CondoWhite c) { return LookBlocks.condoSlab(c).defaultBlockState(); }
	public static BlockState eyebrow(LookBlocks.Accent a, Direction out) { return LookBlocks.onWall(LookBlocks.eyebrow(a), out); }
	public static BlockState fin(LookBlocks.Accent a, Direction out) { return LookBlocks.onWall(LookBlocks.fin(a), out); }
	public static BlockState speedLine(LookBlocks.Accent a, Direction out) { return LookBlocks.onWall(LookBlocks.speedLine(a), out); }
	public static BlockState awning(LookBlocks.Awning a, Direction out) { return LookBlocks.onWall(LookBlocks.awning(a), out); }
	public static BlockState neon(LookBlocks.Neon n, Direction out, boolean vertical) { return LookBlocks.neon(n, out, vertical); }

	// light: emissive full cubes for ribbons, signs and fixtures
	public static BlockState ledCyan() { return LookBlocks.ledPanel(LookBlocks.Led.CYAN).defaultBlockState(); }
	public static BlockState ledGold() { return LookBlocks.ledPanel(LookBlocks.Led.GOLD).defaultBlockState(); }
	public static BlockState ledWhite() { return LookBlocks.ledPanel(LookBlocks.Led.WARM_WHITE).defaultBlockState(); }
	public static BlockState ledRibbon(LookBlocks.Led c, Direction out, boolean vertical) { return LookBlocks.ledRibbon(c, out, vertical); }
	public static BlockState ledWarm() { return Blocks.SHROOMLIGHT.defaultBlockState(); }
	public static BlockState glowstone() { return Blocks.GLOWSTONE.defaultBlockState(); }
	public static BlockState lamp() { return Blocks.SEA_LANTERN.defaultBlockState(); }
	/** Invisible light source (interiors). */
	public static BlockState light(int level) { return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level); }

	// sports
	public static BlockState seat(Seat s, Direction facing) {
		return stairs(LookBlocks.stadiumSeat(LookBlocks.SeatColor.valueOf(s == Seat.BLACK ? "NAVY" : s.name())), facing, Half.BOTTOM);
	}

	public enum Seat { TEAL, NAVY, BURGUNDY, RED, BLACK, GREY }

	public static BlockState court() { return LookBlocks.COURT_WOOD.defaultBlockState(); }
	public static BlockState courtDark() { return LookBlocks.COURT_WOOD.defaultBlockState(); }
	public static BlockState courtLine() { return Blocks.CARPET.pick(DyeColor.WHITE).defaultBlockState(); }
	public static BlockState courtPaint() { return Blocks.CARPET.pick(DyeColor.RED).defaultBlockState(); }
	public static BlockState courtApron() { return Blocks.CONCRETE.pick(DyeColor.BLACK).defaultBlockState(); }
	public static BlockState grass() { return LookBlocks.TURF_GRASS.defaultBlockState(); }
	public static BlockState grassDark() { return LookBlocks.TURF_GRASS_DARK.defaultBlockState(); }
	public static BlockState infield() { return Blocks.TERRACOTTA.defaultBlockState(); }
	public static BlockState warningTrack() { return Blocks.RED_SAND.defaultBlockState(); }
	public static BlockState chalk() { return Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState(); }
	public static BlockState padding() { return Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE).defaultBlockState(); }
	public static BlockState screen() { return Blocks.STAINED_GLASS.pick(DyeColor.BLACK).defaultBlockState(); }
	public static BlockState netting() { return Blocks.COBWEB.defaultBlockState(); }

	// landscape
	public static BlockState sand() { return LookBlocks.BEACH_SAND.defaultBlockState(); }
	public static BlockState deck() { return LookBlocks.WOOD_DECK.defaultBlockState(); }
	public static BlockState hedge() { return Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true); }
	public static BlockState palmLog() { return Blocks.JUNGLE_LOG.defaultBlockState(); }
	public static BlockState water() { return Blocks.WATER.defaultBlockState(); }

	public static BlockState slab(Block b, SlabType t) { return b.defaultBlockState().setValue(SlabBlock.TYPE, t); }

	public static BlockState stairs(Block b, Direction facing, Half half) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
	}
}
