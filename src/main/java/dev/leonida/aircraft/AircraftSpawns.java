package dev.leonida.aircraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.leonida.aircraft.entity.AircraftEntity;
import dev.leonida.core.api.MapQuery;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Parked aircraft at real places on the Leonida map: private jets and helicopters on the VCIA apron, Brian's
 * floatplane and Dodos at the seaplane docks, crop dusters at rural airfields, police helicopters at police stations.
 * Points come from data/leonida/aircraft/spawn_points.json (landmark positions from community-world-v1). When a
 * player comes within range, missing aircraft are placed on clear flat ground or open water near the landmark.
 * Only runs once the world module has installed the real MapQuery (on the flat placeholder there is no map).
 */
public final class AircraftSpawns {
	public record Point(String id, String landmark, String kind, List<String> aircraft, int count, int x, int z) {
		boolean water() { return kind.equals("water"); }
	}

	public static final List<Point> POINTS = new ArrayList<>();
	static final int NEAR = 176, NOT_TOO_NEAR = 40, SEARCH = 64, COOLDOWN = 6000;
	private static final Map<String, Long> nextTry = new HashMap<>();

	private AircraftSpawns() {}

	static void init() {
		load();
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level.getGameTime() % 100 != 37 || !active(level)) return;
			for (Point p : POINTS) tick(level, p);
		});
	}

	static void load() {
		POINTS.clear();
		try (InputStream in = AircraftSpawns.class.getResourceAsStream("/data/leonida/aircraft/spawn_points.json")) {
			if (in == null) return;
			JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (JsonElement e : root.getAsJsonArray("points")) {
				JsonObject o = e.getAsJsonObject();
				List<String> types = new ArrayList<>();
				for (JsonElement t : o.getAsJsonArray("aircraft")) {
					if (AircraftModule.SPECS.containsKey(t.getAsString())) types.add(t.getAsString());
				}
				if (types.isEmpty()) continue;
				POINTS.add(new Point(o.get("id").getAsString(), o.get("landmark").getAsString(), o.get("kind").getAsString(),
						List.copyOf(types), o.has("count") ? o.get("count").getAsInt() : 1, o.get("x").getAsInt(), o.get("z").getAsInt()));
			}
		} catch (Exception e) {
			AircraftModule.LOG.warn("aircraft spawn points not loaded", e);
		}
	}

	/** The Leonida map (overworld of the Leonida preset, or the Leonida dimension) with the real MapQuery installed. */
	static boolean active(ServerLevel level) {
		if (MapQuery.get() == MapQuery.FLAT) return false;
		return level.dimension() == Level.OVERWORLD || level.dimension().identifier().getPath().contains("leonida");
	}

	public static @Nullable Point byId(String id) {
		for (Point p : POINTS) if (p.id.equals(id)) return p;
		return null;
	}

	private static void tick(ServerLevel level, Point p) {
		boolean near = false;
		for (ServerPlayer pl : level.players()) {
			double dx = pl.getX() - p.x, dz = pl.getZ() - p.z;
			double d2 = dx * dx + dz * dz;
			if (d2 < NOT_TOO_NEAR * NOT_TOO_NEAR) return; // never pop in right next to someone
			if (d2 < NEAR * NEAR) near = true;
		}
		if (!near) return;
		if (level.getGameTime() < nextTry.getOrDefault(p.id, 0L)) return;
		if (!level.isPositionEntityTicking(new BlockPos(p.x, 64, p.z))) return; // parked ones may not be loaded yet
		int placed = fill(level, p, p.x, p.z);
		nextTry.put(p.id, level.getGameTime() + (placed > 0 ? COOLDOWN : 600));
	}

	/** Place whatever is missing at this point, searching around (cx, cz). Returns how many were placed. */
	public static int fill(ServerLevel level, Point p, int cx, int cz) {
		AABB area = new AABB(cx - SEARCH - 32, level.getMinY(), cz - SEARCH - 32, cx + SEARCH + 32, level.getMaxY(), cz + SEARCH + 32);
		List<AircraftEntity> here = level.getEntitiesOfClass(AircraftEntity.class, area, a -> p.id.equals(a.spawnPoint));
		int placed = 0;
		for (int i = here.size(); i < p.count; i++) {
			String type = p.aircraft.get(i % p.aircraft.size());
			AircraftSpec spec = AircraftModule.SPECS.get(type);
			List<AircraftEntity> others = level.getEntitiesOfClass(AircraftEntity.class, area, a -> true);
			BlockPos spot = findSpot(level, p, spec, cx, cz, others);
			if (spot == null) break;
			AircraftEntity a = AircraftModule.type(spec).create(level, EntitySpawnReason.STRUCTURE);
			if (a == null) break;
			float yaw = (Math.floorMod(p.id.hashCode() + i * 97, 8)) * 45f;
			a.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0);
			if (!level.noCollision(a)) break;
			a.spawnPoint = p.id;
			a.setOwner(null);
			level.addFreshEntity(a);
			placed++;
		}
		return placed;
	}

	private static int clearance(AircraftSpec spec) {
		return spec.isHelicopter() ? 6 : 8;
	}

	/** Spiral search for clear flat ground (or open water for seaplanes), away from other aircraft. */
	private static @Nullable BlockPos findSpot(ServerLevel level, Point p, AircraftSpec spec, int cx, int cz, List<AircraftEntity> others) {
		int r = clearance(spec);
		boolean wantRunway = p.kind.equals("apron") || p.kind.equals("airfield");
		BlockPos fallback = null;
		for (int ring = 0; ring <= SEARCH; ring += 4) {
			int steps = Math.max(1, ring * 2 / 4);
			for (int s = 0; s < steps * 4; s++) {
				double ang = (Math.PI * 2 * s) / (steps * 4);
				int x = cx + Mth.floor(Math.cos(ang) * ring), z = cz + Mth.floor(Math.sin(ang) * ring);
				if (!level.isPositionEntityTicking(new BlockPos(x, 64, z))) continue;
				BlockPos spot = check(level, x, z, r, p.water());
				if (spot == null || crowded(spot, r, others)) continue;
				if (!wantRunway) return spot;
				MapQuery.Surface surf = MapQuery.get().surfaceAt(x, z);
				if (surf == MapQuery.Surface.RUNWAY) return spot;
				if (fallback == null && surf != MapQuery.Surface.ROAD && surf != MapQuery.Surface.HIGHWAY) fallback = spot;
			}
		}
		return fallback;
	}

	private static boolean crowded(BlockPos spot, int r, List<AircraftEntity> others) {
		double min = (r + 7) * (r + 7);
		for (AircraftEntity a : others) {
			double dx = a.getX() - spot.getX(), dz = a.getZ() - spot.getZ();
			if (dx * dx + dz * dz < min) return true;
		}
		return false;
	}

	/** Surface position at (x, z) if a square of radius r around it is flat and of the right kind, else null. */
	static @Nullable BlockPos check(ServerLevel level, int x, int z, int r, boolean water) {
		int y = top(level, x, z, water);
		if (y == Integer.MIN_VALUE) return null;
		for (int dx = -r; dx <= r; dx += r) {
			for (int dz = -r; dz <= r; dz += r) {
				if (!level.hasChunk((x + dx) >> 4, (z + dz) >> 4)) return null;
				int yy = top(level, x + dx, z + dz, water);
				if (yy == Integer.MIN_VALUE || Math.abs(yy - y) > (water ? 0 : 1)) return null;
			}
		}
		for (int i = 1; i <= r; i += 2) { // nothing overhead along the axes
			if (top(level, x + i, z, water) > y + 1 || top(level, x - i, z, water) > y + 1) return null;
			if (top(level, x, z + i, water) > y + 1 || top(level, x, z - i, water) > y + 1) return null;
		}
		return new BlockPos(x, y, z);
	}

	/** Y the aircraft would rest at, or MIN_VALUE if the column is the wrong kind (water vs ground, leaves). */
	private static int top(ServerLevel level, int x, int z, boolean water) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		BlockPos below = new BlockPos(x, y - 1, z);
		BlockState st = level.getBlockState(below);
		boolean isWater = st.getFluidState().is(FluidTags.WATER);
		if (water) {
			if (!isWater || !level.getFluidState(below.below()).is(FluidTags.WATER)) return Integer.MIN_VALUE;
			return y;
		}
		if (isWater || st.getFluidState().is(FluidTags.LAVA) || st.is(BlockTags.LEAVES)) return Integer.MIN_VALUE;
		return y;
	}
}
