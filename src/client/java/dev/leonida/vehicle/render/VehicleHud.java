package dev.leonida.vehicle.render;

import dev.leonida.vehicle.VehicleEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** Interaction prompts (enter, slim jim, smash, carjack), lock-pick progress, vehicle name and a small speed readout. */
public final class VehicleHud {
	private VehicleHud() {}

	private static Component enteredName;
	private static int nameTicks;

	public static void onEnter(VehicleEntity v) {
		enteredName = Component.translatable("entity.leonida.vehicle_" + v.spec.id());
		nameTicks = 70;
	}

	public static void tick() {
		if (nameTicks > 0) nameTicks--;
	}

	/** The vehicle under the crosshair within reach, if any. */
	public static VehicleEntity lookedAt(Minecraft mc) {
		HitResult hr = mc.hitResult;
		if (hr instanceof EntityHitResult ehr && ehr.getEntity() instanceof VehicleEntity v && mc.player != null
				&& mc.player.distanceTo(v) < 4.5f + v.spec.length() / 2) {
			return v;
		}
		return null;
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null) return;
		Font font = mc.font;
		int w = g.guiWidth(), h = g.guiHeight();
		int cx = w / 2, y = h / 2 + 16;

		if (p.getVehicle() instanceof VehicleEntity v) {
			if (nameTicks > 0 && enteredName != null) {
				int a = Math.min(255, nameTicks * 12);
				g.centeredText(font, enteredName, cx, h / 4, (a << 24) | 0xFFE36B);
				if (v.getControllingPassenger() == p) {
					g.centeredText(font, Component.translatable("hud.leonida.vehicle.controls"), cx, h / 4 + 12, (a << 24) | 0xE0E0E0);
				}
			}
			if (v.getControllingPassenger() == p) {
				int kmh = Math.round(Math.abs(v.clientSpeed) * 3.6f);
				String speed = kmh + " km/h";
				int x1 = w - 8, y1 = h - 40;
				g.fill(x1 - 74, y1 - 4, x1, y1 + 22, 0x80000000);
				g.text(font, speed, x1 - 70, y1, 0xFFFFFFFF, true);
				float hp = v.healthFraction();
				int bar = (int) (66 * hp);
				int col = hp > 0.6f ? 0xFF5BD16A : hp > 0.3f ? 0xFFF2B233 : 0xFFE24A3B;
				g.fill(x1 - 70, y1 + 12, x1 - 4, y1 + 16, 0xFF303030);
				g.fill(x1 - 70, y1 + 12, x1 - 70 + bar, y1 + 16, col);
			}
			return;
		}

		VehicleEntity v = lookedAt(mc);
		if (v == null || v.isWrecked()) return;
		int pick = v.getPickProgress();
		if (pick > 0) {
			g.centeredText(font, Component.translatable("hud.leonida.vehicle.picking"), cx, y, 0xFFFFFFFF);
			int bw = 80;
			int filled = (int) (bw * Math.min(1f, pick / 100f));
			g.fill(cx - bw / 2 - 1, y + 11, cx + bw / 2 + 1, y + 17, 0xC0000000);
			g.fill(cx - bw / 2, y + 12, cx - bw / 2 + filled, y + 16, 0xFFFFC83A);
			return;
		}
		Component name = Component.translatable("entity.leonida.vehicle_" + v.spec.id());
		g.centeredText(font, name, cx, y, 0xFFFFE36B);
		if (v.hasNpcDriver() || (v.getControllingPassenger() != null && v.getControllingPassenger() != p)) {
			g.centeredText(font, Component.translatable("hud.leonida.vehicle.carjack"), cx, y + 11, 0xFFFF8A65);
		} else if (v.isLocked()) {
			g.centeredText(font, Component.translatable("hud.leonida.vehicle.locked"), cx, y + 11, 0xFFFF6B6B);
			g.centeredText(font, Component.translatable("hud.leonida.vehicle.slimjim"), cx, y + 22, 0xFFFFFFFF);
			g.centeredText(font, Component.translatable("hud.leonida.vehicle.smash"), cx, y + 33, 0xFFFFFFFF);
		} else {
			g.centeredText(font, Component.translatable("hud.leonida.vehicle.enter"), cx, y + 11, 0xFFFFFFFF);
		}
	}
}
