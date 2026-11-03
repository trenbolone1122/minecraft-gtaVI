package dev.leonida.fx.physics;

import dev.leonida.fx.FxApi;
import dev.leonida.fx.FxConfig;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/** Turns broken blocks into debris pieces with sensible sizes, textures and velocities. */
public final class DebrisSpawner {
	private final DebrisWorld world;
	private final Random random = new Random();

	public DebrisSpawner(DebrisWorld world) {
		this.world = world;
	}

	/**
	 * @param center    where the burst comes from
	 * @param power     explosion power scale (TNT 4)
	 * @param visualOnly blocks still exist: chip small pieces off their surface
	 * @param shatter   glass style break
	 */
	public void burst(Level level, Vector3d center, float power, long[] positions, int[] states, boolean visualOnly, boolean shatter) {
		int budget = FxConfig.get().quality.maxPieces;
		int n = positions.length;
		if (n == 0) return;
		// pieces per block from the budget: big blasts use bigger and fewer chunks per block
		int per = Math.max(1, Math.min(8, (int) (budget * 0.8F / n)));
		float keep = n * 1.0F > budget * 0.8F ? budget * 0.8F / n : 1.0F;
		for (int i = 0; i < n; i++) {
			BlockState state = net.minecraft.world.level.block.Block.stateById(states[i]);
			if (state.isAir()) continue;
			BlockPos pos = BlockPos.of(positions[i]);
			if (shatter || FxApi.isGlass(state)) {
				glass(level, center, power, pos, state, budget);
			} else if (visualOnly) {
				chips(level, center, power, pos, state, budget, 2 + random.nextInt(2));
			} else {
				if (keep < 1.0F && random.nextFloat() > keep) continue;
				chunks(level, center, power, pos, state, per, budget);
			}
		}
	}

