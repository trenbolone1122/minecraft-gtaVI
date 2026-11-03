package dev.leonida.vehicle.ai;

import dev.leonida.road.api.RoadLanes;
import dev.leonida.road.api.RoadLanes.LanePos;
import dev.leonida.road.gen.RoadGen;
import dev.leonida.road.net.RoadNetwork;
import dev.leonida.road.net.RoadNetwork.Node;
import dev.leonida.road.net.Segment;
import dev.leonida.vehicle.VehicleControls;
import dev.leonida.vehicle.VehicleEntity;
import dev.leonida.world.WorldModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ambient traffic on the real Leonida road network (road module lane API): keeps to the painted lane centre,
 * turns at junctions by a stable per-car choice, cruises at the lane's suggested speed and stops at red lights.
 */
public class LaneDriver extends PathDriver {
	/** Distance short of the stop line where the front bumper should come to rest. */
	private static final double STOP_MARGIN = 1.0;
	private static final double COMFORT_DECEL = 4.5;

	private LanePos track;
	private final int choice;
	private final double cruiseScale;
	private int rebuild;
	private int selfId;
	/** Ticks left in which this car creeps past waiting traffic with a higher entity id (gridlock breaker). */
	private int priorityTicks;
	private boolean heldByLight;
	/** Junction box state: the node we are about to cross or are crossing, our entry heading and intent. */
	private int boxNode = -1;
	private boolean inBox, entering, straight;
	private double entryHeading;
	/** Ticks spent waiting for the junction box; after a long wait the car goes anyway (avoids box deadlocks). */
	private int boxWait, boxForce;
	/** Last signal decision, for tests and debugging. */
	public RoadLanes.Light lastLight;
	public double lastStopDistance = Double.NaN;
	/** Why the car is holding, for tests: "box:<id>" waiting for the junction, "obs" blocked ahead, or "". */
	public String holdReason = "";

	public LaneDriver(int choice, double cruiseScale) {
		this.choice = choice;
		this.cruiseScale = cruiseScale;
	}

	/** True when traffic in this level should use the lane network. */
	public static boolean available(Level level) {
		return level.dimension() == WorldModule.LEONIDA && RoadNetwork.peek() != null;
	}

	@Override
	public String kind() { return "lane"; }

	public LanePos track() { return track; }

	public void setTrack(LanePos p) {
		track = p;
		rebuild = 0;
	}

