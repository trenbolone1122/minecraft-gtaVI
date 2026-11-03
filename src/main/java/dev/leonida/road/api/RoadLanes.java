package dev.leonida.road.api;

import dev.leonida.road.net.RoadNetwork;
import dev.leonida.road.net.RoadNetwork.Arm;
import dev.leonida.road.net.RoadNetwork.Node;
import dev.leonida.road.net.Segment;
import java.util.ArrayList;
import java.util.List;

/**
 * Lane-level view of the Leonida road network for AI traffic (vehicle module). Block coordinates;
 * Y values are the top face of the drivable surface (stand a wheel there). Right-hand traffic:
 * lane 0 is the inner (fast) lane next to the centre line or median, the highest lane is the kerb lane.
 * Thread safe and deterministic.
 *
 * <pre>
 * LanePos p = RoadLanes.nearest(x, y, z, 12);        // snap a car to a lane
 * LanePos q = RoadLanes.advance(p, 5.0, choice);     // 5 blocks further along its lane, crossing junctions
 * RoadLanes.Signal s = RoadLanes.signalAhead(p, 40, level.getGameTime()); // stop line ahead?
 * </pre>
 */
public final class RoadLanes {
	private RoadLanes() {}

	public enum Light { GREEN, YELLOW, RED }

	/**
	 * A position on a lane. {@code forward} = travelling in the segment's +station direction.
	 * x, y, z: lane centre point; heading: travel direction in radians (atan2(dz, dx));
	 * yaw: Minecraft yaw in degrees; speed: suggested cruise speed in blocks per second.
	 */
	public record LanePos(int segment, int lane, boolean forward, double station, double x, double y, double z, double heading,
			float yaw, double speed, boolean highway, int lanes) {}

	/** Upcoming signal: distance (blocks) to the stop line along the lane and its light. */
	public record Signal(int node, double distance, Light light, double stopX, double stopY, double stopZ) {}

	/** Stable id of a lane: segment, direction and lane index packed in one long. */
	public static long laneId(LanePos p) {
		return ((long) p.segment() << 8) | (p.forward() ? 0x80 : 0) | p.lane();
	}

	/** Successor lanes where this lane ends: the first point of every exit arm (no U-turns; dead end = the opposite lane). */
	public static List<LanePos> exits(LanePos p) {
		RoadNetwork net = RoadNetwork.get();
		Segment s = net.segments.get(p.segment());
		Node node = p.forward() ? s.b : s.a;
		List<LanePos> out = new ArrayList<>();
		if (node != null) {
			for (Arm a : node.arms) {
				if (a.seg == s && a.atStart == !p.forward()) continue;
				Segment t = a.seg;
				out.add(make(t, Math.min(p.lane(), t.lanesPerSide - 1), a.atStart, a.atStart ? 0 : t.length));
			}
		}
		if (out.isEmpty()) out.add(make(s, p.lane(), !p.forward(), p.forward() ? s.length : 0));
		return out;
	}

	/** Lane centre points from {@code p} for {@code length} blocks every {@code step}, through junctions by {@code choice}. */
	public static List<LanePos> polyline(LanePos p, double length, double step, int choice) {
		List<LanePos> out = new ArrayList<>();
		out.add(p);
		LanePos c = p;
		for (double d = step; d <= length + 1e-6; d += step) {
			c = advance(c, step, choice);
			out.add(c);
		}
		return out;
	}

	/** Lane centre offset from the road centre line (signed, + = right of +station). */
	static double laneOffset(Segment s, int lane, boolean forward) {
		double off = s.inner + (lane + 0.5) * s.laneWidth;
		return forward ? off : -off;
	}

