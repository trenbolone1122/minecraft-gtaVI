package dev.leonida.world.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.leonida.core.api.LandmarkGenerator;
import dev.leonida.core.api.RoadGenerator;
import dev.leonida.world.data.CwFeatures;
import dev.leonida.world.data.CwTerrain;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds Leonida 1:1 from community-world-v1: terrain, beaches and depth-banded water here, then
 * roads (RoadGenerator), buildings (BuildingGenerator), landmarks (LandmarkGenerator), and trees in
 * the decoration step.
 */
public final class LeonidaChunkGenerator extends ChunkGenerator {
	public static final MapCodec<LeonidaChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
	).apply(i, i.stable(LeonidaChunkGenerator::new)));

	public static final int MIN_Y = -64, HEIGHT = 768;
	static final int SEA = CwTerrain.SEA;
	private static final Logger LOG = LoggerFactory.getLogger("leonida-world");
	/** Default spawn: Vice Beach at 1500 Ocean Drive. */
	public static final int SPAWN_X = 2075, SPAWN_Z = -970;

	private final CwTerrain terrain = CwTerrain.get();
	private volatile int[] spawn;

	public LeonidaChunkGenerator(BiomeSource biomeSource) {
		super(biomeSource);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

	/** Spawn column: the nearest dry beach sand to Ocean Drive / Vice Beach, clear of buildings. */
	public int[] spawnPoint() {
		int[] s = spawn;
		if (s != null) return s;
		int[] best = {SPAWN_X, SPAWN_Z};
		outer:
		for (int r = 0; r < 300; r++)
			for (int dx = -r; dx <= r; dx++)
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					int x = SPAWN_X + dx, z = SPAWN_Z + dz;
					if (terrain.cls(x, z) == CwTerrain.SAND && !terrain.nearWater(x, z, 6) && CwFeatures.get().buildingAt(x, z) == null) {
						best = new int[] {x, z};
						break outer;
					}
				}
		spawn = best;
		return best;
	}

	@Override
	public ChunkPos getOrigin(RandomState randomState) {
		int[] s = spawnPoint();
		return new ChunkPos(s[0] >> 4, s[1] >> 4);
	}

	@Override
	public int getSpawnHeight(LevelHeightAccessor level) {
		int[] s = spawnPoint();
		return terrain.landY(s[0], s[1]) + 1;
	}

	// ------------------------------------------------------------------------------------------------ terrain

	/** Natural column top-down: block at Y (above bottom fill); null means stone. */
	static BlockState surfaceBlock(CwTerrain t, int c, int x, int z, int depthBelowTop) {
		if (CwTerrain.isWater(c)) {
			int kind = c - CwTerrain.WATER;
			if (kind == CwTerrain.W_SWAMP) return WorldBlocks.MUD;
			if (kind == CwTerrain.W_CHANNEL && depthBelowTop == 0 && Hash.h01(x, z, 7) < 0.3) return WorldBlocks.MUD;
			return depthBelowTop < 3 ? WorldBlocks.seabedSand() : null;
		}
		switch (c) {
			case CwTerrain.SAND:
				if (depthBelowTop == 0 && t.nearWater(x, z, 2)) return WorldBlocks.wetSand();
				return depthBelowTop < 4 ? WorldBlocks.beachSand() : null;
			case CwTerrain.PAVING:
				return depthBelowTop == 0 ? WorldBlocks.paving(x, z) : depthBelowTop < 3 ? WorldBlocks.DIRT : null;
			case CwTerrain.DIRT:
				return depthBelowTop == 0 ? WorldBlocks.COARSE_DIRT : depthBelowTop < 3 ? WorldBlocks.DIRT : null;
			default:
				return depthBelowTop == 0 ? WorldBlocks.GRASS : depthBelowTop < 3 ? WorldBlocks.DIRT : null;
		}
	}

	@Override
	public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
			StructureManager structureManager, BiomeManager biomeManager, @Nullable WorldGenRegion region, Set<Holder<Biome>> possibleBiomes) {
		long t0 = System.nanoTime();
		ChunkPos cp = chunk.getPos();
		int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		// Direct section writes (no per-block locking); heightmaps updated once per column at the top.
		for (int lx = 0; lx < 16; lx++)
			for (int lz = 0; lz < 16; lz++) {
				int x = x0 + lx, z = z0 + lz;
				int c = terrain.clsSmooth(x, z);
				int top = terrain.groundY(x, z);
				LevelChunkSection sec = null;
				int secIdx = -1;
				BlockState topState = WorldBlocks.STONE;
				for (int y = MIN_Y; y <= top; y++) {
					BlockState st = y == MIN_Y ? WorldBlocks.BEDROCK : top - y > 4 ? null : surfaceBlock(terrain, c, x, z, top - y);
					if (st == null) st = WorldBlocks.STONE;
					int si = chunk.getSectionIndex(y);
					if (si != secIdx) {
						secIdx = si;
						sec = chunk.getSection(si);
					}
					sec.setBlockState(lx, y & 15, lz, st, false);
					topState = st;
				}
				ocean.update(lx, top, lz, topState);
				surface.update(lx, top, lz, topState);
				if (CwTerrain.isWater(c)) {
					int wy = terrain.waterSurfaceY(x, z);
					for (int y = top + 1; y <= wy; y++) {
						int si = chunk.getSectionIndex(y);
						if (si != secIdx) {
							secIdx = si;
							sec = chunk.getSection(si);
						}
						sec.setBlockState(lx, y & 15, lz, WorldBlocks.WATER, false);
					}
					if (wy > top) surface.update(lx, wy, lz, WorldBlocks.WATER);
				}
			}
		RandomSource rnd = RandomSource.create(((long) cp.x() * 341873128712L) ^ ((long) cp.z() * 132897987541L) ^ 0x4C454F4EL);
		long t1 = System.nanoTime();
		GenStats.add(GenStats.TERRAIN, t1 - t0);
		try {
			RoadGenerator.get().generate(chunk, terrain);
		} catch (RuntimeException e) {
			LOG.error("RoadGenerator failed at chunk {}", cp, e);
		}
		long t2 = System.nanoTime();
		GenStats.add(GenStats.ROADS, t2 - t1);
		Buildings.place(terrain, chunk, rnd);
		long t3 = System.nanoTime();
		GenStats.add(GenStats.BUILDINGS, t3 - t2);
		for (LandmarkGenerator g : LandmarkGenerator.all()) {
			try {
				g.generate(chunk, terrain);
			} catch (RuntimeException e) {
				LOG.error("LandmarkGenerator {} failed at chunk {}", g.getClass().getName(), cp, e);
			}
		}
		GenStats.add(GenStats.LANDMARKS, System.nanoTime() - t3);
		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
		long t0 = System.nanoTime();
		Decoration.decorate(terrain, level, chunk);
		GenStats.add(GenStats.DECORATION, System.nanoTime() - t0);
	}

	@Override
	public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager,
			ChunkAccess centerChunk, StructureTemplateManager templates, ResourceKey<Level> level) {
		// no vanilla structures in Leonida
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {}

	@Override
	public int getGenDepth() { return HEIGHT; }

	@Override
	public int getSeaLevel() { return SEA + 1; }

	@Override
	public int getMinY() { return MIN_Y; }

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
		boolean floorOnly = type == Heightmap.Types.OCEAN_FLOOR_WG || type == Heightmap.Types.OCEAN_FLOOR;
		int g = terrain.groundY(x, z);
		int top = floorOnly ? g : Math.max(g, terrain.waterY(x, z));
		if (!floorOnly) {
			int deck = RoadGenerator.get().deckY(x, z);
			if (deck != Integer.MIN_VALUE) top = Math.max(top, deck);
		}
		return top + 1;
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
		int c = terrain.cls(x, z);
		int top = terrain.groundY(x, z);
		int wy = terrain.waterY(x, z);
		BlockState[] states = new BlockState[Math.max(top, wy) + 1 - MIN_Y];
		for (int y = MIN_Y; y < MIN_Y + states.length; y++) {
			BlockState st;
			if (y == MIN_Y) st = WorldBlocks.BEDROCK;
			else if (y <= top) {
				st = surfaceBlock(terrain, c, x, z, top - y);
				if (st == null) st = WorldBlocks.STONE;
			} else st = WorldBlocks.WATER;
			states[y - MIN_Y] = st;
		}
		return new NoiseColumn(MIN_Y, states);
	}

	@Override
	public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feet, SamplerContext ctx) {
		int x = feet.getX(), z = feet.getZ();
		result.add("Leonida: class " + terrain.cls(x, z) + " depth " + terrain.depth(x, z) + " ground " + terrain.groundY(x, z)
				+ String.format(" h %.1f m", terrain.landHeightM(x, z)));
	}
}
