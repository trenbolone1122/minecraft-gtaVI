package dev.leonida.gameplay.util;

import dev.leonida.hud.HudNet;
import net.minecraft.server.level.ServerPlayer;

/** Thin wrapper over the hud module's HudNet so gameplay code has one place for HUD calls. */
public final class HudBridge {
	private HudBridge() {}

	public static void busted(ServerPlayer p) { HudNet.busted(p); }

	public static void wasted(ServerPlayer p) { HudNet.wasted(p); }

	public static void searching(ServerPlayer p, boolean searching) { HudNet.searching(p, searching); }

	public static void toast(ServerPlayer p, String msg) { HudNet.toast(p, msg); }

	public static void text(ServerPlayer p, String contact, String msg) { HudNet.text(p, contact, msg); }

	public static void mission(ServerPlayer p, String title, String sub) { HudNet.mission(p, title, sub); }

	public static void blip(ServerPlayer p, String id, String type, int x, int z) { HudNet.blip(p, id, type, x, z); }
}
