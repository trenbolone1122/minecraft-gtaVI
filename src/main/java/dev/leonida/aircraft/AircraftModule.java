package dev.leonida.aircraft;

import dev.leonida.LeonidaMod;
import dev.leonida.aircraft.api.PoliceHelicopters;
import dev.leonida.aircraft.entity.AircraftEntity;
import dev.leonida.aircraft.entity.ParachuteEntity;
import dev.leonida.aircraft.entity.Parachutes;
import dev.leonida.aircraft.item.AircraftItem;
import dev.leonida.aircraft.item.ParachuteItem;
import dev.leonida.aircraft.net.AircraftControlPayload;
import dev.leonida.aircraft.net.ParachuteDeployPayload;
import dev.leonida.core.api.PoliceUnits;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/** Server and common setup for the aircraft module. Owned by the aircraft agent. */
public final class AircraftModule {
	public static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("leonida-aircraft");
	private AircraftModule() {}

	public static final Map<String, AircraftSpec> SPECS = new LinkedHashMap<>();
	public static final Map<String, EntityType<AircraftEntity>> TYPES = new LinkedHashMap<>();
	private static final Map<AircraftSpec, Item> ITEMS = new IdentityHashMap<>();
	private static final Map<AircraftSpec, float[]> SIZES = new IdentityHashMap<>();
	public static EntityType<ParachuteEntity> PARACHUTE;
	public static Item PARACHUTE_ITEM;
	public static final SoundEvent SOUND_PROPELLER = sound("aircraft.propeller");
	public static final SoundEvent SOUND_ROTOR = sound("aircraft.rotor");
	public static final SoundEvent SOUND_WIND = sound("aircraft.wind");

