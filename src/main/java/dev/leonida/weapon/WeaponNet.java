package dev.leonida.weapon;

import dev.leonida.LeonidaMod;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Packets of the weapon module. */
public final class WeaponNet {
	private WeaponNet() {}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> newType(String path) {
		return new CustomPacketPayload.Type<>(LeonidaMod.id("weapon/" + path));
	}

	/** Client asks to fire the held gun once. */
	public record Fire(boolean aiming) implements CustomPacketPayload {
		public static final Type<Fire> TYPE = WeaponNet.newType("fire");
		public static final StreamCodec<FriendlyByteBuf, Fire> CODEC = CustomPacketPayload.codec((p, b) -> b.writeBoolean(p.aiming), b -> new Fire(b.readBoolean()));
		@Override public Type<Fire> type() { return TYPE; }
	}

	/** Client asks to reload the held gun. */
	public record Reload() implements CustomPacketPayload {
		public static final Type<Reload> TYPE = WeaponNet.newType("reload");
		public static final StreamCodec<FriendlyByteBuf, Reload> CODEC = StreamCodec.unit(new Reload());
		@Override public Type<Reload> type() { return TYPE; }
	}

	/** Weapon wheel: open/close (for slow motion) and the chosen inventory slot to bring to the hand (-1 none). */
	public record Wheel(boolean open, int slot) implements CustomPacketPayload {
		public static final Type<Wheel> TYPE = WeaponNet.newType("wheel");
		public static final StreamCodec<FriendlyByteBuf, Wheel> CODEC = CustomPacketPayload.codec(
				(p, b) -> { b.writeBoolean(p.open); b.writeVarInt(p.slot + 1); }, b -> new Wheel(b.readBoolean(), b.readVarInt() - 1));
		@Override public Type<Wheel> type() { return TYPE; }
	}

	/** Server to clients: visual effects of one shot. kinds: 0 miss, 1 block, 2 entity. */
	public record ShotFx(int shooterId, int gun, Vec3 muzzle, List<Vec3> ends, byte[] kinds) implements CustomPacketPayload {
		public static final Type<ShotFx> TYPE = WeaponNet.newType("shot_fx");
		public static final StreamCodec<RegistryFriendlyByteBuf, ShotFx> CODEC = CustomPacketPayload.codec(ShotFx::write, ShotFx::read);
		@Override public Type<ShotFx> type() { return TYPE; }

		private void write(RegistryFriendlyByteBuf b) {
			b.writeVarInt(shooterId);
			b.writeByte(gun);
			Vec3.STREAM_CODEC.encode(b, muzzle);
			b.writeVarInt(ends.size());
			for (int i = 0; i < ends.size(); i++) {
				Vec3.STREAM_CODEC.encode(b, ends.get(i));
				b.writeByte(kinds[i]);
			}
		}

		private static ShotFx read(RegistryFriendlyByteBuf b) {
			int id = b.readVarInt();
			int gun = b.readByte();
			Vec3 m = Vec3.STREAM_CODEC.decode(b);
			int n = Math.min(b.readVarInt(), 64);
			List<Vec3> ends = new ArrayList<>(n);
			byte[] kinds = new byte[n];
			for (int i = 0; i < n; i++) {
				ends.add(Vec3.STREAM_CODEC.decode(b));
				kinds[i] = b.readByte();
			}
			return new ShotFx(id, gun, m, ends, kinds);
		}
	}

	/** Server to shooter: a bullet hit a living entity (hit marker), kill = it died. */
	public record HitConfirm(boolean kill, boolean headshot) implements CustomPacketPayload {
		public static final Type<HitConfirm> TYPE = WeaponNet.newType("hit");
		public static final StreamCodec<FriendlyByteBuf, HitConfirm> CODEC = CustomPacketPayload.codec(
				(p, b) -> { b.writeBoolean(p.kill); b.writeBoolean(p.headshot); }, b -> new HitConfirm(b.readBoolean(), b.readBoolean()));
		@Override public Type<HitConfirm> type() { return TYPE; }
	}

	/** Server to nearby clients: a rocket blast (camera shake and flash, scaled by distance on the client). */
	public record Blast(Vec3 pos, float power) implements CustomPacketPayload {
		public static final Type<Blast> TYPE = WeaponNet.newType("blast");
		public static final StreamCodec<RegistryFriendlyByteBuf, Blast> CODEC = CustomPacketPayload.codec(
				(p, b) -> { Vec3.STREAM_CODEC.encode(b, p.pos); b.writeFloat(p.power); }, b -> new Blast(Vec3.STREAM_CODEC.decode(b), b.readFloat()));
		@Override public Type<Blast> type() { return TYPE; }
	}

	/** Server to client: open the Ammu-Nation menu for the shop block at pos. */
	public record OpenShop(net.minecraft.core.BlockPos pos) implements CustomPacketPayload {
		public static final Type<OpenShop> TYPE = WeaponNet.newType("open_shop");
		public static final StreamCodec<FriendlyByteBuf, OpenShop> CODEC = CustomPacketPayload.codec(
				(p, b) -> b.writeBlockPos(p.pos), b -> new OpenShop(b.readBlockPos()));
		@Override public Type<OpenShop> type() { return TYPE; }
	}

	/** Client asks to buy catalog row index at the shop block pos. */
	public record Buy(net.minecraft.core.BlockPos pos, int index) implements CustomPacketPayload {
		public static final Type<Buy> TYPE = WeaponNet.newType("buy");
		public static final StreamCodec<FriendlyByteBuf, Buy> CODEC = CustomPacketPayload.codec(
				(p, b) -> { b.writeBlockPos(p.pos); b.writeVarInt(p.index); }, b -> new Buy(b.readBlockPos(), b.readVarInt()));
		@Override public Type<Buy> type() { return TYPE; }
	}

	/** Server to client: outcome of a purchase ("bought", "owned", "money"). */
	public record ShopResult(int index, String result) implements CustomPacketPayload {
		public static final Type<ShopResult> TYPE = WeaponNet.newType("shop_result");
		public static final StreamCodec<FriendlyByteBuf, ShopResult> CODEC = CustomPacketPayload.codec(
				(p, b) -> { b.writeVarInt(p.index); b.writeUtf(p.result, 16); }, b -> new ShopResult(b.readVarInt(), b.readUtf(16)));
		@Override public Type<ShopResult> type() { return TYPE; }
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Fire.TYPE, Fire.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Reload.TYPE, Reload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Wheel.TYPE, Wheel.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShotFx.TYPE, ShotFx.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HitConfirm.TYPE, HitConfirm.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Blast.TYPE, Blast.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Buy.TYPE, Buy.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenShop.TYPE, OpenShop.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShopResult.TYPE, ShopResult.CODEC);
	}
}
