package dev.leonida.vehicle;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.leonida.LeonidaMod;
import dev.leonida.core.api.PoliceUnits;
import dev.leonida.vehicle.ai.TrafficManager;
import dev.leonida.vehicle.net.VehiclePayloads;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Server and common setup for the vehicle module. Owned by the vehicle agent. */
public final class VehicleModule {
	private VehicleModule() {}

	public static final ResourceKey<DamageType> VEHICLE_HIT = ResourceKey.create(Registries.DAMAGE_TYPE, LeonidaMod.id("vehicle/hit"));
	private static final Identifier CAMERA_MOD = LeonidaMod.id("vehicle_camera");

	public static void init() {
		VehicleTypes.register();
		VehicleSounds.register();

		PayloadTypeRegistry.serverboundPlay().register(VehiclePayloads.Controls.TYPE, VehiclePayloads.Controls.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(VehiclePayloads.Action.TYPE, VehiclePayloads.Action.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(VehiclePayloads.Controls.TYPE, (payload, ctx) -> {
			if (ctx.player().getVehicle() instanceof VehicleEntity v && v.getControllingPassenger() == ctx.player()) v.setClientControls(payload.bits());
		});
		ServerPlayNetworking.registerGlobalReceiver(VehiclePayloads.Action.TYPE, (payload, ctx) -> handleAction(ctx.player(), payload));

		ServerTickEvents.END_LEVEL_TICK.register(TrafficManager::tick);
		ServerTickEvents.END_LEVEL_TICK.register(dev.leonida.vehicle.ai.SpotVehicles::tick);
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof VehicleEntity v && v.isTraffic()) TrafficManager.track(v);
		});
		CommandRegistrationCallback.EVENT.register((d, ctx, env) -> registerCommands(d));
		PoliceUnits.installCars(VehicleApi.POLICE_CARS);
	}

	private static void handleAction(ServerPlayer p, VehiclePayloads.Action a) {
		if (a.action() == VehiclePayloads.Action.EXIT_OR_ENTER) {
			exitOrEnter(p);
			return;
		}
		if (!(p.getVehicle() instanceof VehicleEntity v) || v.getControllingPassenger() != p) return;
		switch (a.action()) {
			case VehiclePayloads.Action.HORN -> {
				if (p.level().getGameTime() - lastHorn(p) > 6) { v.honk(); hornTimes.put(p.getUUID(), p.level().getGameTime()); }
			}
			case VehiclePayloads.Action.LIGHTS -> v.setHeadlights(!v.headlightsOn());
			case VehiclePayloads.Action.SIREN -> v.setSiren(!v.isSirenOn());
			case VehiclePayloads.Action.CRASH -> {
				float impact = Math.min(a.value(), (float) v.spec.maxSpeed() * 1.5f);
				if (impact > 6) v.onCrash((ServerLevel) p.level(), impact);
			}
			default -> {}
		}
	}

	/** F key: leave the vehicle you are in (any seat), or on foot get into the nearest vehicle you are looking at. */
	public static void exitOrEnter(ServerPlayer p) {
		if (p.getVehicle() instanceof VehicleEntity v) {
			v.exitRider(p);
			return;
		}
		if (p.isPassenger() || p.isSpectator()) return;
		VehicleEntity best = null;
		double bestD = Double.MAX_VALUE;
		Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
		for (VehicleEntity v : p.level().getEntitiesOfClass(VehicleEntity.class, p.getBoundingBox().inflate(5))) {
			Vec3 to = v.position().add(0, 0.6, 0).subtract(eye);
			double d = to.length() - v.spec.length() / 2;
			if (d > 3.5) continue;
			double score = d - 1.5 * look.dot(to.normalize());
			if (score < bestD) { bestD = score; best = v; }
		}
		if (best != null) best.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND, best.position());
	}

	private static final java.util.Map<java.util.UUID, Long> hornTimes = new java.util.HashMap<>();

	private static long lastHorn(ServerPlayer p) { return hornTimes.getOrDefault(p.getUUID(), 0L); }

	static void onPlayerEnter(ServerPlayer p, VehicleEntity v) {
		if (v.getControllingPassenger() == p) v.onDriverEnter(p);
		AttributeInstance cam = p.getAttribute(Attributes.CAMERA_DISTANCE);
		if (cam != null && !cam.hasModifier(CAMERA_MOD)) {
			double extra = v.spec.isBike() ? 1.0 : v.spec.length() * 0.8;
			cam.addTransientModifier(new AttributeModifier(CAMERA_MOD, extra, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	static void onPlayerExit(ServerPlayer p, VehicleEntity v) {
		AttributeInstance cam = p.getAttribute(Attributes.CAMERA_DISTANCE);
		if (cam != null) cam.removeModifier(CAMERA_MOD);
	}

	public static DamageSource vehicleDamage(ServerLevel level, Entity vehicle, Entity driver) {
		var holder = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(VEHICLE_HIT);
		return new DamageSource(holder, vehicle, driver);
	}

	private static void registerCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> d) {
		d.register(Commands.literal("vehicle")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("spawn")
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(VehicleTypes.SPECS.keySet(), b))
								.executes(c -> spawnCmd(c.getSource(), StringArgumentType.getString(c, "type"), false, -1))
								.then(Commands.argument("locked", BoolArgumentType.bool())
										.executes(c -> spawnCmd(c.getSource(), StringArgumentType.getString(c, "type"), BoolArgumentType.getBool(c, "locked"), -1))
										.then(Commands.argument("paint", IntegerArgumentType.integer(0, 15))
												.executes(c -> spawnCmd(c.getSource(), StringArgumentType.getString(c, "type"),
														BoolArgumentType.getBool(c, "locked"), IntegerArgumentType.getInteger(c, "paint")))))))
				.then(Commands.literal("traffic")
						.then(Commands.literal("on").executes(c -> { TrafficManager.enabled = true; return 1; }))
						.then(Commands.literal("off").executes(c -> { TrafficManager.enabled = false; return 1; }))
						.then(Commands.literal("spawn").executes(c -> {
							ServerPlayer p = c.getSource().getPlayerOrException();
							var v = TrafficManager.trySpawn(p.level(), p);
							c.getSource().sendSuccess(() -> Component.literal(v == null ? "no road spot found" : "spawned " + v.spec.id()), false);
							return v == null ? 0 : 1;
						}))
						.then(Commands.literal("density").then(Commands.argument("n", IntegerArgumentType.integer(0, 60))
								.executes(c -> { TrafficManager.density = IntegerArgumentType.getInteger(c, "n"); return 1; }))))
				.then(Commands.literal("police").then(Commands.argument("target", EntityArgument.player())
						.executes(c -> {
							ServerPlayer t = EntityArgument.getPlayer(c, "target");
							Vec3 pos = c.getSource().getPosition();
							Entity car = PoliceUnits.cars().spawnCruiser(c.getSource().getLevel(), pos, c.getSource().getRotation().y, t, false);
							return car == null ? 0 : 1;
						})))
				.then(Commands.literal("clear").executes(c -> {
					int n = 0;
					for (Entity e : c.getSource().getLevel().getAllEntities()) {
						if (e instanceof VehicleEntity v && !v.isVehicle()) { v.discard(); n++; }
					}
					int fn = n;
					c.getSource().sendSuccess(() -> Component.literal("removed " + fn + " vehicles"), false);
					return n;
				})));
	}

	private static int spawnCmd(net.minecraft.commands.CommandSourceStack src, String type, boolean locked, int paint) {
		VehicleSpec s = VehicleTypes.get(type);
		if (s == null) {
			src.sendFailure(Component.literal("unknown vehicle " + type));
			return 0;
		}
		Vec3 look = Vec3.directionFromRotation(0, src.getRotation().y);
		Vec3 pos = src.getPosition().add(look.scale(4 + s.length() / 2));
		VehicleEntity v = VehicleApi.spawn(src.getLevel(), type, pos, src.getRotation().y + 90);
		if (v == null) return 0;
		if (paint >= 0) v.setColor(s.paints()[paint % s.paints().length]);
		v.setLocked(locked);
		if (!locked && src.getEntity() instanceof ServerPlayer p) v.setOwner(p.getUUID());
		src.sendSuccess(() -> Component.literal("spawned " + s.displayName()), false);
		return 1;
	}
}
