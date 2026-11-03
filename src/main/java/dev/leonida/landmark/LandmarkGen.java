package dev.leonida.landmark;

import dev.leonida.core.api.GenTerrain;
import dev.leonida.core.api.LandmarkGenerator;
import dev.leonida.landmark.gen.Canvas;
import dev.leonida.landmark.gen.Site;
import dev.leonida.landmark.site.BriansMarina;
import dev.leonida.landmark.site.JasonHouse;
import dev.leonida.landmark.site.StarletMotel;
import java.util.List;
import net.minecraft.world.level.chunk.ChunkAccess;

/** The landmark module's generator: every authored site outside Vice City. */
public final class LandmarkGen implements LandmarkGenerator {
	public static final List<Site> SITES = List.of(new JasonHouse(), new BriansMarina(), new StarletMotel());

	public static Site byId(String id) {
		for (Site s : SITES) if (s.id.equals(id)) return s;
		return null;
	}

	@Override
	public void generate(ChunkAccess chunk, GenTerrain terrain) {
		Canvas c = null;
		for (Site s : SITES) {
			int cx = chunk.getPos().getMinBlockX(), cz = chunk.getPos().getMinBlockZ();
			if (s.maxX < cx || s.minX > cx + 15 || s.maxZ < cz || s.minZ > cz + 15) continue;
			if (c == null) c = new Canvas.ChunkCanvas(chunk);
			s.build(c, terrain);
		}
	}

	@Override
	public boolean mayClaimBox(int x0, int z0, int x1, int z1) {
		for (Site s : SITES) if (s.maxX >= x0 && s.minX <= x1 && s.maxZ >= z0 && s.minZ <= z1) return true;
		return false;
	}

	@Override
	public boolean claims(int x, int z) {
		for (Site s : SITES) if (s.claims(x, z)) return true;
		return false;
	}
}
