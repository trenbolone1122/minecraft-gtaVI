package dev.leonida.vicecity;

import dev.leonida.core.api.LandmarkGenerator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/** Server and common setup for the vicecity module (hand-built Vice City and Vice Beach landmarks: Sahara Arena, Fleeca Field, hero towers, Ocean Drive hotels). Owned by the vicecity agent. */
public final class VicecityModule {
	private VicecityModule() {}

	public static void init() {
		dev.leonida.vicecity.site.Sites.registerAll(ViceLandmarks.INSTANCE);
		LandmarkGenerator.register(ViceLandmarks.INSTANCE);
		CommandRegistrationCallback.EVENT.register((d, ctx, env) -> ViceCommand.register(d));
	}
}
