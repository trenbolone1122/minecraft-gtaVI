package dev.leonida.hud;

import dev.leonida.core.PlayerState;
import dev.leonida.core.api.AmmoProvider;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Top right stack (wanted stars, known flags, ammo + weapon, money) and GTA style health/armour bars. */
public final class StatusHud {
	private StatusHud() {}

	private static final String[] FLAG_ICONS = {"flag_face", "flag_clothes", "flag_person", "flag_car"};
	private static final int[] FLAG_BITS = {PlayerState.KNOWN_FACE, PlayerState.KNOWN_CLOTHES, PlayerState.KNOWN_PERSON, PlayerState.KNOWN_VEHICLE};

	public static void draw(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Draw.begin(g);
		try {
			topRight(g, mc.player);
			bars(g, mc.player);
		} finally {
			Draw.end(g);
		}
	}

	/** Bottom of the top right stack (real px), for notifications that sit under it. */
	public static float stackBottom;
	/** Left edge of the star row. */
	public static float starsLeft;

	static void topRight(GuiGraphicsExtractor g, Player p) {
		float u = Draw.U;
		float right = Draw.W - u * 3.1f;
		float cy = u * 2.6f;
		PlayerState s = HudState.player();
		long now = HudState.now();
		float step = u * 3.45f;
		starsLeft = right;
		if (s.wanted() > 0) {
			float alpha = 1f;
			if (HudState.searching) alpha = 0.25f + 0.75f * (float) (0.5 + 0.5 * Math.cos(now / 160.0));
			boolean justRaised = now - HudState.wantedChangedAt < 2000;
			for (int i = 0; i < s.wanted(); i++) {
				float sx = right - step * (s.wanted() - 1 - i) - step / 2;
				float a = alpha;
				if (justRaised && i == s.wanted() - 1) a = ((now / 120) % 2 == 0) ? 1f : 0.2f;
				Draw.iconCentered(g, "star", sx, cy + u * 1.4f, u * 2.9f, 0, Draw.argb(a, 0xFFFFFF));
			}
			starsLeft = right - step * s.wanted();
			cy += u * 3.3f;
			// known flags, right aligned in the star columns
			int n = 0;
			for (int i = 0; i < 4; i++) if ((s.knownFlags() & FLAG_BITS[i]) != 0) n++;
			int k = 0;
			for (int i = 0; i < 4; i++) {
				if ((s.knownFlags() & FLAG_BITS[i]) == 0) continue;
				float fx = right - step * (n - 1 - k) - step / 2;
				Draw.iconCentered(g, FLAG_ICONS[i], fx, cy + u * 1.2f, u * 2.4f, 0, 0xFFFFFFFF);
				k++;
			}
			if (n > 0) cy += u * 3.1f;
			cy += u * 0.4f;
		}
		// ammo + weapon
		ItemStack held = p.getMainHandItem();
		AmmoProvider.Ammo ammo = null;
		try {
			ammo = held.isEmpty() ? null : AmmoProvider.get().ammo(p, held);
		} catch (RuntimeException ignored) {}
		if (ammo != null) {
			float th = u * 3.0f;
			float rw = Draw.textRight(g, String.valueOf(ammo.reserve()), Draw.BLACK, right, cy, th, 0xFFD6D8E0, true);
			Draw.textRight(g, String.valueOf(ammo.clip()), Draw.BLACK, right - rw - u * 1.1f, cy, th, ammo.clip() == 0 ? 0xFFE04848 : 0xFFFFFFFF, true);
			cy += th + u * 0.6f;
			String icon = weaponIcon(ammo.weaponName());
			float iw = icon.equals("weapon_pistol") ? u * 13f : u * 17f, ih = iw * 96f / 256f;
			Draw.image(g, icon, right - iw + iw * 0.04f, cy - ih * 0.15f, iw, ih, 256, 96, 0xFFFFFFFF);
			cy += ih * 0.75f + u * 0.6f;
		}
		// money
		float mh = u * 2.4f;
		String money = "$" + String.format(Locale.ROOT, "%,d", s.money());
		float mw = Draw.textRight(g, money.substring(1), Draw.BLACK, right, cy, mh, 0xFFFFFFFF, true);
		Draw.textRight(g, "$", Draw.BLACK, right - mw - u * 0.2f, cy, mh, 0xFF6BCB77, true);
		cy += mh + u * 0.4f;
		long since = now - HudState.moneyDeltaAt;
		if (HudState.moneyDelta != 0 && since < 3500) {
			float a = since < 3000 ? 1f : 1f - (since - 3000) / 500f;
			long d = HudState.moneyDelta;
			String t = (d > 0 ? "+$" : "-$") + String.format(Locale.ROOT, "%,d", Math.abs(d));
			Draw.textRight(g, t, Draw.BLACK, right, cy, u * 2.0f, Draw.argb(a, d > 0 ? 0x6BCB77 : 0xE04848), true);
			cy += u * 2.4f;
		}
		stackBottom = cy;
		// LIVE badge, left of the stars
		if (s.liveViewers() > 0) {
			String t = "LIVE " + viewers(s.liveViewers());
			float th = u * 2.1f;
			float bw = Draw.width(t, Draw.BLACK, th) + u * 2.0f, bh = u * 3.3f;
			float bx = Math.min(starsLeft, right - u * 14f) - u * 6f - bw, by = u * 8.0f;
			Draw.roundRect(g, bx, by, bw, bh, u * 0.5f, 0xF0D9252F);
			Draw.text(g, t, Draw.BLACK, bx + u * 1.0f, by + (bh - th) / 2 + u * 0.15f, th, 0xFFFFFFFF, false);
		}
	}

