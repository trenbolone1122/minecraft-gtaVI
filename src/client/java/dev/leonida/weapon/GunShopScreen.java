package dev.leonida.weapon;

import dev.leonida.core.PlayerState;
import dev.leonida.weapon.shop.Shop;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Ammu-Nation counter menu in the GTA style: red banner, dark list with section headers, white
 * highlight bar, price on the right, and a card with the item and its stats. Click, Enter or Space buys.
 */
public class GunShopScreen extends Screen {
	private static final int ROW = 11;
	private final BlockPos pos;
	/** Display rows: index into the catalog, or -1 - section for a header. */
	private final List<Integer> rows = new ArrayList<>();
	private int selected = 1;
	private int scroll;
	private String message = "";
	private int messageColor = 0xFFFFFFFF;
	private int messageTicks;
	private int ticks;

	private static final String[] SECTIONS = {"HANDGUNS AND SMGS", "RIFLES AND SHOTGUNS", "HEAVY", "MELEE", "AMMUNITION", "ARMOR"};

	public GunShopScreen(BlockPos pos) {
		super(Component.translatable("gui.leonida.weapon.shop"));
		this.pos = pos;
		List<Shop.Entry> cat = Shop.catalog();
		int last = -1;
		for (int i = 0; i < cat.size(); i++) {
			int sec = section(cat.get(i));
			if (sec != last) { rows.add(-1 - sec); last = sec; }
			rows.add(i);
		}
	}

	private static int section(Shop.Entry e) {
		return switch (e.kind()) {
			case GUN -> switch (e.gun()) {
				case PISTOL, SMG -> 0;
				case RIFLE, SHOTGUN, SNIPER -> 1;
				case RPG -> 2;
			};
			case MELEE -> 3;
			case AMMO -> 4;
			case ARMOR -> 5;
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, 0x30000000, 0x60000000);
	}

