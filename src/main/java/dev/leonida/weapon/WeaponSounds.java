package dev.leonida.weapon;

import dev.leonida.LeonidaMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** Sound events. The audio files are CC0 sounds from Just Enough Guns (see NOTICE.d/weapon.md). */
public final class WeaponSounds {
	private WeaponSounds() {}

	public static final SoundEvent[] FIRE = new SoundEvent[GunType.values().length];
	/** Muffled, echoing report heard from far away (generated from the fire sounds, see assetgen notes). */
	public static final SoundEvent[] FIRE_FAR = new SoundEvent[GunType.values().length];
	public static final SoundEvent MAG_OUT = reg("mag_out");
	public static final SoundEvent MAG_IN = reg("mag_in");
	public static final SoundEvent PISTOL_MAG_OUT = reg("pistol_mag_out");
	public static final SoundEvent PISTOL_MAG_IN = reg("pistol_mag_in");
	public static final SoundEvent CHARGE = reg("charge");
	public static final SoundEvent PUMP = reg("pump");
	public static final SoundEvent SHELL = reg("shell");
	public static final SoundEvent BOLT = reg("bolt");
	public static final SoundEvent ROCKET_IN = reg("rocket_in");
	public static final SoundEvent EMPTY = reg("empty");
	public static final SoundEvent EQUIP = reg("equip");
	public static final SoundEvent HIT_ENTITY = reg("hit_entity");
	public static final SoundEvent HIT_STONE = reg("hit_stone");
	public static final SoundEvent HIT_METAL = reg("hit_metal");
	public static final SoundEvent HIT_WOOD = reg("hit_wood");
	public static final SoundEvent EXPLOSION_FAR = reg("explosion_far");
	public static final SoundEvent CASING = reg("casing");

	static {
		for (GunType t : GunType.values()) FIRE[t.ordinal()] = reg(t.id + "_fire");
		for (GunType t : GunType.values()) FIRE_FAR[t.ordinal()] = reg(t.id + "_fire_far");
	}

	private static SoundEvent reg(String name) {
		var id = LeonidaMod.id("weapon." + name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	static void init() {}

	static SoundEvent casing(net.minecraft.util.RandomSource r) {
		return CASING;
	}

	public static SoundEvent reloadStart(GunType t) {
		return switch (t) {
			case PISTOL -> PISTOL_MAG_OUT;
			case SHOTGUN -> SHELL;
			case SNIPER -> BOLT;
			case RPG -> ROCKET_IN;
			default -> MAG_OUT;
		};
	}

	public static SoundEvent reloadEnd(GunType t) {
		return switch (t) {
			case PISTOL -> PISTOL_MAG_IN;
			case SHOTGUN -> PUMP;
			case SNIPER -> BOLT;
			case RPG -> CHARGE;
			default -> CHARGE;
		};
	}
}
