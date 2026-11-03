package dev.leonida.aircraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.leonida.aircraft.api.PoliceHelicopters;
import dev.leonida.aircraft.entity.AircraftEntity;
import dev.leonida.aircraft.entity.Parachutes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Operator commands, mostly for tests and showcases:
 * /leonida_aircraft spawn &lt;type&gt;, fly &lt;type&gt; [ride], police, police_dismiss, parachute.
 */
final class AircraftCommands {
	private AircraftCommands() {}

	static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("leonida_aircraft").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("spawn").then(Commands.argument("type", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(AircraftModule.SPECS.keySet(), b))
						.executes(c -> spawn(c, -1))
						.then(Commands.argument("variant", IntegerArgumentType.integer(0, 9))
								.executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "variant"))))))
				.then(Commands.literal("fly").then(Commands.argument("type", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(AircraftModule.SPECS.keySet(), b))
						.executes(c -> fly(c, false))
						.then(Commands.argument("ride", BoolArgumentType.bool()).executes(c -> fly(c, BoolArgumentType.getBool(c, "ride"))))))
				.then(Commands.literal("police").executes(AircraftCommands::police))
				.then(Commands.literal("police_dismiss").executes(c -> {
					int n = 0;
					for (AircraftEntity a : PoliceHelicopters.active(c.getSource().getLevel())) {
						PoliceHelicopters.INSTANCE.setTarget(a, null);
						n++;
					}
					int count = n;
					c.getSource().sendSuccess(() -> Component.literal("Dismissed " + count + " police helicopters"), false);
					return n;
				}))
				.then(Commands.literal("seat").then(Commands.argument("seat", IntegerArgumentType.integer(0, 9)).executes(c -> {
					// swap the executing passenger into another occupied seat (debugging and tests)
					Entity e = c.getSource().getEntityOrException();
					int seat = IntegerArgumentType.getInteger(c, "seat");
					boolean ok = e.getVehicle() instanceof AircraftEntity a && a.moveToSeat(e, seat);
					if (!ok) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Seat " + seat + " is not taken or out of range")).create();
					return 1;
				})))
				.then(Commands.literal("spawnpoint").then(Commands.argument("id", StringArgumentType.word())
						.suggests((c, b) -> SharedSuggestionProvider.suggest(AircraftSpawns.POINTS.stream().map(AircraftSpawns.Point::id), b))
						.executes(c -> {
							// place this point's parked aircraft around the command position (tests and debugging)
							AircraftSpawns.Point p = AircraftSpawns.byId(StringArgumentType.getString(c, "id"));
							if (p == null) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Unknown spawn point")).create();
							Vec3 pos = c.getSource().getPosition();
							int n = AircraftSpawns.fill(c.getSource().getLevel(), p, (int) Math.floor(pos.x), (int) Math.floor(pos.z));
							c.getSource().sendSuccess(() -> Component.literal("Spawn point " + p.id() + " (" + p.landmark() + "): placed " + n), false);
							return n;
						})))
				.then(Commands.literal("parachute").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					if (!Parachutes.hasParachute(p) && !p.getAbilities().instabuild) p.getInventory().add(new ItemStack(AircraftModule.PARACHUTE_ITEM));
					return Parachutes.deploy(p) ? 1 : 0;
				})));
	}

	private static AircraftSpec spec(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		String id = StringArgumentType.getString(c, "type");
		AircraftSpec s = AircraftModule.SPECS.get(id);
		if (s == null) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Unknown aircraft " + id)).create();
		return s;
	}

	private static AircraftEntity create(CommandSourceStack src, AircraftSpec spec, Vec3 pos, float yaw) {
		ServerLevel level = src.getLevel();
		AircraftEntity a = AircraftModule.type(spec).create(level, EntitySpawnReason.COMMAND);
		a.snapTo(pos.x, pos.y, pos.z, yaw, 0);
		// never start buried in terrain: lift up to 6 blocks until the hull is clear
		for (int i = 0; i < 24 && !level.noCollision(a); i++) a.setPos(a.getX(), a.getY() + 0.25, a.getZ());
		level.addFreshEntity(a);
		return a;
	}

	private static int spawn(CommandContext<CommandSourceStack> c, int variant) throws CommandSyntaxException {
		AircraftSpec spec = spec(c);
		Vec3 pos = c.getSource().getPosition();
		float yaw = c.getSource().getRotation().y;
		AircraftEntity a = create(c.getSource(), spec, pos, yaw);
		if (variant >= 0) a.setVariant(variant);
		return 1;
	}

	private static int fly(CommandContext<CommandSourceStack> c, boolean ride) throws CommandSyntaxException {
		AircraftSpec spec = spec(c);
		Vec3 center = c.getSource().getPosition();
		double radius = spec.isHelicopter() ? 14 : 40;
		AircraftEntity a = create(c.getSource(), spec, center.add(radius, 20, 0), 180);
		a.startEngineNow();
		a.autopilot.yieldsToPlayer = false;
		a.autopilot.faceTarget = false;
		a.autopilot.orbit(null, center, radius, 20, spec.isHelicopter() ? 0.35 : 0.8);
		if (spec.searchlight) a.setSearchlight(true);
		if (ride && c.getSource().getEntity() instanceof ServerPlayer p) {
			p.startRiding(a, true, true);
		}
		return 1;
	}

	private static int police(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		Vec3 dir = Vec3.directionFromRotation(0, p.getYRot());
		Entity heli = PoliceHelicopters.INSTANCE.spawnHelicopter(c.getSource().getLevel(), p.position().add(dir.scale(30)).add(0, 25, 0), p);
		if (heli instanceof AircraftEntity a) a.startEngineNow();
		return heli != null ? 1 : 0;
	}
}
