package dev.leonida.hud;

import dev.leonida.core.PlayerState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** Social feed (phone app) and the livestream chat that scrolls while the LIVE badge is up. */
public final class Feed {
	private Feed() {}

	public record Post(String handle, String text, int color, int likes, long at) {}

	public record Comment(String handle, String text, int color, long at) {}

	public static final List<Post> POSTS = new ArrayList<>();
	public static final Deque<Comment> LIVE = new ArrayDeque<>();
	private static final Random RNG = new Random(51);
	private static long nextComment;
	private static int lastWanted;

	private static final String[] HANDLES = {"ninabrina_luvsu", "suxincok", "Brodiebae", "papi_mucho_dinero", "patriotpaul51",
			"SeamoreTittums", "that_vc_bhaddie", "SugarG1rl93", "keyskid305", "gatorgrl", "leonida_law", "realdimez",
			"ViceBeachBabe", "port_gellhorn_pat", "kortz_kid"};
	private static final String[] LIVE_LINES = {"Corre coño", "dayum", "Bro loves attention lol", "sup camilo and chat",
			"Dale dale dale", "KING OF TRUNK!!! KING OF TRUNK!!", "That's tuff", "gotchu on my phone in the background",
			"cops are RIGHT there", "this is the best stream ever", "not again lmaooo", "W", "LEONIDA NEVER CHANGES",
			"who's driving??", "clip it clip it", "bro is cooked", "turn left TURN LEFT", "chat is this real"};
	private static final String[] WANTED_POSTS = {"Helicopters over Vice City again. Somebody is having a day.",
			"Just saw someone run three red lights with half the VCPD behind them",
			"Whoever is getting chased on Ocean Drive rn, godspeed",
			"Police scanner is going CRAZY tonight"};

	static {
		long t = System.currentTimeMillis();
		POSTS.add(new Post("ViceBeachBabe", "Sunset on Ocean Drive hits different. Leonida forever.", 0xE85DA0, 2410, t));
		POSTS.add(new Post("gatorgrl", "Found a gator in my pool AGAIN. Third time this week.", 0x3AA070, 812, t));
		POSTS.add(new Post("keyskid305", "Who parked a boat on the highway??", 0x5A8FD0, 120, t));
		POSTS.add(new Post("realdimez", "New track dropping Friday. Vice City, you ready?", 0xC040A0, 15400, t));
	}

	public static int color(String handle) {
		int[] pal = {0xE85DA0, 0x5A8FD0, 0x3AA070, 0xD8A030, 0x7A5CC8, 0xD05040, 0x46B4C8};
		return pal[Math.floorMod(handle.hashCode(), pal.length)];
	}

	public static void post(String handle, String text) {
		POSTS.add(0, new Post(handle, text, color(handle), 10 + RNG.nextInt(900), System.currentTimeMillis()));
		while (POSTS.size() > 40) POSTS.remove(POSTS.size() - 1);
	}

	static void tick(PlayerState s) {
		long now = HudState.now();
		if (s.liveViewers() > 0) {
			if (now >= nextComment) {
				String h = HANDLES[RNG.nextInt(HANDLES.length)];
				LIVE.addLast(new Comment(h, LIVE_LINES[RNG.nextInt(LIVE_LINES.length)], color(h), now));
				while (LIVE.size() > 8) LIVE.removeFirst();
				nextComment = now + 700 + RNG.nextInt(1100) - Math.min(500, s.liveViewers() / 200);
			}
		} else if (!LIVE.isEmpty() && now >= nextComment) {
			LIVE.removeFirst();
			nextComment = now + 400;
		}
		if (s.wanted() > lastWanted && s.wanted() >= 2) post(HANDLES[RNG.nextInt(HANDLES.length)], WANTED_POSTS[RNG.nextInt(WANTED_POSTS.length)]);
		lastWanted = s.wanted();
	}
}
