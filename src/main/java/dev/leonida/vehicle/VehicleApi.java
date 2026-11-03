package dev.leonida.vehicle;

import dev.leonida.vehicle.ai.PursuitDriver;
import dev.leonida.vehicle.ai.TrafficManager;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Public vehicle API for other modules (gameplay police, missions). Server side only.
 */
public final class VehicleApi {
	private VehicleApi() {}

	/** Creates the pedestrian that gets pulled out of a carjacked traffic car. Gameplay/character may replace it. */
	public interface PedestrianFactory {
		@Nullable LivingEntity spawnDriver(ServerLevel level, Vec3 pos, VehicleEntity car, ServerPlayer carjacker);
	}

	public static PedestrianFactory pedestrianFactory = (level, pos, car, p) -> {
		Villager v = net.minecraft.world.entity.EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
		if (v == null) return null;
		v.snapTo(pos.x, pos.y, pos.z, car.getYRot() + 90, 0);
		level.addFreshEntity(v);
		return v;
	};

	public static boolean isPoliceVehicle(@Nullable Entity e) {
		return e instanceof VehicleEntity v && v.isPolice();
	}

	/** The vehicle a player is in (driver or passenger), or null. */
	@Nullable public static VehicleEntity vehicleOf(@Nullable Entity e) {
		return e != null && e.getVehicle() instanceof VehicleEntity v ? v : null;
	}

	public static boolean isDriver(Entity e) {
		VehicleEntity v = vehicleOf(e);
		return v != null && v.getControllingPassenger() == e;
	}

	/** Spawn any vehicle by spec id (see VehicleTypes), unlocked, no driver. */
	@Nullable public static VehicleEntity spawn(ServerLevel level, String specId, Vec3 pos, float yaw) {
		VehicleSpec s = VehicleTypes.get(specId);
		if (s == null) return null;
		VehicleEntity v = TrafficManager.create(level, s, pos, yaw);
		if (v != null) level.addFreshEntity(v);
		return v;
	}

	/**
	 * Spawn a self-driving police car chasing target (siren on). heavy=true gives the police SUV.
	 * Officers (any Mob) are seated with startRiding; they stay seated until {@link #exitAll}.
	 */
	@Nullable public static VehicleEntity spawnPolice(ServerLevel level, Vec3 pos, float yaw, @Nullable Entity target, boolean heavy, List<? extends Mob> officers) {
		return spawnPolice(level, pos, yaw, target, heavy ? VehicleTypes.POLICE_SUV : VehicleTypes.POLICE_CRUISER, false, officers);
	}

	/** Police models: police_cruiser, police_suv, buffalo_police, bison_police, police_riot (SWAT). */
	public static final List<VehicleSpec> POLICE_MODELS = List.of(VehicleTypes.POLICE_CRUISER, VehicleTypes.POLICE_SUV,
			VehicleTypes.BUFFALO_POLICE, VehicleTypes.BISON_POLICE, VehicleTypes.POLICE_RIOT);

	/**
	 * Spawn a self-driving police vehicle of the given model chasing target. sheriff=true paints the Leonard County Sheriff
	 * livery (green and white), otherwise VCPD (white and blue).
	 */
	@Nullable public static VehicleEntity spawnPolice(ServerLevel level, Vec3 pos, float yaw, @Nullable Entity target, VehicleSpec model,
			boolean sheriff, List<? extends Mob> officers) {
		VehicleEntity v = TrafficManager.create(level, model, pos, yaw);
		if (v == null) return null;
		setLivery(v, sheriff);
		v.setDriver(new PursuitDriver(target));
		v.setSiren(target != null);
		v.setHeadlights(true);
		v.setLocked(false);
		level.addFreshEntity(v);
		for (Mob m : officers) m.startRiding(v, true, false);
		return v;
	}

	/** Police livery: true = Leonard County Sheriff, false = VCPD. No effect on civilian vehicles. */
	public static void setLivery(VehicleEntity v, boolean sheriff) {
		if (v.isPolice()) v.setColor(sheriff ? VehicleTypes.SHERIFF_GREEN : VehicleTypes.VCPD_WHITE);
	}

	public static boolean isSheriff(VehicleEntity v) { return v.isPolice() && VehicleTypes.isSheriffLivery(v.getColor()); }

	/** Make an AI vehicle chase target (siren on), or park with null. Takes over traffic cars too. */
	public static void pursue(VehicleEntity v, @Nullable Entity target) {
		if (v.getControllingPassenger() != null) return;
		if (v.getDriver() instanceof PursuitDriver pd) pd.setTarget(target);
		else v.setDriver(new PursuitDriver(target));
		v.setTraffic(false);
		v.setSiren(target != null);
		if (target != null) v.setHeadlights(true);
	}

	/** Called once each time a pursuing car pulls up next to a stopped target. */
	public static void onArrive(VehicleEntity v, @Nullable Consumer<VehicleEntity> callback) {
		if (v.getDriver() instanceof PursuitDriver pd) pd.onArrive(callback);
	}

	/** Eject every passenger (officers bail out). */
	public static void exitAll(VehicleEntity v) {
		v.ejectPassengers();
	}

	static void ejectNpcDriver(VehicleEntity car, Vec3 door, ServerPlayer p) {
		if (!(car.level() instanceof ServerLevel level)) return;
		LivingEntity npc = pedestrianFactory.spawnDriver(level, door, car, p);
		if (npc != null) npc.push(car.right().scale(-0.4).add(0, 0.2, 0));
	}

	/** PoliceUnits.Cars implementation installed in VehicleModule.init. */
	static final dev.leonida.core.api.PoliceUnits.Cars POLICE_CARS = new dev.leonida.core.api.PoliceUnits.Cars() {
		@Override
		public Entity spawnCruiser(ServerLevel level, Vec3 pos, float yaw, ServerPlayer target, boolean swat) {
			return spawnPolice(level, pos, yaw, target, swat, List.of());
		}

		@Override
		public void setTarget(Entity car, ServerPlayer target) {
			if (car instanceof VehicleEntity v) pursue(v, target);
		}
	};
}
