package dev.leonida.landmark.gen;

import dev.leonida.core.api.GenTerrain;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Local-frame builder for one landmark: origin (ox, oz) in world blocks, y relative to the site ground gy. Local +x is
 * east, local +z is south (same as world). Every write goes through the chunk-clipped {@link Canvas}.
 */
public final class B {
	public final Canvas c;
	public final GenTerrain t;
	public final int ox, oz, gy;
	private final long seed;

	public B(Canvas c, GenTerrain t, int ox, int oz, int gy, long seed) {
		this.c = c;
		this.t = t;
		this.ox = ox;
		this.oz = oz;
		this.gy = gy;
		this.seed = seed;
	}

	public int wx(int lx) {
		return ox + lx;
	}

	public int wz(int lz) {
		return oz + lz;
	}

	/** Terrain top at a local column, relative to gy. */
	public int ground(int lx, int lz) {
		return t.groundY(ox + lx, oz + lz) - gy;
	}

	/** Water surface at a local column relative to gy, or Integer.MIN_VALUE when dry. */
	public int water(int lx, int lz) {
		int w = t.waterY(ox + lx, oz + lz);
		return w == Integer.MIN_VALUE ? w : w - gy;
	}

	public boolean wet(int lx, int lz) {
		return t.waterY(ox + lx, oz + lz) != Integer.MIN_VALUE;
	}

	/** Writes at or below 2 m above the site ground are skipped on road columns, so roads stay intact. */
	public void set(int lx, int ly, int lz, BlockState s) {
		if (ly <= 2 && Site.roadAt(ox + lx, oz + lz)) return;
		c.set(ox + lx, gy + ly, oz + lz, s);
	}

	public void set(int lx, int ly, int lz, Block b) {
		set(lx, ly, lz, b.defaultBlockState());
	}

	public BlockState get(int lx, int ly, int lz) {
		return c.get(ox + lx, gy + ly, oz + lz);
	}

	public boolean inClip(int lx, int lz) {
		int x = ox + lx, z = oz + lz;
		return x >= c.minX && x <= c.maxX && z >= c.minZ && z <= c.maxZ;
	}

