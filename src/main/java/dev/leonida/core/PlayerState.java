package dev.leonida.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.leonida.LeonidaMod;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player GTA state, saved with the player and synced to that player's client so the HUD can
 * read it. Writers: gameplay (wanted, knownFlags, money, liveViewers), character (character).
 * Reader: hud. Always replace the whole record with the with* helpers; never mutate.
 */
public record PlayerState(int wanted, int knownFlags, long money, int character, int liveViewers) {
	/** knownFlags bits: what the police currently know about the player (the four icons under the stars). */
	public static final int KNOWN_FACE = 1, KNOWN_CLOTHES = 2, KNOWN_PERSON = 4, KNOWN_VEHICLE = 8;
	/** character values. */
	public static final int JASON = 0, LUCIA = 1;
	public static final int MAX_WANTED = 6;

	public static final PlayerState DEFAULT = new PlayerState(0, 0, 500L, JASON, 0);

	public static final Codec<PlayerState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("wanted").forGetter(PlayerState::wanted),
			Codec.INT.fieldOf("known_flags").forGetter(PlayerState::knownFlags),
			Codec.LONG.fieldOf("money").forGetter(PlayerState::money),
			Codec.INT.fieldOf("character").forGetter(PlayerState::character),
			Codec.INT.fieldOf("live_viewers").forGetter(PlayerState::liveViewers)
	).apply(i, PlayerState::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PlayerState::wanted,
			ByteBufCodecs.VAR_INT, PlayerState::knownFlags,
			ByteBufCodecs.VAR_LONG, PlayerState::money,
			ByteBufCodecs.VAR_INT, PlayerState::character,
			ByteBufCodecs.VAR_INT, PlayerState::liveViewers,
			PlayerState::new);

	public static AttachmentType<PlayerState> TYPE;

	static void register() {
		TYPE = AttachmentRegistry.create(LeonidaMod.id("player_state"), b -> b
				.initializer(() -> DEFAULT)
				.persistent(CODEC)
				.syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly())
				.copyOnDeath());
	}

	public static PlayerState get(Player p) {
		return p.getAttachedOrCreate(TYPE);
	}

	public static void set(Player p, PlayerState s) {
		p.setAttached(TYPE, s);
	}

	public PlayerState withWanted(int v) { return new PlayerState(Math.max(0, Math.min(MAX_WANTED, v)), knownFlags, money, character, liveViewers); }
	public PlayerState withKnownFlags(int v) { return new PlayerState(wanted, v, money, character, liveViewers); }
	public PlayerState withMoney(long v) { return new PlayerState(wanted, knownFlags, Math.max(0, v), character, liveViewers); }
	public PlayerState withCharacter(int v) { return new PlayerState(wanted, knownFlags, money, v, liveViewers); }
	public PlayerState withLiveViewers(int v) { return new PlayerState(wanted, knownFlags, money, character, Math.max(0, v)); }
}
