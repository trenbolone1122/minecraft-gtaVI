package dev.leonida.world.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.leonida.core.api.BuildingGenerator;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Buildings, infill lots, trees, landmarks and the road graph from community-world-v1 (tools/worldgen/cw_features_pack.py). */
public final class CwFeatures {
	public static final int CELL = 64;

	private static volatile CwFeatures instance;

	public static CwFeatures get() {
		CwFeatures f = instance;
		if (f == null) {
			synchronized (CwFeatures.class) {
				if (instance == null) instance = new CwFeatures();
				f = instance;
			}
		}
		return f;
	}

	public record Building(long id, BuildingGenerator.Kind kind, int style, int height, double groundM, String name,
			List<int[]> outer, List<List<int[]>> holes, int minX, int minZ, int maxX, int maxZ) {
		public boolean infill() { return id >= 1_000_000; }

		public boolean contains(double x, double z) {
			if (x < minX || x > maxX || z < minZ || z > maxZ) return false;
			if (!Poly.contains(outer, x, z)) return false;
			for (List<int[]> h : holes) if (Poly.contains(h, x, z)) return false;
			return true;
		}

		private static final java.util.Map<Long, Boolean> CLAIMED = new java.util.concurrent.ConcurrentHashMap<>();

		/**
		 * True when any column of this footprint lies in a landmark claim (then the building is skipped whole): a
		 * bounding box test through the registry first, then the polygon column by column. Cached per building.
		 */
		public boolean landmarkClaimed() {
			if (!dev.leonida.core.api.LandmarkGenerator.anyMayClaimBox(minX, minZ, maxX, maxZ)) return false;
			return CLAIMED.computeIfAbsent(id, k -> {
				for (int x = minX; x <= maxX; x++)
					for (int z = minZ; z <= maxZ; z++)
						if (contains(x + 0.5, z + 0.5) && dev.leonida.core.api.LandmarkGenerator.anyClaims(x, z)) return true;
				return false;
			});
		}

		public int centerX() { return (minX + maxX) / 2; }
		public int centerZ() { return (minZ + maxZ) / 2; }
	}

	/** Tree kinds: 0 royal palm, 1 mangrove, 2 cypress, 3 pine, 4 coconut palm (coastal restoration). */
	public record Tree(int x, int z, double groundM, int height, int kind) {}

	public record Landmark(String name, int x, int z, String address, List<String> tags) {}

	public record Junction(int x, int z, int arms) {}

	public record Road(int a, int b, int kind, double width, float[] xz) {}

	public final List<Building> buildings = new ArrayList<>();
	public final List<Tree> trees = new ArrayList<>();
	public final List<Landmark> landmarks = new ArrayList<>();
	public final List<Junction> junctions = new ArrayList<>();
	public final List<Road> roads = new ArrayList<>();
	private final Long2ObjectOpenHashMap<int[]> buildingCells = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<int[]> treeCells = new Long2ObjectOpenHashMap<>();
	private final Long2ObjectOpenHashMap<int[]> landmarkCells = new Long2ObjectOpenHashMap<>();

