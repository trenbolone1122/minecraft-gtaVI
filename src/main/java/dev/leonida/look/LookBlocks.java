package dev.leonida.look;

import dev.leonida.LeonidaMod;
import dev.leonida.look.block.ChainLinkFenceBlock;
import dev.leonida.look.block.ClearGlassBlock;
import dev.leonida.look.block.CottageDoorBlock;
import dev.leonida.look.block.DoorPanelBlock;
import dev.leonida.look.block.Glow;
import dev.leonida.look.block.KerbBlock;
import dev.leonida.look.block.LookSlab;
import dev.leonida.look.block.LookStairs;
import dev.leonida.look.block.OrientedBlock;
import dev.leonida.look.block.PalmFrondBlock;
import dev.leonida.look.block.PalmTrunkBlock;
import dev.leonida.look.block.ShapedBlock;
import dev.leonida.look.block.SidewalkBlock;
import dev.leonida.look.block.TallApplianceBlock;
import dev.leonida.look.block.WindowBlock;
import dev.leonida.look.block.WindowKind;
import dev.leonida.look.block.WoodRailingBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared GTA VI material blocks (module look). Block ids are {@code leonida:look/<name>}. Palettes are the exact LOOK.md
 * linear colours (textures are generated from them in sRGB). Everything is static: call {@link #init()} once at mod
 * init (LookModule does), then use the accessors from world gen and building code.
 *
 * <h2>What each block is for</h2>
 * <table>
 * <caption>LookBlocks catalogue</caption>
 * <tr><th>accessor</th><th>ids</th><th>use</th></tr>
 * <tr><td>{@link #facade(int)}</td><td>-</td><td>everything a facade style 0..5 needs, by palette index (seed)</td></tr>
 * <tr><td>{@link #glass(GlassTint)}</td><td>glass_teal/navy/graphite/green/slate</td><td>style 0 curtain wall pane, opaque, thin mullions, lights at night (office)</td></tr>
 * <tr><td>{@link #spandrel(GlassTint)}</td><td>spandrel_*</td><td>style 0 dark band, bottom 1 m of each 4 m floor</td></tr>
 * <tr><td>{@link #frame(Frame)}</td><td>frame_dark/steel/silver</td><td>mullion and frame metal: tower corners, 2.2 m cap, lobby frame</td></tr>
 * <tr><td>{@link #window(Window)}</td><td>window_condo/deco/house/ribbon/clerestory/shopfront (+ {@link #WINDOW_COTTAGE}, {@link #WINDOW_PORTHOLE})</td><td>windows for styles 1..5 and shopfronts, opaque, light at night</td></tr>
 * <tr><td>{@link #CLEAR_GLASS}</td><td>clear_glass</td><td>see-through lobby / door glass (translucent)</td></tr>
 * <tr><td>{@link #GLASS_RAILING}</td><td>glass_railing</td><td>style 1 balcony railing, thin panel on the facing edge</td></tr>
 * <tr><td>{@link #stucco(Pastel)}</td><td>stucco_pink..peach (+_grimy)</td><td>style 2 Ocean Drive Deco walls; grimy = bottom 1.5 m</td></tr>
 * <tr><td>{@link #stucco(Earth)}</td><td>stucco_sand..sage (+_grimy)</td><td>style 3 suburban stucco walls</td></tr>
 * <tr><td>{@link #condo(CondoWhite)}</td><td>condo_warm..sand (+_grimy, _slab)</td><td>style 1 white condo walls; slab = balcony slab edge</td></tr>
 * <tr><td>{@link #panel(OfficeGrey)}</td><td>panel_grey/warm/blue/light</td><td>style 4 office cladding with panel joints</td></tr>
 * <tr><td>{@link #corrugated(Metal)}</td><td>metal_silver..green_grey</td><td>style 5 corrugated metal, ribs every 0.2 m</td></tr>
 * <tr><td>{@link #TRIM_WHITE}</td><td>trim_white (+_slab, _stairs)</td><td>white trim, pilasters, Deco top band, window surrounds</td></tr>
 * <tr><td>{@link #eyebrow(Accent)}</td><td>eyebrow_*</td><td>Deco eyebrow ledge over windows (thin projecting plate at the top)</td></tr>
 * <tr><td>{@link #fin(Accent)}</td><td>fin_*</td><td>Deco vertical fin blade, stack it up the facade centre</td></tr>
 * <tr><td>{@link #speedLine(Accent)}</td><td>speed_line_*</td><td>three horizontal speed lines under the parapet, thin wall overlay</td></tr>
 * <tr><td>{@link #awning(Awning)}</td><td>awning_*</td><td>striped sloped awning over shopfronts (3.0 to 3.5 m up)</td></tr>
 * <tr><td>{@link #neon(Neon)}</td><td>neon_pink/cyan/violet/blue/gold/white/mint</td><td>emissive neon tube on a wall, horizontal or vertical, light 10</td></tr>
 * <tr><td>{@link #ROLL_UP_DOOR}</td><td>roll_up_door(_white)</td><td>style 5 roll-up doors, 4 m wide every 14 m</td></tr>
 * <tr><td>{@link #DOOR_BROWN}</td><td>door_brown</td><td>style 3 front door (lower and upper half)</td></tr>
 * <tr><td>{@link #CONCRETE}</td><td>concrete (+_slab, _stairs)</td><td>plinths, decks, piers, viaduct columns, lobby floors</td></tr>
 * <tr><td>roof</td><td>clay_tile(_aged), shingle, metal_roof (+_slab, _stairs); gravel_roof, membrane_roof, parapet_cap, ac_unit</td><td>hipped barrel tile roofs (stairs), flat roofs, parapet caps, rooftop AC</td></tr>
 * <tr><td>{@link #asphalt(Asphalt)}</td><td>asphalt(_worn/_patched/_cracked/_wheelpath), asphalt_slab, asphalt_stairs</td><td>road surface; wheelpath = lighter tyre tracks</td></tr>
 * <tr><td>{@link #marking(Marking, Direction)}</td><td>line_white, line_white_edge, line_yellow, line_yellow_double, stop_bar, zebra, arrow_*</td><td>asphalt with paint, oriented by traffic direction</td></tr>
 * <tr><td>{@link #sidewalk(int, int, SlabType)}</td><td>sidewalk</td><td>sidewalk slab, joints every 1.5 m (state picks joint phase from x, z)</td></tr>
 * <tr><td>{@link #kerb(Direction, SlabType)}</td><td>kerb</td><td>kerb stone, facing the road</td></tr>
 * <tr><td>{@link #JERSEY_BARRIER}</td><td>jersey_barrier</td><td>highway median barrier (axis = road axis)</td></tr>
 * <tr><td>ground</td><td>beach_sand, wet_sand, seabed_sand, coral_stone, terrazzo, tile_floor, marble, wood_deck(+slab, stairs)</td><td>beach, swash zone, shallow seabed, coral rock walls, lobby and pool floors, boardwalks</td></tr>
 * <tr><td>props</td><td>chain_link_fence, utility_pole, utility_pole_crossarm, container_*</td><td>fences, wooden power poles, port containers</td></tr>
 * <tr><td>{@link #stadiumSeat(SeatColor)}</td><td>stadium_seat_teal/navy/burgundy/red/grey</td><td>stadium and arena bowls (stairs shaped seat rows)</td></tr>
 * <tr><td>arena</td><td>roof_membrane_white(+_slab), arena_panel_silver/white(+_slab), court_wood(+_slab), turf_grass(_dark)</td><td>stadium roof skin, arena cladding with faint ribs, maple court, ballpark grass (alternate the two for mowing stripes)</td></tr>
 * <tr><td>{@link #ledRibbon(Led, Direction, boolean)}, {@link #ledPanel(Led)}</td><td>led_ribbon_cyan/gold/warm_white, led_panel_*</td><td>emissive arena facade light lines (thin, wall mounted, light 12) and full sign panels (light 15)</td></tr>
 * <tr><td>{@link #truss(Truss)}</td><td>steel_truss_white/grey</td><td>open lattice for roof trusses, cranes, metro structure (cutout)</td></tr>
 * <tr><td>Keys houses</td><td>siding_teal_weathered(_dark), weathered_wood(+_slab, _stairs), wood_brown(+_slab, _stairs), wood_railing, shutter_grey, lattice_panel, bamboo_blind, door_cottage, door_cottage_openable, window_cottage, tarp_blue, tropical_fabric(+_slab, _stairs), oyster_shell_gravel, panel_cream, siding_teal_lined(_faded), weathered_wood_fence, wood_railing_weathered, trim_white_peeling(+_slab), glass_coffee_table, fridge_white, cooler_white, bucket_white, hose_reel</td><td>board and batten siding (3 random wear variants per position), bleached pilings (axis), deck railing (connects like a fence), wall panels (facing = out), cottage door/window, roof tarp layer, upholstery, shell driveway, cream interior board paneling, teal siding lined with cream paneling on the inside (facing = outward)</td></tr>
 * <tr><td>Palms</td><td>palm_trunk_royal, palm_crownshaft_royal, palm_trunk_coconut, palm_trunk_sabal, palm_crown_(royal|coconut|sabal), palm_frond_(royal|coconut|sabal)</td><td>trunk posts (coconut: facing + shift 0..3 for curved trunks), crown = 3D star of arching fronds (one block, no collision), long fronds for the 8 crown neighbours (facing away from the crown, diagonal); use {@link #palm}</td></tr>
 * </table>
 */
public final class LookBlocks {
	private LookBlocks() {}

	/** A palette entry: LOOK.md linear RGB. */
	public interface Palette {
		double[] linear();

		String id();

		/** Packed sRGB 0xRRGGBB. */
		default int rgb() {
			double[] c = linear();
			return LookColor.srgb(c[0], c[1], c[2]);
		}
	}

	/** Style 2 Deco pastels. */
	public enum Pastel implements Palette {
		PINK(0.98, 0.70, 0.73), MINT(0.60, 0.89, 0.79), BUTTER(0.99, 0.88, 0.56), SKY(0.58, 0.81, 0.96), LAVENDER(0.84, 0.73, 0.94),
		CREAM(0.97, 0.95, 0.90), PEACH(0.99, 0.75, 0.57);

		final double[] c;

		Pastel(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Style 2 accents (eyebrows, fins, speed lines). */
	public enum Accent implements Palette {
		TEAL(0.06, 0.60, 0.62), CORAL(0.95, 0.40, 0.44), GOLD(0.99, 0.77, 0.22), BLUE(0.18, 0.44, 0.86), WHITE(0.97, 0.97, 0.96);

		final double[] c;

		Accent(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Awning stripe colours (paired with white stripes). */
	public enum Awning implements Palette {
		TEAL(0.06, 0.60, 0.62), CORAL(0.95, 0.40, 0.44), GOLD(0.99, 0.77, 0.22), BLUE(0.18, 0.44, 0.86), PINK(1.0, 0.22, 0.72);

		final double[] c;

		Awning(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Style 1 condo whites. */
	public enum CondoWhite implements Palette {
		WARM(0.95, 0.94, 0.91), CREAM(0.94, 0.90, 0.81), COOL(0.87, 0.88, 0.89), AQUA(0.86, 0.92, 0.92), SAND(0.93, 0.87, 0.77);

		final double[] c;

		CondoWhite(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Style 3 suburban earth tones. */
	public enum Earth implements Palette {
		SAND(0.93, 0.85, 0.70), OFF_WHITE(0.96, 0.94, 0.89), TERRACOTTA(0.91, 0.71, 0.58), OCHRE(0.97, 0.88, 0.62), TAUPE(0.83, 0.79, 0.71),
		SAGE(0.80, 0.89, 0.80);

		final double[] c;

		Earth(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Style 4 office greys. */
	public enum OfficeGrey implements Palette {
		GREY(0.71, 0.71, 0.69), WARM(0.79, 0.75, 0.68), BLUE(0.63, 0.64, 0.67), LIGHT(0.85, 0.84, 0.81);

		final double[] c;

		OfficeGrey(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Style 5 warehouse metal. */
	public enum Metal implements Palette {
		SILVER(0.89, 0.90, 0.90), LIGHT(0.77, 0.79, 0.81), BLUE_GREY(0.56, 0.66, 0.76), BEIGE(0.83, 0.79, 0.69), GREEN_GREY(0.67, 0.73, 0.67);

		final double[] c;

		Metal(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Style 0 curtain wall glass tints. */
	public enum GlassTint implements Palette {
		TEAL(0.13, 0.27, 0.31), NAVY(0.08, 0.16, 0.28), GRAPHITE(0.18, 0.19, 0.21), GREEN(0.14, 0.24, 0.19), SLATE(0.24, 0.28, 0.34);

		final double[] c;

		GlassTint(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Mullion and frame metal, dark grey to silver. */
	public enum Frame implements Palette {
		DARK(0.22, 0.24, 0.27), STEEL(0.45, 0.47, 0.50), SILVER(0.75, 0.76, 0.78);

		final double[] c;

		Frame(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Neon tube colours. */
	public enum Neon implements Palette {
		PINK(1.0, 0.22, 0.72), CYAN(0.15, 0.95, 1.0), VIOLET(0.62, 0.28, 1.0), BLUE(0.25, 0.55, 1.0), GOLD(1.0, 0.72, 0.18),
		WHITE(0.92, 0.94, 1.0), MINT(0.30, 1.0, 0.70);

		final double[] c;

		Neon(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Shipping container colours. */
	public enum ContainerColor implements Palette {
		RED(0.45, 0.06, 0.04), BLUE(0.04, 0.12, 0.35), GREEN(0.06, 0.24, 0.10), ORANGE(0.80, 0.25, 0.03), WHITE(0.80, 0.80, 0.78),
		GREY(0.30, 0.32, 0.33);

		final double[] c;

		ContainerColor(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Stadium seat plastic. */
	public enum SeatColor implements Palette {
		TEAL(0.03, 0.42, 0.45), NAVY(0.03, 0.06, 0.18), BURGUNDY(0.25, 0.03, 0.06), RED(0.55, 0.04, 0.04), GREY(0.30, 0.31, 0.32);

		final double[] c;

		SeatColor(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** LED light colours (arena ribbons, sign panels). */
	public enum Led implements Palette {
		CYAN(0.15, 0.95, 1.0), GOLD(1.0, 0.70, 0.20), WARM_WHITE(1.0, 0.85, 0.64);

		final double[] c;

		Led(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Steel truss paint. */
	/**
	 * Palm species. ROYAL: straight smooth grey trunk with a green crownshaft (boulevards). COCONUT: curved ringed trunk
	 * with coconuts (beaches). SABAL: cabbage palm, straight criss-cross booted trunk with a round head of fan leaves.
	 */
	public enum Palm { ROYAL, COCONUT, SABAL }

	public enum Truss implements Palette {
		WHITE(0.85, 0.86, 0.86), GREY(0.40, 0.42, 0.44);

		final double[] c;

		Truss(double... c) {
			this.c = c;
		}

		public double[] linear() {
			return c;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Windows for styles 1..5 and shopfronts. */
	public enum Window {
		/** Style 1 condo: blue-grey reflective, light frame. */
		CONDO(WindowKind.HOME),
		/** Style 2 Deco: dark glass in a thin white frame. */
		DECO(WindowKind.HOME),
		/** Style 3 house: dark glass, white sash cross. */
		HOUSE(WindowKind.HOME),
		/** Style 4 ribbon window: grey-blue with dark mullions. */
		RIBBON(WindowKind.OFFICE),
		/** Style 5 clerestory: frosted, grey-green frame. */
		CLERESTORY(WindowKind.INDUSTRIAL),
		/** Shopfront: big warm pane, lit most nights. */
		SHOPFRONT(WindowKind.SHOP);

		public final WindowKind kind;

		Window(WindowKind kind) {
			this.kind = kind;
		}

		public String id() {
			return name().toLowerCase();
		}
	}

	/** Asphalt surfaces. */
	public enum Asphalt {
		FRESH("asphalt"), WORN("asphalt_worn"), PATCHED("asphalt_patched"), CRACKED("asphalt_cracked"), WHEELPATH("asphalt_wheelpath");

		public final String id;

		Asphalt(String id) {
			this.id = id;
		}
	}

	/**
	 * Road markings, painted on fresh asphalt. All are oriented by the traffic direction passed to
	 * {@link #marking(Marking, Direction)}: lines, zebra bars and stop bars by its axis (state {@code axis} = the direction
	 * traffic flows), arrows by the direction itself, the edge line by the side of the block the line sits on.
	 */
	public enum Marking {
		/** 0.15 m solid or dashed white lane line along the block centre (dash it yourself: 3 on, 9 off). */
		LINE_WHITE("line_white", OrientedBlock.Mode.AXIS),
		/** Solid white edge line 0.2 m from the block side given as the direction (the road edge). */
		LINE_WHITE_EDGE("line_white_edge", OrientedBlock.Mode.TRAVEL),
		/** Single yellow line along the centre. */
		LINE_YELLOW("line_yellow", OrientedBlock.Mode.AXIS),
		/** Double yellow centre line (two 0.1 m lines, 0.1 m apart) along the centre. */
		LINE_YELLOW_DOUBLE("line_yellow_double", OrientedBlock.Mode.AXIS),
		/** 0.5 m white stop bar across the lane. */
		STOP_BAR("stop_bar", OrientedBlock.Mode.AXIS),
		/** Continental zebra crossing: one 0.6 m bar per block, parallel to traffic; fill the crossing with it. */
		ZEBRA("zebra", OrientedBlock.Mode.AXIS),
		/** Straight-ahead arrow pointing the travel direction (arrow is 1 block; use 2 blocks along the lane for a 2 m arrow head + tail: place ARROW_* at the head). */
		ARROW_STRAIGHT("arrow_straight", OrientedBlock.Mode.TRAVEL),
		ARROW_LEFT("arrow_left", OrientedBlock.Mode.TRAVEL),
		ARROW_RIGHT("arrow_right", OrientedBlock.Mode.TRAVEL);

		public final String id;
		public final OrientedBlock.Mode mode;

		Marking(String id, OrientedBlock.Mode mode) {
			this.id = id;
			this.mode = mode;
		}
	}

	// ---------------------------------------------------------------- registry

	private static final List<Block> ALL = new ArrayList<>();
	private static final Map<String, Block> BY_ID = new LinkedHashMap<>();

	private static BlockBehaviour.Properties props(int rgb, SoundType sound, float strength) {
		return BlockBehaviour.Properties.of().mapColor(LookColor.map(rgb)).sound(sound).strength(strength, strength * 3f);
	}

	private static BlockBehaviour.Properties stone(int rgb) {
		return props(rgb, SoundType.STONE, 1.5f);
	}

	private static BlockBehaviour.Properties metal(int rgb) {
		return props(rgb, SoundType.METAL, 2f);
	}

	private static BlockBehaviour.Properties glassProps(int rgb) {
		return props(rgb, SoundType.GLASS, 0.6f);
	}

	private static <B extends Block> B reg(String name, Function<BlockBehaviour.Properties, B> f, BlockBehaviour.Properties props) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, LeonidaMod.id("look/" + name));
		B b = Registry.register(BuiltInRegistries.BLOCK, key, f.apply(props.setId(key)));
		ResourceKey<Item> ik = ResourceKey.create(Registries.ITEM, LeonidaMod.id("look/" + name));
		Registry.register(BuiltInRegistries.ITEM, ik, new BlockItem(b, new Item.Properties().setId(ik).useBlockDescriptionPrefix()));
		ALL.add(b);
		BY_ID.put(name, b);
		return b;
	}

	private static Block cube(String name, BlockBehaviour.Properties p) {
		return reg(name, Block::new, p);
	}

	private static Block slab(String name, BlockBehaviour.Properties p) {
		return reg(name + "_slab", LookSlab::new, p);
	}

	private static Block stairs(String name, Block base, BlockBehaviour.Properties p) {
		return reg(name + "_stairs", q -> new LookStairs(base.defaultBlockState(), q), p);
	}

	private static Block oriented(String name, OrientedBlock.Mode mode, VoxelShape north, VoxelShape vertical, BlockBehaviour.Properties p) {
		return reg(name, q -> new OrientedBlock(mode, north, vertical, q), p);
	}

	private static VoxelShape box(double x0, double y0, double z0, double x1, double y1, double z1) {
		return Block.box(x0, y0, z0, x1, y1, z1);
	}

	// ---------------------------------------------------------------- blocks

	private static final Map<Pastel, Block> STUCCO_PASTEL = new EnumMap<>(Pastel.class), STUCCO_PASTEL_GRIMY = new EnumMap<>(Pastel.class);
	private static final Map<Earth, Block> STUCCO_EARTH = new EnumMap<>(Earth.class), STUCCO_EARTH_GRIMY = new EnumMap<>(Earth.class);
	private static final Map<CondoWhite, Block> CONDO = new EnumMap<>(CondoWhite.class), CONDO_GRIMY = new EnumMap<>(CondoWhite.class),
			CONDO_SLAB = new EnumMap<>(CondoWhite.class);
	private static final Map<OfficeGrey, Block> PANEL = new EnumMap<>(OfficeGrey.class);
	private static final Map<Metal, Block> CORRUGATED = new EnumMap<>(Metal.class);
	private static final Map<GlassTint, Block> GLASS = new EnumMap<>(GlassTint.class), SPANDREL = new EnumMap<>(GlassTint.class);
	private static final Map<Frame, Block> FRAME = new EnumMap<>(Frame.class);
	private static final Map<Window, Block> WINDOW = new EnumMap<>(Window.class);
	private static final Map<Accent, Block> EYEBROW = new EnumMap<>(Accent.class), FIN = new EnumMap<>(Accent.class),
			SPEED_LINE = new EnumMap<>(Accent.class);
	private static final Map<Awning, Block> AWNING = new EnumMap<>(Awning.class);
	private static final Map<Neon, Block> NEON = new EnumMap<>(Neon.class);
	private static final Map<ContainerColor, Block> CONTAINER = new EnumMap<>(ContainerColor.class);
	private static final Map<Asphalt, Block> ASPHALT = new EnumMap<>(Asphalt.class);
	private static final Map<Marking, Block> MARKING = new EnumMap<>(Marking.class);

	public static final Block CLEAR_GLASS, GLASS_RAILING, TRIM_WHITE, TRIM_WHITE_SLAB, TRIM_WHITE_STAIRS, ROLL_UP_DOOR, ROLL_UP_DOOR_WHITE, DOOR_BROWN,
			CONCRETE, CONCRETE_SLAB, CONCRETE_STAIRS;
	public static final Block CLAY_TILE, CLAY_TILE_SLAB, CLAY_TILE_STAIRS, CLAY_TILE_AGED, CLAY_TILE_AGED_SLAB, CLAY_TILE_AGED_STAIRS, SHINGLE,
			SHINGLE_SLAB, SHINGLE_STAIRS, METAL_ROOF, METAL_ROOF_SLAB, METAL_ROOF_STAIRS, GRAVEL_ROOF, MEMBRANE_ROOF, PARAPET_CAP, AC_UNIT;
	public static final Block ASPHALT_SLAB, ASPHALT_STAIRS, SIDEWALK, KERB, JERSEY_BARRIER;
	public static final Block BEACH_SAND, WET_SAND, SEABED_SAND, CORAL_STONE, TERRAZZO, TILE_FLOOR, MARBLE, WOOD_DECK, WOOD_DECK_SLAB, WOOD_DECK_STAIRS;
	public static final Block CHAIN_LINK_FENCE, UTILITY_POLE, UTILITY_POLE_CROSSARM;
	private static final Map<SeatColor, Block> SEAT = new EnumMap<>(SeatColor.class);
	private static final Map<Led, Block> LED_RIBBON = new EnumMap<>(Led.class), LED_PANEL = new EnumMap<>(Led.class);
	private static final Map<Truss, Block> TRUSS = new EnumMap<>(Truss.class);
	public static final Block ROOF_MEMBRANE_WHITE, ROOF_MEMBRANE_WHITE_SLAB, ARENA_PANEL_SILVER, ARENA_PANEL_SILVER_SLAB, ARENA_PANEL_WHITE,
			ARENA_PANEL_WHITE_SLAB, COURT_WOOD, COURT_WOOD_SLAB, TURF_GRASS, TURF_GRASS_DARK;
	public static final Block SIDING_TEAL_WEATHERED, SIDING_TEAL_WEATHERED_DARK, WEATHERED_WOOD, WEATHERED_WOOD_SLAB, WOOD_BROWN, WOOD_BROWN_SLAB,
			WOOD_BROWN_STAIRS, WOOD_RAILING, SHUTTER_GREY, LATTICE_PANEL, BAMBOO_BLIND, DOOR_COTTAGE, TARP_BLUE, TROPICAL_FABRIC, OYSTER_SHELL_GRAVEL;
	/** Keys cottage white two-pane sash and round Deco porthole: window blocks that light at night (homes). */
	public static final Block WINDOW_COTTAGE, WINDOW_PORTHOLE;
	/** Openable cottage door (vanilla door states), tropical fabric sofa slab/stairs, weathered wood stairs (knee braces). */
	/** Palm parts (see {@link #palm}): trunks, royal crownshaft, crown (3D frond star, one block), long fronds for the 8 crown neighbours. */
	public static final Block PALM_TRUNK_ROYAL, PALM_CROWNSHAFT_ROYAL, PALM_TRUNK_COCONUT, PALM_TRUNK_SABAL, PALM_CROWN_ROYAL,
			PALM_CROWN_COCONUT, PALM_CROWN_SABAL, PALM_FROND_ROYAL, PALM_FROND_COCONUT, PALM_FROND_SABAL;
	/** Interior cream board paneling; one block thick teal siding wall with the cream paneling on the inside (facing = outward). */
	public static final Block PANEL_CREAM, SIDING_TEAL_LINED;
	/**
	 * Keys house pass 2: weathered wood fence post (stilts, connects like a fence), weathered deck railing, faded lined
	 * siding (facing = out, 3 random variants), peeling white trim (+slab), glass coffee table, 2 tall white fridge
	 * (facing = front, half), picnic cooler (facing), 5 gallon bucket, wall hose reel (facing = out from the wall).
	 */
	public static final Block WEATHERED_WOOD_FENCE, WOOD_RAILING_WEATHERED, SIDING_TEAL_LINED_FADED, TRIM_WHITE_PEELING,
			TRIM_WHITE_PEELING_SLAB, GLASS_COFFEE_TABLE, FRIDGE_WHITE, COOLER_WHITE, BUCKET_WHITE, HOSE_REEL;
	public static final Block DOOR_COTTAGE_OPENABLE, TROPICAL_FABRIC_SLAB, TROPICAL_FABRIC_STAIRS, WEATHERED_WOOD_STAIRS;

	static {
		// facades
		for (Pastel c : Pastel.values()) {
			STUCCO_PASTEL.put(c, cube("stucco_" + c.id(), stone(c.rgb())));
			STUCCO_PASTEL_GRIMY.put(c, cube("stucco_" + c.id() + "_grimy", stone(c.rgb())));
		}
		for (Earth c : Earth.values()) {
			STUCCO_EARTH.put(c, cube("stucco_" + c.id(), stone(c.rgb())));
			STUCCO_EARTH_GRIMY.put(c, cube("stucco_" + c.id() + "_grimy", stone(c.rgb())));
		}
		for (CondoWhite c : CondoWhite.values()) {
			CONDO.put(c, cube("condo_" + c.id(), stone(c.rgb())));
			CONDO_GRIMY.put(c, cube("condo_" + c.id() + "_grimy", stone(c.rgb())));
			CONDO_SLAB.put(c, slab("condo_" + c.id(), stone(c.rgb())));
		}
		for (OfficeGrey c : OfficeGrey.values()) PANEL.put(c, cube("panel_" + c.id(), stone(c.rgb())));
		for (Metal c : Metal.values()) CORRUGATED.put(c, cube("metal_" + c.id(), metal(c.rgb())));
		for (GlassTint c : GlassTint.values()) {
			GLASS.put(c, reg("glass_" + c.id(), p -> new WindowBlock(WindowKind.OFFICE, p), glassProps(c.rgb())));
			SPANDREL.put(c, cube("spandrel_" + c.id(), glassProps(c.rgb())));
		}
		for (Frame c : Frame.values()) FRAME.put(c, cube("frame_" + c.id(), metal(c.rgb())));
		for (Window w : Window.values()) WINDOW.put(w, reg("window_" + w.id(), p -> new WindowBlock(w.kind, p), glassProps(0x405060)));
		CLEAR_GLASS = reg("clear_glass", ClearGlassBlock::new, glassProps(0x9fc6cc).noOcclusion());
		GLASS_RAILING = oriented("glass_railing", OrientedBlock.Mode.WALL, box(0, 0, 0, 16, 16, 2), null, glassProps(0x9fc6cc).noOcclusion());
		int white = LookColor.srgb(0.97, 0.97, 0.95);
		TRIM_WHITE = cube("trim_white", stone(white));
		TRIM_WHITE_SLAB = slab("trim_white", stone(white));
		TRIM_WHITE_STAIRS = stairs("trim_white", TRIM_WHITE, stone(white));
		for (Accent a : Accent.values()) {
			EYEBROW.put(a, oriented("eyebrow_" + a.id(), OrientedBlock.Mode.WALL, box(0, 13, 6, 16, 16, 16), null, stone(a.rgb()).noOcclusion()));
			FIN.put(a, oriented("fin_" + a.id(), OrientedBlock.Mode.WALL, box(6, 0, 0, 10, 16, 16), null, stone(a.rgb()).noOcclusion()));
			SPEED_LINE.put(a, oriented("speed_line_" + a.id(), OrientedBlock.Mode.WALL, box(0, 0, 15, 16, 16, 16), null,
					stone(a.rgb()).noOcclusion().noCollision()));
		}
		for (Awning a : Awning.values()) {
			AWNING.put(a, oriented("awning_" + a.id(), OrientedBlock.Mode.WALL, box(0, 8, 0, 16, 16, 16), null,
					props(a.rgb(), SoundType.WOOL, 0.8f).noOcclusion()));
		}
		for (Neon n : Neon.values()) {
			NEON.put(n, oriented("neon_" + n.id(), OrientedBlock.Mode.WALL_TUBE, box(0, 6, 12, 16, 10, 16), box(6, 0, 12, 10, 16, 16),
					glassProps(n.rgb()).noOcclusion().noCollision().lightLevel(s -> 5).emissiveRendering(s -> true))); // low block light: the tube glows itself, it must not wash the wall it sits on
		}
		ROLL_UP_DOOR = cube("roll_up_door", metal(0x8a9096));
		ROLL_UP_DOOR_WHITE = cube("roll_up_door_white", metal(0xd8dad8));
		DOOR_BROWN = reg("door_brown", DoorPanelBlock::new, props(0x5a3a22, SoundType.WOOD, 2f));
		int concrete = LookColor.srgb(0.50, 0.49, 0.46);
		CONCRETE = cube("concrete", stone(concrete));
		CONCRETE_SLAB = slab("concrete", stone(concrete));
		CONCRETE_STAIRS = stairs("concrete", CONCRETE, stone(concrete));

		// roofs
		int clay = LookColor.srgb(0.72, 0.36, 0.22), clayAged = LookColor.srgb(0.62, 0.40, 0.30), shingle = 0x5b5b5e, metalRoof = 0x9aa4ab;
		CLAY_TILE = cube("clay_tile", stone(clay));
		CLAY_TILE_SLAB = slab("clay_tile", stone(clay));
		CLAY_TILE_STAIRS = stairs("clay_tile", CLAY_TILE, stone(clay));
		CLAY_TILE_AGED = cube("clay_tile_aged", stone(clayAged));
		CLAY_TILE_AGED_SLAB = slab("clay_tile_aged", stone(clayAged));
		CLAY_TILE_AGED_STAIRS = stairs("clay_tile_aged", CLAY_TILE_AGED, stone(clayAged));
		SHINGLE = cube("shingle", stone(shingle));
		SHINGLE_SLAB = slab("shingle", stone(shingle));
		SHINGLE_STAIRS = stairs("shingle", SHINGLE, stone(shingle));
		METAL_ROOF = cube("metal_roof", metal(metalRoof));
		METAL_ROOF_SLAB = slab("metal_roof", metal(metalRoof));
		METAL_ROOF_STAIRS = stairs("metal_roof", METAL_ROOF, metal(metalRoof));
		GRAVEL_ROOF = cube("gravel_roof", props(0x8d8a84, SoundType.GRAVEL, 1f));
		MEMBRANE_ROOF = cube("membrane_roof", stone(0xd9dbd8));
		PARAPET_CAP = reg("parapet_cap", p -> new ShapedBlock(box(0, 0, 0, 16, 4, 16), p), stone(white));
		AC_UNIT = reg("ac_unit", p -> new ShapedBlock(box(1, 0, 1, 15, 12, 15), p), metal(0xb8bcbc).noOcclusion());

		// roads
		int asphalt = LookColor.srgb(0.045, 0.045, 0.05);
		for (Asphalt a : Asphalt.values()) ASPHALT.put(a, cube(a.id, stone(asphalt)));
		ASPHALT_SLAB = slab("asphalt", stone(asphalt));
		ASPHALT_STAIRS = stairs("asphalt", ASPHALT.get(Asphalt.FRESH), stone(asphalt));
		for (Marking m : Marking.values()) MARKING.put(m, oriented(m.id, m.mode, null, null, stone(asphalt)));
		int sidewalk = LookColor.srgb(0.42, 0.41, 0.39);
		SIDEWALK = reg("sidewalk", SidewalkBlock::new, stone(sidewalk));
		KERB = reg("kerb", KerbBlock::new, stone(LookColor.srgb(0.56, 0.55, 0.52)));
		JERSEY_BARRIER = oriented("jersey_barrier", OrientedBlock.Mode.AXIS,
				Shapes.or(box(3, 0, 0, 13, 4, 16), box(5, 4, 0, 11, 13, 16)), null, stone(LookColor.srgb(0.62, 0.62, 0.60)).noOcclusion());

		// ground
		BEACH_SAND = cube("beach_sand", props(LookColor.srgb(0.86, 0.80, 0.66), SoundType.SAND, 0.5f));
		WET_SAND = cube("wet_sand", props(LookColor.srgb(0.52, 0.45, 0.33), SoundType.SAND, 0.5f));
		SEABED_SAND = cube("seabed_sand", props(LookColor.srgb(0.70, 0.66, 0.50), SoundType.SAND, 0.5f));
		CORAL_STONE = cube("coral_stone", stone(LookColor.srgb(0.80, 0.74, 0.58)));
		TERRAZZO = cube("terrazzo", stone(0xe6e1d8));
		TILE_FLOOR = cube("tile_floor", stone(0xeeeae2));
		MARBLE = cube("marble", stone(0xf0eeea));
		int wood = 0x8a7660;
		WOOD_DECK = cube("wood_deck", props(wood, SoundType.WOOD, 2f));
		WOOD_DECK_SLAB = slab("wood_deck", props(wood, SoundType.WOOD, 2f));
		WOOD_DECK_STAIRS = stairs("wood_deck", WOOD_DECK, props(wood, SoundType.WOOD, 2f));

		// props
		CHAIN_LINK_FENCE = reg("chain_link_fence", ChainLinkFenceBlock::new, metal(0x9a9e9e).noOcclusion());
		UTILITY_POLE = reg("utility_pole", p -> new ShapedBlock(box(5, 0, 5, 11, 16, 11), p), props(0x4a3a2c, SoundType.WOOD, 2f).noOcclusion());
		UTILITY_POLE_CROSSARM = oriented("utility_pole_crossarm", OrientedBlock.Mode.AXIS,
				Shapes.or(box(5, 0, 5, 11, 16, 11), box(6.5, 10, 0, 9.5, 13, 16)), null, props(0x4a3a2c, SoundType.WOOD, 2f).noOcclusion());
		for (ContainerColor c : ContainerColor.values()) CONTAINER.put(c, cube("container_" + c.id(), metal(c.rgb())));

		// stadiums and arenas
		for (SeatColor c : SeatColor.values()) {
			SEAT.put(c, reg("stadium_seat_" + c.id(), q -> new LookStairs(CONCRETE.defaultBlockState(), q), stone(c.rgb())));
		}
		int membrane = LookColor.srgb(0.86, 0.87, 0.86);
		ROOF_MEMBRANE_WHITE = cube("roof_membrane_white", metal(membrane));
		ROOF_MEMBRANE_WHITE_SLAB = slab("roof_membrane_white", metal(membrane));
		int silver = LookColor.srgb(0.70, 0.72, 0.74), panelWhite = LookColor.srgb(0.88, 0.89, 0.88);
		ARENA_PANEL_SILVER = cube("arena_panel_silver", metal(silver));
		ARENA_PANEL_SILVER_SLAB = slab("arena_panel_silver", metal(silver));
		ARENA_PANEL_WHITE = cube("arena_panel_white", metal(panelWhite));
		ARENA_PANEL_WHITE_SLAB = slab("arena_panel_white", metal(panelWhite));
		for (Led c : Led.values()) {
			LED_RIBBON.put(c, oriented("led_ribbon_" + c.id(), OrientedBlock.Mode.WALL_TUBE, box(0, 6.5, 15, 16, 9.5, 16), box(6.5, 0, 15, 9.5, 16, 16),
					glassProps(c.rgb()).noOcclusion().noCollision().lightLevel(s -> 7).emissiveRendering(s -> true)));
			LED_PANEL.put(c, cube("led_panel_" + c.id(), glassProps(c.rgb()).lightLevel(s -> 15).emissiveRendering(s -> true)));
		}
		for (Truss c : Truss.values()) TRUSS.put(c, reg("steel_truss_" + c.id(), ClearGlassBlock::new, metal(c.rgb()).noOcclusion()));
		int maple = LookColor.srgb(0.62, 0.42, 0.22);
		COURT_WOOD = cube("court_wood", props(maple, SoundType.WOOD, 2f));
		COURT_WOOD_SLAB = slab("court_wood", props(maple, SoundType.WOOD, 2f));
		TURF_GRASS = cube("turf_grass", props(LookColor.srgb(0.10, 0.30, 0.05), SoundType.GRASS, 0.6f));
		TURF_GRASS_DARK = cube("turf_grass_dark", props(LookColor.srgb(0.08, 0.23, 0.04), SoundType.GRASS, 0.6f));

		// Keys houses
		int teal = 0x5e9c8c, grey = LookColor.srgb(0.36, 0.34, 0.31), brown = 0x8a5e3c;
		SIDING_TEAL_WEATHERED = cube("siding_teal_weathered", props(teal, SoundType.WOOD, 2f));
		SIDING_TEAL_WEATHERED_DARK = cube("siding_teal_weathered_dark", props(teal, SoundType.WOOD, 2f));
		PANEL_CREAM = cube("panel_cream", props(0xe8e0cc, SoundType.WOOD, 2f));
		SIDING_TEAL_LINED = oriented("siding_teal_lined", OrientedBlock.Mode.WALL, null, null, props(teal, SoundType.WOOD, 2f));
		WEATHERED_WOOD_FENCE = reg("weathered_wood_fence", WoodRailingBlock::new, props(0x8a8170, SoundType.WOOD, 2f));
		WOOD_RAILING_WEATHERED = reg("wood_railing_weathered", WoodRailingBlock::new, props(0x8a8170, SoundType.WOOD, 2f).noOcclusion());
		SIDING_TEAL_LINED_FADED = oriented("siding_teal_lined_faded", OrientedBlock.Mode.WALL, null, null, props(0x86a89c, SoundType.WOOD, 2f));
		TRIM_WHITE_PEELING = cube("trim_white_peeling", props(0xdedbd2, SoundType.WOOD, 2f));
		TRIM_WHITE_PEELING_SLAB = slab("trim_white_peeling", props(0xdedbd2, SoundType.WOOD, 2f));
		GLASS_COFFEE_TABLE = reg("glass_coffee_table", q -> new ShapedBlock(box(0, 0, 0, 16, 7, 16), q),
				props(0x3a2a1e, SoundType.WOOD, 1.5f).noOcclusion());
		FRIDGE_WHITE = reg("fridge_white", q -> new TallApplianceBlock(box(1, 0, 2, 15, 16, 16), box(1, 0, 2, 15, 15, 16), q),
				props(0xeeeeea, SoundType.METAL, 2f).noOcclusion());
		COOLER_WHITE = oriented("cooler_white", OrientedBlock.Mode.TRAVEL, box(2, 0, 4, 14, 8, 12), null,
				props(0xe8ecef, SoundType.STONE, 1f).noOcclusion());
		BUCKET_WHITE = reg("bucket_white", q -> new ShapedBlock(box(4, 0, 4, 12, 10, 12), q), props(0xeeeeea, SoundType.STONE, 0.6f).noOcclusion());
		HOSE_REEL = oriented("hose_reel", OrientedBlock.Mode.WALL, box(2, 2, 7, 14, 14, 16), null, props(0x2f7a32, SoundType.STONE, 1f).noOcclusion());
		WEATHERED_WOOD = reg("weathered_wood", RotatedPillarBlock::new, props(grey, SoundType.WOOD, 2f));
		WEATHERED_WOOD_SLAB = slab("weathered_wood", props(grey, SoundType.WOOD, 2f));
		WOOD_BROWN = cube("wood_brown", props(brown, SoundType.WOOD, 2f));
		WOOD_BROWN_SLAB = slab("wood_brown", props(brown, SoundType.WOOD, 2f));
		WOOD_BROWN_STAIRS = stairs("wood_brown", WOOD_BROWN, props(brown, SoundType.WOOD, 2f));
		WOOD_RAILING = reg("wood_railing", WoodRailingBlock::new, props(brown, SoundType.WOOD, 2f).noOcclusion());
		SHUTTER_GREY = oriented("shutter_grey", OrientedBlock.Mode.WALL, box(0, 0, 14.5, 16, 16, 16), null,
				props(LookColor.srgb(0.30, 0.38, 0.44), SoundType.WOOD, 1f).noOcclusion());
		LATTICE_PANEL = oriented("lattice_panel", OrientedBlock.Mode.WALL, box(0, 0, 15, 16, 16, 16), null,
				props(LookColor.srgb(0.72, 0.72, 0.70), SoundType.WOOD, 1f).noOcclusion());
		BAMBOO_BLIND = oriented("bamboo_blind", OrientedBlock.Mode.WALL, box(0, 0, 15.5, 16, 16, 16), null,
				props(LookColor.srgb(0.55, 0.40, 0.20), SoundType.BAMBOO, 0.5f).noOcclusion().noCollision());
		WINDOW_COTTAGE = reg("window_cottage", p -> new WindowBlock(WindowKind.HOME, p), glassProps(0xe0e0dc));
		WINDOW_PORTHOLE = reg("window_porthole", p -> new WindowBlock(WindowKind.HOME, p), glassProps(0xf0f0ee));
		DOOR_COTTAGE = reg("door_cottage", DoorPanelBlock::new, props(0x2f6f62, SoundType.WOOD, 2f));
		TARP_BLUE = reg("tarp_blue", p -> new ShapedBlock(box(0, 0, 0, 16, 1, 16), p), props(LookColor.srgb(0.03, 0.18, 0.55), SoundType.WOOL, 0.2f)
				.noOcclusion());
		TROPICAL_FABRIC = cube("tropical_fabric", props(LookColor.srgb(0.05, 0.25, 0.08), SoundType.WOOL, 0.8f));
		DOOR_COTTAGE_OPENABLE = reg("door_cottage_openable", CottageDoorBlock::new, props(0x2f6f62, SoundType.WOOD, 3f).noOcclusion());
		TROPICAL_FABRIC_SLAB = slab("tropical_fabric", props(LookColor.srgb(0.05, 0.25, 0.08), SoundType.WOOL, 0.8f));
		TROPICAL_FABRIC_STAIRS = stairs("tropical_fabric", TROPICAL_FABRIC, props(LookColor.srgb(0.05, 0.25, 0.08), SoundType.WOOL, 0.8f));
		WEATHERED_WOOD_STAIRS = stairs("weathered_wood", WEATHERED_WOOD, props(grey, SoundType.WOOD, 2f));
		PALM_TRUNK_ROYAL = reg("palm_trunk_royal", q -> new ShapedBlock(box(3, 0, 3, 13, 16, 13), q), props(0xa9a49a, SoundType.WOOD, 2f));
		PALM_CROWNSHAFT_ROYAL = reg("palm_crownshaft_royal", q -> new ShapedBlock(box(3, 0, 3, 13, 16, 13), q), props(0x5f8a3a, SoundType.WOOD, 1.5f));
		PALM_TRUNK_COCONUT = reg("palm_trunk_coconut", q -> new PalmTrunkBlock(8, q), props(0x7a6248, SoundType.WOOD, 2f));
		PALM_TRUNK_SABAL = reg("palm_trunk_sabal", q -> new ShapedBlock(box(2, 0, 2, 14, 16, 14), q), props(0x5a4a38, SoundType.WOOD, 2f));
		PALM_CROWN_SABAL = reg("palm_crown_sabal", q -> new ShapedBlock(box(4, 0, 4, 12, 12, 12), q),
				props(0x3d5530, SoundType.GRASS, 0.3f).noCollision().noOcclusion());
		PALM_FROND_SABAL = reg("palm_frond_sabal", PalmFrondBlock::new, props(0x3d5530, SoundType.GRASS, 0.2f).noCollision().noOcclusion());
		PALM_CROWN_ROYAL = reg("palm_crown_royal", q -> new ShapedBlock(box(4, 0, 4, 12, 12, 12), q),
				props(0x2f5a1e, SoundType.GRASS, 0.3f).noCollision().noOcclusion());
		PALM_CROWN_COCONUT = reg("palm_crown_coconut", q -> new ShapedBlock(box(4, 0, 4, 12, 12, 12), q),
				props(0x4c6a22, SoundType.GRASS, 0.3f).noCollision().noOcclusion());
		PALM_FROND_ROYAL = reg("palm_frond_royal", PalmFrondBlock::new, props(0x2f5a1e, SoundType.GRASS, 0.2f).noCollision().noOcclusion());
		PALM_FROND_COCONUT = reg("palm_frond_coconut", PalmFrondBlock::new, props(0x4c6a22, SoundType.GRASS, 0.2f).noCollision().noOcclusion());
		OYSTER_SHELL_GRAVEL = cube("oyster_shell_gravel", props(LookColor.srgb(0.80, 0.78, 0.72), SoundType.GRAVEL, 0.6f));
	}

	/** Forces class init (registration). Called from LookModule.init(). */
	public static void init() {}

	// ---------------------------------------------------------------- accessors

	/** Every look block, registration order (creative tab, tests). */
	public static List<Block> all() {
		return Collections.unmodifiableList(ALL);
	}

	/** Block by short name (without the {@code look/} prefix), or null. */
	public static Block byName(String name) {
		return BY_ID.get(name);
	}

	public static Block stucco(Pastel c) {
		return STUCCO_PASTEL.get(c);
	}

	public static Block stuccoGrimy(Pastel c) {
		return STUCCO_PASTEL_GRIMY.get(c);
	}

	public static Block stucco(Earth c) {
		return STUCCO_EARTH.get(c);
	}

	public static Block stuccoGrimy(Earth c) {
		return STUCCO_EARTH_GRIMY.get(c);
	}

	public static Block condo(CondoWhite c) {
		return CONDO.get(c);
	}

	public static Block condoGrimy(CondoWhite c) {
		return CONDO_GRIMY.get(c);
	}

	public static Block condoSlab(CondoWhite c) {
		return CONDO_SLAB.get(c);
	}

	public static Block panel(OfficeGrey c) {
		return PANEL.get(c);
	}

	public static Block corrugated(Metal c) {
		return CORRUGATED.get(c);
	}

	public static Block glass(GlassTint c) {
		return GLASS.get(c);
	}

	public static Block spandrel(GlassTint c) {
		return SPANDREL.get(c);
	}

	public static Block frame(Frame c) {
		return FRAME.get(c);
	}

	public static Block window(Window w) {
		return WINDOW.get(w);
	}

	/** Window or curtain wall state with a forced glow mode (AUTO = lit at night by position hash). */
	public static BlockState window(Block windowBlock, Glow glow) {
		return windowBlock.defaultBlockState().setValue(WindowBlock.GLOW, glow);
	}

	public static Block eyebrow(Accent a) {
		return EYEBROW.get(a);
	}

	public static Block fin(Accent a) {
		return FIN.get(a);
	}

	public static Block speedLine(Accent a) {
		return SPEED_LINE.get(a);
	}

	public static Block awning(Awning a) {
		return AWNING.get(a);
	}

	public static Block neon(Neon n) {
		return NEON.get(n);
	}

	public static Block container(ContainerColor c) {
		return CONTAINER.get(c);
	}

	/**
	 * State for a wall-mounted block (eyebrow, fin, speed line, awning, railing, neon) on a wall whose outward normal is
	 * {@code out} (the block sits in front of the wall, on the street side).
	 */
	public static BlockState onWall(Block b, Direction out) {
		return b.defaultBlockState().setValue(OrientedBlock.FACING, out);
	}

	/** Neon tube on a wall facing {@code out}, running horizontally or vertically. */
	public static BlockState neon(Neon n, Direction out, boolean vertical) {
		return onWall(NEON.get(n), out).setValue(OrientedBlock.VERTICAL, vertical);
	}

	public static Block asphalt(Asphalt a) {
		return ASPHALT.get(a);
	}

	public static Block marking(Marking m) {
		return MARKING.get(m);
	}

	/**
	 * Marking state. {@code dir}: for AXIS markings any direction along the traffic flow; for arrows the travel
	 * direction; for LINE_WHITE_EDGE the side of the block where the road edge is.
	 */
	public static BlockState marking(Marking m, Direction dir) {
		BlockState s = MARKING.get(m).defaultBlockState();
		return m.mode == OrientedBlock.Mode.AXIS ? s.setValue(OrientedBlock.AXIS, dir.getAxis()) : s.setValue(OrientedBlock.FACING, dir);
	}

	/** Sidewalk at world x, z (joint phase), as a bottom slab, top slab or full block (DOUBLE). */
	public static BlockState sidewalk(int x, int z, SlabType type) {
		return ((SidewalkBlock) SIDEWALK).at(SIDEWALK.defaultBlockState().setValue(SidewalkBlock.TYPE, type), x, z);
	}

	/** Kerb facing the road. */
	public static BlockState kerb(Direction toRoad, SlabType type) {
		return KERB.defaultBlockState().setValue(KerbBlock.FACING, toRoad).setValue(KerbBlock.TYPE, type);
	}

	/** Jersey barrier along a road axis. */
	public static BlockState jerseyBarrier(Direction.Axis roadAxis) {
		return JERSEY_BARRIER.defaultBlockState().setValue(OrientedBlock.AXIS, roadAxis);
	}

	/** Door panel half. */
	public static BlockState door(boolean upper) {
		return DOOR_BROWN.defaultBlockState().setValue(DoorPanelBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
	}

	/** Stadium seat row (stairs block: facing, half, shape like vanilla stairs). */
	public static Block stadiumSeat(SeatColor c) {
		return SEAT.get(c);
	}

	/** Thin emissive LED ribbon block (wall mounted like neon, light 12). */
	public static Block ledRibbon(Led c) {
		return LED_RIBBON.get(c);
	}

	/** LED ribbon on a wall facing {@code out}, horizontal or vertical. */
	public static BlockState ledRibbon(Led c, Direction out, boolean vertical) {
		return onWall(LED_RIBBON.get(c), out).setValue(OrientedBlock.VERTICAL, vertical);
	}

	/** Full emissive LED sign panel (light 15). */
	public static Block ledPanel(Led c) {
		return LED_PANEL.get(c);
	}

	/** Open lattice steel truss (cutout, full cube). */
	public static Block palmTrunk(Palm p) {
		return switch (p) {
			case ROYAL -> PALM_TRUNK_ROYAL;
			case COCONUT -> PALM_TRUNK_COCONUT;
			case SABAL -> PALM_TRUNK_SABAL;
		};
	}

	public static Block palmCrown(Palm p) {
		return switch (p) {
			case ROYAL -> PALM_CROWN_ROYAL;
			case COCONUT -> PALM_CROWN_COCONUT;
			case SABAL -> PALM_CROWN_SABAL;
		};
	}

	/** Long frond for a crown neighbour: facing points away from the crown; diagonal = between facing and its clockwise side. */
	public static BlockState palmFrond(Palm p, Direction facing, boolean diagonal) {
		Block b = switch (p) {
			case ROYAL -> PALM_FROND_ROYAL;
			case COCONUT -> PALM_FROND_COCONUT;
			case SABAL -> PALM_FROND_SABAL;
		};
		return b.defaultBlockState()
				.setValue(PalmFrondBlock.FACING, facing).setValue(PalmFrondBlock.DIAGONAL, diagonal);
	}

	/** Coconut trunk segment drawn {@code 4 * shift} px toward lean (shift 0..3). */
	public static BlockState palmTrunkCoconut(Direction lean, int shift) {
		return PALM_TRUNK_COCONUT.defaultBlockState().setValue(PalmTrunkBlock.FACING, lean).setValue(PalmTrunkBlock.SHIFT, shift);
	}

	/**
	 * Grows a whole palm with its trunk base at {@code base}: {@code height} trunk blocks (min 3), then the crown, plus
	 * long fronds in the 4 side neighbours (and the 4 diagonals when {@code big}). Royal palms are straight with a 2 block
	 * green crownshaft; sabal palms are straight; coconut palms curve toward {@code lean} by 1 to 4 blocks (seeded,
	 * at most height / 2.5). Deterministic from {@code seed}. Works on any LevelAccessor (WorldGenLevel in decoration):
	 * only replaces air or replaceable blocks, flag 2 (no neighbour updates). Returns the crown position.
	 */
	public static net.minecraft.core.BlockPos palm(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos base,
			Palm kind, int height, Direction lean, boolean big, long seed) {
		int h = Math.max(3, height);
		net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
		long r = (seed ^ 0x5DEECE66DL) * 0x9E3779B97F4A7C15L;
		int maxLean = Math.max(16, Math.min(64, (int) (h * 16 / 2.5)));
		int total = kind == Palm.COCONUT ? 16 + (int) Math.floorMod(r >>> 17, (long) (maxLean - 16 + 1)) : 0;
		int col = 0;
		for (int k = 0; k < h; k++) {
			BlockState st;
			if (kind == Palm.COCONUT) {
				double t = (double) k / h;
				int off = (int) Math.round(total * t * t / 4.0) * 4;
				col = off / 16;
				st = palmTrunkCoconut(lean, (off % 16) / 4);
			} else if (kind == Palm.ROYAL && k >= h - 2 && h >= 6) {
				st = PALM_CROWNSHAFT_ROYAL.defaultBlockState();
			} else {
				st = palmTrunk(kind).defaultBlockState();
			}
			m.set(base.getX() + lean.getStepX() * col, base.getY() + k, base.getZ() + lean.getStepZ() * col);
			place(level, m, st);
		}
		if (kind == Palm.COCONUT) col = ((int) Math.round(total / 4.0) * 4 + 8) / 16;
		net.minecraft.core.BlockPos crown = base.offset(lean.getStepX() * col, h, lean.getStepZ() * col);
		place(level, crown, palmCrown(kind).defaultBlockState());
		for (Direction d : Direction.Plane.HORIZONTAL) {
			place(level, crown.relative(d), palmFrond(kind, d, false));
			if (big) place(level, crown.relative(d).relative(d.getClockWise()), palmFrond(kind, d, true));
		}
		return crown;
	}

	private static void place(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos p, BlockState s) {
		if (level.getBlockState(p).canBeReplaced()) level.setBlock(p, s, 2);
	}


	public static Block truss(Truss c) {
		return TRUSS.get(c);
	}

	/** Cottage door half. */
	public static BlockState doorCottage(boolean upper) {
		return DOOR_COTTAGE.defaultBlockState().setValue(DoorPanelBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
	}

	// ---------------------------------------------------------------- facade sets

	private static final FacadeSet[] FACADES = new FacadeSet[6];

	/** The block set for facade style 0..5 (LOOK.md table). Pick colours with a per-building seed index. */
	public static FacadeSet facade(int style) {
		if (style < 0 || style > 5) throw new IllegalArgumentException("facade style " + style);
		if (FACADES[style] == null) FACADES[style] = buildFacade(style);
		return FACADES[style];
	}

	private static <K extends Enum<K>> List<Block> list(Map<K, Block> m) {
		return List.copyOf(m.values());
	}

	private static FacadeSet buildFacade(int style) {
		List<Block> frames = list(FRAME);
		List<Block> accents = list(EYEBROW);
		return switch (style) {
			case 0 -> new FacadeSet(0, 4, 1.5, list(GLASS), list(GLASS), list(GLASS), list(SPANDREL), frames, FRAME.get(Frame.SILVER),
					CLEAR_GLASS, List.of(), List.of(), List.of(), List.of());
			case 1 -> new FacadeSet(1, 3, 4.2, list(CONDO), list(CONDO_GRIMY), List.of(WINDOW.get(Window.CONDO)), list(CONDO_SLAB),
					List.of(TRIM_WHITE), TRIM_WHITE, CLEAR_GLASS, List.of(), List.of(), List.of(), List.of());
			case 2 -> new FacadeSet(2, 3, 3.2, list(STUCCO_PASTEL), list(STUCCO_PASTEL_GRIMY), List.of(WINDOW.get(Window.DECO)),
					List.of(TRIM_WHITE), List.of(TRIM_WHITE), TRIM_WHITE, WINDOW.get(Window.SHOPFRONT), accents, list(FIN), list(SPEED_LINE), list(AWNING));
			case 3 -> new FacadeSet(3, 3, 3.8, list(STUCCO_EARTH), list(STUCCO_EARTH_GRIMY), List.of(WINDOW.get(Window.HOUSE)),
					List.of(TRIM_WHITE), List.of(TRIM_WHITE), PARAPET_CAP, WINDOW.get(Window.SHOPFRONT), List.of(), List.of(), List.of(), List.of());
			case 4 -> new FacadeSet(4, 4, 1.6, list(PANEL), list(PANEL), List.of(WINDOW.get(Window.RIBBON)), list(PANEL),
					List.of(FRAME.get(Frame.DARK)), FRAME.get(Frame.DARK), WINDOW.get(Window.SHOPFRONT), List.of(), List.of(), List.of(), List.of());
			default -> new FacadeSet(5, 6, 4.0, list(CORRUGATED), list(CORRUGATED), List.of(WINDOW.get(Window.CLERESTORY)), List.of(CONCRETE),
					List.of(FRAME.get(Frame.STEEL)), CONCRETE, ROLL_UP_DOOR, List.of(), List.of(), List.of(), List.of());
		};
	}

	/**
	 * Blocks for one facade style. Index arguments wrap (use a per-building seed). Wall and window indices line up for
	 * style 0 (glass i and spandrel i share a tint).
	 *
	 * @param floorHeight blocks per floor (4.0 m becomes 4, 3.2 m becomes 3)
	 * @param bayWidth LOOK.md bay width in metres
	 * @param walls main wall blocks (style 0: the glass itself)
	 * @param grimyWalls same palette with darker dirt, for the bottom 1.5 m
	 * @param windows window blocks
	 * @param bands spandrel (0), balcony slab edge (1), trim (2, 3), panel (4), plinth (5)
	 * @param frames frame or mullion blocks
	 * @param trim cap / trim / pilaster block (parapet cap for style 3)
	 * @param ground lobby glass (0, 1), shopfront (2, 3, 4) or roll-up door (5)
	 */
	public record FacadeSet(int style, int floorHeight, double bayWidth, List<Block> walls, List<Block> grimyWalls, List<Block> windows,
			List<Block> bands, List<Block> frames, Block trim, Block ground, List<Block> eyebrows, List<Block> fins, List<Block> speedLines,
			List<Block> awnings) {
		private static Block pick(List<Block> l, int i) {
			return l.isEmpty() ? null : l.get(Math.floorMod(i, l.size()));
		}

		public Block wall(int i) {
			return pick(walls, i);
		}

		public Block grimyWall(int i) {
			return pick(grimyWalls, i);
		}

		public Block window(int i) {
			return pick(windows, i);
		}

		public Block band(int i) {
			return pick(bands, i);
		}

		public Block frame(int i) {
			return pick(frames, i);
		}

		/** Style 2 only (else null): accent pieces by accent index. */
		public Block eyebrow(int i) {
			return pick(eyebrows, i);
		}

		public Block fin(int i) {
			return pick(fins, i);
		}

		public Block speedLine(int i) {
			return pick(speedLines, i);
		}

		public Block awning(int i) {
			return pick(awnings, i);
		}
	}
}
