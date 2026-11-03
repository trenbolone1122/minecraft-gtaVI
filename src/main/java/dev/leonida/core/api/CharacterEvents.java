package dev.leonida.core.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/** Fired on the server by the character module when a player changes outfit or switches character. Gameplay clears the "clothes known" wanted flag. */
public final class CharacterEvents {
	private CharacterEvents() {}

	@FunctionalInterface
	public interface OutfitChanged {
		void onOutfitChanged(ServerPlayer player);
	}

	public static final Event<OutfitChanged> OUTFIT_CHANGED = EventFactory.createArrayBacked(OutfitChanged.class,
			ls -> p -> {
				for (OutfitChanged l : ls) l.onOutfitChanged(p);
			});

	public static void outfitChanged(ServerPlayer p) {
		OUTFIT_CHANGED.invoker().onOutfitChanged(p);
	}
}
