package dev.leonida.vehicle.render;

import static dev.leonida.vehicle.render.VehicleModel.G_PROP;
import static dev.leonida.vehicle.render.VehicleModel.G_STEER;

import dev.leonida.vehicle.VehicleSpec;
import dev.leonida.vehicle.VehicleTypes;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * Procedural, original box models for every vehicle. All numbers are pixels (16 px = 1 block).
 * Cars share a parametric body generator (wheel arches, stepped greenhouse, slanted glass, pillars, lights);
 * bikes and boats have their own builders.
 */
public final class VehicleModels {
	private VehicleModels() {}

	private static final Map<String, VehicleModel> CACHE = new HashMap<>();

	public static VehicleModel get(VehicleSpec spec) {
		return CACHE.computeIfAbsent(spec.id(), id -> build(spec));
	}

	private static VehicleModel build(VehicleSpec spec) {
		VehicleModel m = new VehicleModel();
		switch (spec.id()) {
			case "dominator" -> FleetCars.dominator(m, spec);
			case "gauntlet_hellfire" -> FleetCars.gauntlet(m, spec);
			case "tulip" -> FleetCars.tulip(m, spec);
			case "ganado" -> FleetCars.ganado(m, spec);
			case "buffalo" -> FleetCars.buffalo(m, spec, false);
			case "voodoo" -> FleetCars.voodoo(m, spec);
			case "coquette_d10" -> FleetCars.coquette(m, spec);
			case "cheetah" -> FleetCars.cheetah(m, spec);
			case "tempesta" -> FleetCars.tempesta(m, spec);
			case "infernus_classic" -> FleetCars.infernus(m, spec);
			case "adder" -> FleetCars.adder(m, spec);
			case "sentinel_classic_cabrio" -> FleetCars.sentinelCabrio(m, spec);
			case "elegy" -> FleetCars.elegy(m, spec);
			case "stanier" -> FleetCars.stanier(m, spec, false);
			case "caracara" -> FleetCars.caracara(m, spec);
			case "bison" -> FleetCars.bison(m, spec, false);
			case "granger" -> FleetCars.granger(m, spec, false);
			case "granger_taxi" -> FleetCars.granger(m, spec, true);
			case "dubsta" -> FleetCars.dubsta(m, spec);
			case "speedo" -> FleetCars.speedo(m, spec);
			case "stockade" -> FleetCars.armoured(m, spec, false);
			case "police_cruiser", "police_suv", "buffalo_police", "bison_police", "police_riot" -> FleetCars.police(m, spec);
			case "sanchez" -> dirtBike(m);
			case "enduro" -> enduro(m);
			case "zombie" -> zombie(m);
			case "alvino_v1" -> motorcycle(m);
			case "lido" -> scooter(m);
			case "airboat_09" -> airboat(m);
			case "seashark" -> jetSki(m);
			case "clarion" -> speedboat(m);
			case "squalo" -> squalo(m);
			case "lure_predator" -> lurePredator(m);
			case "kayak" -> kayak(m);
			default -> FleetCars.stanier(m, spec, false);
		}
		if (spec.isBike()) m.rimTile = 31;
		m.attachDoorTrim();
		return m;
	}

	/**
	 * A thin slanted slab from (zLow, yLow) to (zHigh, yHigh), spanning x0..x1, of the given thickness.
	 * Used for windshields, rear glass and pillars.
	 */
	static VehicleModel.Box slab(VehicleModel m, Mat mat, float x0, float x1, float zLow, float yLow, float zHigh, float yHigh, float thick) {
		float dz = zHigh - zLow, dy = yHigh - yLow;
		float len = (float) Math.sqrt(dz * dz + dy * dy);
		float cz = (zLow + zHigh) / 2, cy = (yLow + yHigh) / 2;
		// box z axis rotated onto the slope direction: z' = (-sin, cos) in (y, z); we want direction (dy, dz) * sign(dz)
		float ang = (float) Math.toDegrees(Math.atan2(-dy, dz));
		if (dz < 0) ang = (float) Math.toDegrees(Math.atan2(dy, -dz));
		return m.box(mat, x0, cy - thick / 2, cz - len / 2, x1, cy + thick / 2, cz + len / 2).pitch(ang);
	}

	private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

	// ----------------------------------------------------------------- bikes