	/** True when the column is within {@code r} blocks of this canvas (for features that spread around a point). */
	public boolean inClipNear(int lx, int lz, int r) {
		int x = ox + lx, z = oz + lz;
		return x >= c.minX - r && x <= c.maxX + r && z >= c.minZ - r && z <= c.maxZ + r;
	}

	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				if (!inClip(x, z)) continue;
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) set(x, y, z, s);
			}
	}

	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block b) {
		fill(x0, y0, z0, x1, y1, z1, b.defaultBlockState());
	}

	public void air(int x0, int y0, int z0, int x1, int y1, int z1) {
		fill(x0, y0, z0, x1, y1, z1, Blocks.AIR.defaultBlockState());
	}

	/** Deterministic hash in [0, 1) of a local position and salt. */
	public double rnd(int lx, int ly, int lz, int salt) {
		long h = seed * 0x9E3779B97F4A7C15L + (ox + lx) * 0x632BE59BD9B4E019L + (gy + ly) * 0x85157AF5L + (oz + lz) * 0xC2B2AE3D27D4EB4FL
				+ salt * 0x165667B19E3779F9L;
		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		h *= 0xc4ceb9fe1a85ec53L;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}

	// ------------------------------------------------------------- state helpers (no-ops when the property is absent)

	public static BlockState facing(BlockState s, Direction d) {
		if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && d.getAxis().isHorizontal())
			return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
		if (s.hasProperty(BlockStateProperties.FACING)) return s.setValue(BlockStateProperties.FACING, d);
		return s;
	}

	public static BlockState stairs(BlockState s, Direction up, boolean upsideDown) {
		s = facing(s, up);
		if (s.hasProperty(BlockStateProperties.HALF)) s = s.setValue(BlockStateProperties.HALF, upsideDown ? Half.TOP : Half.BOTTOM);
		return s;
	}

	public static BlockState slab(BlockState s, boolean top) {
		return s.hasProperty(BlockStateProperties.SLAB_TYPE) ? s.setValue(BlockStateProperties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM) : s;
	}

	public static BlockState axis(BlockState s, Direction.Axis a) {
		if (s.hasProperty(BlockStateProperties.AXIS)) return s.setValue(BlockStateProperties.AXIS, a);
		if (s.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) && a.isHorizontal()) return s.setValue(BlockStateProperties.HORIZONTAL_AXIS, a);
		return s;
	}

	/** Thin panel on a wall: an open trapdoor or a look wall panel, hugging the wall behind it, facing {@code out}. */
	public static BlockState panel(BlockState s, Direction out, boolean topHalf) {
		s = facing(s, out);
		if (s.hasProperty(BlockStateProperties.OPEN)) s = s.setValue(BlockStateProperties.OPEN, true);
		if (s.hasProperty(BlockStateProperties.HALF)) s = s.setValue(BlockStateProperties.HALF, topHalf ? Half.TOP : Half.BOTTOM);
		return s;
	}

	/** Closed trapdoor lying flat (table tops, hatches, louvres on the floor). */
	public static BlockState flat(BlockState s, boolean top) {
		if (s.hasProperty(BlockStateProperties.OPEN)) s = s.setValue(BlockStateProperties.OPEN, false);
		if (s.hasProperty(BlockStateProperties.HALF)) s = s.setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
		return s;
	}

	public static BlockState persistent(BlockState s) {
		return s.hasProperty(BlockStateProperties.PERSISTENT) ? s.setValue(BlockStateProperties.PERSISTENT, true) : s;
	}

	public static BlockState hanging(BlockState s, boolean hang) {
		return s.hasProperty(BlockStateProperties.HANGING) ? s.setValue(BlockStateProperties.HANGING, hang) : s;
	}

	/** Two-block door (vanilla door or a look door panel). {@code facing} is the side the door opens away from. */
	public void door(int lx, int ly, int lz, BlockState s, Direction facing, boolean hingeRight) {
		BlockState d = facing(s, facing);
		if (d.hasProperty(BlockStateProperties.DOOR_HINGE)) d = d.setValue(BlockStateProperties.DOOR_HINGE, hingeRight ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
		if (d.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			set(lx, ly, lz, d.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
			set(lx, ly + 1, lz, d.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
		} else {
			set(lx, ly, lz, d);
			set(lx, ly + 1, lz, d);
		}
	}

	/** Bed with its foot at (lx, lz) and head one block towards {@code head}. */
	public void bed(int lx, int ly, int lz, Block bed, Direction head) {
		BlockState s = bed.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, head);
		set(lx, ly, lz, s.setValue(BlockStateProperties.BED_PART, BedPart.FOOT));
		set(lx + head.getStepX(), ly, lz + head.getStepZ(), s.setValue(BlockStateProperties.BED_PART, BedPart.HEAD));
	}

	/** Straight run of blocks between two local points (Bresenham 3D), e.g. power lines. */
	public void line(int x0, int y0, int z0, int x1, int y1, int z1, java.util.function.Function<Direction.Axis, BlockState> state) {
		int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), dz = Math.abs(z1 - z0);
		int n = Math.max(dx, Math.max(dy, dz));
		Direction.Axis a = dx >= dz && dx >= dy ? Direction.Axis.X : dz >= dy ? Direction.Axis.Z : Direction.Axis.Y;
		for (int i = 0; i <= n; i++) {
			double f = n == 0 ? 0 : (double) i / n;
			int x = (int) Math.round(x0 + (x1 - x0) * f);
			int y = (int) Math.round(y0 + (y1 - y0) * f - Math.sin(Math.PI * f) * n * 0.04);
			int z = (int) Math.round(z0 + (z1 - z0) * f);
			set(x, y, z, state.apply(a));
		}
	}
}
