package dev.leonida.hud;

import dev.leonida.fx.FxConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Leonida Settings: performance preset plus every wired control. Left click (or the right arrow) raises a value,
 * right click (or the left arrow) lowers it. Changes apply live and save to config/leonida.json.
 */
public class SettingsScreen extends Screen {
	/** One row: label key, value text, step up and down. */
	public record Control(String key, Supplier<String> value, Runnable up, Runnable down) {}

	private final Screen parent;
	public final List<Control> controls = new ArrayList<>();
	private float rowX, rowY0, rowW, rowH;
	private float btnY, btnH, irisX, irisW, doneX, doneW;

	public SettingsScreen(Screen parent) {
		super(Component.translatable("screen.leonida.settings"));
		this.parent = parent;
		LeonidaSettings s = LeonidaSettings.get();
		controls.add(new Control("preset", () -> level(s.preset), () -> s.applyPreset(next(s.preset, 1)), () -> s.applyPreset(next(s.preset, -1))));
		controls.add(new Control("traffic", () -> s.trafficDensity + "%", () -> s.trafficDensity = Math.min(100, s.trafficDensity + 10), () -> s.trafficDensity = Math.max(0, s.trafficDensity - 10)));
		controls.add(new Control("peds", () -> s.pedDensity + "%", () -> s.pedDensity = Math.min(100, s.pedDensity + 10), () -> s.pedDensity = Math.max(0, s.pedDensity - 10)));
		controls.add(new Control("police", () -> String.valueOf(s.maxPolice), () -> s.maxPolice = Math.min(30, s.maxPolice + 2), () -> s.maxPolice = Math.max(2, s.maxPolice - 2)));
		controls.add(new Control("effects", () -> t("leonida.settings.fx." + s.effects.name().toLowerCase()),
				() -> s.effects = FxConfig.Quality.values()[Math.min(3, s.effects.ordinal() + 1)],
				() -> s.effects = FxConfig.Quality.values()[Math.max(0, s.effects.ordinal() - 1)]));
		controls.add(new Control("debris", () -> onOff(s.miningDebris), () -> s.miningDebris = !s.miningDebris, () -> s.miningDebris = !s.miningDebris));
		controls.add(new Control("minimap", () -> onOff(s.minimap), () -> s.minimap = !s.minimap, () -> s.minimap = !s.minimap));
		controls.add(new Control("minimap_rate", () -> t("leonida.settings.fps", s.minimapRate), () -> s.minimapRate = step(s.minimapRate, 1), () -> s.minimapRate = step(s.minimapRate, -1)));
		controls.add(new Control("shader", () -> level(s.shaderProfile), () -> s.shaderProfile = next(s.shaderProfile, 1), () -> s.shaderProfile = next(s.shaderProfile, -1)));
		controls.add(new Control("dh", () -> onOff(s.distantHorizons), () -> s.distantHorizons = !s.distantHorizons, () -> s.distantHorizons = !s.distantHorizons));
		controls.add(new Control("dh_radius", () -> t("leonida.settings.chunks", s.dhRadius), () -> s.dhRadius = Math.min(512, s.dhRadius * 2), () -> s.dhRadius = Math.max(32, s.dhRadius / 2)));
		controls.add(new Control("pregen", () -> t(Pregen.running() ? "leonida.settings.pregen.running" : "leonida.settings.pregen.go"),
				() -> pregen(true), () -> pregen(false)));
		controls.add(new Control("buildings", () -> onOff(s.fasterBuildings), () -> s.fasterBuildings = !s.fasterBuildings, () -> s.fasterBuildings = !s.fasterBuildings));
	}