	private static void dirtBike(VehicleModel m) {
		m.bikeRider = true;
		float r = 6.5f;
		float zf = 12.5f, zr = -11;
		m.wheels.add(new VehicleModel.Wheel(0, r, zf, r, 2.6f, true, true));
		m.wheels.add(new VehicleModel.Wheel(0, r, zr, r, 3, false, false));
		m.steerPivotZ = zf - 2;
		m.steerRake = 22;
		// fork, bars, front fender, number plate
		m.box(Mat.CHROME, -2.2f, r, zf - 0.6f, -1.4f, 20, zf + 0.6f).group(G_STEER).pitch(-22);
		m.box(Mat.CHROME, 1.4f, r, zf - 0.6f, 2.2f, 20, zf + 0.6f).group(G_STEER).pitch(-22);
		m.box(Mat.BLACK, -7, 20.5f, zf - 5.6f, 7, 21.3f, zf - 4.8f).group(G_STEER);
		m.box(Mat.PAINT, -1.8f, r * 2 + 1.2f, zf - 3, 1.8f, r * 2 + 2, zf + 4).group(G_STEER);
		m.box(Mat.WHITE, -3, 15, zf - 3.8f, 3, 19.5f, zf - 3.2f).group(G_STEER).pitch(-22);
		// frame, tank, seat, engine, swingarm, rear fender, exhaust
		m.box(Mat.PAINT, -2.6f, 13, -1, 2.6f, 17.5f, 7.5f);
		m.box(Mat.SEAT, -2.2f, 15.5f, -10, 2.2f, 17, 2);
		m.box(Mat.PAINT, -2.4f, 13, -14.5f, 2.4f, 15.6f, -6);
		m.box(Mat.METAL, -2.5f, 6, -3, 2.5f, 12.5f, 4.5f);
		m.box(Mat.BLACK, -1.8f, 7.5f, 4.5f, 1.8f, 13, 6.5f);
		m.box(Mat.BLACK, -2.2f, r - 0.8f, zr, 2.2f, r + 0.8f, -2);
		m.box(Mat.CHROME, 2.5f, 10, -10, 3.6f, 11.4f, 0);
		m.box(Mat.PAINT_DARK, -2.7f, 9, -6, 2.7f, 13, -2.5f);
	}

	/** Dual-sport trail bike (XR500 style): dirt bike with a headlight cowl, mirrors, longer seat and a rear rack. */
	private static void enduro(VehicleModel m) {
		dirtBike(m);
		float zf = 12.5f;
		m.box(Mat.WHITE, -2.6f, 16.5f, zf - 3.9f, 2.6f, 20, zf - 2.6f).group(G_STEER).pitch(-22);
		m.box(Mat.HEADLIGHT, -1.5f, 17.2f, zf - 2.7f, 1.5f, 19.2f, zf - 2.3f).group(G_STEER).pitch(-22);
		m.box(Mat.BLACK, -6.6f, 21.3f, zf - 5.4f, -6.0f, 24, zf - 4.9f).group(G_STEER);
		m.box(Mat.BLACK, 6.0f, 21.3f, zf - 5.4f, 6.6f, 24, zf - 4.9f).group(G_STEER);
		m.box(Mat.CHROME, -7.6f, 23.6f, zf - 5.6f, -5.6f, 24.8f, zf - 4.8f).group(G_STEER);
		m.box(Mat.CHROME, 5.6f, 23.6f, zf - 5.6f, 7.6f, 24.8f, zf - 4.8f).group(G_STEER);
		m.box(Mat.BLACK, -2.0f, 15.8f, -14, 2.0f, 16.4f, -9.5f);
		m.box(Mat.TAILLIGHT, -1.1f, 14.2f, -14.9f, 1.1f, 15.4f, -14.5f);
		m.box(Mat.PLATE, -1.8f, 11.4f, -15.4f, 1.8f, 13.8f, -15.0f);
		m.box(Mat.WHITE, -2.8f, 13.4f, 2, 2.8f, 16.8f, 6);
	}

