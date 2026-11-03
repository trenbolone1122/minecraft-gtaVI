package dev.leonida.build;

import dev.leonida.core.api.BuildingGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Generates each building once and replays per-chunk slices of the recorded block writes.
 *
 * The per-chunk path ran the whole building for every chunk it touches and dropped writes outside
 * the chunk. A recording runs the same code once on a canvas covering the whole bbox and keeps the
 * ordered writes per chunk. Replay is only used for a chunk when it provably gives the same blocks
 * as the per-chunk path: every read the building made must return what that chunk would have
 * returned (reads outside the chunk are air there, unwritten reads inside must be air in the chunk).
 * Otherwise the chunk falls back to the per-chunk path.
 */
final class BuildCache {
	private BuildCache() {}

	static final BlockState AIR = Blocks.AIR.defaultBlockState();

	static long ck(int cx, int cz) { return (long) cx << 32 | (cz & 0xFFFFFFFFL); }
	private static final int MAX_ENTRIES = 384;
	static volatile boolean ENABLED = Boolean.getBoolean("leonida.build.cache");

	/** Recorded building. Slices are keyed by chunk; positions are packed lx | lz << 4 | (y - minY) << 8, bit 31 = put-if-free. */
	static final class Recording {
		final long key;
		final int minY;
		boolean impure;
		final Map<Long, Slice> slices = new HashMap<>();
		/** Packed world positions (BlockPos.asLong) of reads that returned a non-air state. */
		final List<Long> nonAirReads = new ArrayList<>();
		/** Per chunk: positions read before the building wrote them (assumed air). */
		final Map<Long, List<Long>> assumedAir = new HashMap<>();
		final java.util.concurrent.atomic.AtomicInteger left = new java.util.concurrent.atomic.AtomicInteger();

		Recording(long key, int minY) { this.key = key; this.minY = minY; }
	}

	static final class Slice {
		int n;
		int[] pos = new int[64];
		BlockState[] st = new BlockState[64];
		final java.util.concurrent.atomic.AtomicBoolean claim = new java.util.concurrent.atomic.AtomicBoolean();

		void add(int p, BlockState s) {
			if (n == pos.length) { pos = java.util.Arrays.copyOf(pos, n * 2); st = java.util.Arrays.copyOf(st, n * 2); }
			pos[n] = p; st[n] = s; n++;
		}
	}

	/** Canvas over the whole bbox that records writes and checks reads. */
	static final class RecordCanvas extends Canvas {
		final Recording rec;
		/** Cell state written so far: a BlockState, or null when the last write was conditional. */
		private final Map<Long, BlockState> written = new HashMap<>();
		private final Set<Long> conditional = new HashSet<>();

		RecordCanvas(int minX, int minZ, int maxX, int maxZ, int minY, int maxY, Recording rec) {
			super(minX, minZ, maxX, maxZ, minY, maxY);
			this.rec = rec;
		}

		private Slice slice(int x, int z) {
			return rec.slices.computeIfAbsent(ck(x >> 4, z >> 4), k -> new Slice());
		}

		private int pack(int x, int y, int z) { return (x & 15) | (z & 15) << 4 | (y - rec.minY) << 8; }

		@Override
		void put(BlockPos p, BlockState s) {
			long k = p.asLong();
			written.put(k, s);
			conditional.remove(k);
			slice(p.getX(), p.getZ()).add(pack(p.getX(), p.getY(), p.getZ()), s);
		}

		@Override
		void putIfFree(BlockPos p, BlockState s) {
			long k = p.asLong();
			written.remove(k);
			conditional.add(k);
			slice(p.getX(), p.getZ()).add(pack(p.getX(), p.getY(), p.getZ()) | 0x80000000, s);
		}

		@Override
		BlockState read(BlockPos p) {
			long k = p.asLong();
			if (conditional.contains(k)) { rec.impure = true; return AIR; }
			BlockState w = written.get(k);
			if (w == null) {
				rec.assumedAir.computeIfAbsent(ck(p.getX() >> 4, p.getZ() >> 4), c -> new ArrayList<>()).add(k);
				return AIR;
			}
			if (w != AIR) rec.nonAirReads.add(k);
			return w;
		}
	}

