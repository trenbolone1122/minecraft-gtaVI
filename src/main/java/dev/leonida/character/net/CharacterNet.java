package dev.leonida.character.net;

import dev.leonida.LeonidaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Character packets. */
public final class CharacterNet {
	private CharacterNet() {}

	/** Client asks to switch. target -1 = the other character. */
	public record SwitchRequest(int target) implements CustomPacketPayload {
		public static final Type<SwitchRequest> TYPE = new Type<>(LeonidaMod.id("character/switch_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SwitchRequest> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, SwitchRequest::target, SwitchRequest::new);
		@Override public Type<SwitchRequest> type() { return TYPE; }
	}

	/** Server accepted: play the pull-up for upTicks, the swap happens after that. */
	public record SwitchStart(int target, int upTicks) implements CustomPacketPayload {
		public static final Type<SwitchStart> TYPE = new Type<>(LeonidaMod.id("character/switch_start"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SwitchStart> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, SwitchStart::target, ByteBufCodecs.VAR_INT, SwitchStart::upTicks, SwitchStart::new);
		@Override public Type<SwitchStart> type() { return TYPE; }
	}

	/** Swap done on the server: play the drop-down onto the new character. */
	public record SwitchDone(int character) implements CustomPacketPayload {
		public static final Type<SwitchDone> TYPE = new Type<>(LeonidaMod.id("character/switch_done"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SwitchDone> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, SwitchDone::character, SwitchDone::new);
		@Override public Type<SwitchDone> type() { return TYPE; }
	}

	/** Server opened a wardrobe for this player. */
	public record OpenWardrobe(int dummy) implements CustomPacketPayload {
		public static final Type<OpenWardrobe> TYPE = new Type<>(LeonidaMod.id("character/open_wardrobe"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenWardrobe> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenWardrobe::dummy, OpenWardrobe::new);
		@Override public Type<OpenWardrobe> type() { return TYPE; }
	}

	/** Client picked an outfit in the wardrobe. */
	public record SetOutfit(int index) implements CustomPacketPayload {
		public static final Type<SetOutfit> TYPE = new Type<>(LeonidaMod.id("character/set_outfit"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SetOutfit> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, SetOutfit::index, SetOutfit::new);
		@Override public Type<SetOutfit> type() { return TYPE; }
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SwitchRequest.TYPE, SwitchRequest.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetOutfit.TYPE, SetOutfit.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SwitchStart.TYPE, SwitchStart.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SwitchDone.TYPE, SwitchDone.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenWardrobe.TYPE, OpenWardrobe.CODEC);
	}
}
