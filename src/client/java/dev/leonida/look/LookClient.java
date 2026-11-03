package dev.leonida.look;

import dev.leonida.look.client.NightWindows;

/** Client-only setup for the look module. Owned by the look agent. */
public final class LookClient {
	private LookClient() {}

	public static void init() {
		NightWindows.init();
	}
}
