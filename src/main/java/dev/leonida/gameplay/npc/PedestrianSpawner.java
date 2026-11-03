package dev.leonida.gameplay.npc;

import dev.leonida.core.api.MapQuery;
import dev.leonida.gameplay.GameplayRegistry;
import dev.leonida.gameplay.util.SpawnFinder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import dev.leonida.vehicle.VehicleEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Keeps a crowd of ambient locals on the sidewalks around each player, out of their view. */
public final class PedestrianSpawner {
	private PedestrianSpawner() {}

	/** Target number of ambient pedestrians within 64 blocks of each player. 0 disables. */
	public static int density = 14;

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PedestrianSpawner::tick);
	}

	private static void tick(MinecraftServer server) {
		if (density <= 0 || server.getTickCount() % 40 != 0) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator()) continue;
			ServerLevel level = p.level();
			if (level.dimension() == Level.NETHER || level.dimension() == Level.END) continue;
			int have = level.getEntitiesOfClass(Pedestrian.class, p.getBoundingBox().inflate(64, 32, 64), e -> !(e instanceof StoreClerk)).size();
			int toSpawn = Math.min(3, density - have);
			for (int i = 0; i < toSpawn; i++) spawnAmbient(level, p.position(), 24, 56, p);
			fillTrafficCars(level, p);
		}
	}

	/**
	 * Traffic cars from the vehicle module drive themselves; seat a local behind the wheel (seat 0)
	 * and sometimes a passenger, so traffic has people in it. Done out of the player's view. When
	 * the car is carjacked the vehicle module pulls mob passengers out, and they flee and report it.
	 */
	private static void fillTrafficCars(ServerLevel level, ServerPlayer p) {
		for (VehicleEntity car : level.getEntitiesOfClass(VehicleEntity.class, p.getBoundingBox().inflate(72, 24, 72),
				v -> v.isTraffic() && v.hasNpcDriver() && !v.isPolice() && v.getPassengers().isEmpty())) {
			Vec3 to = car.position().subtract(p.getEyePosition());
			if (to.length() < 20 || (p.getLookAngle().dot(to.normalize()) > 0.3 && to.length() < 60)) continue;
			int riders = 1 + (level.getRandom().nextInt(3) == 0 ? 1 : 0);
			for (int i = 0; i < riders; i++) {
				Pedestrian ped = spawn(level, car.position());
				if (ped == null) break;
				ped.ambient = true;
				if (!ped.startRiding(car, true, false)) {
					ped.discard();
					break;
				}
			}
		}
	}

	/** Carjack victim factory for the vehicle module: reuse the seated driver, or make a local. */
	public static LivingEntity carjackVictim(ServerLevel level, Vec3 door, VehicleEntity car, ServerPlayer jacker) {
		for (var e : car.getPassengers()) {
			if (e instanceof Pedestrian ped) {
				ped.hearDanger(jacker.position(), jacker);
				return null; // the vehicle module drags seated mobs out itself
			}
		}
		Pedestrian ped = spawn(level, door);
		if (ped != null) ped.hearDanger(jacker.position(), jacker);
		return ped;
	}

	public static Pedestrian spawnAmbient(ServerLevel level, Vec3 around, double minR, double maxR, ServerPlayer viewer) {
		BlockPos pos = SpawnFinder.ring(level, around, minR, maxR, 12, q -> viewer == null || !sees(viewer, q),
				MapQuery.Surface.SIDEWALK, MapQuery.Surface.PARK, MapQuery.Surface.BEACH, MapQuery.Surface.LAND);
		if (pos == null) return null;
		MapQuery.Surface s = MapQuery.get().surfaceAt(pos.getX(), pos.getZ());
		if (s == MapQuery.Surface.ROAD || s == MapQuery.Surface.HIGHWAY || s == MapQuery.Surface.RUNWAY) return null;
		Pedestrian ped = spawn(level, Vec3.atBottomCenterOf(pos));
		if (ped != null) ped.ambient = true;
		return ped;
	}

	public static Pedestrian spawn(ServerLevel level, Vec3 at) {
		Pedestrian ped = GameplayRegistry.PEDESTRIAN.create(level, EntitySpawnReason.EVENT);
		if (ped == null) return null;
		ped.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360f, 0);
		level.addFreshEntity(ped);
		return ped;
	}

	private static boolean sees(ServerPlayer p, BlockPos q) {
		Vec3 to = Vec3.atBottomCenterOf(q).add(0, 1, 0).subtract(p.getEyePosition());
		return to.length() < 30 || p.getLookAngle().dot(to.normalize()) > 0.5;
	}
}
