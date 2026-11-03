package dev.leonida.aircraft;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.entity.player.Player;

/**
 * Bridges from common code to the client. The client sets these in AircraftClient.init(), so common code never
 * references client classes. On a dedicated server they keep their idle defaults.
 */
public final class AircraftHooks {
	private AircraftHooks() {}

	/** Raw pilot keys of the local player. */
	public record PilotKeys(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down) {
		public static final PilotKeys NONE = new PilotKeys(false, false, false, false, false, false);
	}

	public static Supplier<PilotKeys> pilotKeys = () -> PilotKeys.NONE;
	/** True while the given (local) player is in skydiving freefall; drives the spread-eagle pose. */
	public static Predicate<Player> freefalling = p -> false;
}
