package dev.leonida.shader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * config/leonida-shader.properties. autoInstall copies the pack into .minecraft/shaderpacks on launch,
 * autoSelect picks it in Iris once (never again after the player changes packs or turns shaders off).
 */
public final class ShaderConfig {
	public boolean autoInstall = true;
	public boolean autoSelect = true;
	public boolean selectedOnce = false;
	public String installedHash = "";

	private final Path file;

	private ShaderConfig(Path file) {
		this.file = file;
	}

	public static ShaderConfig load(Path configDir) {
		ShaderConfig c = new ShaderConfig(configDir.resolve("leonida-shader.properties"));
		if (Files.isRegularFile(c.file)) {
			Properties p = new Properties();
			try (Reader r = Files.newBufferedReader(c.file)) {
				p.load(r);
				c.autoInstall = Boolean.parseBoolean(p.getProperty("autoInstall", "true"));
				c.autoSelect = Boolean.parseBoolean(p.getProperty("autoSelect", "true"));
				c.selectedOnce = Boolean.parseBoolean(p.getProperty("selectedOnce", "false"));
				c.installedHash = p.getProperty("installedHash", "");
			} catch (IOException | IllegalArgumentException e) {
				ShaderClient.LOG.warn("Could not read {}, using defaults", c.file, e);
			}
		}
		return c;
	}

	public void save() {
		Properties p = new Properties();
		p.setProperty("autoInstall", Boolean.toString(autoInstall));
		p.setProperty("autoSelect", Boolean.toString(autoSelect));
		p.setProperty("selectedOnce", Boolean.toString(selectedOnce));
		p.setProperty("installedHash", installedHash);
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file)) {
				p.store(w, "Leonida Shaders. autoInstall: copy the pack to shaderpacks on launch. autoSelect: pick it in Iris once if no other pack is chosen.");
			}
		} catch (IOException e) {
			ShaderClient.LOG.warn("Could not write {}", file, e);
		}
	}
}