	private static void motorcycle(VehicleModel m) {
		m.bikeRider = true;
		float r = 5.8f;
		float zf = 13.5f, zr = -11.5f;
		m.wheels.add(new VehicleModel.Wheel(0, r, zf, r, 3, true, true));
		m.wheels.add(new VehicleModel.Wheel(0, r, zr, r, 4, false, false));
		m.steerPivotZ = zf - 2.5f;
		m.steerRake = 25;
		m.box(Mat.METAL, -2.4f, r, zf - 0.7f, -1.5f, 17, zf + 0.7f).group(G_STEER).pitch(-25);
		m.box(Mat.METAL, 1.5f, r, zf - 0.7f, 2.4f, 17, zf + 0.7f).group(G_STEER).pitch(-25);
		m.box(Mat.BLACK, -6, 17.8f, zf - 6.5f, 6, 18.6f, zf - 5.7f).group(G_STEER);
		m.box(Mat.PAINT, -1.9f, r * 2 + 0.5f, zf - 2.5f, 1.9f, r * 2 + 1.3f, zf + 3).group(G_STEER);
		// fairing with headlight and screen
		m.box(Mat.PAINT, -3.6f, 11, zf - 6, 3.6f, 18, zf - 1.5f);
		m.box(Mat.HEADLIGHT, -1.8f, 13.5f, zf - 1.6f, 1.8f, 15.5f, zf - 1.2f);
		m.box(Mat.GLASS, -2.8f, 18, zf - 5.5f, 2.8f, 21, zf - 4.6f).pitch(-30);
		// tank, seat, tail, engine, exhaust
		m.box(Mat.PAINT, -3, 12, -1, 3, 16.5f, 7.5f);
		m.box(Mat.SEAT, -2.6f, 13.5f, -10, 2.6f, 15.2f, -0.5f);
		m.box(Mat.PAINT, -2.4f, 13.8f, -15, 2.4f, 16.2f, -9);
		m.box(Mat.TAILLIGHT, -1.4f, 14, -15.3f, 1.4f, 15.4f, -14.9f);
		m.box(Mat.METAL, -3.2f, 5, -4, 3.2f, 12, 6);
		m.box(Mat.PAINT_DARK, -3.4f, 5, 4, 3.4f, 11, 8);
		m.box(Mat.BLACK, -2.4f, r - 0.8f, zr, 2.4f, r + 0.8f, -3);
		m.box(Mat.CHROME, 3, 6, -14, 4.4f, 7.6f, -2);
	}

	private static void scooter(VehicleModel m) {
		m.bikeRider = true;
		float r = 3.8f;
		float zf = 10, zr = -9;
		m.wheels.add(new VehicleModel.Wheel(0, r, zf, r, 2.6f, true, true));
		m.wheels.add(new VehicleModel.Wheel(0, r, zr, r, 2.8f, false, false));
		m.steerPivotZ = zf - 1;
		m.steerRake = 18;
		m.box(Mat.PAINT, -1.4f, r, zf - 0.8f, 1.4f, 18, zf + 0.6f).group(G_STEER).pitch(-18);
		m.box(Mat.CHROME, -6, 18, zf - 5, 6, 18.8f, zf - 4.2f).group(G_STEER);
		m.box(Mat.PAINT, -2.5f, 17, zf - 6, 2.5f, 20, zf - 3).group(G_STEER);
		m.box(Mat.HEADLIGHT, -1.2f, 17.6f, zf - 3.1f, 1.2f, 19.4f, zf - 2.8f).group(G_STEER);
		m.box(Mat.PAINT, -1.8f, 2 * r + 0.4f, zf - 2.6f, 1.8f, 2 * r + 1.6f, zf + 3).group(G_STEER);
		// leg shield, floorboard, rear body, seat, rack
		m.box(Mat.PAINT, -4, 4, zf - 4.5f, 4, 15, zf - 2.5f).pitch(-12);
		m.box(Mat.PAINT, -3, 3, -3, 3, 4.5f, zf - 3);
		m.box(Mat.PAINT, -3.6f, 4, -13.5f, 3.6f, 11, -2);
		m.box(Mat.SEAT, -3, 11, -12, 3, 12.6f, -2.5f);
		m.box(Mat.CHROME, -2.5f, 12, -15, 2.5f, 12.6f, -11.5f);
		m.box(Mat.TAILLIGHT, -1.2f, 8.5f, -13.8f, 1.2f, 9.8f, -13.4f);
	}

	// ----------------------------------------------------------------- boats

	/** Tapered hull: a series of boxes narrowing to the bow, with a white deck line. */
	private static void hull(VehicleModel m, float len, float wid, float depth, float deckY, Mat side, Mat bottom) {
		float hl = len / 2;
		int n = 8;
		float bowStart = hl * 0.25f;
		float seg = (hl - bowStart) / n;
		m.box(side, -wid / 2, deckY - depth + 2, -hl, wid / 2, deckY, bowStart);
		m.box(bottom, -wid / 2 + 2, deckY - depth, -hl, wid / 2 - 2, deckY - depth + 2, bowStart);
		for (int i = 0; i < n; i++) {
			float t = (i + 1) / (float) n;
			float w = wid * (1 - t * t * 0.85f);
			float rise = depth * 0.45f * t * t;
			float z0 = bowStart + seg * i, z1 = z0 + seg;
			m.box(side, -w / 2, deckY - depth + 2 + rise, z0, w / 2, deckY + t * 1.5f, z1);
			if (w > 4.5f) m.box(bottom, -w / 2 + 2, deckY - depth + rise, z0, w / 2 - 2, deckY - depth + 2 + rise, z1);
		}
	}

