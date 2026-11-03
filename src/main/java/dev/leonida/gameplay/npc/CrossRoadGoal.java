package dev.leonida.gameplay.npc;

import dev.leonida.core.api.MapQuery;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pedestrians cross streets like people do: walk along the sidewalk to the nearest crosswalk
 * (white road markings next to the curb), wait at the curb until no car is coming, then cross
 * straight to the opposite sidewalk. Without a crosswalk nearby they only rarely jaywalk.
 */
final class CrossRoadGoal extends Goal {
	private enum Phase { APPROACH, WAIT, CROSS }

	private static final int MAX_ROAD_WIDTH = 16;
	private static final int SEARCH_ALONG = 10;

	private final Pedestrian ped;
	private Phase phase;
	private Vec3 entry, exit;
	private int ticks, waited;

	CrossRoadGoal(Pedestrian ped) {
		this.ped = ped;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (ped.state != Pedestrian.State.WALK || ped.getRandom().nextInt(160) != 0) return false;
		BlockPos at = ped.blockPosition();
		if (surface(at) != MapQuery.Surface.SIDEWALK) return false;
		for (Direction d : Direction.Plane.HORIZONTAL.shuffledCopy(ped.getRandom())) {
			if (!roadAhead(at, d)) continue;
			if (plan(at, d)) return true;
		}
		return false;
	}

	/** Road within three blocks in this direction from the sidewalk. */
	private boolean roadAhead(BlockPos at, Direction d) {
		for (int i = 1; i <= 3; i++) {
			MapQuery.Surface s = surface(at.relative(d, i));
			if (s == MapQuery.Surface.ROAD) return true;
			if (s != MapQuery.Surface.SIDEWALK) return false;
		}
		return false;
	}

	private boolean plan(BlockPos at, Direction d) {
		Direction along = d.getClockWise();
		BlockPos bestCurb = null;
		int bestDist = Integer.MAX_VALUE;
		for (int off = -SEARCH_ALONG; off <= SEARCH_ALONG; off++) {
			BlockPos side = at.relative(along, off);
			BlockPos curb = curbFrom(side, d);
			if (curb == null || !isCrosswalk(curb.relative(d), d)) continue;
			if (Math.abs(off) < bestDist) {
				bestDist = Math.abs(off);
				bestCurb = curb;
			}
		}
		if (bestCurb == null) {
			if (ped.getRandom().nextInt(6) != 0) return false; // jaywalking is the exception
			bestCurb = curbFrom(at, d);
			if (bestCurb == null) return false;
		}
		BlockPos far = farSidewalk(bestCurb, d);
		if (far == null) return false;
		entry = ground(bestCurb);
		exit = ground(far.relative(d));
		phase = Phase.APPROACH;
		ticks = 0;
		waited = 0;
		return true;
	}

	/** Last sidewalk block before the road, walking from this sidewalk block in direction d. */
	private BlockPos curbFrom(BlockPos side, Direction d) {
		if (surface(side) != MapQuery.Surface.SIDEWALK) return null;
		BlockPos p = side;
		for (int i = 0; i < 4; i++) {
			BlockPos n = p.relative(d);
			MapQuery.Surface s = surface(n);
			if (s == MapQuery.Surface.ROAD) return p;
			if (s != MapQuery.Surface.SIDEWALK) return null;
			p = n;
		}
		return null;
	}

	/** First sidewalk block across a plain road (never across highways or water). */
	private BlockPos farSidewalk(BlockPos curb, Direction d) {
		for (int i = 1; i <= MAX_ROAD_WIDTH; i++) {
			BlockPos p = curb.relative(d, i);
			MapQuery.Surface s = surface(p);
			if (s == MapQuery.Surface.SIDEWALK) return i > 1 ? p : null;
			if (s != MapQuery.Surface.ROAD) return null;
		}
		return null;
	}

	/** Crosswalks are painted: light-coloured top blocks in the first road columns past the curb. */
	private boolean isCrosswalk(BlockPos road, Direction d) {
		return painted(road) || painted(road.relative(d));
	}

	private boolean painted(BlockPos road) {
		Level level = ped.level();
		BlockPos top = topBlock(road);
		MapColor c = level.getBlockState(top).getMapColor(level, top);
		return c == MapColor.SNOW || c == MapColor.QUARTZ || c == MapColor.WOOL || c == MapColor.TERRACOTTA_WHITE;
	}

	@Override
	public boolean canContinueToUse() {
		return phase != null && ped.state == Pedestrian.State.WALK && ticks < 900;
	}

	@Override
	public void start() {
		ped.getNavigation().moveTo(entry.x, entry.y, entry.z, 0.9);
	}

	@Override
	public void stop() {
		phase = null;
		ped.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		ticks++;
		if (phase == null) return;
		switch (phase) {
			case APPROACH -> {
				if (ped.position().distanceToSqr(entry) < 1.2) {
					phase = Phase.WAIT;
					ped.getNavigation().stop();
				} else if (ped.getNavigation().isDone()) {
					if (ticks > 200) phase = null;
					else ped.getNavigation().moveTo(entry.x, entry.y, entry.z, 0.9);
				}
			}
			case WAIT -> {
				waited++;
				// Look left and right along the street.
				Vec3 dir = exit.subtract(entry).normalize();
				Vec3 side = new Vec3(-dir.z, 0, dir.x).scale((waited / 20) % 2 == 0 ? 8 : -8);
				ped.getLookControl().setLookAt(entry.x + side.x, ped.getEyeY(), entry.z + side.z);
				if (waited >= 20 && !carComing()) {
					phase = Phase.CROSS;
					ped.getNavigation().moveTo(exit.x, exit.y, exit.z, 1.05);
				} else if (waited > 400) {
					phase = null;
				}
			}
			case CROSS -> {
				if (ped.position().distanceToSqr(exit) < 1.5) phase = null;
				else if (ped.getNavigation().isDone()) ped.getNavigation().moveTo(exit.x, exit.y, exit.z, 1.05);
			}
		}
	}

	/** Any moving vehicle-sized entity near the crossing line. */
	private boolean carComing() {
		AABB box = new AABB(entry, exit).inflate(14, 3, 14);
		List<Entity> near = ped.level().getEntities(ped, box, e -> !(e instanceof LivingEntity) && e.getBbWidth() >= 1.2
				&& (e.getDeltaMovement().horizontalDistanceSqr() > 0.01 || e.isVehicle()));
		return !near.isEmpty();
	}

	private MapQuery.Surface surface(BlockPos p) {
		return MapQuery.get().surfaceAt(p.getX(), p.getZ());
	}

	private BlockPos topBlock(BlockPos p) {
		int y = ped.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) - 1;
		return new BlockPos(p.getX(), y, p.getZ());
	}

	private Vec3 ground(BlockPos p) {
		return Vec3.atBottomCenterOf(topBlock(p).above());
	}
}
