package dev.leonida.hud;

/** Server and common setup for the hud module. Owned by the hud agent. */
public final class HudModule {
	private HudModule() {}

	public static void init() {
		HudNet.register();
		Pregen.register();
		DhGeneration.register();
	}
}
