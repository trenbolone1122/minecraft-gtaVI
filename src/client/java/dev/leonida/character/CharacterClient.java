package dev.leonida.character;

import com.mojang.blaze3d.platform.InputConstants;
import dev.leonida.LeonidaMod;
import dev.leonida.character.net.CharacterNet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Client-only setup for the character module. Owned by the character agent. */
public final class CharacterClient {
	private CharacterClient() {}

	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(LeonidaMod.id("character"));
	public static KeyMapping switchKey;

	private static final Map<Outfits.Outfit, PlayerSkin> SKINS = new HashMap<>();
	private static final Map<Outfits.Outfit, PlayerSkin> SKINS_HD = new HashMap<>();
	private static final Map<Outfits.Outfit, Boolean> HD_PRESENT = new HashMap<>();
	/** HD (128x128) skins are used when present; start the game with -Dleonida.character.hd=false for the 64x64 set. */
	public static boolean useHd = !"false".equals(System.getProperty("leonida.character.hd"));
	private static final Map<Identifier, Outfits.Outfit> BY_TEXTURE = new HashMap<>();

	public static void init() {
		for (List<Outfits.Outfit> l : List.of(Outfits.JASON, Outfits.LUCIA)) {
			for (Outfits.Outfit o : l) {
				ClientAsset.ResourceTexture tex = new ClientAsset.ResourceTexture(o.texture());
				SKINS.put(o, new PlayerSkin(tex, null, null, o.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE, true));
				BY_TEXTURE.put(tex.texturePath(), o);
				ClientAsset.ResourceTexture hd = new ClientAsset.ResourceTexture(hdAsset(o));
				SKINS_HD.put(o, new PlayerSkin(hd, null, null, o.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE, true));
				BY_TEXTURE.put(hd.texturePath(), o);
			}
		}

		switchKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leonida.character_switch",
				InputConstants.Type.KEYBOARD, InputConstants.KEY_Z, CATEGORY));

		ClientPlayNetworking.registerGlobalReceiver(CharacterNet.SwitchStart.TYPE,
				(p, ctx) -> SwitchTransition.startUp(p.target(), p.upTicks()));
		ClientPlayNetworking.registerGlobalReceiver(CharacterNet.SwitchDone.TYPE,
				(p, ctx) -> SwitchTransition.startDown(p.character()));
		ClientPlayNetworking.registerGlobalReceiver(CharacterNet.OpenWardrobe.TYPE,
				(p, ctx) -> ctx.client().gui.setScreen(new WardrobeScreen()));

		ClientTickEvents.END_CLIENT_TICK.register(CharacterClient::tick);
		HudElementRegistry.addLast(LeonidaMod.id("character_switch"), SwitchTransition::extractOverlay);

		ModelLayerRegistry.registerModelLayer(AccessoryLayer.PONYTAIL, AccessoryLayer::createPonytail);
		ModelLayerRegistry.registerModelLayer(AccessoryLayer.CAP_BACK, AccessoryLayer::createCapBack);
		dev.leonida.character.model.HdModelLayer.registerLayers();
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				@SuppressWarnings("unchecked")
				var parent = (net.minecraft.client.renderer.entity.RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) avatar;
				helper.register(new AccessoryLayer(parent, ctx.getModelSet()));
				helper.register(new dev.leonida.character.model.HdModelLayer(parent, ctx.getModelSet()));
			}
		});
	}

	private static void tick(Minecraft mc) {
		SwitchTransition.tick(mc);
		while (switchKey.consumeClick()) {
			if (mc.player != null && mc.gui.screen() == null && !SwitchTransition.active()) {
				ClientPlayNetworking.send(new CharacterNet.SwitchRequest(-1));
			}
		}
	}

	/** Skin of an outfit, as a vanilla PlayerSkin (used for players and the wardrobe preview). */
	public static PlayerSkin skin(Outfits.Outfit o) {
		if (useHd && hdPresent(o)) return SKINS_HD.get(o);
		return SKINS.get(o);
	}

	/** leonida:entity/character/jason/everyday becomes leonida:entity/character/hd/jason/everyday. */
	public static Identifier hdAsset(Outfits.Outfit o) {
		return o.texture().withPath(p -> p.replace("entity/character/", "entity/character/hd/"));
	}

	private static boolean hdPresent(Outfits.Outfit o) {
		Boolean b = HD_PRESENT.get(o);
		if (b == null) {
			Identifier file = hdAsset(o).withPath(p -> "textures/" + p + ".png");
			b = Minecraft.getInstance().getResourceManager().getResource(file).isPresent();
			HD_PRESENT.put(o, b);
		}
		return b;
	}

	/** Outfit for a skin texture path, or null if the texture is not one of ours. */
	public static Outfits.Outfit outfitForTexture(Identifier texturePath) {
		return BY_TEXTURE.get(texturePath);
	}
}
