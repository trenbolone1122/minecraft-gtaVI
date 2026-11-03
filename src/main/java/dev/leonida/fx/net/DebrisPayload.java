package dev.leonida.fx.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: throw physical debris pieces for a list of blocks.
 *
 * @param x,y,z   burst centre (pieces fly away from it)
 * @param power   burst strength, roughly explosion power (TNT is 4)
 * @param flags   bit 0: pieces only (blocks were not removed, visual scrape), bit 1: glass shatter style
 * @param positions packed block positions ({@code BlockPos.asLong})
 * @param states  block state ids ({@code Block.getId})
 */
public record DebrisPayload(double x, double y, double z, float power, int flags, long[] positions, int[] states)
		implements CustomPacketPayload {
	public static final int FLAG_VISUAL_ONLY = 1;
	public static final int FLAG_SHATTER = 2;
	public static final int MAX_BLOCKS = 1024;

	public static final Type<DebrisPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("leonida", "fx_debris"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DebrisPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeFloat(p.power);
				buf.writeVarInt(p.flags);
				int n = Math.min(p.positions.length, MAX_BLOCKS);
				buf.writeVarInt(n);
				for (int i = 0; i < n; i++) {
					buf.writeLong(p.positions[i]);
					buf.writeVarInt(p.states[i]);
				}
			},
			buf -> {
				double x = buf.readDouble();
				double y = buf.readDouble();
				double z = buf.readDouble();
				float power = buf.readFloat();
				int flags = buf.readVarInt();
				int n = Math.min(buf.readVarInt(), MAX_BLOCKS);
				long[] pos = new long[n];
				int[] st = new int[n];
				for (int i = 0; i < n; i++) {
					pos[i] = buf.readLong();
					st[i] = buf.readVarInt();
				}
				return new DebrisPayload(x, y, z, power, flags, pos, st);
			});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
