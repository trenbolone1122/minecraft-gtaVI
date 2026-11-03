package dev.leonida.character;

import dev.leonida.LeonidaMod;
import dev.leonida.core.PlayerState;
import java.util.List;
import net.minecraft.resources.Identifier;

/** The wardrobe: every outfit of Jason and Lucia. Index order is stable (saved in worlds). */
public final class Outfits {
	private Outfits() {}

	/** 3D extras rendered on top of the skin (see the client AccessoryLayer). */
	public static final int ACC_PONYTAIL = 1, ACC_CAP_BACK = 2;

	/**
	 * @param id short name used in commands and lang keys
	 * @param texture skin asset id (resolves to textures/entity/character/...png)
	 */
	public record Outfit(String id, int character, int index, Identifier texture, boolean slim, int accessories) {
		public String langKey() {
			return "character.leonida.outfit." + characterName(character) + "." + id;
		}
	}

	public static final List<Outfit> JASON = List.of(
			jason(0, "everyday", 0),
			jason(1, "heist", 0),
			jason(2, "swim", ACC_CAP_BACK),
			jason(3, "casual", 0));

	public static final List<Outfit> LUCIA = List.of(
			lucia(0, "everyday", ACC_PONYTAIL),
			lucia(1, "heist", ACC_PONYTAIL),
			lucia(2, "swim", ACC_PONYTAIL),
			lucia(3, "jumpsuit", ACC_PONYTAIL));

	private static Outfit jason(int i, String id, int acc) {
		return new Outfit(id, PlayerState.JASON, i, LeonidaMod.id("entity/character/jason/" + id), false, acc);
	}

	private static Outfit lucia(int i, String id, int acc) {
		return new Outfit(id, PlayerState.LUCIA, i, LeonidaMod.id("entity/character/lucia/" + id), true, acc);
	}

	public static List<Outfit> forCharacter(int character) {
		return character == PlayerState.LUCIA ? LUCIA : JASON;
	}

	public static Outfit get(int character, int index) {
		List<Outfit> l = forCharacter(character);
		return l.get(Math.floorMod(index, l.size()));
	}

	public static int find(int character, String id) {
		for (Outfit o : forCharacter(character)) if (o.id().equals(id)) return o.index();
		return -1;
	}

	public static String characterName(int character) {
		return character == PlayerState.LUCIA ? "lucia" : "jason";
	}

	public static int other(int character) {
		return character == PlayerState.LUCIA ? PlayerState.JASON : PlayerState.LUCIA;
	}
}
