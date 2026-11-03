package dev.leonida.world.gen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.leonida.world.data.CwTerrain;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Biomes follow the baked terrain: five water looks by depth and setting, plus lake, beach, land and city. */
public final class LeonidaBiomeSource extends BiomeSource implements BiomeResolver {
	public static final MapCodec<LeonidaBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Biome.CODEC.fieldOf("shallows").forGetter(s -> s.b[0]),
			Biome.CODEC.fieldOf("bay").forGetter(s -> s.b[1]),
			Biome.CODEC.fieldOf("channel").forGetter(s -> s.b[2]),
			Biome.CODEC.fieldOf("deep").forGetter(s -> s.b[3]),
			Biome.CODEC.fieldOf("swamp").forGetter(s -> s.b[4]),
			Biome.CODEC.fieldOf("lake").forGetter(s -> s.b[5]),
			Biome.CODEC.fieldOf("ocean").forGetter(s -> s.b[6]),
			Biome.CODEC.fieldOf("beach").forGetter(s -> s.b[7]),
			Biome.CODEC.fieldOf("land").forGetter(s -> s.b[8]),
			Biome.CODEC.fieldOf("city").forGetter(s -> s.b[9])
	).apply(i, i.stable(LeonidaBiomeSource::new)));

	private final Holder<Biome>[] b;

	@SuppressWarnings("unchecked")
	public LeonidaBiomeSource(Holder<Biome> shallows, Holder<Biome> bay, Holder<Biome> channel, Holder<Biome> deep, Holder<Biome> swamp,
			Holder<Biome> lake, Holder<Biome> ocean, Holder<Biome> beach, Holder<Biome> land, Holder<Biome> city) {
		this.b = new Holder[] {shallows, bay, channel, deep, swamp, lake, ocean, beach, land, city};
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() { return CODEC; }

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() { return Stream.of(b); }

	@Override
	public BiomeResolver createResolver(Climate.Sampler sampler) { return this; }

	@Override
	public Holder<Biome> getNoiseBiome(int qx, int qy, int qz) {
		int c = CwTerrain.get().cls((qx << 2) + 2, (qz << 2) + 2);
		if (CwTerrain.isWater(c)) return b[c - CwTerrain.WATER];
		return switch (c) {
			case CwTerrain.SAND -> b[7];
			case CwTerrain.PAVING -> b[9];
			default -> b[8];
		};
	}
}
