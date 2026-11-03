package dev.leonida.vicecity.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * buildings.json footprints (map metres, y north) of the vicecity landmark sites, keyed by gtadb landmark id. Bundled
 * as data/leonida/vicecity/footprints.json (extracted from the community-world-v1 pack, GTADB/YANIS CC BY 4.0).
 */
public final class Footprints {
	private Footprints() {}

	private static Map<String, List<double[][]>> all;

	public static synchronized List<double[][]> get(String id) {
		if (all == null) all = load();
		return all.getOrDefault(id, List.of());
	}

	private static Map<String, List<double[][]>> load() {
		Map<String, List<double[][]>> m = new HashMap<>();
		try (InputStream in = Footprints.class.getResourceAsStream("/data/leonida/vicecity/footprints.json")) {
			if (in == null) return m;
			JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				List<double[][]> polys = new ArrayList<>();
				for (JsonElement pe : e.getValue().getAsJsonArray()) {
					JsonArray pa = pe.getAsJsonArray();
					double[][] p = new double[pa.size()][];
					for (int i = 0; i < pa.size(); i++) {
						JsonArray q = pa.get(i).getAsJsonArray();
						p[i] = new double[] {q.get(0).getAsDouble(), q.get(1).getAsDouble()};
					}
					polys.add(p);
				}
				m.put(e.getKey(), polys);
			}
		} catch (Exception ex) {
			dev.leonida.LeonidaMod.LOG.warn("vicecity: footprints.json not loaded", ex);
		}
		return m;
	}
}
