package dev.leonida.hud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.leonida.LeonidaMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;

/** Client-only setup for the hud module. Owned by the hud agent. */
public final class HudClient {
	private HudClient() {}

	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(LeonidaMod.id("hud"));
	public static KeyMapping MAP_KEY, PHONE_KEY, SETTINGS_KEY;

	public static void init() {
		MAP_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leonida.hud.map", InputConstants.KEY_M, CATEGORY));
		SETTINGS_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leonida.settings", InputConstants.KEY_F7, CATEGORY));
		LeonidaSettings.init();
		PHONE_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leonida.hud.phone", InputConstants.KEY_UP, CATEGORY));

		// vanilla parts that do not fit the GTA look
		HudElementRegistry.removeElement(VanillaHudElements.HEALTH_BAR);
		HudElementRegistry.removeElement(VanillaHudElements.ARMOR_BAR);
		HudElementRegistry.removeElement(VanillaHudElements.FOOD_BAR);
		HudElementRegistry.removeElement(VanillaHudElements.EXPERIENCE_LEVEL);
		HudElementRegistry.removeElement(VanillaHudElements.MOUNT_HEALTH);
		// keep the jump bar for rideable vehicles, drop the XP bar
		HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, orig -> (g, dt) -> {
			var p = Minecraft.getInstance().player;
			if (p != null && p.jumpableVehicle() != null) orig.extractRenderState(g, dt);
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LeonidaMod.id("hud/minimap"), Minimap::draw);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LeonidaMod.id("hud/status"), StatusHud::draw);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, LeonidaMod.id("hud/notifications"), Notifications::draw);
		HudElementRegistry.addLast(LeonidaMod.id("hud/death"), Notifications::drawDeath);

		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof net.minecraft.client.gui.screens.options.OptionsScreen) {
				net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(
						net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.translatable("leonida.settings.button"),
								b -> mc.gui.setScreen(new SettingsScreen(screen))).bounds(5, 5, 90, 20).build());
			}
			if (screen instanceof DeathScreen)
				ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> Notifications.drawDeath(g, null));
		});

		ClientPlayNetworking.registerGlobalReceiver(HudNet.Event.TYPE, (e, ctx) -> handle(e));
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
			Blips.clear();
			Blips.clearWaypoint();
			HudState.searching = false;
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			HudState.tick();
			Minimap.tick();
			Route.tick();
			while (MAP_KEY.consumeClick()) if (mc.gui.screen() == null && mc.player != null) mc.gui.setScreen(new MapScreen());
			while (SETTINGS_KEY.consumeClick()) if (mc.gui.screen() == null) mc.gui.setScreen(new SettingsScreen(null));
			while (PHONE_KEY.consumeClick()) if (mc.gui.screen() == null && mc.player != null) mc.gui.setScreen(new Phone());
		});
	}

	/** Applies one server HUD event (also callable directly from client code and tests). */
	public static void handle(HudNet.Event e) {
		switch (e.kind()) {
			case HudNet.BUSTED -> HudState.busted();
			case HudNet.WASTED -> HudState.wasted();
			case HudNet.TEXT -> HudState.text(e.a(), e.b());
			case HudNet.MISSION -> HudState.mission(e.a(), e.b());
			case HudNet.TOAST -> HudState.toast(e.a());
			case HudNet.BLIP -> Blips.put(e.a(), Blips.Type.parse(e.b()), e.x() + 0.5, e.z() + 0.5);
			case HudNet.REMOVE_BLIP -> Blips.remove(e.a());
			case HudNet.WAYPOINT -> Blips.setWaypoint(e.x() + 0.5, e.z() + 0.5);
			case HudNet.CLEAR_WAYPOINT -> Blips.clearWaypoint();
			case HudNet.SEARCHING -> HudState.searching = e.x() != 0;
			default -> {}
		}
	}
}
