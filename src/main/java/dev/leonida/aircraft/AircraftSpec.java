package dev.leonida.aircraft;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Static description of one aircraft type: flight stats (the same knobs Immersive Aircraft keeps in its
 * aircraft JSON files), seats, model and special equipment. Values are per tick unless noted.
 */
public final class AircraftSpec {
	public enum Kind { PLANE, HELICOPTER }

	public final String id;
	public final Kind kind;
	/** Blockbench model id, resolved by the client renderer as leonida:objects/aircraft/&lt;id&gt;.bbmodel. */
	public final String model;
	public final List<Vec3> seats;
	public final float yawSpeed, pitchSpeed, engineSpeed, verticalSpeed, friction, horizontalDecay, verticalDecay;
	public final float groundFriction, brakeFactor, pushSpeed, glideFactor, lift, rollFactor, wind, mass, stabilizer;
	public final float acceleration, maxHealth;
	public final boolean floats, smokeTrail, searchlight, newsCamera;
	public final Vec3 smokeOffset;
	/** Searchlight pivot in blocks, model space (matches the "searchlight" bone origin in the bbmodel). */
	public final Vec3 lightPivot;
	/** Nose-up attitude in degrees while parked (tail draggers), pivoting on the main gear at groundPivotZ blocks. */
	public final float groundPitch, groundPivotZ;
	/** Number of paint schemes; the client loads model id, id_v1, id_v2 ... */
	public final int variants;
	public final String displayName;
	/** Third person camera distance while riding, in blocks. */
	public final float cameraDistance;

	private AircraftSpec(Builder b) {
		this.lightPivot = b.lightPivot;
		this.groundPitch = b.groundPitch;
		this.groundPivotZ = b.groundPivotZ;
		this.variants = b.variants;
		this.displayName = b.displayName;
		this.cameraDistance = b.cameraDistance;
		this.id = b.id;
		this.kind = b.kind;
		this.model = b.model;
		this.seats = List.copyOf(b.seats);
		this.yawSpeed = b.yawSpeed;
		this.pitchSpeed = b.pitchSpeed;
		this.engineSpeed = b.engineSpeed;
		this.verticalSpeed = b.verticalSpeed;
		this.friction = b.friction;
		this.horizontalDecay = b.horizontalDecay;
		this.verticalDecay = b.verticalDecay;
		this.groundFriction = b.groundFriction;
		this.brakeFactor = b.brakeFactor;
		this.pushSpeed = b.pushSpeed;
		this.glideFactor = b.glideFactor;
		this.lift = b.lift;
		this.rollFactor = b.rollFactor;
		this.wind = b.wind;
		this.mass = b.mass;
		this.stabilizer = b.stabilizer;
		this.acceleration = b.acceleration;
		this.maxHealth = b.maxHealth;
		this.floats = b.floats;
		this.smokeTrail = b.smokeTrail;
		this.searchlight = b.searchlight;
		this.newsCamera = b.newsCamera;
		this.smokeOffset = b.smokeOffset;
	}

	public boolean isHelicopter() {
		return kind == Kind.HELICOPTER;
	}

	static Builder plane(String id) {
		return new Builder(id, Kind.PLANE);
	}

	static Builder helicopter(String id) {
		return new Builder(id, Kind.HELICOPTER).yaw(4.0f).pitch(4.0f).engine(0.13f).vertical(0.035f).friction(0.005f)
				.decay(0.97f, 0.9f).groundFriction(0.8f).lift(0.1f).roll(25f).wind(0.03f).mass(3f).stabilizer(0.2f)
				.acceleration(0.6f);
	}

	static final class Builder {
		final String id;
		final Kind kind;
		String model;
		List<Vec3> seats = List.of(Vec3.ZERO);
		float yawSpeed = 3f, pitchSpeed = 3f, engineSpeed = 0.06f, verticalSpeed = 0f, friction = 0.002f;
		float horizontalDecay = 0.995f, verticalDecay = 0.995f, groundFriction = 0.92f, brakeFactor = 0.95f;
		float pushSpeed = 0.03f, glideFactor = 0.05f, lift = 0.15f, rollFactor = 40f, wind = 0.02f, mass = 2f;
		float stabilizer = 0f, acceleration = 1f, maxHealth = 1f;
		boolean floats, smokeTrail, searchlight, newsCamera;
		Vec3 smokeOffset = Vec3.ZERO;
		Vec3 lightPivot = new Vec3(0, 0.5, 1.9);
		float groundPitch, groundPivotZ;
		int variants = 1;
		String displayName = "";
		float cameraDistance = 12f;

		Builder(String id, Kind kind) {
			this.id = id;
			this.kind = kind;
			this.model = id;
		}

		Builder seats(Vec3... s) { this.seats = List.of(s); return this; }
		Builder yaw(float v) { yawSpeed = v; return this; }
		Builder pitch(float v) { pitchSpeed = v; return this; }
		Builder engine(float v) { engineSpeed = v; return this; }
		Builder vertical(float v) { verticalSpeed = v; return this; }
		Builder friction(float v) { friction = v; return this; }
		Builder decay(float h, float v) { horizontalDecay = h; verticalDecay = v; return this; }
		Builder groundFriction(float v) { groundFriction = v; return this; }
		Builder lift(float v) { lift = v; return this; }
		Builder glide(float v) { glideFactor = v; return this; }
		Builder roll(float v) { rollFactor = v; return this; }
		Builder wind(float v) { wind = v; return this; }
		Builder mass(float v) { mass = v; return this; }
		Builder stabilizer(float v) { stabilizer = v; return this; }
		Builder acceleration(float v) { acceleration = v; return this; }
		Builder health(float v) { maxHealth = v; return this; }
		Builder floats() { floats = true; return this; }
		Builder smoke(Vec3 offset) { smokeTrail = true; smokeOffset = offset; return this; }
		Builder searchlight(Vec3 pivot) { searchlight = true; lightPivot = pivot; return this; }
		Builder groundPitch(float degrees, float pivotZ) { groundPitch = degrees; groundPivotZ = pivotZ; return this; }
		Builder variants(int n) { variants = n; return this; }
		Builder name(String n) { displayName = n; return this; }
		Builder camera(float d) { cameraDistance = d; return this; }
		Builder newsCamera() { newsCamera = true; return this; }

		AircraftSpec build() {
			return new AircraftSpec(this);
		}
	}
}
