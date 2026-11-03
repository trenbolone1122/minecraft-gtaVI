package dev.leonida.vehicle;

import dev.leonida.LeonidaMod;
import dev.leonida.vehicle.VehicleSpec.Kind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;

/** All vehicle models and their entity types (one entity type per model: leonida:vehicle_<id>). */
public final class VehicleTypes {
	/** True for the airboats (air propeller, shallow draft). */
	public static boolean isAirboat(VehicleSpec s) { return s.id().startsWith("airboat"); }

	private VehicleTypes() {}

	public static final Map<String, VehicleSpec> SPECS = new LinkedHashMap<>();
	public static final Map<String, EntityType<VehicleEntity>> TYPES = new LinkedHashMap<>();
	public static final Map<EntityType<?>, VehicleSpec> BY_TYPE = new LinkedHashMap<>();

	/** Entity type tag listing the police vehicles. */
	public static final TagKey<EntityType<?>> POLICE_TAG = TagKey.create(Registries.ENTITY_TYPE, LeonidaMod.id("vehicle/police"));

	// Paint palette (RGB)
	public static final int RED = 0xB3202A, TEAL = 0x1E8C93, BLACK = 0x17181C, ORANGE = 0xE0662A, YELLOW = 0xE8C033, WHITE = 0xEDEDED,
			PINK = 0xE0407F, LIME = 0x9BD234, BLUE = 0x2456C9, SILVER = 0xB8BCC2, BEIGE = 0xCDBB95, NAVY = 0x1F2D4D,
			MAROON = 0x5E1A24, GREEN = 0x3E6B4A, GRAY = 0x5A5E66, SAND = 0xC9B48A, SKY = 0x6FA8D6, RUST = 0x8E4B2A, GANADO_TURQUOISE = 0x6A9EA4,
			CREAM = 0xE9E2C8, TAXI = 0xF2C230, PURPLE = 0x6B3FA0, MINT = 0x7FD6B8, CANDY_GREEN = 0x2F8A3A, CORAL = 0xF06A55,
			GUNMETAL = 0x3A3F47, HOT_RED = 0xC8161E, CYAN = 0x2CC6D6, MAGENTA = 0xC2308E, ACID_LIME = 0x8CE01E, GOLD = 0xC9A14A,
			BABY_BLUE = 0x9CC6E8, OLIVE = 0x56603A, ARMY = 0x4A5236, G6_GREEN = 0x2E7A48, VCPD_WHITE = 0xF2F2F0, SHERIFF_GREEN = 0x23472E,
			VCPD_BLUE = 0x1D3F8F, RIOT_BLACK = 0x1E2126, BOAT_WHITE = 0xF1EFE8;

	/** Police paints are liveries: VCPD (white and blue) or Leonard County Sheriff (green and white). */
	public static final int[] POLICE_LIVERIES = {VCPD_WHITE, SHERIFF_GREEN};

	public static boolean isSheriffLivery(int color) { return (color & 0xFFFFFF) == SHERIFF_GREEN; }

	/** Secondary colour shown on accent parts: livery band for police, a darker shade for civilian two-tones. */
	public static int accentFor(VehicleSpec spec, int color) {
		if (spec.police()) return isSheriffLivery(color) ? VCPD_WHITE : VCPD_BLUE;
		return switch (color) {
			case WHITE, CREAM, BOAT_WHITE -> 0x2A2C30;
			default -> darken(color, 0.55f);
		};
	}

	private static int darken(int rgb, float f) {
		int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
		return (r << 16) | (g << 8) | b;
	}

