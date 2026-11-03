package dev.leonida.hud;

import dev.leonida.core.PlayerState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

/** Client side HUD runtime state: popups, screens, money deltas, phone history. */
public final class HudState {
	private HudState() {}

	public static long now() { return Util.getMillis(); }

	// ---- player state ------------------------------------------------------------------------
	public static PlayerState player() {
		Player p = Minecraft.getInstance().player;
		if (p == null || PlayerState.TYPE == null) return PlayerState.DEFAULT;
		PlayerState s = p.getAttached(PlayerState.TYPE);
		return s == null ? PlayerState.DEFAULT : s;
	}

	public static boolean searching;
	/** Test hook / fallback: force searching on. */
	public static long wantedChangedAt;
	static int lastWanted;

	// ---- money delta -------------------------------------------------------------------------
	static long lastMoney = Long.MIN_VALUE;
	public static long moneyDelta;
	public static long moneyDeltaAt;

	// ---- text messages -----------------------------------------------------------------------
	public record Text(String contact, String message, long at) {}

	public static final Deque<Text> POPUPS = new ArrayDeque<>();
	public static final List<Text> HISTORY = new ArrayList<>();
	public static final long TEXT_MS = 7000;

	public static void text(String contact, String message) {
		Text t = new Text(contact, message, now());
		POPUPS.addFirst(t);
		while (POPUPS.size() > 3) POPUPS.removeLast();
		HISTORY.add(t);
		Phone.unread++;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6f, 1.6f);
	}

	// ---- help toast + mission ----------------------------------------------------------------
	public static String toast;
	public static long toastAt;
	public static final long TOAST_MS = 7000;

	public static void toast(String msg) { toast = msg; toastAt = now(); }

	public static String missionTitle, missionSub;
	public static long missionAt;
	public static final long MISSION_MS = 5000;

	public static void mission(String title, String sub) { missionTitle = title; missionSub = sub; missionAt = now(); }

	// ---- busted / wasted ---------------------------------------------------------------------
	/** 0 none, 1 busted, 2 wasted. */
	public static int deathKind;
	public static long deathAt;
	public static final long DEATH_MS = 6000;

	public static void busted() { deathKind = 1; deathAt = now(); }

	public static void wasted() { deathKind = 2; deathAt = now(); }

	public static float deathProgress() {
		if (deathKind == 0) return -1;
		long t = now() - deathAt;
		if (t > DEATH_MS) { deathKind = 0; return -1; }
		return t / (float) DEATH_MS;
	}

	// ---- per tick ----------------------------------------------------------------------------
	static boolean wasDead;

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null) { lastMoney = Long.MIN_VALUE; return; }
		PlayerState s = player();
		if (lastMoney != Long.MIN_VALUE && s.money() != lastMoney) {
			long d = s.money() - lastMoney;
			// accumulate while the previous delta is still on screen
			if (now() - moneyDeltaAt < 3000 && Long.signum(d) == Long.signum(moneyDelta)) moneyDelta += d;
			else moneyDelta = d;
			moneyDeltaAt = now();
		}
		lastMoney = s.money();
		if (s.wanted() != lastWanted) {
			if (s.wanted() > lastWanted) wantedChangedAt = now();
			if (s.wanted() == 0) searching = false;
			lastWanted = s.wanted();
		}
		boolean dead = p.isDeadOrDying();
		if (dead && !wasDead && deathKind != 1) wasted();
		wasDead = dead;
		POPUPS.removeIf(t -> now() - t.at > TEXT_MS);
		Feed.tick(s);
	}
}
