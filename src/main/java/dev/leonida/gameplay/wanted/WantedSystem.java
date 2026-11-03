package dev.leonida.gameplay.wanted;

import dev.leonida.core.PlayerState;
import dev.leonida.core.api.CrimeEvents;
import dev.leonida.core.api.CrimeEvents.Crime;
import dev.leonida.gameplay.npc.Pedestrian;
import dev.leonida.gameplay.npc.PoliceOfficer;
import dev.leonida.gameplay.police.PoliceDispatch;
import dev.leonida.gameplay.util.Compat;
import dev.leonida.gameplay.util.HudBridge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * GTA style wanted level. Crimes only count when somebody knows: police who see it raise the level
 * at once, pedestrians who see it phone it in after a delay (if they live that long). The four
 * "known" flags record what the police know (face, clothes, person, vehicle). When officers lose
 * sight the search phase starts (stars flash); staying out of sight long enough, or outside the
 * search area, clears the level. Changing clothes or vehicles while unseen wipes those flags, and
 * unrecognisable players can walk right past officers.
 */
public final class WantedSystem {
	private WantedSystem() {}

	public static final int LOSE_SIGHT_TICKS = 200;

	public static final class Tracker {
		public Vec3 lastSeenPos = Vec3.ZERO;
		public long lastSeenTick = -10000;
		public boolean searching;
		public Vec3 searchCenter = Vec3.ZERO;
		public double searchProgress;
		public UUID vehicleAtSighting;
		public long lastLethalTick = -100000;
		public long lastEscalateTick = -100000;
		public int viewerGain;
		public long lastFilmTick = -100000;
		public boolean initialised;
		public boolean textedThisSpree;
		public long heardAt = -1;
		public Crime heardCrime;
		public Vec3 heardPos;
	}

	private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();
	private static MinecraftServer server;

	public static void init() {
		CrimeEvents.CRIME.register(WantedSystem::onCrime);
		ServerTickEvents.END_SERVER_TICK.register(WantedSystem::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> TRACKERS.remove(h.getPlayer().getUUID()));
		Compat.onOutfitChanged(WantedSystem::onOutfitChanged);
	}

	public static Tracker tracker(ServerPlayer p) {
		return TRACKERS.computeIfAbsent(p.getUUID(), u -> new Tracker());
	}

	public static long now() {
		return server == null ? 0 : server.getTickCount();
	}

	// ------------------------------------------------------------------ crimes

	private static void onCrime(ServerPlayer p, Crime crime, BlockPos pos) {
		if (p.isCreative() || p.isSpectator() || PoliceDispatch.isBusted(p)) return;
		server = p.level().getServer();
		Tracker t = tracker(p);
		if (CrimeRules.lethal(crime)) t.lastLethalTick = now();
		Vec3 at = Vec3.atCenterOf(pos);
		ServerLevel level = p.level();

		double seen = policeSightDistance(p, 48);
		if (seen >= 0) {
			apply(p, crime, seen, true);
		} else {
			List<Pedestrian> witnesses = new ArrayList<>(level.getEntitiesOfClass(Pedestrian.class, new AABB(at, at).inflate(32),
					ped -> ped.isAlive() && ped.distanceToSqr(p) < 32 * 32 && ped.hasLineOfSight(p)));
			witnesses.sort(Comparator.comparingDouble(ped -> ped.distanceToSqr(p)));
			boolean reporter = false;
			for (Pedestrian ped : witnesses) {
				boolean wantReport = !reporter && (ped.isClerk() || witnesses.size() == 1 || ped.getRandom().nextFloat() < 0.7f);
				if (ped.witness(p, crime, at, wantReport)) reporter = true;
			}
			if (!reporter && CrimeRules.loud(crime) && t.heardAt < 0) {
				t.heardAt = now() + 300;
				t.heardCrime = crime;
				t.heardPos = at;
			}
		}
		// Bystanders further away still react to danger.
		if (CrimeRules.scary(crime)) {
			for (Pedestrian ped : level.getEntitiesOfClass(Pedestrian.class, new AABB(at, at).inflate(40))) ped.hearDanger(at, p);
		}
		if (CrimeRules.scary(crime) || crime == Crime.CARJACK || crime == Crime.HIT_PEDESTRIAN) chaos(p, crime);
	}

