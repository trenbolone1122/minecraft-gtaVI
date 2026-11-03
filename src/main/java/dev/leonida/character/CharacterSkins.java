package dev.leonida.character;

import dev.leonida.LeonidaMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * Skin library for the people of Leonida, usable from server and client code.
 * Every skin is a standard 64x64 player skin (vanilla PlayerModel layout); slim=true means 3 px arms
 * (PlayerModel built with slim=true / ModelLayers.PLAYER_SLIM).
 *
 * Typical use (gameplay NPCs): store a long seed on the entity, then on the client
 * {@code CharacterSkins.randomPedestrian(seed).texture()} gives the texture to bind.
 * Same seed, same skin, on every machine.
 */
public final class CharacterSkins {
	private CharacterSkins() {}

	public enum Kind { LOCAL, BEACH, TOURIST, WORKER }

	/**
	 * @param texture full texture path, e.g. leonida:textures/entity/character/pedestrian/local_00.png
	 * @param assetId the same skin as a ClientAsset id (leonida:entity/character/...), for PlayerSkin.Patch
	 */
	public record PedSkin(Identifier texture, Identifier assetId, boolean slim, Kind kind) {}

	private static PedSkin ped(String name, Kind kind, boolean slim) {
		return skin("pedestrian/" + name, slim, kind);
	}

	private static PedSkin skin(String path, boolean slim) {
		return skin(path, slim, Kind.WORKER);
	}

	private static PedSkin skin(String path, boolean slim, Kind kind) {
		Identifier asset = LeonidaMod.id("entity/character/" + path);
		return new PedSkin(asset.withPath(p -> "textures/" + p + ".png"), asset, slim, kind);
	}

	public static final List<PedSkin> PEDESTRIANS = List.of(
			ped("local_00", Kind.LOCAL, false),
			ped("local_01", Kind.LOCAL, true),
			ped("local_02", Kind.LOCAL, false),
			ped("local_03", Kind.LOCAL, true),
			ped("local_04", Kind.LOCAL, false),
			ped("local_05", Kind.LOCAL, false),
			ped("local_06", Kind.LOCAL, true),
			ped("local_07", Kind.LOCAL, true),
			ped("local_08", Kind.LOCAL, true),
			ped("local_09", Kind.LOCAL, false),
			ped("local_10", Kind.LOCAL, true),
			ped("local_11", Kind.LOCAL, false),
			ped("local_12", Kind.LOCAL, false),
			ped("local_13", Kind.LOCAL, true),
			ped("local_14", Kind.LOCAL, true),
			ped("local_15", Kind.LOCAL, false),
			ped("beach_00", Kind.BEACH, true),
			ped("beach_01", Kind.BEACH, false),
			ped("beach_02", Kind.BEACH, false),
			ped("beach_03", Kind.BEACH, false),
			ped("beach_04", Kind.BEACH, true),
			ped("beach_05", Kind.BEACH, true),
			ped("beach_06", Kind.BEACH, true),
			ped("beach_07", Kind.BEACH, false),
			ped("beach_08", Kind.BEACH, true),
			ped("beach_09", Kind.BEACH, true),
			ped("beach_10", Kind.BEACH, true),
			ped("beach_11", Kind.BEACH, false),
			ped("tourist_00", Kind.TOURIST, true),
			ped("tourist_01", Kind.TOURIST, false),
			ped("tourist_02", Kind.TOURIST, false),
			ped("tourist_03", Kind.TOURIST, false),
			ped("tourist_04", Kind.TOURIST, true),
			ped("tourist_05", Kind.TOURIST, true),
			ped("tourist_06", Kind.TOURIST, true),
			ped("tourist_07", Kind.TOURIST, false),
			ped("tourist_08", Kind.TOURIST, false),
			ped("tourist_09", Kind.TOURIST, false),
			ped("worker_00", Kind.WORKER, true),
			ped("worker_01", Kind.WORKER, false),
			ped("worker_02", Kind.WORKER, false),
			ped("worker_03", Kind.WORKER, true),
			ped("worker_04", Kind.WORKER, true),
			ped("worker_05", Kind.WORKER, false),
			ped("worker_06", Kind.WORKER, false),
			ped("worker_07", Kind.WORKER, false),
			ped("worker_08", Kind.WORKER, false),
			ped("worker_09", Kind.WORKER, false)
	);

	/** VCPD (white or light-blue shirts, navy trousers; path without "sheriff") and Leonard County Sheriff deputies (tan and green; "sheriff" in the path). */
	public static final List<PedSkin> POLICE = List.of(
			skin("police/police_00", true),
			skin("police/police_01", false),
			skin("police/police_02", false),
			skin("police/police_03", false),
			skin("police/sheriff_04", true),
			skin("police/sheriff_05", false),
			skin("police/police_06", true),
			skin("police/police_07", false)
	);

	public static final List<PedSkin> SWAT = List.of(
			skin("swat/swat_00", false),
			skin("swat/swat_01", false),
			skin("swat/swat_02", false),
			skin("swat/swat_03", false)
	);

