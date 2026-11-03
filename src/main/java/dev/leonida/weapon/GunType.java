package dev.leonida.weapon;

/**
 * Stats for every gun. Damage is per pellet in half hearts, fire delay in ticks.
 * Numbers started from Just Enough Guns gun definitions (GPL, see NOTICE.d/weapon.md) and were retuned for GTA pacing.
 */
public enum GunType {
	//        id         display name                 cat dmg  delay mag reload pellets hip  ads  recoil range falloff minMul auto  zoom  head
	PISTOL("pistol", "Hawk P9 Pistol", 2, 6.0f, 4, 12, 28, 1, 2.0f, 0.5f, 2.6f, 60, 20, 0.5f, false, 0.80f, 2.0f),
	SMG("smg", "Kestrel Micro SMG", 3, 4.0f, 2, 30, 36, 1, 3.5f, 1.6f, 1.3f, 50, 15, 0.4f, true, 0.80f, 1.8f),
	RIFLE("rifle", "Duke 556 Assault Rifle", 4, 6.5f, 3, 30, 44, 1, 2.6f, 0.5f, 1.7f, 110, 35, 0.5f, true, 0.62f, 2.0f),
	SHOTGUN("shotgun", "Moreland 850 Shotgun", 5, 3.2f, 16, 8, 56, 8, 6.0f, 4.5f, 7.0f, 30, 8, 0.15f, false, 0.80f, 1.5f),
	SNIPER("sniper", "Longshore Sniper Rifle", 6, 30.0f, 30, 5, 60, 1, 5.0f, 0.0f, 8.0f, 260, 160, 0.7f, false, 0.14f, 3.0f),
	RPG("rpg", "RPG-7L Rocket Launcher", 7, 0.0f, 30, 1, 60, 1, 1.5f, 0.3f, 6.0f, 200, 200, 1.0f, false, 0.70f, 1.0f);

	public final String id;
	public final String displayName;
	/** Weapon wheel slot. 0 unarmed, 1 melee, 2 pistol, 3 smg, 4 rifle, 5 shotgun, 6 sniper, 7 heavy. */
	public final int category;
	public final float damage;
	public final int fireDelay;
	public final int magazine;
	public final int reloadTicks;
	public final int pellets;
	public final float hipSpread;
	public final float adsSpread;
	public final float recoil;
	public final int range;
	public final int falloffStart;
	public final float minDamageMul;
	public final boolean automatic;
	public final float zoom;
	public final float headshotMul;

	GunType(String id, String displayName, int category, float damage, int fireDelay, int magazine, int reloadTicks, int pellets,
			float hipSpread, float adsSpread, float recoil, int range, int falloffStart, float minDamageMul, boolean automatic,
			float zoom, float headshotMul) {
		this.id = id;
		this.displayName = displayName;
		this.category = category;
		this.damage = damage;
		this.fireDelay = fireDelay;
		this.magazine = magazine;
		this.reloadTicks = reloadTicks;
		this.pellets = pellets;
		this.hipSpread = hipSpread;
		this.adsSpread = adsSpread;
		this.recoil = recoil;
		this.range = range;
		this.falloffStart = falloffStart;
		this.minDamageMul = minDamageMul;
		this.automatic = automatic;
		this.zoom = zoom;
		this.headshotMul = headshotMul;
	}

	public boolean scoped() {
		return this == SNIPER;
	}

	/** Damage multiplier at a distance: full until falloffStart, then linear down to minDamageMul at range. */
	public float falloff(double dist) {
		if (dist <= falloffStart) return 1f;
		if (dist >= range) return minDamageMul;
		double t = (dist - falloffStart) / (double) (range - falloffStart);
		return (float) (1.0 - t * (1.0 - minDamageMul));
	}
}