	/** A pedestrian finished the phone call. */
	public static void report(ServerPlayer p, Crime crime, double witnessDistance, boolean wasInVehicle) {
		if (!p.isAlive() || PoliceDispatch.isBusted(p)) return;
		apply(p, crime, witnessDistance, false);
		PlayerState s = PlayerState.get(p);
		if (wasInVehicle) PlayerState.set(p, s.withKnownFlags(s.knownFlags() | PlayerState.KNOWN_VEHICLE));
	}

	static void apply(ServerPlayer p, Crime crime, double dist, boolean policeSaw) {
		Tracker t = tracker(p);
		PlayerState s = PlayerState.get(p);
		int old = s.wanted();
		int stars = CrimeRules.stars(crime);
		if (crime == Crime.CAR_THEFT && Compat.isPoliceVehicle(p.getVehicle())) stars = 2;
		int w = Math.max(old, stars);
		if (old >= stars && CrimeRules.escalates(crime) && now() - t.lastEscalateTick > 300) {
			w = old + 1;
			t.lastEscalateTick = now();
		}
		int flags = s.knownFlags() | PlayerState.KNOWN_CLOTHES;
		if (dist < (policeSaw ? 20 : 10)) flags |= PlayerState.KNOWN_FACE;
		if (policeSaw && dist < 8) flags |= PlayerState.KNOWN_PERSON;
		if (p.getVehicle() != null) flags |= PlayerState.KNOWN_VEHICLE;
		s = s.withWanted(w).withKnownFlags(flags);
		PlayerState.set(p, s);
		t.initialised = true;
		if (policeSaw) {
			markSeen(p, t, dist);
		} else if (old == 0 || t.searching) {
			startSearch(p, t, p.position());
		}
		if (w >= 3 && old < 3 && !t.textedThisSpree) {
			t.textedThisSpree = true;
			HudBridge.text(p, s.character() == PlayerState.JASON ? "Lucia" : "Jason", w >= 5 ? "They sent the chopper. Get off the street!" : "Cops everywhere. Lose them, now.");
		}
		if (w > old) PoliceDispatch.onWantedRaised(p, old, w);
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer srv) {
		server = srv;
		long now = srv.getTickCount();
		for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
			Tracker t = tracker(p);
			if (now % 20 == 0) tickViewers(p, t, now);
			if (t.heardAt >= 0 && now >= t.heardAt) {
				t.heardAt = -1;
				if (PlayerState.get(p).wanted() == 0 && t.heardPos != null && t.heardPos.distanceTo(p.position()) < 96) {
					// "Shots fired" call: police come looking but know nothing about you.
					PlayerState s = PlayerState.get(p);
					PlayerState.set(p, s.withWanted(1));
					startSearch(p, t, t.heardPos);
					PoliceDispatch.onWantedRaised(p, 0, 1);
				}
			}
			if ((now + p.getId()) % 5 != 0) continue;
			PlayerState s = PlayerState.get(p);
			if (s.wanted() <= 0 || p.isCreative() || p.isSpectator()) {
				if (t.searching) HudBridge.searching(p, false);
				t.searching = false;
				t.textedThisSpree = false;
				if (s.knownFlags() != 0 && s.wanted() <= 0) PlayerState.set(p, s.withKnownFlags(0));
				continue;
			}
			if (PoliceDispatch.isBusted(p)) continue;
			if (!t.initialised) {
				// Loaded from disk with stars: begin in the search phase where the player stands.
				t.initialised = true;
				startSearch(p, t, p.position());
			}
			double d = policeSightDistance(p, p.isCrouching() ? 24 : 40);
			boolean recognised = d >= 0 && recognises(p, s, d);
			if (!t.searching) {
				if (recognised) markSeen(p, t, d);
				else if (now - t.lastSeenTick > LOSE_SIGHT_TICKS) startSearch(p, t, t.lastSeenPos);
			} else {
				if (recognised) {
					t.searching = false;
					HudBridge.searching(p, false);
					markSeen(p, t, d);
				} else {
					clearFlagsWhileHidden(p, t);
					double radius = searchRadius(s.wanted());
					boolean outside = p.position().distanceTo(t.searchCenter) > radius;
					int unknown = Integer.bitCount(~PlayerState.get(p).knownFlags() & 0xF);
					t.searchProgress += (outside ? 5 : 1.5) * (1 + 0.25 * unknown);
					if (t.searchProgress >= searchDuration(s.wanted())) clear(p, true);
				}
			}
		}
	}

	private static boolean recognises(ServerPlayer p, PlayerState s, double d) {
		int f = s.knownFlags();
		if (d < 6) return true;
		if ((f & PlayerState.KNOWN_FACE) != 0 && d < 20) return true;
		if ((f & PlayerState.KNOWN_CLOTHES) != 0) return true;
		if ((f & PlayerState.KNOWN_PERSON) != 0 && d < 24) return true;
		return (f & PlayerState.KNOWN_VEHICLE) != 0 && p.getVehicle() != null;
	}

	private static void markSeen(ServerPlayer p, Tracker t, double dist) {
		t.lastSeenTick = now();
		t.lastSeenPos = p.position();
		Entity v = p.getVehicle();
		t.vehicleAtSighting = v == null ? null : v.getUUID();
		PlayerState s = PlayerState.get(p);
		int f = s.knownFlags() | PlayerState.KNOWN_CLOTHES;
		if (dist < 16) f |= PlayerState.KNOWN_FACE;
		if (dist < 6) f |= PlayerState.KNOWN_PERSON;
		if (v != null) f |= PlayerState.KNOWN_VEHICLE;
		if (f != s.knownFlags()) PlayerState.set(p, s.withKnownFlags(f));
		if (t.searching) {
			t.searching = false;
			HudBridge.searching(p, false);
		}
	}

	private static void startSearch(ServerPlayer p, Tracker t, Vec3 center) {
		boolean was = t.searching;
		t.searching = true;
		t.searchCenter = center;
		t.searchProgress = 0;
		t.lastSeenTick = Math.min(t.lastSeenTick, now() - LOSE_SIGHT_TICKS - 1);
		if (!was) HudBridge.searching(p, true);
	}

	/** While nobody is watching: ditching or swapping the car wipes the vehicle flag. */
	private static void clearFlagsWhileHidden(ServerPlayer p, Tracker t) {
		PlayerState s = PlayerState.get(p);
		if ((s.knownFlags() & PlayerState.KNOWN_VEHICLE) == 0) return;
		Entity v = p.getVehicle();
		UUID now = v == null ? null : v.getUUID();
		if (t.vehicleAtSighting != null && !t.vehicleAtSighting.equals(now) && (now != null || p.getVehicle() == null)) {
			if (now != null) {
				PlayerState.set(p, s.withKnownFlags(s.knownFlags() & ~PlayerState.KNOWN_VEHICLE));
				HudBridge.toast(p, "New ride. The police are looking for the old one.");
			} else if (p.distanceToSqr(t.lastSeenPos) > 20 * 20) {
				PlayerState.set(p, s.withKnownFlags(s.knownFlags() & ~PlayerState.KNOWN_VEHICLE));
			}
		}
	}

	private static void onOutfitChanged(ServerPlayer p) {
		PlayerState s = PlayerState.get(p);
		if (s.wanted() <= 0 || (s.knownFlags() & PlayerState.KNOWN_CLOTHES) == 0) return;
		Tracker t = tracker(p);
		if (now() - t.lastSeenTick < 40 && !t.searching) return; // they watched you change
		PlayerState.set(p, s.withKnownFlags(s.knownFlags() & ~PlayerState.KNOWN_CLOTHES));
		t.searchProgress += searchDuration(s.wanted()) * 0.25;
		HudBridge.toast(p, "New clothes. The police no longer know your outfit.");
	}

	public static void clear(ServerPlayer p, boolean escaped) {
		Tracker t = tracker(p);
		PlayerState s = PlayerState.get(p);
		boolean had = s.wanted() > 0;
		PlayerState.set(p, s.withWanted(0).withKnownFlags(0));
		if (t.searching) HudBridge.searching(p, false);
		t.searching = false;
		t.searchProgress = 0;
		t.heardAt = -1;
		t.textedThisSpree = false;
		t.lastLethalTick = -100000;
		PoliceDispatch.standDown(p);
		if (escaped && had) HudBridge.toast(p, "You lost the police.");
	}

	/** Debug / commands: set the level and treat the player as seen right now. */
	public static void forceWanted(ServerPlayer p, int stars, boolean seen) {
		server = p.level().getServer();
		Tracker t = tracker(p);
		PlayerState s = PlayerState.get(p);
		int old = s.wanted();
		if (stars <= 0) {
			clear(p, false);
			return;
		}
		PlayerState.set(p, s.withWanted(stars).withKnownFlags(s.knownFlags() | PlayerState.KNOWN_CLOTHES | PlayerState.KNOWN_FACE));
		t.initialised = true;
		if (seen) markSeen(p, t, 10);
		else startSearch(p, t, p.position());
		if (stars > old) PoliceDispatch.onWantedRaised(p, old, stars);
	}

	public static double searchRadius(int wanted) {
		return 40 + 20 * wanted;
	}

	public static double searchDuration(int wanted) {
		return 400 + 200 * wanted;
	}

	/** Police currently have eyes on the player (not searching). */
	public static boolean isSeen(ServerPlayer p) {
		Tracker t = tracker(p);
		return !t.searching && now() - t.lastSeenTick < LOSE_SIGHT_TICKS;
	}

	public static boolean isSearching(ServerPlayer p) {
		return tracker(p).searching;
	}

	/** Attacking officers makes them shoot back. */
	public static void markLethal(ServerPlayer p) {
		tracker(p).lastLethalTick = now();
	}

	/** Officers shoot instead of trying to cuff. */
	public static boolean lethalForce(ServerPlayer p) {
		int w = PlayerState.get(p).wanted();
		Tracker t = tracker(p);
		return w >= 3 || now() - t.lastLethalTick < 1200 || (w >= 2 && Compat.isArmed(p));
	}

	// ------------------------------------------------------------------ sight

	/** Distance to the closest police eye (officer, police car, helicopter) with line of sight, or -1. */
	public static double policeSightDistance(ServerPlayer p, double range) {
		ServerLevel level = p.level();
		double best = -1;
		for (PoliceOfficer o : level.getEntitiesOfClass(PoliceOfficer.class, p.getBoundingBox().inflate(range), PoliceOfficer::isAlive)) {
			double d = o.distanceTo(p);
			if (d <= range && (best < 0 || d < best) && o.hasLineOfSight(p)) best = d;
		}
		if (best >= 0) return best;
		for (Entity e : level.getEntities((Entity) null, p.getBoundingBox().inflate(range), Compat::isPoliceVehicle)) {
			if (e == p.getVehicle()) continue;
			double d = e.distanceTo(p);
			if (d <= range && clearLine(level, e.position().add(0, 1.6, 0), p.getEyePosition(), e)) best = best < 0 ? d : Math.min(best, d);
		}
		if (best >= 0) return best;
		for (Entity heli : PoliceDispatch.helicoptersFor(p)) {
			double d = heli.distanceTo(p);
			if (d < 110 && clearLine(level, heli.position().add(0, -1, 0), p.getEyePosition(), heli)) return d;
		}
		return -1;
	}

	public static boolean clearLine(ServerLevel level, Vec3 from, Vec3 to, Entity source) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, source)).getType() == HitResult.Type.MISS;
	}

	// ------------------------------------------------------------------ live viewers

	/** Called by filming pedestrians every second. */
	public static void addViewers(ServerPlayer p, int n) {
		Tracker t = tracker(p);
		t.viewerGain += n;
	}

	private static void chaos(ServerPlayer p, Crime c) {
		PlayerState s = PlayerState.get(p);
		if (s.liveViewers() > 0) addViewers(p, 500 + p.getRandom().nextInt(2500));
	}

	private static void tickViewers(ServerPlayer p, Tracker t, long now) {
		PlayerState s = PlayerState.get(p);
		int v = s.liveViewers();
		if (t.viewerGain > 0) {
			v += t.viewerGain;
			t.viewerGain = 0;
			t.lastFilmTick = now;
		} else if (v > 0 && now - t.lastFilmTick > 200) {
			v = (int) (v * 0.85);
			if (v < 300) v = 0;
		}
		v = Math.min(v, 9_900_000);
		if (v != s.liveViewers()) {
			s = s.withLiveViewers(v);
			// Gone viral: everybody knows who you are now.
			if (v >= 25_000 && s.wanted() > 0) s = s.withKnownFlags(s.knownFlags() | PlayerState.KNOWN_PERSON);
			PlayerState.set(p, s);
		}
	}
}
