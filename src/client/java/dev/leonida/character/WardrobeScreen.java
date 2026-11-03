package dev.leonida.character;

import dev.leonida.character.net.CharacterNet;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Wardrobe: preview and pick an outfit for the character being played. */
public class WardrobeScreen extends Screen {
	private int character;
	private int selected;
	private final List<Button> outfitButtons = new ArrayList<>();

	public WardrobeScreen() {
		super(Component.translatable("character.leonida.wardrobe"));
	}

	@Override
	protected void init() {
		Appearance a = this.minecraft.player != null ? Appearance.get(this.minecraft.player) : Appearance.DEFAULT;
		if (outfitButtons.isEmpty()) {
			character = a.character();
			selected = a.outfitIndex(character);
		}
		outfitButtons.clear();
		int previewW = Math.min(140, this.width / 3);
		int previewH = Math.min(200, this.height - 70);
		int cx = this.width / 2;
		PlayerSkinWidget preview = new PlayerSkinWidget(previewW, previewH, this.minecraft.getEntityModels(),
				() -> CharacterClient.skin(Outfits.get(character, selected)));
		preview.setPosition(cx - previewW - 10, 34);
		this.addRenderableWidget(preview);

		List<Outfits.Outfit> outfits = Outfits.forCharacter(character);
		int y = 40;
		for (Outfits.Outfit o : outfits) {
			Button b = Button.builder(Component.translatable(o.langKey()), btn -> {
				selected = o.index();
				refresh();
			}).bounds(cx + 10, y, 140, 20).build();
			outfitButtons.add(b);
			this.addRenderableWidget(b);
			y += 24;
		}
		this.addRenderableWidget(Button.builder(Component.translatable("character.leonida.wardrobe.wear"), btn -> {
			ClientPlayNetworking.send(new CharacterNet.SetOutfit(selected));
			this.onClose();
		}).bounds(cx + 10, y + 10, 68, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> this.onClose())
				.bounds(cx + 82, y + 10, 68, 20).build());
		refresh();
	}

	private void refresh() {
		for (int i = 0; i < outfitButtons.size(); i++) outfitButtons.get(i).active = i != selected;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(this.font, Component.translatable("character.leonida.wardrobe.title",
				Component.translatable("character.leonida.name." + Outfits.characterName(character))), this.width / 2, 14, 0xffffffff);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
