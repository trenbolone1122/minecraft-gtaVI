package dev.leonida.landmark.gen;

import dev.leonida.core.api.GenTerrain;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One hand-authored landmark. {@link #build} must be deterministic and only depend on the terrain, because it runs
 * once per chunk with a clipped canvas. Bounds are world block coordinates (inclusive).
 */
public abstract class Site {
	public final String id;
	public final int minX, minZ, maxX, maxZ;

	protected Site(String id, int minX, int minZ, int maxX, int maxZ) {
		this.id = id;
		this.minX = minX;
		this.minZ = minZ;
		this.maxX = maxX;
		this.maxZ = maxZ;
	}

	/**
	 * Columns where generic buildings, infill and trees must be skipped. Default: the whole bounds except road columns
	 * (roads are generated regardless of claims, so a site never claims them and {@link B} never writes near their
	 * surface).
	 */
	public boolean claims(int x, int z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ && !roadAt(x, z);
	}

	public static boolean roadAt(int x, int z) {
		dev.leonida.core.api.RoadGenerator r = dev.leonida.core.api.RoadGenerator.get();
		return r != null && r.claims(x, z);
	}

	/** Site ground level for this terrain (the y every local coordinate is relative to). */
	public abstract int groundY(GenTerrain t);

	public abstract void build(Canvas c, GenTerrain t);

	/** Named camera presets for tests and screenshots (local to origin and ground). */
	public List<View> views() {
		return List.of();
	}

	/**
	 * Structures the server-side check measures in the generated world: the bounding box of the columns holding the
	 * {@code wall} block (searched within {@code slack} of the expected box) must match the expected footprint, and the
	 * highest {@code wall} or {@code roof} block top must match the expected height above the ground surface (top of the block at the site ground y), each within 1 m.
	 * Expected sizes come from the gtadb footprint and the in-game photo.
	 */
	public record Footprint(String name, int x0, int z0, int x1, int z1, int height, BlockState wall, BlockState roof, int slack) {}

	public List<Footprint> footprints() {
		return List.of();
	}

	/** Local origin used by views. */
	public abstract int originX();

	public abstract int originZ();

	/** Camera preset: local eye position, yaw/pitch (Minecraft degrees), vertical fov. */
	public record View(String name, double x, double y, double z, float yaw, float pitch, int fov) {
		/** Camera at (x, y, z) looking at the local point (tx, ty, tz). */
		public static View look(String name, double x, double y, double z, double tx, double ty, double tz, int fov) {
			double dx = tx - x, dy = ty - y, dz = tz - z;
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			return new View(name, x, y, z, yaw, pitch, fov);
		}
	}
}
