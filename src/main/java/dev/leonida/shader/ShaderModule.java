package dev.leonida.shader;

/**
 * Common side of the shader module. The Leonida Shaders pack and its installer are client only
 * (see dev.leonida.shader.ShaderClient and ShaderPreLaunch); nothing runs on a dedicated server.
 */
public final class ShaderModule {
	public static final String PACK_NAME = "Leonida Shaders";

	private ShaderModule() {}

	public static void init() {
		// nothing on the common side yet
	}
}
