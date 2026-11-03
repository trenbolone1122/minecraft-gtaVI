package dev.leonida.aircraft.item;

import dev.leonida.aircraft.AircraftModule;
import dev.leonida.aircraft.AircraftSpec;
import dev.leonida.aircraft.entity.AircraftEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Places an aircraft where the player looks (water too, for the seaplane). The placer owns it. */
public class AircraftItem extends Item {
	public final AircraftSpec spec;

	public AircraftItem(AircraftSpec spec, Properties properties) {
		super(properties);
		this.spec = spec;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
		if (hit.getType() != HitResult.Type.BLOCK) return InteractionResult.PASS;
		Vec3 at = hit.getLocation();
		AircraftEntity aircraft = AircraftModule.type(spec).create(level, net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
		if (aircraft == null) return InteractionResult.FAIL;
		aircraft.snapTo(at.x, at.y, at.z, player.getYRot(), 0);
		if (!level.noCollision(aircraft, aircraft.getBoundingBox())) return InteractionResult.FAIL;
		if (level instanceof ServerLevel server) {
			aircraft.setOwner(player.getUUID());
			server.addFreshEntity(aircraft);
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}
}
