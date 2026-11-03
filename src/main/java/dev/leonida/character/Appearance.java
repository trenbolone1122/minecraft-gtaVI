package dev.leonida.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.leonida.LeonidaMod;
import dev.leonida.core.PlayerState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/**
 * What a player looks like: the active character and the chosen outfit of each character.
 * Synced to every client so other players render the right skin. Server writes only.
 */
public record Appearance(int character, int jasonOutfit, int luciaOutfit) {
	public static final Appearance DEFAULT = new Appearance(PlayerState.JASON, 0, 0);

	public static final Codec<Appearance> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("character").forGetter(Appearance::character),
			Codec.INT.fieldOf("jason_outfit").forGetter(Appearance::jasonOutfit),
			Codec.INT.fieldOf("lucia_outfit").forGetter(Appearance::luciaOutfit)
	).apply(i, Appearance::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, Appearance> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Appearance::character,
			ByteBufCodecs.VAR_INT, Appearance::jasonOutfit,
			ByteBufCodecs.VAR_INT, Appearance::luciaOutfit,
			Appearance::new);

	public static AttachmentType<Appearance> TYPE;

	static void register() {
		TYPE = AttachmentRegistry.create(LeonidaMod.id("character_appearance"), b -> b
				.initializer(() -> DEFAULT)
				.persistent(CODEC)
				.syncWith(STREAM_CODEC, AttachmentSyncPredicate.all())
				.copyOnDeath());
	}

	public static Appearance get(Player p) {
		Appearance a = p.getAttached(TYPE);
		return a == null ? DEFAULT : a;
	}

	public int outfitIndex(int c) {
		return c == PlayerState.LUCIA ? luciaOutfit : jasonOutfit;
	}

	public Outfits.Outfit outfit() {
		return Outfits.get(character, outfitIndex(character));
	}

	public Appearance withCharacter(int c) {
		return new Appearance(c, jasonOutfit, luciaOutfit);
	}

	public Appearance withOutfit(int c, int index) {
		return c == PlayerState.LUCIA ? new Appearance(character, jasonOutfit, index) : new Appearance(character, index, luciaOutfit);
	}
}