	private static final List<List<PedSkin>> BY_KIND = new ArrayList<>();
	static {
		for (Kind k : Kind.values()) BY_KIND.add(PEDESTRIANS.stream().filter(s -> s.kind() == k).toList());
	}

	private static int pick(long seed, int n) {
		long z = seed + 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z = z ^ (z >>> 31);
		return (int) Math.floorMod(z, (long) n);
	}

	/** Any Leonida local, beachgoer, tourist or worker. */
	public static PedSkin randomPedestrian(long seed) {
		return PEDESTRIANS.get(pick(seed, PEDESTRIANS.size()));
	}

	/** A pedestrian of one kind (beach strips: BEACH, downtown: WORKER and LOCAL, hotels: TOURIST). */
	public static PedSkin randomPedestrian(long seed, Kind kind) {
		List<PedSkin> l = BY_KIND.get(kind.ordinal());
		return l.get(pick(seed, l.size()));
	}

	public static PedSkin police(long seed) {
		return POLICE.get(pick(seed, POLICE.size()));
	}

	public static PedSkin swat(long seed) {
		return SWAT.get(pick(seed, SWAT.size()));
	}

	/** Fixed-name NPC skins in textures/entity/character/npc/ (ped_m_0..7, ped_f_0..7, police_m, police_f, swat, clerk). */
	public static PedSkin npc(String name) {
		boolean slim = name.startsWith("ped_f") || name.equals("police_f");
		return skin("npc/" + name, slim);
	}

	/** Districts with their own ambient pedestrian set (textures/entity/character/pedestrian/district/<id>_NN.png). */
	public enum District {
		/** Vice Beach: beachgoers in swimwear and tourists. */
		VICE_BEACH("vice_beach"),
		/** Downtown Vice City: suits, office workers, some trades. */
		DOWNTOWN("downtown"),
		/** Ocean Drive nightlife: party dresses, silk shirts, gold chains. */
		OCEAN_DRIVE("ocean_drive"),
		/** Leonida Keys: fishermen and boaters, caps and sun shirts. */
		KEYS("keys"),
		/** Grassrivers locals: flannel, camo, overalls, white rubber boots. */
		GRASSRIVERS("grassrivers"),
		/** Port Gellhorn suburbs: polos, tracksuits, older residents. */
		PORT_GELLHORN("port_gellhorn"),
		/** Anywhere else: the general mix of locals, beachgoers, tourists and workers. */
		OTHER("other");

		public final String id;

		District(String id) {
			this.id = id;
		}

		/**
		 * Best match for a map place name (MapQuery.placeName), case-insensitive. Unknown or null names give OTHER.
		 * Beaches map to VICE_BEACH, "downtown"/"financial"/"brickell" to DOWNTOWN, "ocean drive"/"nightlife" to OCEAN_DRIVE,
		 * "keys"/"marina"/"harbor" to KEYS, "grassrivers"/"everglade"/"swamp" to GRASSRIVERS, "gellhorn"/"suburb" to PORT_GELLHORN.
		 */
		public static District fromPlaceName(String name) {
			if (name == null) return OTHER;
			String n = name.toLowerCase(java.util.Locale.ROOT);
			if (n.contains("ocean drive") || n.contains("nightlife") || n.contains("club")) return OCEAN_DRIVE;
			if (n.contains("vice beach") || n.contains("beach") || n.contains("boardwalk")) return VICE_BEACH;
			if (n.contains("downtown") || n.contains("financial") || n.contains("brickell") || n.contains("business")) return DOWNTOWN;
			if (n.contains("keys") || n.contains("key ") || n.endsWith(" key") || n.contains("marina") || n.contains("harbor") || n.contains("harbour")) return KEYS;
			if (n.contains("grassriver") || n.contains("everglade") || n.contains("swamp") || n.contains("glades")) return GRASSRIVERS;
			if (n.contains("gellhorn") || n.contains("suburb")) return PORT_GELLHORN;
			for (District d : values()) if (n.contains(d.id.replace('_', ' '))) return d;
			return OTHER;
		}
	}

	private static PedSkin dped(String name, Kind kind, boolean slim) {
		return skin("pedestrian/district/" + name, slim, kind);
	}

