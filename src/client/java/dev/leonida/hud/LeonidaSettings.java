package dev.leonida.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.leonida.fx.FxConfig;
import dev.leonida.gameplay.npc.PedestrianSpawner;
import dev.leonida.gameplay.police.PoliceDispatch;
import dev.leonida.shader.ShaderModule;
import dev.leonida.vehicle.ai.TrafficManager;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One place for Leonida performance and quality settings (config/leonida.json). {@link #apply()} pushes every value
 * into the owning system: traffic and pedestrian density, police cap (integrated server statics, same JVM in
 * single player), fx quality (config/leonida-fx.json), minimap, Iris shader profile (shaderpacks/Leonida Shaders.txt
 * plus an Iris reload), Distant Horizons (DH config entries) and the experimental building cache.
 */
public final class LeonidaSettings {
	private static final Logger LOG = LoggerFactory.getLogger("leonida/settings");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static LeonidaSettings instance;

	public enum Level { LOW, MEDIUM, HIGH }

	public Level preset = Level.MEDIUM;
	/** Percent 0..100. 50 = the module defaults. */
	public int trafficDensity = 50;
	public int pedDensity = 50;
	public int maxPolice = 12;
	public FxConfig.Quality effects = FxConfig.Quality.MEDIUM;
	public boolean miningDebris = true;
	public boolean minimap = true;
	/** Minimap redraws per second. */
	public int minimapRate = 30;
	public Level shaderProfile = Level.MEDIUM;
	public boolean distantHorizons = false;
	public int dhRadius = 128;
	public boolean fasterBuildings = false;

	public static LeonidaSettings get() {
		if (instance == null) load();
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("leonida.json");
	}

	public static void load() {
		LeonidaSettings s = null;
		try {
			if (Files.exists(path())) try (Reader r = Files.newBufferedReader(path())) { s = GSON.fromJson(r, LeonidaSettings.class); }
		} catch (Exception e) {
			LOG.warn("Could not read leonida.json, using defaults", e);
		}
		if (s == null) s = new LeonidaSettings();
		if (s.preset == null) s.preset = Level.MEDIUM;
		if (s.effects == null) s.effects = FxConfig.Quality.MEDIUM;
		if (s.shaderProfile == null) s.shaderProfile = Level.MEDIUM;
		instance = s;
	}

	public void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path())) { GSON.toJson(this, w); }
		} catch (Exception e) {
			LOG.warn("Could not save leonida.json", e);
		}
	}

	/** Sets every control from a preset. */
	public void applyPreset(Level p) {
		preset = p;
		switch (p) {
			case LOW -> { trafficDensity = 25; pedDensity = 25; maxPolice = 6; effects = FxConfig.Quality.LOW; minimapRate = 15; shaderProfile = Level.LOW; distantHorizons = false; dhRadius = 64; }
			case MEDIUM -> { trafficDensity = 50; pedDensity = 50; maxPolice = 12; effects = FxConfig.Quality.MEDIUM; minimapRate = 30; shaderProfile = Level.MEDIUM; distantHorizons = false; dhRadius = 128; }
			case HIGH -> { trafficDensity = 100; pedDensity = 100; maxPolice = 20; effects = FxConfig.Quality.HIGH; minimapRate = 60; shaderProfile = Level.HIGH; dhRadius = 256; }
		}
	}

	// ---- apply to the owning systems ----

	private transient Level appliedShader;
	private transient Boolean appliedDh;
	private transient int appliedDhRadius = -1;

	/** Client start: push saved values into the systems, but leave Iris and DH alone (they keep their own saved state). */
	public static void init() {
		boolean exists = Files.exists(path());
		LeonidaSettings s = get();
		s.appliedShader = s.shaderProfile;
		s.appliedDh = s.distantHorizons;
		s.appliedDhRadius = s.dhRadius;
		if (exists) s.apply();
	}

	/** Pushes all values live and saves. Cheap when nothing changed for the slower targets (Iris, DH). */
	public void apply() {
		TrafficManager.density = Math.round(trafficDensity / 100f * 20);
		PedestrianSpawner.density = Math.round(pedDensity / 100f * 28);
		PoliceDispatch.maxUnits = maxPolice;
		FxConfig fx = FxConfig.get();
		if (fx.quality != effects || fx.miningDebris != miningDebris) {
			fx.quality = effects;
			fx.miningDebris = miningDebris;
			fx.save();
		}
		setBuildCache(fasterBuildings);
		if (appliedShader != shaderProfile) { appliedShader = shaderProfile; applyShaderProfile(shaderProfile); }
		if (appliedDh == null || appliedDh != distantHorizons || appliedDhRadius != dhRadius) {
			appliedDh = distantHorizons;
			appliedDhRadius = dhRadius;
			applyDh(distantHorizons, dhRadius);
		}
		save();
	}

	public static boolean buildCacheEnabled() {
		try {
			Field f = Class.forName("dev.leonida.build.BuildCache").getDeclaredField("ENABLED");
			f.setAccessible(true);
			return f.getBoolean(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	private static void setBuildCache(boolean on) {
		try {
			Field f = Class.forName("dev.leonida.build.BuildCache").getDeclaredField("ENABLED");
			f.setAccessible(true);
			if (f.getBoolean(null) != on) f.setBoolean(null, on);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.warn("Could not set the building cache flag", e);
		}
	}

	/** Iris keeps per-pack option values in shaderpacks/&lt;pack&gt;.txt; we write the profile's values and reload Iris. */
	public static Path shaderOptionsFile() {
		return FabricLoader.getInstance().getGameDir().resolve("shaderpacks").resolve(ShaderModule.PACK_NAME + ".txt");
	}

	private static void applyShaderProfile(Level level) {
		try {
			Properties pack = new Properties();
			try (InputStream in = LeonidaSettings.class.getResourceAsStream("/assets/leonida/shader/pack/shaders/shaders.properties")) {
				if (in == null) return;
				pack.load(in);
			}
			String line = pack.getProperty("profile." + level.name());
			if (line == null) return;
			Map<String, String> opts = new LinkedHashMap<>();
			Path file = shaderOptionsFile();
			if (Files.exists(file)) {
				Properties old = new Properties();
				try (Reader r = Files.newBufferedReader(file)) { old.load(r); }
				for (String k : old.stringPropertyNames()) opts.put(k, old.getProperty(k));
			}
			for (String tok : line.trim().split("\\s+")) {
				if (tok.isEmpty()) continue;
				if (tok.startsWith("!")) opts.put(tok.substring(1), "false");
				else if (tok.contains(":")) opts.put(tok.substring(0, tok.indexOf(':')), tok.substring(tok.indexOf(':') + 1));
				else opts.put(tok, "true");
			}
			StringBuilder sb = new StringBuilder("#Leonida Settings shader profile " + level.name() + "\n");
			for (Map.Entry<String, String> e : opts.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
			Files.createDirectories(file.getParent());
			Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
			if (FabricLoader.getInstance().isModLoaded("iris")) {
				try {
					Method reload = Class.forName("net.irisshaders.iris.Iris").getMethod("reload");
					reload.invoke(null);
				} catch (ReflectiveOperationException | RuntimeException e) {
					LOG.info("Iris reload not available ({}); the shader profile applies on the next pack load", e.toString());
				}
			}
		} catch (Exception e) {
			LOG.warn("Could not write the shader profile", e);
		}
	}

	private static final String DH_CFG = "com.seibel.distanthorizons.core.config.Config$";

	private static void dhSet(String cls, String field, Object value) throws ReflectiveOperationException {
		Object e = Class.forName(DH_CFG + cls).getField(field).get(null);
		e.getClass().getMethod("set", Object.class).invoke(e, value);
	}

	private static Object dhGet(String cls, String field) throws ReflectiveOperationException {
		Object e = Class.forName(DH_CFG + cls).getField(field).get(null);
		return e.getClass().getMethod("get").invoke(e);
	}

	private static void applyDh(boolean on, int radius) {
		if (!FabricLoader.getInstance().isModLoaded("distanthorizons")) return;
		// rendering and distant generation go on and off together
		DhGeneration.set(on);
		try {
			dhSet("Client", "quickEnableRendering", on);
			dhSet("Client$Advanced$Graphics$Quality", "lodChunkRenderDistanceRadius", radius);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.warn("Could not change Distant Horizons settings", e);
		}
	}

	/** Distant Horizons rendering flag as DH sees it, or null if DH is absent. */
	public static Boolean dhRendering() {
		if (!FabricLoader.getInstance().isModLoaded("distanthorizons")) return null;
		try {
			return (Boolean) dhGet("Client", "quickEnableRendering");
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	public static Integer dhRadiusNow() {
		if (!FabricLoader.getInstance().isModLoaded("distanthorizons")) return null;
		try {
			return (Integer) dhGet("Client$Advanced$Graphics$Quality", "lodChunkRenderDistanceRadius");
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
