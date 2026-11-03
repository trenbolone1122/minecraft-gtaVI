package dev.leonida.hud;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * /leonida pregen &lt;radius&gt; and /leonida pregen stop: generates chunks in a square spiral around the player in the
 * background. Chunks are requested asynchronously (getChunkFuture, worldgen runs on the worker threads) with a small
 * in-flight window, so the server tick never waits on generation. Progress shows in the action bar.
 */
public final class Pregen {
	private Pregen() {}

	private static final Logger LOG = LoggerFactory.getLogger("leonida/pregen");
	/** Chunks requested at once; worldgen workers stay busy without flooding the chunk map. */
	private static final int IN_FLIGHT = 24;
	/** New requests per tick. */
	private static final int PER_TICK = 8;

	private static Job job;

	private static final class Job {
		final ServerLevel level;
		final ServerPlayer owner;
		final CommandSourceStack source;
		final ArrayDeque<long[]> todo = new ArrayDeque<>();
		final List<CompletableFuture<?>> inFlight = new ArrayList<>();
		final int total;
		final long startNs = System.nanoTime();
		int done, skipped;

		Job(ServerLevel level, ServerPlayer owner, CommandSourceStack source, int cx, int cz, int r) {
			this.level = level;
			this.owner = owner;
			this.source = source;
			// square spiral outward so the closest chunks come first
			todo.add(new long[] { cx, cz });
			for (int ring = 1; ring <= r; ring++) {
				for (int x = cx - ring; x <= cx + ring; x++) { todo.add(new long[] { x, cz - ring }); todo.add(new long[] { x, cz + ring }); }
				for (int z = cz - ring + 1; z <= cz + ring - 1; z++) { todo.add(new long[] { cx - ring, z }); todo.add(new long[] { cx + ring, z }); }
			}
			total = todo.size();
		}
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((d, ctx, env) -> commands(d));
		ServerTickEvents.END_SERVER_TICK.register(Pregen::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> job = null);
	}

	private static void commands(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("leonida")
				.then(Commands.literal("pregen")
						.requires(s -> !s.getServer().isDedicatedServer() || Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(s))
						.then(Commands.literal("stop").executes(c -> {
							if (job == null) { c.getSource().sendFailure(Component.translatable("leonida.pregen.none")); return 0; }
							finish("leonida.pregen.stopped");
							return 1;
						}))
						.then(Commands.argument("radius", IntegerArgumentType.integer(1, 128)).executes(c -> {
							CommandSourceStack src = c.getSource();
							int r = IntegerArgumentType.getInteger(c, "radius");
							ServerPlayer p = src.getPlayer();
							ServerLevel level = src.getLevel();
							int cx = (int) Math.floor(src.getPosition().x) >> 4, cz = (int) Math.floor(src.getPosition().z) >> 4;
							start(level, p, src, cx, cz, r);
							return 1;
						}))));
	}

	/** Starts (or restarts) a pre-generation job; also usable from code. */
	public static void start(ServerLevel level, ServerPlayer owner, CommandSourceStack src, int cx, int cz, int r) {
		if (job != null) finish("leonida.pregen.stopped");
		job = new Job(level, owner, src, cx, cz, r);
		LOG.info("pregen start: {} chunks, radius {} around chunk {}, {}", job.total, r, cx, cz);
		src.sendSuccess(() -> Component.translatable("leonida.pregen.start", job.total, r), true);
	}

	public static boolean running() { return job != null; }

	private static void tick(MinecraftServer server) {
		Job j = job;
		if (j == null) return;
		for (Iterator<CompletableFuture<?>> it = j.inFlight.iterator(); it.hasNext();) {
			if (it.next().isDone()) { it.remove(); j.done++; }
		}
		int asked = 0;
		while (asked < PER_TICK && j.inFlight.size() < IN_FLIGHT && !j.todo.isEmpty()) {
			long[] c = j.todo.poll();
			int x = (int) c[0], z = (int) c[1];
			if (j.level.getChunkSource().getChunkNow(x, z) != null) { j.done++; j.skipped++; continue; }
			j.inFlight.add(j.level.getChunkSource().getChunkFuture(x, z, ChunkStatus.FULL, true));
			asked++;
		}
		if (server.getTickCount() % 10 == 0) {
			double sec = (System.nanoTime() - j.startNs) / 1e9;
			Component msg = Component.translatable("leonida.pregen.progress", j.done, j.total, (int) (100L * j.done / j.total), String.format("%.1f", j.done / Math.max(0.05, sec)));
			if (j.owner != null && !j.owner.hasDisconnected()) j.owner.connection.send(new ClientboundSetActionBarTextPacket(msg));
		}
		if (j.todo.isEmpty() && j.inFlight.isEmpty()) finish("leonida.pregen.done");
	}

	private static void finish(String key) {
		Job j = job;
		job = null;
		if (j == null) return;
		double sec = (System.nanoTime() - j.startNs) / 1e9;
		String rate = String.format("%.1f", j.done / Math.max(0.05, sec));
		LOG.info("pregen {}: {} of {} chunks ({} already loaded) in {} s, {} chunks per second", key.substring(key.lastIndexOf('.') + 1), j.done, j.total, j.skipped, String.format("%.1f", sec), rate);
		Component msg = Component.translatable(key, j.done, j.total, String.format("%.1f", sec), rate);
		j.source.sendSuccess(() -> msg, true);
		if (j.owner != null && !j.owner.hasDisconnected()) j.owner.connection.send(new ClientboundSetActionBarTextPacket(msg));
	}
}
