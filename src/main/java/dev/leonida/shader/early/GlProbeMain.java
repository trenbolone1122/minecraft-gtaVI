package dev.leonida.shader.early;

/**
 * Runs in a short child JVM before the game starts. Asks SDL for the same OpenGL window and context that
 * Minecraft 26.3 requests (GlBackend: core 3.3, forward compatible, sRGB framebuffer) and exits 0 if it works.
 * LWJGL is used by reflection so this class has no compile dependency on client libraries.
 * The result is a line on stdout (LEONIDA_GL_OK or LEONIDA_GL_FAIL plus the SDL error), not the exit code:
 * some drivers crash while the JVM exits after a successful check (seen with NVIDIA EGL under VirtualGL).
 */
public final class GlProbeMain {
	private GlProbeMain() {}

	public static void main(String[] args) {
		try {
			Class<?> init = Class.forName("org.lwjgl.sdl.SDLInit");
			Class<?> video = Class.forName("org.lwjgl.sdl.SDLVideo");
			Class<?> error = Class.forName("org.lwjgl.sdl.SDLError");
			try {
				Class<?> hints = Class.forName("org.lwjgl.sdl.SDLHints");
				hints.getMethod("SDL_SetHint", CharSequence.class, CharSequence.class).invoke(null, "SDL_NO_SIGNAL_HANDLERS", "1");
			} catch (Throwable ignored) {
				// optional
			}
			if (!(Boolean) init.getMethod("SDL_Init", int.class).invoke(null, 0x20)) {
				System.out.println("SDL video init failed: " + error.getMethod("SDL_GetError").invoke(null));
				System.exit(4);
			}
			java.lang.reflect.Method attr = video.getMethod("SDL_GL_SetAttribute", int.class, int.class);
			attr.invoke(null, 17, 3);
			attr.invoke(null, 18, 3);
			attr.invoke(null, 20, 1);
			attr.invoke(null, 19, 2);
			attr.invoke(null, 22, 1);
			// 0x2 OPENGL | 0x8 HIDDEN
			long window = (Long) video.getMethod("SDL_CreateWindow", CharSequence.class, int.class, int.class, long.class)
					.invoke(null, "Leonida OpenGL check", 64, 64, 0x2L | 0x8L);
			if (window == 0L) {
				System.out.println("LEONIDA_GL_FAIL window: " + error.getMethod("SDL_GetError").invoke(null));
				init.getMethod("SDL_Quit").invoke(null);
				System.exit(3);
			}
			long ctx = (Long) video.getMethod("SDL_GL_CreateContext", long.class).invoke(null, window);
			if (ctx == 0L) {
				System.out.println("LEONIDA_GL_FAIL context: " + error.getMethod("SDL_GetError").invoke(null));
				video.getMethod("SDL_DestroyWindow", long.class).invoke(null, window);
				init.getMethod("SDL_Quit").invoke(null);
				System.exit(3);
			}
			video.getMethod("SDL_GL_DestroyContext", long.class).invoke(null, ctx);
			video.getMethod("SDL_DestroyWindow", long.class).invoke(null, window);
			init.getMethod("SDL_Quit").invoke(null);
			System.out.println("LEONIDA_GL_OK");
			System.out.flush();
			System.exit(0);
		} catch (Throwable t) {
			System.out.println("probe error: " + t);
			System.exit(4);
		}
	}
}