	static LanePos make(Segment s, int lane, boolean forward, double st) {
		st = Math.max(0, Math.min(s.length, st));
		double[] c = s.at(st);
		double nx = -Math.sin(c[3]), nz = Math.cos(c[3]);
		double off = laneOffset(s, lane, forward);
		double heading = forward ? c[3] : c[3] + Math.PI;
		double y = 62 + 1 + Math.round(c[2] * 2) / 2.0;
		float yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(heading), Math.sin(heading)));
		double speed = s.highway ? 29 : (s.width >= 16 ? 17 : 13);
		return new LanePos(s.id, lane, forward, st, c[0] + nx * off, y, c[1] + nz * off, heading, yaw, speed, s.highway, s.lanesPerSide);
	}

	/** Lanes of every road within {@code radius} of (x, z): one LanePos per lane and direction at the closest station. */
	public static List<LanePos> lanesNear(double x, double z, double radius) {
		List<LanePos> out = new ArrayList<>();
		RoadNetwork net = RoadNetwork.get();
		for (Segment s : net.segmentsNear(x, z, radius)) {
			double st = closestStation(s, x, z);
			for (int dir = 0; dir < 2; dir++)
				for (int l = 0; l < s.lanesPerSide; l++) out.add(make(s, l, dir == 0, st));
		}
		return out;
	}

	/** The lane nearest to a point (within radius), matching travel direction to the side of the road; null if none. */
	public static LanePos nearest(double x, double y, double z, double radius) {
		LanePos best = null;
		double bd = Double.MAX_VALUE;
		for (LanePos p : lanesNear(x, z, radius)) {
			double d = (p.x - x) * (p.x - x) + (p.z - z) * (p.z - z) + 0.25 * (p.y - y) * (p.y - y);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}

	static double closestStation(Segment s, double x, double z) {
		double best = 0, bd = Double.MAX_VALUE;
		double step = 2;
		for (double st = 0; st <= s.length; st += step) {
			double[] c = s.at(st);
			double d = (c[0] - x) * (c[0] - x) + (c[1] - z) * (c[1] - z);
			if (d < bd) {
				bd = d;
				best = st;
			}
		}
		return best;
	}

	/**
	 * Move {@code distance} blocks along the lane. At a junction the exit arm is chosen by
	 * {@code choice} (any int, e.g. a per-car random; same choice = same route), U-turns excluded.
	 * Dead ends turn the car around.
	 */
	public static LanePos advance(LanePos p, double distance, int choice) {
		RoadNetwork net = RoadNetwork.get();
		Segment s = net.segments.get(p.segment());
		double st = p.station() + (p.forward() ? distance : -distance);
		int guard = 0;
		boolean forward = p.forward();
		int lane = p.lane();
		while ((st > s.length || st < 0) && guard++ < 8) {
			double over = st > s.length ? st - s.length : -st;
			Node node = st > s.length ? s.b : s.a;
			Arm from = null;
			if (node != null) for (Arm a : node.arms) if (a.seg == s && a.atStart == !(st > s.length)) from = a;
			List<Arm> exits = new ArrayList<>();
			if (node != null) for (Arm a : node.arms) if (a != from) exits.add(a);
			if (exits.isEmpty()) { // dead end: turn around
				forward = !forward;
				st = st > s.length ? s.length - over : over;
				break;
			}
			Arm next = exits.get(Math.floorMod(choice + node.id * 31, exits.size()));
			s = next.seg;
			forward = next.atStart;
			lane = Math.min(lane, s.lanesPerSide - 1);
			st = forward ? over : s.length - over;
		}
		return make(s, lane, forward, st);
	}

	/** The next stop line on this lane within {@code lookahead} blocks, or null (no signal ahead). */
	public static Signal signalAhead(LanePos p, double lookahead, long gameTime) {
		Segment s = RoadNetwork.get().segments.get(p.segment());
		Node node = p.forward() ? s.b : s.a;
		if (node == null || !node.signalled) return null;
		double mouth = p.forward() ? s.mouthEnd() : s.mouthStart();
		double stopSt = p.forward() ? s.length - mouth - 5.65 : mouth + 5.65;
		double dist = p.forward() ? stopSt - p.station() : p.station() - stopSt;
		if (dist < -2 || dist > lookahead) return null;
		Arm arm = null;
		for (Arm a : node.arms) if (a.seg == s && a.atStart == !p.forward()) arm = a;
		LanePos stop = make(s, p.lane(), p.forward(), stopSt);
		return new Signal(node.id, dist, light(node, arm == null ? 0 : arm.angle, gameTime), stop.x(), stop.y(), stop.z());
	}

	/** Light shown to traffic arriving at {@code node} on the arm leaving in direction {@code armAngle}. */
	public static Light light(Node node, double armAngle, long gameTime) {
		return Signals.light(node, node.onMainAxis(armAngle), gameTime);
	}

	// ---- turns, lane changes ----

	public enum Turn { LEFT, STRAIGHT, RIGHT, UTURN }

	/** A successor lane with the manoeuvre needed to reach it and whether the entry lane may take it. */
	public record Exit(LanePos lane, Turn turn, boolean allowed) {}

	/** Distance before the stop line where lane changes end (solid lane lines are painted from about here). */
	public static final double NO_CHANGE_ZONE = 30;

	static Turn classify(double fromHeading, double toHeading) {
		double d = Math.toDegrees(toHeading - fromHeading);
		d = ((d % 360) + 540) % 360 - 180;
		if (Math.abs(d) > 150) return Turn.UTURN;
		if (d > 30) return Turn.RIGHT; // +z is south: a clockwise heading change seen from above is a right turn
		if (d < -30) return Turn.LEFT;
		return Turn.STRAIGHT;
	}

	/**
	 * Successors of the lane at the junction it runs into, with turn rules: one lane may do anything;
	 * otherwise the inner lane turns left or goes straight, the kerb lane turns right or goes straight,
	 * middle lanes go straight. If a rule would leave a lane without any exit, all exits are allowed.
	 * Left turns land in the exit's inner lane, right turns in its kerb lane, straight keeps the lane index.
	 * Each exit LanePos is at the exit lane's mouth (where the junction box ends).
	 */
	public static List<Exit> turns(LanePos p) {
		RoadNetwork net = RoadNetwork.get();
		Segment s = net.segments.get(p.segment());
		Node node = p.forward() ? s.b : s.a;
		List<Exit> out = new ArrayList<>();
		if (node == null) return out;
		double inHeading = p.forward() ? s.at(s.length)[3] : s.at(0)[3] + Math.PI;
		int lanes = s.lanesPerSide;
		for (Arm a : node.arms) {
			if (a.seg == s && a.atStart == !p.forward()) continue;
			Segment t = a.seg;
			double st = a.atStart ? t.mouthStart() : t.length - t.mouthEnd();
			double outHeading = a.atStart ? t.at(0)[3] : t.at(t.length)[3] + Math.PI;
			Turn turn = classify(inHeading, outHeading);
			int lane = switch (turn) {
				case LEFT, UTURN -> 0;
				case RIGHT -> t.lanesPerSide - 1;
				default -> Math.min(p.lane(), t.lanesPerSide - 1);
			};
			boolean ok = lanes == 1 || switch (turn) {
				case LEFT -> p.lane() == 0;
				case RIGHT -> p.lane() == lanes - 1;
				case STRAIGHT -> true;
				case UTURN -> false;
			};
			out.add(new Exit(make(t, lane, a.atStart, st), turn, ok));
		}
		boolean any = false;
		for (Exit e : out) any |= e.allowed();
		if (!any) {
			List<Exit> all = new ArrayList<>();
			for (Exit e : out) all.add(new Exit(e.lane(), e.turn(), e.turn() != Turn.UTURN || out.size() == 1));
			return all;
		}
		return out;
	}

	/**
	 * Path through the junction from the entry lane's stop line to {@code exit} (normally a lane from
	 * {@link #turns}): straight along the entry lane to its mouth, then a cubic curve tangent to both
	 * lanes to the exit lane's mouth. Points every {@code step} blocks; the first points carry the entry
	 * segment, the curve the exit segment; y is interpolated across the (flat) junction.
	 */
	public static List<LanePos> turnPath(LanePos entry, LanePos exit, double step) {
		Segment s = RoadNetwork.get().segments.get(entry.segment());
		double mouth = entry.forward() ? s.length - s.mouthEnd() : s.mouthStart();
		double stop = entry.forward() ? mouth - dev.leonida.road.gen.RoadGen.STOP_AT : mouth + dev.leonida.road.gen.RoadGen.STOP_AT;
		List<LanePos> out = new ArrayList<>();
		double dir = entry.forward() ? 1 : -1;
		double len = Math.abs(mouth - stop);
		for (double d = 0; d < len; d += step) out.add(make(s, entry.lane(), entry.forward(), stop + dir * d));
		LanePos a = make(s, entry.lane(), entry.forward(), mouth);
		double ax = a.x(), az = a.z(), bx = exit.x(), bz = exit.z();
		double chord = Math.hypot(bx - ax, bz - az);
		Turn turn = classify(a.heading(), exit.heading());
		double k = turn == Turn.STRAIGHT ? chord / 3 : chord * 0.55;
		double c1x = ax + Math.cos(a.heading()) * k, c1z = az + Math.sin(a.heading()) * k;
		double c2x = bx - Math.cos(exit.heading()) * k, c2z = bz - Math.sin(exit.heading()) * k;
		int n = Math.max(2, (int) Math.ceil(chord * (turn == Turn.STRAIGHT ? 1 : 1.25) / step));
		for (int i = 0; i <= n; i++) {
			double t = i / (double) n, u = 1 - t;
			double x = u * u * u * ax + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * bx;
			double z = u * u * u * az + 3 * u * u * t * c1z + 3 * u * t * t * c2z + t * t * t * bz;
			double dx = 3 * u * u * (c1x - ax) + 6 * u * t * (c2x - c1x) + 3 * t * t * (bx - c2x);
			double dz = 3 * u * u * (c1z - az) + 6 * u * t * (c2z - c1z) + 3 * t * t * (bz - c2z);
			double hd = (dx == 0 && dz == 0) ? exit.heading() : Math.atan2(dz, dx);
			double y = a.y() + (exit.y() - a.y()) * t;
			float yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(hd), Math.sin(hd)));
			LanePos ref = t < 0.5 ? a : exit;
			out.add(new LanePos(ref.segment(), ref.lane(), ref.forward(), ref.station(), x, y, z, hd, yaw,
					Math.min(a.speed(), turn == Turn.STRAIGHT ? a.speed() : 8), ref.highway(), ref.lanes()));
		}
		return out;
	}

	/** Same-direction neighbour lane at the same station: delta -1 = left (towards the centre), +1 = right; null if none. */
	public static LanePos neighbour(LanePos p, int delta) {
		Segment s = RoadNetwork.get().segments.get(p.segment());
		int lane = p.lane() + delta;
		if (lane < 0 || lane >= s.lanesPerSide) return null;
		return make(s, lane, p.forward(), p.station());
	}

	/** False inside the no-change zone before a junction stop line (solid lane lines) and inside junction boxes. */
	public static boolean laneChangeAllowed(LanePos p) {
		Segment s = RoadNetwork.get().segments.get(p.segment());
		boolean junctionAhead = p.forward() ? s.junctionAtEnd() : s.junctionAtStart();
		double toMouth = p.forward() ? s.length - s.mouthEnd() - p.station() : p.station() - s.mouthStart();
		double fromMouth = p.forward() ? p.station() - s.mouthStart() : s.length - s.mouthEnd() - p.station();
		if (fromMouth < 3 && (p.forward() ? s.junctionAtStart() : s.junctionAtEnd())) return false;
		return !junctionAhead || toMouth > NO_CHANGE_ZONE + dev.leonida.road.gen.RoadGen.STOP_AT;
	}
}
