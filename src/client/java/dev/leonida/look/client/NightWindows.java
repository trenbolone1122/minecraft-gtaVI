package dev.leonida.look.client;

import dev.leonida.look.block.Glow;
import dev.leonida.look.block.WindowBlock;
import dev.leonida.look.block.WindowKind;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Night windows. Every window model carries two glow overlay layers (tintindex 1 = open room, 2 = curtains/blinds).
 * A model wrapper keeps at most one of them, decided per window group from a position hash and the night stage, makes
 * it emissive and tints it warm (homes), cool (offices), blue (TV) or warm-bright (shops). When the night stage
 * changes (dusk, night, dawn: 8 steps a day) the sections around the camera re-mesh, so lights come on gradually.
 *
 * <p>Window groups: two columns wide (x/2, z/2) and one vertical run of the same window block tall (scan down to the
 * run's bottom), so a 3 block tall floor of curtain wall lights as one room. Offices light whole floors.
 */
public final class NightWindows {
	private NightWindows() {}

	private static final int TINT_OPEN = 1, TINT_BLINDS = 2;
	/** Current night stage 0 (day) .. 4 (full night), read by chunk meshing threads. */
	private static volatile int stage = 0;
	/** Test hook: force a stage (-1 = follow the clock). */
	public static volatile int forcedStage = -1;

	public static void init() {
		ModelLoadingPlugin.register(ctx -> ctx.modifyBlockModelAfterBake().register((model, c) ->
				c.state().getBlock() instanceof WindowBlock ? new Wrapped(model) : model));
		ClientTickEvents.END_CLIENT_TICK.register(NightWindows::tick);
	}

	public static int stage() {
		return stage;
	}

	/** Night stage from the time of day (ticks, 0 = 06:00, 12000 = 18:00 sunset). */
	public static int stageAt(long dayTime) {
		long t = Math.floorMod(dayTime, 24000L);
		if (t < 11600) return 0;
		if (t < 12200) return 1;
		if (t < 12800) return 2;
		if (t < 13400) return 3;
		if (t < 22300) return 4;
		if (t < 22900) return 3;
		if (t < 23400) return 2;
		if (t < 23800) return 1;
		return 0;
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) return;
		int s = forcedStage >= 0 ? forcedStage : level.dimensionType().hasFixedTime() ? 0 : stageAt(level.getDefaultClockTime());
		if (s != stage) {
			stage = s;
			remeshAround(mc, level);
		}
	}

	private static void remeshAround(Minecraft mc, ClientLevel level) {
		if (mc.player == null || mc.levelExtractor == null) return;
		BlockPos cam = mc.player.blockPosition();
		int r = mc.options.getEffectiveRenderDistance() + 1;
		int cx = SectionPos.blockToSectionCoord(cam.getX()), cz = SectionPos.blockToSectionCoord(cam.getZ());
		int y0 = level.getMinSectionY(), y1 = level.getMaxSectionY();
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				for (int y = y0; y <= y1; y++) mc.levelExtractor.setSectionDirty(x, y, z);
			}
		}
	}

	static int hash(int x, int y, int z) {
		int h = x * 0x27d4eb2d ^ y * 0x165667b1 ^ z * 0x9e3779b1;
		h ^= h >>> 15;
		h *= 0x85ebca6b;
		h ^= h >>> 13;
		h *= 0xc2b2ae35;
		return h ^ (h >>> 16);
	}

	static float unit(int h) {
		return (h >>> 8) / (float) (1 << 24);
	}

	/** Packed ABGR-free 0xAARRGGBB colour the glow is multiplied by, or 0 if the window stays dark. */
	static int glowColor(WindowKind kind, Glow glow, int stage, int gx, int gy, int gz) {
		if (glow == Glow.OFF || (glow == Glow.AUTO && stage == 0)) return 0;
		int h = hash(gx, gy, gz);
		float r1 = unit(h), r2 = unit(hash(h, 7, 13)), r3 = unit(hash(h, 31, 5));
		if (glow == Glow.AUTO) {
			float lit;
			if (kind == WindowKind.OFFICE) {
				// whole floors on or off (per building area), then most bays on that floor
				float floor = unit(hash(gx >> 4, gy, gz >> 4));
				lit = floor < kind.litFraction * 1.25f && r1 < 0.8f ? 1 : 0;
			} else {
				lit = r1 < kind.litFraction ? 1 : 0;
			}
			if (lit == 0) return 0;
			// staggered switch-on: each window needs a minimum stage 1..4
			int need = 1 + (int) (r3 * 4f);
			if (kind == WindowKind.SHOP) need = 1;
			if (stage < need) return 0;
		}
		float r, g, b;
		switch (kind) {
			case OFFICE -> {
				float t = r2;
				r = lerp(0.78f, 0.95f, t);
				g = lerp(0.88f, 0.97f, t);
				b = 1.0f;
			}
			case SHOP -> {
				// cool white shop light, a few warm
				if (r2 < 0.2f) { r = 1.0f; g = 0.86f; b = 0.70f; }
				else { r = 0.90f; g = 0.95f; b = 1.0f; }
			}
			case INDUSTRIAL -> {
				r = 0.92f;
				g = 0.95f;
				b = 1.0f;
			}
			default -> {
				// homes: mostly cool white and pale blue, TV blue, only a few warm rooms (neon must lead at night)
				if (r2 > 0.82f) {
					r = 0.45f;
					g = 0.6f;
					b = 1.0f;
				} else if (r2 > 0.18f) {
					float t = (r2 - 0.18f) / 0.64f;
					r = lerp(0.78f, 0.92f, t);
					g = lerp(0.88f, 0.95f, t);
					b = 1.0f;
				} else {
					r = 1.0f;
					g = 0.84f;
					b = 0.66f;
				}
			}
		}
		// brightness variation per room, dimmer than before so lit windows sit under the neon (sRGB space)
		float level = 0.50f + 0.20f * unit(hash(h, 3, 3));
		return 0xFF000000 | srgb(r * level) << 16 | srgb(g * level) << 8 | srgb(b * level);
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static int srgb(float c) {
		c = Math.max(0, Math.min(1, c));
		double s = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
		return (int) Math.round(s * 255);
	}

	/** Bottom y of the vertical run of the same block containing pos (max 8 down). */
	static int runBottom(BlockAndTintGetter world, BlockPos pos, BlockState state) {
		BlockPos.MutableBlockPos m = pos.mutable();
		int y = pos.getY();
		for (int i = 0; i < 8; i++) {
			m.setY(y - 1);
			if (world.getBlockState(m).getBlock() != state.getBlock()) break;
			y--;
		}
		return y;
	}

	private static final class Wrapped extends WrapperBlockStateModel {
		Wrapped(BlockStateModel model) {
			super(model);
		}

		@Override
		public void emitQuads(QuadEmitter emitter, BlockAndTintGetter world, BlockPos pos, BlockState state, RandomSource random,
				Predicate<Direction> cullTest) {
			WindowKind kind = state.getBlock() instanceof WindowBlock w ? w.kind() : WindowKind.HOME;
			Glow glow = state.hasProperty(WindowBlock.GLOW) ? state.getValue(WindowBlock.GLOW) : Glow.AUTO;
			int gx = Math.floorDiv(pos.getX(), 2), gz = Math.floorDiv(pos.getZ(), 2);
			int gy = world == null ? pos.getY() : runBottom(world, pos, state);
			int color = glowColor(kind, glow, stage, gx, gy, gz);
			float blindOdds = kind == WindowKind.HOME ? 0.35f : kind == WindowKind.OFFICE ? 0.12f : 0.06f;
			int keep = unit(hash(gx, gy, gz) * 31 + 17) < blindOdds ? TINT_BLINDS : TINT_OPEN;
			// random pane brightness per window (LOOK.md), on the glass itself
			int shade = 0xD8 + (int) (unit(hash(pos.getX(), pos.getY() >> 2, pos.getZ())) * 0x27);
			int paneMul = 0xFF000000 | shade << 16 | shade << 8 | shade;
			emitter.pushTransform(q -> {
				int ti = q.tintIndex();
				if (ti == TINT_OPEN || ti == TINT_BLINDS) {
					if (color == 0 || ti != keep) return false;
					q.tintIndex(-1);
					q.emissive(true);
					q.multiplyColor(color);
					return true;
				}
				q.multiplyColor(paneMul);
				return true;
			});
			try {
				super.emitQuads(emitter, world, pos, state, random, cullTest);
			} finally {
				emitter.popTransform();
			}
		}

		@Override
		public Object createGeometryKey(BlockAndTintGetter world, BlockPos pos, BlockState state, RandomSource random) {
			return null; // depends on position and time
		}
	}
}
