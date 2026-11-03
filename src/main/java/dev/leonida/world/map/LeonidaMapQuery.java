package dev.leonida.world.map;

import dev.leonida.core.api.BuildingGenerator;
import dev.leonida.core.api.MapQuery;
import dev.leonida.core.api.RoadGenerator;
import dev.leonida.world.data.CwFeatures;
import dev.leonida.world.data.CwTerrain;
import dev.leonida.world.data.RoadNet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** The real MapQuery over community-world-v1 terrain, roads, buildings and landmarks. */
public final class LeonidaMapQuery implements MapQuery {
	private final CwTerrain t;
	private final CwFeatures f;
	private volatile Map<BuildingGenerator.Kind, List<int[]>> targets;

	public LeonidaMapQuery() {
		this.t = CwTerrain.get();
		this.f = CwFeatures.get();
	}

	private RoadNet roads() { return RoadNet.get(); }

	@Override
	public Surface surfaceAt(int x, int z) {
		RoadNet.Hit h = roads().nearest(x + 0.5, z + 0.5, 30);
		if (h != null) {
			CwFeatures.Road r = f.roads.get(h.seg());
			if (h.dist() <= r.width() / 2) return r.kind() == 1 ? Surface.HIGHWAY : Surface.ROAD;
			if (r.kind() == 0 && h.dist() <= r.width() / 2 + 3 && !t.water(x, z)) return Surface.SIDEWALK;
		}
		if (f.buildingAt(x, z) != null) return Surface.BUILDING;
		int c = t.cls(x, z);
		if (CwTerrain.isWater(c)) {
			return switch (c - CwTerrain.WATER) {
				case CwTerrain.W_CHANNEL, CwTerrain.W_LAKE -> Surface.WATER;
				case CwTerrain.W_SWAMP -> Surface.SWAMP;
				default -> Surface.OCEAN;
			};
		}
		return switch (c) {
			case CwTerrain.SAND -> Surface.BEACH;
			case CwTerrain.PAVING -> Surface.SIDEWALK;
			case CwTerrain.GRASS -> t.cls(x + 16, z) == CwTerrain.PAVING || t.cls(x - 16, z) == CwTerrain.PAVING ? Surface.PARK : Surface.LAND;
			default -> Surface.LAND;
		};
	}

	@Override
	public int groundY(int x, int z) {
		int deck = RoadGenerator.get().deckY(x, z);
		if (deck != Integer.MIN_VALUE) return deck;
		CwFeatures.Building b = f.buildingAt(x, z);
		if (b != null) return Math.max(CwTerrain.SEA + 2, CwTerrain.SEA + (int) Math.round(b.groundM()) + 1) - 1;
		if (t.water(x, z)) return t.waterSurfaceY(x, z);
		return t.landY(x, z);
	}

	private static int lerp(int a, int b, double k) {
		int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
		int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * k);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	@Override
	public int mapColor(int x, int z) {
		int c = t.cls(x, z);
		// roads first so bridges and elevated segments over water draw on top of the water
		RoadNet.Hit h = roads().nearest(x + 0.5, z + 0.5, 30);
		if (h != null) {
			CwFeatures.Road r = f.roads.get(h.seg());
			if (h.dist() <= r.width() / 2)
				return CwTerrain.isWater(c) ? (r.kind() == 1 ? 0xFFE6D990 : 0xFFE2E2DC) : r.kind() == 1 ? 0xFFF3E7A6 : 0xFFF6F6F2;
		}
		if (CwTerrain.isWater(c)) {
			int kind = c - CwTerrain.WATER;
			double d = Math.min(1, t.depth(x, z) / 20.0);
			return switch (kind) {
				case CwTerrain.W_SHALLOW -> 0xFF4FD8D0;
				case CwTerrain.W_BAY -> lerp(0x6FA8A8, 0x4F8C90, d);
				case CwTerrain.W_CHANNEL -> 0xFF2F7F80;
				case CwTerrain.W_SWAMP -> 0xFF6E7A44;
				case CwTerrain.W_LAKE -> 0xFF3F8FB0;
				default -> lerp(0x3A86C8, 0x1E4F9A, d);
			};
		}
		if (f.buildingAt(x, z) != null) return 0xFFA9ADB2;
		return switch (c) {
			case CwTerrain.SAND -> 0xFFF4ECD2;
			case CwTerrain.PAVING -> 0xFFCDD0D0;
			case CwTerrain.DIRT -> 0xFFB59A6E;
			default -> {
				double hm = Math.min(1, t.landHeightM(x, z) / 400.0);
				yield lerp(0x9CCB78, 0x6E9356, hm);
			}
		};
	}

