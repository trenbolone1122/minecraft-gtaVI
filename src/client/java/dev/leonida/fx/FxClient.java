package dev.leonida.fx;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.leonida.fx.look.LeonidaLook;
import dev.leonida.fx.net.DebrisPayload;
import dev.leonida.fx.net.RagdollPayload;
import dev.leonida.fx.physics.DebrisSpawner;
import dev.leonida.fx.physics.DebrisWorld;
import dev.leonida.fx.physics.FxRenderer;
import dev.leonida.fx.physics.RagdollManager;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Client setup for the fx module: debris and ragdoll simulation, rendering, the Leonida look and settings. */
public final class FxClient {
	private static final DebrisWorld DEBRIS = new DebrisWorld();
	private static final DebrisSpawner SPAWNER = new DebrisSpawner(DEBRIS);
	private static final RagdollManager RAGDOLLS = new RagdollManager();

	private FxClient() {}

	public static DebrisWorld debris() {
		return DEBRIS;
	}

	public static RagdollManager ragdolls() {
		return RAGDOLLS;
	}

	public static void init() {
		FxConfig.load();
		ClientPlayNetworking.registerGlobalReceiver(DebrisPayload.TYPE, (p, ctx) -> onDebris(p));
		ClientPlayNetworking.registerGlobalReceiver(RagdollPayload.TYPE, (p, ctx) -> onRagdoll(p));
		ClientTickEvents.END_CLIENT_TICK.register(FxClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
			DEBRIS.clear();
			RAGDOLLS.clear();
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) return;
			Vec3 cam = ctx.levelState().cameraRenderState.pos;
			dev.leonida.fx.look.WaterFoam.submit(ctx.submitNodeCollector(), ctx.poseStack(), cam);
			if (DEBRIS.size() == 0 && RAGDOLLS.bodies().isEmpty()) return;
			float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			FxRenderer.submit(ctx.submitNodeCollector(), ctx.poseStack(), cam, partial, DEBRIS, RAGDOLLS);
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(ClientCommands.literal("leonidafx")
				.then(ClientCommands.literal("quality").then(ClientCommands.argument("level", StringArgumentType.word())
						.suggests((c, b) -> {
							for (FxConfig.Quality q : FxConfig.Quality.values()) b.suggest(q.name().toLowerCase(Locale.ROOT));
							return b.buildFuture();
						})
						.executes(c -> {
							String v = StringArgumentType.getString(c, "level").toUpperCase(Locale.ROOT);
							try {
								FxConfig.get().quality = FxConfig.Quality.valueOf(v);
							} catch (IllegalArgumentException e) {
								c.getSource().sendError(Component.translatable("leonida.fx.command.quality.invalid"));
								return 0;
							}
							FxConfig.get().save();
							c.getSource().sendFeedback(Component.translatable("leonida.fx.command.quality", Component.translatable("leonida.fx.quality." + v.toLowerCase(Locale.ROOT))));
							return 1;
						})))
				.then(ClientCommands.literal("grade").then(ClientCommands.argument("percent", IntegerArgumentType.integer(0, 100)).executes(c -> {
					FxConfig.get().gradeStrength = IntegerArgumentType.getInteger(c, "percent") / 100.0F;
					FxConfig.get().save();
					c.getSource().sendFeedback(Component.translatable("leonida.fx.command.grade", IntegerArgumentType.getInteger(c, "percent")));
					return 1;
				})))
				.then(ClientCommands.literal("mining").then(ClientCommands.argument("on", StringArgumentType.word()).executes(c -> {
					FxConfig.get().miningDebris = !StringArgumentType.getString(c, "on").equalsIgnoreCase("off");
					FxConfig.get().save();
					c.getSource().sendFeedback(Component.translatable(FxConfig.get().miningDebris ? "leonida.fx.command.mining.on" : "leonida.fx.command.mining.off"));
					return 1;
				})))
				.then(ClientCommands.literal("clear").executes(c -> {
					DEBRIS.clear();
					RAGDOLLS.clear();
					c.getSource().sendFeedback(Component.translatable("leonida.fx.command.clear"));
					return 1;
				}))));
	}

	private static void tick(Minecraft mc) {
		LeonidaLook.tick(mc);
		dev.leonida.fx.look.WaterFoam.tick(mc);
		if (mc.level == null) {
			if (DEBRIS.size() > 0 || !RAGDOLLS.bodies().isEmpty()) {
				DEBRIS.clear();
				RAGDOLLS.clear();
			}
			return;
		}
		if (mc.isPaused()) return;
		DEBRIS.tick(mc.level);
		RAGDOLLS.tick(mc.level);
	}

	private static void onDebris(DebrisPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		SPAWNER.burst(mc.level, new Vector3d(p.x(), p.y(), p.z()), p.power(), p.positions(), p.states(),
				(p.flags() & DebrisPayload.FLAG_VISUAL_ONLY) != 0, (p.flags() & DebrisPayload.FLAG_SHATTER) != 0);
	}

	private static void onRagdoll(RagdollPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		Entity e = mc.level.getEntity(p.entityId());
		if (e instanceof LivingEntity le) ragdoll(le, new Vec3(p.ix(), p.iy(), p.iz()), p.ticks());
	}

	/** Client-side entry point: ragdoll an entity now (used by the network handler and tests). */
	public static boolean ragdoll(LivingEntity entity, Vec3 impulse, int ticks) {
		return RAGDOLLS.spawn(entity, impulse, ticks);
	}

	/** Client-side entry point: spawn debris for the given block states (tests, client-only effects). */
	public static void debris(ClientLevel level, Vec3 center, float power, long[] positions, int[] states, boolean visualOnly) {
		SPAWNER.burst(level, new Vector3d(center.x, center.y, center.z), power, positions, states, visualOnly, false);
	}

	/** From {@code ClientLevel.addDestroyBlockEffect}: small chips or glass shards for a block broken by hand. */
	public static void onBlockDestroyed(ClientLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty() && state.canBeReplaced()) return;
		if (!FxConfig.get().miningDebris && !FxApi.isGlass(state)) return;
		if (FxConfig.get().quality == FxConfig.Quality.OFF && !FxApi.isGlass(state)) return;
		SPAWNER.mined(level, pos, state);
	}
}
