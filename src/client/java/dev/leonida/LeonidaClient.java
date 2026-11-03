package dev.leonida;

import net.fabricmc.api.ClientModInitializer;

public final class LeonidaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		dev.leonida.look.LookClient.init();
		dev.leonida.world.WorldClient.init();
		dev.leonida.build.BuildClient.init();
		dev.leonida.character.CharacterClient.init();
		dev.leonida.vehicle.VehicleClient.init();
		dev.leonida.aircraft.AircraftClient.init();
		dev.leonida.gameplay.GameplayClient.init();
		dev.leonida.weapon.WeaponClient.init();
		dev.leonida.hud.HudClient.init();
		dev.leonida.fx.FxClient.init();
		dev.leonida.shader.ShaderClient.init();
		dev.leonida.road.RoadClient.init();
		dev.leonida.landmark.LandmarkClient.init();
		dev.leonida.vicecity.VicecityClient.init();
	}
}
