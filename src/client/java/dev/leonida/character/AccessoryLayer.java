package dev.leonida.character;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.leonida.LeonidaMod;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** 3D extras that a flat skin cannot do: Lucia's high ponytail and the brim of Jason's backwards cap. */
public class AccessoryLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public static final ModelLayerLocation PONYTAIL = new ModelLayerLocation(LeonidaMod.id("character/ponytail"), "main");
	public static final ModelLayerLocation CAP_BACK = new ModelLayerLocation(LeonidaMod.id("character/cap_back"), "main");
	private static final Identifier PONYTAIL_TEX = LeonidaMod.id("textures/entity/character/accessory/ponytail.png");
	private static final Identifier CAP_TEX = LeonidaMod.id("textures/entity/character/accessory/cap_back.png");

	private final PlayerModel ponytail;
	private final PlayerModel capBack;

	public AccessoryLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet models) {
		super(parent);
		this.ponytail = new PlayerModel(models.bakeLayer(PONYTAIL), false);
		this.capBack = new PlayerModel(models.bakeLayer(CAP_BACK), false);
	}

	private static MeshDefinition emptyPlayerMesh() {
		MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
		mesh.getRoot().clearRecursively();
		return mesh;
	}

	public static LayerDefinition createPonytail() {
		MeshDefinition mesh = emptyPlayerMesh();
		PartDefinition head = mesh.getRoot().getChild("head");
		// hair swept up into a raised crown so the high ponytail reads from the front, then the scrunchie
		head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, -2.0F, -2.5F, 4.0F, 2.0F, 5.0F),
				PartPose.offset(0.0F, -7.9F, 2.0F));
		head.addOrReplaceChild("tie", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, -1.0F, -1.0F, 3.0F, 2.0F, 2.0F),
				PartPose.offsetAndRotation(0.0F, -9.1F, 4.6F, 0.6F, 0.0F, 0.0F));
		PartDefinition tail = head.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 0)
						.addBox(-1.5F, 0.0F, -1.0F, 3.0F, 5.0F, 2.0F, new CubeDeformation(0.2F)),
				PartPose.offsetAndRotation(0.0F, -9.0F, 5.4F, 1.05F, 0.0F, 0.0F));
		tail.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(12, 0)
						.addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.1F)),
				PartPose.offsetAndRotation(0.0F, 4.8F, 0.0F, -0.75F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	public static LayerDefinition createCapBack() {
		MeshDefinition mesh = emptyPlayerMesh();
		PartDefinition head = mesh.getRoot().getChild("head");
		head.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -0.5F, 0.0F, 8.0F, 1.0F, 4.0F),
				PartPose.offsetAndRotation(0.0F, -5.4F, 4.3F, -0.12F, 0.0F, 0.0F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) return;
		Outfits.Outfit o = CharacterClient.outfitForTexture(state.skin.body().texturePath());
		if (o == null || dev.leonida.character.model.HdModelLayer.active(state.skin.body().texturePath()) != null) return;
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
		if ((o.accessories() & Outfits.ACC_PONYTAIL) != 0) {
			collector.submitModel(this.ponytail, state, poseStack, RenderTypes.entityCutout(PONYTAIL_TEX), light, overlay, state.outlineColor);
		}
		if ((o.accessories() & Outfits.ACC_CAP_BACK) != 0) {
			collector.submitModel(this.capBack, state, poseStack, RenderTypes.entityCutout(CAP_TEX), light, overlay, state.outlineColor);
		}
	}
}
