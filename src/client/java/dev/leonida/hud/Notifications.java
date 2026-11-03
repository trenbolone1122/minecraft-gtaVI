package dev.leonida.hud;

import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Text message popups, help toast, livestream chat, mission titles and the BUSTED / WASTED screens. */
public final class Notifications {
	private Notifications() {}

	public static void draw(GuiGraphicsExtractor g, DeltaTracker dt) {
		if (Minecraft.getInstance().player == null) return;
		Draw.begin(g);
		try {
			float y = Draw.U * 3f;
			y = toast(g, y);
			texts(g, y);
			liveChat(g);
			mission(g);
		} finally {
			Draw.end(g);
		}
	}

	/** Separate element so it draws above everything else. */
	public static void drawDeath(GuiGraphicsExtractor g, DeltaTracker dt) {
		Draw.begin(g);
		try {
			death(g);
		} finally {
			Draw.end(g);
		}
	}

	static float toast(GuiGraphicsExtractor g, float y) {
		if (HudState.toast == null) return y;
		long t = HudState.now() - HudState.toastAt;
		if (t > HudState.TOAST_MS) { HudState.toast = null; return y; }
		float u = Draw.U;
		float a = Math.min(1, t / 200f) * Math.min(1, (HudState.TOAST_MS - t) / 400f);
		float th = u * 1.9f, pad = u * 1.2f, maxW = u * 30f;
		List<String> lines = Draw.wrap(HudState.toast, Draw.BOLD, th, maxW - pad * 2);
		float bh = pad * 2 + lines.size() * th * 1.25f - th * 0.25f;
		float x = u * 3f;
		Draw.roundRect(g, x, y, maxW, bh, u * 0.6f, Draw.argb(0.82f * a, 0x0C0C10));
		float ly = y + pad;
		for (String l : lines) {
			Draw.text(g, l, Draw.BOLD, x + pad, ly, th, Draw.argb(a, 0xFFFFFF), false);
			ly += th * 1.25f;
		}
		return y + bh + u * 1.2f;
	}

	static void texts(GuiGraphicsExtractor g, float y) {
		float u = Draw.U;
		long now = HudState.now();
		for (HudState.Text msg : HudState.POPUPS) {
			long t = now - msg.at();
			float in = Draw.ease(t / 350f);
			float a = Math.min(in, Math.min(1, (HudState.TEXT_MS - t) / 500f));
			if (a <= 0) continue;
			float cw = u * 34f, pad = u * 1.3f, av = u * 5.2f;
			float x = u * 3f - (1 - in) * (cw + u * 4);
			float th = u * 1.95f;
			List<String> lines = Draw.wrap(msg.message(), Draw.BOLD, th, cw - av - pad * 3);
			if (lines.size() > 3) lines = lines.subList(0, 3);
			float ch = Math.max(av + pad * 2, pad * 2 + u * 1.5f + u * 0.6f + u * 2.2f + lines.size() * th * 1.2f);
			Draw.roundRect(g, x, y, cw, ch, u * 1.3f, Draw.argb(0.86f * a, 0x15151B));
			Contacts.Contact c = Contacts.get(msg.contact());
			Contacts.drawAvatar(g, c, x + pad, y + pad, av);
			float tx = x + pad * 2 + av, ty = y + pad;
			Draw.text(g, "MESSAGES", Draw.BOLD, tx, ty, u * 1.35f, Draw.argb(a * 0.6f, 0xFFFFFF), false);
			Draw.textRight(g, "now", Draw.BOLD, x + cw - pad, ty, u * 1.35f, Draw.argb(a * 0.6f, 0xFFFFFF), false);
			ty += u * 2.0f;
			Draw.text(g, c.name(), Draw.BLACK, tx, ty, u * 2.2f, Draw.argb(a, 0xFFFFFF), false);
			ty += u * 2.7f;
			for (String l : lines) {
				Draw.text(g, l, Draw.BOLD, tx, ty, th, Draw.argb(a * 0.92f, 0xFFFFFF), false);
				ty += th * 1.2f;
			}
			y += ch + u * 1.0f;
		}
	}

