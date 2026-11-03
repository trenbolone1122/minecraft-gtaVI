package dev.leonida.road.gen;

import dev.leonida.look.LookBlocks;
import dev.leonida.road.block.DecalBlock;
import dev.leonida.road.block.RoadBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Every block the road generator places, in one place. */
public final class RoadPalette {
	private RoadPalette() {}

	public static final BlockState AIR = Blocks.AIR.defaultBlockState();
	public static final BlockState FILL = Blocks.STONE.defaultBlockState();
	public static final BlockState SUBBASE = Blocks.GRAVEL.defaultBlockState();

	/** Soil of planted medians: grass with a little coarse dirt and mulch. */
	public static BlockState planter(int x, int z) {
		int n = (int) (hash01(x * 7 + 3, z * 13 + 5) * 16);
		if (n == 0) return Blocks.COARSE_DIRT.defaultBlockState();
		if (n == 1) return Blocks.ROOTED_DIRT.defaultBlockState();
		return Blocks.GRASS_BLOCK.defaultBlockState();
	}

	public static BlockState concrete() {
		return LookBlocks.CONCRETE.defaultBlockState();
	}

	public static BlockState concreteSlab() {
		return LookBlocks.CONCRETE_SLAB.defaultBlockState();
	}

	/** Asphalt surface with clustered wear: mostly fresh, patches of worn, patched and cracked. */
	public static BlockState asphalt(int x, int z, boolean highway, boolean half) {
		if (half) return LookBlocks.ASPHALT_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		double n = noise(x / 7.0, z / 7.0) * 0.7 + noise(x / 2.3 + 91, z / 2.3 + 17) * 0.3;
		LookBlocks.Asphalt a;
		if (highway) a = n > 0.80 ? LookBlocks.Asphalt.WORN : n < 0.12 ? LookBlocks.Asphalt.PATCHED : LookBlocks.Asphalt.FRESH;
		else a = n > 0.74 ? LookBlocks.Asphalt.CRACKED : n > 0.64 ? LookBlocks.Asphalt.WORN : n < 0.14 ? LookBlocks.Asphalt.PATCHED : LookBlocks.Asphalt.FRESH;
		return LookBlocks.asphalt(a).defaultBlockState();
	}

	public static BlockState sidewalk(int x, int z, boolean half) {
		return LookBlocks.sidewalk(x, z, half ? SlabType.BOTTOM : SlabType.DOUBLE);
	}

	public static BlockState kerb(Direction toRoad, boolean half) {
		return LookBlocks.kerb(toRoad, half ? SlabType.BOTTOM : SlabType.DOUBLE);
	}

	public static BlockState barrier(Direction.Axis axis) {
		return LookBlocks.jerseyBarrier(axis);
	}

	/**
	 * A marking drawn at any angle: a line with direction {@code theta} (radians, atan2(dz, dx))
	 * passing at signed distance {@code d} metres from the block centre along the line normal
	 * (-sin theta, cos theta). Returns null when the line does not touch the block.
	 */
	public static BlockState decal(DecalBlock b, double theta, double d, boolean half) {
		boolean pattern = b == RoadBlocks.ZEBRA;
		if (!pattern && d < 0) {
			theta += Math.PI;
			d = -d;
		}
		double deg = Math.toDegrees(theta) % 360;
		if (deg < 0) deg += 360;
		int r = (int) Math.floor(deg / 90);
		double phi = deg - 90 * r;
		int a = (int) Math.round(phi / 5.625);
		if (a >= 16) {
			a = 0;
			r = (r + 1) & 3;
		}
		r &= 3;
		int o;
		if (pattern) {
			double period = b.offsets * b.step;
			double p = ((d % period) + period) % period;
			o = (int) Math.round(p / b.step) % b.offsets;
		} else {
			o = (int) Math.round(d / b.step);
			if (o >= b.offsets) return null;
		}
		return b.defaultBlockState().setValue(DecalBlock.A, a).setValue(DecalBlock.R, r).setValue(b.offset, o).setValue(DecalBlock.HALF, half);
	}

	/** Axis-aligned direction nearest to the vector (dx, dz). */
	public static Direction nearest(double dx, double dz) {
		if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
		return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
	}

	/** Smooth deterministic value noise in [0, 1]. */
	public static double noise(double x, double z) {
		int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
		double fx = x - xi, fz = z - zi;
		fx = fx * fx * (3 - 2 * fx);
		fz = fz * fz * (3 - 2 * fz);
		double a = hash01(xi, zi), b = hash01(xi + 1, zi), c = hash01(xi, zi + 1), e = hash01(xi + 1, zi + 1);
		return (a + (b - a) * fx) + ((c + (e - c) * fx) - (a + (b - a) * fx)) * fz;
	}

	public static double hash01(int x, int z) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 11) * 0x1.0p-53;
	}
}