	/** Clarion: long narrow offshore powerboat (Cigarette style) with a low screen and twin stern drives. */
	private static void speedboat(VehicleModel m) {
		float len = 110, wid = 34, deck = 12;
		hull(m, len, wid, 12, deck, Mat.PAINT, Mat.WHITE);
		float hl = len / 2;
		m.pair(Mat.WHITE, wid / 2 - 0.05f, deck - 6, -hl + 1, wid / 2 + 0.06f, deck - 4.5f, hl * 0.25f);
		m.pair(Mat.BLACK, wid / 2 - 0.04f, deck - 4.5f, -hl + 1, wid / 2 + 0.07f, deck - 3.8f, hl * 0.25f);
		// long foredeck with hatch and cleats, cockpit, low wraparound screen
		m.box(Mat.FIBERGLASS, -wid / 2 + 1, deck, 10, wid / 2 - 1, deck + 1.6f, hl * 0.25f + 2);
		m.box(Mat.GLASS_DARK, -5, deck + 1.6f, 22, 5, deck + 1.9f, 32);
		m.pair(Mat.CHROME, 4, deck + 1.6f, 40, 5.5f, deck + 2.2f, 42);
		m.box(Mat.DECK, -wid / 2 + 2, deck - 1.2f, -hl + 10, wid / 2 - 2, deck + 0.05f, 10);
		m.box(Mat.FIBERGLASS, -wid / 2, deck, -hl + 10, -wid / 2 + 2, deck + 2.4f, 10);
		m.box(Mat.FIBERGLASS, wid / 2 - 2, deck, -hl + 10, wid / 2, deck + 2.4f, 10);
		m.box(Mat.FIBERGLASS, -wid / 2 + 2, deck, 6, wid / 2 - 2, deck + 4, 11);
		m.box(Mat.DASH, -wid / 2 + 3, deck + 3.2f, 5.95f, wid / 2 - 3, deck + 4, 6.05f);
		m.box(Mat.BLACK, -10.8f, deck + 4, 5.5f, -7.2f, deck + 7, 6.2f);
		VehicleModels.slab(m, Mat.GLASS, -wid / 2 + 2, wid / 2 - 2, 12, deck + 4, 9, deck + 8, 0.6f);
		m.box(Mat.CHROME, -wid / 2 + 1.5f, deck + 3.6f, 9, -wid / 2 + 2.3f, deck + 8.2f, 12.2f);
		m.box(Mat.CHROME, wid / 2 - 2.3f, deck + 3.6f, 9, wid / 2 - 1.5f, deck + 8.2f, 12.2f);
		bucket(m, -8, deck, 7);
		bucket(m, 8, deck, 7);
		m.box(Mat.SEAT, -wid / 2 + 2.5f, deck, -26, wid / 2 - 2.5f, deck + 4, -18);
		m.box(Mat.SEAT, -wid / 2 + 2.5f, deck + 4, -26, wid / 2 - 2.5f, deck + 9, -24);
		// engine hatch with vents, swim platform, twin drives
		m.box(Mat.PAINT, -wid / 2 + 2, deck, -hl, wid / 2 - 2, deck + 2.6f, -hl + 22);
		m.box(Mat.VENT, -10, deck + 2.6f, -hl + 6, -3, deck + 2.7f, -hl + 18);
		m.box(Mat.VENT, 3, deck + 2.6f, -hl + 6, 10, deck + 2.7f, -hl + 18);
		m.box(Mat.DECK, -wid / 2 + 1, deck - 4, -hl - 4, wid / 2 - 1, deck - 3, -hl);
		for (int sx = -1; sx <= 1; sx += 2) {
			m.box(Mat.OUTBOARD, sx * 6 - 2.5f, deck - 12, -hl - 6, sx * 6 + 2.5f, deck - 3, -hl - 1);
			m.box(Mat.METAL, sx * 6 - 0.8f, deck - 14, -hl - 5, sx * 6 + 0.8f, deck - 11, -hl - 3);
		}
		m.pair(Mat.HEADLIGHT, 1.5f, deck - 1, hl - 0.3f, 3, deck + 0.5f, hl + 0.2f);
		m.pair(Mat.TAIL_PLAIN, wid / 2 - 3, deck + 0.4f, -hl - 0.1f, wid / 2 - 1.5f, deck + 1.4f, -hl + 0.2f);
	}

