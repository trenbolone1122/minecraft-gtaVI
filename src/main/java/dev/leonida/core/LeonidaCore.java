package dev.leonida.core;

/**
 * Shared core. Owned by the integrator (root agent). Module agents may read it freely, but changes
 * go through the integrator so every module keeps compiling.
 */
public final class LeonidaCore {
	private LeonidaCore() {}

	public static void init() {
		PlayerState.register();
	}
}
