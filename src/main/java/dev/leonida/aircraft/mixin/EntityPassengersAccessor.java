package dev.leonida.aircraft.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets an aircraft reorder its passengers (the list index is the seat). */
@Mixin(Entity.class)
public interface EntityPassengersAccessor {
	@Accessor("passengers")
	ImmutableList<Entity> leonida$getPassengers();

	@Accessor("passengers")
	void leonida$setPassengers(ImmutableList<Entity> passengers);
}
