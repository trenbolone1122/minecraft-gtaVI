package dev.leonida.gameplay.police;

import dev.leonida.core.PlayerState;
import dev.leonida.core.api.BuildingGenerator;
import dev.leonida.core.api.MapQuery;
import dev.leonida.gameplay.GameplayRegistry;
import dev.leonida.gameplay.npc.PoliceOfficer;
import dev.leonida.gameplay.util.Compat;
import dev.leonida.gameplay.util.HudBridge;
import dev.leonida.gameplay.util.SpawnFinder;
import dev.leonida.gameplay.wanted.WantedSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

/**
 * Sends police after wanted players and handles getting busted. Response by stars:
 * 1: two patrol officers, one cruiser. 2: four officers, two cruisers. 3: six officers, three
 * cruisers, lethal force. 4: plus a SWAT team in an SUV. 5: plus a helicopter with a marksman.
 * 6: heavy response, more SWAT with shotguns and two helicopters.
 */
public final class PoliceDispatch {
	private PoliceDispatch() {}

	/** stars -> {patrol officers, cruisers, swat, helicopters} */
	private static final int[][] RESPONSE = {
			{0, 0, 0, 0}, {2, 0, 0, 0}, {4, 2, 0, 0}, {6, 3, 0, 1}, {6, 3, 4, 1}, {6, 4, 6, 1}, {6, 4, 8, 2}};

	/** Cap on police officers plus cruisers around one player (Leonida Settings: max police units). */
	public static volatile int maxUnits = 12;

	static final class Units {
		final List<UUID> cars = new ArrayList<>();
		final Map<UUID, Long> carsAwaitingCrew = new HashMap<>();
		final Map<UUID, Boolean> carIsSwat = new HashMap<>();
		final List<UUID> helis = new ArrayList<>();
		long nextSpawn;
		long nextHeli;
		long nextSniper;
		int bustProgress;
		long bustedUntil = -1;
		boolean warned;
	}

