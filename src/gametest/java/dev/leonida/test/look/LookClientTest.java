package dev.leonida.test.look;

import dev.leonida.look.LookBlocks;
import dev.leonida.look.LookBlocks.Accent;
import dev.leonida.look.LookBlocks.Asphalt;
import dev.leonida.look.LookBlocks.Awning;
import dev.leonida.look.LookBlocks.CondoWhite;
import dev.leonida.look.LookBlocks.Frame;
import dev.leonida.look.LookBlocks.GlassTint;
import dev.leonida.look.LookBlocks.Marking;
import dev.leonida.look.LookBlocks.Neon;
import dev.leonida.look.LookBlocks.Pastel;
import dev.leonida.look.LookBlocks.Window;
import dev.leonida.look.client.NightWindows;
import dev.leonida.test.TestFilter;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Client game tests for the look module. Owned by the look agent. Run: tools/pod-test.sh look
 *
 * <p>Builds a showcase street in the flat test world (north to south): a style 0 teal glass tower, a style 1 white
 * condo with balconies and a style 2 pink Deco hotel with eyebrows, fin, neon, speed lines and awning; a sidewalk with
 * kerbs; a two way road with edge lines, dashed lane lines, double yellow centre, stop bars, arrows and a zebra
 * crossing; a beach with wet sand and the sea. Screenshots from three cameras at noon, golden hour, dusk and night.
 */
