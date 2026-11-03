package dev.leonida.road.api;

import dev.leonida.road.api.RoadLanes.Light;
import dev.leonida.road.net.RoadNetwork.Node;

/**
 * Traffic signal timing shared by the client (lit lamps) and the server (AI traffic): a 60 s cycle
 * per junction, main axis green 26 s, yellow 3 s, all red 1 s, then the cross axis the same. Each
 * junction has its own phase offset so the city does not switch in lockstep.
 */
public final class Signals {
	private Signals() {}

	public static final int CYCLE = 1200, GREEN = 520, YELLOW = 60, ALL_RED = 20;

	public static Light light(Node node, boolean mainAxis, long gameTime) {
		int offset = (int) ((node.id * 2654435761L) & 0x7fffffff) % CYCLE;
		int t = (int) ((gameTime + offset) % CYCLE);
		if (!mainAxis) t = (t + CYCLE / 2) % CYCLE;
		if (t < GREEN) return Light.GREEN;
		if (t < GREEN + YELLOW) return Light.YELLOW;
		return Light.RED;
	}
}
