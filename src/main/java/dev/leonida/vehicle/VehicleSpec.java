package dev.leonida.vehicle;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Static description of one vehicle model: size, handling, seats, paint options.
 * Speeds are metres per second, accelerations metres per second squared (1 block = 1 metre).
 */
public record VehicleSpec(
		String id,
		String displayName,
		Kind kind,
		float width,          // collision width (blocks)
		float length,         // body length for front/rear probes and entity hits
		float height,
		float wheelbase,
		float maxSpeed,       // m/s
		float accel,          // m/s^2 at low speed
		float brake,          // m/s^2
		float maxReverse,     // m/s
		float maxSteerDeg,
		float grip,           // lateral grip, m/s^2
		float mass,           // kg, for vehicle-vehicle impulses
		float maxHealth,
		float stepHeight,
		List<Vec3> seats,     // x right, y up, z forward, in blocks relative to entity origin
		int[] paints,         // allowed body colours (RGB)
		boolean police,
		String engineSound) {

	public enum Kind { CAR, BIKE, BOAT }

	public boolean isBoat() { return kind == Kind.BOAT; }
	public boolean isBike() { return kind == Kind.BIKE; }

	public int seatCount() { return seats.size(); }
}
