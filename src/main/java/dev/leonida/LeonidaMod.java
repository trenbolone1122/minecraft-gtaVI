package dev.leonida;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LeonidaMod implements ModInitializer {
	public static final String MOD_ID = "leonida";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		dev.leonida.core.LeonidaCore.init();
		dev.leonida.look.LookModule.init();
		dev.leonida.world.WorldModule.init();
		dev.leonida.build.BuildModule.init();
		dev.leonida.character.CharacterModule.init();
		dev.leonida.vehicle.VehicleModule.init();
		dev.leonida.aircraft.AircraftModule.init();
		dev.leonida.gameplay.GameplayModule.init();
		dev.leonida.weapon.WeaponModule.init();
		dev.leonida.hud.HudModule.init();
		dev.leonida.fx.FxModule.init();
		dev.leonida.shader.ShaderModule.init();
		dev.leonida.road.RoadModule.init();
		dev.leonida.landmark.LandmarkModule.init();
		dev.leonida.vicecity.VicecityModule.init();
		LOG.info("Leonida loaded");
	}
}
