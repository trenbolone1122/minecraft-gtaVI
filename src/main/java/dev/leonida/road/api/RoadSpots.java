package dev.leonida.road.api;

import dev.leonida.road.gen.RoadGen;
import dev.leonida.road.net.Props;
import dev.leonida.road.net.RoadNetwork;
import dev.leonida.road.net.RoadNetwork.Hit;
import dev.leonida.road.net.Segment;
import java.util.ArrayList;
import java.util.List;

/**
 * Planting spots that belong to the road layout, for the world module's decoration step: royal palms
 * down the planted medians of wide boulevards and along the sidewalks of downtown streets. Static and
 * deterministic; every spot is 12 to 16 m from the next on the same line, clear of junctions and at
 * least 3 m from lamps, signals and power poles. Also the kerb parking bays of kind 2 streets.
 */
public final class RoadSpots {
	private RoadSpots() {}

	public enum Kind { MEDIAN, SIDEWALK }

	/** One palm: block x, z, the Y of the soil or sidewalk block (plant the trunk at y + 1), and where it stands. */
	public record Palm(int x, int z, int y, Kind kind) {}

	private static final double MEDIAN_SPACING = 14, SIDEWALK_SPACING = 16, POLE_CLEAR = 3.0;

	/** Palm spots inside chunk (chunkX, chunkZ). */
	public static List<Palm> palms(int chunkX, int chunkZ) {
		List<Palm> out = new ArrayList<>();
		RoadNetwork net = RoadNetwork.get();
		Props props = Props.get();
		int x0 = chunkX << 4, z0 = chunkZ << 4;
		List<Props.Pole> poles = props.in(x0 - 4, z0 - 4, x0 + 20, z0 + 20);
		for (Segment s : net.segmentsNear(x0 + 8, z0 + 8, 8 + 40)) {
			if (s.highway) continue;
			if (s.median > 0) line(out, net, poles, s, 0, MEDIAN_SPACING, 3, Kind.MEDIAN, x0, z0);
			if (s.parking) {
				// Ocean Drive: palms on the beach (+u, right of the south to north direction) sidewalk only
				line(out, net, poles, s, s.half() + RoadNetwork.SIDEWALK - 0.8, 12, 11, Kind.SIDEWALK, x0, z0);
			} else if (s.width >= 16) {
				double off = s.half() + RoadNetwork.SIDEWALK - 0.8;
				line(out, net, poles, s, off, SIDEWALK_SPACING, 11, Kind.SIDEWALK, x0, z0);
				line(out, net, poles, s, -off, SIDEWALK_SPACING, 5, Kind.SIDEWALK, x0, z0);
			}
		}
		return out;
	}

	/**
	 * One kerb parking bay: centre (block coords, x z), the Y a car stands on (top of the asphalt block, so
	 * the wheels sit at y), heading and Minecraft yaw facing the traffic direction of that side, the segment
	 * and which side (true = right of +station, the forward lanes).
	 */
	public record Bay(double x, double z, double y, double heading, float yaw, int segment, boolean forward) {}

	/** Kerb parking bays (6 m long, 2.4 m deep) whose centre lies inside chunk (chunkX, chunkZ). */
	public static List<Bay> parkingBays(int chunkX, int chunkZ) {
		List<Bay> out = new ArrayList<>();
		int x0 = chunkX << 4, z0 = chunkZ << 4;
		for (Segment s : RoadNetwork.get().segmentsNear(x0 + 8, z0 + 8, 8 + 20)) {
			if (!s.parking) continue;
			double off = s.edge + Segment.PARKING / 2;
			for (double st = Segment.BAY / 2; st <= s.length - Segment.BAY / 2; st += Segment.BAY) {
				if (s.junctionDistance(st) < RoadGen.STOP_AT + Segment.BAY + Segment.BAY / 2) continue;
				double[] c = s.at(st);
				if (c[5] != 0) continue;
				double nx = -Math.sin(c[3]), nz = Math.cos(c[3]);
				for (int side = -1; side <= 1; side += 2) {
					double bx = c[0] + nx * off * side, bz = c[1] + nz * off * side;
					if (bx < x0 || bx >= x0 + 16 || bz < z0 || bz >= z0 + 16) continue;
					double heading = side > 0 ? c[3] : c[3] + Math.PI;
					float yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(heading), Math.sin(heading)));
					double y = 62 + 1 + Math.round(c[2] * 2) / 2.0;
					out.add(new Bay(bx, bz, y, heading, yaw, s.id, side > 0));
				}
			}
		}
		return out;
	}

	private static void line(List<Palm> out, RoadNetwork net, List<Props.Pole> poles, Segment s, double u, double spacing, int salt,
			Kind kind, int x0, int z0) {
		double phase = (((s.id * 2654435761L + salt * 40503L) & 0xffff) / 65536.0) * spacing;
		for (double st = phase; st <= s.length; st += spacing) {
			if (s.junctionDistance(st) < (kind == Kind.MEDIAN ? Segment.PLANTER_NOSE + 3 : 9)) continue;
			if (kind == Kind.MEDIAN && !s.planterAt(st)) continue;
			double[] c = s.at(st);
			if (c[5] != 0) continue; // no palms on bridges
			double nx = -Math.sin(c[3]), nz = Math.cos(c[3]);
			int bx = (int) Math.floor(c[0] + nx * u), bz = (int) Math.floor(c[1] + nz * u);
			if (bx < x0 || bx >= x0 + 16 || bz < z0 || bz >= z0 + 16) continue;
			boolean clear = true;
			for (Props.Pole p : poles) {
				double dx = p.x - (bx + 0.5), dz = p.z - (bz + 0.5);
				if (dx * dx + dz * dz < POLE_CLEAR * POLE_CLEAR) clear = false;
				// keep the crown clear of signal and lamp arms too
				double ax = p.armX() - (bx + 0.5), az = p.armZ() - (bz + 0.5);
				if (p.kind != Props.Kind.POWER && ax * ax + az * az < POLE_CLEAR * POLE_CLEAR) clear = false;
			}
			if (!clear) continue;
			// the column must resolve to this segment's planter or sidewalk (not a crossing road)
			Hit best = null;
			RoadGen.Zone bz2 = null;
			for (Hit h : net.hitsAt(bx, bz)) {
				RoadGen.Zone z = RoadGen.zone(h);
				if (z == RoadGen.Zone.VERGE) continue;
				if (best == null || z.ordinal() > bz2.ordinal()) {
					best = h;
					bz2 = z;
				}
			}
			if (best == null || best.seg != s) continue;
			if (kind == Kind.MEDIAN ? bz2 != RoadGen.Zone.PLANTER : bz2 != RoadGen.Zone.SIDEWALK) continue;
			if (Math.abs(best.h - c[2]) > 1.5 || best.h - best.ground > 3) continue;
			int s2 = RoadGen.s2(best.h) + 1;
			int y = RoadGen.blockY(62, s2);
			if (kind == Kind.MEDIAN && RoadGen.half(s2)) y -= 1; // soil is sunk half a block below the kerb tops
			out.add(new Palm(bx, bz, y, kind));
		}
	}
}