	public static String viewers(int v) {
		if (v >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", v / 1_000_000.0).replace(".0M", "M");
		if (v >= 10_000) return (v / 1000) + "k";
		if (v >= 1000) return String.format(Locale.ROOT, "%.1fk", v / 1000.0).replace(".0k", "k");
		return String.valueOf(v);
	}

	static String weaponIcon(String name) {
		String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
		if (n.matches(".*(pistol|glock|revolver|handgun|deagle|colt|9mm).*")) return "weapon_pistol";
		if (n.matches(".*(smg|uzi|mac|micro|mp5|tec).*")) return "weapon_smg";
		if (n.matches(".*(shotgun|pump|sawn|12g).*")) return "weapon_shotgun";
		if (n.matches(".*(sniper|marksman|rifle_long|bolt).*")) return "weapon_sniper";
		if (n.matches(".*(rpg|launcher|rocket|grenade_l).*")) return "weapon_launcher";
		if (n.matches(".*(knife|bat|melee|machete|crowbar).*")) return "weapon_melee";
		return "weapon_rifle";
	}

	/** Health (green, red when low) and armour (blue) bars under the minimap, GTA style. */
	static void bars(GuiGraphicsExtractor g, Player p) {
		float u = Draw.U;
		float x = Minimap.x, w = Minimap.w;
		if (w <= 0) return;
		float y = Minimap.y + Minimap.h + u * 0.7f, bh = u * 0.85f, gap = u * 0.45f;
		float half = (w - gap) / 2;
		float hp = Math.max(0, Math.min(1, p.getHealth() / p.getMaxHealth()));
		float armor = Math.max(0, Math.min(1, p.getArmorValue() / 20f));
		boolean low = hp < 0.25f;
		int hpCol = low ? ((HudState.now() / 300) % 2 == 0 ? 0xFFE04848 : 0xFFA03030) : 0xFF5DB962;
		Draw.rect(g, x - 1, y - 1, w + 2, bh + 2, 0xA0000000);
		Draw.rect(g, x, y, half, bh, low ? 0xD0501414 : 0xD0233F25);
		Draw.rect(g, x, y, half * hp, bh, hpCol);
		Draw.rect(g, x + half + gap, y, half, bh, 0xD01A2E50);
		Draw.rect(g, x + half + gap, y, half * armor, bh, 0xFF4F9BE0);
	}
}
