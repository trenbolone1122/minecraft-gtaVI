package dev.leonida.shader.early;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

/**
 * Not a real language adapter: Fabric Loader creates adapters before Mixin starts, which is the only point
 * early enough to steer Iris (see ShaderGlGuard). No entrypoint uses this adapter.
 */
public final class ShaderEarlyAdapter implements LanguageAdapter {
	public ShaderEarlyAdapter() {
		try {
			ShaderGlGuard.run();
		} catch (Throwable t) {
			System.out.println("[Leonida Shaders] OpenGL guard failed, settings unchanged: " + t);
		}
	}

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		throw new LanguageAdapterException("leonida_shader_guard is not an entrypoint adapter");
	}
}
