package dev.leonida.vehicle.ai;

import dev.leonida.vehicle.VehicleControls;
import dev.leonida.vehicle.VehicleEntity;

/** Server-side AI that drives a vehicle with no player at the wheel. */
public interface VehicleDriver {
	VehicleControls drive(VehicleEntity vehicle);

	/** Saved kind, or null if this driver should not persist. */
	default String kind() { return null; }
}