	private static SoundEvent sound(String name) {
		var id = LeonidaMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	// Lore roster from the official footage (see mcmods/VEHICLES.md). Seat positions are in blocks relative to the
	// entity origin, +Z forward, +X to the pilot's left; y is the seat cushion. Sizes are the hull hit box.
	/** Boeing-Stearman biplane crop duster that trails pink smoke over the beach (Launch Trailer 0:55). */
	public static final AircraftSpec WESTERN_DUSTER = add(AircraftSpec.plane("western_duster").name("Western Duster").camera(12f)
			.seats(new Vec3(0, 1.95, -1.05), new Vec3(0, 1.95, 0.25))
			.yaw(4f).pitch(4f).engine(0.055f).friction(0.001f).decay(0.995f, 0.995f).groundFriction(0.9f)
			.glide(0.05f).lift(0.15f).roll(50f).wind(0.025f).mass(1.6f).smoke(new Vec3(0.7, 1.2, 0.35))
			.groundPitch(11f, 1.9f).variants(3), 2.6f, 2.8f);
	/** de Havilland Canada Beaver on floats, Brian's plane at the marina (Trailer 2). */
	public static final AircraftSpec FLOATPLANE = add(AircraftSpec.plane("floatplane").name("Floatplane").camera(18f)
			.seats(new Vec3(0.32, 1.75, 1.5), new Vec3(-0.32, 1.75, 1.5), new Vec3(0.4, 1.75, 0.4), new Vec3(-0.4, 1.75, 0.4))
			.yaw(3f).pitch(3f).engine(0.05f).friction(0.001f).decay(0.995f, 0.995f).groundFriction(0.85f)
			.glide(0.05f).lift(0.15f).roll(35f).wind(0.025f).mass(2.2f).floats().variants(3), 3.0f, 3.3f);
	/** Mammoth Dodo, twin engine amphibian. */
	public static final AircraftSpec DODO = add(AircraftSpec.plane("dodo").name("Mammoth Dodo").camera(19f)
			.seats(new Vec3(0.33, 1.725, 3.35), new Vec3(-0.33, 1.725, 3.35), new Vec3(0.35, 1.725, 1.8), new Vec3(-0.35, 1.725, 1.8),
					new Vec3(0.35, 1.725, 0.375), new Vec3(-0.35, 1.725, 0.375))
			.yaw(2.6f).pitch(2.6f).engine(0.055f).friction(0.001f).decay(0.995f, 0.995f).groundFriction(0.85f)
			.glide(0.05f).lift(0.15f).roll(30f).wind(0.02f).mass(3f).floats().groundPitch(5f, 0f).variants(2), 3.0f, 3.0f);
	/** Buckingham Shamal business jet (Learjet 35 style). */
	public static final AircraftSpec SHAMAL = add(AircraftSpec.plane("shamal").name("Buckingham Shamal").camera(20f)
			.seats(new Vec3(0.28, 1.225, 5.15), new Vec3(-0.28, 1.225, 5.15), new Vec3(0.28, 1.225, 3.75), new Vec3(-0.28, 1.225, 3.75),
					new Vec3(0.28, 1.225, 2.5), new Vec3(-0.28, 1.225, 2.5))
			.yaw(2.2f).pitch(2.4f).engine(0.085f).friction(0.0008f).decay(0.996f, 0.995f).groundFriction(0.9f)
			.glide(0.04f).lift(0.14f).roll(30f).wind(0.012f).mass(4f).variants(2), 2.8f, 2.8f);
	/** Buckingham Maverick, Bell 206 JetRanger. */
	public static final AircraftSpec MAVERICK = add(maverick("maverick").name("Buckingham Maverick").variants(3), 2.6f, 2.9f);
	/** Police Maverick in VCPD white and blue with a Nightsun searchlight (Trailer 2 2:27). */
	public static final AircraftSpec POLICE_MAVERICK = add(maverick("police_maverick").name("Police Maverick")
			.searchlight(new Vec3(-12 / 16.0, 11 / 16.0, 31 / 16.0)), 2.6f, 2.9f);
	/** Weazel News Maverick with a camera ball. */
	public static final AircraftSpec NEWS_MAVERICK = add(maverick("news_maverick").name("Weazel News Maverick").newsCamera(), 2.6f, 2.9f);
	/** Nagasaki Buzzard (MD 500 Defender) in police livery, also with a searchlight. */
	public static final AircraftSpec BUZZARD = add(AircraftSpec.helicopter("buzzard").name("Nagasaki Buzzard").camera(11f)
			.seats(new Vec3(0.28, 0.975, 0.6), new Vec3(-0.28, 0.975, 0.6))
			.yaw(5f).pitch(5f).engine(0.15f).searchlight(new Vec3(-6 / 16.0, 10.5 / 16.0, 22 / 16.0)), 2.2f, 2.6f);

	/** The unit the police helicopter API sends after wanted players. */
	public static final AircraftSpec POLICE_HELICOPTER = POLICE_MAVERICK;

	private static AircraftSpec.Builder maverick(String id) {
		return AircraftSpec.helicopter(id).camera(14f).seats(new Vec3(-0.32, 0.975, 1.35), new Vec3(0.32, 0.975, 1.35),
				new Vec3(-0.4, 0.975, 0.1), new Vec3(0.4, 0.975, 0.1));
	}

	private static AircraftSpec add(AircraftSpec.Builder b, float width, float height) {
		AircraftSpec s = b.build();
		SPECS.put(s.id, s);
		SIZES.put(s, new float[] {width, height});
		return s;
	}

	public static Item item(AircraftSpec spec) {
		return ITEMS.get(spec);
	}

	public static EntityType<AircraftEntity> type(AircraftSpec spec) {
		return TYPES.get(spec.id);
	}

	public static void init() {
		List<Item> tabItems = new ArrayList<>();
		for (AircraftSpec spec : SPECS.values()) {
			ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, LeonidaMod.id("aircraft/" + spec.id));
			float[] size = SIZES.get(spec);
			EntityType<AircraftEntity> type = EntityType.Builder.<AircraftEntity>of((t, l) -> new AircraftEntity(t, l, spec), MobCategory.MISC)
					.sized(size[0], size[1]).clientTrackingRange(16).updateInterval(1).build(key);
			Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
			TYPES.put(spec.id, type);
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, LeonidaMod.id("aircraft/" + spec.id));
			Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new AircraftItem(spec, new Item.Properties().stacksTo(1).setId(itemKey)));
			ITEMS.put(spec, item);
			tabItems.add(item);
		}
		ResourceKey<EntityType<?>> chuteKey = ResourceKey.create(Registries.ENTITY_TYPE, LeonidaMod.id("aircraft/parachute"));
		PARACHUTE = Registry.register(BuiltInRegistries.ENTITY_TYPE, chuteKey,
				EntityType.Builder.<ParachuteEntity>of(ParachuteEntity::new, MobCategory.MISC).sized(0.8f, 1.8f)
						.clientTrackingRange(10).updateInterval(1).build(chuteKey));
		ResourceKey<Item> chuteItemKey = ResourceKey.create(Registries.ITEM, LeonidaMod.id("aircraft/parachute"));
		PARACHUTE_ITEM = Registry.register(BuiltInRegistries.ITEM, chuteItemKey, new ParachuteItem(new Item.Properties().stacksTo(1).setId(chuteItemKey)));
		tabItems.add(PARACHUTE_ITEM);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(out -> tabItems.forEach(out::accept));

		PayloadTypeRegistry.serverboundPlay().register(AircraftControlPayload.TYPE, AircraftControlPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ParachuteDeployPayload.TYPE, ParachuteDeployPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AircraftControlPayload.TYPE, (p, ctx) -> {
			if (ctx.player().level().getEntity(p.entityId()) instanceof AircraftEntity a && a.getControllingPassenger() == ctx.player()) {
				a.setEngineTarget(p.engineTarget());
				a.setSmoke(p.smoke());
				a.setSearchlight(p.searchlight());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(ParachuteDeployPayload.TYPE, (p, ctx) -> Parachutes.deploy(ctx.player()));

		CommandRegistrationCallback.EVENT.register((dispatcher, registries, env) -> AircraftCommands.register(dispatcher));
		PoliceUnits.installHelicopters(PoliceHelicopters.INSTANCE);
		AircraftSpawns.init();
	}
}
