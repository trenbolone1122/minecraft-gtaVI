package dev.leonida.road.gen;

import dev.leonida.core.api.GenTerrain;
import dev.leonida.look.LookBlocks;
import dev.leonida.look.block.OrientedBlock;
import dev.leonida.road.block.RoadBlocks;
import dev.leonida.road.net.Props;
import dev.leonida.road.net.Props.Pole;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pole blocks of the street furniture from {@link Props} (the arms, heads and wires are drawn by the
 * client at exact angles) plus invisible light blocks under every lamp head so streets are lit at night.
 */
final class RoadProps {
	private RoadProps() {}

	static final BlockState LAMP_LIGHT = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);

	static void place(RoadGen.Writer w, int x0, int z0, GenTerrain terrain) {
		Props props = Props.get();
		// poles in this chunk, plus lamp heads that reach into it from poles nearby
		for (Pole p : props.in(x0 - 4, z0 - 4, x0 + 20, z0 + 20)) {
			int bx = p.bx(), bz = p.bz();
			boolean mine = bx >= x0 && bx < x0 + 16 && bz >= z0 && bz < z0 + 16;
			if (mine) pole(w, p, bx, bz);
			boolean lit = p.kind == Props.Kind.LAMP || p.kind == Props.Kind.MEDIAN_LAMP || p.lamp;
			if (lit) {
				int y = (int) p.topY() - 1;
				w.setIfAir((int) Math.floor(p.armX()), y, (int) Math.floor(p.armZ()), LAMP_LIGHT);
				if (p.kind == Props.Kind.MEDIAN_LAMP) {
					double ax = p.x - Math.cos(p.armAngle) * p.armLen, az = p.z - Math.sin(p.armAngle) * p.armLen;
					w.setIfAir((int) Math.floor(ax), y, (int) Math.floor(az), LAMP_LIGHT);
				}
			}
		}
	}

	private static void pole(RoadGen.Writer w, Pole p, int bx, int bz) {
		int top = p.baseY + p.height;
		if (p.kind == Props.Kind.POWER) {
			BlockState pole = LookBlocks.UTILITY_POLE.defaultBlockState();
			double ax = Math.cos(p.armAngle), az = Math.sin(p.armAngle);
			Direction.Axis cross = Math.abs(ax) >= Math.abs(az) ? Direction.Axis.X : Direction.Axis.Z;
			for (int y = p.baseY; y < top - 1; y++) w.set(bx, y, bz, pole);
			w.set(bx, top - 1, bz, LookBlocks.UTILITY_POLE_CROSSARM.defaultBlockState().setValue(OrientedBlock.AXIS, cross));
			// sink the pole into the ground where the verge falls away
			for (int y = p.baseY - 1, n = 0; n < 8 && w.get(bx, y, bz).isAir(); y--, n++) w.set(bx, y, bz, pole);
			for (int y = p.baseY - 1, n = 0; n < 8 && !w.get(bx, y, bz).isSolid() && !w.get(bx, y, bz).isAir(); y--, n++) w.set(bx, y, bz, pole);
			return;
		}
		BlockState pole = RoadBlocks.POLE.defaultBlockState();
		for (int y = p.baseY; y < top; y++) w.set(bx, y, bz, pole);
		for (int y = p.baseY - 1, n = 0; n < 12 && w.get(bx, y, bz).isAir(); y--, n++) w.set(bx, y, bz, pole);
	}
}
