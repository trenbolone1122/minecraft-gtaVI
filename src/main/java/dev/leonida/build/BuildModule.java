package dev.leonida.build;

import dev.leonida.core.api.BuildingGenerator;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Server and common setup for the build module. Owned by the build agent. */
public final class BuildModule {
	private BuildModule() {}

	public static void init() {
		BuildingGenerator.install(new LeonidaBuildingGenerator());
		ServerTickEvents.END_SERVER_TICK.register(Elevator::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> BuildCommand.register(dispatcher));
	}
}
