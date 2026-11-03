package dev.leonida.vehicle;

import dev.leonida.LeonidaMod;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public final class VehicleSounds {
	private VehicleSounds() {}

	public static final Map<String, SoundEvent> ALL = new HashMap<>();

	public static final SoundEvent ENGINE_CAR = reg("engine_car");
	public static final SoundEvent ENGINE_MUSCLE = reg("engine_muscle");
	public static final SoundEvent ENGINE_BIKE = reg("engine_bike");
	public static final SoundEvent ENGINE_BOAT = reg("engine_boat");
	public static final SoundEvent HORN = reg("horn");
	public static final SoundEvent SIREN = reg("siren");
	public static final SoundEvent SIREN_YELP = reg("siren_yelp");
	public static final SoundEvent ALARM = reg("alarm");
	public static final SoundEvent GLASS = reg("glass");
	public static final SoundEvent SLIMJIM = reg("slimjim");
	public static final SoundEvent CRASH = reg("crash");
	public static final SoundEvent DOOR = reg("door");
	public static final SoundEvent SKID = reg("skid");
	public static final SoundEvent START = reg("start");

	private static SoundEvent reg(String name) {
		Identifier id = LeonidaMod.id("vehicle." + name);
		SoundEvent e = SoundEvent.createVariableRangeEvent(id);
		ALL.put(name, e);
		return e;
	}

	static void register() {
		for (SoundEvent e : ALL.values()) Registry.register(BuiltInRegistries.SOUND_EVENT, e.location(), e);
	}

	public static SoundEvent engine(VehicleSpec s) {
		return ALL.getOrDefault(s.engineSound(), ENGINE_CAR);
	}
}