	@Override
	public VehicleControls drive(VehicleEntity v) {
		Vec3 pos = v.position();
		selfId = v.getId();
		if (priorityTicks > 0) priorityTicks--;
		// gridlock: blocked by other traffic for 2 s without a red light holding us; the lowest id goes first
		if (blockedTicks > 40 && !heldByLight) {
			priorityTicks = 40;
			blockedTicks = 0;
		}
		if (track == null || (plan == null || onEntry()) && horiz(new Vec3(track.x(), pos.y, track.z()), pos) > 9 && !inBox) {
			track = RoadLanes.nearest(pos.x, pos.y, pos.z, 12);
			rebuild = 0;
			plan = null;
			if (track == null) return VehicleControls.BRAKE;
		}
		if (plan != null) {
			// crossing the planned junction: switch to the exit lane once the car reaches its mouth
			LanePos ex = plan.exit.lane();
			double past = (pos.x - ex.x()) * Math.cos(ex.heading()) + (pos.z - ex.z()) * Math.sin(ex.heading());
			if (past > -1.0 && horiz(new Vec3(ex.x(), pos.y, ex.z()), pos) < 8) {
				track = ex;
				plan = null;
				rebuild = 0;
			}
		}
		// slide the tracking point along the lane to the car's projection (not past the end of a planned entry)
		double hx = Math.cos(track.heading()), hz = Math.sin(track.heading());
		double along = (pos.x - track.x()) * hx + (pos.z - track.z()) * hz;
		if (along > 0.25) {
			if (onEntry()) {
				Segment s0 = RoadNetwork.get().segments.get(track.segment());
				double left = track.forward() ? s0.length - track.station() : track.station();
				along = Math.min(along, Math.max(0, left - 0.05));
			}
			if (along > 0.01) track = RoadLanes.advance(track, along, choice);
		}
		updatePlan(v);
		laneChange(v);
		if (--rebuild <= 0 || pathDone()) {
			rebuild = 5;
			List<Vec3> pts = buildPath(v, 14 + Math.max(0, v.forwardSpeed()) * 1.6);
			if (pts.size() < 2) return VehicleControls.BRAKE;
			path = pts;
			idx = 0;
		}
		if (mergeTicks > 0) mergeTicks--;
		if (laneCooldown > 0) laneCooldown--;

		double cruise = Math.min(track.speed() * cruiseScale, v.spec.maxSpeed() * 0.8);
		double cap = cruise;
		long time = v.level() instanceof ServerLevel sl ? sl.getGameTime() : 0;
		RoadLanes.Signal sig = RoadLanes.signalAhead(track, 60, time);
		lastLight = sig == null ? null : sig.light();
		lastStopDistance = sig == null ? Double.NaN : sig.distance();
		heldByLight = false;
		if (sig != null && sig.light() != RoadLanes.Light.GREEN) {
			heldByLight = true;
			double speed = Math.max(0, v.forwardSpeed());
			double toBar = sig.distance() - v.spec.length() / 2 - STOP_MARGIN; // front bumper to stop line
			boolean canStop = speed * speed / (2 * 7.0) < toBar + 1;   // hard braking still stops before the line
			if (sig.light() == RoadLanes.Light.RED ? toBar > -1 : canStop) {
				cap = toBar <= 0.3 ? 0 : Math.min(cap, Math.sqrt(2 * COMFORT_DECEL * toBar));
			}
		}
		if (plan != null && plan.exit.turn() != RoadLanes.Turn.STRAIGHT) {
			Segment s1 = RoadNetwork.get().segments.get(track.segment());
			double toMouth = onEntry() ? (track.forward() ? s1.length - s1.mouthEnd() - track.station() : track.station() - s1.mouthStart()) : 0;
			cap = Math.min(cap, 8 + Math.max(0, toMouth) * 0.6);
		}
		holdReason = "";
		cap = Math.min(cap, junctionCap(v));
		VehicleControls c = followPath(v, cap);
		if (holdReason.isEmpty() && blockedTicks > 0) holdReason = "obs";
		return c;
	}

	/**
	 * Junction box rule: a car only enters the junction when no crossing or turning traffic is inside it (or
	 * entering ahead of it with a lower id). Following traffic and opposing straight-through traffic may share it.
	 */
	private double junctionCap(VehicleEntity v) {
		RoadNetwork net = RoadNetwork.get();
		Segment seg = net.segments.get(track.segment());
		Node node = track.forward() ? seg.b : seg.a;
		Node behind = track.forward() ? seg.a : seg.b;
		Vec3 pos = v.position();
		// still inside the junction we just came through?
		if (inBox && behind != null && behind.id == boxNode && Math.hypot(pos.x - behind.x, pos.z - behind.z) < boxRadius(behind)) {
			entering = false;
			return Double.MAX_VALUE;
		}
		inBox = false;
		entering = false;
		if (node == null || !node.isJunction()) { boxNode = -1; return Double.MAX_VALUE; }
		double toNode = track.forward() ? seg.length - track.station() : track.station();
		double mouth = track.forward() ? seg.mouthEnd() : seg.mouthStart();
		double toBox = toNode - mouth - v.spec.length() / 2;
		boxNode = node.id;
		entryHeading = track.heading();
		straight = plan == null || plan.exit.turn() == RoadLanes.Turn.STRAIGHT;
		if (boxForce > 0) boxForce--;
		if (toBox < 1.5 || boxForce > 0) { // at or across the box edge, or waited too long: committed
			inBox = true;
			return Double.MAX_VALUE;
		}
		if (toBox > 10) { boxWait = 0; return Double.MAX_VALUE; }
		entering = toBox < 4;
		double r = boxRadius(node);
		for (VehicleEntity o : v.level().getEntitiesOfClass(VehicleEntity.class, new AABB(node.x - r, pos.y - 4, node.z - r, node.x + r, pos.y + 4, node.z + r))) {
			if (o == v || !(o.getDriver() instanceof LaneDriver ld) || ld.boxNode != node.id) continue;
			boolean blocks = ld.inBox || (ld.entering && o.getId() < v.getId() && o.forwardSpeed() > 0.5);
			if (!blocks) continue;
			double dh = Math.abs(Math.IEEEremainder(ld.entryHeading - entryHeading, 2 * Math.PI));
			if (dh < Math.toRadians(30)) continue; // following through
			if (dh > Math.toRadians(150) && straight && ld.straight) continue; // opposing straight traffic
			entering = false;
			holdReason = "box:" + o.getId();
			if (++boxWait > 100) { boxWait = 0; boxForce = 60; }
			return toBox < 2.5 ? 0 : Math.sqrt(2 * COMFORT_DECEL * (toBox - 2.5));
		}
		return Double.MAX_VALUE;
	}

