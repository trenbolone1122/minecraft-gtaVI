package dev.leonida.gameplay;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.leonida.core.PlayerState;
import dev.leonida.core.api.CrimeEvents;
import dev.leonida.core.api.CrimeEvents.Crime;
import dev.leonida.gameplay.npc.PedestrianSpawner;
import dev.leonida.gameplay.npc.PoliceOfficer;
import dev.leonida.gameplay.npc.StoreClerk;
import dev.leonida.gameplay.police.PoliceDispatch;
import dev.leonida.gameplay.wanted.WantedSystem;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

/** Debug and test commands: /gta wanted|money|crime|spawn|peds|bust|viewers|clearpolice. */
final class GameplayCommands {
	private GameplayCommands() {}

	static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("gta").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("wanted").then(Commands.argument("stars", IntegerArgumentType.integer(0, 6))
						.executes(c -> wanted(c, true))
						.then(Commands.argument("seen", BoolArgumentType.bool()).executes(c -> wanted(c, BoolArgumentType.getBool(c, "seen"))))))
				.then(Commands.literal("money").then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					PlayerState.set(p, PlayerState.get(p).withMoney(IntegerArgumentType.getInteger(c, "amount")));
					return 1;
				})))
				.then(Commands.literal("crime").then(Commands.argument("crime", StringArgumentType.word()).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					Crime crime;
					try {
						crime = Crime.valueOf(StringArgumentType.getString(c, "crime").toUpperCase(Locale.ROOT));
					} catch (IllegalArgumentException e) {
						c.getSource().sendFailure(Component.literal("Unknown crime"));
						return 0;
					}
					CrimeEvents.fire(p, crime, p.blockPosition());
					return 1;
				})))
				.then(Commands.literal("spawn").then(Commands.argument("what", StringArgumentType.word())
						.executes(c -> spawn(c, 1))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 32)).executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "count"))))))
				.then(Commands.literal("peds")
						.then(Commands.literal("off").executes(c -> { PedestrianSpawner.density = 0; return 1; }))
						.then(Commands.argument("density", IntegerArgumentType.integer(0, 64)).executes(c -> {
							PedestrianSpawner.density = IntegerArgumentType.getInteger(c, "density");
							return 1;
						})))
				.then(Commands.literal("bust").executes(c -> {
					PoliceDispatch.bust(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("viewers").then(Commands.argument("n", IntegerArgumentType.integer(0)).executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					PlayerState.set(p, PlayerState.get(p).withLiveViewers(IntegerArgumentType.getInteger(c, "n")));
					return 1;
				})))
				.then(Commands.literal("clearpolice").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					WantedSystem.clear(p, false);
					PoliceDispatch.standDown(p);
					for (PoliceOfficer o : p.level().getEntitiesOfClass(PoliceOfficer.class, p.getBoundingBox().inflate(256))) o.discard();
					return 1;
				})));
	}

	private static int wanted(CommandContext<CommandSourceStack> c, boolean seen) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		WantedSystem.forceWanted(p, IntegerArgumentType.getInteger(c, "stars"), seen);
		return 1;
	}

	private static int spawn(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		ServerLevel level = p.level();
		String what = StringArgumentType.getString(c, "what").toLowerCase(Locale.ROOT);
		Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
		for (int i = 0; i < count; i++) {
			double side = count == 1 ? 0 : ((i % 3) - 1) * 1.5 + (i / 3 % 2) * 0.75;
			Vec3 at = p.position().add(look.scale(4 + (i / 3) * 1.5)).add(look.yRot((float) Math.PI / 2).scale(side));
			switch (what) {
				case "ped" -> PedestrianSpawner.spawn(level, at);
				case "police" -> PoliceDispatch.spawnOfficer(level, at, p, PoliceOfficer.PATROL);
				case "vcpd", "sheriff" -> {
					PoliceOfficer o = PoliceDispatch.spawnOfficer(level, at, p, PoliceOfficer.PATROL);
					if (o != null) o.setAgency(what.equals("vcpd") ? PoliceOfficer.VCPD : PoliceOfficer.SHERIFF);
				}
				case "swat" -> PoliceDispatch.spawnOfficer(level, at, p, PoliceOfficer.SWAT);
				case "heavy" -> PoliceDispatch.spawnOfficer(level, at, p, PoliceOfficer.HEAVY);
				case "clerk" -> {
					StoreClerk clerk = GameplayRegistry.CLERK.create(level, EntitySpawnReason.COMMAND);
					if (clerk != null) {
						float yaw = p.getYRot() + 180f;
						clerk.snapTo(at.x, at.y, at.z, yaw, 0);
						clerk.setYHeadRot(yaw);
						clerk.setHome(clerk.blockPosition());
						level.addFreshEntity(clerk);
					}
				}
				default -> {
					c.getSource().sendFailure(Component.literal("ped, police, vcpd, sheriff, swat, heavy or clerk"));
					return 0;
				}
			}
		}
		return count;
	}
}
