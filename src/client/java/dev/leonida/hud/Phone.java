package dev.leonida.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/** The phone: home screen with Messages, Contacts, Map, Camera and the Blast social feed. Does not pause. */
public class Phone extends Screen {
	public static int unread;

	public enum App { HOME, MESSAGES, THREAD, CONTACTS, CALL, BLAST, CAMERA }

	static final String[][] APPS = {
			{"messages", "Messages"}, {"contacts", "Contacts"}, {"map", "Maps"},
			{"camera", "Camera"}, {"social", "Blast"}, {"phone", "Phone"}};

	public App app = App.HOME;
	private int sel;
	private String thread;
	private Contacts.Contact calling;
	private long callAt, openedAt, flashAt;
	private float scroll;

	/** Phone rectangle in real pixels (valid after a frame). */
	private float px, py, pw, ph;

	public Phone() {
		super(Component.translatable("screen.leonida.hud.phone"));
	}

	public Phone(App start) {
		this();
		app = start;
	}

	@Override
	protected void init() { openedAt = HudState.now(); }

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {}

	private double gs() { return Minecraft.getInstance().getWindow().getGuiScale(); }

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Draw.begin(g);
		try {
			if (app == App.CAMERA) { camera(g); return; }
			float u = Draw.U;
			pw = u * 27f;
			ph = u * 54f;
			float rise = Draw.ease((HudState.now() - openedAt) / 260f);
			px = Draw.W - pw - u * 6f;
			py = Draw.H - ph - u * 4f + (1 - rise) * (ph + u * 6);
			// body
			Draw.roundRect(g, px - u * 0.8f, py - u * 0.8f, pw + u * 1.6f, ph + u * 1.6f, u * 4.2f, 0xFF0B0B0E);
			Draw.roundRect(g, px - u * 0.9f, py - u * 0.9f + 1, pw + u * 1.8f, ph + u * 1.8f - 2, u * 4.3f, 0x30FFFFFF);
			Draw.roundRect(g, px - u * 0.8f, py - u * 0.8f, pw + u * 1.6f, ph + u * 1.6f, u * 4.2f, 0xFF0B0B0E);
			wallpaper(g);
			statusBar(g);
			switch (app) {
				case HOME -> home(g);
				case MESSAGES -> messages(g);
				case THREAD -> thread(g);
				case CONTACTS -> contacts(g);
				case CALL -> call(g);
				case BLAST -> blast(g);
				default -> {}
			}
			// home bar
			Draw.roundRect(g, px + pw / 2 - u * 4.5f, py + ph - u * 1.6f, u * 9f, u * 0.5f, u * 0.25f, 0xCCFFFFFF);
		} finally {
			Draw.end(g);
		}
	}

	private void wallpaper(GuiGraphicsExtractor g) {
		float u = Draw.U;
		int steps = Math.max(40, (int) ph);
		float r = u * 3.4f;
		for (int i = 0; i < steps; i++) {
			float t = i / (float) (steps - 1);
			int c;
			if (app == App.HOME) c = 0xFF000000 | lerp(lerp(0x2A1B5E, 0xE0507E, t), 0xF6A15A, Math.max(0, t - 0.6f) / 0.4f);
			else c = 0xFF000000 | lerp(0x17171D, 0x101014, t);
			float y0 = py + ph * i / steps, y1 = py + ph * (i + 1) / steps;
			// clip corners of the screen with rounded spans
			float inset = 0;
			float top = y0 - py, bot = py + ph - y1;
			if (top < r) inset = (float) (r - Math.sqrt(Math.max(0, r * r - (r - top) * (r - top))));
			if (bot < r) inset = (float) (r - Math.sqrt(Math.max(0, r * r - (r - bot) * (r - bot))));
			Draw.rect(g, px + inset, y0, pw - inset * 2, y1 - y0 + 1, c);
		}
		if (app == App.HOME) {
			// palm silhouette + sun
			Draw.circle(g, px + pw * 0.68f, py + ph * 0.66f, u * 4.5f, 0x55FFE0A0);
			Draw.rect(g, px, py + ph * 0.78f, pw, u * 0.4f, 0x40FFFFFF);
		}
	}

	static int lerp(int a, int b, float t) { return MapRaster.mix(a, b, Math.max(0, Math.min(1, t))); }

	private void statusBar(GuiGraphicsExtractor g) {
		float u = Draw.U;
		Minecraft mc = Minecraft.getInstance();
		long dayTime = mc.level != null ? mc.level.getOverworldClockTime() % 24000 : 6000;
		int hours = (int) ((dayTime / 1000 + 6) % 24), mins = (int) ((dayTime % 1000) * 60 / 1000);
		Draw.text(g, String.format(Locale.ROOT, "%d:%02d", hours == 0 ? 12 : hours > 12 ? hours - 12 : hours, mins), Draw.BLACK, px + u * 2.6f, py + u * 1.6f, u * 1.7f, 0xFFFFFFFF, false);
		// notch
		Draw.roundRect(g, px + pw / 2 - u * 4f, py + u * 1.0f, u * 8f, u * 2.2f, u * 1.1f, 0xFF000000);
		// signal + battery
		float bx = px + pw - u * 2.6f;
		Draw.roundRect(g, bx - u * 3.0f, py + u * 1.75f, u * 3.0f, u * 1.4f, u * 0.4f, 0xFFFFFFFF);
		for (int i = 0; i < 4; i++) Draw.rect(g, bx - u * 7.5f + i * u * 0.8f, py + u * 3.0f - (i + 1) * u * 0.32f, u * 0.5f, (i + 1) * u * 0.32f, 0xFFFFFFFF);
	}

	private void header(GuiGraphicsExtractor g, String title, boolean back) {
		float u = Draw.U;
		if (back) Draw.text(g, "< Back", Draw.BOLD, px + u * 2f, py + u * 4.6f, u * 1.7f, 0xFFFF7BC4, false);
		Draw.text(g, title, Draw.BLACK, px + u * 2f, py + u * 7.2f, u * 3.2f, 0xFFFFFFFF, false);
	}

	private float[] appRect(int i) {
		float u = Draw.U;
		float s = u * 5.6f, gapX = (pw - 3 * s) / 4, gapY = u * 4.2f;
		int col = i % 3, row = i / 3;
		return new float[] {px + gapX + col * (s + gapX), py + u * 13f + row * (s + gapY), s};
	}

	private void home(GuiGraphicsExtractor g) {
		float u = Draw.U;
		Minecraft mc = Minecraft.getInstance();
		long dayTime = mc.level != null ? mc.level.getOverworldClockTime() % 24000 : 6000;
		int hours = (int) ((dayTime / 1000 + 6) % 24), mins = (int) ((dayTime % 1000) * 60 / 1000);
		Draw.textCentered(g, String.format(Locale.ROOT, "%d:%02d", hours == 0 ? 12 : hours > 12 ? hours - 12 : hours, mins), Draw.BLACK, px + pw / 2, py + u * 5.2f, u * 5f, 0xFFFFFFFF, true);
		for (int i = 0; i < APPS.length; i++) {
			float[] r = appRect(i);
			if (i == sel) Draw.roundRect(g, r[0] - u * 0.5f, r[1] - u * 0.5f, r[2] + u, r[2] + u, u * 1.8f, 0x66FFFFFF);
			Draw.image(g, "app_" + APPS[i][0], r[0], r[1], r[2], r[2], 128, 128, 0xFFFFFFFF);
			Draw.textCentered(g, APPS[i][1], Draw.BOLD, r[0] + r[2] / 2, r[1] + r[2] + u * 0.7f, u * 1.3f, 0xFFFFFFFF, true);
			if (i == 0 && unread > 0) {
				Draw.circle(g, r[0] + r[2] - u * 0.3f, r[1] + u * 0.3f, u * 1.1f, 0xFFE0283C);
				Draw.textCentered(g, String.valueOf(Math.min(99, unread)), Draw.BLACK, r[0] + r[2] - u * 0.3f, r[1] - u * 0.35f, u * 1.3f, 0xFFFFFFFF, false);
			}
		}
		// dock hint
		Draw.textCentered(g, "Enter: open    Backspace: back", Draw.BOLD, px + pw / 2, py + ph - u * 5f, u * 1.2f, 0xCCFFFFFF, false);
	}

	private Map<String, List<HudState.Text>> conversations() {
		Map<String, List<HudState.Text>> m = new LinkedHashMap<>();
		for (int i = HudState.HISTORY.size() - 1; i >= 0; i--) {
			HudState.Text t = HudState.HISTORY.get(i);
			m.computeIfAbsent(t.contact(), k -> new ArrayList<>()).add(0, t);
		}
		return m;
	}

	private void messages(GuiGraphicsExtractor g) {
		float u = Draw.U;
		header(g, "Messages", true);
		Map<String, List<HudState.Text>> conv = conversations();
		float y = py + u * 12f;
		int i = 0;
		if (conv.isEmpty()) Draw.textCentered(g, "No messages yet", Draw.BOLD, px + pw / 2, y + u * 6, u * 1.8f, 0x99FFFFFF, false);
		for (Map.Entry<String, List<HudState.Text>> e : conv.entrySet()) {
			if (y > py + ph - u * 8) break;
			if (i == sel) Draw.rect(g, px, y - u * 0.6f, pw, u * 6.4f, 0x30FFFFFF);
			Contacts.Contact c = Contacts.get(e.getKey());
			Contacts.drawAvatar(g, c, px + u * 1.6f, y, u * 4.6f);
			Draw.text(g, c.name(), Draw.BLACK, px + u * 7.2f, y + u * 0.3f, u * 1.9f, 0xFFFFFFFF, false);
			String last = e.getValue().get(e.getValue().size() - 1).message();
			if (Draw.width(last, Draw.BOLD, u * 1.55f) > pw - u * 9) {
				while (last.length() > 3 && Draw.width(last + "...", Draw.BOLD, u * 1.55f) > pw - u * 9) last = last.substring(0, last.length() - 1);
				last += "...";
			}
			Draw.text(g, last, Draw.BOLD, px + u * 7.2f, y + u * 2.7f, u * 1.55f, 0xAAFFFFFF, false);
			Draw.rect(g, px + u * 7.2f, y + u * 5.6f, pw - u * 7.2f, 1, 0x22FFFFFF);
			y += u * 6.4f;
			i++;
		}
	}

	private void thread(GuiGraphicsExtractor g) {
		float u = Draw.U;
		Contacts.Contact c = Contacts.get(thread);
		header(g, c.name(), true);
		List<HudState.Text> msgs = conversations().getOrDefault(thread, List.of());
		float th = u * 1.7f, maxW = pw * 0.72f;
		// lay out from the bottom up
		float y = py + ph - u * 4f;
		for (int i = msgs.size() - 1; i >= 0 && y > py + u * 12f; i--) {
			List<String> lines = Draw.wrap(msgs.get(i).message(), Draw.BOLD, th, maxW - u * 2.4f);
			float bw = 0;
			for (String l : lines) bw = Math.max(bw, Draw.width(l, Draw.BOLD, th));
			bw += u * 2.4f;
			float bh = lines.size() * th * 1.25f + u * 1.6f;
			y -= bh + u * 1.0f;
			Draw.roundRect(g, px + u * 1.5f, y, bw, bh, u * 1.6f, 0xFF2C2C34);
			float ly = y + u * 0.9f;
			for (String l : lines) {
				Draw.text(g, l, Draw.BOLD, px + u * 2.7f, ly, th, 0xFFFFFFFF, false);
				ly += th * 1.25f;
			}
		}
	}

	private void contacts(GuiGraphicsExtractor g) {
		float u = Draw.U;
		header(g, "Contacts", true);
		float y = py + u * 12f - scroll;
		for (int i = 0; i < Contacts.ALL.size(); i++) {
			Contacts.Contact c = Contacts.ALL.get(i);
			if (y > py + ph - u * 7) break;
			if (y >= py + u * 11f) {
				if (i == sel) Draw.rect(g, px, y - u * 0.5f, pw, u * 5.2f, 0x30FFFFFF);
				Contacts.drawAvatar(g, c, px + u * 1.6f, y, u * 4f);
				Draw.text(g, c.name(), Draw.BLACK, px + u * 6.8f, y + u * 0.4f, u * 1.9f, 0xFFFFFFFF, false);
				Draw.text(g, c.number(), Draw.BOLD, px + u * 6.8f, y + u * 2.5f, u * 1.4f, 0x99FFFFFF, false);
			}
			y += u * 5.2f;
		}
	}

	private void call(GuiGraphicsExtractor g) {
		float u = Draw.U;
		Draw.rect(g, px + u * 2, py + u * 4, pw - u * 4, 1, 0x00000000);
		Contacts.drawAvatar(g, calling, px + pw / 2 - u * 6, py + u * 12, u * 12);
		Draw.textCentered(g, calling.name(), Draw.BLACK, px + pw / 2, py + u * 27, u * 3.2f, 0xFFFFFFFF, false);
		long t = HudState.now() - callAt;
		String status = t < 4000 ? "calling" + ".".repeat((int) (t / 400 % 4)) : "No answer";
		Draw.textCentered(g, status, Draw.BOLD, px + pw / 2, py + u * 31.5f, u * 1.9f, 0xBBFFFFFF, false);
		Draw.circle(g, px + pw / 2, py + ph - u * 10, u * 3.2f, 0xFFE0283C);
		Draw.textCentered(g, "End", Draw.BOLD, px + pw / 2, py + ph - u * 6f, u * 1.5f, 0xFFFFFFFF, false);
		if (t > 6000) app = App.CONTACTS;
	}

	private void blast(GuiGraphicsExtractor g) {
		float u = Draw.U;
		header(g, "Blast", true);
		float y = py + u * 12f - scroll;
		for (Feed.Post p : Feed.POSTS) {
			List<String> lines = Draw.wrap(p.text(), Draw.BOLD, u * 1.6f, pw - u * 9.5f);
			float h = u * 3.4f + lines.size() * u * 2.0f + u * 2.6f;
			if (y > py + ph - u * 4) break;
			if (y + h > py + u * 11f) {
				Draw.circle(g, px + u * 3.6f, y + u * 2f, u * 2f, 0xFF000000 | p.color());
				Draw.textCentered(g, p.handle().substring(0, 1).toUpperCase(Locale.ROOT), Draw.BLACK, px + u * 3.6f, y + u * 1.1f, u * 1.9f, 0xFFFFFFFF, false);
				Draw.text(g, "@" + p.handle(), Draw.BLACK, px + u * 6.8f, y + u * 0.4f, u * 1.6f, 0xFFFFFFFF, false);
				float ly = y + u * 2.8f;
				for (String l : lines) {
					Draw.text(g, l, Draw.BOLD, px + u * 6.8f, ly, u * 1.6f, 0xDDFFFFFF, false);
					ly += u * 2.0f;
				}
				Draw.text(g, "\u2665 " + String.format(Locale.ROOT, "%,d", p.likes()), Draw.BOLD, px + u * 6.8f, ly + u * 0.3f, u * 1.35f, 0xFFFF7BC4, false);
				Draw.rect(g, px + u * 1.5f, y + h - u * 0.6f, pw - u * 3, 1, 0x22FFFFFF);
			}
			y += h;
		}
	}

	private void camera(GuiGraphicsExtractor g) {
		float u = Draw.U;
		int W = Draw.W, H = Draw.H;
		int c = 0xE6FFFFFF;
		float m = u * 6, l = u * 5, t = Math.max(2, u * 0.35f);
		// viewfinder corners
		Draw.rect(g, m, m, l, t, c); Draw.rect(g, m, m, t, l, c);
		Draw.rect(g, W - m - l, m, l, t, c); Draw.rect(g, W - m - t, m, t, l, c);
		Draw.rect(g, m, H - m - t, l, t, c); Draw.rect(g, m, H - m - l, t, l, c);
		Draw.rect(g, W - m - l, H - m - t, l, t, c); Draw.rect(g, W - m - t, H - m - l, t, l, c);
		Draw.rect(g, W / 2f - u, H / 2f - t / 2, u * 2, t, 0x99FFFFFF);
		Draw.rect(g, W / 2f - t / 2, H / 2f - u, t, u * 2, 0x99FFFFFF);
		Draw.circle(g, m + u * 2.5f, m + u * 3.5f, u * 0.8f, (HudState.now() / 500) % 2 == 0 ? 0xFFE0283C : 0x00000000);
		Draw.text(g, "REC", Draw.BLACK, m + u * 4f, m + u * 2.6f, u * 1.8f, 0xFFFFFFFF, true);
		Draw.textCentered(g, "Click / Enter: take photo     Backspace: back", Draw.BOLD, W / 2f, H - m - u * 4f, u * 1.9f, 0xFFFFFFFF, true);
		Draw.circle(g, W / 2f, H - m - u * 9f, u * 3f, 0xFFFFFFFF);
		Draw.circle(g, W / 2f, H - m - u * 9f, u * 2.4f, 0xFFDDDDDD);
		long since = HudState.now() - flashAt;
		if (since < 250) Draw.rect(g, 0, 0, W, H, Draw.argb(1 - since / 250f, 0xFFFFFF));
	}

	private void takePhoto() {
		Minecraft mc = Minecraft.getInstance();
		flashAt = HudState.now();
		try {
			net.minecraft.client.Screenshot.grab(mc, false);
			HudState.toast("Photo saved to Gallery");
			Feed.post("you", "Just snapped this in Leonida #nofilter");
		} catch (RuntimeException e) {
			HudState.toast("Camera failed: " + e.getMessage());
		}
		if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 2f);
	}

	// ---------------------------------------------------------------------------------------- input

	private int itemCount() {
		return switch (app) {
			case HOME -> APPS.length;
			case MESSAGES -> conversations().size();
			case CONTACTS -> Contacts.ALL.size();
			default -> 0;
		};
	}

	private void open(int i) {
		switch (app) {
			case HOME -> {
				switch (APPS[i][0]) {
					case "messages" -> { app = App.MESSAGES; unread = 0; }
					case "contacts", "phone" -> app = App.CONTACTS;
					case "map" -> { Minecraft.getInstance().gui.setScreen(new MapScreen()); return; }
					case "camera" -> app = App.CAMERA;
					case "social" -> app = App.BLAST;
					default -> {}
				}
			}
			case MESSAGES -> {
				List<String> names = new ArrayList<>(conversations().keySet());
				if (i < names.size()) { thread = names.get(i); app = App.THREAD; }
			}
			case CONTACTS -> { calling = Contacts.ALL.get(i); callAt = HudState.now(); app = App.CALL; }
			default -> {}
		}
		sel = 0;
		scroll = 0;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.8f);
	}

	private void back() {
		switch (app) {
			case HOME -> onClose();
			case THREAD -> app = App.MESSAGES;
			case CALL -> app = App.CONTACTS;
			default -> app = App.HOME;
		}
		sel = 0;
		scroll = 0;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (HudClient.PHONE_KEY.matches(e) && app == App.HOME && e.key() != InputConstants.KEY_UP) { onClose(); return true; }
		int n = itemCount();
		switch (e.key()) {
			case InputConstants.KEY_BACKSPACE -> { back(); return true; }
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> {
				if (app == App.CAMERA) takePhoto();
				else if (n > 0) open(sel);
				return true;
			}
			case InputConstants.KEY_RIGHT -> { if (n > 0) sel = (sel + 1) % n; return true; }
			case InputConstants.KEY_LEFT -> { if (n > 0) sel = (sel + n - 1) % n; return true; }
			case InputConstants.KEY_DOWN -> { if (n > 0) sel = Math.min(n - 1, sel + (app == App.HOME ? 3 : 1)); else scroll += Draw.U * 4; return true; }
			case InputConstants.KEY_UP -> { if (n > 0) sel = Math.max(0, sel - (app == App.HOME ? 3 : 1)); else scroll = Math.max(0, scroll - Draw.U * 4); return true; }
			default -> { return super.keyPressed(e); }
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		double mx = e.x() * gs(), my = e.y() * gs();
		if (e.button() == 1) { back(); return true; }
		if (app == App.CAMERA) { takePhoto(); return true; }
		float u = Draw.U;
		if (mx < px || mx > px + pw || my < py || my > py + ph) { onClose(); return true; }
		if (app != App.HOME && my < py + u * 6.5f) { back(); return true; }
		switch (app) {
			case HOME -> {
				for (int i = 0; i < APPS.length; i++) {
					float[] r = appRect(i);
					if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[2]) { open(i); return true; }
				}
			}
			case MESSAGES -> { int i = (int) ((my - (py + u * 11.4f)) / (u * 6.4f)); if (i >= 0 && i < itemCount()) open(i); }
			case CONTACTS -> { int i = (int) ((my + scroll - (py + u * 11.5f)) / (u * 5.2f)); if (i >= 0 && i < itemCount()) open(i); }
			case CALL -> { if (my > py + ph - u * 14) app = App.CONTACTS; }
			default -> {}
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double sx, double sy) {
		scroll = Math.max(0, scroll - (float) sy * Draw.U * 4);
		return true;
	}
}