	private static double boxRadius(Node n) {
		double m = 0;
		for (RoadNetwork.Arm a : n.arms) m = Math.max(m, a.mouth);
		return m + 1;
	}

	/** The planned way through the next (or current) junction, from road's turns()/turnPath(). */
	private record Plan(int node, int entrySeg, boolean entryForward, RoadLanes.Exit exit, List<Vec3> pts) {}
	private Plan plan;
	private int mergeTicks, laneCooldown, obsTicks;
	private double mergeDist;
	/** Lane changes made so far, for tests. */
	public int laneChanges;
	/** Turn at the next junction: 1 right, -1 left, 0 straight (for tests). */
	public int nextTurn;

	private boolean onEntry() {
		return plan != null && track.segment() == plan.entrySeg && track.forward() == plan.entryForward;
	}

	/** Pick (or keep) the exit at the junction ahead; re-picks among allowed exits once lane changes are over. */
	private void updatePlan(VehicleEntity v) {
		RoadNetwork net = RoadNetwork.get();
		Segment s = net.segments.get(track.segment());
		Node node = track.forward() ? s.b : s.a;
		if (plan != null && onEntry()) {
			// keep the plan, but once lane changes are over make sure the lane may take the exit
			if (!plan.exit.allowed() && !RoadLanes.laneChangeAllowed(track)) plan = choose(s, node, true);
			return;
		}
		if (plan != null) return; // crossing the box, released in drive()
		if (node == null || !node.isJunction()) { nextTurn = 0; return; }
		double toNode = track.forward() ? s.length - track.station() : track.station();
		if (toNode > 150) { nextTurn = 0; return; }
		plan = choose(s, node, !RoadLanes.laneChangeAllowed(track));
	}

	private Plan choose(Segment s, Node node, boolean allowedOnly) {
		List<RoadLanes.Exit> all = RoadLanes.turns(track), pick = new ArrayList<>();
		for (RoadLanes.Exit e : all) if (e.turn() != RoadLanes.Turn.UTURN && (!allowedOnly || e.allowed())) pick.add(e);
		if (pick.isEmpty()) for (RoadLanes.Exit e : all) if (e.allowed()) pick.add(e);
		if (pick.isEmpty()) return null;
		RoadLanes.Exit e = pick.get(Math.floorMod(choice + node.id * 31, pick.size()));
		nextTurn = e.turn() == RoadLanes.Turn.RIGHT ? 1 : e.turn() == RoadLanes.Turn.LEFT ? -1 : 0;
		List<Vec3> pts = new ArrayList<>();
		for (LanePos p : RoadLanes.turnPath(track, e.lane(), 1.5)) pts.add(new Vec3(p.x(), p.y(), p.z()));
		return new Plan(node.id, s.id, track.forward(), e, pts);
	}