	@Override
	public List<BlockPos> route(BlockPos from, BlockPos to) {
		List<int[]> pts = roads().route(from.getX(), from.getZ(), to.getX(), to.getZ());
		List<BlockPos> out = new ArrayList<>(pts.size());
		for (int[] p : pts) out.add(new BlockPos(p[0], groundY(p[0], p[1]) + 1, p[1]));
		return out;
	}

	@Override
	public String placeName(int x, int z) {
		CwFeatures.Landmark named = null, area = null;
		double nd = 45 * 45, ad = 900 * 900;
		for (CwFeatures.Landmark l : f.landmarksNear(x, z, 900)) {
			double d = (double) (l.x() - x) * (l.x() - x) + (double) (l.z() - z) * (l.z() - z);
			if (d < nd && !l.name().isEmpty() && !l.name().matches("^\\d.*")) { nd = d; named = l; }
			if (d < ad && district(l.address()) != null) { ad = d; area = l; }
		}
		if (named != null) return named.name();
		if (area != null) return district(area.address());
		if (x < t.minX() || x > t.maxX() || z < t.minZ() || z > t.maxZ()) return null;
		return t.water(x, z) ? "Leonida Waters" : "Leonida";
	}

	/** "street, neighbourhood, city" -> neighbourhood (or city). */
	static String district(String address) {
		if (address == null || address.isEmpty()) return null;
		String[] p = address.split(",");
		for (int i = Math.min(1, p.length - 1); i < p.length; i++) {
			String s = p[i].trim();
			if (!s.isEmpty() && !s.equals("?")) return s;
		}
		return null;
	}

	private Map<BuildingGenerator.Kind, List<int[]>> targets() {
		Map<BuildingGenerator.Kind, List<int[]>> m = targets;
		if (m != null) return m;
		m = new EnumMap<>(BuildingGenerator.Kind.class);
		for (CwFeatures.Building b : f.buildings) {
			if (b.infill() && b.kind() != BuildingGenerator.Kind.SHOP) continue;
			m.computeIfAbsent(b.kind(), k -> new ArrayList<>()).add(new int[] {b.centerX(), b.centerZ()});
		}
		for (CwFeatures.Landmark l : f.landmarks) {
			BuildingGenerator.Kind k = null;
			if (l.tags().contains("#police")) k = BuildingGenerator.Kind.POLICE;
			else if (l.tags().contains("#hospital")) k = BuildingGenerator.Kind.HOSPITAL;
			else if (l.tags().contains("#gas")) k = BuildingGenerator.Kind.GAS_STATION;
			else if (l.tags().contains("#supermarket") || l.tags().contains("#clothes") || l.tags().contains("#guns")) k = BuildingGenerator.Kind.SHOP;
			if (k != null) m.computeIfAbsent(k, q -> new ArrayList<>()).add(new int[] {l.x(), l.z()});
		}
		targets = m;
		return m;
	}

	@Override
	public BlockPos nearest(BuildingGenerator.Kind kind, int x, int z) {
		List<int[]> l = targets().get(kind);
		if (l == null || l.isEmpty()) return null;
		int[] best = null;
		long bd = Long.MAX_VALUE;
		for (int[] p : l) {
			long d = (long) (p[0] - x) * (p[0] - x) + (long) (p[1] - z) * (p[1] - z);
			if (d < bd) { bd = d; best = p; }
		}
		// step out of the footprint toward the nearest road to find a door-side spot
		int ex = best[0], ez = best[1];
		RoadNet.Hit h = roads().nearest(ex, ez, 200);
		if (h != null) {
			double dx = h.px() - ex, dz = h.pz() - ez, len = Math.max(1e-6, Math.hypot(dx, dz));
			for (int s = 0; s < (int) len; s++) {
				int px = (int) Math.floor(ex + dx / len * s), pz = (int) Math.floor(ez + dz / len * s);
				if (f.buildingAt(px, pz) == null && !t.water(px, pz)) { ex = px; ez = pz; break; }
			}
		}
		return new BlockPos(ex, groundY(ex, ez) + 1, ez);
	}
}
