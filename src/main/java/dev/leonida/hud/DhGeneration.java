package dev.leonida.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Distant Horizons distant generation follows the Leonida "Distant Horizons" setting (config/leonida.json,
 * default off). While off, DH's world generator mode is PRE_EXISTING_ONLY: it still builds LODs from chunks that
 * exist, but never asks our generator for new chunks in the background (its request ticker used to keep the worldgen
 * threads busy even with LOD rendering off). Works on the integrated and the dedicated server.
 */
public final class DhGeneration {
	private DhGeneration() {}

	private static final Logger LOG = LoggerFactory.getLogger("leonida/dh");
	private static final String CFG = "com.seibel.distanthorizons.core.config.Config$Common$WorldGenerator";
	private static final String MODE = "com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode";

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(s -> set(enabledInConfig()));
	}

	/** The saved Leonida setting (false when the file or field is missing). */
	public static boolean enabledInConfig() {
		Path p = FabricLoader.getInstance().getConfigDir().resolve("leonida.json");
		if (!Files.exists(p)) return false;
		try (Reader r = Files.newBufferedReader(p)) {
			JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
			return o.has("distantHorizons") && o.get("distantHorizons").getAsBoolean();
		} catch (Exception e) {
			return false;
		}
	}

	/** Turns DH distant generation on (FEATURES, DH's default) or off (PRE_EXISTING_ONLY). No-op without DH. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void set(boolean on) {
		if (!FabricLoader.getInstance().isModLoaded("distanthorizons")) return;
		try {
			Object entry = Class.forName(CFG).getField("chunkGeneratorMode").get(null);
			Object value = Enum.valueOf((Class<Enum>) Class.forName(MODE), on ? "FEATURES" : "PRE_EXISTING_ONLY");
			entry.getClass().getMethod("set", Object.class).invoke(entry, value);
			LOG.info("Distant Horizons distant generation {} (chunkGeneratorMode={})", on ? "ON" : "OFF", current());
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOG.warn("Could not set Distant Horizons generation mode", e);
		}
	}

	/** Current DH generator mode name, or null without DH. */
	public static String current() {
		if (!FabricLoader.getInstance().isModLoaded("distanthorizons")) return null;
		try {
			Object entry = Class.forName(CFG).getField("chunkGeneratorMode").get(null);
			return String.valueOf(entry.getClass().getMethod("get").invoke(entry));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
