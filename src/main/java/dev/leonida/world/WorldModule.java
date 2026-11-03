package dev.leonida.world;

import dev.leonida.core.api.MapQuery;
import dev.leonida.world.gen.LeonidaBiomeSource;
import dev.leonida.world.gen.LeonidaChunkGenerator;
import dev.leonida.world.map.LeonidaMapQuery;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Server and common setup for the world module. Owned by the world agent. */
public final class WorldModule {
	private WorldModule() {}

	public static final Identifier ID = Identifier.fromNamespaceAndPath("leonida", "world/leonida");
	/** The Leonida dimension (also used as the overworld by the Leonida world preset). */
	public static final ResourceKey<Level> LEONIDA = ResourceKey.create(Registries.DIMENSION, ID);

	public static void init() {
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, ID, LeonidaChunkGenerator.CODEC);
		Registry.register(BuiltInRegistries.BIOME_SOURCE, ID, LeonidaBiomeSource.CODEC);
		MapQuery.install(new LeonidaMapQuery());
	}
}