	static final java.util.concurrent.atomic.LongAdder[] STAT = new java.util.concurrent.atomic.LongAdder[8];
	static { for (int i = 0; i < STAT.length; i++) STAT[i] = new java.util.concurrent.atomic.LongAdder(); }
	private static final String[] STAT_NAMES = {"calls", "replayed", "records", "recordMs", "impure", "nonAirOutside", "assumedAirFail", "reused"};
	private static final java.util.concurrent.atomic.AtomicLong CALLS = new java.util.concurrent.atomic.AtomicLong();

	static void stat(int i, long v) {
		STAT[i].add(v);
		if (i == 0 && CALLS.incrementAndGet() % 500 == 0) {
			StringBuilder sb = new StringBuilder("[leonida-build-cache]");
			for (int k = 0; k < STAT.length; k++) sb.append(' ').append(STAT_NAMES[k]).append('=').append(k == 3 ? STAT[k].sum() / 1000000 : STAT[k].sum());
			System.out.println(sb);
		}
	}


	/** Full identity of a footprint (ids are not guaranteed unique across split or overlapping lots). */
	private static long key(BuildingGenerator.Footprint fp) {
		long h = fp.id() * 0x9E3779B97F4A7C15L;
		h = h * 31 + fp.baseY(); h = h * 31 + fp.height(); h = h * 31 + fp.floors(); h = h * 31 + fp.style(); h = h * 31 + fp.kind().ordinal();
		h = h * 31 + java.util.Objects.hashCode(fp.landmark());
		for (int[] p : fp.polygon()) { h = h * 1000003 + p[0]; h = h * 1000003 + p[1]; }
		if (fp.holes() != null) for (List<int[]> hole : fp.holes()) for (int[] p : hole) { h = h * 1000003 + p[0]; h = h * 1000003 + p[1]; }
		return h;
	}

	private static final java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.CompletableFuture<Recording>> FUTURES = new java.util.concurrent.ConcurrentHashMap<>();

	/** Writes the footprint into the chunk. Returns false when the caller must use the per-chunk path. */
	static boolean generate(ChunkAccess chunk, BuildingGenerator.Footprint fp) {
		if (!ENABLED) return false;
		stat(0, 1);
		int[] bb = Site.bbox(fp);
		if (bb == null) return false;
		int minY = chunk.getMinY(), maxY = chunk.getMinY() + chunk.getHeight() - 1;
		long key = key(fp) * 31 + minY;
		java.util.concurrent.CompletableFuture<Recording> mine = new java.util.concurrent.CompletableFuture<>();
		java.util.concurrent.CompletableFuture<Recording> f = FUTURES.putIfAbsent(key, mine);
		Recording rec;
		if (f == null) {
			long t0 = System.nanoTime();
			Recording r;
			try { r = record(fp, bb, key, minY, maxY); } catch (Throwable t) { r = new Recording(key, minY); r.impure = true; }
			stat(2, 1); stat(3, System.nanoTime() - t0);
			mine.complete(r);
			rec = r;
			if (FUTURES.size() > MAX_ENTRIES) {
				for (Long k : FUTURES.keySet()) { if (FUTURES.size() <= MAX_ENTRIES / 2) break; if (k != key) FUTURES.remove(k); }
			}
		} else {
			rec = f.join();
		}
		if (rec.impure) { stat(4, 1); return false; }
		ChunkPos cp = chunk.getPos();
		long ck = ck(cp.x(), cp.z());
		int cx0 = cp.getMinBlockX(), cz0 = cp.getMinBlockZ();
		for (long r : rec.nonAirReads) {
			int x = BlockPos.getX(r), z = BlockPos.getZ(r);
			if (x >> 4 != cp.x() || z >> 4 != cp.z()) { stat(5, 1); return false; }
		}
		BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
		List<Long> air = rec.assumedAir.get(ck);
		if (air != null) for (long r : air) if (chunk.getBlockState(mp.set(r)) != AIR) { stat(6, 1); return false; }
		Slice sl = rec.slices.get(ck);
		if (sl != null) {
			if (!sl.claim.compareAndSet(false, true)) sl = null;
			else if (rec.left.decrementAndGet() <= 0) FUTURES.remove(key);
		}
		if (sl == null) {
			stat(7, 1);
			// slice already replayed once (chunk regenerated) or no writes here: fall back unless nothing to write
			return !rec.slices.containsKey(ck);
		}
		stat(1, 1);
		if (VERIFY) verify(chunk, fp, sl, minY, cx0, cz0);
		Canvas.ChunkCanvas cv = new Canvas.ChunkCanvas(chunk);
		for (int i = 0; i < sl.n; i++) {
			int p = sl.pos[i];
			mp.set(cx0 + (p & 15), ((p & 0x7FFFFFFF) >>> 8) + minY, cz0 + (p >> 4 & 15));
			if (p < 0) cv.putIfFree(mp, sl.st[i]);
			else cv.put(mp, sl.st[i]);
		}
		return true;
	}

