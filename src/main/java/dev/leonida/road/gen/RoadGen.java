package dev.leonida.road.gen;

import dev.leonida.core.api.GenTerrain;
import dev.leonida.core.api.RoadGenerator;
import dev.leonida.road.block.RoadBlocks;
import dev.leonida.road.net.RoadNetwork;
import dev.leonida.road.net.RoadNetwork.Hit;
import dev.leonida.road.net.Segment;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Lays the Leonida road network into chunks: asphalt with angled markings, kerbs and sidewalks,
 * flat junction pads, cut and fill with graded verges, elevated decks and bridges on concrete piers,
 * jersey barriers, and street furniture ({@link RoadProps}). Pure function of the baked network and
 * the terrain, written strictly inside the chunk.
 */
public final class RoadGen implements RoadGenerator {
	public static final double DASH_PERIOD = 12, DASH_ON = 3;
	public static final double ZEBRA_FROM = 1.2, ZEBRA_TO = 4.8, STOP_AT = 5.65, STOP_HALF = 0.25;

	public enum Zone {
		VERGE(1), SIDEWALK(2), KERB(3), BARRIER(4), MEDIAN(5), CARRIAGEWAY(6), PLANTER(7);
		final int prio;

		Zone(int p) {
			prio = p;
		}
	}

	/** One resolved road element at a column. */
	static final class Cell {
		Hit hit;
		Zone zone;
		int s2; // surface height in half metres above sea
		double rel;
	}

	@Override
	public boolean claims(int x, int z) {
		for (Hit h : RoadNetwork.get().hitsAt(x, z)) if (zone(h) != Zone.VERGE) return true;
		return false;
	}

	@Override
	public int deckY(int x, int z) {
		int best = Integer.MIN_VALUE;
		for (Hit h : RoadNetwork.get().hitsAt(x, z)) {
			Zone zn = zone(h);
			if (zn == Zone.VERGE) continue;
			int s2 = s2(h.h);
			int y = blockY(SEA, s2);
			if (y > best) best = y;
		}
		return best;
	}

	static int SEA = 62;

	public static int s2(double h) {
		return (int) Math.round(h * 2);
	}

	/** Y of the surface block whose top face sits at sea + 1 + s2 / 2. */
	public static int blockY(int sea, int s2) {
		return (s2 & 1) == 0 ? sea + s2 / 2 : sea + (s2 + 1) / 2;
	}

	public static boolean half(int s2) {
		return (s2 & 1) != 0;
	}

	public static Zone zone(Hit h) {
		Segment s = h.seg;
		double a = Math.abs(h.u), half = s.half();
		if (a < s.median && !h.pad && s.planterAt(h.s)) return Zone.PLANTER;
		if (a <= half) return s.highway && a < 0.5 && !h.pad ? Zone.MEDIAN : Zone.CARRIAGEWAY;
		if (s.highway) return a <= half + RoadNetwork.SHOULDER ? Zone.BARRIER : Zone.VERGE;
		if (a <= half + 1) return Zone.KERB;
		if (a <= half + RoadNetwork.SIDEWALK) return Zone.SIDEWALK;
		return Zone.VERGE;
	}

