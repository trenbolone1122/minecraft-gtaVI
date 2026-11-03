package dev.leonida.gameplay;

import dev.leonida.gameplay.client.NpcRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client-only setup for the gameplay module. Owned by the gameplay agent. */
public final class GameplayClient {
	private GameplayClient() {}

	public static void init() {
		EntityRendererRegistry.register(GameplayRegistry.PEDESTRIAN, NpcRenderer::new);
		EntityRendererRegistry.register(GameplayRegistry.CLERK, NpcRenderer::new);
		EntityRendererRegistry.register(GameplayRegistry.POLICE, NpcRenderer::new);
	}
}
