package dev.leonida.vehicle.ai;

import dev.leonida.core.api.MapQuery;
import dev.leonida.road.api.RoadLanes;
import dev.leonida.core.api.MapQuery.Surface;
import dev.leonida.vehicle.VehicleEntity;
import dev.leonida.vehicle.VehicleSpec;
import dev.leonida.vehicle.VehicleTypes;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Spawns and despawns ambient traffic (driving and parked cars) on roads around players. */
public final class TrafficManager {
	private TrafficManager() {}

	public static final double LANE_OFFSET = 2.0;
	public static boolean enabled = true;
	/** Target number of traffic vehicles around each player. */
	public static int density = 10;
	public static double parkedShare = 0.3;
	public static final int DESPAWN_RANGE = 150;

	private static final Set<VehicleEntity> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

	/** Ambient lore cars, weighted by repetition (everyday cars more often than supercars). */
	private static final VehicleSpec[] CIVILIAN = {
			VehicleTypes.STANIER, VehicleTypes.STANIER, VehicleTypes.STANIER, VehicleTypes.GRANGER, VehicleTypes.GRANGER,
			VehicleTypes.GRANGER_TAXI, VehicleTypes.GRANGER_TAXI, VehicleTypes.BISON, VehicleTypes.BISON, VehicleTypes.CARACARA,
			VehicleTypes.SPEEDO, VehicleTypes.BUFFALO, VehicleTypes.BUFFALO, VehicleTypes.DOMINATOR, VehicleTypes.GAUNTLET_HELLFIRE,
			VehicleTypes.TULIP, VehicleTypes.DUBSTA, VehicleTypes.ELEGY, VehicleTypes.SENTINEL_CABRIO, VehicleTypes.COQUETTE_D10,
			VehicleTypes.VOODOO, VehicleTypes.CHEETAH, VehicleTypes.TEMPESTA, VehicleTypes.INFERNUS_CLASSIC, VehicleTypes.ADDER,
			VehicleTypes.STOCKADE, VehicleTypes.LIDO, VehicleTypes.SANCHEZ, VehicleTypes.ZOMBIE, VehicleTypes.ALVINO};

	public static boolean isRoad(Surface s) { return s == Surface.ROAD || s == Surface.HIGHWAY; }

	public static void track(VehicleEntity v) { TRACKED.add(v); }

