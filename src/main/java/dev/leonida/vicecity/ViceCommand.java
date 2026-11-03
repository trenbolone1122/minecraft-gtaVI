package dev.leonida.vicecity;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.leonida.core.api.GenTerrain;
import dev.leonida.vicecity.gen.Site;
import dev.leonida.vicecity.gen.Vox;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Debug command: /leonida_vc list | place &lt;site|all&gt; | view &lt;site&gt; &lt;view&gt;. place builds a site at its real
 * map position in the current level with a flat terrain at the level's surface (test worlds before the world module
 * merges), through the same Vox path as chunk generation.
 */
public final class ViceCommand {
	private ViceCommand() {}

	/** Flat terrain at a fixed ground y, no water. */
	public record FlatTerrain(int ground) implements GenTerrain {
		public int groundY(int x, int z) { return ground; }
		public int waterY(int x, int z) { return Integer.MIN_VALUE; }
		public int seaLevel() { return ground; }
	}

	private static FlatTerrain flat;

	public static FlatTerrain flat(ServerLevel level) {
		if (flat == null) {
			level.getChunk(0, 0); // make sure the heightmap is real
			flat = new FlatTerrain(level.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0) - 1);
		}
		return flat;
	}

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("leonida_vc").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("list").executes(c -> {
				for (Site s : ViceLandmarks.INSTANCE.sites().values()) c.getSource().sendSuccess(() -> Component.literal(s.id + " " + s.name), false);
				return 1;
			}))
			.then(Commands.literal("place").then(Commands.argument("site", StringArgumentType.greedyString()).executes(c -> {
				String id = StringArgumentType.getString(c, "site");
				ServerLevel level = c.getSource().getLevel();
				int n = 0;
				for (Site s : ViceLandmarks.INSTANCE.sites().values()) {
					if (!id.equals("all") && ViceLandmarks.INSTANCE.site(id) != s) continue;
					long t0 = System.currentTimeMillis();
					Vox v;
					try {
						v = s.vox(flat(level));
						v.applyLevel(level);
					} catch (RuntimeException ex) {
						System.out.println("[leonida-vc] FAILED " + s.id + ": " + ex);
						ex.printStackTrace(System.out);
						continue;
					}
					long dt = System.currentTimeMillis() - t0;
					System.out.println("[leonida-vc] placed " + s.id + " " + s.name + " blocks " + v.count() + " in " + dt + " ms");
					n++;
				}
				int fn = n;
				c.getSource().sendSuccess(() -> Component.literal("placed " + fn), false);
				return n;
			})))
			.then(Commands.literal("view").then(Commands.argument("site", StringArgumentType.word()).then(Commands.argument("view", StringArgumentType.word()).executes(c -> {
				Site s = ViceLandmarks.INSTANCE.site(StringArgumentType.getString(c, "site"));
				String vn = StringArgumentType.getString(c, "view");
				if (s == null) return 0;
				ServerLevel level = c.getSource().getLevel();
				int y0 = s.datum(flat(level));
				for (Site.View v : s.views()) {
					if (!v.name().equals(vn)) continue;
					double[] e = s.toWorld(v.u(), v.v()), t = s.toWorld(v.tu(), v.tv());
					String cmd = String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", e[0], y0 + v.h(), e[1], t[0], y0 + v.th(), t[1]);
					c.getSource().getServer().getCommands().performPrefixedCommand(c.getSource(), cmd);
					return 1;
				}
				return 0;
			})))));
	}
}