	private static final java.util.EnumMap<District, List<PedSkin>> DISTRICT_SKINS = new java.util.EnumMap<>(District.class);
	static {
		DISTRICT_SKINS.put(District.VICE_BEACH, List.of(
				dped("vice_beach_00", Kind.BEACH, true),
				dped("vice_beach_01", Kind.BEACH, false),
				dped("vice_beach_02", Kind.BEACH, false),
				dped("vice_beach_03", Kind.TOURIST, false),
				dped("vice_beach_04", Kind.TOURIST, true),
				dped("vice_beach_05", Kind.BEACH, true),
				dped("vice_beach_06", Kind.TOURIST, true),
				dped("vice_beach_07", Kind.BEACH, false),
				dped("vice_beach_08", Kind.BEACH, true),
				dped("vice_beach_09", Kind.TOURIST, true),
				dped("vice_beach_10", Kind.BEACH, false),
				dped("vice_beach_11", Kind.TOURIST, true)));
		DISTRICT_SKINS.put(District.DOWNTOWN, List.of(
				dped("downtown_00", Kind.WORKER, true),
				dped("downtown_01", Kind.WORKER, true),
				dped("downtown_02", Kind.WORKER, false),
				dped("downtown_03", Kind.WORKER, false),
				dped("downtown_04", Kind.WORKER, false),
				dped("downtown_05", Kind.WORKER, false),
				dped("downtown_06", Kind.WORKER, false),
				dped("downtown_07", Kind.WORKER, false),
				dped("downtown_08", Kind.WORKER, false),
				dped("downtown_09", Kind.LOCAL, false),
				dped("downtown_10", Kind.WORKER, false),
				dped("downtown_11", Kind.WORKER, true)));
		DISTRICT_SKINS.put(District.OCEAN_DRIVE, List.of(
				dped("ocean_drive_00", Kind.LOCAL, true),
				dped("ocean_drive_01", Kind.LOCAL, true),
				dped("ocean_drive_02", Kind.LOCAL, true),
				dped("ocean_drive_03", Kind.LOCAL, true),
				dped("ocean_drive_04", Kind.LOCAL, true),
				dped("ocean_drive_05", Kind.LOCAL, false),
				dped("ocean_drive_06", Kind.LOCAL, false),
				dped("ocean_drive_07", Kind.LOCAL, false),
				dped("ocean_drive_08", Kind.LOCAL, false),
				dped("ocean_drive_09", Kind.TOURIST, false),
				dped("ocean_drive_10", Kind.LOCAL, true),
				dped("ocean_drive_11", Kind.LOCAL, false)));
		DISTRICT_SKINS.put(District.KEYS, List.of(
				dped("keys_00", Kind.LOCAL, false),
				dped("keys_01", Kind.LOCAL, true),
				dped("keys_02", Kind.LOCAL, true),
				dped("keys_03", Kind.LOCAL, false),
				dped("keys_04", Kind.LOCAL, true),
				dped("keys_05", Kind.LOCAL, true),
				dped("keys_06", Kind.LOCAL, true),
				dped("keys_07", Kind.LOCAL, false),
				dped("keys_08", Kind.LOCAL, false),
				dped("keys_09", Kind.BEACH, false),
				dped("keys_10", Kind.LOCAL, true),
				dped("keys_11", Kind.LOCAL, false)));
		DISTRICT_SKINS.put(District.GRASSRIVERS, List.of(
				dped("grassrivers_00", Kind.LOCAL, true),
				dped("grassrivers_01", Kind.LOCAL, false),
				dped("grassrivers_02", Kind.LOCAL, false),
				dped("grassrivers_03", Kind.LOCAL, true),
				dped("grassrivers_04", Kind.LOCAL, true),
				dped("grassrivers_05", Kind.LOCAL, true),
				dped("grassrivers_06", Kind.LOCAL, false),
				dped("grassrivers_07", Kind.LOCAL, false),
				dped("grassrivers_08", Kind.LOCAL, false),
				dped("grassrivers_09", Kind.LOCAL, true),
				dped("grassrivers_10", Kind.WORKER, false),
				dped("grassrivers_11", Kind.LOCAL, false)));
		DISTRICT_SKINS.put(District.PORT_GELLHORN, List.of(
				dped("port_gellhorn_00", Kind.LOCAL, false),
				dped("port_gellhorn_01", Kind.LOCAL, true),
				dped("port_gellhorn_02", Kind.LOCAL, false),
				dped("port_gellhorn_03", Kind.LOCAL, false),
				dped("port_gellhorn_04", Kind.LOCAL, true),
				dped("port_gellhorn_05", Kind.LOCAL, false),
				dped("port_gellhorn_06", Kind.LOCAL, false),
				dped("port_gellhorn_07", Kind.LOCAL, false),
				dped("port_gellhorn_08", Kind.LOCAL, true),
				dped("port_gellhorn_09", Kind.LOCAL, true),
				dped("port_gellhorn_10", Kind.WORKER, true),
				dped("port_gellhorn_11", Kind.LOCAL, false)));
	}

	/** The pedestrian set for a district (OTHER gives the general library). */
	public static List<PedSkin> pedestrians(District district) {
		return district == District.OTHER ? PEDESTRIANS : DISTRICT_SKINS.get(district);
	}

	/**
	 * Ambient pedestrian for a district. About one in six picks comes from the general library so crowds stay mixed.
	 * Same district and seed, same skin, on every machine.
	 */
	public static PedSkin pedestrian(District district, long seed) {
		if (district == null || district == District.OTHER || pick(seed ^ 0x5DEECE66DL, 6) == 0) return randomPedestrian(seed);
		List<PedSkin> l = DISTRICT_SKINS.get(district);
		return l.get(pick(seed, l.size()));
	}

	/** Convenience: pedestrian for a map place name, e.g. pedestrian(MapQuery.get().placeName(x, z), seed). */
	public static PedSkin pedestrian(String placeName, long seed) {
		return pedestrian(District.fromPlaceName(placeName), seed);
	}
}
