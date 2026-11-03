package dev.leonida.vehicle;

/** Driver input for one tick: throttle and steer in -1..1 (steer +1 = right), handbrake. */
public record VehicleControls(float throttle, float steer, boolean handbrake) {
	public static final VehicleControls NONE = new VehicleControls(0, 0, false);
	public static final VehicleControls BRAKE = new VehicleControls(0, 0, true);

	public static final int FORWARD = 1, BACK = 2, LEFT = 4, RIGHT = 8, HANDBRAKE = 16;

	public int bits() {
		int b = 0;
		if (throttle > 0.1f) b |= FORWARD;
		if (throttle < -0.1f) b |= BACK;
		if (steer < -0.1f) b |= LEFT;
		if (steer > 0.1f) b |= RIGHT;
		if (handbrake) b |= HANDBRAKE;
		return b;
	}

	public static VehicleControls fromBits(int b) {
		float t = ((b & FORWARD) != 0 ? 1 : 0) - ((b & BACK) != 0 ? 1 : 0);
		float s = ((b & RIGHT) != 0 ? 1 : 0) - ((b & LEFT) != 0 ? 1 : 0);
		return new VehicleControls(t, s, (b & HANDBRAKE) != 0);
	}
}
