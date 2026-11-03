package dev.leonida.shader.early;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Keeps the bundled Iris from crashing the game when OpenGL cannot start.
 *
 * Minecraft 26.3 tries OpenGL first and silently falls back to Vulkan if the OpenGL window or context fails.
 * Iris only renders on OpenGL. It decides once, while Mixin starts, whether to load its OpenGL hooks, by
 * reading preferredGraphicsBackend from options.txt ("vulkan" means Vulkan-safe mode with shaders off).
 * If OpenGL then fails anyway, Iris calls OpenGL with no context and the JVM aborts.
 *
 * This runs from a language adapter, which Fabric Loader creates before Mixin starts. It checks OpenGL in a
 * short child JVM (same SDL request as Minecraft's GlBackend). If OpenGL fails it writes
 * preferredGraphicsBackend:"vulkan" so Iris and Minecraft both start in Vulkan mode, and remembers that it did
 * so; the next launch probes again and puts the player's own setting back once OpenGL works.
 * If OpenGL works and the player left the backend on Default, it pins OpenGL (Iris needs it).
 * State lives in config/leonida-shader-gl.properties; set glGuard=false there to switch all of this off.
 */
public final class ShaderGlGuard {
	public static final String KEY = "preferredGraphicsBackend";
	private static volatile String lastResult = "not run";

	private ShaderGlGuard() {}

	public static String lastResult() {
		return lastResult;
	}

	public static void run() {
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.getEnvironmentType() != EnvType.CLIENT) return;
		Path gameDir = loader.getGameDir();
		Path stateFile = loader.getConfigDir().resolve("leonida-shader-gl.properties");
		Properties st = load(stateFile);
		if (!Boolean.parseBoolean(st.getProperty("glGuard", "true"))) {
			lastResult = "disabled in config";
			return;
		}
		for (String a : loader.getLaunchArguments(false)) {
			if (a.startsWith("--graphicsBackend")) {
				lastResult = "backend forced by launch argument, not touched";
				return;
			}
		}
		Path options = gameDir.resolve("options.txt");
		String pref = readOption(options);
		boolean ours = Boolean.parseBoolean(st.getProperty("vulkanByGuard", "false"));
		if ("vulkan".equals(pref) && !ours) {
			lastResult = "player chose Vulkan, Iris runs in its Vulkan mode (no shaders)";
			return;
		}
		boolean cleanLast = Boolean.parseBoolean(st.getProperty("startedClean", "false"));
		boolean knownGood = Boolean.parseBoolean(st.getProperty("glOk", "false")) && cleanLast && !ours;

		Boolean glWorks;
		String detail;
		if (knownGood) {
			glWorks = Boolean.TRUE;
			detail = "known good from last clean start";
		} else {
			String[] r = probe(loader);
			glWorks = r[0] == null ? null : Boolean.valueOf(r[0]);
			detail = r[1];
		}

		st.setProperty("startedClean", "false");
		if (glWorks == null) {
			lastResult = "OpenGL check could not run (" + detail + "), settings unchanged";
		} else if (glWorks) {
			st.setProperty("glOk", "true");
			if (ours) {
				String back = st.getProperty("previousBackend", "default");
				writeOption(options, back.isEmpty() ? "opengl" : back.equals("default") ? "opengl" : back);
				st.setProperty("vulkanByGuard", "false");
				lastResult = "OpenGL works again, restored graphics backend";
			} else if (pref == null || "default".equals(pref)) {
				if (Files.isRegularFile(options)) writeOption(options, "opengl");
				lastResult = "OpenGL works (" + detail + "), backend OpenGL";
			} else {
				lastResult = "OpenGL works (" + detail + ")";
			}
		} else {
			st.setProperty("glOk", "false");
			if (!ours) st.setProperty("previousBackend", pref == null ? "default" : pref);
			st.setProperty("vulkanByGuard", "true");
			writeOption(options, "vulkan");
			lastResult = "OpenGL is not available (" + detail + "): starting on Vulkan, Iris shaders off";
		}
		save(stateFile, st);
		System.out.println("[Leonida Shaders] OpenGL guard: " + lastResult);
	}

	/** Called once the client has started, so a later crash makes the next launch probe again. */
	public static void markStartedClean() {
		FabricLoader loader = FabricLoader.getInstance();
		Path stateFile = loader.getConfigDir().resolve("leonida-shader-gl.properties");
		Properties st = load(stateFile);
		st.setProperty("startedClean", "true");
		save(stateFile, st);
	}

	public static boolean vulkanFallbackActive() {
		Properties st = load(FabricLoader.getInstance().getConfigDir().resolve("leonida-shader-gl.properties"));
		return Boolean.parseBoolean(st.getProperty("vulkanByGuard", "false"));
	}

	/** Returns {"true"/"false"/null, detail}. null means the check itself could not run. */
	private static String[] probe(FabricLoader loader) {
		try {
			Set<String> cp = new LinkedHashSet<>();
			for (String s : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
				if (!s.isEmpty()) cp.add(Path.of(s).toAbsolutePath().toString());
			}
			ModContainer self = loader.getModContainer("leonida").orElse(null);
			if (self != null) {
				for (Path p : self.getOrigin().getPaths()) cp.add(p.toAbsolutePath().toString());
			}
			String java = Path.of(System.getProperty("java.home"), "bin",
					System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "javaw.exe" : "java").toString();
			List<String> cmd = new ArrayList<>();
			cmd.add(java);
			cmd.add("-Xmx64m");
			cmd.add("-Xss1m");
			cmd.add("-XX:+IgnoreUnrecognizedVMOptions");
			cmd.add("--enable-native-access=ALL-UNNAMED");
			cmd.add("-XX:-CreateCoredumpOnCrash");
			Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
			cmd.add("-XX:ErrorFile=" + tmp.resolve("leonida-glprobe-crash.log"));
			if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) cmd.add("-XstartOnFirstThread");
			for (String k : System.getProperties().stringPropertyNames()) {
				if (k.startsWith("org.lwjgl.") || k.equals("java.library.path")) cmd.add("-D" + k + "=" + System.getProperty(k));
			}
			cmd.add("-cp");
			cmd.add(String.join(File.pathSeparator, cp));
			cmd.add(GlProbeMain.class.getName());
			ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true).directory(tmp.toFile());
			long t0 = System.nanoTime();
			Process p = pb.start();
			StringBuilder out = new StringBuilder();
			Thread reader = new Thread(() -> {
				try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while ((line = r.readLine()) != null) {
						synchronized (out) {
							String t = line.trim();
							if (t.startsWith("LEONIDA_GL_") || t.startsWith("probe ") || t.startsWith("SDL ")) out.append(t).append(' ');
						}
					}
				} catch (IOException ignored) {
					// process ended
				}
			}, "leonida-gl-probe");
			reader.setDaemon(true);
			reader.start();
			// a result line is enough: do not wait for a driver that hangs or crashes while the probe exits
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			while (!p.waitFor(50, TimeUnit.MILLISECONDS)) {
				boolean got;
				synchronized (out) {
					got = out.indexOf("LEONIDA_GL_") >= 0;
				}
				if (got) {
					p.waitFor(500, TimeUnit.MILLISECONDS);
					break;
				}
				if (System.nanoTime() > deadline) {
					p.destroyForcibly();
					return new String[] {null, "timed out"};
				}
			}
			if (p.isAlive()) p.destroyForcibly();
			reader.join(500);
			long ms = (System.nanoTime() - t0) / 1_000_000L;
			String msg;
			synchronized (out) {
				msg = out.toString().trim();
			}
			if (msg.contains("LEONIDA_GL_OK")) return new String[] {"true", "checked in " + ms + " ms"};
			int at = msg.indexOf("LEONIDA_GL_FAIL");
			if (at >= 0) return new String[] {"false", msg.substring(at + "LEONIDA_GL_FAIL".length()).trim()};
			return new String[] {null, "no result line: " + msg};
		} catch (IOException | RuntimeException e) {
			return new String[] {null, e.toString()};
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new String[] {null, "interrupted"};
		}
	}

	static String readOption(Path options) {
		if (!Files.isRegularFile(options)) return null;
		try {
			for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
				if (line.startsWith(KEY + ":")) {
					return line.substring(KEY.length() + 1).trim().replace("\"", "").toLowerCase(Locale.ROOT);
				}
			}
		} catch (IOException | RuntimeException e) {
			return null;
		}
		return null;
	}

	static void writeOption(Path options, String value) {
		try {
			List<String> lines = Files.isRegularFile(options)
					? new ArrayList<>(Files.readAllLines(options, StandardCharsets.UTF_8)) : new ArrayList<>();
			String entry = KEY + ":\"" + value + "\"";
			boolean found = false;
			for (int i = 0; i < lines.size(); i++) {
				if (lines.get(i).startsWith(KEY + ":")) {
					lines.set(i, entry);
					found = true;
				}
			}
			if (!found) lines.add(entry);
			Files.write(options, lines, StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			System.out.println("[Leonida Shaders] could not update options.txt: " + e);
		}
	}

	private static Properties load(Path f) {
		Properties p = new Properties();
		if (Files.isRegularFile(f)) {
			try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
				p.load(r);
			} catch (IOException | IllegalArgumentException ignored) {
				// defaults
			}
		}
		return p;
	}

	private static void save(Path f, Properties p) {
		try {
			Files.createDirectories(f.getParent());
			try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
				p.store(w, "Leonida Shaders OpenGL guard. glGuard=false turns it off. Other keys are state.");
			}
		} catch (IOException e) {
			System.out.println("[Leonida Shaders] could not write " + f + ": " + e);
		}
	}
}
