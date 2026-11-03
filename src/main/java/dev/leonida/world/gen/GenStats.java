package dev.leonida.world.gen;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Per-stage chunk generation timings, logged every 256 chunks (average ms per chunk). */
public final class GenStats {
	private static final Logger LOG = LoggerFactory.getLogger("leonida/world");
	public static final int TERRAIN = 0, ROADS = 1, BUILDINGS = 2, LANDMARKS = 3, DECORATION = 4;
	private static final String[] NAMES = {"terrain", "roads", "buildings", "landmarks", "decoration"};
	private static final LongAdder[] NANOS = new LongAdder[NAMES.length];
	private static final LongAdder[] COUNT = new LongAdder[NAMES.length];
	private static final AtomicLong CHUNKS = new AtomicLong();
	static {
		for (int i = 0; i < NAMES.length; i++) {
			NANOS[i] = new LongAdder();
			COUNT[i] = new LongAdder();
		}
	}

	private GenStats() {}

	public static void add(int stage, long nanos) {
		NANOS[stage].add(nanos);
		COUNT[stage].increment();
		if (stage == DECORATION && CHUNKS.incrementAndGet() % 256 == 0) {
			StringBuilder sb = new StringBuilder("chunk gen after ").append(CHUNKS.get()).append(" chunks, avg ms:");
			for (int i = 0; i < NAMES.length; i++) {
				long c = Math.max(1, COUNT[i].sum());
				sb.append(' ').append(NAMES[i]).append('=').append(String.format("%.2f", NANOS[i].sum() / 1e6 / c));
			}
			LOG.info(sb.toString());
		}
	}

	/** Snapshot for tests: average ms per stage. */
	public static double[] averages() {
		double[] a = new double[NAMES.length];
		for (int i = 0; i < a.length; i++) a[i] = NANOS[i].sum() / 1e6 / Math.max(1, COUNT[i].sum());
		return a;
	}
}
