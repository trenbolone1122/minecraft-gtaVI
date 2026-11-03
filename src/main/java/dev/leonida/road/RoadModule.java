package dev.leonida.road;

import dev.leonida.LeonidaMod;
import dev.leonida.core.api.RoadGenerator;
import dev.leonida.road.block.RoadBlocks;
import dev.leonida.road.gen.RoadGen;
import dev.leonida.road.net.RoadNetwork;

/** Server and common setup for the road module (roads, highways, bridges, markings, sidewalks and street furniture). Owned by the road agent. */
public final class RoadModule {
	private RoadModule() {}

	public static void init() {
		RoadBlocks.init();
		RoadGenerator.install(new RoadGen());
		// load the network off thread so the first chunk does not wait for it
		Thread t = new Thread(() -> {
			long t0 = System.nanoTime();
			RoadNetwork n = RoadNetwork.get();
			LeonidaMod.LOG.info("[road] network: {} segments, {} nodes in {} ms", n.segments.size(), n.nodes.size(), (System.nanoTime() - t0) / 1_000_000);
			dev.leonida.road.net.Props props = dev.leonida.road.net.Props.get();
			LeonidaMod.LOG.info("[road] street furniture: {} poles", props.poles.size());
		}, "leonida-road-load");
		t.setDaemon(true);
		t.start();
	}
}
