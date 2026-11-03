package dev.leonida.gameplay.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Render state for Leonida NPCs. Deliberately not an AvatarRenderState: the entity render
 * dispatcher sends every AvatarRenderState to the vanilla player renderer, which would ignore
 * our skins and poses.
 */
public class NpcRenderState extends HumanoidRenderState {
	public Identifier texture;
	public boolean slim;
	public int npcPose;
}
