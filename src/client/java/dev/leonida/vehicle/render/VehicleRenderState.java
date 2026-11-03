package dev.leonida.vehicle.render;

import dev.leonida.vehicle.VehicleSpec;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Snapshot of a vehicle for one frame. */
public class VehicleRenderState extends EntityRenderState {
	public VehicleSpec spec;
	public float yaw, pitch, roll, doorL, doorR, suspension, steer, wheelSpin, speed, healthFraction, hurt;
	public int flags, color, accent, controls, entityId, rimTile = 2;
	public boolean wrecked, npcDriver, windowBroken, headlights, siren, alarm, braking, reversing, hasDriver, sheriff;
}
