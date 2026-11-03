package dev.leonida.vehicle.net;

import dev.leonida.LeonidaMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server vehicle packets. */
public final class VehiclePayloads {
	private VehiclePayloads() {}

	/** Driver input bits (for brake lights and steering on other clients). */
	public record Controls(int bits) implements CustomPacketPayload {
		public static final Type<Controls> TYPE = new Type<>(LeonidaMod.id("vehicle_controls"));
		public static final StreamCodec<ByteBuf, Controls> CODEC = ByteBufCodecs.VAR_INT.map(Controls::new, Controls::bits);
		@Override public Type<Controls> type() { return TYPE; }
	}

	/** Driver action: horn, headlights, siren, or a crash with impact speed. */
	public record Action(int action, float value) implements CustomPacketPayload {
		public static final int HORN = 0, LIGHTS = 1, SIREN = 2, CRASH = 3, EXIT_OR_ENTER = 4;
		public static final Type<Action> TYPE = new Type<>(LeonidaMod.id("vehicle_action"));
		public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Action::action, ByteBufCodecs.FLOAT, Action::value, Action::new);
		@Override public Type<Action> type() { return TYPE; }
	}
}