	/** Shitzu Squalo: cabin cruiser with a raised wheelhouse, dark windows and a flybridge rail. */
	private static void squalo(VehicleModel m) {
		float len = 116, wid = 40, deck = 14;
		hull(m, len, wid, 14, deck, Mat.FIBERGLASS, Mat.PAINT);
		float hl = len / 2;
		m.pair(Mat.PAINT, wid / 2 - 0.05f, deck - 6, -hl + 1, wid / 2 + 0.06f, deck - 1.5f, hl * 0.25f);
		m.pair(Mat.CHROME, wid / 2 - 0.05f, deck - 0.8f, -hl + 1, wid / 2 + 0.08f, deck - 0.4f, hl * 0.25f);
		// cabin: tapered front, dark window band, hardtop
		m.box(Mat.FIBERGLASS, -wid / 2 + 3, deck, -12, wid / 2 - 3, deck + 10, 20);
		VehicleModels.slab(m, Mat.FIBERGLASS, -wid / 2 + 4, wid / 2 - 4, 30, deck, 20, deck + 10, 2);
		m.pair(Mat.GLASS_DARK, wid / 2 - 3.05f, deck + 4.5f, -10, wid / 2 - 2.8f, deck + 8.6f, 18);
		VehicleModels.slab(m, Mat.GLASS_DARK, -wid / 2 + 5, wid / 2 - 5, 29.2f, deck + 4.6f, 21, deck + 9.6f, 0.4f);
		m.box(Mat.FIBERGLASS, -wid / 2 + 2, deck + 10, -22, wid / 2 - 2, deck + 11.4f, 21);
		m.box(Mat.CHROME, -wid / 2 + 2.5f, deck + 11.4f, -20, -wid / 2 + 3.2f, deck + 14, 18);
		m.box(Mat.CHROME, wid / 2 - 3.2f, deck + 11.4f, -20, wid / 2 - 2.5f, deck + 14, 18);
		m.box(Mat.METAL, -1, deck + 11.4f, -6, 1, deck + 22, -4);
		m.box(Mat.METAL, -6, deck + 19, -5.6f, 6, deck + 19.6f, -4.4f);
		// aft cockpit, seats, swim platform, rails and fittings
		m.box(Mat.TEAK, -wid / 2 + 2, deck - 1, -hl + 6, wid / 2 - 2, deck + 0.05f, -12);
		m.box(Mat.FIBERGLASS, -wid / 2, deck, -hl + 6, -wid / 2 + 2, deck + 3, -12);
		m.box(Mat.FIBERGLASS, wid / 2 - 2, deck, -hl + 6, wid / 2, deck + 3, -12);
		m.box(Mat.SEAT, -wid / 2 + 3, deck, -hl + 7, wid / 2 - 3, deck + 4, -hl + 13);
		m.box(Mat.SEAT, -wid / 2 + 3, deck + 4, -hl + 7, wid / 2 - 3, deck + 9, -hl + 9);
		bucket(m, -9, deck, 14);
		bucket(m, 9, deck, 14);
		m.box(Mat.TEAK, -wid / 2 + 1, deck - 5, -hl - 6, wid / 2 - 1, deck - 4, -hl);
		m.pair(Mat.CHROME, wid / 2 - 2, deck + 1, 22, wid / 2 - 1.2f, deck + 5, hl * 0.25f + 6);
		m.pair(Mat.HEADLIGHT, 1.5f, deck, hl - 0.3f, 3, deck + 1.5f, hl + 0.2f);
		m.box(Mat.OUTBOARD, -4, deck - 14, -hl - 5, 4, deck - 5, -hl - 1);
	}

