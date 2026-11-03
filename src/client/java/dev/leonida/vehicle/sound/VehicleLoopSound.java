package dev.leonida.vehicle.sound;

import dev.leonida.vehicle.VehicleEntity;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** A looping sound that follows a vehicle while a condition holds (engine, siren, alarm, skid). */
public class VehicleLoopSound extends AbstractTickableSoundInstance {
	private final VehicleEntity vehicle;
	private final Predicate<VehicleEntity> active;
	private final ToDoubleFunction<VehicleEntity> volumeFn, pitchFn;

	public VehicleLoopSound(SoundEvent event, VehicleEntity vehicle, Predicate<VehicleEntity> active,
			ToDoubleFunction<VehicleEntity> volume, ToDoubleFunction<VehicleEntity> pitch) {
		super(event, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
		this.vehicle = vehicle;
		this.active = active;
		this.volumeFn = volume;
		this.pitchFn = pitch;
		this.looping = true;
		this.delay = 0;
		this.attenuation = Attenuation.LINEAR;
		update();
	}

	private void update() {
		this.x = vehicle.getX();
		this.y = vehicle.getY() + 0.6;
		this.z = vehicle.getZ();
		this.volume = (float) volumeFn.applyAsDouble(vehicle);
		this.pitch = (float) pitchFn.applyAsDouble(vehicle);
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}

	@Override
	public void tick() {
		if (vehicle.isRemoved() || !active.test(vehicle)) {
			stop();
			return;
		}
		update();
	}
}
