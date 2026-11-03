package dev.leonida.hud;

import dev.leonida.LeonidaMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side entry points for HUD events. Any module may call these (gameplay, weapons, vehicles).
 * Signatures are a contract: gameplay calls some of them by reflection, so never change them.
 */
public final class HudNet {
	private HudNet() {}

	/** Event kinds carried by {@link Event}. */
	public static final int BUSTED = 0, WASTED = 1, TEXT = 2, MISSION = 3, TOAST = 4, BLIP = 5, REMOVE_BLIP = 6,
			WAYPOINT = 7, SEARCHING = 8, CLEAR_WAYPOINT = 9;

	/** One generic HUD event: kind plus two strings and two ints (meaning depends on kind). */
	public record Event(int kind, String a, String b, int x, int z) implements CustomPacketPayload {
		public static final Type<Event> TYPE = new Type<>(LeonidaMod.id("hud_event"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Event> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Event::kind,
				ByteBufCodecs.STRING_UTF8, Event::a,
				ByteBufCodecs.STRING_UTF8, Event::b,
				ByteBufCodecs.VAR_INT, Event::x,
				ByteBufCodecs.VAR_INT, Event::z,
				Event::new);

		@Override
		public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Event.TYPE, Event.CODEC);
	}

	private static void send(ServerPlayer p, int kind, String a, String b, int x, int z) {
		if (p == null) return;
		if (!ServerPlayNetworking.canSend(p, Event.TYPE)) return;
		ServerPlayNetworking.send(p, new Event(kind, a == null ? "" : a, b == null ? "" : b, x, z));
	}

	/** BUSTED screen (about 5 s). Call at the arrest moment. */
	public static void busted(ServerPlayer p) { send(p, BUSTED, "", "", 0, 0); }

	/** WASTED screen. The client also shows it on its own when the player dies. */
	public static void wasted(ServerPlayer p) { send(p, WASTED, "", "", 0, 0); }

	/** Phone text popup from a contact; also stored in the phone Messages app. */
	public static void text(ServerPlayer p, String contact, String message) { send(p, TEXT, contact, message, 0, 0); }

	/** Big centred mission title, e.g. ("STORE ROBBED", "+$1,240"). */
	public static void mission(ServerPlayer p, String title, String subtitle) { send(p, MISSION, title, subtitle, 0, 0); }

	/** Help box top left, e.g. "Lose the cops". */
	public static void toast(ServerPlayer p, String message) { send(p, TOAST, message, "", 0, 0); }

	/** Map blip. type: home, safehouse, shop, gun, clothes, mission, bar, police, hospital, car, target, gas. */
	public static void blip(ServerPlayer p, String id, String type, int x, int z) { send(p, BLIP, id, type, x, z); }

	public static void removeBlip(ServerPlayer p, String id) { send(p, REMOVE_BLIP, id, "", 0, 0); }

	/** Sets the GPS target; the pink route comes from MapQuery.route. */
	public static void waypoint(ServerPlayer p, int x, int z) { send(p, WAYPOINT, "", "", x, z); }

	public static void clearWaypoint(ServerPlayer p) { send(p, CLEAR_WAYPOINT, "", "", 0, 0); }

	/** true while police search for the player (stars flash), false when they reacquire or wanted is 0. */
	public static void searching(ServerPlayer p, boolean searching) { send(p, SEARCHING, "", "", searching ? 1 : 0, 0); }
}