	private static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL); }

	private static DataInputStream gz(String name) throws IOException {
		InputStream in = CwFeatures.class.getResourceAsStream("/data/leonida/world/" + name);
		if (in == null) throw new IOException("missing " + name);
		return new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(in, 65536)));
	}

	/** Unnamed footprints thinner than this on average (2 * area / perimeter) are trace artifacts, not buildings. */
	private static final double MIN_MEAN_WIDTH = 2.5;

	private static double meanWidth(List<int[]> ring) {
		double area2 = 0, per = 0;
		for (int i = 0, n = ring.size(); i < n; i++) {
			int[] a = ring.get((i + n - 1) % n), b = ring.get(i);
			area2 += (double) a[0] * b[1] - (double) b[0] * a[1];
			per += Math.hypot(b[0] - a[0], b[1] - a[1]);
		}
		return per == 0 ? 0 : Math.abs(area2) / per;
	}

	private CwFeatures() {
		BuildingGenerator.Kind[] kinds = BuildingGenerator.Kind.values();
		try (DataInputStream in = gz("buildings.bin.gz")) {
			int n = in.readInt(), slivers = 0;
			for (int i = 0; i < n; i++) {
				long id = in.readInt();
				int k = in.readByte(), st = in.readByte(), h = in.readShort(), zdm = in.readShort();
				byte[] nm = new byte[in.readUnsignedByte()];
				in.readFully(nm);
				List<int[]> outer = ring(in);
				int nh = in.readByte();
				List<List<int[]>> holes = new ArrayList<>();
				for (int j = 0; j < nh; j++) holes.add(ring(in));
				if (nm.length == 0 && meanWidth(outer) < MIN_MEAN_WIDTH) { slivers++; continue; }
				int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
				for (int[] p : outer) { x0 = Math.min(x0, p[0]); z0 = Math.min(z0, p[1]); x1 = Math.max(x1, p[0]); z1 = Math.max(z1, p[1]); }
				buildings.add(new Building(id, kinds[k], st, h, zdm / 10.0, new String(nm, StandardCharsets.UTF_8),
						Collections.unmodifiableList(outer), Collections.unmodifiableList(holes), x0, z0, x1, z1));
			}
			if (slivers > 0) org.slf4j.LoggerFactory.getLogger("leonida/world").info("skipped {} sliver footprints (traced lines, mean width under {} m)", slivers, MIN_MEAN_WIDTH);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		Long2ObjectOpenHashMap<IntArrayList> tmp = new Long2ObjectOpenHashMap<>();
		for (int i = 0; i < buildings.size(); i++) {
			Building b = buildings.get(i);
			for (int cx = Math.floorDiv(b.minX, CELL); cx <= Math.floorDiv(b.maxX, CELL); cx++)
				for (int cz = Math.floorDiv(b.minZ, CELL); cz <= Math.floorDiv(b.maxZ, CELL); cz++)
					tmp.computeIfAbsent(key(cx, cz), q -> new IntArrayList()).add(i);
		}
		tmp.forEach((k, v) -> buildingCells.put((long) k, v.toIntArray()));

		try (DataInputStream in = gz("trees.bin.gz")) {
			int n = in.readInt();
			for (int i = 0; i < n; i++) trees.add(new Tree(in.readInt(), in.readInt(), in.readShort() / 10.0, in.readByte(), in.readByte()));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		tmp.clear();
		for (int i = 0; i < trees.size(); i++) {
			Tree t = trees.get(i);
			tmp.computeIfAbsent(key(t.x() >> 4, t.z() >> 4), q -> new IntArrayList()).add(i);
		}
		tmp.forEach((k, v) -> treeCells.put((long) k, v.toIntArray()));

		try (InputStream in = CwFeatures.class.getResourceAsStream("/data/leonida/world/landmarks.json")) {
			JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
			for (JsonElement e : arr) {
				JsonObject o = e.getAsJsonObject();
				List<String> tags = new ArrayList<>();
				for (JsonElement t : o.getAsJsonArray("t")) tags.add(t.getAsString());
				String name = o.get("n").isJsonNull() ? "" : o.get("n").getAsString();
				landmarks.add(new Landmark(name, o.get("x").getAsInt(), o.get("z").getAsInt(), o.get("a").getAsString(), List.copyOf(tags)));
			}
		} catch (IOException | RuntimeException e) {
			throw new IllegalStateException("landmarks.json", e);
		}
		tmp.clear();
		for (int i = 0; i < landmarks.size(); i++) {
			Landmark l = landmarks.get(i);
			tmp.computeIfAbsent(key(Math.floorDiv(l.x(), 256), Math.floorDiv(l.z(), 256)), q -> new IntArrayList()).add(i);
		}
		tmp.forEach((k, v) -> landmarkCells.put((long) k, v.toIntArray()));

		try (DataInputStream in = gz("roads.bin.gz")) {
			int nj = in.readInt();
			for (int i = 0; i < nj; i++) junctions.add(new Junction(in.readInt(), in.readInt(), in.readByte()));
			int ns = in.readInt();
			for (int i = 0; i < ns; i++) {
				int a = in.readInt(), b = in.readInt(), k = in.readByte();
				double w = in.readShort() / 10.0;
				int np = in.readShort();
				float[] xz = new float[np * 2];
				for (int j = 0; j < np * 2; j++) xz[j] = in.readFloat();
				roads.add(new Road(a, b, k, w, xz));
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static List<int[]> ring(DataInputStream in) throws IOException {
		int n = in.readShort();
		List<int[]> r = new ArrayList<>(n);
		for (int i = 0; i < n; i++) r.add(new int[] {in.readInt(), in.readInt()});
		return r;
	}

	/** Building indices whose bbox touches the 64-block cell containing (x, z). */
	public int[] buildingsNear(int x, int z) {
		int[] a = buildingCells.get(key(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)));
		return a == null ? new int[0] : a;
	}

	/** Buildings whose bbox touches the chunk. */
	public List<Building> buildingsInChunk(int cx, int cz) {
		int x0 = cx << 4, z0 = cz << 4;
		int[] a = buildingCells.get(key(Math.floorDiv(x0, CELL), Math.floorDiv(z0, CELL)));
		List<Building> out = new ArrayList<>();
		if (a == null) return out;
		for (int i : a) {
			Building b = buildings.get(i);
			if (b.maxX >= x0 && b.minX <= x0 + 15 && b.maxZ >= z0 && b.minZ <= z0 + 15) out.add(b);
		}
		return out;
	}

	public Building buildingAt(int x, int z) {
		for (int i : buildingsNear(x, z)) {
			Building b = buildings.get(i);
			if (b.contains(x + 0.5, z + 0.5)) return b;
		}
		return null;
	}

	public List<Tree> treesInChunk(int cx, int cz) {
		int[] a = treeCells.get(key(cx, cz));
		if (a == null) return List.of();
		List<Tree> out = new ArrayList<>(a.length);
		for (int i : a) out.add(trees.get(i));
		return out;
	}

	/** Landmarks within roughly r blocks (bucketed by 256). */
	public List<Landmark> landmarksNear(int x, int z, int r) {
		List<Landmark> out = new ArrayList<>();
		for (int cx = Math.floorDiv(x - r, 256); cx <= Math.floorDiv(x + r, 256); cx++)
			for (int cz = Math.floorDiv(z - r, 256); cz <= Math.floorDiv(z + r, 256); cz++) {
				int[] a = landmarkCells.get(key(cx, cz));
				if (a != null) for (int i : a) out.add(landmarks.get(i));
			}
		return out;
	}
}