	@Override
	public void generate(ChunkAccess chunk, GenTerrain terrain) {
		SEA = terrain.seaLevel();
		RoadNetwork net = RoadNetwork.get();
		int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Writer w = new Writer(chunk, pos);
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				int x = x0 + dx, z = z0 + dz;
				List<Hit> hits = net.hitsAt(x, z);
				if (hits.isEmpty()) continue;
				column(w, x, z, hits, terrain);
			}
		}
		RoadProps.place(w, x0, z0, terrain);
	}

	private void column(Writer w, int x, int z, List<Hit> hits, GenTerrain terrain) {
		int sea = terrain.seaLevel();
		List<Cell> cells = new ArrayList<>(hits.size());
		Cell verge = null;
		for (Hit h : hits) {
			Cell c = new Cell();
			c.hit = h;
			c.zone = zone(h);
			c.s2 = s2(h.h);
			c.rel = Math.abs(h.u) / Math.max(1, h.seg.half());
			if (c.zone == Zone.VERGE) {
				if (verge == null || Math.abs(h.u) - h.seg.footprint() < Math.abs(verge.hit.u) - verge.hit.seg.footprint()) verge = c;
			} else cells.add(c);
		}
		int gy = terrain.groundY(x, z), wy = terrain.waterY(x, z);
		if (cells.isEmpty()) {
			if (verge != null) grade(w, x, z, verge, gy, wy, sea);
			return;
		}
		cells.sort((p, q) -> p.s2 != q.s2 ? Integer.compare(p.s2, q.s2) : Integer.compare(p.hit.seg.id, q.hit.seg.id));
		// layers: elements within 5 m vertically merge; higher layers are structures over lower ones
		int start = 0;
		boolean lowest = true;
		while (start < cells.size()) {
			int end = start + 1;
			while (end < cells.size() && cells.get(end).s2 - cells.get(start).s2 < 10) end++;
			Cell best = null;
			for (int i = start; i < end; i++) {
				Cell c = cells.get(i);
				if (best == null || c.zone.prio > best.zone.prio || c.zone.prio == best.zone.prio && c.rel < best.rel - 1e-9) best = c;
			}
			// the whole merged layer sits at the chosen element's height unless a carriageway pad sets it
			build(w, x, z, best, gy, wy, sea, !lowest);
			lowest = false;
			start = end;
		}
	}

	private void build(Writer w, int x, int z, Cell c, int gy, int wy, int sea, boolean upper) {
		Hit h = c.hit;
		Segment seg = h.seg;
		int s2 = c.s2;
		boolean raised = c.zone == Zone.KERB || c.zone == Zone.SIDEWALK || c.zone == Zone.PLANTER;
		int top2 = raised ? s2 + 1 : s2;
		int y = blockY(sea, top2);
		boolean half = half(top2);
		boolean wet = wy != Integer.MIN_VALUE;
		boolean structure = upper || h.bridge || y - gy >= 4 || (wet && h.bridge);
		// surface block
		BlockState surface;
		switch (c.zone) {
			case CARRIAGEWAY -> surface = carriageway(x, z, h, half);
			case KERB -> surface = RoadPalette.kerb(RoadPalette.nearest(-Math.sin(h.angle) * -Math.signum(h.u), Math.cos(h.angle) * -Math.signum(h.u)), half);
			case SIDEWALK -> surface = RoadPalette.sidewalk(x, z, half);
			case PLANTER -> {
				double su = Math.signum(h.u) == 0 ? 1 : Math.signum(h.u);
				if (Math.abs(h.u) > seg.median - 0.75 || seg.junctionDistance(h.s) < Segment.PLANTER_NOSE + 1.5) surface = RoadPalette.kerb(RoadPalette.nearest(-Math.sin(h.angle) * su, Math.cos(h.angle) * su), half);
				else surface = RoadPalette.planter(x, z);
			}
			case BARRIER, MEDIAN -> surface = half ? RoadPalette.concrete() : RoadPalette.asphalt(x, z, true, false);
			default -> surface = RoadPalette.asphalt(x, z, seg.highway, half);
		}
		if (c.zone == Zone.PLANTER && half && Math.abs(h.u) <= seg.median - 0.75 && seg.junctionDistance(h.s) >= Segment.PLANTER_NOSE + 1.5) {
			// soil sits a half block below the kerb tops instead of poking out above them
			w.set(x, y, z, RoadPalette.AIR);
			y -= 1;
		}
		w.set(x, y, z, surface);
		if (c.zone == Zone.BARRIER || c.zone == Zone.MEDIAN) {
			double ax = Math.cos(h.angle), az = Math.sin(h.angle);
			w.set(x, y + 1, z, RoadPalette.barrier(Math.abs(ax) >= Math.abs(az) ? Direction.Axis.X : Direction.Axis.Z));
		}
		// headroom: clear terrain above the road (cuts through hills)
		int clearTop = Math.max(gy, y) + (upper ? 0 : 6);
		int from = c.zone == Zone.BARRIER || c.zone == Zone.MEDIAN ? y + 2 : y + 1;
		if (!upper) for (int yy = from; yy <= clearTop; yy++) w.clear(x, yy, z);
		if (structure) {
			// deck: one concrete layer under the surface (two for highways), then piers down to the ground
			w.set(x, y - 1, z, RoadPalette.concrete());
			if (seg.highway) w.set(x, y - 2, z, RoadPalette.concrete());
			int deckBottom = y - (seg.highway ? 2 : 1);
			Pier p = pier(h);
			if (p != Pier.NONE && deckBottom - 1 > gy) {
				if (p == Pier.CAP || p == Pier.COLUMN) {
					w.set(x, deckBottom - 1, z, RoadPalette.concrete());
					if (seg.highway) w.set(x, deckBottom - 2, z, RoadPalette.concrete());
				}
				if (p == Pier.COLUMN) {
					int bottom = gy;
					for (int yy = deckBottom - 1; yy > bottom; yy--) w.set(x, yy, z, RoadPalette.concrete());
					w.set(x, bottom, z, RoadPalette.concrete());
				}
			}
		} else {
			// embankment or cut: solid fill from the terrain (or seabed) up to the surface
			int bottom = Math.min(gy, y - 1);
			for (int yy = y - 1; yy > bottom; yy--) w.set(x, yy, z, yy == y - 1 ? RoadPalette.SUBBASE : RoadPalette.FILL);
		}
	}

	enum Pier { NONE, CAP, COLUMN }

	/** Pier bents every 24 m (streets) or 32 m (highways): a cap under the whole deck, columns at fixed offsets. */
	static Pier pier(Hit h) {
		Segment seg = h.seg;
		double period = seg.highway ? 32 : 24;
		double k = Math.rint((h.s - period / 2) / period);
		double sc = period / 2 + k * period;
		if (Math.abs(h.s - sc) > 1.0) return Pier.NONE;
		double a = Math.abs(h.u), half = seg.half();
		if (a > half) return Pier.NONE;
		if (seg.width <= 13) return a <= 1.5 ? Pier.COLUMN : Pier.CAP;
		double off = half - 3.5;
		if (Math.abs(a - off) <= 1.2) return Pier.COLUMN;
		if (seg.width > 24 && a <= 1.2) return Pier.COLUMN;
		return Pier.CAP;
	}

	/** Asphalt or a marking decal for a carriageway column. */
	static BlockState carriageway(int x, int z, Hit h, boolean half) {
		Segment seg = h.seg;
		if (h.pad) return RoadPalette.asphalt(x, z, seg.highway, half);
		double u = h.u, th = h.angle;
		// junction approach: stop bar and zebra crossing (streets)
		double eStart = seg.junctionAtStart() ? h.s - seg.mouthStart() : 1e9;
		double eEnd = seg.junctionAtEnd() ? (seg.length - h.s) - seg.mouthEnd() : 1e9;
		boolean nearStart = eStart < eEnd;
		double e = Math.min(eStart, eEnd);
		if (!seg.highway && e < STOP_AT + 1.5) {
			boolean approach = nearStart ? u < 0 : u > 0;
			if (approach && Math.abs(u) <= seg.edge + 0.3) {
				double sBar = nearStart ? seg.mouthStart() + STOP_AT : seg.length - seg.mouthEnd() - STOP_AT;
				BlockState bar = RoadPalette.decal(RoadBlocks.STOP_BAR, th + Math.PI / 2, h.s - sBar, half);
				if (bar != null && Math.abs(h.s - sBar) <= 0.72 + STOP_HALF) return bar;
			}
			if (e >= ZEBRA_FROM - 0.5 && e <= ZEBRA_TO + 0.5) {
				// zebra bars 0.6 m on 0.6 m off across the carriageway, clipped to the crossing band
				if (e >= ZEBRA_FROM && e <= ZEBRA_TO) return RoadPalette.decal(RoadBlocks.ZEBRA, th, -u, half);
			}
		}
		boolean solidZone = e < STOP_AT + 1;
		// kerb parking: bay ends as short white ticks across the strip, every BAY metres, none near junctions
		if (seg.parking && Math.abs(u) > seg.edge + 0.2) {
			BlockState edgeLine = RoadPalette.decal(RoadBlocks.LINE_WHITE, th, Math.signum(u) * seg.edge - u, half);
			if (edgeLine != null) return edgeLine;
			if (e < STOP_AT + Segment.BAY) return RoadPalette.asphalt(x, z, false, half);
			double ph = ((h.s % Segment.BAY) + Segment.BAY) % Segment.BAY;
			double d = ph > Segment.BAY / 2 ? ph - Segment.BAY : ph;
			BlockState tick = RoadPalette.decal(RoadBlocks.LINE_WHITE, th + Math.PI / 2, d, half);
			if (tick != null) return tick;
			return RoadPalette.asphalt(x, z, false, half);
		}
		// lane lines: pick the nearest one
		double bestD = 1e9;
		int bestType = -1;
		double edge = seg.edge, inner = seg.inner, lw = seg.laneWidth;
		for (int side = -1; side <= 1; side += 2) {
			double[] us = new double[seg.lanesPerSide + 1];
			for (int k = 0; k <= seg.lanesPerSide; k++) us[k] = side * (inner + k * lw);
			for (int k = 0; k <= seg.lanesPerSide; k++) {
				double ul = us[k];
				int type;
				if (k == seg.lanesPerSide) type = 0; // edge, solid white
				else if (k == 0) type = seg.highway || seg.median > 0 ? 2 : 3; // yellow inner edge by a median, else double yellow centre
				else type = 1; // dashed white lane line
				if (k == 0 && !seg.highway && seg.median == 0 && side > 0) continue; // centre line once
				double d = ul - u;
				if (Math.abs(d) < Math.abs(bestD)) {
					bestD = d;
					bestType = type;
				}
			}
		}
		if (bestType == 1 && !solidZone) {
			double ph = ((h.s % DASH_PERIOD) + DASH_PERIOD) % DASH_PERIOD;
			if (ph >= DASH_ON) bestType = -1;
		}
		if (bestType == 1 && solidZone) bestType = -1; // lane lines stop short of the crossing
		if (bestType >= 0) {
			var block = switch (bestType) {
				case 0, 1 -> RoadBlocks.LINE_WHITE;
				case 2 -> RoadBlocks.LINE_YELLOW;
				default -> RoadBlocks.LINE_YELLOW_DOUBLE;
			};
			BlockState s = RoadPalette.decal(block, th, bestD, half);
			if (s != null) return s;
		}
		return RoadPalette.asphalt(x, z, seg.highway, half);
	}

	/** Grade terrain beside an at-grade road towards the road surface. */
	private void grade(Writer w, int x, int z, Cell v, int gy, int wy, int sea) {
		if (wy != Integer.MIN_VALUE) return;
		Hit h = v.hit;
		if (h.bridge) return;
		int roadTop = blockY(sea, v.s2 + (h.seg.highway ? 0 : 1)) - (half(v.s2 + (h.seg.highway ? 0 : 1)) ? 1 : 0);
		if (roadTop - gy >= 4 + 6) return; // viaduct over open ground: leave the terrain alone
		double t = (Math.abs(h.u) - h.seg.footprint()) / RoadNetwork.VERGE;
		t = Math.max(0, Math.min(1, t));
		t = t * t * (3 - 2 * t);
		int target = (int) Math.round(roadTop + (gy - roadTop) * t);
		if (Math.abs(roadTop - gy) >= 4 && h.h - h.ground > 3) return; // elevated deck section: no embankment
		if (target == gy) return;
		BlockState topBlock = w.get(x, gy, z), under = w.get(x, gy - 1, z);
		if (topBlock.isAir()) return;
		if (target > gy) {
			for (int yy = gy; yy < target; yy++) w.set(x, yy, z, under.isAir() ? RoadPalette.FILL : under);
			w.set(x, target, z, topBlock);
		} else {
			for (int yy = gy; yy > target; yy--) w.clear(x, yy, z);
			w.set(x, target, z, topBlock);
		}
	}

	/** Chunk-clipped block writer. */
	static final class Writer {
		final ChunkAccess chunk;
		final BlockPos.MutableBlockPos pos;
		final int minX, minZ, minY, maxY;

		Writer(ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
			this.chunk = chunk;
			this.pos = pos;
			this.minX = chunk.getPos().getMinBlockX();
			this.minZ = chunk.getPos().getMinBlockZ();
			this.minY = chunk.getMinY();
			this.maxY = chunk.getMaxY();
		}

		boolean inside(int x, int y, int z) {
			return x >= minX && x < minX + 16 && z >= minZ && z < minZ + 16 && y >= minY && y <= maxY;
		}

		void set(int x, int y, int z, BlockState s) {
			if (s == null || !inside(x, y, z)) return;
			chunk.setBlockState(pos.set(x, y, z), s, 0);
		}

		void setIfAir(int x, int y, int z, BlockState s) {
			if (!inside(x, y, z)) return;
			if (chunk.getBlockState(pos.set(x, y, z)).isAir()) chunk.setBlockState(pos, s, 0);
		}

		void clear(int x, int y, int z) {
			if (!inside(x, y, z)) return;
			pos.set(x, y, z);
			if (!chunk.getBlockState(pos).isAir()) chunk.setBlockState(pos, RoadPalette.AIR, 0);
		}

		BlockState get(int x, int y, int z) {
			if (!inside(x, y, z)) return RoadPalette.AIR;
			return chunk.getBlockState(pos.set(x, y, z));
		}
	}
}