public class LookClientTest implements FabricClientGameTest {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	@Override
	public void runTest(ClientGameTestContext context) {
		String only = System.getProperty("leonida.test");
		if ("lookneon".equals(only) || "lookneoncount".equals(only)) { LookNeonShots.run(context, "lookneoncount".equals(only), "lookneon".equals(only)); return; }
		if (!TestFilter.enabled("look")) return;
		checkModels(context);
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			TestServerContext server = sp.getServer();
			BlockPos o = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).blockPosition());
			int x0 = o.getX(), g = o.getY() - 1, z0 = o.getZ();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("gamerule spawn_mobs false");
			server.runCommand("weather clear");
			server.runCommand("gamemode spectator @a");
			server.runOnServer(s -> buildStreet(s.overworld(), x0, g, z0));
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
			});
			int[][] cams = {
					// x, y, z, yaw, pitch (relative to origin / ground)
					{31, 9, 19, 128, -6}, // over the beach, looking north-west at the whole street
					{27, 3, 2, 90, 8}, // low down the road from the east
					{-2, 5, 34, 180, -10}, // from the sea toward the beach and city
			};
			String[] camNames = {"street", "road", "beach"};
			int[][] times = {{6000}, {11500}, {12700}, {18000}};
			String[] timeNames = {"noon", "golden", "dusk", "night"};
			for (int t = 0; t < times.length; t++) {
				server.runCommand("time set " + times[t][0]);
				for (int c = 0; c < cams.length; c++) {
					int[] k = cams[c];
					server.runCommand(String.format("tp @a %d.5 %d %d.5 %d %d", x0 + k[0], g + k[1], z0 + k[2], k[3], k[4]));
					context.waitTicks(c == 0 ? 30 : 15);
					sp.getConnection().waitForChunksRender();
					context.waitTicks(5);
					int stage = context.computeOnClient(mc -> NightWindows.stage());
					System.out.println("[leonida-test] look " + camNames[c] + "-" + timeNames[t] + " night stage " + stage);
					context.takeScreenshot("look-" + camNames[c] + "-" + timeNames[t]);
				}
			}
			// close-up of the hotel shopfront and neon at night, and of the road paint at noon
			server.runCommand(String.format("tp @a %d.5 %d %d.5 %d %d", x0 + 15, g + 4, z0 + 3, 180, -8));
			context.waitTicks(15);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("look-hotel-night");
			server.runCommand("time set 6000");
			server.runCommand(String.format("tp @a %d.5 %d %d.5 %d %d", x0 + 6, g + 5, z0 + 2, 90, 45));
			context.waitTicks(20);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("look-paint-noon");
			server.runCommand(String.format("tp @a %d.5 %d %d.5 %d %d", x0 + 2, g + 4, z0 - 1, 200, -5));
			context.waitTicks(15);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("look-condo-noon");
			// palms close up
			for (int t : new int[] {6000, 11500}) {
				server.runCommand("time set " + t);
				server.runCommand(String.format("tp @a %d.5 %d %d.5 %d %d", x0 + 20, g + 6, z0 + 30, 180, -12));
				context.waitTicks(20);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("look-palms" + (t == 6000 ? "-noon" : "-golden"));
			}
			server.runCommand(String.format("tp @a %d.5 %d %d.5 %d %d", x0 + 16, g + 2, z0 + 11, 200, -55));
			context.waitTicks(15);
			sp.getConnection().waitForChunksRender();
			context.takeScreenshot("look-palms-under");
			for (int t : new int[] {6000, 18000}) {
				server.runCommand("time set " + t);
				server.runCommand(String.format("tp @a %d.5 %d %d.5 %d %d", x0 - 23, g + 3, z0 + 23, 180, 14));
				context.waitTicks(20);
				sp.getConnection().waitForChunksRender();
				context.takeScreenshot("look-keys2" + (t == 6000 ? "-noon" : "-night"));
			}
			// batch 2 material gallery on the beach, two halves, noon and night
			for (int t : new int[] {6000, 18000}) {
				server.runCommand("time set " + t);
				for (int half = 0; half < 3; half++) {
					server.runCommand(String.format("tp @a %d.5 %d %d.5 %d %d", x0 - 29 + half * 16, g + 3, z0 + 6, 0, 18));
					context.waitTicks(20);
					sp.getConnection().waitForChunksRender();
					context.takeScreenshot("look-gallery" + half + (t == 6000 ? "-noon" : "-night"));
				}
			}
		}
	}

	/** Every state of every look block must have a baked model (not the missing model). */
	private static void checkModels(ClientGameTestContext context) {
		String bad = context.computeOnClient(mc -> {
			var set = mc.getModelManager().getBlockStateModelSet();
			StringBuilder sb = new StringBuilder();
			int states = 0;
			for (Block b : LookBlocks.all()) {
				for (BlockState s : b.getStateDefinition().getPossibleStates()) {
					states++;
					if (set.get(s) == set.missingModel()) sb.append(' ').append(s);
				}
			}
			System.out.println("[leonida-test] look blocks " + LookBlocks.all().size() + ", states checked " + states);
			return sb.toString();
		});
		if (!bad.isEmpty()) throw new AssertionError("look states with missing model:" + bad);
	}

	// ------------------------------------------------------------------ scene

	private static void set(ServerLevel l, int x, int y, int z, BlockState s) {
		l.setBlock(new BlockPos(x, y, z), s, FLAGS);
	}

	private static void set(ServerLevel l, int x, int y, int z, Block b) {
		set(l, x, y, z, b.defaultBlockState());
	}

	private static void buildStreet(ServerLevel l, int x0, int g, int z0) {
		int xa = x0 - 40, xb = x0 + 40;
		BlockState air = Blocks.AIR.defaultBlockState();
		// clear the area above ground
		for (int x = xa; x <= xb; x++) {
			for (int z = z0 - 20; z <= z0 + 45; z++) {
				for (int y = g + 1; y <= g + 4; y++) set(l, x, y, z, air);
			}
		}
		Block asphalt = LookBlocks.asphalt(Asphalt.FRESH);
		Block worn = LookBlocks.asphalt(Asphalt.WHEELPATH);
		for (int x = xa; x <= xb; x++) {
			int rx = x - x0;
			// north sidewalk z -6..-4 (raised half a block), kerb at -3
			for (int z = -6; z <= -4; z++) {
				set(l, x, g, z0 + z, LookBlocks.CONCRETE);
				set(l, x, g + 1, z0 + z, LookBlocks.sidewalk(x, z0 + z, SlabType.BOTTOM));
			}
			set(l, x, g, z0 - 3, LookBlocks.CONCRETE);
			set(l, x, g + 1, z0 - 3, LookBlocks.kerb(Direction.SOUTH, SlabType.BOTTOM));
			// road z -2..6: westbound lanes north of the centre line, eastbound south
			for (int z = -2; z <= 6; z++) set(l, x, g, z0 + z, (z == -1 || z == 5) && Math.floorMod(rx, 7) < 5 ? worn : asphalt);
			set(l, x, g, z0 - 2, LookBlocks.marking(Marking.LINE_WHITE_EDGE, Direction.NORTH));
			set(l, x, g, z0 + 6, LookBlocks.marking(Marking.LINE_WHITE_EDGE, Direction.SOUTH));
			boolean crossing = rx >= 10 && rx <= 13;
			if (!crossing) {
				set(l, x, g, z0 + 2, LookBlocks.marking(Marking.LINE_YELLOW_DOUBLE, Direction.EAST));
				if (Math.floorMod(rx, 12) < 3) {
					set(l, x, g, z0, LookBlocks.marking(Marking.LINE_WHITE, Direction.EAST));
					set(l, x, g, z0 + 4, LookBlocks.marking(Marking.LINE_WHITE, Direction.EAST));
				}
			} else {
				for (int z = -2; z <= 6; z++) set(l, x, g, z0 + z, LookBlocks.marking(Marking.ZEBRA, Direction.EAST));
			}
			// south kerb, sidewalk, beach, wet sand, sea
			set(l, x, g, z0 + 7, LookBlocks.CONCRETE);
			set(l, x, g + 1, z0 + 7, LookBlocks.kerb(Direction.NORTH, SlabType.BOTTOM));
			set(l, x, g, z0 + 8, LookBlocks.CONCRETE);
			set(l, x, g + 1, z0 + 8, LookBlocks.sidewalk(x, z0 + 8, SlabType.BOTTOM));
			for (int z = 9; z <= 13; z++) set(l, x, g, z0 + z, LookBlocks.BEACH_SAND);
			for (int z = 14; z <= 16; z++) set(l, x, g, z0 + z, LookBlocks.WET_SAND);
			for (int z = 17; z <= 45; z++) {
				int depth = Math.min(3, 1 + (z - 17) / 4);
				for (int d = 0; d < depth; d++) set(l, x, g - d, z0 + z, Blocks.WATER);
				set(l, x, g - depth, z0 + z, LookBlocks.SEABED_SAND);
			}
		}
		// stop bars and arrows (eastbound stops at x 9, westbound at x 14)
		for (int z = 3; z <= 5; z += 2) set(l, x0 + 9, g, z0 + z, LookBlocks.marking(Marking.STOP_BAR, Direction.EAST));
		for (int z = -1; z <= 1; z += 2) set(l, x0 + 14, g, z0 + z, LookBlocks.marking(Marking.STOP_BAR, Direction.EAST));
		set(l, x0 + 5, g, z0 + 3, LookBlocks.marking(Marking.ARROW_LEFT, Direction.EAST));
		set(l, x0 + 5, g, z0 + 5, LookBlocks.marking(Marking.ARROW_STRAIGHT, Direction.EAST));
		set(l, x0 + 18, g, z0 - 1, LookBlocks.marking(Marking.ARROW_RIGHT, Direction.WEST));
		set(l, x0 + 18, g, z0 + 1, LookBlocks.marking(Marking.ARROW_LEFT, Direction.WEST));

		gallery(l, x0 - 36, g + 1, z0 + 11);

		keysPass2(l, x0, g + 1, z0 + 14);

		// palms: royal row behind the sidewalk, coconuts leaning to the sea, a sabal
		for (int i = 0; i < 4; i++) LookBlocks.palm(l, new BlockPos(x0 + 6 + 8 * i, g + 1, z0 + 10), LookBlocks.Palm.ROYAL, 13 + (i * 3) % 5, Direction.SOUTH, true, i);
		for (int i = 0; i < 3; i++) LookBlocks.palm(l, new BlockPos(x0 + 9 + 10 * i, g + 1, z0 + 14), LookBlocks.Palm.COCONUT, 8 + i, i == 1 ? Direction.EAST : Direction.SOUTH, false, 7 + i);
		LookBlocks.palm(l, new BlockPos(x0 + 12, g + 1, z0 + 12), LookBlocks.Palm.SABAL, 7, Direction.NORTH, true, 3);

		tower(l, x0 - 26, x0 - 14, g + 1, z0 - 19, z0 - 7, 44);
		condo(l, x0 - 10, x0 + 4, g + 1, z0 - 17, z0 - 8, 8);
		hotel(l, x0 + 8, x0 + 22, g + 1, z0 - 16, z0 - 7, 4);
	}

	/**
	 * Row of the batch 2 materials on the beach (x from x0 east), back wall at z + 1, wall mounted pieces facing north at
	 * eye height, free standing pieces on the sand.
	 */
	private static void gallery(ServerLevel l, int x0, int y, int z) {
		java.util.List<BlockState> ground = new java.util.ArrayList<>();
		for (LookBlocks.SeatColor c : LookBlocks.SeatColor.values()) {
			ground.add(LookBlocks.stadiumSeat(c).defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.SOUTH));
		}
		for (Block b : new Block[] {LookBlocks.ROOF_MEMBRANE_WHITE, LookBlocks.ARENA_PANEL_SILVER, LookBlocks.ARENA_PANEL_WHITE,
				LookBlocks.truss(LookBlocks.Truss.WHITE), LookBlocks.truss(LookBlocks.Truss.GREY), LookBlocks.COURT_WOOD, LookBlocks.TURF_GRASS,
				LookBlocks.TURF_GRASS_DARK, LookBlocks.SIDING_TEAL_WEATHERED, LookBlocks.SIDING_TEAL_WEATHERED, LookBlocks.SIDING_TEAL_WEATHERED_DARK,
				LookBlocks.WEATHERED_WOOD, LookBlocks.WOOD_BROWN, LookBlocks.TROPICAL_FABRIC, LookBlocks.OYSTER_SHELL_GRAVEL}) {
			ground.add(b.defaultBlockState());
		}
		for (LookBlocks.Led c : LookBlocks.Led.values()) ground.add(LookBlocks.ledPanel(c).defaultBlockState());
		ground.add(LookBlocks.PANEL_CREAM.defaultBlockState());
		ground.add(LookBlocks.SIDING_TEAL_LINED.defaultBlockState().setValue(dev.leonida.look.block.OrientedBlock.FACING, Direction.NORTH));
		ground.add(LookBlocks.SIDING_TEAL_LINED.defaultBlockState().setValue(dev.leonida.look.block.OrientedBlock.FACING, Direction.SOUTH));
		ground.add(LookBlocks.window(LookBlocks.WINDOW_COTTAGE, dev.leonida.look.block.Glow.ON));
		ground.add(LookBlocks.window(LookBlocks.WINDOW_PORTHOLE, dev.leonida.look.block.Glow.ON));
		ground.add(LookBlocks.window(LookBlocks.WINDOW_COTTAGE, dev.leonida.look.block.Glow.OFF));
		int x = x0;
		for (BlockState s : ground) {
			set(l, x, y, z, s);
			x++;
		}
		// cottage door, then railing run
		set(l, x, y, z, LookBlocks.doorCottage(false));
		set(l, x, y + 1, z, LookBlocks.doorCottage(true));
		x += 2;
		for (int i = 0; i < 4; i++) set(l, x + i, y, z, LookBlocks.WOOD_RAILING.defaultBlockState()
				.setValue(net.minecraft.world.level.block.FenceBlock.EAST, i < 3).setValue(net.minecraft.world.level.block.FenceBlock.WEST, i > 0));
		set(l, x + 5, y, z, LookBlocks.TARP_BLUE);
		// openable cottage doors (closed, open), fabric slab/stairs, weathered wood stairs
		for (int k = 0; k < 2; k++) {
			BlockState d = LookBlocks.DOOR_COTTAGE_OPENABLE.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.SOUTH)
					.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, k == 1);
			set(l, x + 7 + k * 2, y, z, d);
			set(l, x + 7 + k * 2, y + 1, z, d.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		}
		set(l, x + 11, y, z, LookBlocks.TROPICAL_FABRIC_SLAB);
		set(l, x + 12, y, z, LookBlocks.TROPICAL_FABRIC_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH));
		set(l, x + 13, y, z, LookBlocks.WEATHERED_WOOD_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.NORTH));
		// back wall with wall-mounted pieces facing north
		int xb = x0;
		for (int i = 0; i < 40; i++) for (int dy = 0; dy < 3; dy++) set(l, xb + i, y + dy, z + 1, LookBlocks.SIDING_TEAL_WEATHERED);
		BlockState[] wall = {LookBlocks.onWall(LookBlocks.SHUTTER_GREY, Direction.NORTH), LookBlocks.onWall(LookBlocks.LATTICE_PANEL, Direction.NORTH),
				LookBlocks.onWall(LookBlocks.BAMBOO_BLIND, Direction.NORTH), LookBlocks.neon(Neon.GOLD, Direction.NORTH, false),
				LookBlocks.neon(Neon.WHITE, Direction.NORTH, true), LookBlocks.ledRibbon(LookBlocks.Led.CYAN, Direction.NORTH, false),
				LookBlocks.ledRibbon(LookBlocks.Led.GOLD, Direction.NORTH, true), LookBlocks.ledRibbon(LookBlocks.Led.WARM_WHITE, Direction.NORTH, false)};
		for (int i = 0; i < wall.length; i++) set(l, xb + 30 + i, y + 2, z, wall[i]);
	}

	/** Keys house pass 2 pieces on the wet sand in front of the gallery wall's south face. */
	private static void keysPass2(ServerLevel l, int x0, int y, int z) {
		for (int x = x0 - 34; x <= x0 - 32; x++) for (int dy = 0; dy < 2; dy++) set(l, x, y + dy, z, LookBlocks.WEATHERED_WOOD_FENCE.defaultBlockState()
				.setValue(net.minecraft.world.level.block.FenceBlock.EAST, x < x0 - 32).setValue(net.minecraft.world.level.block.FenceBlock.WEST, x > x0 - 34));
		for (int x = x0 - 30; x <= x0 - 28; x++) set(l, x, y, z, LookBlocks.WOOD_RAILING_WEATHERED.defaultBlockState()
				.setValue(net.minecraft.world.level.block.FenceBlock.EAST, x < x0 - 28).setValue(net.minecraft.world.level.block.FenceBlock.WEST, x > x0 - 30));
		var F = dev.leonida.look.block.OrientedBlock.FACING;
		for (int dy = 0; dy < 2; dy++) {
			set(l, x0 - 26, y + dy, z, LookBlocks.SIDING_TEAL_LINED_FADED.defaultBlockState().setValue(F, Direction.SOUTH));
			set(l, x0 - 25, y + dy, z, LookBlocks.SIDING_TEAL_LINED_FADED.defaultBlockState().setValue(F, Direction.NORTH));
		}
		set(l, x0 - 24, y, z, LookBlocks.TRIM_WHITE_PEELING);
		set(l, x0 - 23, y, z, LookBlocks.TRIM_WHITE_PEELING_SLAB);
		set(l, x0 - 21, y, z, LookBlocks.GLASS_COFFEE_TABLE);
		BlockState fr = LookBlocks.FRIDGE_WHITE.defaultBlockState().setValue(dev.leonida.look.block.TallApplianceBlock.FACING, Direction.SOUTH);
		set(l, x0 - 19, y, z, fr);
		set(l, x0 - 19, y + 1, z, fr.setValue(dev.leonida.look.block.TallApplianceBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
		set(l, x0 - 17, y, z, LookBlocks.COOLER_WHITE.defaultBlockState().setValue(F, Direction.EAST));
		set(l, x0 - 16, y, z, LookBlocks.BUCKET_WHITE);
		set(l, x0 - 14, y + 1, z - 1, LookBlocks.HOSE_REEL.defaultBlockState().setValue(F, Direction.SOUTH));
	}

	/** Shell of a box: calls the painter for each outer wall block with (x, y, z, face normal). */
	private interface Painter {
		BlockState at(int x, int y, int z, Direction out, int along);
	}

	private static void shell(ServerLevel l, int xa, int xb, int y0, int y1, int za, int zb, Painter p) {
		for (int y = y0; y <= y1; y++) {
			for (int x = xa; x <= xb; x++) {
				set(l, x, y, zb, p.at(x, y, zb, Direction.SOUTH, x - xa));
				set(l, x, y, za, p.at(x, y, za, Direction.NORTH, xb - x));
			}
			for (int z = za + 1; z < zb; z++) {
				set(l, xa, y, z, p.at(xa, y, z, Direction.WEST, z - za));
				set(l, xb, y, z, p.at(xb, y, z, Direction.EAST, zb - z));
			}
		}
		// roof
		for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) set(l, x, y1 + 1, z, LookBlocks.MEMBRANE_ROOF);
	}

	/** Style 0: teal curtain wall, spandrel every 4th row, silver mullions every 3 blocks and at the corners. */
	private static void tower(ServerLevel l, int xa, int xb, int y0, int za, int zb, int h) {
		Block glass = LookBlocks.glass(GlassTint.TEAL), spandrel = LookBlocks.spandrel(GlassTint.TEAL);
		Block silver = LookBlocks.frame(Frame.SILVER);
		int w = xb - xa;
		shell(l, xa, xb, y0, y0 + h - 1, za, zb, (x, y, z, out, along) -> {
			int ry = y - y0;
			if (along == 0 || along == w) return silver.defaultBlockState();
			if (ry < 4) return along % 3 == 0 ? silver.defaultBlockState() : LookBlocks.CLEAR_GLASS.defaultBlockState();
			if (ry % 4 == 3) return spandrel.defaultBlockState();
			return along % 3 == 0 ? silver.defaultBlockState() : glass.defaultBlockState();
		});
		// lobby canopy
		for (int x = xa + 3; x <= xb - 3; x++) set(l, x, y0 + 4, zb + 1, LookBlocks.frame(Frame.DARK));
		for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) set(l, x, y0 + h, z, LookBlocks.GRAVEL_ROOF);
	}

	/** Style 1: white condo, slab band per floor, window bays, balconies with glass railings on alternate bays. */
	private static void condo(ServerLevel l, int xa, int xb, int y0, int za, int zb, int floors) {
		Block wall = LookBlocks.condo(CondoWhite.values()[0]);
		Block grimy = LookBlocks.condoGrimy(CondoWhite.values()[0]);
		Block win = LookBlocks.window(Window.CONDO);
		int w = xb - xa;
		int h = floors * 3 + 1;
		shell(l, xa, xb, y0, y0 + h - 1, za, zb, (x, y, z, out, along) -> {
			int ry = y - y0;
			if (along == 0 || along == w) return (ry < 2 ? grimy : wall).defaultBlockState();
			if (ry < 3) return along % 4 == 0 ? grimy.defaultBlockState() : LookBlocks.CLEAR_GLASS.defaultBlockState();
			int fy = (ry - 1) % 3;
			if (fy == 2) return wall.defaultBlockState();
			return along % 4 == 0 ? wall.defaultBlockState() : win.defaultBlockState();
		});
		// balconies on the street side: slab floor (top half) + glass railing, alternate bays and floors offset
		Block slab = LookBlocks.condoSlab(CondoWhite.values()[0]);
		for (int f = 1; f < floors; f++) {
			int yb = y0 + f * 3; // slab band row
			for (int x = xa + 1; x < xb; x++) {
				int bay = (x - xa - 1) / 4;
				if ((bay + f) % 2 == 1) continue;
				for (int d = 1; d <= 2; d++) {
					set(l, x, yb, zb + d, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
				}
				set(l, x, yb + 1, zb + 2, LookBlocks.onWall(LookBlocks.GLASS_RAILING, Direction.SOUTH));
			}
		}
	}

	/** Style 2: pink Deco hotel, shopfront ground floor with awning, eyebrows, central fin with neon, speed lines. */
	private static void hotel(ServerLevel l, int xa, int xb, int y0, int za, int zb, int floors) {
		Block wall = LookBlocks.stucco(Pastel.PINK), grimy = LookBlocks.stuccoGrimy(Pastel.PINK);
		Block win = LookBlocks.window(Window.DECO), shop = LookBlocks.window(Window.SHOPFRONT);
		Block trim = LookBlocks.TRIM_WHITE;
		int w = xb - xa, mid = (xa + xb) / 2;
		int ground = 4, h = ground + floors * 3 + 1;
		shell(l, xa, xb, y0, y0 + h - 1, za, zb, (x, y, z, out, along) -> {
			int ry = y - y0;
			if (ry == h - 1) return trim.defaultBlockState();
			if (along == 0 || along == w) return (ry < 2 ? grimy : wall).defaultBlockState();
			if (ry < ground) {
				if (ry == ground - 1) return trim.defaultBlockState();
				if (out == Direction.SOUTH && x == mid) return ry < 2 ? LookBlocks.CLEAR_GLASS.defaultBlockState() : wall.defaultBlockState();
				return along % 5 == 0 ? grimy.defaultBlockState() : ry == 0 ? grimy.defaultBlockState() : shop.defaultBlockState();
			}
			int fy = (ry - ground) % 3;
			// windows in pairs of 2, wall between
			int col = along % 4;
			if (fy == 2 || col == 0 || col == 3) return wall.defaultBlockState();
			return win.defaultBlockState();
		});
		// awning over the shopfront, eyebrows over every window pair on the street side
		for (int x = xa + 1; x < xb; x++) {
			if (x != mid && (x - xa) % 5 != 0) set(l, x, y0 + 2, zb + 1, LookBlocks.onWall(LookBlocks.awning(Awning.TEAL), Direction.SOUTH));
			int col = (x - xa) % 4;
			for (int f = 0; f < floors; f++) {
				int yWinTop = y0 + ground + f * 3 + 1;
				if (col == 1 || col == 2) set(l, x, yWinTop, zb + 1, LookBlocks.onWall(LookBlocks.eyebrow(Accent.WHITE), Direction.SOUTH));
			}
		}
		// speed lines: three horizontal teal bands round the left corner
		for (int i = 0; i < 3; i++) {
			int y = y0 + ground + floors * 3 - 1 - i;
			for (int x = xa; x <= xa + 2; x++) set(l, x, y, zb + 1, LookBlocks.onWall(LookBlocks.speedLine(Accent.TEAL), Direction.SOUTH));
		}
		// central fin from the shopfront to above the roof, flanked by vertical pink neon, cyan neon along the cornice
		for (int y = y0 + ground; y <= y0 + h + 2; y++) {
			set(l, mid, y, zb + 1, LookBlocks.onWall(LookBlocks.fin(Accent.WHITE), Direction.SOUTH));
			if (y < y0 + h) {
				set(l, mid - 1, y, zb + 1, LookBlocks.neon(Neon.PINK, Direction.SOUTH, true));
				set(l, mid + 1, y, zb + 1, LookBlocks.neon(Neon.PINK, Direction.SOUTH, true));
			}
		}
		for (int x = xa + 3; x <= xb - 3; x++) {
			if (Math.abs(x - mid) > 1) set(l, x, y0 + h - 2, zb + 1, LookBlocks.neon(Neon.CYAN, Direction.SOUTH, false));
		}
		// entrance door
		set(l, mid, y0, zb, LookBlocks.door(false));
		set(l, mid, y0 + 1, zb, LookBlocks.door(true));
	}
}
