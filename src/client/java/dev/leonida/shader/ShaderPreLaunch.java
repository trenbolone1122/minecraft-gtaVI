package dev.leonida.shader;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Installs and selects Leonida Shaders before Minecraft (and Iris) start. Files only, no game classes. */
public final class ShaderPreLaunch implements PreLaunchEntrypoint {
	@Override
	public void onPreLaunch() {
		if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
		ShaderInstaller.run();
	}
}
