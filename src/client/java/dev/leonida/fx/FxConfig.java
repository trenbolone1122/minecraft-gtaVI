package dev.leonida.fx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client settings for the fx module, stored in {@code config/leonida-fx.json} and changed in game with
 * {@code /leonidafx quality <off|low|medium|high>} and {@code /leonidafx grade <0..100>}.
 */
public final class FxConfig {
	public enum Quality {
		/** No colour grade, sky or fog changes; small debris budget. For very weak PCs. */
		OFF(150, 3, false, false),
		/** Colour grade and haze, no bloom; medium debris budget. */
		LOW(300, 6, true, false),
		/** Grade, haze and bloom glow on lights and neon. Default. */
		MEDIUM(700, 10, true, true),
		/** Everything, largest debris budget. */
		HIGH(1500, 16, true, true);

		public final int maxPieces;
		public final int maxRagdolls;
		public final boolean grade;
		public final boolean bloom;

		Quality(int maxPieces, int maxRagdolls, boolean grade, boolean bloom) {
			this.maxPieces = maxPieces;
			this.maxRagdolls = maxRagdolls;
			this.grade = grade;
			this.bloom = bloom;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static FxConfig instance;

	public Quality quality = Quality.MEDIUM;
	/** 0..1, how strong the colour grade, sky and fog changes are. */
	public float gradeStrength = 1.0F;
	/** Small chunks fly off blocks broken by hand. */
	public boolean miningDebris = true;

	public static FxConfig get() {
		if (instance == null) load();
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("leonida-fx.json");
	}

	public static void load() {
		FxConfig c = null;
		try {
			Path p = path();
			if (Files.exists(p)) {
				try (Reader r = Files.newBufferedReader(p)) {
					c = GSON.fromJson(r, FxConfig.class);
				}
			}
		} catch (Exception e) {
			FxModule.LOG.warn("Could not read leonida-fx.json, using defaults", e);
		}
		if (c == null) c = new FxConfig();
		if (c.quality == null) c.quality = Quality.MEDIUM;
		c.gradeStrength = Math.max(0.0F, Math.min(1.0F, c.gradeStrength));
		instance = c;
	}

	public void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer w = Files.newBufferedWriter(path())) {
				GSON.toJson(this, w);
			}
		} catch (Exception e) {
			FxModule.LOG.warn("Could not save leonida-fx.json", e);
		}
	}
}
