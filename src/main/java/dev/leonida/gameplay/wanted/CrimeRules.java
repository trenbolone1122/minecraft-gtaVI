package dev.leonida.gameplay.wanted;

import dev.leonida.core.api.CrimeEvents.Crime;

/** How serious each crime is. */
public final class CrimeRules {
	private CrimeRules() {}

	/** Minimum wanted level this crime earns once the police know about it. */
	public static int stars(Crime c) {
		return switch (c) {
			case TRESPASS, PROPERTY_DAMAGE, RECKLESS_DRIVING, CAR_THEFT, ASSAULT -> 1;
			case CARJACK, HIT_PEDESTRIAN, GUNSHOT, ROBBERY -> 2;
			case KILL_CIVILIAN, EXPLOSION, AIRCRAFT_THEFT -> 3;
			case KILL_COP -> 4;
		};
	}

	/** Violent crimes push the level one star higher when the player is already at or above stars(c). */
	public static boolean escalates(Crime c) {
		return c == Crime.KILL_COP || c == Crime.KILL_CIVILIAN || c == Crime.EXPLOSION;
	}

	/** Heard from far away even with nobody looking. */
	public static boolean loud(Crime c) {
		return c == Crime.GUNSHOT || c == Crime.EXPLOSION;
	}

	/** Makes pedestrians run instead of filming. */
	public static boolean scary(Crime c) {
		return c == Crime.GUNSHOT || c == Crime.EXPLOSION || c == Crime.KILL_CIVILIAN || c == Crime.KILL_COP || c == Crime.ROBBERY;
	}

	/** Police switch from arresting to shooting. */
	public static boolean lethal(Crime c) {
		return c == Crime.GUNSHOT || c == Crime.EXPLOSION || c == Crime.KILL_CIVILIAN || c == Crime.KILL_COP;
	}
}