	static final boolean VERIFY = Boolean.getBoolean("leonida.build.verify");

	/** Scratch overlay on a chunk: reads fall through to the chunk, writes stay in the overlay. */
	static final class Scratch extends Canvas {
		final ChunkAccess chunk;
		final Map<Long, BlockState> over = new HashMap<>();
		Scratch(ChunkAccess c) {
			super(c.getPos().getMinBlockX(), c.getPos().getMinBlockZ(), c.getPos().getMaxBlockX(), c.getPos().getMaxBlockZ(), c.getMinY(), c.getMinY() + c.getHeight() - 1);
			chunk = c;
		}
		@Override void put(BlockPos p, BlockState s) { over.put(p.asLong(), s); }
		@Override BlockState read(BlockPos p) { BlockState s = over.get(p.asLong()); return s != null ? s : chunk.getBlockState(p); }
	}

	private static void verify(ChunkAccess chunk, BuildingGenerator.Footprint fp, Slice sl, int minY, int cx0, int cz0) {
		Scratch a = new Scratch(chunk);
		Site s = Site.create(a, fp);
		if (s != null) try { Kinds.build(s); } catch (RuntimeException e) { System.out.println("[leonida-build-verify] original threw " + e); }
		Scratch b = new Scratch(chunk);
		BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
		for (int i = 0; i < sl.n; i++) {
			int p = sl.pos[i];
			mp.set(cx0 + (p & 15), ((p & 0x7FFFFFFF) >>> 8) + minY, cz0 + (p >> 4 & 15));
			if (p < 0) b.putIfFree(mp, sl.st[i]); else b.put(mp, sl.st[i]);
		}
		Set<Long> keys = new HashSet<>(a.over.keySet()); keys.addAll(b.over.keySet());
		int bad = 0; String first = null;
		for (long k : keys) {
			BlockState x = a.over.containsKey(k) ? a.over.get(k) : chunk.getBlockState(mp.set(k));
			BlockState y = b.over.containsKey(k) ? b.over.get(k) : chunk.getBlockState(mp.set(k));
			if (x != y) { bad++; if (first == null) first = BlockPos.of(k) + " orig=" + x + " cache=" + y; }
		}
		if (bad > 0) System.out.println("[leonida-build-verify] MISMATCH fp " + fp.id() + " " + fp.kind() + " style " + fp.style() + " chunk " + chunk.getPos() + " cells " + bad + " first " + first);
	}

	private static Recording record(BuildingGenerator.Footprint fp, int[] bb, long key, int minY, int maxY) {
		Recording rec = new Recording(key, minY);
		RecordCanvas cv = new RecordCanvas(bb[0] - 3, bb[1] - 3, bb[2] + 3, bb[3] + 3, minY, maxY, rec);
		try {
			Site s = Site.create(cv, fp);
			if (s == null) rec.impure = true;
			else Kinds.build(s);
		} catch (RuntimeException e) {
			rec.impure = true;
		}
		rec.left.set(rec.slices.size());
		return rec;
	}
}
