package dev.leonida.core.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired on the server whenever a player commits a crime. Weapons, vehicles, aircraft and gameplay
 * fire it; gameplay listens and turns it into wanted stars, witnesses and police response.
 */
public final class CrimeEvents {
	private CrimeEvents() {}

	public enum Crime {
		ASSAULT, GUNSHOT, KILL_CIVILIAN, KILL_COP, CAR_THEFT, CARJACK, RECKLESS_DRIVING, HIT_PEDESTRIAN,
		ROBBERY, PROPERTY_DAMAGE, EXPLOSION, TRESPASS, AIRCRAFT_THEFT
	}

	@FunctionalInterface
	public interface Listener {
		void onCrime(ServerPlayer player, Crime crime, BlockPos pos);
	}

	public static final Event<Listener> CRIME = EventFactory.createArrayBacked(Listener.class,
			ls -> (player, crime, pos) -> {
				for (Listener l : ls) l.onCrime(player, crime, pos);
			});

	public static void fire(ServerPlayer player, Crime crime, BlockPos pos) {
		CRIME.invoker().onCrime(player, crime, pos);
	}
}
