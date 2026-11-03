package dev.leonida.build;

import dev.leonida.LeonidaMod;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Vanilla block palette by registry name, plus small state helpers. Unknown names resolve to air and are logged once. */
final class B {
	private B() {}

	private static final Map<String, BlockState> CACHE = new ConcurrentHashMap<>();
	static final Set<String> MISSING = ConcurrentHashMap.newKeySet();
	static final BlockState AIR = Blocks.AIR.defaultBlockState();

	static BlockState of(String name) {
		BlockState s = CACHE.get(name);
		if (s != null) return s;
		Identifier id = name.indexOf(':') >= 0 ? Identifier.parse(name) : Identifier.withDefaultNamespace(name);
		if (BuiltInRegistries.BLOCK.containsKey(id)) {
			s = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
		} else {
			s = AIR;
			if (MISSING.add(name)) LeonidaMod.LOG.warn("[leonida-build] unknown block {}", name);
		}
		CACHE.put(name, s);
		return s;
	}

	/** Colour + suffix, e.g. c("pink", "concrete"). */
	static BlockState c(String color, String suffix) {
		return of(color + "_" + suffix);
	}

	static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, T v) {
		return s.hasProperty(p) ? s.setValue(p, v) : s;
	}

	/** Another module's block if it is registered, else a vanilla stand-in (no warning). */
	static BlockState optional(String id, BlockState fallback) {
		Identifier i = Identifier.parse(id);
		return BuiltInRegistries.BLOCK.containsKey(i) ? BuiltInRegistries.BLOCK.getValue(i).defaultBlockState() : fallback;
	}

	/** Gameplay cash register; FACING = customer side. Clerk stands one block behind, one below. */
	static BlockState register(Direction customer) {
		return face(optional("leonida:gameplay/register", face(of("dark_oak_trapdoor"), customer)), customer);
	}

	/** Weapon gun counter (glass case, opens the shop); FACING = customer side. */
	static BlockState gunCounter(Direction customer) {
		return face(optional("leonida:weapon/gun_counter", of("glass")), customer);
	}

	/** Weapon gun rack wall panel (opens the shop); FACING = into the room. */
	static BlockState gunRack(Direction into) {
		return face(optional("leonida:weapon/gun_rack", trap("spruce_trapdoor", into, true, true)), into);
	}

	/** Weapon ammo crate (half height, refills). */
	static BlockState ammoBox(Direction d) {
		return face(optional("leonida:weapon/ammo_box", of("barrel")), d);
	}

	/** Gameplay ATM; FACING = screen side. */
	static BlockState atm(Direction screen) {
		return face(optional("leonida:gameplay/atm", of("light_blue_concrete")), screen);
	}

	static BlockState face(BlockState s, Direction d) {
		if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && d.getAxis().isHorizontal()) return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
		if (s.hasProperty(BlockStateProperties.FACING)) return s.setValue(BlockStateProperties.FACING, d);
		return s;
	}

	static BlockState face(String name, Direction d) {
		return face(of(name), d);
	}

	static BlockState stair(String name, Direction facing, boolean top) {
		return with(face(of(name), facing), BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slab(String name, boolean top) {
		return with(of(name), BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState trap(String name, Direction facing, boolean open, boolean top) {
		BlockState s = face(of(name), facing);
		s = with(s, BlockStateProperties.OPEN, open);
		return with(s, BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState axis(String name, Direction.Axis a) {
		return with(of(name), BlockStateProperties.AXIS, a);
	}

	static BlockState leaves(String name) {
		return with(of(name), BlockStateProperties.PERSISTENT, true);
	}

	static BlockState door(String name, Direction facing, boolean upper, boolean rightHinge) {
		BlockState s = face(of(name), facing);
		s = with(s, BlockStateProperties.DOUBLE_BLOCK_HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
		return with(s, BlockStateProperties.DOOR_HINGE, rightHinge ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
	}

	static BlockState bed(String color, Direction facing, boolean head) {
		return with(face(c(color, "bed"), facing), BlockStateProperties.BED_PART, head ? BedPart.HEAD : BedPart.FOOT);
	}

	static BlockState hanging(String name) {
		return with(of(name), BlockStateProperties.HANGING, true);
	}

	static BlockState button(String name, Direction facing) {
		return with(face(of(name), facing), BlockStateProperties.ATTACH_FACE, AttachFace.WALL);
	}

	static BlockState lit(String name) {
		return with(of(name), BlockStateProperties.LIT, true);
	}

	static BlockState cauldron(int level) {
		return with(of("water_cauldron"), BlockStateProperties.LEVEL_CAULDRON, level);
	}

	static BlockState light(int level) {
		return with(of("light"), BlockStateProperties.LEVEL, level);
	}

	static final String[] PASTELS = {"pink", "light_blue", "white", "yellow", "lime", "cyan", "magenta", "orange"};
	static final String[] NEONS = {"pearlescent_froglight", "sea_lantern", "verdant_froglight", "sea_lantern"};
}
