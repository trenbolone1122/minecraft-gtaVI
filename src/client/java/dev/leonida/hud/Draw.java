package dev.leonida.hud;

import dev.leonida.LeonidaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Drawing helpers that work in real screen pixels: call {@link #begin} to scale the pose by 1/guiScale,
 * draw with pixel coordinates, then {@link #end}. Keeps the HUD crisp and independent of the GUI scale.
 */
public final class Draw {
	private Draw() {}

	public static final FontDescription BLACK = new FontDescription.Resource(LeonidaMod.id("hud/black"));
	public static final FontDescription BOLD = new FontDescription.Resource(LeonidaMod.id("hud/bold"));

	/** Real pixel size of the window. */
	public static int W, H;
	/** Unit used for layout: 1/100 of the window height. */
	public static float U;

	public static void begin(GuiGraphicsExtractor g) {
		var win = Minecraft.getInstance().getWindow();
		W = win.getWidth();
		H = win.getHeight();
		U = H / 100f;
		float s = 1f / win.getGuiScale();
		g.pose().pushMatrix();
		g.pose().scale(s, s);
	}

	public static void end(GuiGraphicsExtractor g) {
		g.pose().popMatrix();
	}

	public static Identifier tex(String name) {
		return LeonidaMod.id("textures/hud/" + name + ".png");
	}

	public static int argb(float a, int rgb) {
		int ai = Math.max(0, Math.min(255, Math.round(a * 255)));
		return (ai << 24) | (rgb & 0xFFFFFF);
	}

	public static int mulAlpha(int argb, float a) {
		int ai = Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, a)));
		return (ai << 24) | (argb & 0xFFFFFF);
	}

	/** Image blit at real pixel position/size (the whole texture). */
	public static void icon(GuiGraphicsExtractor g, String name, float x, float y, float w, float h, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(w / 64f, h / 64f);
		g.blit(RenderPipelines.GUI_TEXTURED, tex(name), 0, 0, 0, 0, 64, 64, 64, 64, 64, 64, color);
		g.pose().popMatrix();
	}

	/** Blit a texture of native size tw x th scaled to w x h. */
	public static void image(GuiGraphicsExtractor g, String name, float x, float y, float w, float h, int tw, int th, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(w / tw, h / th);
		g.blit(RenderPipelines.GUI_TEXTURED, tex(name), 0, 0, 0, 0, tw, th, tw, th, tw, th, color);
		g.pose().popMatrix();
	}

	/** Icon centred at (cx, cy) with optional rotation (radians). */
	public static void iconCentered(GuiGraphicsExtractor g, String name, float cx, float cy, float size, float rot, int color) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		if (rot != 0) g.pose().rotate(rot);
		g.pose().scale(size / 64f, size / 64f);
		g.blit(RenderPipelines.GUI_TEXTURED, tex(name), -32, -32, 0, 0, 64, 64, 64, 64, 64, 64, color);
		g.pose().popMatrix();
	}

	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
		g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
	}

	/** Filled rounded rectangle built from per-row spans (pixel exact). */
	public static void roundRect(GuiGraphicsExtractor g, float xf, float yf, float wf, float hf, float rf, int color) {
		int x = Math.round(xf), y = Math.round(yf), w = Math.round(wf), h = Math.round(hf);
		int r = Math.min(Math.round(rf), Math.min(w, h) / 2);
		if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
		g.fill(x, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
		}
	}

	/** Rounded rectangle outline of thickness t. */
	public static void roundRectOutline(GuiGraphicsExtractor g, float xf, float yf, float wf, float hf, float rf, int t, int color) {
		int x = Math.round(xf), y = Math.round(yf), w = Math.round(wf), h = Math.round(hf);
		int r = Math.min(Math.round(rf), Math.min(w, h) / 2);
		g.fill(x + r, y, x + w - r, y + t, color);
		g.fill(x + r, y + h - t, x + w - r, y + h, color);
		g.fill(x, y + r, x + t, y + h - r, color);
		g.fill(x + w - t, y + r, x + w, y + h - r, color);
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			double outer = r - Math.sqrt(Math.max(0, r * r - dy * dy));
			double ri = r - t;
			double inner = dy < ri ? r - Math.sqrt(ri * ri - dy * dy) : r;
			int a = (int) Math.round(outer), b = Math.max(a + 1, (int) Math.round(Math.min(inner, r)));
			g.fill(x + a, y + i, x + b, y + i + 1, color);
			g.fill(x + w - b, y + i, x + w - a, y + i + 1, color);
			g.fill(x + a, y + h - 1 - i, x + b, y + h - i, color);
			g.fill(x + w - b, y + h - 1 - i, x + w - a, y + h - i, color);
		}
	}

	public static void circle(GuiGraphicsExtractor g, float cx, float cy, float rad, int color) {
		int r = Math.round(rad);
		int x = Math.round(cx), y = Math.round(cy);
		for (int i = -r; i < r; i++) {
			double dy = i + 0.5;
			int half = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
			g.fill(x - half, y + i, x + half, y + i + 1, color);
		}
	}

	public static Component comp(String s, FontDescription font) {
		return Component.literal(s).withStyle(Style.EMPTY.withFont(font));
	}

	/** Width in real pixels of text drawn at the given pixel height. */
	public static float width(String s, FontDescription font, float px) {
		Font f = Minecraft.getInstance().font;
		return f.width(comp(s, font)) * px / 9f;
	}

	/** Draws text whose line height is px real pixels. Returns the width. */
	public static float text(GuiGraphicsExtractor g, String s, FontDescription font, float x, float y, float px, int color, boolean shadow) {
		Font f = Minecraft.getInstance().font;
		float sc = px / 9f;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(sc, sc);
		Component c = comp(s, font);
		if (shadow) {
			// soft GTA style drop shadow: dark copy offset by ~1 real pixel
			g.pose().pushMatrix();
			g.pose().translate(1.2f / sc, 1.2f / sc);
			g.text(f, c, 0, 0, mulAlpha(0xFF000000, ((color >>> 24) & 0xFF) / 255f * 0.75f), false);
			g.pose().popMatrix();
		}
		g.text(f, c, 0, 0, color, false);
		g.pose().popMatrix();
		return f.width(c) * sc;
	}

	public static float textRight(GuiGraphicsExtractor g, String s, FontDescription font, float right, float y, float px, int color, boolean shadow) {
		float w = width(s, font, px);
		text(g, s, font, right - w, y, px, color, shadow);
		return w;
	}

	public static float textCentered(GuiGraphicsExtractor g, String s, FontDescription font, float cx, float y, float px, int color, boolean shadow) {
		float w = width(s, font, px);
		text(g, s, font, cx - w / 2, y, px, color, shadow);
		return w;
	}

	/** Word wraps s to lines no wider than maxW at height px. */
	public static java.util.List<String> wrap(String s, FontDescription font, float px, float maxW) {
		java.util.List<String> out = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" ")) {
			String cand = line.isEmpty() ? word : line + " " + word;
			if (width(cand, font, px) > maxW && !line.isEmpty()) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(cand);
			}
		}
		if (!line.isEmpty()) out.add(line.toString());
		return out;
	}

	public static float ease(float t) {
		t = Math.max(0, Math.min(1, t));
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}
}
