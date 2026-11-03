package dev.leonida.weapon;

import com.mojang.blaze3d.platform.InputConstants;
import dev.leonida.LeonidaMod;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** GTA style weapon wheel: hold Tab, point the mouse at a slot, release to equip. Slows time in singleplayer. */
public class WeaponWheelScreen extends Screen {
	private static final Identifier WHEEL = LeonidaMod.id("textures/gui/weapon/wheel.png");
	private static final Identifier[] SEL = new Identifier[8];
	static {
		for (int i = 0; i < 8; i++) SEL[i] = LeonidaMod.id("textures/gui/weapon/wheel_sel_" + i + ".png");
	}
	private static final String[] SLOT_NAMES = {"Unarmed", "Melee", "Pistol", "SMG", "Rifle", "Shotgun", "Sniper", "Heavy"};

	private final int[] slotInv = new int[8];
	private int selected = -1;
	private boolean done;
	private boolean physicalSeen;
	/** Lets tests point at a slot without moving the mouse. */
	public static int forcedSelection = -1;

	public WeaponWheelScreen() {
		super(Component.translatable("gui.leonida.weapon.wheel"));
	}

	@Override
	protected void init() {
		LocalPlayer p = Minecraft.getInstance().player;
		java.util.Arrays.fill(slotInv, -1);
		if (p != null) {
			var items = p.getInventory().getNonEquipmentItems();
			int sel = p.getInventory().getSelectedSlot();
			for (int i = 0; i < items.size(); i++) {
				int cat = category(items.get(i));
				if (cat < 0) continue;
				// prefer the hand, then the hotbar, then the backpack
				if (slotInv[cat] < 0 || i == sel) slotInv[cat] = i;
			}
			if (slotInv[0] < 0) {
				for (int i = 0; i < 9; i++) if (items.get(i).isEmpty()) { slotInv[0] = i; break; }
			}
			GunType held = WeaponItems.gunOf(p.getMainHandItem());
			selected = held != null ? held.category : -1;
		}
		ClientPlayNetworking.send(new WeaponNet.Wheel(true, -1));
	}