	/** Center console fishing boat (unnamed in trailers): centre-console fishing boat with twin outboards, T-top and rod holders. */
	private static void lurePredator(VehicleModel m) {
		float len = 96, wid = 36, deck = 11;
		hull(m, len, wid, 11, deck, Mat.PAINT, Mat.WHITE);
		float hl = len / 2;
		m.pair(Mat.WHITE, wid / 2 - 0.05f, deck - 5, -hl + 1, wid / 2 + 0.06f, deck - 4, hl * 0.25f);
		m.box(Mat.FIBERGLASS, -wid / 2, deck, -hl, -wid / 2 + 1.6f, deck + 3, hl * 0.25f);
		m.box(Mat.FIBERGLASS, wid / 2 - 1.6f, deck, -hl, wid / 2, deck + 3, hl * 0.25f);
		m.box(Mat.RUBBER_MAT, -wid / 2 + 1.6f, deck - 1, -hl + 2, wid / 2 - 1.6f, deck + 0.05f, hl * 0.25f);
		// centre console with screen, wheel and leaning post
		m.box(Mat.FIBERGLASS, -6, deck, -2, 6, deck + 9, 8);
		VehicleModels.slab(m, Mat.GLASS, -6, 6, 8, deck + 9, 6, deck + 13, 0.5f);
		m.box(Mat.DASH, -5, deck + 6, 8.0f, 5, deck + 8.6f, 8.1f).faces(32);
		m.box(Mat.BLACK, -2, deck + 7, -2.6f, 2, deck + 10, -2);
		m.box(Mat.SEAT, -6, deck, -14, 6, deck + 6, -8);
		m.box(Mat.SEAT, -6, deck + 6, -14, 6, deck + 11, -12.5f);
		// T-top on four posts
		for (int sx = -1; sx <= 1; sx += 2) for (float z : new float[] {-12, 6}) m.box(Mat.METAL, sx * 6.5f - 0.5f, deck + 9, z - 0.5f, sx * 6.5f + 0.5f, deck + 26, z + 0.5f);
		m.box(Mat.CANVAS, -8, deck + 26, -14, 8, deck + 27, 9);
		m.box(Mat.CHROME, -7, deck + 27, -4, -5, deck + 30, -3);
		for (float x : new float[] {-4, -1.5f, 1.5f, 4}) m.box(Mat.METAL, x - 0.4f, deck + 27, -13, x + 0.4f, deck + 29, -12.2f);
		// casting deck, cooler bench, bow rail
		m.box(Mat.FIBERGLASS, -wid / 2 + 1.6f, deck, 20, wid / 2 - 1.6f, deck + 2, hl * 0.25f + 6);
		m.box(Mat.WHITE, -6, deck, 10, 6, deck + 5, 16);
		m.pair(Mat.CHROME, wid / 2 - 2.2f, deck + 3, 14, wid / 2 - 1.4f, deck + 6, hl - 6);
		// twin outboards on the transom
		for (int sx = -1; sx <= 1; sx += 2) {
			float x = sx * 6;
			m.box(Mat.OUTBOARD, x - 2.6f, deck - 2, -hl - 6, x + 2.6f, deck + 7, -hl - 1);
			m.box(Mat.WHITE, x - 2.65f, deck + 2, -hl - 6.05f, x + 2.65f, deck + 3, -hl - 0.95f);
			m.box(Mat.OUTBOARD, x - 1, deck - 13, -hl - 5, x + 1, deck - 2, -hl - 2);
			m.box(Mat.METAL, x - 1.2f, deck - 14, -hl - 5.5f, x + 1.2f, deck - 12, -hl - 1.5f);
		}
		m.pair(Mat.HEADLIGHT, 1.2f, deck + 0.5f, hl - 0.3f, 2.6f, deck + 1.8f, hl + 0.2f);
	}

	/** Crest Kayak: single sit-in kayak with a double paddle. */
	private static void kayak(VehicleModel m) {
		float len = 50, wid = 11, deck = 5;
		hull(m, len, wid, 5, deck, Mat.KAYAK, Mat.KAYAK);
		float hl = len / 2;
		// pointed stern too
		m.box(Mat.KAYAK, -wid / 2 + 2, deck - 3, -hl - 6, wid / 2 - 2, deck, -hl);
		m.box(Mat.KAYAK, -1.5f, deck - 2, -hl - 10, 1.5f, deck, -hl - 6);
		m.box(Mat.BLACK, -4, deck - 0.5f, -7, 4, deck + 0.6f, 6);
		m.box(Mat.RUBBER, -4.5f, deck + 0.6f, -7.5f, 4.5f, deck + 1.2f, 6.5f);
		m.box(Mat.SEAT, -3, deck - 1, -6, 3, deck + 3, -4.5f);
		m.box(Mat.BLACK, -0.6f, deck, 10, 0.6f, deck + 0.4f, 20);
		// paddle across the cockpit
		m.box(Mat.METAL, -20, deck + 6, 3.6f, 20, deck + 6.6f, 4.2f).roll(12);
		m.box(Mat.YELLOW, -24, deck + 4.6f, 3.2f, -19, deck + 6.6f, 4.6f).roll(12);
		m.box(Mat.YELLOW, 19, deck + 6, 3.2f, 24, deck + 8, 4.6f).roll(12);
		m.kayak = true;
	}

