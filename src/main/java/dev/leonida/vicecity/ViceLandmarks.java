package dev.leonida.vicecity;

import dev.leonida.core.api.GenTerrain;
import dev.leonida.core.api.LandmarkGenerator;
import dev.leonida.vicecity.gen.Site;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;

/** All vicecity landmark sites behind one LandmarkGenerator, with a chunk index for fast claims. */
public final class ViceLandmarks implements LandmarkGenerator {
	public static final ViceLandmarks INSTANCE = new ViceLandmarks();

	private final Map<String, Site> sites = new LinkedHashMap<>();
	private final Long2ObjectOpenHashMap<List<Site>> index = new Long2ObjectOpenHashMap<>();

	private ViceLandmarks() {}

	public synchronized void add(Site s) {
		sites.put(s.id, s);
		for (int cx = s.minX >> 4; cx <= s.maxX >> 4; cx++)
			for (int cz = s.minZ >> 4; cz <= s.maxZ >> 4; cz++) index.computeIfAbsent(ChunkPos.pack(cx, cz), k -> new ArrayList<>()).add(s);
	}

	public Map<String, Site> sites() {
		return sites;
	}

	public Site site(String id) {
		Site s = sites.get(id);
		if (s != null) return s;
		for (Site t : sites.values()) if (t.name.equalsIgnoreCase(id) || t.name.replace(' ', '_').equalsIgnoreCase(id)) return t;
		return null;
	}

	@Override
	public void generate(ChunkAccess chunk, GenTerrain terrain) {
		ChunkPos cp = chunk.getPos();
		List<Site> l = index.get(ChunkPos.pack(cp.x(), cp.z()));
		if (l == null) return;
		for (Site s : l) {
			try {
				s.vox(terrain).apply(chunk);
			} catch (RuntimeException e) {
				dev.leonida.LeonidaMod.LOG.error("vicecity: landmark {} failed in chunk {}", s.id, cp, e);
			}
		}
	}

	@Override
	public boolean claims(int x, int z) {
		List<Site> l = index.get(ChunkPos.pack(x >> 4, z >> 4));
		if (l == null) return false;
		for (Site s : l) if (s.claims(x, z)) return true;
		return false;
	}
}