	/** Path ahead: lane centre to the stop line, road's turn curve through the box, then the exit lane. */
	private List<Vec3> buildPath(VehicleEntity v, double ahead) {
		List<Vec3> pts = new ArrayList<>();
		double skip = mergeTicks > 0 ? mergeDist * mergeTicks / 25.0 : 0;
		if (plan == null) {
			LanePos start = skip > 0 ? RoadLanes.advance(track, skip, choice) : track;
			for (LanePos p : RoadLanes.polyline(start, ahead, 2.0, choice)) pts.add(new Vec3(p.x(), p.y(), p.z()));
			return pts;
		}
		Vec3 pos = v.position();
		Vec3 f = v.forward();
		if (onEntry()) {
			Segment s = RoadNetwork.get().segments.get(track.segment());
			double toStop = (track.forward() ? s.length - s.mouthEnd() - track.station() : track.station() - s.mouthStart()) - RoadGen.STOP_AT;
			if (toStop > skip + 1) {
				LanePos start = skip > 0 ? RoadLanes.advance(track, skip, choice) : track;
				for (LanePos p : RoadLanes.polyline(start, toStop - skip - 1, 2.0, choice)) pts.add(new Vec3(p.x(), p.y(), p.z()));
			}
		}
		for (Vec3 q : plan.pts) {
			Vec3 d = q.subtract(pos);
			if (d.x * f.x + d.z * f.z > 1.0) pts.add(q);
		}
		for (LanePos p : RoadLanes.polyline(plan.exit.lane(), Math.max(10, ahead * 0.6), 2.0, choice)) pts.add(new Vec3(p.x(), p.y(), p.z()));
		return pts;
	}

	/**
	 * Lane changes: into a lane that may take the planned exit (kerb lane for right turns, inner lane for left
	 * turns) and around traffic that has been stopped ahead for a while. Only where road allows it and into a gap.
	 */
	private void laneChange(VehicleEntity v) {
		if (holdReason.equals("obs") && !heldByLight) obsTicks++;
		else if (v.forwardSpeed() > 2) obsTicks = 0;
		if (track.lanes() < 2 || laneCooldown > 0 || (plan != null && !onEntry())) return;
		if (!RoadLanes.laneChangeAllowed(track)) return;
		int dir = 0;
		if (plan != null && !plan.exit.allowed()) dir = nextTurn > 0 ? 1 : nextTurn < 0 ? -1 : (track.lane() == 0 ? 1 : -1);
		else if (obsTicks > 60) dir = track.lane() > 0 ? -1 : 1;
		if (dir == 0) return;
		LanePos cand = RoadLanes.neighbour(track, dir);
		if (cand == null || !gapClear(v, cand)) return;
		track = cand;
		laneChanges++;
		laneCooldown = 80;
		obsTicks = 0;
		mergeDist = 5 + Math.max(0, v.forwardSpeed()) * 0.9;
		mergeTicks = 25;
		rebuild = 0;
		blockedTicks = 0;
		if (plan != null) {
			Segment s = RoadNetwork.get().segments.get(track.segment());
			plan = choose(s, track.forward() ? s.b : s.a, false);
			// keep the same arm if the new lane may take it, else the pick above
		}
	}

	private boolean gapClear(VehicleEntity v, LanePos lane) {
		double hx = Math.cos(lane.heading()), hz = Math.sin(lane.heading());
		Vec3 pos = v.position();
		for (VehicleEntity o : v.level().getEntitiesOfClass(VehicleEntity.class, v.getBoundingBox().inflate(16, 3, 16))) {
			if (o == v) continue;
			double dx = o.getX() - lane.x(), dz = o.getZ() - lane.z();
			double lat = -dx * hz + dz * hx;
			double lon = (o.getX() - pos.x) * hx + (o.getZ() - pos.z) * hz;
			double rel = Math.max(0, o.forwardSpeed() - v.forwardSpeed());
			if (Math.abs(lat) < 1.4 + o.spec.width() / 2 && lon > -8 - rel * 1.5 && lon < 10) return false;
		}
		return true;
	}

	@Override
	protected boolean ignore(net.minecraft.world.entity.Entity e) {
		return priorityTicks > 0 && e instanceof VehicleEntity o && o.getId() > selfId && o.getDriver() instanceof LaneDriver;
	}

	@Override
	protected void onStuck(VehicleEntity v) {
		track = null;
		plan = null;
	}
}