	/** Western Zombie: long-raked chopper with ape-hanger bars, fat rear tyre and lots of chrome. */
	private static void zombie(VehicleModel m) {
		m.bikeRider = true;
		float rf = 6.6f, rr = 6.2f;
		float zf = 19, zr = -13;
		m.wheels.add(new VehicleModel.Wheel(0, rf, zf, rf, 2.4f, true, true));
		m.wheels.add(new VehicleModel.Wheel(0, rr, zr, rr, 5, false, false));
		m.steerPivotZ = zf - 6;
		m.steerRake = 34;
		// long fork, ape hangers, headlight
		m.box(Mat.CHROME, -2.0f, rf, zf - 0.6f, -1.2f, 24, zf + 0.6f).group(G_STEER).pitch(-34);
		m.box(Mat.CHROME, 1.2f, rf, zf - 0.6f, 2.0f, 24, zf + 0.6f).group(G_STEER).pitch(-34);
		m.box(Mat.CHROME, -6.5f, 22, zf - 10.4f, -5.8f, 28, zf - 9.6f).group(G_STEER);
		m.box(Mat.CHROME, 5.8f, 22, zf - 10.4f, 6.5f, 28, zf - 9.6f).group(G_STEER);
		m.box(Mat.CHROME, -6.5f, 27.4f, zf - 10.6f, 6.5f, 28.2f, zf - 9.6f).group(G_STEER);
		m.box(Mat.BLACK, -7.5f, 27.4f, zf - 10.6f, -5.5f, 28.4f, zf - 9.4f).group(G_STEER);
		m.box(Mat.BLACK, 5.5f, 27.4f, zf - 10.6f, 7.5f, 28.4f, zf - 9.4f).group(G_STEER);
		m.box(Mat.CHROME, -2.2f, 18, zf - 7.6f, 2.2f, 21.4f, zf - 5.4f).group(G_STEER);
		m.box(Mat.HEADLIGHT, -1.8f, 18.4f, zf - 5.45f, 1.8f, 21.0f, zf - 5.3f).group(G_STEER);
		// teardrop tank, low solo seat, V-twin, exhausts, fat fender
		m.box(Mat.PAINT, -2.8f, 13.5f, 0, 2.8f, 17.5f, 10);
		m.box(Mat.PAINT, -2.2f, 14, 10, 2.2f, 17, 12).pitch(-15);
		m.box(Mat.CHROME, -0.4f, 17.5f, 2, 0.4f, 17.7f, 9);
		m.box(Mat.LEATHER, -3.4f, 9.4f, -9, 3.4f, 11.0f, -1);
		m.box(Mat.LEATHER, -2.4f, 11, -10, 2.4f, 13, -8.5f);
		m.box(Mat.CHROME, -3.6f, 4.5f, 0.5f, 3.6f, 9, 7);
		m.box(Mat.CHROME, -3.0f, 8.4f, 1, -0.2f, 13.4f, 3.6f).pitch(20);
		m.box(Mat.CHROME, 0.2f, 8.4f, 4, 3.0f, 13.4f, 6.6f).pitch(-20);
		m.box(Mat.BLACK, -2.6f, 3.6f, -2, 2.6f, 6, 1);
		m.box(Mat.CHROME, 3.4f, 4.4f, -18, 4.6f, 5.6f, 4);
		m.box(Mat.CHROME, 3.4f, 6.4f, -18, 4.6f, 7.6f, 6);
		m.box(Mat.PAINT, -3.4f, rr * 2 - 0.6f, zr - 6, 3.4f, rr * 2 + 0.6f, zr + 4);
		m.box(Mat.TAIL_PLAIN, -1.2f, rr * 2 - 0.4f, zr - 6.3f, 1.2f, rr * 2 + 0.6f, zr - 5.9f);
		m.box(Mat.BLACK, -3.2f, rr - 0.8f, zr, 3.2f, rr + 0.8f, -2);
		m.box(Mat.CHROME, -0.5f, 3, 6, 0.5f, 13.6f, 7).pitch(-25);
		m.beam(0, 19.7f, zf - 5.2f);
		m.beams.remove(m.beams.size() - 1);
	}

	private static void bucket(VehicleModel m, float x, float deck, float z) {
		m.box(Mat.SEAT, x - 3.5f, deck, z - 8, x + 3.5f, deck + 3.5f, z - 2);
		m.box(Mat.SEAT, x - 3.5f, deck + 3.5f, z - 8, x + 3.5f, deck + 10, z - 6.5f);
	}

