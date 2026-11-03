package dev.leonida.build;

import dev.leonida.core.api.BuildingGenerator;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Writes one footprint's blocks into one chunk. Deterministic per footprint id; the RandomSource is ignored. */
final class LeonidaBuildingGenerator implements BuildingGenerator {
	private static final Logger LOG = LoggerFactory.getLogger("leonida-build");
	private static volatile int errors;

	@Override
	public void generate(ChunkAccess chunk, Footprint fp, RandomSource random) {
		try {
			if (BuildCache.generate(chunk, fp)) return;
			Site s = Site.create(new Canvas.ChunkCanvas(chunk), fp);
			if (s != null) Kinds.build(s);
		} catch (RuntimeException e) {
			if (errors++ < 5) LOG.error("[leonida-build] footprint {} ({}) failed in chunk {}", fp.id(), fp.kind(), chunk.getPos(), e);
		}
	}
}
