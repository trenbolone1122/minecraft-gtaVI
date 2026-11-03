package dev.leonida.vehicle.render;

/** Materials of the vehicle atlas: tile per face, base colour, render pass and light role. */
public enum Mat {
	PAINT(0, -1), PAINT_DARK(0, -2), STRIPE(16, 0xF4F4F4, Pass.CUTOUT), TRIM(0, 0x26272B), CHROME(12, 0xE6E8EC), BLACK(0, 0x151517),
	WHITE(0, 0xF1F1F1), GLASS(1, 0x7FA0B8, Pass.GLASS), TIRE(3, 0xFFFFFF), INTERIOR(0, 0x3B3632), SEAT(13, 0x6B5543),
	GRILLE(4, 0xFFFFFF, Face.FRONT_BACK), PLATE(26, 0xFFFFFF, Face.FRONT_BACK), HEADLIGHT(6, 0xFFFFFF, Face.FRONT_BACK, Light.HEAD),
	TAILLIGHT(7, 0xFFFFFF, Face.FRONT_BACK, Light.BRAKE), REVERSE(0, 0xE8E8E8, Face.ALL, Light.REVERSE),
	INDICATOR(23, 0xFFFFFF, Face.ALL, Light.INDICATOR), POLICE_DOOR(8, 0xFFFFFF, Face.SIDES), POLICE_BAND(22, 0xFFFFFF, Face.SIDES),
	TAXI_SIGN(9, 0xFFFFFF, Face.SIDES_FRONT_BACK), CHECKER(10, 0xFFFFFF, Face.SIDES), LIGHTBAR_BASE(11, 0xFFFFFF),
	SIREN_RED(0, 0xD01818, Face.ALL, Light.SIREN_RED), SIREN_BLUE(0, 0x1840E0, Face.ALL, Light.SIREN_BLUE), METAL(12, 0xFFFFFF),
	VENT(18, 0xFFFFFF), WOOD(15, 0xFFFFFF), CAGE(17, 0xFFFFFF, Pass.CUTOUT), RUBBER(0, 0x1C1C1E), HULL(0, 0xF4F2EC),
	ORANGE(0, 0xE87A22), SKIN(0, 0xCD9673), FACE(14, 0xFFFFFF, Face.FRONT), HAIR(20, 0xFFFFFF), SHIRT(21, -3), PANTS(0, 0x2D3A55),
	YELLOW(0, 0xF0C232), RED(0, 0xB01E24), DECK(15, 0xFFFFFF),
	// lore fleet
	ACCENT(0, -4), LIVERY_DOOR(24, 0xFFFFFF, Face.SIDES), LIVERY_BAND(37, 0xFFFFFF, Face.SIDES), GLASS_DARK(1, 0x2E3A44, Pass.GLASS),
	LED_HEAD(32, 0xFFFFFF, Face.FRONT_BACK, Light.HEAD), TWIN_HEAD(33, 0xFFFFFF, Face.FRONT_BACK, Light.HEAD),
	TAIL_BAR(34, 0xFFFFFF, Face.FRONT_BACK, Light.BRAKE), ROUND_TAIL(45, 0xFFFFFF, Face.FRONT_BACK, Light.BRAKE),
	TAIL_PLAIN(0, 0xE02828, Face.ALL, Light.BRAKE), HEAD_PLAIN(0, 0xF4F4EE, Face.ALL, Light.HEAD), DRL(0, 0xF6FAFF, Face.ALL, Light.HEAD),
	WEB(35, -1, Face.SIDES), G6(36, 0xFFFFFF, Face.SIDES), CARBON(39, 0xFFFFFF), ROUNDEL(40, 0xFFFFFF, Pass.CUTOUT, Face.SIDES, Light.NONE),
	DASH(41, 0xFFFFFF, Face.BACK), SWAT(42, 0xFFFFFF, Face.SIDES), KAYAK(43, 0xFFFFFF), TAXI_STRIP(44, 0xFFFFFF, Face.SIDES),
	CHROME_GRILLE(46, 0xFFFFFF, Face.FRONT_BACK), STRAKES(47, -1, Face.SIDES), GOLD(0, 0xD8B04A), BRAKE_CAL(0, 0xC8202A),
	LEATHER(13, 0x2B2523), TAN_LEATHER(13, 0xB08458), RED_LEATHER(13, 0x8E1E22), CANVAS(0, 0x26262A), RUBBER_MAT(0, 0x222224),
	ORANGE_LENS(23, 0xFFFFFF, Face.ALL, Light.INDICATOR), FIBERGLASS(0, 0xF4F2EC), NAVY_HULL(0, 0x1F2D4D), TEAK(15, 0xFFFFFF),
	OUTBOARD(0, 0x202226), RED_STRIPE(0, 0xC8202A), HULL_PAINT(0, -1), PAINT_ACCENT2(0, -2);

	public enum Pass { SOLID, CUTOUT, GLASS }
	/** Which faces use the special tile; other faces use the plain white tile tinted with the colour. */
	public enum Face { ALL, FRONT_BACK, SIDES, SIDES_FRONT_BACK, FRONT, BACK }
	public enum Light { NONE, HEAD, BRAKE, REVERSE, INDICATOR, SIREN_RED, SIREN_BLUE }

	public final int tile;
	/** RGB, or -1 = body paint, -2 = darker body paint, -3 = per-driver shirt colour, -4 = accent (livery or two-tone). */
	public final int color;
	public final Pass pass;
	public final Face face;
	public final Light light;

	Mat(int tile, int color) { this(tile, color, Pass.SOLID, Face.ALL, Light.NONE); }
	Mat(int tile, int color, Pass pass) { this(tile, color, pass, Face.ALL, Light.NONE); }
	Mat(int tile, int color, Face face) { this(tile, color, Pass.SOLID, face, Light.NONE); }
	Mat(int tile, int color, Face face, Light light) { this(tile, color, Pass.SOLID, face, light); }

	Mat(int tile, int color, Pass pass, Face face, Light light) {
		this.tile = tile;
		this.color = color;
		this.pass = pass;
		this.face = face;
		this.light = light;
	}

	/** Tile for a face: 0 -x, 1 +x, 2 -y, 3 +y, 4 -z (back), 5 +z (front). */
	public int tileFor(int f) {
		boolean special = switch (face) {
			case ALL -> true;
			case FRONT_BACK -> f >= 4;
			case SIDES -> f <= 1;
			case SIDES_FRONT_BACK -> f <= 1 || f >= 4;
			case FRONT -> f == 5;
			case BACK -> f == 4;
		};
		return special ? tile : (pass == Pass.CUTOUT ? tile : 0);
	}
}
