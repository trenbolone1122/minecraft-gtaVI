package dev.leonida.vehicle.ai;

import dev.leonida.landmark.LandmarkSpots;
import dev.leonida.vehicle.VehicleEntity;
import dev.leonida.vehicle.VehicleSpec;
import dev.leonida.vehicle.VehicleTypes;
import dev.leonida.world.WorldModule;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Parked, unowned, unlocked story vehicles at landmark spots ({@link LandmarkSpots}, ids with a "vehicle:" hint),
 * e.g. the turquoise Vapid Ganado and the Dinka Enduro at Jason's house. A spot is filled when a player comes within
 * range and no vehicle tagged for it is loaded anywhere in the level (so a car driven away is not duplicated while
 * it stays loaded).
 */
public final class SpotVehicles {
	private SpotVehicles() {}

	public static final String TAG = "leonida_spot:";
	private static final double RANGE = 96;

	public static void tick(ServerLevel level) {
		if (level.dimension() != WorldModule.LEONIDA || level.getGameTime() % 40 != 7) return;
		for (LandmarkSpots.Spot s : LandmarkSpots.all()) {
			if (!s.what().startsWith("vehicle:")) continue;
			boolean near = false;
			for (ServerPlayer p : level.players()) near |= p.distanceToSqr(s.x(), p.getY(), s.z()) < RANGE * RANGE;
			if (!near || !level.hasChunkAt(net.minecraft.core.BlockPos.containing(s.x(), 64, s.z()))) continue;
			if (present(level, s.id())) continue;
			spawn(level, s);
		}
	}

	private static boolean present(ServerLevel level, String id) {
		for (Entity e : level.getAllEntities()) if (e instanceof VehicleEntity && e.isAlive() && e.entityTags().contains(TAG + id)) return true;
		return false;
	}

	/** Spawn the vehicle for a spot now (also used by tests). */
	public static VehicleEntity spawn(ServerLevel level, LandmarkSpots.Spot s) {
		String[] parts = s.what().substring("vehicle:".length()).split(":");
		VehicleSpec spec = specFor(parts[0]);
		if (spec == null) return null;
		double y = LandmarkSpots.groundAware(s) ? ground(level, s.x(), s.z()) : s.y();
		VehicleEntity v = TrafficManager.create(level, spec, new Vec3(s.x(), y, s.z()), s.yaw());
		if (v == null) return null;
		if (parts.length > 1 && parts[1].equals("turquoise")) v.setColor(VehicleTypes.GANADO_TURQUOISE);
		else v.setColor(spec.paints()[0]);
		v.setLocked(false);
		v.setOwner(null);
		v.addTag(TAG + s.id());
		level.addFreshEntity(v);
		return v;
	}

	/**
	 * Lowest standable spot at (x, z): scanning up from below the surface for a solid top with two free blocks above,
	 * so a spot under a stilt house or a power line still lands on the ground.
	 */
	static double ground(ServerLevel level, double x, double z) {
		int bx = Mth.floor(x), bz = Mth.floor(z);
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
		for (int y = Math.max(level.getMinY() + 1, top - 24); y <= top + 2; y++) {
			net.minecraft.core.BlockPos below = new net.minecraft.core.BlockPos(bx, y - 1, bz), at = below.above(), up = at.above();
			var sb = level.getBlockState(below).getCollisionShape(level, below);
			if (sb.isEmpty() || !level.getFluidState(at).isEmpty()) continue;
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) continue;
			if (!level.getBlockState(up).getCollisionShape(level, up).isEmpty()) continue;
			return y - 1 + sb.max(net.minecraft.core.Direction.Axis.Y);
		}
		return top;
	}

	/** "vapid_ganado" or "ganado" to a spec: matches the id or the display name. */
	static VehicleSpec specFor(String key) {
		key = key.toLowerCase(Locale.ROOT);
		VehicleSpec direct = VehicleTypes.get(key);
		if (direct != null) return direct;
		for (VehicleSpec v : VehicleTypes.SPECS.values()) {
			if (v.displayName().toLowerCase(Locale.ROOT).replace(' ', '_').equals(key)) return v;
			if (key.endsWith("_" + v.id())) return v;
		}
		return null;
	}
}
