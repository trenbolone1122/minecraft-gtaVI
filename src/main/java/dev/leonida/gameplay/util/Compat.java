package dev.leonida.gameplay.util;

import dev.leonida.core.api.AmmoProvider;
import dev.leonida.core.api.BuildingGenerator;
import dev.leonida.core.api.CharacterEvents;
import dev.leonida.core.api.MapQuery;
import dev.leonida.core.api.PoliceUnits;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Soft links to other modules and to newer core APIs, resolved by reflection so gameplay never
 * depends on another branch being merged. Every method has a safe fallback.
 */
public final class Compat {
	private Compat() {}

	public static final TagKey<EntityType<?>> POLICE_VEHICLE_TAG = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("leonida", "vehicle/police"));

	private static Object call(String cls, String name, Class<?>[] types, Object target, Object... args) {
		try {
			Class<?> c = Class.forName(cls);
			Method m = c.getMethod(name, types);
			return m.invoke(target, args);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			return null;
		}
	}

	private static Object getter(String cls, String name) {
		return call(cls, name, new Class<?>[0], null);
	}

	public static Entity spawnPoliceCar(ServerLevel level, Vec3 pos, float yaw, ServerPlayer target, boolean swat) {
		try { return PoliceUnits.cars().spawnCruiser(level, pos, yaw, target, swat); } catch (RuntimeException e) { return null; }
	}

	public static void setPoliceCarTarget(Entity car, ServerPlayer target) {
		try { PoliceUnits.cars().setTarget(car, target); } catch (RuntimeException ignored) {}
	}

	public static Entity spawnHelicopter(ServerLevel level, Vec3 pos, ServerPlayer target) {
		try { return PoliceUnits.helicopters().spawnHelicopter(level, pos, target); } catch (RuntimeException e) { return null; }
	}

	public static void setHelicopterTarget(Entity heli, ServerPlayer target) {
		try { PoliceUnits.helicopters().setTarget(heli, target); } catch (RuntimeException ignored) {}
	}

	public static boolean isPoliceVehicle(Entity e) {
		return e != null && e.is(POLICE_VEHICLE_TAG);
	}

	/** Nearest building entrance of this kind from the world module, or null. */
	public static BlockPos nearestBuilding(BuildingGenerator.Kind kind, int x, int z) {
		try { return MapQuery.get().nearest(kind, x, z); } catch (RuntimeException e) { return null; }
	}

	public static void onOutfitChanged(Consumer<ServerPlayer> handler) {
		CharacterEvents.OUTFIT_CHANGED.register(handler::accept);
	}

	/** Weapon module's NPC shot (tracer, flash, sound, hitscan with gun stats). False if unavailable or not fired. */
	public static boolean weaponNpcShoot(Mob shooter, LivingEntity target, float spread) {
		return dev.leonida.weapon.api.WeaponApi.npcShoot(shooter, target, spread);
	}

	/** True if the mob holds a weapon-module gun (fired through WeaponApi.npcShoot). */
	public static boolean holdsWeaponGun(Mob mob) {
		return dev.leonida.weapon.api.WeaponApi.isGun(mob.getMainHandItem());
	}

	/** Gives the mob a loaded weapon-module gun (pistol, smg, rifle, shotgun, sniper, rpg). */
	public static boolean weaponArm(Mob mob, String id) {
		dev.leonida.weapon.api.WeaponApi.arm(mob, id);
		return !mob.getMainHandItem().isEmpty();
	}

	/**
	 * Which agency answers at a spot: VCPD inside Vice City, Leonard County Sheriff outside.
	 * Uses the world's place names when present, otherwise city surfaces (sidewalk, road) mean VCPD.
	 */
	public static int agencyAt(int x, int z) {
		MapQuery q = MapQuery.get();
		String place = q.placeName(x, z);
		if (place != null) return place.toLowerCase(java.util.Locale.ROOT).contains("vice") ? 0 : 1;
		MapQuery.Surface s = q.surfaceAt(x, z);
		return s == MapQuery.Surface.SIDEWALK || s == MapQuery.Surface.ROAD ? 0 : 1;
	}

	public static boolean weaponModuleFiresKills() {
		try {
			Class.forName("dev.leonida.weapon.api.WeaponApi");
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	public static ItemStack item(String id) {
		Identifier rl = Identifier.tryParse(id);
		if (rl == null) return ItemStack.EMPTY;
		return BuiltInRegistries.ITEM.getOptional(rl).map(i -> new ItemStack((Item) i)).orElse(ItemStack.EMPTY);
	}

	/** A gun (per the weapon module) or any leonida weapon item counts as armed. */
	public static boolean isGun(Player p, ItemStack s) {
		if (s.isEmpty()) return false;
		// Vanilla ranged weapons count too, so robberies work without the weapon module.
		if (s.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem) return true;
		if (AmmoProvider.get().ammo(p, s) != null) return true;
		Identifier id = BuiltInRegistries.ITEM.getKey(s.getItem());
		String path = id.getPath();
		return id.getNamespace().equals("leonida") && path.startsWith("weapon/") && !path.contains("ammo") && !path.equals("weapon/bat") && !path.equals("weapon/knife");
	}

	public static boolean isArmed(Player p) {
		return isGun(p, p.getMainHandItem()) || isGun(p, p.getOffhandItem());
	}
}