	private static List<Vec3> seats(double... xyz) {
		Vec3[] out = new Vec3[xyz.length / 3];
		for (int i = 0; i < out.length; i++) out[i] = new Vec3(xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2]);
		return List.of(out);
	}

	// A seated player's head top is about 1.4 blocks above the seat attachment, so the seat height follows the roof
	// (vehicle height) to keep heads under it.
	private static double seatY(float height) { return Math.max(-0.22, (height * 16 - 22.5) / 16.0); }

	private static List<Vec3> two(float w, float h, double z) {
		double x = w * 0.21, y = seatY(h);
		return seats(-x, y, z, x, y, z);
	}

	private static List<Vec3> four(float w, float h, double zf, double zr) {
		double x = w * 0.21, y = seatY(h);
		return seats(-x, y, zf, x, y, zf, -x, y, zr, x, y, zr);
	}

	/** Handling template: max speed, accel, brake, reverse (m/s, m/s^2), steer (deg), grip, mass (kg), health, step. */
	private record H(float max, float accel, float brake, float rev, float steer, float grip, float mass, float health, float step) {}

	private static final H MUSCLE_H = new H(54, 10, 14, 9, 34, 11, 1700, 110, 1.0f);
	private static final H SPORT_H = new H(60, 11.5f, 17, 9, 32, 14, 1450, 95, 1.0f);
	private static final H SUPER_H = new H(68, 13, 19, 9, 30, 15.5f, 1500, 95, 1.0f);
	private static final H TUNER_H = new H(58, 11, 16, 9, 34, 13.5f, 1400, 95, 1.0f);
	private static final H CABRIO_H = new H(50, 8.5f, 14, 8, 36, 12, 1250, 85, 1.0f);
	private static final H SEDAN_H = new H(46, 8, 13, 8, 36, 10.5f, 1700, 110, 1.0f);
	private static final H LOWRIDER_H = new H(40, 6.5f, 11, 7, 34, 9, 1900, 110, 1.0f);
	private static final H SUV_H = new H(44, 7.5f, 12, 8, 34, 9.5f, 2400, 140, 1.0f);
	private static final H PICKUP_H = new H(46, 8, 12, 8, 34, 9.5f, 2500, 140, 1.0f);
	private static final H VAN_H = new H(38, 6, 11, 7, 34, 8.5f, 2700, 150, 1.0f);
	private static final H ARMORED_H = new H(32, 4.5f, 10, 6, 32, 8, 7000, 400, 1.0f);

	private static VehicleSpec car(String id, String name, H h, float w, float l, float ht, float wb, List<Vec3> seats, int[] paints,
			boolean police, String sound) {
		return add(new VehicleSpec(id, name, Kind.CAR, w, l, ht, wb, h.max, h.accel, h.brake, h.rev, h.steer, h.grip, h.mass, h.health,
				h.step, seats, paints, police, sound));
	}

	private static int[] p(int... c) { return c; }

	// ---------------------------------------------------------------- muscle and lowrider
	public static final VehicleSpec DOMINATOR = car("dominator", "Vapid Dominator", MUSCLE_H, 2.0f, 4.6f, 1.36f, 2.7f,
			two(2.0f, 1.36f, -0.25), p(HOT_RED, BLACK, WHITE, ORANGE, NAVY, CYAN, GUNMETAL), false, "engine_muscle");
	public static final VehicleSpec GAUNTLET_HELLFIRE = car("gauntlet_hellfire", "Bravado Gauntlet Hellfire", MUSCLE_H, 2.05f, 4.9f, 1.4f,
			2.9f, two(2.05f, 1.4f, -0.3), p(RED, BLACK, PURPLE, ACID_LIME, ORANGE, SILVER, CORAL), false, "engine_muscle");
	public static final VehicleSpec TULIP = car("tulip", "Declasse Tulip", MUSCLE_H, 2.0f, 4.9f, 1.36f, 2.85f, two(2.0f, 1.36f, -0.35),
			p(CREAM, MAROON, BABY_BLUE, BLACK, OLIVE, GOLD, RUST), false, "engine_muscle");
	public static final VehicleSpec BUFFALO = car("buffalo", "Bravado Buffalo STX", MUSCLE_H, 2.0f, 5.0f, 1.45f, 2.95f,
			four(2.0f, 1.45f, 0.1, -0.9), p(BLACK, WHITE, GUNMETAL, HOT_RED, NAVY, SILVER), false, "engine_muscle");
	public static final VehicleSpec VOODOO = car("voodoo", "Spiderweb Lowrider", LOWRIDER_H, 2.05f, 5.3f, 1.36f, 3.0f,
			four(2.05f, 1.36f, -0.1, -1.1), p(CANDY_GREEN, MAGENTA, GOLD, BABY_BLUE, MAROON), false, "engine_muscle");

	/** Jason's car: a 70s coupe utility (El Camino style) in faded turquoise. */
	public static final VehicleSpec GANADO = car("ganado", "Vapid Ganado", MUSCLE_H, 2.0f, 5.4f, 1.38f, 2.95f, two(2.0f, 1.38f, -0.2),
			p(GANADO_TURQUOISE, CREAM, MAROON, OLIVE, BABY_BLUE, RUST), false, "engine_muscle");

	// ---------------------------------------------------------------- sports and super
	public static final VehicleSpec COQUETTE_D10 = car("coquette_d10", "Invetero Coquette D10", SPORT_H, 2.05f, 4.6f, 1.22f, 2.7f,
			two(2.05f, 1.22f, 0.15), p(HOT_RED, YELLOW, WHITE, BLACK, ORANGE, SKY, SILVER), false, "engine_muscle");
	public static final VehicleSpec CHEETAH = car("cheetah", "Grotti Cheetah", SPORT_H, 2.05f, 4.5f, 1.16f, 2.55f,
			two(2.05f, 1.16f, 0.1), p(WHITE, HOT_RED, BLACK, YELLOW, CYAN), false, "engine_car");
	public static final VehicleSpec TEMPESTA = car("tempesta", "Pegassi Tempesta", SUPER_H, 2.05f, 4.5f, 1.17f, 2.6f,
			two(2.05f, 1.17f, 0.15), p(ACID_LIME, ORANGE, WHITE, BLUE, BLACK, YELLOW, MAGENTA), false, "engine_car");
	public static final VehicleSpec INFERNUS_CLASSIC = car("infernus_classic", "Pegassi Infernus Classic", SUPER_H, 2.05f, 4.5f, 1.12f,
			2.65f, two(2.05f, 1.12f, 0.15), p(YELLOW, HOT_RED, WHITE, PURPLE, BLACK), false, "engine_car");
	public static final VehicleSpec ADDER = car("adder", "Lime Hypercar", SUPER_H, 2.05f, 4.5f, 1.2f, 2.7f, two(2.05f, 1.2f, 0.05),
			p(ACID_LIME, BLACK, NAVY, SILVER, HOT_RED), false, "engine_car");
	public static final VehicleSpec SENTINEL_CABRIO = car("sentinel_classic_cabrio", "Ubermacht Sentinel Classic Cabrio", CABRIO_H, 1.85f,
			4.4f, 1.32f, 2.55f, four(1.85f, 1.32f, 0.0, -0.85), p(HOT_RED, WHITE, BLACK, BABY_BLUE, SILVER), false, "engine_car");
	public static final VehicleSpec ELEGY = car("elegy", "Annis Elegy", TUNER_H, 1.9f, 4.6f, 1.33f, 2.7f, four(1.9f, 1.33f, 0.0, -0.85),
			p(WHITE, NAVY, PURPLE, SILVER, BLACK, HOT_RED), false, "engine_car");

	// ---------------------------------------------------------------- sedans, trucks, SUVs, vans
	public static final VehicleSpec STANIER = car("stanier", "Vapid Stanier", SEDAN_H, 2.0f, 5.2f, 1.48f, 2.9f,
			four(2.0f, 1.48f, 0.05, -0.95), p(BEIGE, SILVER, NAVY, MAROON, WHITE, GREEN, BLACK), false, "engine_car");
	public static final VehicleSpec CARACARA = car("caracara", "Vapid Caracara 4x4", PICKUP_H, 2.25f, 5.6f, 1.95f, 3.5f,
			four(2.25f, 1.95f, 0.55, -0.45), p(SILVER, BLACK, BLUE, HOT_RED, WHITE, ORANGE), false, "engine_muscle");
	public static final VehicleSpec BISON = car("bison", "Bravado Bison", PICKUP_H, 2.15f, 5.6f, 1.9f, 3.5f,
			four(2.15f, 1.9f, 0.55, -0.45), p(WHITE, BLACK, RED, GUNMETAL, SAND, NAVY), false, "engine_muscle");
	public static final VehicleSpec GRANGER = car("granger", "Declasse Granger 3600LX", SUV_H, 2.1f, 5.5f, 1.9f, 3.3f,
			four(2.1f, 1.9f, 0.55, -0.45), p(BLACK, WHITE, SILVER, NAVY, MAROON), false, "engine_car");
	public static final VehicleSpec DUBSTA = car("dubsta", "Benefactor Dubsta", SUV_H, 2.0f, 4.7f, 1.95f, 2.85f,
			four(2.0f, 1.95f, 0.15, -0.8), p(BLACK, WHITE, OLIVE, SILVER, GUNMETAL), false, "engine_car");
	public static final VehicleSpec SPEEDO = car("speedo", "Vapid Speedo", VAN_H, 2.1f, 5.6f, 2.1f, 3.4f,
			seats(-0.45, 0.62, 1.4, 0.45, 0.62, 1.4, -0.45, 0.62, 0.2, 0.45, 0.62, 0.2), p(MAROON, WHITE, GRAY, BEIGE), false, "engine_car");
	public static final VehicleSpec STOCKADE = car("stockade", "Gruppe6 Stockade", ARMORED_H, 2.35f, 6.2f, 2.6f, 3.8f,
			seats(-0.5, 0.8, 1.75, 0.5, 0.8, 1.75, -0.5, 0.8, -0.6, 0.5, 0.8, -0.6), p(G6_GREEN), false, "engine_muscle");
	public static final VehicleSpec GRANGER_TAXI = car("granger_taxi", "Declasse Granger Taxi", SUV_H, 2.1f, 5.5f, 1.9f, 3.3f,
			four(2.1f, 1.9f, 0.55, -0.45), p(TAXI), false, "engine_car");

	// ---------------------------------------------------------------- police (each with VCPD and sheriff liveries)
	public static final VehicleSpec POLICE_CRUISER = car("police_cruiser", "Vapid Police Car", new H(55, 10.5f, 16, 9, 36, 12.5f, 1800, 160, 1.0f),
			2.0f, 5.2f, 1.48f, 2.9f, four(2.0f, 1.48f, 0.05, -0.95), POLICE_LIVERIES, true, "engine_muscle");
	public static final VehicleSpec POLICE_SUV = car("police_suv", "Vapid Police SUV", new H(50, 9.5f, 15, 9, 34, 11, 2400, 190, 1.0f),
			2.05f, 5.0f, 1.85f, 3.0f, four(2.05f, 1.85f, 0.25, -0.75), POLICE_LIVERIES, true, "engine_muscle");
	public static final VehicleSpec BUFFALO_POLICE = car("buffalo_police", "Bravado Buffalo Police", new H(58, 11.5f, 17, 9, 34, 13, 1900, 170, 1.0f),
			2.0f, 5.0f, 1.45f, 2.95f, four(2.0f, 1.45f, 0.1, -0.9), POLICE_LIVERIES, true, "engine_muscle");
	public static final VehicleSpec BISON_POLICE = car("bison_police", "Bravado Bison Police", new H(48, 8.5f, 14, 8, 34, 10, 2600, 190, 1.0f),
			2.15f, 5.6f, 1.9f, 3.5f, four(2.15f, 1.9f, 0.55, -0.45), POLICE_LIVERIES, true, "engine_muscle");
	public static final VehicleSpec POLICE_RIOT = car("police_riot", "Police Riot", new H(36, 5.5f, 11, 7, 32, 8.5f, 7500, 500, 1.0f),
			2.4f, 6.2f, 2.7f, 3.8f, seats(-0.5, 0.85, 1.75, 0.5, 0.85, 1.75, -0.5, 0.85, -0.6, 0.5, 0.85, -0.6), POLICE_LIVERIES, true,
			"engine_muscle");

	// ---------------------------------------------------------------- bikes
	public static final VehicleSpec SANCHEZ = add(new VehicleSpec("sanchez", "Maibatsu Sanchez", Kind.BIKE, 0.9f, 2.15f, 1.2f, 1.45f,
			42, 12, 14, 5, 40, 13, 120, 50, 1.0f, seats(0, 0.42, -0.15), p(YELLOW, ORANGE, BLUE, RED, ACID_LIME, WHITE), false, "engine_bike"));
	public static final VehicleSpec ZOMBIE = add(new VehicleSpec("zombie", "Western Zombie", Kind.BIKE, 0.95f, 2.5f, 1.1f, 1.75f,
			50, 10, 13, 5, 34, 11, 300, 70, 1.0f, seats(0, 0.22, -0.35, 0, 0.42, -0.85), p(BLACK, MAROON, GUNMETAL, ORANGE, CANDY_GREEN),
			false, "engine_bike"));
	public static final VehicleSpec ALVINO = add(new VehicleSpec("alvino_v1", "Principe Alvino V1", Kind.BIKE, 0.85f, 2.15f, 1.15f, 1.5f,
			64, 15, 17, 5, 32, 14.5f, 200, 60, 1.0f, seats(0, 0.45, -0.15, 0, 0.58, -0.6), p(HOT_RED, WHITE, BLACK, YELLOW), false, "engine_bike"));
	public static final VehicleSpec ENDURO = add(new VehicleSpec("enduro", "Dinka Enduro", Kind.BIKE, 0.9f, 2.2f, 1.25f, 1.48f,
			40, 11, 13, 5, 40, 12.5f, 140, 55, 1.0f, seats(0, 0.44, -0.15, 0, 0.52, -0.6), p(RED, WHITE, BLUE, BLACK, YELLOW), false, "engine_bike"));
	public static final VehicleSpec LIDO = add(new VehicleSpec("lido", "Pegassi Lido", Kind.BIKE, 0.8f, 1.8f, 1.15f, 1.25f,
			24, 6, 11, 4, 40, 10, 100, 40, 1.0f, seats(0, 0.36, -0.2, 0, 0.44, -0.62), p(MINT, PINK, CREAM, BABY_BLUE, YELLOW, RED), false,
			"engine_bike"));

	// ---------------------------------------------------------------- boats
	public static final VehicleSpec AIRBOAT = add(new VehicleSpec("airboat_09", "Airboat 09", Kind.BOAT, 2.4f, 4.6f, 2.6f, 3.0f,
			30, 7, 4, 5, 34, 3, 900, 100, 1.0f, seats(0, 1.05, -0.2, -0.5, 0.45, 0.9, 0.5, 0.45, 0.9), p(OLIVE, SAND, GRAY, ARMY, RUST), false,
			"engine_boat"));
	public static final VehicleSpec SEASHARK = add(new VehicleSpec("seashark", "Speedophile Seashark", Kind.BOAT, 1.0f, 2.7f, 1.0f, 1.8f,
			32, 9, 5, 4, 40, 5, 350, 60, 0.6f, seats(0, 0.5, -0.1, 0, 0.6, -0.65), p(YELLOW, CYAN, MAGENTA, ACID_LIME, HOT_RED), false,
			"engine_bike"));
	public static final VehicleSpec CLARION = add(new VehicleSpec("clarion", "Clarion", Kind.BOAT, 2.4f, 7.0f, 1.3f, 4.6f,
			46, 8, 4, 6, 30, 4, 2200, 140, 0.6f, seats(-0.5, 0.4, 0.4, 0.5, 0.4, 0.4, -0.5, 0.4, -1.3, 0.5, 0.4, -1.3),
			p(HOT_RED, BLUE, CYAN, BLACK, MAGENTA, YELLOW), false, "engine_boat"));
	public static final VehicleSpec SQUALO = add(new VehicleSpec("squalo", "Shitzu Squalo", Kind.BOAT, 2.6f, 7.4f, 2.4f, 4.8f,
			36, 6, 4, 5, 30, 3.5f, 3000, 160, 0.6f, seats(-0.55, 0.85, 0.5, 0.55, 0.85, 0.5, -0.6, 0.5, -1.8, 0.6, 0.5, -1.8),
			p(NAVY, BOAT_WHITE, TEAL, BLACK, MAROON), false, "engine_boat"));
	public static final VehicleSpec LURE_PREDATOR = add(new VehicleSpec("lure_predator", "Center Console Fishing Boat", Kind.BOAT, 2.3f, 6.2f, 1.6f, 4.0f,
			38, 7, 4, 6, 32, 4, 1500, 120, 0.6f, seats(0, 0.55, -0.2, -0.55, 0.45, 1.3, 0.55, 0.45, -1.6), p(GUNMETAL, BOAT_WHITE, NAVY, SKY),
			false, "engine_boat"));
	public static final VehicleSpec KAYAK = add(new VehicleSpec("kayak", "Crest Kayak", Kind.BOAT, 0.8f, 3.2f, 0.6f, 2.0f,
			6, 2.5f, 3, 2, 40, 3, 40, 30, 0.6f, seats(0, 0.0, -0.1), p(ORANGE, YELLOW, ACID_LIME, CORAL, SKY), false, "engine_boat"));

	private static VehicleSpec add(VehicleSpec s) {
		SPECS.put(s.id(), s);
		return s;
	}

	public static VehicleSpec get(String id) { return SPECS.get(id); }

	public static EntityType<VehicleEntity> type(String id) { return TYPES.get(id); }

	public static EntityType<VehicleEntity> type(VehicleSpec s) { return TYPES.get(s.id()); }

	static void register() {
		for (VehicleSpec s : SPECS.values()) {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, LeonidaMod.id("vehicle_" + s.id()));
			EntityType<VehicleEntity> type = EntityType.Builder.<VehicleEntity>of((t, level) -> new VehicleEntity(t, level), MobCategory.MISC)
					.sized(s.width(), s.height())
					.clientTrackingRange(10)
					.updateInterval(1)
					.build(key);
			Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
			TYPES.put(s.id(), type);
			BY_TYPE.put(type, s);
		}
	}
}
