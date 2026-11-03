package dev.leonida.gameplay.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.leonida.character.CharacterSkins;
import dev.leonida.gameplay.npc.LeonidaNpc;
import dev.leonida.gameplay.npc.PoliceOfficer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/** Renders pedestrians, clerks and police with player-shaped skins from the character module. */
public class NpcRenderer extends HumanoidMobRenderer<LeonidaNpc, NpcRenderState, NpcModel> {
	private static final Map<Identifier, Boolean> EXISTS = new HashMap<>();
	private final NpcModel wide;
	private final NpcModel slimModel;

	public NpcRenderer(EntityRendererProvider.Context ctx) {
		this(ctx, new NpcModel(ctx.bakeLayer(ModelLayers.PLAYER)));
	}

	private NpcRenderer(EntityRendererProvider.Context ctx, NpcModel wide) {
		super(ctx, wide, 0.5f);
		this.wide = wide;
		this.slimModel = new NpcModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM));
	}

	@Override
	public NpcRenderState createRenderState() {
		return new NpcRenderState();
	}

	@Override
	public void extractRenderState(LeonidaNpc npc, NpcRenderState s, float partialTicks) {
		super.extractRenderState(npc, s, partialTicks);
		s.npcPose = npc.npcPose();
		int skin = Math.floorMod(npc.skin(), 1 << 20);
		CharacterSkins.PedSkin ps = switch (npc.skinFamily()) {
			case POLICE -> policeSkin(npc, skin);
			case SWAT -> CharacterSkins.swat(skin);
			case CLERK -> CharacterSkins.npc("clerk");
			default -> CharacterSkins.randomPedestrian(skin);
		};
		Identifier tex = ps.texture();
		boolean slim = ps.slim();
		if (!exists(tex)) {
			PlayerSkin def = DefaultPlayerSkin.get(new java.util.UUID(skin * 0x9E3779B97F4A7C15L, skin));
			tex = def.body().texturePath();
			slim = def.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
		}
		s.texture = tex;
		s.slim = slim;
		if (s.npcPose == LeonidaNpc.POSE_COWER) s.isCrouching = true;
		switch (s.npcPose) {
			case LeonidaNpc.POSE_FILMING, LeonidaNpc.POSE_PHONE_CALL -> s.rightArmPose = HumanoidModel.ArmPose.SPYGLASS;
			case LeonidaNpc.POSE_AIMING -> {
				s.rightArmPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
				s.leftArmPose = HumanoidModel.ArmPose.EMPTY;
			}
			default -> {}
		}
	}

	@Override
	public void submit(NpcRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.slim ? slimModel : wide;
		super.submit(state, pose, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(NpcRenderState state) {
		return state.texture != null ? state.texture : DefaultPlayerSkin.getDefaultTexture();
	}

	/** VCPD officers wear the Vice City uniforms, Leonard County Sheriff deputies the sheriff ones. */
	private static CharacterSkins.PedSkin policeSkin(LeonidaNpc npc, int seed) {
		boolean sheriff = npc instanceof PoliceOfficer o && o.agency() == PoliceOfficer.SHERIFF;
		java.util.List<CharacterSkins.PedSkin> pool = new java.util.ArrayList<>();
		for (CharacterSkins.PedSkin ps : CharacterSkins.POLICE)
			if (ps.texture().getPath().contains("sheriff") == sheriff) pool.add(ps);
		return pool.isEmpty() ? CharacterSkins.police(seed) : pool.get(Math.floorMod(seed * 31 + 7, pool.size()));
	}

	private static boolean exists(Identifier id) {
		return EXISTS.computeIfAbsent(id, k -> Minecraft.getInstance().getResourceManager().getResource(k).isPresent());
	}
}
