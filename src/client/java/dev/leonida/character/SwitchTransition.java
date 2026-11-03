package dev.leonida.character;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * GTA style switch camera: the view lifts off the current character high into the sky looking down,
 * holds while the server swaps, then drops onto the other character. Purely client side.
 */
public final class SwitchTransition {
	private SwitchTransition() {}

	enum Phase { IDLE, UP, HOLD, DOWN }

	private static final double HEIGHT = 80.0;
	private static final long DOWN_MS = 1300;
	private static final long HOLD_TIMEOUT_MS = 4000;

	private static Phase phase = Phase.IDLE;
	private static long phaseStart;
	private static long upMs = 1200;
	private static int target;
	private static float startPitch;

	public static boolean active() {
		return phase != Phase.IDLE;
	}

	public static String phaseName() {
		return phase.name();
	}

	static void startUp(int tgt, int upTicks) {
		Minecraft mc = Minecraft.getInstance();
		target = tgt;
		upMs = Math.max(200, upTicks * 50L);
		startPitch = mc.player != null ? mc.player.getXRot() : 0f;
		phase = Phase.UP;
		phaseStart = System.currentTimeMillis();
	}

	static void startDown(int character) {
		target = character;
		Minecraft mc = Minecraft.getInstance();
		startPitch = mc.player != null ? mc.player.getXRot() : 0f;
		phase = Phase.DOWN;
		phaseStart = System.currentTimeMillis();
	}

	static void tick(Minecraft mc) {
		if (mc.player == null) {
			phase = Phase.IDLE;
			return;
		}
		long t = System.currentTimeMillis() - phaseStart;
		if (phase == Phase.UP && t >= upMs) {
			phase = Phase.HOLD;
			phaseStart = System.currentTimeMillis();
		} else if (phase == Phase.HOLD && t >= HOLD_TIMEOUT_MS) {
			startDown(target);
		} else if (phase == Phase.DOWN && t >= DOWN_MS) {
			phase = Phase.IDLE;
		}
	}

	/** 0 = on the ground, 1 = high in the sky. */
	public static float lift() {
		long t = System.currentTimeMillis() - phaseStart;
		return switch (phase) {
			case IDLE -> 0f;
			case UP -> {
				float u = Mth.clamp(t / (float) upMs, 0f, 1f);
				// three quick pulls like the GTA V switch: stepped ease
				float steps = 3f;
				float s = (float) Math.floor(u * steps);
				float f = u * steps - s;
				float e = f * f * (3 - 2 * f);
				yield Math.min(1f, (s + e) / steps);
			}
			case HOLD -> 1f;
			case DOWN -> {
				float u = Mth.clamp(t / (float) DOWN_MS, 0f, 1f);
				float e = 1 - (1 - u) * (1 - u) * (1 - u);
				yield 1f - e;
			}
		};
	}

	/** Camera override, called at the end of Camera.alignWithEntity. Returns null when inactive. */
	public static double[] camera(Entity e, float partialTicks) {
		if (phase == Phase.IDLE) return null;
		float k = lift();
		Vec3 base = new Vec3(Mth.lerp(partialTicks, e.xo, e.getX()), Mth.lerp(partialTicks, e.yo, e.getY()) + e.getEyeHeight(),
				Mth.lerp(partialTicks, e.zo, e.getZ()));
		float kk = Math.min(1f, k * 4f);
		double up = 3.0 * kk + HEIGHT * k * k;
		double back = 4.0 * kk * (1 - k);
		float yaw = e.getViewYRot(partialTicks);
		double rad = Math.toRadians(yaw);
		double bx = Math.sin(rad) * back, bz = -Math.cos(rad) * back;
		float pitch = Mth.lerp(Math.min(1f, k * 1.6f), startPitch, 89.9f);
		return new double[] {base.x + bx, base.y + up, base.z + bz, yaw, pitch};
	}

	static void extractOverlay(GuiGraphicsExtractor g, DeltaTracker dt) {
		if (phase == Phase.IDLE) return;
		Minecraft mc = Minecraft.getInstance();
		int w = g.guiWidth(), h = g.guiHeight();
		float k = lift();
		// desaturated blue-grey wash that thickens with height, and a white flash at the swap
		int a = (int) (k * 120);
		g.fill(0, 0, w, h, (a << 24) | 0x1c2a3a);
		if (phase == Phase.DOWN) {
			long t = System.currentTimeMillis() - phaseStart;
			int flash = (int) Math.max(0, 200 - t * 0.8);
			if (flash > 0) g.fill(0, 0, w, h, (flash << 24) | 0xffffff);
		}
		int bars = (int) (h * 0.08f * Math.min(1f, k * 3f));
		g.fill(0, 0, w, bars, 0xff000000);
		g.fill(0, h - bars, w, h, 0xff000000);
		Component name = Component.translatable("character.leonida.name." + Outfits.characterName(target));
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(w / 2f, h * 0.70f);
		pose.scale(2.5f, 2.5f);
		int alpha = (int) (Math.min(1f, k * 2f) * 255);
		if (alpha > 8) g.centeredText(mc.font, name, 0, 0, (alpha << 24) | 0xffffff);
		pose.popMatrix();
	}
}
