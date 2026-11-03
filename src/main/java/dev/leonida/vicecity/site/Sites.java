package dev.leonida.vicecity.site;

import dev.leonida.vicecity.ViceLandmarks;

/** Every vicecity landmark site, in priority order. */
public final class Sites {
	private Sites() {}

	public static void registerAll(ViceLandmarks reg) {
		reg.add(new SaharaArena());
		reg.add(new FleecaField());
		reg.add(new SaharaHQ());
		OceanDrive.registerAll(reg);
		LifeguardTower.registerAll(reg);
		reg.add(new SkyviewsWheel());
		reg.add(new PalaestraTower());
		reg.add(new BeachPatrolHQ());
	}
}