	static void liveChat(GuiGraphicsExtractor g) {
		if (Feed.LIVE.isEmpty()) return;
		float u = Draw.U;
		float x = Draw.W - u * 46f, y = u * 22f;
		long now = HudState.now();
		float av = u * 2.6f;
		int i = 0;
		for (Feed.Comment c : Feed.LIVE) {
			float a = Math.min(1, (now - c.at()) / 250f);
			Draw.circle(g, x + av / 2, y + av / 2, av / 2, Draw.argb(a, c.color()));
			Draw.textCentered(g, c.handle().substring(0, 1).toUpperCase(java.util.Locale.ROOT), Draw.BLACK, x + av / 2, y + av * 0.22f, av * 0.6f, Draw.argb(a, 0xFFFFFF), false);
			Draw.text(g, "@" + c.handle(), Draw.BLACK, x + av + u * 1.0f, y + u * 0.3f, u * 2.0f, Draw.argb(a, 0xFFFFFF), true);
			Draw.text(g, c.text(), Draw.BOLD, x, y + av + u * 0.6f, u * 1.9f, Draw.argb(a, 0xFFFFFF), true);
			y += av + u * 3.4f;
			i++;
		}
	}

	static void mission(GuiGraphicsExtractor g) {
		if (HudState.missionTitle == null) return;
		long t = HudState.now() - HudState.missionAt;
		if (t > HudState.MISSION_MS) { HudState.missionTitle = null; return; }
		float u = Draw.U;
		float a = Math.min(1, t / 250f) * Math.min(1, (HudState.MISSION_MS - t) / 600f);
		float sc = 1.15f - 0.15f * Draw.ease(t / 400f);
		float cy = Draw.H * 0.28f;
		float th = u * 6.2f * sc;
		Draw.rect(g, 0, cy - u * 2.5f, Draw.W, th + u * 9f, Draw.argb(0.62f * a, 0x05070C));
		Draw.textCentered(g, HudState.missionTitle, Draw.BLACK, Draw.W / 2f, cy, th, Draw.argb(a, 0xF2C14E), true);
		if (HudState.missionSub != null && !HudState.missionSub.isEmpty()) {
			int col = HudState.missionSub.startsWith("+$") ? 0x6BCB77 : 0xFFFFFF;
			Draw.textCentered(g, HudState.missionSub, Draw.BLACK, Draw.W / 2f, cy + th + u * 1.2f, u * 3.0f, Draw.argb(a, col), true);
		}
	}

	static void death(GuiGraphicsExtractor g) {
		float p = HudState.deathProgress();
		if (p < 0) return;
		boolean busted = HudState.deathKind == 1;
		float u = Draw.U;
		float in = Math.min(1, p / 0.08f), out = Math.min(1, (1 - p) / 0.12f);
		float a = Math.min(in, out);
		// darken + colour wash (busted: cold blue, wasted: blood red)
		Draw.rect(g, 0, 0, Draw.W, Draw.H, Draw.argb(0.45f * a, 0x000000));
		Draw.rect(g, 0, 0, Draw.W, Draw.H, Draw.argb(0.22f * a, busted ? 0x1A3060 : 0x701010));
		float textIn = Math.min(1, Math.max(0, (p - 0.06f) / 0.08f));
		float ta = Math.min(textIn, out);
		float bandH = u * 15f, cy = Draw.H * 0.5f;
		Draw.rect(g, 0, cy - bandH / 2, Draw.W, bandH, Draw.argb(0.62f * ta, 0x000000));
		String word = busted ? "BUSTED" : "WASTED";
		float th = u * 10f * (1.25f - 0.25f * Draw.ease(textIn));
		Draw.textCentered(g, word, Draw.BLACK, Draw.W / 2f, cy - th * 0.42f, th, Draw.argb(ta, busted ? 0xF4F4F4 : 0xD8343C), true);
	}
}