	/** Small chips for a block broken by hand. */
	public void mined(Level level, BlockPos pos, BlockState state) {
		int budget = FxConfig.get().quality.maxPieces;
		Vector3d c = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5);
		if (FxApi.isGlass(state)) {
			glass(level, c, 0.6F, pos, state, budget);
			return;
		}
		for (int k = 0; k < 4; k++) {
			float h = 0.07F + random.nextFloat() * 0.07F;
			Vector3d p = new Vector3d(pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.2 + random.nextDouble() * 0.6,
					pos.getZ() + 0.2 + random.nextDouble() * 0.6);
			Piece piece = make(level, pos, state, h * (0.8F + random.nextFloat() * 0.4F), h, h * (0.8F + random.nextFloat() * 0.4F), false,
					140 + random.nextInt(80));
			piece.pos.set(p);
			piece.prevPos.set(p);
			piece.vel.set((p.x - c.x) * 4 + (random.nextDouble() - 0.5), 2.0 + random.nextDouble() * 2.5, (p.z - c.z) * 4 + (random.nextDouble() - 0.5));
			spin(piece, 10);
			world.add(piece, budget);
		}
	}

	private void chunks(Level level, Vector3d center, float power, BlockPos pos, BlockState state, int per, int budget) {
		// sub-cells of a 2x2x2 split, shuffled
		int[] cells = {0, 1, 2, 3, 4, 5, 6, 7};
		for (int i = 7; i > 0; i--) {
			int j = random.nextInt(i + 1);
			int t = cells[i];
			cells[i] = cells[j];
			cells[j] = t;
		}
		int count = Math.min(per, 8);
		for (int k = 0; k < count; k++) {
			int c = cells[k];
			double ox = (c & 1) == 0 ? 0.25 : 0.75, oy = (c & 2) == 0 ? 0.25 : 0.75, oz = (c & 4) == 0 ? 0.25 : 0.75;
			float base = count >= 6 ? 0.25F : count >= 3 ? 0.3F : 0.38F;
			float hx = base * (0.7F + random.nextFloat() * 0.3F);
			float hy = base * (0.6F + random.nextFloat() * 0.4F);
			float hz = base * (0.7F + random.nextFloat() * 0.3F);
			Piece piece = make(level, pos, state, hx, hy, hz, false, 260 + random.nextInt(200));
			Vector3d p = count == 1 ? new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
					: new Vector3d(pos.getX() + ox, pos.getY() + oy, pos.getZ() + oz);
			piece.pos.set(p);
			piece.prevPos.set(p);
			launch(piece, center, power, 1.0);
			world.add(piece, budget);
		}
	}

	private void chips(Level level, Vector3d center, float power, BlockPos pos, BlockState state, int budget, int count) {
		for (int k = 0; k < count; k++) {
			float h = 0.06F + random.nextFloat() * 0.08F;
			Piece piece = make(level, pos, state, h * (0.8F + random.nextFloat() * 0.5F), h, h * (0.8F + random.nextFloat() * 0.5F), false,
					160 + random.nextInt(100));
			// on the face looking at the centre
			Vector3d bc = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			Vector3d d = new Vector3d(center).sub(bc);
			if (d.lengthSquared() < 1.0E-6) d.set(0, 1, 0);
			d.normalize();
			Vector3d p = new Vector3d(bc).fma(0.55, d).add((random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6, (random.nextDouble() - 0.5) * 0.6);
			piece.pos.set(p);
			piece.prevPos.set(p);
			launch(piece, center, power, 0.6);
			world.add(piece, budget);
		}
	}

	private void glass(Level level, Vector3d center, float power, BlockPos pos, BlockState state, int budget) {
		int count = 7 + random.nextInt(5);
		boolean pane = state.getCollisionShape(level, pos).bounds().getXsize() < 0.5 || state.getCollisionShape(level, pos).bounds().getZsize() < 0.5;
		for (int k = 0; k < count; k++) {
			float a = 0.06F + random.nextFloat() * 0.13F;
			float b = 0.06F + random.nextFloat() * 0.13F;
			Piece piece = make(level, pos, state, a, b, 0.012F, true, 160 + random.nextInt(120));
			Vector3d p = new Vector3d(pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() + 0.1 + random.nextDouble() * 0.8,
					pos.getZ() + 0.15 + random.nextDouble() * 0.7);
			if (pane) {
				p.x = pos.getX() + 0.5 + (p.x - pos.getX() - 0.5) * (state.getCollisionShape(level, pos).bounds().getXsize() < 0.5 ? 0.1 : 1);
				p.z = pos.getZ() + 0.5 + (p.z - pos.getZ() - 0.5) * (state.getCollisionShape(level, pos).bounds().getZsize() < 0.5 ? 0.1 : 1);
			}
			piece.pos.set(p);
			piece.prevPos.set(p);
			piece.rot.rotationXYZ(random.nextFloat() * 6.28F, random.nextFloat() * 6.28F, random.nextFloat() * 6.28F);
			piece.prevRot.set(piece.rot);
			launch(piece, center, Math.max(power, 0.8F), 0.8);
			world.add(piece, budget);
		}
	}

	private void launch(Piece piece, Vector3d center, float power, double scale) {
		Vector3d d = new Vector3d(piece.pos).sub(center);
		double dist = d.length();
		if (dist < 1.0E-4) d.set(0, 1, 0);
		else d.mul(1.0 / dist);
		double speed = (2.0 + power * 3.2) / (1.0 + dist * 0.35) * scale * (0.7 + random.nextDouble() * 0.6);
		piece.vel.set(d.x * speed + (random.nextDouble() - 0.5) * 2.0, d.y * speed + (1.5 + power * 0.9) * (0.6 + random.nextDouble() * 0.8) * scale,
				d.z * speed + (random.nextDouble() - 0.5) * 2.0);
		spin(piece, (float) (4 + speed * 1.4));
	}

	private void spin(Piece piece, float max) {
		piece.angVel.set((random.nextFloat() - 0.5F) * 2 * max, (random.nextFloat() - 0.5F) * 2 * max, (random.nextFloat() - 0.5F) * 2 * max);
	}

	private Piece make(Level level, BlockPos pos, BlockState state, float hx, float hy, float hz, boolean glass, int life) {
		Minecraft mc = Minecraft.getInstance();
		TextureAtlasSprite sprite = mc.getModelManager().getBlockStateModelSet().getParticleMaterial(state).sprite();
		int color = 0xFFFFFFFF;
		if (!state.is(Blocks.GRASS_BLOCK)) {
			BlockTintSource tint = mc.getBlockColors().getTintSource(state, 0);
			if (tint != null && level instanceof net.minecraft.client.renderer.block.BlockAndTintGetter btg) color = 0xFF000000 | tint.colorAsTerrainParticle(state, btg, pos);
		}
		float size = Math.min(1.0F, Math.max(hx, Math.max(hy, hz)) * 2.0F);
		float ou = random.nextFloat() * (1.0F - size), ov = random.nextFloat() * (1.0F - size);
		boolean translucent = glass || sprite.transparency().hasTranslucent();
		return new Piece(hx, hy, hz, sprite, sprite.getU(ou), sprite.getV(ov), sprite.getU(ou + size), sprite.getV(ov + size), color, translucent,
				glass ? 0.15F : 0.25F, glass ? 0.35F : 0.6F, life);
	}
}
