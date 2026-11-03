package dev.leonida.character.model;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.leonida.LeonidaMod;
import dev.leonida.character.CharacterClient;
import dev.leonida.character.Outfits;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Renders Jason and Lucia as their high detail cube models (see HdModels) instead of the flat
 * player skin. The meshes ride on the vanilla PlayerModel skeleton, so walking, swimming, sitting,
 * aiming and every other vanilla pose drives them. The vanilla body is hidden by swapping its
 * texture for a transparent one (AvatarRendererMixin). Run with -Dleonida.character.model=false to
 * fall back to the plain skins.
 */
public class HdModelLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public static boolean enabled = !"false".equals(System.getProperty("leonida.character.model"));
	public static final Identifier BLANK = LeonidaMod.id("textures/entity/character/model/blank.png");

	private static final Map<Outfits.Outfit, ModelLayerLocation> LAYERS = new HashMap<>();
	/** Models baked by the most recent renderer build, shared with the first person hand mixin. */
	private static Map<Outfits.Outfit, PlayerModel> current = Map.of();

	private final Map<Outfits.Outfit, PlayerModel> models = new HashMap<>();

	public static void registerLayers() {
		for (List<Outfits.Outfit> l : List.of(Outfits.JASON, Outfits.LUCIA)) {
			for (Outfits.Outfit o : l) {
				Supplier<LayerDefinition> def = definition(o);
				if (def == null) continue;
				ModelLayerLocation loc = new ModelLayerLocation(LeonidaMod.id("character/hd_" + name(o)), "main");
				LAYERS.put(o, loc);
				ModelLayerRegistry.registerModelLayer(loc, def::get);
			}
		}
	}

	private static String name(Outfits.Outfit o) {
		return Outfits.characterName(o.character()) + "_" + o.id();
	}

	private static Supplier<LayerDefinition> definition(Outfits.Outfit o) {
		return switch (name(o)) {
			case "jason_everyday" -> HdModels::jason_everyday;
			case "jason_heist" -> HdModels::jason_heist;
			case "jason_swim" -> HdModels::jason_swim;
			case "jason_casual" -> HdModels::jason_casual;
			case "lucia_everyday" -> HdModels::lucia_everyday;
			case "lucia_heist" -> HdModels::lucia_heist;
			case "lucia_swim" -> HdModels::lucia_swim;
			case "lucia_jumpsuit" -> HdModels::lucia_jumpsuit;
			default -> null;
		};
	}

	public static Identifier texture(Outfits.Outfit o) {
		return LeonidaMod.id("textures/entity/character/model/" + name(o) + ".png");
	}

	public HdModelLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet set) {
		super(parent);
		for (Map.Entry<Outfits.Outfit, ModelLayerLocation> e : LAYERS.entrySet()) {
			this.models.put(e.getKey(), new PlayerModel(set.bakeLayer(e.getValue()), e.getKey().slim()));
		}
		current = this.models;
	}

	/** The outfit whose detailed model replaces this skin texture, or null for the vanilla look. */
	public static Outfits.Outfit active(Identifier skinTexture) {
		if (!enabled) return null;
		Outfits.Outfit o = CharacterClient.outfitForTexture(skinTexture);
		return o != null && current.containsKey(o) ? o : null;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.isInvisible) return;
		Outfits.Outfit o = active(state.skin.body().texturePath());
		if (o == null) return;
		PlayerModel model = this.models.get(o);
		collector.submitModel(model, state, poseStack, RenderTypes.entityCutout(texture(o)), light,
				LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
	}

	/** First person arm in the detailed model. Returns false when the vanilla arm should render. */
	public static boolean submitHand(PoseStack poseStack, SubmitNodeCollector collector, int light, Identifier skinTexture, boolean right) {
		Outfits.Outfit o = active(skinTexture);
		if (o == null) return false;
		PlayerModel model = current.get(o);
		ModelPart arm = right ? model.rightArm : model.leftArm;
		arm.resetPose();
		arm.visible = true;
		arm.zRot = right ? 0.1F : -0.1F;
		collector.submitModelPart(arm, poseStack, RenderTypes.entityCutout(texture(o)), light, OverlayTexture.NO_OVERLAY, null);
		return true;
	}
}
