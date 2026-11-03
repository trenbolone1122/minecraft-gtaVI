package dev.leonida.hud;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client side map blip registry shown on the minimap (with edge clamping) and the full map.
 * Other modules call {@link #put}/{@link #remove} from client code, or {@code HudNet.blip} from the server.
 */
public final class Blips {
	private Blips() {}

	/** Blip kinds; texture is textures/hud/blip_&lt;name&gt;.png. */
	public enum Type {
		HOME("Home"), SAFEHOUSE("Safehouse"), SHOP("Store"), GUN("Gun shop"), CLOTHES("Clothes"), MISSION("Mission"),
		BAR("Bar"), POLICE("Police"), HOSPITAL("Hospital"), CAR("Vehicle"), TARGET("Target"), GAS("Gas station");

		public final String label;

		Type(String label) { this.label = label; }

		public String texture() { return "blip_" + name().toLowerCase(java.util.Locale.ROOT); }

		public static Type parse(String s) {
			try { return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT)); } catch (Exception e) { return MISSION; }
		}
	}

	/** edge: if true the blip stays pinned to the minimap edge when out of view (within {@link #EDGE_RANGE}). */
	public record Blip(String id, Type type, double x, double z, String label, boolean edge) {}

	/** Max distance (blocks) at which off-screen blips stick to the minimap edge. */
	public static final double EDGE_RANGE = 600;

	private static final Map<String, Blip> BLIPS = new LinkedHashMap<>();
	private static double[] waypoint;

	public static synchronized void put(String id, Type type, double x, double z) {
		put(id, type, x, z, type.label, true);
	}

	public static synchronized void put(String id, Type type, double x, double z, String label, boolean edge) {
		BLIPS.put(id, new Blip(id, type, x, z, label, edge));
	}

	public static synchronized void remove(String id) { BLIPS.remove(id); }

	public static synchronized void clear() { BLIPS.clear(); }

	public static synchronized Collection<Blip> all() { return Collections.unmodifiableList(new java.util.ArrayList<>(BLIPS.values())); }

	/** GPS target (x, z) or null. */
	public static synchronized double[] waypoint() { return waypoint == null ? null : waypoint.clone(); }

	public static synchronized void setWaypoint(double x, double z) {
		waypoint = new double[] {x, z};
		Route.invalidate();
	}

	public static synchronized void clearWaypoint() {
		waypoint = null;
		Route.invalidate();
	}
}
