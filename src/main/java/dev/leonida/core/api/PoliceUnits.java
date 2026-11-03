package dev.leonida.core.api;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Police vehicles for the wanted system. The vehicle module installs cars, the aircraft module
 * installs helicopters; gameplay calls them and puts its own officers in or around them.
 * Methods may return null when the module has not installed an implementation yet.
 */
public final class PoliceUnits {
	private PoliceUnits() {}

	public interface Cars {
		/** Spawn a self-driving police cruiser (siren on) at pos that chases target; null if unsupported. swat=true for the SWAT van or heavy SUV. */
		Entity spawnCruiser(ServerLevel level, Vec3 pos, float yaw, ServerPlayer target, boolean swat);

		/** Change who the unit chases; null target means stop and park (siren off). */
		void setTarget(Entity car, ServerPlayer target);
	}

	public interface Helicopters {
		/** Spawn a police helicopter that hovers about 25 blocks above and circles target with a spotlight; null if unsupported. */
		Entity spawnHelicopter(ServerLevel level, Vec3 pos, ServerPlayer target);

		/** null target means fly away and despawn. */
		void setTarget(Entity heli, ServerPlayer target);
	}

	public static final Cars NO_CARS = new Cars() {
		public Entity spawnCruiser(ServerLevel l, Vec3 p, float y, ServerPlayer t, boolean s) { return null; }
		public void setTarget(Entity c, ServerPlayer t) {}
	};

	public static final Helicopters NO_HELIS = new Helicopters() {
		public Entity spawnHelicopter(ServerLevel l, Vec3 p, ServerPlayer t) { return null; }
		public void setTarget(Entity h, ServerPlayer t) {}
	};

	private static volatile Cars cars = NO_CARS;
	private static volatile Helicopters helis = NO_HELIS;

	public static Cars cars() { return cars; }
	public static Helicopters helicopters() { return helis; }
	public static void installCars(Cars c) { cars = c; }
	public static void installHelicopters(Helicopters h) { helis = h; }
}