	private int panelX() { return Math.max(8, width / 16); }
	private int panelW() { return Math.min(200, width / 2 - 12); }
	private int listY() { return 8 + 26 + 13; }
	private int visibleRows() { return Math.max(4, (height - listY() - 26) / ROW); }

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return;
		List<Shop.Entry> cat = Shop.catalog();
		int x = panelX(), w = panelW(), y = 8;
		// banner
		g.fill(x, y, x + w, y + 26, 0xF0B3141C);
		g.fill(x, y + 24, x + w, y + 26, 0xFF6E0A10);
		g.pose().pushMatrix();
		g.pose().translate(x + w / 2f, y + 6);
		g.pose().scale(1.6f, 1.6f);
		Component title = Component.literal("AMMU-NATION").withStyle(s -> s.withBold(true));
		g.text(font, title, -font.width(title) / 2, 0, 0xFFFFFFFF, true);
		g.pose().popMatrix();
		// subheader: shop name and cash
		y += 26;
		g.fill(x, y, x + w, y + 13, 0xF0000000);
		g.text(font, "WEAPONS", x + 4, y + 3, 0xFFFFFFFF, false);
		long money = PlayerState.get(p).money();
		String cash = p.hasInfiniteMaterials() ? "CREATIVE" : "$" + String.format(Locale.ROOT, "%,d", money);
		g.text(font, cash, x + w - 4 - font.width(cash), y + 3, 0xFF7CD67C, false);
		// list
		y = listY();
		int vis = Math.min(visibleRows(), rows.size());
		scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - vis));
		g.fill(x, y, x + w, y + vis * ROW + 2, 0xD0101014);
		for (int r = 0; r < vis; r++) {
			int idx = rows.get(scroll + r);
			int ry = y + 1 + r * ROW;
			if (idx < 0) {
				g.fill(x, ry, x + w, ry + ROW, 0xE0262A30);
				g.text(font, SECTIONS[-1 - idx], x + 4, ry + 2, 0xFF9AA6B2, false);
				continue;
			}
			Shop.Entry e = cat.get(idx);
			boolean sel = scroll + r == selected;
			if (sel) g.fill(x, ry, x + w, ry + ROW, 0xFFF2F2F2);
			int fg = sel ? 0xFF111111 : 0xFFFFFFFF;
			String name = label(e);
			g.text(font, name, x + 8, ry + 2, fg, false);
			String price;
			int pc;
			if (e.kind() == Shop.Kind.GUN && Shop.owns(p, e.gun())) { price = "OWNED"; pc = sel ? 0xFF2F7D32 : 0xFF7CD67C; }
			else { price = "$" + String.format(Locale.ROOT, "%,d", e.price()); pc = !p.hasInfiniteMaterials() && money < e.price() ? 0xFFE05050 : fg; }
			g.text(font, price, x + w - 4 - font.width(price), ry + 2, pc, false);
		}
		if (rows.size() > vis) {
			int barH = Math.max(8, vis * ROW * vis / rows.size());
			int by = y + 1 + (vis * ROW - barH) * scroll / Math.max(1, rows.size() - vis);
			g.fill(x + w - 2, by, x + w, by + barH, 0x80FFFFFF);
		}
		// footer hint / result message
		int fy = y + vis * ROW + 4;
		g.fill(x, fy, x + w, fy + 13, 0xD0000000);
		if (messageTicks > 0) g.centeredText(font, message, x + w / 2, fy + 3, messageColor);
		else g.centeredText(font, "CLICK OR ENTER TO BUY, ESC TO LEAVE", x + w / 2, fy + 3, 0xFFB0B0B0);
		// item card
		int idx = selected >= 0 && selected < rows.size() ? rows.get(selected) : -1;
		if (idx >= 0) card(g, p, cat.get(idx), x + w + 8, 8);
	}

	private static String label(Shop.Entry e) {
		ItemStack s = e.stack().get();
		String n = s.getHoverName().getString();
		if (e.kind() == Shop.Kind.AMMO) n = n + " x" + s.getCount();
		return n;
	}

	private void card(GuiGraphicsExtractor g, LocalPlayer p, Shop.Entry e, int x, int y) {
		int w = Math.min(170, width - x - 8);
		if (w < 90) return;
		boolean gun = e.kind() == Shop.Kind.GUN;
		int h = gun ? 128 : 104;
		g.fill(x, y, x + w, y + h, 0xD0101014);
		g.fill(x, y, x + w, y + 2, 0xFFB3141C);
		ItemStack s = e.stack().get();
		if (gun) {
			// the side-view icon texture, drawn large and crisp
			var tex = dev.leonida.LeonidaMod.id("textures/item/weapon/" + e.gun().id + "_icon.png");
			g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex, x + w / 2 - 32, y + 6, 0f, 0f, 64, 64, 64, 64);
		} else {
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2f - 24, y + 14);
			g.pose().scale(3f, 3f);
			g.item(s, 0, 0);
			g.pose().popMatrix();
		}
		String name = s.getHoverName().getString().toUpperCase(Locale.ROOT);
		g.centeredText(font, name, x + w / 2, y + 76, 0xFFFFFFFF);
		if (gun) {
			var t = e.gun();
			float dmg = t == GunType.RPG ? 1f : Math.min(1f, t.damage * t.pellets / 30f);
			float rate = t.automatic ? Math.min(1f, 20f / t.fireDelay / 10f) : Math.min(1f, 20f / t.fireDelay / 12f);
			float acc = t == GunType.RPG ? 0.4f : Mth.clamp(1f - t.adsSpread / 4.5f, 0.05f, 1f);
			float range = Math.min(1f, t.range / 260f);
			stat(g, "DAMAGE", dmg, x + 6, y + 90, w - 12);
			stat(g, "FIRE RATE", rate, x + 6, y + 99, w - 12);
			stat(g, "ACCURACY", acc, x + 6, y + 108, w - 12);
			stat(g, "RANGE", range, x + 6, y + 117, w - 12);
		} else {
			String d = switch (e.kind()) {
				case AMMO -> "Rounds for the " + e.gun().displayName;
				case MELEE -> "Close quarters";
				case ARMOR -> "Soaks up gunfire";
				default -> "";
			};
			g.centeredText(font, d, x + w / 2, y + 90, 0xFF9AA6B2);
		}
	}

	private void stat(GuiGraphicsExtractor g, String label, float v, int x, int y, int w) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.75f, 0.75f);
		g.text(font, label, 0, 0, 0xFFB0B0B0, false);
		g.pose().popMatrix();
		int bx = x + 48, bw = w - 48;
		g.fill(bx, y + 1, bx + bw, y + 5, 0x60FFFFFF);
		g.fill(bx, y + 1, bx + Math.max(1, (int) (bw * v)), y + 5, 0xFFF2F2F2);
	}

	@Override
	public void tick() {
		ticks++;
		if (messageTicks > 0) messageTicks--;
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 8 * 8) onClose();
	}

	private void move(int d) {
		int n = rows.size();
		int s = selected;
		for (int k = 0; k < n; k++) {
			s = Math.floorMod(s + d, n);
			if (rows.get(s) >= 0) break;
		}
		select(s);
	}

	private void select(int s) {
		selected = s;
		int vis = Math.min(visibleRows(), rows.size());
		if (selected < scroll + 1) scroll = Math.max(0, selected - 1);
		if (selected >= scroll + vis) scroll = selected - vis + 1;
	}

	/** Selects the catalog entry with this key (used by tests). */
	public void selectKey(String key) {
		List<Shop.Entry> cat = Shop.catalog();
		for (int r = 0; r < rows.size(); r++) if (rows.get(r) >= 0 && cat.get(rows.get(r)).key().equals(key)) { select(r); return; }
	}

	/** Buys the selected row. */
	public void buy() {
		int idx = rows.get(selected);
		if (idx >= 0) ClientPlayNetworking.send(new WeaponNet.Buy(pos, idx));
	}

	/** Server reply. */
	public void result(int index, String result) {
		List<Shop.Entry> cat = Shop.catalog();
		String name = index >= 0 && index < cat.size() ? cat.get(index).stack().get().getHoverName().getString() : "";
		switch (result) {
			case "bought" -> { message = "PURCHASED: " + name.toUpperCase(Locale.ROOT); messageColor = 0xFF7CD67C; }
			case "owned" -> { message = "ALREADY OWNED, BUY AMMO"; messageColor = 0xFFFFD060; }
			default -> { message = "NOT ENOUGH CASH"; messageColor = 0xFFE05050; }
		}
		messageTicks = 60;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int k = event.key();
		if (k == InputConstants.KEY_DOWN || k == InputConstants.KEY_S) { move(1); return true; }
		if (k == InputConstants.KEY_UP || k == InputConstants.KEY_W) { move(-1); return true; }
		if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER || k == InputConstants.KEY_SPACE) { buy(); return true; }
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		move(sy < 0 ? 1 : -1);
		return true;
	}

	private int rowAt(double mx, double my) {
		int x = panelX(), w = panelW(), y = listY() + 1;
		if (mx < x || mx >= x + w || my < y) return -1;
		int r = (int) ((my - y) / ROW);
		int vis = Math.min(visibleRows(), rows.size());
		return r < vis ? scroll + r : -1;
	}

	@Override
	public void mouseMoved(double mx, double my) {
		int r = rowAt(mx, my);
		if (r >= 0 && rows.get(r) >= 0) selected = r;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int r = rowAt(event.x(), event.y());
		if (r >= 0 && rows.get(r) >= 0) {
			selected = r;
			buy();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}
}
