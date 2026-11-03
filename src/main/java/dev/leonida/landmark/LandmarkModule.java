package dev.leonida.landmark;

import dev.leonida.core.api.LandmarkGenerator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/** Server and common setup for the landmark module (hand-built Leonida landmarks outside Vice City: Jason's house, the Keys, Grassrivers, Port Gellhorn, Ambrosia). Owned by the landmark agent. */
public final class LandmarkModule {
	private LandmarkModule() {}

	public static void init() {
		LandmarkGenerator.register(new LandmarkGen());
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> LandmarkCommand.register(dispatcher));
	}
}
