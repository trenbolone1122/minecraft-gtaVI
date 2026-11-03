package dev.leonida.shader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * First launch defaults for the bundled Distant Horizons, sized for a 4 GB GTX 1650 on a 4 core CPU: LOD radius
 * 128 chunks, LOW quality preset, LOW_IMPACT thread preset, overdraw prevention 0.9. Written once through DH's own config entries (the
 * normal user setting path, saved to DH's config file), never as API overrides, so players can change them in
 * DH's options screen and the change sticks. Reflection keeps Leonida free of a compile dependency on DH.
 */
public final class DhDefaults {
	private static final Logger LOG = LoggerFactory.getLogger("leonida/shader");
	public static final int LOD_RADIUS_CHUNKS = 128;
	private static final String CFG = "com.seibel.distanthorizons.core.config.Config$";
	private static String status = "not run";

	private DhDefaults() {}

	public static String status() {
		return status;
	}

	public static boolean dhLoaded() {
		return FabricLoader.getInstance().isModLoaded("distanthorizons");
	}

	/** Applies the defaults once per game directory (marker in config/). Safe to call when DH is absent. */
	public static void applyOnce() {
		if (!dhLoaded()) {
			status = "Distant Horizons not loaded";
			return;
		}
		Path marker = FabricLoader.getInstance().getConfigDir().resolve("leonida-dh-defaults-v3.properties");
		if (Files.exists(marker)) {
			status = "already applied earlier";
			return;
		}
		try {
			// safe default while LOD artefacts near towers are being fixed: LOD rendering starts OFF; players can
			// switch it on in the Distant Horizons options (the other defaults below then apply)
			set("Client", "quickEnableRendering", Boolean.FALSE);
			set("Client", "qualityPresetSetting", enumValue("com.seibel.distanthorizons.api.enums.config.quickOptions.EDhApiQualityPreset", "LOW"));
			set("Client", "threadPresetSetting", enumValue("com.seibel.distanthorizons.api.enums.config.quickOptions.EDhApiThreadPreset", "LOW_IMPACT"));
			// radius last: the quality preset does not own it, but keep it out of any preset listener's way
			set("Client$Advanced$Graphics$Quality", "lodChunkRenderDistanceRadius", Integer.valueOf(LOD_RADIUS_CHUNKS));
			// LODs start at 90 percent of the vanilla render distance instead of auto, so they never cover loaded chunks
			set("Client$Advanced$Graphics$Culling", "overdrawPrevention", Float.valueOf(0.9f));
			set("Client$Advanced$Graphics$Culling", "reduceOverdrawWithFastMovement", Boolean.FALSE);
			Files.writeString(marker, "# Leonida wrote first launch Distant Horizons defaults once; delete this file to write them again.\napplied=true\n");
			status = "applied: rendering OFF, quality LOW, threads LOW_IMPACT, radius " + LOD_RADIUS_CHUNKS;
			LOG.info("Distant Horizons defaults {}", status);
		} catch (ReflectiveOperationException | IOException | RuntimeException e) {
			status = "failed: " + e;
			LOG.warn("Could not write Distant Horizons defaults; DH keeps its own", e);
		}
	}

	/** Current DH values for test logs. */
	public static String describe() {
		if (!dhLoaded()) return "DH absent";
		try {
			return "rendering=" + get("Client", "quickEnableRendering") + " quality=" + get("Client", "qualityPresetSetting") + " threads=" + get("Client", "threadPresetSetting")
				+ " radius=" + get("Client$Advanced$Graphics$Quality", "lodChunkRenderDistanceRadius")
				+ " overdraw=" + get("Client$Advanced$Graphics$Culling", "overdrawPrevention")
				+ " plan=" + get("Common$WorldGenerator", "generatorPlan") + " mode=" + get("Common$WorldGenerator", "chunkGeneratorMode");
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "unreadable: " + e;
		}
	}

	private static Object entry(String cls, String field) throws ReflectiveOperationException {
		Field f = Class.forName(CFG + cls).getField(field);
		return f.get(null);
	}

	private static void set(String cls, String field, Object value) throws ReflectiveOperationException {
		Object e = entry(cls, field);
		Method m = e.getClass().getMethod("set", Object.class);
		m.invoke(e, value);
	}

	private static Object get(String cls, String field) throws ReflectiveOperationException {
		Object e = entry(cls, field);
		return e.getClass().getMethod("get").invoke(e);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object enumValue(String cls, String name) throws ClassNotFoundException {
		return Enum.valueOf((Class) Class.forName(cls), name);
	}
}
