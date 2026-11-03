package dev.leonida.aircraft.client;

import dev.leonida.aircraft.entity.AircraftEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Looping propeller or rotor sound that follows its aircraft and scales with engine power. */
public final class EngineSound extends AbstractTickableSoundInstance {
	private final AircraftEntity aircraft;

	public EngineSound(AircraftEntity aircraft, SoundEvent event) {
		super(event, SoundSource.NEUTRAL, RandomSource.create());
		this.aircraft = aircraft;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.01f;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.x = aircraft.getX();
		this.y = aircraft.getY();
		this.z = aircraft.getZ();
	}

	@Override
	public void tick() {
		if (aircraft.isRemoved() || aircraft.getEnginePower() < 0.01f) {
			stop();
			return;
		}
		x = aircraft.getX();
		y = aircraft.getY();
		z = aircraft.getZ();
		float p = aircraft.getEnginePower();
		volume = Math.min(1f, p * 1.2f);
		pitch = (aircraft.spec.isHelicopter() ? 0.7f : 0.6f) + p * 0.5f;
	}

	@Override
	public boolean canStartSilent() {
		return true;
	}
}