	static int category(ItemStack s) {
		GunType t = WeaponItems.gunOf(s);
		if (t != null) return t.category;
		if (s.is(WeaponItems.BAT) || s.is(WeaponItems.KNIFE)) return 1;
		return -1;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.fillGradient(0, 0, width, height, 0x50000000, 0x70000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int cx = width / 2, cy = height / 2;
		int size = Math.min(220, height - 20);
		int R = size / 2;
		double dx = mouseX - cx, dy = mouseY - cy;
		if (forcedSelection >= 0) selected = forcedSelection;
		else if (scrolled && mouseX == lastMouseX && mouseY == lastMouseY) {
			// keep the scrolled choice until the mouse moves
		} else if (dx * dx + dy * dy > 18 * 18) {
			scrolled = false;
			double ang = Math.toDegrees(Math.atan2(dx, -dy));
			if (ang < 0) ang += 360;
			selected = (int) Math.round(ang / 45.0) % 8;
		}
		g.blit(RenderPipelines.GUI_TEXTURED, WHEEL, cx - R, cy - R, 0, 0, size, size, 256, 256, 256, 256);
		if (selected >= 0) g.blit(RenderPipelines.GUI_TEXTURED, SEL[selected], cx - R, cy - R, 0, 0, size, size, 256, 256, 256, 256);
		LocalPlayer p = Minecraft.getInstance().player;
		for (int i = 0; i < 8; i++) {
			double ang = Math.toRadians(i * 45);
			int sx = cx + (int) (Math.sin(ang) * R * 0.70);
			int sy = cy - (int) (Math.cos(ang) * R * 0.70);
			int inv = slotInv[i];
			ItemStack st = p != null && inv >= 0 && i > 0 ? p.getInventory().getNonEquipmentItems().get(inv) : ItemStack.EMPTY;
			if (i == 0) {
				// a fist: simple pixel glyph
				int c = inv >= 0 ? 0xFFEDEDED : 0x60EDEDED;
				g.fill(sx - 7, sy - 5, sx + 7, sy + 5, c);
				g.fill(sx - 7, sy - 8, sx + 5, sy - 5, c);
				g.fill(sx - 5, sy - 5, sx - 4, sy + 1, 0xFF333333);
				g.fill(sx - 1, sy - 5, sx, sy + 1, 0xFF333333);
				g.fill(sx + 3, sy - 5, sx + 4, sy + 1, 0xFF333333);
			} else if (!st.isEmpty()) {
				g.pose().pushMatrix();
				g.pose().translate(sx - 16, sy - 16);
				g.pose().scale(2f, 2f);
				g.item(st, 0, 0);
				g.pose().popMatrix();
			} else {
				g.fill(sx - 6, sy - 1, sx + 6, sy + 1, 0x50FFFFFF);
			}
		}
		// centre: name of the selected slot and its ammo
		if (selected >= 0) {
			int inv = slotInv[selected];
			ItemStack st = p != null && inv >= 0 && selected > 0 ? p.getInventory().getNonEquipmentItems().get(inv) : ItemStack.EMPTY;
			String name = selected == 0 ? "UNARMED" : st.isEmpty() ? SLOT_NAMES[selected].toUpperCase(Locale.ROOT) : st.getHoverName().getString().toUpperCase(Locale.ROOT);
			String[] words = name.split(" ");
			// wrap long names into two lines to fit the hole
			String l1 = name, l2 = null;
			if (font.width(name) > R * 0.7 && words.length > 1) {
				int half = words.length / 2;
				l1 = String.join(" ", java.util.Arrays.copyOfRange(words, 0, half));
				l2 = String.join(" ", java.util.Arrays.copyOfRange(words, half, words.length));
			}
			int ty = cy - (l2 == null ? 8 : 13);
			g.centeredText(font, l1, cx, ty, 0xFFFFFFFF);
			if (l2 != null) g.centeredText(font, l2, cx, ty + 10, 0xFFFFFFFF);
			GunType t = WeaponItems.gunOf(st);
			if (t != null && p != null) {
				int reserve = p.hasInfiniteMaterials() ? Math.max(Ballistics.reserve(p, t), 999) : Ballistics.reserve(p, t);
				g.centeredText(font, GunItem.clip(st) + " / " + reserve, cx, ty + (l2 == null ? 12 : 22), 0xFFB8C4CC);
			}
		}
	}

	@Override
	public void tick() {
		// Release normally arrives as keyReleased; polling the physical key is a fallback for a lost
		// release event (only once the key has really been seen down, so injected input works too).
		var key = KeyMappingHelper.getBoundKeyOf(WeaponClient.keyWheel);
		if (forcedSelection < 0 && key.getType() == InputConstants.Type.KEYBOARD) {
			boolean down = InputConstants.isKeyDown(key.getValue());
			if (down) physicalSeen = true;
			else if (physicalSeen) confirm();
		}
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (WeaponClient.keyWheel.matches(event)) {
			confirm();
			return true;
		}
		return super.keyReleased(event);
	}

	/** Mouse wheel steps through the slots you have something in, like GTA. */
	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		int d = sy < 0 ? 1 : -1;
		int s = selected < 0 ? 0 : selected;
		for (int k = 0; k < 8; k++) {
			s = Math.floorMod(s + d, 8);
			if (slotInv[s] >= 0) { selected = s; scrolled = true; lastMouseX = (int) mx; lastMouseY = (int) my; break; }
		}
		return true;
	}

	/** Set once the wheel was scrolled, so mouse position does not override it until the mouse moves. */
	private boolean scrolled;
	private int lastMouseX = Integer.MIN_VALUE, lastMouseY = Integer.MIN_VALUE;

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		confirm();
		return true;
	}

	/** Equips the selected slot and closes the wheel. */
	public void confirm() {
		if (done) return;
		done = true;
		LocalPlayer p = Minecraft.getInstance().player;
		int send = -1;
		if (p != null && selected >= 0 && slotInv[selected] >= 0) {
			int inv = slotInv[selected];
			if (inv < 9) p.getInventory().setSelectedSlot(inv);
			else send = inv;
		}
		ClientPlayNetworking.send(new WeaponNet.Wheel(false, send));
		onClose();
	}

	@Override
	public void removed() {
		if (!done) {
			done = true;
			ClientPlayNetworking.send(new WeaponNet.Wheel(false, -1));
		}
		super.removed();
	}
}
