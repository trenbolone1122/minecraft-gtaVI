package dev.leonida.shader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Copies the bundled pack (assets/leonida/shader/pack inside our jar) to .minecraft/shaderpacks/Leonida Shaders and,
 * once, selects it in config/iris.properties when the player has no other pack chosen. Runs from preLaunch because
 * Iris reads its config during Minecraft startup, before any client entrypoint.
 */
public final class ShaderInstaller {
	public static final String RESOURCE_ROOT = "assets/leonida/shader/pack";

	/** What the last run did, for logs and the game test. */
	public static volatile String lastResult = "not run";

	private ShaderInstaller() {}

	public static void run() {
		FabricLoader fl = FabricLoader.getInstance();
		Path gameDir = fl.getGameDir();
		Path configDir = fl.getConfigDir();
		ShaderConfig cfg = ShaderConfig.load(configDir);
		StringBuilder res = new StringBuilder();
		try {
			Optional<Path> src = fl.getModContainer("leonida").flatMap(c -> c.findPath(RESOURCE_ROOT));
			if (src.isEmpty()) {
				lastResult = "pack resources missing from the jar";
				ShaderClient.LOG.warn("Leonida Shaders: {}", lastResult);
				return;
			}
			Path target = gameDir.resolve("shaderpacks").resolve(ShaderModule.PACK_NAME);
			if (cfg.autoInstall) {
				String hash = hash(src.get());
				boolean present = Files.isRegularFile(target.resolve("shaders/shaders.properties"));
				if (!present || !hash.equals(cfg.installedHash)) {
					copyTree(src.get(), target);
					cfg.installedHash = hash;
					res.append("installed ").append(target).append("; ");
				} else {
					res.append("already installed; ");
				}
			} else {
				res.append("autoInstall off; ");
			}
			if (cfg.autoSelect && !cfg.selectedOnce && selectionAllowedInThisRun() && Files.isDirectory(target)) {
				res.append(selectInIris(configDir.resolve("iris.properties"), cfg));
			} else {
				res.append("selection skipped");
			}
			cfg.save();
		} catch (Exception e) {
			res.append("failed: ").append(e);
			ShaderClient.LOG.warn("Leonida Shaders install failed", e);
		}
		lastResult = res.toString();
		ShaderClient.LOG.info("Leonida Shaders: {}", lastResult);
	}

	/** Other modules' game tests stay on the vanilla look; production and the shader test select the pack. */
	private static boolean selectionAllowedInThisRun() {
		String t = System.getProperty("leonida.test");
		return t == null || t.isEmpty() || t.equals("shader");
	}

	private static String selectInIris(Path irisProps, ShaderConfig cfg) throws IOException {
		Properties p = new Properties();
		if (Files.isRegularFile(irisProps)) {
			try (Reader r = Files.newBufferedReader(irisProps)) {
				p.load(r);
			}
		}
		String current = p.getProperty("shaderPack", "").trim();
		if (!current.isEmpty() && !current.equals(ShaderModule.PACK_NAME)) {
			cfg.selectedOnce = true; // the player has their own pack: never override it
			return "kept the player's pack '" + current + "'";
		}
		p.setProperty("shaderPack", ShaderModule.PACK_NAME);
		p.setProperty("enableShaders", "true");
		Files.createDirectories(irisProps.getParent());
		try (Writer w = Files.newBufferedWriter(irisProps)) {
			p.store(w, "This file stores configuration options for Iris");
		}
		cfg.selectedOnce = true;
		return "selected in Iris";
	}

	private static List<Path> files(Path root) throws IOException {
		List<Path> out = new ArrayList<>();
		try (Stream<Path> s = Files.walk(root)) {
			s.filter(Files::isRegularFile).forEach(out::add);
		}
		out.sort((a, b) -> root.relativize(a).toString().compareTo(root.relativize(b).toString()));
		return out;
	}

	static String hash(Path root) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		for (Path f : files(root)) {
			md.update(root.relativize(f).toString().replace('\\', '/').getBytes());
			try (InputStream in = Files.newInputStream(f)) {
				md.update(in.readAllBytes());
			}
		}
		return HexFormat.of().formatHex(md.digest()).substring(0, 16);
	}

	private static void copyTree(Path src, Path target) throws IOException {
		if (Files.exists(target)) deleteTree(target);
		for (Path f : files(src)) {
			String rel = src.relativize(f).toString().replace('\\', '/');
			Path out = target.resolve(rel);
			Files.createDirectories(out.getParent());
			try (InputStream in = Files.newInputStream(f)) {
				Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private static void deleteTree(Path dir) throws IOException {
		Files.walkFileTree(dir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
				Files.delete(d);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
