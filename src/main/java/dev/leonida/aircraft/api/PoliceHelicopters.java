package dev.leonida.aircraft.api;

import dev.leonida.aircraft.AircraftModule;
import dev.leonida.aircraft.entity.AircraftEntity;
import dev.leonida.core.api.PoliceUnits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Police air support for the wanted system (installed as PoliceUnits.helicopters()). The helicopter flies to the
 * target, hovers about 25 blocks above, circles at 18 blocks and keeps its searchlight on them. setTarget(heli, null)
 * sends it away; it despawns after about 20 seconds. A player who climbs into the pilot seat takes it over, which
 * fires AIRCRAFT_THEFT. Units are saved with the world: after a reload they hover in place and pick their target up
 * again when that player is back (or leave after 30 seconds).
 */
public final class PoliceHelicopters implements PoliceUnits.Helicopters {
	public static final PoliceHelicopters INSTANCE = new PoliceHelicopters();
	public static final double HEIGHT = 25, RADIUS = 18, SPEED = 0.45;

	private PoliceHelicopters() {}

	@Override
	public @Nullable Entity spawnHelicopter(ServerLevel level, Vec3 pos, @Nullable ServerPlayer target) {
		AircraftEntity heli = AircraftModule.type(AircraftModule.POLICE_HELICOPTER).create(level, EntitySpawnReason.EVENT);
		if (heli == null) return null;
		float yaw = 0;
		if (target != null) {
			Vec3 to = target.position().subtract(pos);
			yaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
		}
		heli.snapTo(pos.x, pos.y, pos.z, yaw, 0);
		heli.autopilot.policeUnit = true;
		heli.autopilot.yieldsToPlayer = true;
		heli.setEngineTarget(1f);
		level.addFreshEntity(heli);
		setTarget(heli, target);
		return heli;
	}

	@Override
	public void setTarget(Entity entity, @Nullable ServerPlayer target) {
		if (!(entity instanceof AircraftEntity heli) || heli.isRemoved()) return;
		if (target == null) {
			heli.autopilot.leave();
			return;
		}
		heli.autopilot.policeUnit = true;
		heli.autopilot.orbit(target, target.position(), RADIUS, HEIGHT, SPEED);
		heli.setSearchlight(true);
		heli.setSearchlightTarget(target.getId());
	}

	/**
	 * The player this unit is after (also right after a world reload, before that player has rejoined), or null
	 * if it is leaving. Lets the wanted system adopt the units it finds with {@link #active} instead of spawning more.
	 */
	public static java.util.@Nullable UUID targetOf(Entity entity) {
		return entity instanceof AircraftEntity a && a.autopilot.policeUnit ? a.autopilot.savedTarget() : null;
	}

	/** Police helicopters currently circling or leaving in this level. */
	public static List<AircraftEntity> active(ServerLevel level) {
		List<AircraftEntity> out = new ArrayList<>();
		for (Entity e : level.getAllEntities()) {
			if (e instanceof AircraftEntity a && a.autopilot.policeUnit && !a.isRemoved()) out.add(a);
		}
		return out;
	}
}
