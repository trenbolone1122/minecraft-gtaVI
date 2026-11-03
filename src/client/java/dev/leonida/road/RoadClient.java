package dev.leonida.road;

import dev.leonida.road.client.StreetFurnitureRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;

/** Client-only setup for the road module. Owned by the road agent. */
public final class RoadClient {
	private RoadClient() {}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			if (Minecraft.getInstance().level == null) return;
			StreetFurnitureRenderer.submit(ctx.submitNodeCollector(), ctx.poseStack(), ctx.levelState().cameraRenderState.pos);
		});
	}
}
