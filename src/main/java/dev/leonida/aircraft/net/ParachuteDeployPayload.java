package dev.leonida.aircraft.net;

import dev.leonida.LeonidaMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the skydiving player pulls the ripcord. */
public record ParachuteDeployPayload() implements CustomPacketPayload {
	public static final Type<ParachuteDeployPayload> TYPE = new Type<>(LeonidaMod.id("aircraft/deploy_parachute"));
	public static final StreamCodec<FriendlyByteBuf, ParachuteDeployPayload> CODEC = StreamCodec.unit(new ParachuteDeployPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