	/** Runs /leonida pregen 16 (or stop) as the player; the server side does the work. */
	private static void pregen(boolean go) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		mc.player.connection.sendCommand(go && !Pregen.running() ? "leonida pregen 16" : "leonida pregen stop");
	}

	private static final int[] RATES = { 5, 10, 15, 30, 60 };

	private static int step(int v, int d) {
		int i = 0;
		for (int k = 0; k < RATES.length; k++) if (RATES[k] <= v) i = k;
		return RATES[Math.max(0, Math.min(RATES.length - 1, i + d))];
	}

	private static LeonidaSettings.Level next(LeonidaSettings.Level l, int d) {
		return LeonidaSettings.Level.values()[Math.max(0, Math.min(2, l.ordinal() + d))];
	}

	private static String t(String key, Object... args) { return Component.translatable(key, args).getString(); }
	private static String level(LeonidaSettings.Level l) { return t("leonida.settings.level." + l.name().toLowerCase()); }
	private static String onOff(boolean b) { return t(b ? "leonida.settings.on" : "leonida.settings.off"); }

	/** Steps a control (tests and clicks) and applies the result live. */
	public void change(String key, boolean up) {
		for (Control c : controls) if (c.key.equals(key)) { (up ? c.up : c.down).run(); LeonidaSettings.get().apply(); return; }
		throw new IllegalArgumentException(key);
	}

	@Override
	public boolean isPauseScreen() { return true; }

	@Override
	public void onClose() {
		LeonidaSettings.get().apply();
		Minecraft.getInstance().gui.setScreen(parent);
	}

	private double gs() { return Minecraft.getInstance().getWindow().getGuiScale(); }

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		Draw.begin(g);
		try {
			float u = Draw.U;
			Draw.rect(g, 0, 0, Draw.W, Draw.H, 0xE00B0F18);
			Draw.text(g, t("screen.leonida.settings"), Draw.BLACK, u * 4, u * 3, u * 4.2f, 0xFFFFFFFF, true);
			Draw.text(g, t("leonida.settings.hint"), Draw.BOLD, u * 4, u * 8.4f, u * 1.8f, 0xFFB8C0CC, false);
			rowW = Math.min(Draw.W - u * 8, u * 110);
			rowX = (Draw.W - rowW) / 2;
			rowY0 = u * 13;
			rowH = u * 5.6f;
			double mx = mouseX * gs(), my = mouseY * gs();
			for (int i = 0; i < controls.size(); i++) {
				Control c = controls.get(i);
				float y = rowY0 + i * rowH;
				boolean hover = mx >= rowX && mx < rowX + rowW && my >= y && my < y + rowH - u * 0.6f;
				Draw.roundRect(g, rowX, y, rowW, rowH - u * 0.6f, u * 0.8f, hover ? 0xC0303A4E : 0xA01A2130);
				float ty = y + (rowH - u * 0.6f - u * 2.4f) / 2;
				Draw.text(g, t("leonida.settings." + c.key), Draw.BOLD, rowX + u * 2, ty, u * 2.4f, c.key.equals("preset") ? 0xFFF2C14E : 0xFFFFFFFF, true);
				String v = c.value.get();
				float vw = Draw.width(v, Draw.BLACK, u * 2.4f);
				float right = rowX + rowW - u * 2;
				Draw.text(g, ">", Draw.BLACK, right - u * 1.4f, ty, u * 2.4f, 0xFFFF4FB0, false);
				Draw.text(g, v, Draw.BLACK, right - u * 3.2f - vw, ty, u * 2.4f, 0xFFFFFFFF, true);
				Draw.text(g, "<", Draw.BLACK, right - u * 5.0f - vw, ty, u * 2.4f, 0xFFFF4FB0, false);
			}
			btnY = rowY0 + controls.size() * rowH + u * 1.5f;
			btnH = u * 5;
			irisW = rowW * 0.48f;
			irisX = rowX;
			doneW = rowW * 0.48f;
			doneX = rowX + rowW - doneW;
			Draw.roundRect(g, irisX, btnY, irisW, btnH, u * 0.8f, 0xC0303A4E);
			Draw.textCentered(g, t("leonida.settings.iris"), Draw.BOLD, irisX + irisW / 2, btnY + (btnH - u * 2.4f) / 2, u * 2.4f, 0xFFFFFFFF, true);
			Draw.roundRect(g, doneX, btnY, doneW, btnH, u * 0.8f, 0xE0FF4FB0);
			Draw.textCentered(g, t("gui.done"), Draw.BLACK, doneX + doneW / 2, btnY + (btnH - u * 2.4f) / 2, u * 2.4f, 0xFFFFFFFF, true);
		} finally {
			Draw.end(g);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		double mx = e.x() * gs(), my = e.y() * gs();
		if (my >= btnY && my < btnY + btnH) {
			if (mx >= doneX && mx < doneX + doneW) { onClose(); return true; }
			if (mx >= irisX && mx < irisX + irisW) { openIris(); return true; }
		}
		if (mx < rowX || mx >= rowX + rowW || my < rowY0) return super.mouseClicked(e, doubleClick);
		int i = (int) ((my - rowY0) / rowH);
		if (i < 0 || i >= controls.size()) return super.mouseClicked(e, doubleClick);
		change(controls.get(i).key, e.button() == 0);
		return true;
	}

	/** Opens Iris's shader pack screen if Iris is installed (reflection, no compile dependency). */
	private void openIris() {
		try {
			Class<?> c = Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen");
			Screen s = (Screen) c.getConstructor(Screen.class).newInstance(this);
			Minecraft.getInstance().gui.setScreen(s);
		} catch (ReflectiveOperationException | RuntimeException ex) {
			// Iris absent or changed: nothing to open
		}
	}
}
