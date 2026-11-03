package dev.leonida.aircraft.net;

import dev.leonida.LeonidaMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the pilot's engine throttle and equipment toggles, so every client sees the same state. */
public record AircraftControlPayload(int entityId, float engineTarget, boolean smoke, boolean searchlight) implements CustomPacketPayload {
	public static final Type<AircraftControlPayload> TYPE = new Type<>(LeonidaMod.id("aircraft/control"));
	public static final StreamCodec<FriendlyByteBuf, AircraftControlPayload> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entityId);
				buf.writeFloat(p.engineTarget);
				buf.writeBoolean(p.smoke);
				buf.writeBoolean(p.searchlight);
			},
			buf -> new AircraftControlPayload(buf.readVarInt(), buf.readFloat(), buf.readBoolean(), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
