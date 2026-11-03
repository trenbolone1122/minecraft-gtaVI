package dev.leonida.build;

import dev.leonida.look.LookBlocks;
import dev.leonida.look.LookBlocks.Accent;
import dev.leonida.look.LookBlocks.Awning;
import dev.leonida.look.LookBlocks.CondoWhite;
import dev.leonida.look.LookBlocks.Earth;
import dev.leonida.look.LookBlocks.Frame;
import dev.leonida.look.LookBlocks.GlassTint;
import dev.leonida.look.LookBlocks.Metal;
import dev.leonida.look.LookBlocks.Neon;
import dev.leonida.look.LookBlocks.OfficeGrey;
import dev.leonida.look.LookBlocks.Pastel;
import dev.leonida.look.LookBlocks.Window;
import dev.leonida.look.block.Glow;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Every facade material in one place (LOOK.md styles 0..5), backed by the look module's blocks
 * (dev.leonida.look.LookBlocks). Facade code asks this class only, so material swaps are one file.
 */
final class Palette {
	private Palette() {}

	static BlockState st(Block b) { return b.defaultBlockState(); }

	static BlockState slab(Block b, boolean top) {
		return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState stairs(Block b, Direction facing, boolean top) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static <E extends Enum<E>> E pick(Class<E> c, int i) {
		E[] v = c.getEnumConstants();
		return v[Math.floorMod(i, v.length)];
	}

	/**
	 * Look window blocks (opaque, glow at night). False = vanilla stained glass stand-ins while the look module's window
	 * models are broken (missing glow variants on look@0559f49).
	 */
	static final boolean LOOK_WINDOWS = true;

	private static final String[] TOWER_VANILLA = {"cyan_stained_glass", "blue_stained_glass", "gray_stained_glass", "green_stained_glass", "light_gray_stained_glass"};

	// style 0
	static BlockState towerGlass(int tone) {
		if (!LOOK_WINDOWS) return B.of(TOWER_VANILLA[Math.floorMod(tone, TOWER_VANILLA.length)]);
		return st(LookBlocks.glass(pick(GlassTint.class, tone)));
	}

	static BlockState spandrel(int tone) { return st(LookBlocks.spandrel(pick(GlassTint.class, tone))); }

	static BlockState frame(int i) { return st(LookBlocks.frame(pick(Frame.class, i))); }

	static BlockState clearGlass() { return st(LookBlocks.CLEAR_GLASS); }

	// style 1
	static BlockState condo(int i, boolean grimy) {
		CondoWhite c = pick(CondoWhite.class, i);
		return st(grimy ? LookBlocks.condoGrimy(c) : LookBlocks.condo(c));
	}

	static BlockState condoSlab(int i) { return st(LookBlocks.condoSlab(pick(CondoWhite.class, i))); }

	static BlockState railing(Direction out) { return LookBlocks.onWall(LookBlocks.GLASS_RAILING, out); }

	// style 2
	static BlockState pastel(int i, boolean grimy) {
		Pastel c = pick(Pastel.class, i);
		return st(grimy ? LookBlocks.stuccoGrimy(c) : LookBlocks.stucco(c));
	}

	static BlockState eyebrow(int accent, Direction out) { return LookBlocks.onWall(LookBlocks.eyebrow(pick(Accent.class, accent)), out); }

	static BlockState fin(int accent, Direction out) { return LookBlocks.onWall(LookBlocks.fin(pick(Accent.class, accent)), out); }

	static BlockState speedLine(int accent, Direction out) { return LookBlocks.onWall(LookBlocks.speedLine(pick(Accent.class, accent)), out); }

	static BlockState awning(int i, Direction out) { return LookBlocks.onWall(LookBlocks.awning(pick(Awning.class, i)), out); }

	static BlockState neon(int i, Direction out, boolean vertical) { return LookBlocks.neon(pick(Neon.class, i), out, vertical); }

	/** Neon as a solid glowing block (vanilla) for crowns where no wall is behind the tube. */
	static BlockState neonBlock(int i) { return Math.floorMod(i, 2) == 0 ? B.of("pearlescent_froglight") : B.of("sea_lantern"); }

	// style 3
	static BlockState earth(int i, boolean grimy) {
		Earth c = pick(Earth.class, i);
		return st(grimy ? LookBlocks.stuccoGrimy(c) : LookBlocks.stucco(c));
	}

	static BlockState trim() { return st(LookBlocks.TRIM_WHITE); }

	static BlockState trimSlab(boolean top) { return slab(LookBlocks.TRIM_WHITE_SLAB, top); }

	static BlockState parapetCap() { return st(LookBlocks.PARAPET_CAP); }

	static Block tileSlab(boolean aged) { return aged ? LookBlocks.CLAY_TILE_AGED_SLAB : LookBlocks.CLAY_TILE_SLAB; }

	static Block tileStairs(boolean aged) { return aged ? LookBlocks.CLAY_TILE_AGED_STAIRS : LookBlocks.CLAY_TILE_STAIRS; }

	static BlockState tileFull(boolean aged) { return st(aged ? LookBlocks.CLAY_TILE_AGED : LookBlocks.CLAY_TILE); }

	static BlockState brownDoor(boolean upper) { return LookBlocks.door(upper); }

	// style 4
	static BlockState panel(int i) { return st(LookBlocks.panel(pick(OfficeGrey.class, i))); }

	// style 5
	static BlockState metal(int i) { return st(LookBlocks.corrugated(pick(Metal.class, i))); }

	static BlockState concrete() { return st(LookBlocks.CONCRETE); }

	static BlockState concreteSlab(boolean top) { return slab(LookBlocks.CONCRETE_SLAB, top); }

	static BlockState rollUp(boolean white) { return st(white ? LookBlocks.ROLL_UP_DOOR_WHITE : LookBlocks.ROLL_UP_DOOR); }

	// windows (opaque, light at night by themselves when glow = AUTO)
	static BlockState window(Window w) {
		if (!LOOK_WINDOWS) return B.of(w == Window.CONDO ? "light_blue_stained_glass" : w.kind == dev.leonida.look.block.WindowKind.SHOP ? "glass" : "gray_stained_glass");
		return st(LookBlocks.window(w));
	}

	static BlockState windowLit(Window w) { return LOOK_WINDOWS ? LookBlocks.window(LookBlocks.window(w), Glow.ON) : window(w); }

	// roofs
	static BlockState membrane() { return st(LookBlocks.MEMBRANE_ROOF); }

	static BlockState gravelRoof() { return st(LookBlocks.GRAVEL_ROOF); }

	static BlockState acUnit() { return st(LookBlocks.AC_UNIT); }

	static BlockState metalRoof() { return st(LookBlocks.METAL_ROOF); }
	static BlockState cottageWindow() { return LOOK_WINDOWS ? st(LookBlocks.WINDOW_COTTAGE) : B.of("white_stained_glass"); }

	static Block metalRoofSlab() { return LookBlocks.METAL_ROOF_SLAB; }

	static BlockState siding(boolean dark) { return st(dark ? LookBlocks.SIDING_TEAL_WEATHERED_DARK : LookBlocks.SIDING_TEAL_WEATHERED); }

	static BlockState terrazzo() { return st(LookBlocks.TERRAZZO); }

	static BlockState tileFloor() { return st(LookBlocks.TILE_FLOOR); }

	static BlockState woodDeck() { return st(LookBlocks.WOOD_DECK); }

	/** Ceiling light that fits a 3 block floor. */
	static BlockState ceilingWarm() { return B.of("pearlescent_froglight"); }
}
