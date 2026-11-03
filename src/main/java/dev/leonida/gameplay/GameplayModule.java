package dev.leonida.gameplay;

import dev.leonida.core.PlayerState;
import dev.leonida.core.api.BuildingGenerator;
import dev.leonida.core.api.CrimeEvents;
import dev.leonida.core.api.CrimeEvents.Crime;
import dev.leonida.gameplay.money.Cash;
import dev.leonida.gameplay.money.Robbery;
import dev.leonida.gameplay.npc.Pedestrian;
import dev.leonida.gameplay.npc.PedestrianSpawner;
import dev.leonida.gameplay.npc.PoliceOfficer;
import dev.leonida.gameplay.police.PoliceDispatch;
import dev.leonida.gameplay.util.Compat;
import dev.leonida.gameplay.util.HudBridge;
import dev.leonida.gameplay.wanted.WantedSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Server and common setup for the gameplay module. Owned by the gameplay agent. */
public final class GameplayModule {
	private GameplayModule() {}

	private static final Map<UUID, BlockPos> DEATH_POS = new HashMap<>();

	public static void init() {
		GameplayRegistry.init();
		WantedSystem.init();
		PoliceDispatch.init();
		PedestrianSpawner.init();
		dev.leonida.vehicle.VehicleApi.pedestrianFactory = PedestrianSpawner::carjackVictim;
		CommandRegistrationCallback.EVENT.register((d, ctx, sel) -> GameplayCommands.register(d));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long t = server.getTickCount();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if ((t + p.getId()) % 4 == 0) {
					Cash.collect(p);
					Robbery.tick(p);
				}
			}
		});

		// Kills: the weapon module reports KILL_* itself when present; otherwise we do.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer dead) {
				DEATH_POS.put(dead.getUUID(), dead.blockPosition());
				PoliceDispatch.standDown(dead);
				return;
			}
			if (source.getEntity() instanceof ServerPlayer killer && !Compat.weaponModuleFiresKills()) {
				if (entity instanceof PoliceOfficer) CrimeEvents.fire(killer, Crime.KILL_COP, entity.blockPosition());
				else if (entity instanceof Pedestrian || entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager)
					CrimeEvents.fire(killer, Crime.KILL_CIVILIAN, entity.blockPosition());
			}
		});

		// Wasted: wake up at the hospital with a bill and a clean slate.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, p, alive) -> {
			if (alive) return;
			PlayerState s = PlayerState.get(p);
			long bill = Math.min(s.money(), 500);
			PlayerState.set(p, s.withMoney(s.money() - bill).withWanted(0).withKnownFlags(0).withLiveViewers(0));
			WantedSystem.clear(p, false);
			BlockPos died = DEATH_POS.remove(p.getUUID());
			if (died != null) {
				BlockPos hospital = Compat.nearestBuilding(BuildingGenerator.Kind.HOSPITAL, died.getX(), died.getZ());
				PoliceDispatch.respawnAt(p, hospital);
			}
			if (bill > 0) HudBridge.toast(p, "Hospital bill: $" + String.format("%,d", bill) + ".");
		});
	}
}
