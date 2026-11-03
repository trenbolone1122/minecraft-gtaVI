package dev.leonida.hud;

import java.util.List;

/** Phone contacts with avatar textures (or coloured initials). */
public final class Contacts {
	private Contacts() {}

	public record Contact(String name, String avatar, int color, String number) {}

	public static final List<Contact> ALL = List.of(
			new Contact("Lucia", "avatar_lucia", 0xE85DA0, "(305) 555-0142"),
			new Contact("Jason", "avatar_jason", 0x468CC8, "(305) 555-0187"),
			new Contact("Cal", null, 0x7A5CC8, "(305) 555-0113"),
			new Contact("Boobie", null, 0xD8A030, "(305) 555-0166"),
			new Contact("Raul", null, 0x3AA070, "(305) 555-0121"),
			new Contact("Dre'Quan", null, 0xD05040, "(305) 555-0199"),
			new Contact("Real Dimez", null, 0xC040A0, "(305) 555-0175"),
			new Contact("Brian", null, 0x808890, "(305) 555-0104"));

	public static Contact get(String name) {
		for (Contact c : ALL) if (c.name.equalsIgnoreCase(name)) return c;
		int h = name.hashCode();
		int[] palette = {0x5A8FD0, 0xD05A8F, 0x5AD08F, 0xD0A05A, 0x8F5AD0};
		return new Contact(name, null, palette[Math.floorMod(h, palette.length)], "Unknown");
	}

	public static String initials(String name) {
		String[] parts = name.trim().split("\\s+");
		String s = parts[0].isEmpty() ? "?" : parts[0].substring(0, 1);
		if (parts.length > 1 && !parts[1].isEmpty()) s += parts[1].charAt(0);
		return s.toUpperCase(java.util.Locale.ROOT);
	}

	/** Draws a round avatar of diameter d at (x, y) in real pixels. */
	public static void drawAvatar(net.minecraft.client.gui.GuiGraphicsExtractor g, Contact c, float x, float y, float d) {
		if (c.avatar != null) {
			Draw.image(g, c.avatar, x, y, d, d, 128, 128, 0xFFFFFFFF);
		} else {
			Draw.circle(g, x + d / 2, y + d / 2, d / 2, 0xFF000000 | c.color);
			float px = d * 0.5f;
			Draw.textCentered(g, initials(c.name), Draw.BLACK, x + d / 2, y + d / 2 - px * 0.42f, px, 0xFFFFFFFF, false);
		}
	}
}
