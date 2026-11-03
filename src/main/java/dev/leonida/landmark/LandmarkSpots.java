package dev.leonida.landmark;

import java.util.List;

/**
 * Public read-only list of named spots at landmarks, for other modules: parked vehicles (vehicle module), NPC posts
 * (character/gameplay), safehouse entry. World block coordinates; {@code y} is -1 when the spot sits on the site
 * ground (ask {@link #groundAware}).
 */
public final class LandmarkSpots {
	private LandmarkSpots() {}

	/**
	 * @param id unique id, e.g. {@code jason_house/car}
	 * @param what free text hint for the consumer, e.g. a vehicle model name
	 * @param yaw Minecraft yaw the vehicle or NPC should face
	 */
	public record Spot(String id, String what, double x, double y, double z, float yaw) {}

	private static final List<Spot> ALL = List.of(
			// Trailer 2 0:08 and the gtadb photo: turquoise Vapid Ganado (El Camino style ute) parked in front of the stairs,
			// nose pointing east. Ultimate Edition shot also has a Dinka Enduro and a Crest kayak at the house.
			new Spot("jason_house/car", "vehicle:vapid_ganado:turquoise", -2348.5, -1, 5542.5, -90f),
			new Spot("jason_house/bike", "vehicle:dinka_enduro", -2343.5, -1, 5538.5, 180f),
			new Spot("jason_house/door", "safehouse_entry", -2358.5, -1, 5537.5, 180f));

	public static List<Spot> all() {
		return ALL;
	}

	/** True when y is not fixed and the consumer should put the spot on the ground at (x, z). */
	public static boolean groundAware(Spot s) {
		return s.y() < 0;
	}
}
