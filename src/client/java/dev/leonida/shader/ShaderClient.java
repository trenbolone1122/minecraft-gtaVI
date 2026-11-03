package dev.leonida.shader;

import dev.leonida.shader.early.ShaderGlGuard;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client side of the shader module: reports Iris and pack status once per session. */
public final class ShaderClient {
	public static final Logger LOG = LoggerFactory.getLogger("leonida/shader");
	private static boolean reported;

	private ShaderClient() {}

	public static void init() {
		LOG.info("Leonida Shaders OpenGL guard: {}", ShaderGlGuard.lastResult());
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
			ShaderGlGuard.markStartedClean();
			DhDefaults.applyOnce();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			if (reported) return;
			reported = true;
			LOG.info("Leonida Shaders: iris={} packInUse={} pack='{}' installer: {}", ShaderApi.irisLoaded(),
					ShaderApi.shaderPackInUse(), ShaderApi.currentPackName(), ShaderInstaller.lastResult);
			if (ShaderApi.irisLoaded() && ShaderGlGuard.vulkanFallbackActive()) {
				mc.execute(() -> {
					if (mc.player != null) mc.player.sendSystemMessage(Component.translatable("leonida.shader.no_opengl"));
				});
			}
		});
	}
}