	private static void airboat(VehicleModel m) {
		float len = 72, wid = 36;
		float hl = len / 2;
		// flat aluminium hull with a raked bow
		m.box(Mat.METAL, -wid / 2, 1, -hl, wid / 2, 7, hl - 10);
		VehicleModels.slab(m, Mat.METAL, -wid / 2, wid / 2, hl, 7, hl - 10.5f, 1.5f, 2);
		m.box(Mat.PAINT, -wid / 2 - 0.05f, 3, -hl, wid / 2 + 0.05f, 6.5f, hl - 10);
		m.box(Mat.DECK, -wid / 2 + 1.5f, 7, -hl + 1, wid / 2 - 1.5f, 7.4f, hl - 10);
		// front bench, raised driver seat on a frame, engine and prop cage
		m.box(Mat.SEAT, -14, 7, 10, 14, 10, 17);
		m.box(Mat.SEAT, -14, 10, 10, 14, 14, 11.5f);
		m.box(Mat.METAL, -4, 7, -8, -3, 14, -1);
		m.box(Mat.METAL, 3, 7, -8, 4, 14, -1);
		m.box(Mat.SEAT, -4.5f, 14, -7, 4.5f, 16, 1);
		m.box(Mat.SEAT, -4.5f, 16, -7.5f, 4.5f, 24, -6);
		m.box(Mat.METAL, -6, 7, -20, 6, 15, -11);
		m.box(Mat.BLACK, -4.5f, 15, -18, 4.5f, 22, -12);
		m.box(Mat.CHROME, -1, 18, -21, 1, 20, -18);
		m.propY = 19;
		m.propZ = -22;
		// cage (cutout mesh) around the propeller
		float cy = 19, cz = -24.5f, rr = 15;
		m.box(Mat.CAGE, -rr, cy - rr, cz - 0.2f, rr, cy + rr, cz + 0.2f);
		m.box(Mat.METAL, -rr - 1, 7, cz - 0.8f, -rr, cy + rr, cz + 0.8f);
		m.box(Mat.METAL, rr, 7, cz - 0.8f, rr + 1, cy + rr, cz + 0.8f);
		m.box(Mat.METAL, -rr - 1, cy + rr, cz - 0.8f, rr + 1, cy + rr + 1, cz + 0.8f);
		m.box(Mat.METAL, -rr, 7, cz - 0.8f, -rr + 0.9f, 8, -12);
		// rudders
		m.box(Mat.PAINT, -6, 9, -hl - 4, -5.2f, 26, -hl + 1);
		m.box(Mat.PAINT, 5.2f, 9, -hl - 4, 6, 26, -hl + 1);
		// propeller blades (spin)
		m.box(Mat.WOOD, -13, 18.2f, -22.7f, 13, 19.8f, -22.1f).group(G_PROP);
		m.box(Mat.WOOD, -0.8f, 6.5f, -22.7f, 0.8f, 31.5f, -22.1f).group(G_PROP);
	}

	private static void jetSki(VehicleModel m) {
		float len = 40, wid = 15, deck = 9;
		hull(m, len, wid, 9, deck, Mat.PAINT, Mat.WHITE);
		float hl = len / 2;
		m.pair(Mat.BLACK, wid / 2 - 0.05f, deck - 4.5f, -hl + 1, wid / 2 + 0.06f, deck - 3.5f, hl * 0.25f);
		m.box(Mat.HULL, -wid / 2 + 0.5f, deck, -hl + 2, wid / 2 - 0.5f, deck + 2, 4);
		m.box(Mat.SEAT, -2.8f, deck + 2, -hl + 4, 2.8f, deck + 4.6f, 1);
		m.box(Mat.PAINT, -4, deck, 1, 4, deck + 7, 9).pitch(-12);
		m.box(Mat.PAINT_DARK, -2, deck + 5, 3, 2, deck + 9, 6).group(G_STEER);
		m.box(Mat.BLACK, -6, deck + 8.5f, 3.5f, 6, deck + 9.3f, 4.3f).group(G_STEER);
		m.box(Mat.GLASS, -3.5f, deck + 6.5f, 7, 3.5f, deck + 9, 8).pitch(-30);
		m.box(Mat.DECK, -wid / 2, deck - 0.2f, -hl - 1, wid / 2, deck + 0.4f, -hl + 2);
		m.box(Mat.BLACK, -1.5f, 1, -hl - 1, 1.5f, 3.5f, -hl + 1);
		m.steerPivotZ = 4.5f;
	}

	/** Seat in model pixels. */
	static Vec3 seatPx(VehicleSpec spec, int i) {
		Vec3 s = spec.seats().get(i);
		return new Vec3(s.x * 16, s.y * 16, s.z * 16);
	}

	static boolean isCar(VehicleSpec spec) {
		return spec.kind() == VehicleSpec.Kind.CAR;
	}

	static void touch() {
		VehicleTypes.SPECS.values().forEach(VehicleModels::get);
	}
}