	public static void tick(ServerLevel level) {
		long t = level.getGameTime();
		if (t % 20 == 0) {
			TRACKED.removeIf(v -> v.isRemoved() || !v.isTraffic());
			for (VehicleEntity v : List.copyOf(TRACKED)) {
				if (v.level() != level) continue;
				ServerPlayer near = (ServerPlayer) level.getNearestPlayer(v, DESPAWN_RANGE);
				if (near == null && !v.isVehicle() && !v.touchedByPlayer()) v.discard();
			}
		}
		if (!enabled || t % 20 != 7) return;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) continue;
			int count = 0;
			for (VehicleEntity v : TRACKED) if (v.level() == level && v.distanceToSqr(p) < 120 * 120) count++;
			if (count < density) trySpawn(level, p);
		}
	}

	public static VehicleEntity trySpawn(ServerLevel level, ServerPlayer p) {
		if (LaneDriver.available(level)) return trySpawnOnLane(level, p);
		MapQuery map = MapQuery.get();
		RandomSource rnd = level.getRandom();
		Vec3 look = p.getLookAngle();
		for (int i = 0; i < 64; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2;
			double dist = 50 + rnd.nextDouble() * 50;
			double dx = Math.cos(ang), dz = Math.sin(ang);
			if (i < 40 && dx * look.x + dz * look.z > 0.4) continue; // prefer spawning out of view
			int x = Mth.floor(p.getX() + dx * dist), z = Mth.floor(p.getZ() + dz * dist);
			if (!isRoad(map.surfaceAt(x, z))) continue;
			if (!level.hasChunkAt(new BlockPos(x, 0, z))) continue;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos ground = new BlockPos(x, y - 1, z);
			if (!level.getFluidState(ground).isEmpty() || level.getFluidState(ground.above()).isEmpty() == false) continue;
			Vec3 pos = new Vec3(x + 0.5, y, z + 0.5);
			if (!level.getEntitiesOfClass(VehicleEntity.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(14)).isEmpty()) continue;
			if (level.getNearestPlayer(pos.x, pos.y, pos.z, 40, false) != null) continue;
			boolean parked = rnd.nextDouble() < parkedShare;
			return parked ? spawnParked(level, pos, rnd) : spawnDriving(level, pos, rnd);
		}
		return null;
	}

	/** Real-map spawning: a random lane point 50 to 100 blocks out, facing along the lane. */
	public static VehicleEntity trySpawnOnLane(ServerLevel level, ServerPlayer p) {
		RandomSource rnd = level.getRandom();
		Vec3 look = p.getLookAngle();
		for (int i = 0; i < 48; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2;
			double dist = 50 + rnd.nextDouble() * 50;
			double dx = Math.cos(ang), dz = Math.sin(ang);
			if (i < 32 && dx * look.x + dz * look.z > 0.4) continue; // prefer spawning out of view
			List<RoadLanes.LanePos> lanes = RoadLanes.lanesNear(p.getX() + dx * dist, p.getZ() + dz * dist, 10);
			if (lanes.isEmpty()) continue;
			RoadLanes.LanePos lp = lanes.get(rnd.nextInt(lanes.size()));
			Vec3 pos = new Vec3(lp.x(), lp.y(), lp.z());
			if (!level.hasChunkAt(BlockPos.containing(pos))) continue;
			if (!level.getEntitiesOfClass(VehicleEntity.class, new net.minecraft.world.phys.AABB(pos, pos).inflate(12)).isEmpty()) continue;
			if (level.getNearestPlayer(pos.x, pos.y, pos.z, 40, false) != null) continue;
			return spawnOnLane(level, lp, pick(rnd), rnd);
		}
		return null;
	}

	/** Spawns a lane-following traffic car at a lane position. */
	public static VehicleEntity spawnOnLane(ServerLevel level, RoadLanes.LanePos lp, VehicleSpec spec, RandomSource rnd) {
		VehicleEntity v = create(level, spec, new Vec3(lp.x(), lp.y(), lp.z()), lp.yaw());
		if (v == null) return null;
		LaneDriver d = new LaneDriver(rnd.nextInt(), 0.75 + rnd.nextDouble() * 0.2);
		d.setTrack(lp);
		v.setDriver(d);
		v.setNpcDriver(true);
		v.setHeadlights(level.isDarkOutside());
		v.setTraffic(true);
		level.addFreshEntity(v);
		track(v);
		return v;
	}

	private static VehicleSpec pick(RandomSource rnd) { return CIVILIAN[rnd.nextInt(CIVILIAN.length)]; }

	public static VehicleEntity spawnDriving(ServerLevel level, Vec3 pos, RandomSource rnd) {
		VehicleSpec spec = pick(rnd);
		VehicleEntity v = create(level, spec, pos, 0);
		if (v == null) return null;
		TrafficDriver d = new TrafficDriver();
		// route to a random road destination
		MapQuery map = MapQuery.get();
		for (int i = 0; i < 48; i++) {
			double ang = rnd.nextDouble() * Math.PI * 2, dist = 100 + rnd.nextDouble() * 200;
			int x = Mth.floor(pos.x + Math.cos(ang) * dist), z = Mth.floor(pos.z + Math.sin(ang) * dist);
			if (!isRoad(map.surfaceAt(x, z))) continue;
			List<BlockPos> route = map.route(BlockPos.containing(pos), new BlockPos(x, (int) pos.y, z));
			if (route.size() < 2) continue;
			d.setRoute(route, pos.y);
			if (d.path != null && !d.path.isEmpty()) {
				Vec3 first = d.path.get(Math.min(1, d.path.size() - 1));
				float yaw = (float) (Mth.atan2(first.z - pos.z, first.x - pos.x) * Mth.RAD_TO_DEG) - 90f;
				// start in the right-hand lane
				Vec3 dir = new Vec3(first.x - pos.x, 0, first.z - pos.z).normalize();
				Vec3 lane = new Vec3(-dir.z, 0, dir.x).scale(LANE_OFFSET);
				v.snapTo(pos.x + lane.x, pos.y, pos.z + lane.z, yaw, 0);
				break;
			}
		}
		v.setDriver(d);
		v.setNpcDriver(true);
		v.setHeadlights(level.isDarkOutside());
		v.setTraffic(true);
		level.addFreshEntity(v);
		track(v);
		return v;
	}

	public static VehicleEntity spawnParked(ServerLevel level, Vec3 pos, RandomSource rnd) {
		VehicleSpec spec = pick(rnd);
		MapQuery map = MapQuery.get();
		// road direction: the heading along which the road continues furthest
		float bestYaw = 0;
		int best = -1;
		for (int a = 0; a < 8; a++) {
			double r = a * Math.PI / 4;
			int n = 0;
			for (int s = 1; s <= 12; s++) {
				if (isRoad(map.surfaceAt(Mth.floor(pos.x - Math.sin(r) * s), Mth.floor(pos.z + Math.cos(r) * s)))) n++;
			}
			if (n > best) { best = n; bestYaw = (float) Math.toDegrees(r); }
		}
		// slide sideways to the kerb
		double r = Math.toRadians(bestYaw);
		Vec3 right = new Vec3(-Math.cos(r), 0, -Math.sin(r));
		Vec3 p = pos;
		for (int s = 1; s <= 8; s++) {
			Vec3 c = pos.add(right.scale(s));
			if (!isRoad(map.surfaceAt(Mth.floor(c.x), Mth.floor(c.z)))) { p = pos.add(right.scale(Math.max(0, s - 1.5))); break; }
		}
		VehicleEntity v = create(level, spec, p, bestYaw);
		if (v == null) return null;
		v.setLocked(rnd.nextFloat() < 0.85f);
		v.setTraffic(true);
		level.addFreshEntity(v);
		track(v);
		return v;
	}

	public static VehicleEntity create(ServerLevel level, VehicleSpec spec, Vec3 pos, float yaw) {
		VehicleEntity v = VehicleTypes.type(spec).create(level, EntitySpawnReason.EVENT);
		if (v == null) return null;
		v.initFresh();
		// sit on the real collision top: lane heights are the segment grade rounded to half blocks and can be
		// below the built surface (streets, junction pads), and an embedded box falls through the road
		double y = spec.isBoat() ? pos.y : VehicleEntity.surfaceTop(level, pos.x, pos.z, pos.y, spec.width()) + 0.01;
		v.snapTo(pos.x, y, pos.z, yaw, 0);
		return v;
	}
}
