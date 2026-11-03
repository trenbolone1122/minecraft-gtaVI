package dev.leonida.aircraft.client.bb;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.leonida.LeonidaMod;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads every assets/&lt;ns&gt;/objects/**.bbmodel. The id drops the "objects/" prefix and ".bbmodel" suffix,
 * so objects/aircraft/prop_plane.bbmodel becomes leonida:aircraft/prop_plane. Adapted from Immersive Aircraft (GPL-3.0).
 */
public final class BBModelLoader extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {
	public static final Map<Identifier, BBModel> MODELS = new HashMap<>();

	@Override
	protected Map<Identifier, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
		Map<Identifier, JsonElement> map = new HashMap<>();
		for (Map.Entry<Identifier, Resource> e : manager.listResources("objects", id -> id.getPath().endsWith(".bbmodel")).entrySet()) {
			String path = e.getKey().getPath();
			Identifier id = Identifier.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(8, path.length() - 8));
			try (Reader r = e.getValue().openAsReader()) {
				map.put(id, JsonParser.parseReader(r));
			} catch (Exception ex) {
				LeonidaMod.LOG.error("Could not read model {}", e.getKey(), ex);
			}
		}
		return map;
	}

	@Override
	protected void apply(Map<Identifier, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
		MODELS.clear();
		map.forEach((id, json) -> {
			try {
				MODELS.put(id, new BBModel(json.getAsJsonObject(), id));
			} catch (Exception ex) {
				LeonidaMod.LOG.error("Could not parse model {}", id, ex);
			}
		});
		LeonidaMod.LOG.info("Leonida aircraft: loaded {} Blockbench models", MODELS.size());
	}
}
