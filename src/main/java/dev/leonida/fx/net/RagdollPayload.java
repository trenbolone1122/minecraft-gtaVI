package dev.leonida.fx.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: turn an entity into a ragdoll.
 *
 * @param entityId network id of the living entity
 * @param ix,iy,iz impulse in blocks per second added to the body
 * @param ticks    how long the ragdoll lasts; 0 means default (dead body)
 */
public record RagdollPayload(int entityId, float ix, float iy, float iz, int ticks) implements CustomPacketPayload {
	public static final Type<RagdollPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("leonida", "fx_ragdoll"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RagdollPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeVarInt(p.entityId);
				buf.writeFloat(p.ix);
				buf.writeFloat(p.iy);
				buf.writeFloat(p.iz);
				buf.writeVarInt(p.ticks);
			},
			buf -> new RagdollPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