	private static final Map<UUID, Units> UNITS = new HashMap<>();
	/** Police vehicles we spawned that are no longer chasing anyone; removed once nobody is near. */
	private static final Map<UUID, Long> RETIRED = new HashMap<>();
	private static final Set<UUID> NO_EMPTY = Set.of();

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PoliceDispatch::tick);
	}

	static Units units(ServerPlayer p) {
		return UNITS.computeIfAbsent(p.getUUID(), u -> new Units());
	}

	public static boolean isBusted(ServerPlayer p) {
		Units u = UNITS.get(p.getUUID());
		return u != null && u.bustedUntil >= 0;
	}

	public static List<Entity> helicoptersFor(ServerPlayer p) {
		Units u = UNITS.get(p.getUUID());
		if (u == null || u.helis.isEmpty()) return List.of();
		List<Entity> out = new ArrayList<>();
		for (UUID id : u.helis) {
			Entity e = p.level().getEntity(id);
			if (e != null && e.isAlive()) out.add(e);
		}
		return out;
	}

	public static void onWantedRaised(ServerPlayer p, int from, int to) {
		Units u = units(p);
		u.nextSpawn = Math.min(u.nextSpawn, WantedSystem.now() + (from == 0 ? 40 : 10));
		if (to >= 5) u.nextHeli = Math.min(u.nextHeli, WantedSystem.now() + 60);
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Units u = units(p);
			if (u.bustedUntil >= 0) {
				tickBusted(p, u, now);
				continue;
			}
			if ((now + p.getId()) % 10 != 0) continue;
			int w = PlayerState.get(p).wanted();
			if (w <= 0 || p.isCreative() || p.isSpectator()) continue;
			ServerLevel level = p.level();
			tickArrest(p, u, w);
			if (u.bustedUntil >= 0) continue;
			tickCars(p, u, level, now);
			tickHelis(p, u, level, now, w);
			if (now >= u.nextSpawn) {
				spawnWave(p, u, level, w);
				u.nextSpawn = now + Math.max(60, (WantedSystem.isSearching(p) ? 200 : 120) - 10 * w);
			}
		}
		if (now % 100 == 0) tickRetired(server);
	}

	private static List<PoliceOfficer> officersOf(ServerPlayer p, double range) {
		return p.level().getEntitiesOfClass(PoliceOfficer.class, p.getBoundingBox().inflate(range), o -> o.isAlive() && o.targets(p));
	}

	private static void spawnWave(ServerPlayer p, Units u, ServerLevel level, int w) {
		int[] want = RESPONSE[Math.min(w, 6)];
		List<PoliceOfficer> mine = officersOf(p, 160);
		int patrol = 0, swat = 0;
		for (PoliceOfficer o : mine) if (o.rank() == PoliceOfficer.PATROL) patrol++; else swat++;
		pruneDead(level, u.cars);
		if (mine.size() + u.cars.size() >= maxUnits) return;
		int pendingCrew = 0; // crews ride along already counted as officers
		boolean searching = WantedSystem.isSearching(p);
		Vec3 focus = searching ? WantedSystem.tracker(p).searchCenter : p.position();

		boolean needSwat = swat < want[2];
		boolean needPatrol = patrol + pendingCrew < want[0];
		if (!needSwat && !needPatrol) return;
		boolean swatUnit = needSwat;

		// Prefer arriving by car when the vehicle module provides one.
		if (u.cars.size() < want[1] + (want[2] > 0 ? 1 : 0)) {
			BlockPos road = SpawnFinder.ring(level, focus, 55, 75, 24, pos -> !visible(level, p, pos),
					MapQuery.Surface.ROAD, MapQuery.Surface.HIGHWAY, MapQuery.Surface.LAND);
			if (road != null) {
				Vec3 at = Vec3.atBottomCenterOf(road);
				float yaw = (float) Math.toDegrees(Math.atan2(-(p.getX() - at.x), p.getZ() - at.z));
				Entity car = spawnCrewedCar(level, at, yaw, p, swatUnit, w);
				if (car != null) {
					u.cars.add(car.getUUID());
					u.carsAwaitingCrew.put(car.getUUID(), WantedSystem.now());
					u.carIsSwat.put(car.getUUID(), swatUnit);
					return;
				}
			}
		}
		// On foot: they "pull up" just out of sight.
		BlockPos pos = SpawnFinder.ring(level, focus, searching ? 20 : 30, searching ? 45 : 48, 24, q -> !visible(level, p, q),
				MapQuery.Surface.SIDEWALK, MapQuery.Surface.ROAD, MapQuery.Surface.LAND, MapQuery.Surface.PARK);
		// Nothing hidden around the focus: come in from behind the player instead, never in view.
		if (pos == null) pos = SpawnFinder.ring(level, p.position(), 30, 60, 32, q -> !visible(level, p, q),
				MapQuery.Surface.SIDEWALK, MapQuery.Surface.ROAD, MapQuery.Surface.LAND, MapQuery.Surface.PARK);
		if (pos == null) return; // try again next wave tick
		int n = swatUnit ? (w >= 6 ? 4 : 3) : 2;
		for (int i = 0; i < n; i++) spawnOfficer(level, Vec3.atBottomCenterOf(pos).add(i * 0.8 - 0.8, 0, (i % 2) * 0.8), p,
				swatUnit ? (w >= 6 && i % 2 == 0 ? PoliceOfficer.HEAVY : PoliceOfficer.SWAT) : PoliceOfficer.PATROL);
	}

	/**
	 * A police car with its crew already seated: VCPD livery inside Vice City, Leonard County
	 * Sheriff outside; SWAT arrive in the riot van. The crew bails out near the player.
	 */
	private static Entity spawnCrewedCar(ServerLevel level, Vec3 at, float yaw, ServerPlayer p, boolean swat, int w) {
		int agency = Compat.agencyAt((int) Math.floor(at.x), (int) Math.floor(at.z));
		boolean sheriff = agency == PoliceOfficer.SHERIFF;
		var rnd = level.getRandom();
		dev.leonida.vehicle.VehicleSpec model;
		if (swat) model = dev.leonida.vehicle.VehicleTypes.POLICE_RIOT;
		else if (sheriff) model = rnd.nextInt(3) == 0 ? dev.leonida.vehicle.VehicleTypes.BISON_POLICE
				: rnd.nextBoolean() ? dev.leonida.vehicle.VehicleTypes.POLICE_SUV : dev.leonida.vehicle.VehicleTypes.POLICE_CRUISER;
		else model = w >= 3 && rnd.nextInt(3) == 0 ? dev.leonida.vehicle.VehicleTypes.POLICE_SUV
				: rnd.nextBoolean() ? dev.leonida.vehicle.VehicleTypes.BUFFALO_POLICE : dev.leonida.vehicle.VehicleTypes.POLICE_CRUISER;
		int n = swat ? (w >= 6 ? 4 : 3) : 2;
		List<PoliceOfficer> crew = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			PoliceOfficer o = spawnOfficer(level, at, p, swat ? (w >= 6 && i % 2 == 0 ? PoliceOfficer.HEAVY : PoliceOfficer.SWAT) : PoliceOfficer.PATROL);
			if (o != null) {
				o.setAgency(agency);
				crew.add(o);
			}
		}
		dev.leonida.vehicle.VehicleEntity car = dev.leonida.vehicle.VehicleApi.spawnPolice(level, at, yaw, p, model, sheriff, crew);
		if (car == null) {
			crew.forEach(Entity::discard); // stay off-screen rather than pop in on foot here
			return null;
		}
		dev.leonida.vehicle.VehicleApi.onArrive(car, dev.leonida.vehicle.VehicleApi::exitAll);
		return car;
	}

	private static int countPendingSwat(Units u) {
		int n = 0;
		for (UUID id : u.carsAwaitingCrew.keySet()) if (Boolean.TRUE.equals(u.carIsSwat.get(id))) n++;
		return n;
	}

	public static PoliceOfficer spawnOfficer(ServerLevel level, Vec3 at, ServerPlayer target, int rank) {
		PoliceOfficer o = GameplayRegistry.POLICE.create(level, EntitySpawnReason.EVENT);
		if (o == null) return null;
		o.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360f, 0f);
		o.setRank(rank);
		o.setAgency(Compat.agencyAt((int) Math.floor(at.x), (int) Math.floor(at.z)));
		o.setTargetPlayer(target);
		level.addFreshEntity(o);
		return o;
	}

	/**
	 * Could any nearby player see an officer standing here? Spawns must be off-screen: outside a
	 * generous view cone (wide monitors, third-person camera) or behind cover, for every player
	 * within 96 blocks, not just the target.
	 */
	private static boolean visible(ServerLevel level, ServerPlayer target, BlockPos pos) {
		Vec3 body = Vec3.atBottomCenterOf(pos).add(0, 1.0, 0);
		Vec3 head = body.add(0, 0.8, 0);
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.distanceToSqr(body) > 96 * 96) continue;
			Vec3 eye = p.getEyePosition();
			if (eye.distanceToSqr(body) < 18 * 18) return true; // too close to pop in unnoticed
			Vec3 look = p.getLookAngle();
			if (look.dot(head.subtract(eye).normalize()) < 0.15) continue; // behind or far beside
			if (WantedSystem.clearLine(level, eye, head, p) || WantedSystem.clearLine(level, eye, body, p)) return true;
		}
		return false;
	}

	private static void tickCars(ServerPlayer p, Units u, ServerLevel level, long now) {
		Iterator<Map.Entry<UUID, Long>> it = u.carsAwaitingCrew.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> e = it.next();
			Entity car = level.getEntity(e.getKey());
			if (car == null || !car.isAlive()) {
				it.remove();
				continue;
			}
			long age = now - e.getValue();
			boolean close = car.distanceToSqr(p) < 14 * 14;
			boolean stalled = age > 80 && car.getDeltaMovement().horizontalDistanceSqr() < 0.0025;
			if (close || stalled || age > 400) {
				it.remove();
				if (car instanceof dev.leonida.vehicle.VehicleEntity v) dev.leonida.vehicle.VehicleApi.exitAll(v);
				else car.ejectPassengers();
			}
		}
	}

	private static void tickHelis(ServerPlayer p, Units u, ServerLevel level, long now, int w) {
		pruneDead(level, u.helis);
		int want = RESPONSE[Math.min(w, 6)][3];
		if (u.helis.size() < want && now >= u.nextHeli) {
			u.nextHeli = now + 600;
			double a = level.getRandom().nextDouble() * Math.PI * 2;
			Vec3 at = p.position().add(Math.cos(a) * 90, 45, Math.sin(a) * 90);
			Entity h = Compat.spawnHelicopter(level, at, p);
			if (h != null) u.helis.add(h.getUUID());
		}
		// Marksman in the helicopter.
		if (!u.helis.isEmpty() && now >= u.nextSniper && WantedSystem.lethalForce(p)) {
			u.nextSniper = now + 50 + level.getRandom().nextInt(30);
			for (Entity h : helicoptersFor(p)) {
				Vec3 from = h.position().add(0, -1.2, 0), to = p.position().add(0, 1.2, 0);
				if (from.distanceTo(to) > 90 || !WantedSystem.clearLine(level, from, to, h)) continue;
				level.playSound(null, h.getX(), h.getY(), h.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST_FAR, SoundSource.HOSTILE, 3f, 1.4f);
				Vec3 step = to.subtract(from).normalize();
				for (double t = 2; t < from.distanceTo(to); t += 2) {
					Vec3 q = from.add(step.scale(t));
					level.sendParticles(ParticleTypes.CRIT, q.x, q.y, q.z, 1, 0, 0, 0, 0);
				}
				if (level.getRandom().nextFloat() < (p.getVehicle() != null ? 0.25f : 0.4f)) p.hurtServer(level, level.damageSources().generic(), 4f);
				break;
			}
		}
	}

	private static void pruneDead(ServerLevel level, List<UUID> ids) {
		ids.removeIf(id -> {
			Entity e = level.getEntity(id);
			return e == null || !e.isAlive();
		});
	}

	// ------------------------------------------------------------------ busted

	private static void tickArrest(ServerPlayer p, Units u, int w) {
		boolean onFoot = p.getVehicle() == null;
		boolean weak = p.getHealth() <= 6 && w <= 4;
		boolean cuffable = onFoot && (!WantedSystem.lethalForce(p) || weak);
		PoliceOfficer close = null;
		if (cuffable) for (PoliceOfficer o : officersOf(p, 3)) if (!o.isPassenger() && o.distanceTo(p) < 1.9) close = o;
		if (close == null) {
			u.bustProgress = Math.max(0, u.bustProgress - 10);
			if (u.bustProgress == 0) u.warned = false;
			return;
		}
		double speed = p.getDeltaMovement().horizontalDistance();
		u.bustProgress += (speed < 0.1 || p.isCrouching()) ? 10 : 4;
		if (!u.warned) {
			u.warned = true;
			HudBridge.toast(p, "Police: Get on the ground! Hands where I can see them!");
		}
		if (u.bustProgress >= 40) bust(p, u);
	}

	public static void bust(ServerPlayer p) {
		bust(p, units(p));
	}

	private static void bust(ServerPlayer p, Units u) {
		u.bustedUntil = WantedSystem.now() + 110;
		u.bustProgress = 0;
		HudBridge.busted(p);
		p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 110, 10, false, false));
		p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 110, 10, false, false));
		p.stopRiding();
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 1f);
	}

	private static void tickBusted(ServerPlayer p, Units u, long now) {
		if (now < u.bustedUntil) return;
		u.bustedUntil = -1;
		PlayerState s = PlayerState.get(p);
		int w = Math.max(1, s.wanted());
		long fine = Math.min(s.money(), 250L * w + s.money() / 20);
		PlayerState.set(p, s.withMoney(s.money() - fine));
		WantedSystem.clear(p, false);
		BlockPos station = Compat.nearestBuilding(BuildingGenerator.Kind.POLICE, p.getBlockX(), p.getBlockZ());
		respawnAt(p, station);
		HudBridge.toast(p, "Released from custody. Fine: $" + String.format("%,d", fine) + ".");
	}

	/** Moves the player to a facility position (or their respawn point when unknown). */
	public static void respawnAt(ServerPlayer p, BlockPos where) {
		if (where == null) return;
		ServerLevel level = p.level();
		BlockPos g = SpawnFinder.ground(level, where.getX(), where.getZ());
		BlockPos t = g != null ? g : where;
		p.teleportTo(level, t.getX() + 0.5, t.getY(), t.getZ() + 0.5, Set.<Relative>of(), p.getYRot(), 0f, true);
	}

	// ------------------------------------------------------------------ stand down

	public static void standDown(ServerPlayer p) {
		Units u = units(p);
		for (PoliceOfficer o : officersOf(p, 256)) o.setTargetPlayer(null);
		ServerLevel level = p.level();
		for (UUID id : u.cars) {
			Entity car = level.getEntity(id);
			if (car != null) {
				Compat.setPoliceCarTarget(car, null);
				RETIRED.put(id, WantedSystem.now());
			}
		}
		for (UUID id : u.helis) {
			Entity h = level.getEntity(id);
			if (h != null) Compat.setHelicopterTarget(h, null);
		}
		u.cars.clear();
		u.carsAwaitingCrew.clear();
		u.carIsSwat.clear();
		u.helis.clear();
		u.bustProgress = 0;
		u.warned = false;
	}

	private static void tickRetired(MinecraftServer server) {
		long now = server.getTickCount();
		Iterator<Map.Entry<UUID, Long>> it = RETIRED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> e = it.next();
			Entity car = null;
			for (ServerLevel l : server.getAllLevels()) if ((car = l.getEntity(e.getKey())) != null) break;
			if (car == null || !car.isAlive()) {
				it.remove();
				continue;
			}
			if (car.getFirstPassenger() instanceof ServerPlayer) {
				it.remove(); // the player stole it; it is theirs now
				continue;
			}
			var near = car.level().getNearestPlayer(car, -1);
			if (now - e.getValue() > 400 && (near == null || near.distanceToSqr(car) > 64 * 64)) {
				for (Entity pass : List.copyOf(car.getPassengers())) if (pass instanceof PoliceOfficer) pass.discard();
				car.discard();
				it.remove();
			}
		}
	}
}
