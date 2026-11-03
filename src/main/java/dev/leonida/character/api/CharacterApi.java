package dev.leonida.character.api;

import dev.leonida.character.Appearance;
import dev.leonida.character.CharacterSwitcher;
import dev.leonida.character.Outfits;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public character API for other modules (server side).
 * Listen to {@link #OUTFIT_CHANGED} to react when the player looks different (police lose the
 * "clothes known" flag). It fires on wardrobe changes and on every Jason/Lucia switch.
 */
public final class CharacterApi {
	private CharacterApi() {}

	@FunctionalInterface
	public interface OutfitChanged {
		/** @param switched true when the change came from switching character */
		void onOutfitChanged(ServerPlayer player, Outfits.Outfit outfit, boolean switched);
	}

	@FunctionalInterface
	public interface CharacterSwitched {
		void onSwitched(ServerPlayer player, int from, int to);
	}

	public static final Event<OutfitChanged> OUTFIT_CHANGED = EventFactory.createArrayBacked(OutfitChanged.class,
			ls -> (p, o, s) -> { for (OutfitChanged l : ls) l.onOutfitChanged(p, o, s); });

	public static final Event<CharacterSwitched> CHARACTER_SWITCHED = EventFactory.createArrayBacked(CharacterSwitched.class,
			ls -> (p, a, b) -> { for (CharacterSwitched l : ls) l.onSwitched(p, a, b); });

	/** Current outfit of the active character. */
	public static Outfits.Outfit outfit(ServerPlayer p) {
		return Appearance.get(p).outfit();
	}

	/** Change the active character's outfit (wardrobe, clothing shop). Fires OUTFIT_CHANGED. */
	public static void setOutfit(ServerPlayer p, int index) {
		CharacterSwitcher.setOutfit(p, index);
	}

	/** Start a GTA style switch (with the camera transition). Returns false if not allowed right now. */
	public static boolean requestSwitch(ServerPlayer p, int target) {
		return CharacterSwitcher.request(p, target, false);
	}
}
