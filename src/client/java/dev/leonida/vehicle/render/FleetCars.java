package dev.leonida.vehicle.render;

import dev.leonida.vehicle.VehicleSpec;

/**
 * The Leonida car fleet, modelled after the real donor cars named in the lore notes (Mustang, Challenger, Chevelle,
 * Charger, Impala, Corvette C8, Testarossa, Huracan, Diablo, Veyron, E30, R33, Crown Victoria, Raptor, RAM, Suburban,
 * G-Class, Express, armoured truck). Original geometry; numbers are pixels.
 */
final class FleetCars {
	private FleetCars() {}

	static float[] k(float... v) { return v; }

	// ------------------------------------------------------------------ muscle

	static void dominator(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 3;
		b.wheelR = 5.8f;
		b.wheelW = 4.4f;
		b.flare = 0.8f;
		b.top = k(-h, 12.2f, -h + 1.6f, 13.4f, -27, 13.6f, 9, 13.2f, 22, 12.8f, h - 4, 11.8f, h - 1.2f, 10.6f, h, 9.4f);
		b.zA = 9; b.zR1 = -1; b.zR2 = -10; b.zC = -25;
		b.sideRear = -15;
		b.cornerFront = 3;
		b.build(m);
		// fascia: trapezoid grille, slim LED lamps with fangs, splitter
		Parts.frontC(m, b, Mat.GRILLE, 8, 5.2f, 9.4f, 0.2f);
		Parts.frontC(m, b, Mat.CHROME, 1.2f, 7.4f, 8.6f, 0.4f);
		Parts.headlights(m, b, Mat.LED_HEAD, 9.2f, 14.6f, 9.0f, 10.4f);
		Parts.front(m, b, Mat.DRL, 9.6f, 10.4f, 7.2f, 9.0f, 0.3f);
		Parts.frontC(m, b, Mat.BLACK, 12, 2.6f, 4.2f, 0.8f);
		Parts.front(m, b, Mat.BLACK, 10, 13.5f, 4.4f, 6.4f, 0.2f);
		// tri-bar tail lamps, black panel, diffuser and quad tips
		Parts.rearC(m, b, Mat.BLACK, 14.2f, 10, 12.8f, 0.15f);
		for (float x : new float[] {9.2f, 11.0f, 12.8f}) Parts.rear(m, b, Mat.TAIL_PLAIN, x, x + 1.2f, 10.2f, 12.6f, 0.35f);
		Parts.rear(m, b, Mat.REVERSE, 6, 7.4f, 10.6f, 12.2f, 0.3f);
		Parts.rearC(m, b, Mat.BLACK, 12, 2.6f, 5.2f, 0.4f);
		Parts.exhaustsRear(m, b, 7.2f, 4.2f, 0.9f, Mat.CHROME, true);
		Parts.exhaustsRear(m, b, 9.6f, 4.2f, 0.9f, Mat.CHROME, true);
		Parts.plates(m, b, 5.4f, 6.4f);
		// hood vents, ducktail lip, mirrors, handles
		Parts.onTop(m, b, Mat.VENT, -6.5f, -3.5f, 22, 28, 0, 0.15f);
		Parts.onTop(m, b, Mat.VENT, 3.5f, 6.5f, 22, 28, 0, 0.15f);
		Parts.onTop(m, b, Mat.PAINT, -13, 13, -h + 0.5f, -h + 2.5f, 0, 0.9f);
		Parts.mirrors(m, b, Mat.PAINT, b.zA - 0.5f, 13.8f);
		Parts.handles(m, b, Mat.BLACK, 11.6f, -6);
		Parts.seam(m, b, 8, 4.5f, 13);
		Parts.seam(m, b, -8.5f, 4.5f, 13);
		Parts.markers(m, b, 7.5f);
		m.rimTile = 27;
	}

	static void gauntlet(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 3.5f;
		b.wheelR = 6;
		b.wheelW = 4.6f;
		b.flare = 0.4f;
		b.top = k(-h, 13.2f, -h + 1.2f, 14.2f, -22, 14.4f, 8, 14.0f, 30, 13.8f, h - 1.5f, 13.4f, h, 12.6f);
		b.zA = 8; b.zR1 = -1.5f; b.zR2 = -12; b.zC = -22;
		b.sideRear = -15.5f;
		b.cornerFront = 1.2f;
		b.cornerRear = 1.2f;
		b.build(m);
		// full-width grille with quad round lamps
		Parts.frontC(m, b, Mat.GRILLE, 15, 8.4f, 12.8f, 0.15f);
		Parts.headlights(m, b, Mat.TWIN_HEAD, 9.6f, 15.2f, 8.8f, 12.4f);
		Parts.frontC(m, b, Mat.CHROME, 1.5f, 10, 11.2f, 0.4f);
		Parts.bumperF(m, b, Mat.PAINT, 4.6f, 8.4f, 0.6f);
		Parts.frontC(m, b, Mat.BLACK, 9, 4.8f, 7.2f, 0.9f);
		// full-width racetrack tail bar
		Parts.rearC(m, b, Mat.TAIL_BAR, 15.2f, 11.4f, 13.6f, 0.25f);
		Parts.bumperR(m, b, Mat.PAINT, 4.6f, 8.6f, 0.6f);
		Parts.exhaustsRear(m, b, 11.5f, 4.6f, 1.1f, Mat.CHROME, true);
		Parts.plates(m, b, 5.2f, 9);
		// Hellfire: raised hood with twin snorkels, black hood centre, spoiler, side stripe
		Parts.onTop(m, b, Mat.PAINT, -6, 6, 12, 32, 0, 1.2f);
		Parts.onTop(m, b, Mat.BLACK, -5, -2.5f, 14, 22, 1.2f, 2.2f);
		Parts.onTop(m, b, Mat.BLACK, 2.5f, 5, 14, 22, 1.2f, 2.2f);
		Parts.onTop(m, b, Mat.PAINT, -15, 15, -h + 0.5f, -h + 3.5f, 0, 1.2f);
		Parts.sideStrip(m, b, Mat.BLACK, 11.4f, 12.4f, -h + 3, h - 3, 0.12f);
		Parts.mirrors(m, b, Mat.PAINT, b.zA - 0.5f, 14.6f);
		Parts.handles(m, b, Mat.CHROME, 12.4f, -7);
		Parts.seam(m, b, 7.5f, 4.5f, 14);
		Parts.seam(m, b, -10.5f, 4.5f, 14);
		Parts.markers(m, b, 8.5f);
		m.rimTile = 28;
	}

	static void tulip(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 3.5f;
		b.wheelR = 5.8f;
		b.wheelW = 4.2f;
		b.top = k(-h, 12.0f, -h + 1.8f, 13.2f, -26, 13.6f, -20, 13.2f, 8, 13.4f, 30, 13.0f, h - 1.5f, 12.6f, h, 11.6f);
		b.zA = 8; b.zR1 = -2; b.zR2 = -13; b.zC = -23;
		b.sideRear = -15;
		b.roofMat = Mat.CANVAS;
		b.windowTrim = Mat.CHROME;
		b.cornerFront = 1.5f;
		b.build(m);
		// chrome bumpers, full-width grille with twin round lamps
		Parts.bumperF(m, b, Mat.CHROME, 4.2f, 6.6f, 1.0f);
		Parts.frontC(m, b, Mat.GRILLE, 14.8f, 7, 11.4f, 0.15f);
		Parts.frontC(m, b, Mat.CHROME, 15, 11.4f, 11.9f, 0.3f);
		Parts.headlights(m, b, Mat.TWIN_HEAD, 9.0f, 15.0f, 7.6f, 11.0f);
		Parts.frontC(m, b, Mat.CHROME, 1.2f, 8.6f, 9.8f, 0.4f);
		Parts.bumperR(m, b, Mat.CHROME, 4.2f, 7.0f, 1.0f);
		Parts.tails(m, b, Mat.TAIL_PLAIN, 8.5f, 13.8f, 5.0f, 6.6f);
		Parts.rearC(m, b, Mat.CHROME, 15, 11.6f, 12.1f, 0.2f);
		Parts.plates(m, b, 4.6f, 8.6f);
		// cowl induction bulge, rally stripes, vinyl roof, side spear
		Parts.onTop(m, b, Mat.PAINT, -5, 5, 10, 26, 0, 1.0f);
		Parts.onTop(m, b, Mat.VENT, -3, 3, 10, 11, 1.0f, 1.6f);
		Parts.onTop(m, b, Mat.STRIPE, -5.5f, 5.5f, 26, h - 1, 0, 0.06f);
		Parts.onTop(m, b, Mat.STRIPE, -5.5f, 5.5f, -h + 1, -23, 0, 0.06f);
		Parts.sideStrip(m, b, Mat.CHROME, 9.5f, 10, -h + 4, h - 4, 0.15f);
		Parts.mirrors(m, b, Mat.CHROME, b.zA - 0.5f, 14);
		Parts.handles(m, b, Mat.CHROME, 12, -6.5f);
		Parts.seam(m, b, 7.5f, 4.5f, 13.2f);
		Parts.seam(m, b, -9.5f, 4.5f, 13.2f);
		Parts.exhaustsRear(m, b, 10, 3.4f, 0.9f, Mat.CHROME, true);
		m.rimTile = 30;
	}

	/** Vapid Ganado: 70s coupe utility (El Camino style), short cab, long hood, open bed, chrome bumpers. */
	static void ganado(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		float belt = 13.4f, floor = belt - 5.4f, cabBack = -11.5f;
		b.clear = 4.0f;
		b.wheelR = 6.0f;
		b.wheelW = 4.4f;
		b.archGap = 1.5f;
		b.top = k(-h, floor, cabBack - 0.5f, floor, cabBack, belt, 9, belt, 28, belt + 0.2f, h - 1.5f, belt - 0.3f, h, belt - 1.5f);
		b.zA = 8.5f; b.zR1 = -1.0f; b.zR2 = cabBack + 2.2f; b.zC = cabBack + 0.6f;
		b.sideRear = cabBack + 2.8f;
		b.windowTrim = Mat.CHROME;
		b.cornerFront = 1.5f;
		b.cornerRear = 0.8f;
		b.rearWellRoof = false;
		b.build(m);
		// rear wheel tubs inside the bed
		float in = b.inner(), rr = b.archR(b.axleR);
		m.pair(Mat.PAINT_DARK, in - 1.0f, floor, b.axleR - rr + 1, b.hw(b.axleR) - 1.2f, b.wr(b.axleR) * 2 + 1.0f, b.axleR + rr - 1);
		// bed: floor, side walls over the rear arches, cab back wall, tailgate with lettering
		m.box(Mat.BLACK, -b.hw(-20) + 1.2f, floor, -h + 1.4f, b.hw(-20) - 1.2f, floor + 0.3f, cabBack - 0.6f);
		for (float z = -h; z < cabBack - 1e-3f; z += 2) {
			float za = Math.min(cabBack - 0.5f, z + 2), w = b.hw((z + za) / 2);
			float a = b.arch((z + za) / 2);
			m.pair(Mat.PAINT, w - 1.2f, Math.max(floor, a > 0 ? a : floor), z, w, belt, za);
		}
		m.box(Mat.PAINT, -b.hw(cabBack) + 1.2f, floor, cabBack - 1.4f, b.hw(cabBack) - 1.2f, belt, cabBack - 0.4f);
		float zt = b.rearZ(b.hw(-h));
		m.box(Mat.PAINT, -b.hw(-h) + 1.2f, floor - 2.5f, zt, b.hw(-h) - 1.2f, belt, zt + 1.3f);
		for (int i = 0; i < 5; i++) {
			float x = -5.2f + i * 2.6f;
			m.box(Mat.CHROME, x - 0.8f, belt - 3.0f, zt - 0.15f, x + 0.8f, belt - 2.0f, zt);
		}
		// chrome bed rail caps and white roof drip trim
		m.pair(Mat.CHROME, b.hw(-20) - 1.3f, belt, -h + 1, b.hw(-20), belt + 0.3f, cabBack - 0.5f);
		m.pair(Mat.WHITE, b.hw(-4) - 2.2f, b.roofY - 0.4f, b.zR2 - 0.6f, b.hw(-4) - 1.6f, b.roofY + 0.2f, b.zR1 + 0.6f);
		m.box(Mat.WHITE, -b.hw(-4) + 1.6f, b.roofY - 0.4f, b.zR2 - 0.9f, b.hw(-4) - 1.6f, b.roofY + 0.2f, b.zR2 - 0.3f);
		// fascia: full-width grille with twin round lamps, chrome bumper
		Parts.bumperF(m, b, Mat.CHROME, 4.4f, 6.8f, 1.0f);
		Parts.frontC(m, b, Mat.GRILLE, 14.8f, 7.0f, 10.6f, 0.15f);
		Parts.frontC(m, b, Mat.CHROME, 15, 10.6f, 11.1f, 0.3f);
		Parts.headlights(m, b, Mat.TWIN_HEAD, 9.0f, 15.0f, 7.4f, 10.4f);
		Parts.frontC(m, b, Mat.CHROME, 1.0f, 7.4f, 10.4f, 0.4f);
		// rear: vertical lamps in the quarter ends, chrome bumper with the plate
		Parts.rear(m, b, Mat.TAIL_PLAIN, b.hw(-h) - 1.6f, b.hw(-h) - 0.1f, 6.8f, belt - 0.8f, 0.3f);
		Parts.bumperR(m, b, Mat.CHROME, 4.2f, 6.6f, 1.0f);
		Parts.plates(m, b, 0, 4.6f);
		Parts.exhaustsRear(m, b, 9, 3.6f, 0.8f, Mat.CHROME, false);
		// side: chrome rocker spear, body crease, mirrors, handles, door seams, markers
		Parts.sideStrip(m, b, Mat.CHROME, 5.0f, 5.6f, b.axleR + 8, b.axleF - 8, 0.15f);
		Parts.sideStrip(m, b, Mat.PAINT_DARK, 9.2f, 9.5f, -h + 3, h - 6, 0.1f);
		Parts.mirrors(m, b, Mat.CHROME, b.zA - 0.5f, belt + 0.6f);
		Parts.handles(m, b, Mat.CHROME, belt - 1.6f, cabBack + 4);
		Parts.seam(m, b, b.zA, 5.2f, belt);
		Parts.seam(m, b, cabBack + 1.5f, 5.2f, belt);
		Parts.markers(m, b, 8);
		m.rimTile = 30;
	}

	static void buffalo(VehicleModel m, VehicleSpec s, boolean police) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 3.5f;
		b.wheelR = 6;
		b.wheelW = 4.4f;
		b.top = k(-h, 13.2f, -h + 1.5f, 14.2f, -30, 14.4f, 15, 14.0f, 35, 13.4f, h - 1.5f, 12.4f, h, 11.4f);
		b.zA = 15; b.zR1 = 6; b.zR2 = -17; b.zC = -26;
		b.bPillar = -5;
		b.sideRear = -20.5f;
		b.pillar = Mat.BLACK;
		if (police) livery(b);
		b.build(m);
		Parts.frontC(m, b, Mat.GRILLE, 7.6f, 7.2f, 11.2f, 0.2f);
		Parts.frontC(m, b, Mat.BLACK, 7.6f, 9.0f, 9.5f, 0.3f);
		Parts.headlights(m, b, Mat.LED_HEAD, 8.2f, 14.6f, 9.8f, 11.2f);
		Parts.bumperF(m, b, police ? Mat.BLACK : Mat.PAINT, 4.0f, 7.2f, 0.6f);
		Parts.frontC(m, b, Mat.BLACK, 10, 4.2f, 6.4f, 0.9f);
		Parts.rearC(m, b, Mat.TAIL_BAR, 15, 11.6f, 13.6f, 0.25f);
		Parts.bumperR(m, b, police ? Mat.BLACK : Mat.PAINT, 4.0f, 8.0f, 0.6f);
		Parts.exhaustsRear(m, b, 10, 4.4f, 1.0f, Mat.CHROME, true);
		Parts.plates(m, b, 4.8f, 8.6f);
		Parts.onTop(m, b, Mat.PAINT, -15, 15, -h + 0.5f, -h + 2.5f, 0, 0.6f);
		Parts.mirrors(m, b, Mat.BLACK, b.zA - 0.5f, 14.6f);
		Parts.handles(m, b, Mat.CHROME, 12.2f, 7, -9);
		Parts.seam(m, b, 14, 4.5f, 14.2f);
		Parts.seam(m, b, -5, 4.5f, 14.2f);
		Parts.seam(m, b, -20, 6f, 14.2f);
		Parts.markers(m, b, 9);
		m.rimTile = police ? 30 : 28;
		if (police) {
			policeKit(m, b, -2, true);
			Parts.sideStrip(m, b, Mat.LIVERY_DOOR, 9.5f, 13.6f, -4, 13, 0.12f);
		}
	}

	static void voodoo(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 2.4f;
		b.wheelR = 5.2f;
		b.wheelW = 3.8f;
		b.archGap = 1.8f;
		b.top = k(-h, 11.6f, -h + 2, 13.0f, -28, 13.2f, 6, 13.0f, 38, 12.6f, h - 1.5f, 12.2f, h, 11.2f);
		b.zA = 6; b.zR1 = -4; b.zR2 = -14; b.zC = -26;
		b.convertible = true;
		b.openTop = true;
		b.lower = Mat.WEB;
		b.windowTrim = Mat.CHROME;
		b.pillar = Mat.CHROME;
		b.seatMat = Mat.TAN_LEATHER;
		b.cornerFront = 1.2f;
		b.build(m);
		// batwing fins over the rear deck
		for (float z = -h + 1; z < -24; z += 2) {
			float t = (z - (-h)) / 18f;
			float w = b.hw(z);
			m.pair(Mat.PAINT, w - 3.2f, b.top(z), z, w - 0.2f, b.top(z) + 1.4f * (1 - t * 0.7f), z + 2);
		}
		Parts.bumperF(m, b, Mat.CHROME, 3.4f, 6.4f, 1.2f);
		Parts.frontC(m, b, Mat.CHROME_GRILLE, 8.5f, 6.8f, 10.8f, 0.2f);
		Parts.headlights(m, b, Mat.TWIN_HEAD, 8.8f, 15.6f, 7.4f, 10.8f);
		Parts.frontC(m, b, Mat.CHROME, 15.8f, 10.8f, 11.3f, 0.4f);
		Parts.bumperR(m, b, Mat.CHROME, 3.4f, 6.6f, 1.2f);
		Parts.tails(m, b, Mat.ROUND_TAIL, 5.0f, 14.6f, 8.2f, 11.2f);
		Parts.rearC(m, b, Mat.CHROME, 15.6f, 11.2f, 11.7f, 0.3f);
		Parts.plates(m, b, 4.2f, 7.2f);
		Parts.sideStrip(m, b, Mat.CHROME, 9.0f, 9.5f, -h + 3, h - 3, 0.18f);
		Parts.sideStrip(m, b, Mat.GOLD, 4.2f, 4.7f, -h + 6, h - 6, 0.15f);
		Parts.mirrors(m, b, Mat.CHROME, b.zA - 0.5f, 13.4f);
		Parts.handles(m, b, Mat.CHROME, 11.4f, -8);
		Parts.seam(m, b, 5.5f, 3.5f, 13);
		Parts.seam(m, b, -12, 3.5f, 13);
		Parts.antenna(m, 9, 12.6f, -h + 6, 10);
		m.rimTile = 29;
	}

	// ------------------------------------------------------------------ sports and super

	static void coquette(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 2.5f;
		b.wheelR = 5.5f;
		b.frontWheelR = 5.2f;
		b.wheelW = 4.6f;
		b.top = k(-h, 12.0f, -h + 1.2f, 12.8f, -20, 12.6f, 16, 11.0f, 26, 9.6f, h - 3, 7.8f, h, 6.4f);
		b.zA = 16; b.zR1 = 7.5f; b.zR2 = -2.5f; b.zC = -17;
		b.sideRear = -5;
		b.roofMat = Mat.BLACK;
		b.pillar = Mat.BLACK;
		b.gi = 2.2f;
		b.cornerFront = 3.5f;
		b.cornerRear = 2.5f;
		b.build(m);
		Parts.headlights(m, b, Mat.LED_HEAD, 9.6f, 15.0f, 6.6f, 7.6f);
		Parts.frontC(m, b, Mat.GRILLE, 6, 3.2f, 5.6f, 0.2f);
		Parts.front(m, b, Mat.BLACK, 9, 14, 2.8f, 5.2f, 0.2f);
		Parts.frontC(m, b, Mat.CARBON, 14, 2.0f, 2.6f, 1.2f);
		Parts.rearC(m, b, Mat.BLACK, 15, 6.6f, 11.4f, 0.15f);
		Parts.tails(m, b, Mat.TAIL_BAR, 7.5f, 15.6f, 10.0f, 11.4f);
		Parts.rearC(m, b, Mat.GRILLE, 6, 3.6f, 6.6f, 0.2f);
		for (float x : new float[] {1.4f, 3.8f}) Parts.exhaustsRear(m, b, x, 5.0f, 0.8f, Mat.CHROME, true);
		Parts.plates(m, b, 0, 7.0f);
		// side scallop intakes, engine glass, ducktail, mirrors
		Parts.sideStrip(m, b, Mat.VENT, 5.6f, 10.6f, -9.5f, -3.5f, 0.12f);
		Parts.sideStrip(m, b, Mat.BLACK, 4.6f, 5.6f, -12, 10, 0.2f);
		Parts.onTop(m, b, Mat.GLASS, -6, 6, -15, -6, 0, 0.12f);
		Parts.onTop(m, b, Mat.PAINT, -15, 15, -h + 0.5f, -h + 2.5f, 0, 0.8f);
		Parts.mirrors(m, b, Mat.PAINT, b.zA - 1, 11.6f);
		Parts.markers(m, b, 5.5f);
		m.rimTile = 28;
	}

	static void cheetah(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 2.5f;
		b.wheelR = 5.4f;
		b.wheelW = 4.6f;
		b.top = k(-h, 11.4f, -h + 1.4f, 12.2f, -12, 12.2f, 14, 10.6f, 28, 8.4f, h - 2, 7.0f, h, 6.0f);
		b.zA = 14; b.zR1 = 6; b.zR2 = -4; b.zC = -13;
		b.sideRear = -6;
		b.pillar = Mat.PAINT;
		b.gi = 2.4f;
		b.cornerFront = 3;
		b.cornerRear = 1;
		b.wid2 = k(-b.hl, b.hwMax - 0.6f, -b.hl + 3, b.hwMax, 0, b.hwMax, 12, b.hwMax - 1.2f, b.hl - 3, b.hwMax - 1.8f, b.hl, b.hwMax - 4);
		b.build(m);
		// signature side strakes over the radiator intakes
		Parts.sideStrip(m, b, Mat.STRAKES, 5.0f, 10.2f, -22, -1, 0.35f);
		Parts.sideStrip(m, b, Mat.BLACK, 5.0f, 10.2f, -22, -1, 0.05f);
		// pop-up lamp lids on the bonnet, slim driving lamps, front grille
		Parts.onTop(m, b, Mat.TRIM, -14, -9, 27.5f, 28, 0, 0.12f);
		Parts.onTop(m, b, Mat.TRIM, 9, 14, 27.5f, 28, 0, 0.12f);
		Parts.headlights(m, b, Mat.HEAD_PLAIN, 9.6f, 13.6f, 4.6f, 5.8f);
		Parts.front(m, b, Mat.ORANGE_LENS, 6.8f, 9.4f, 4.6f, 5.8f, 0.25f);
		Parts.frontC(m, b, Mat.GRILLE, 6.6f, 3.6f, 5.8f, 0.2f);
		Parts.bumperF(m, b, Mat.PAINT, 2.6f, 3.6f, 0.4f);
		// louvred full-width tail panel over the lamps
		Parts.tails(m, b, Mat.TAIL_PLAIN, 6.5f, 15.6f, 7.6f, 10.8f);
		Parts.rearC(m, b, Mat.GRILLE, 15.6f, 7.4f, 11.0f, 0.45f);
		Parts.bumperR(m, b, Mat.BLACK, 3.0f, 6.0f, 0.5f);
		Parts.exhaustsRear(m, b, 5, 4.2f, 0.9f, Mat.CHROME, true);
		Parts.plates(m, b, 0, 4.6f);
		// single high mirror on the driver's A pillar
		m.box(Mat.PAINT, -b.hw(b.zA) + 0.4f, 12.6f, b.zA - 3.4f, -b.hw(b.zA) + 2.4f, 14.0f, b.zA - 1.6f);
		Parts.markers(m, b, 5.2f);
		m.rimTile = 27;
	}

	static void tempesta(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 2.2f;
		b.wheelR = 5.5f;
		b.wheelW = 4.8f;
		b.top = k(-h, 10.6f, -h + 1.6f, 12.2f, -24, 12.0f, 12, 10.2f, 26, 8.2f, h - 2, 6.4f, h, 5.4f);
		b.zA = 12; b.zR1 = 4; b.zR2 = -4; b.zC = -24;
		b.sideRear = -8;
		b.pillar = Mat.BLACK;
		b.roofMat = Mat.PAINT;
		b.gi = 2.4f;
		b.cornerFront = 3.5f;
		b.wid2 = k(-b.hl, b.hwMax - 1.2f, -b.hl + 4, b.hwMax, 6, b.hwMax - 0.6f, b.hl - 4, b.hwMax - 1.6f, b.hl, b.hwMax - 4.5f);
		b.build(m);
		Parts.headlights(m, b, Mat.LED_HEAD, 9.0f, 14.2f, 5.6f, 6.6f);
		Parts.front(m, b, Mat.BLACK, 8.6f, 14.6f, 2.0f, 5.0f, 0.2f);
		Parts.frontC(m, b, Mat.GRILLE, 6, 2.2f, 4.4f, 0.2f);
		Parts.frontC(m, b, Mat.CARBON, 14, 1.6f, 2.2f, 1.0f);
		Parts.rearC(m, b, Mat.GRILLE, 15, 4.8f, 9.4f, 0.2f);
		Parts.tails(m, b, Mat.TAIL_BAR, 8.6f, 15.2f, 9.4f, 10.4f);
		Parts.exhaustsRear(m, b, 4.6f, 5.6f, 1.0f, Mat.CHROME, true);
		Parts.rearC(m, b, Mat.CARBON, 12, 1.8f, 3.8f, 0.6f);
		Parts.plates(m, b, 0, 0);
		// huge side intakes, louvred engine cover, roof scoop line
		Parts.sideStrip(m, b, Mat.VENT, 4.6f, 9.6f, -14, -5, 0.1f);
		Parts.sideStrip(m, b, Mat.BLACK, 3.0f, 4.0f, -18, 14, 0.25f);
		Parts.onTop(m, b, Mat.VENT, -5, 5, -22, -9, 0, 0.15f);
		Parts.onTop(m, b, Mat.PAINT, -14, 14, -h + 0.5f, -h + 2.5f, 0, 0.6f);
		Parts.mirrors(m, b, Mat.PAINT, b.zA - 1.2f, 10.8f);
		Parts.markers(m, b, 4.6f);
		m.rimTile = 28;
	}

	static void infernus(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 2.2f;
		b.wheelR = 5.4f;
		b.wheelW = 5;
		b.top = k(-h, 11.8f, -h + 1.4f, 12.6f, -14, 12.4f, 12, 9.6f, 26, 8.0f, h - 2, 6.6f, h, 5.8f);
		b.zA = 12; b.zR1 = 4; b.zR2 = -6; b.zC = -14;
		b.sideRear = -8;
		b.pillar = Mat.BLACK;
		b.gi = 2.6f;
		b.cornerFront = 3;
		b.cornerRear = 1;
		b.build(m);
		Parts.headlights(m, b, Mat.TWIN_HEAD, 8.8f, 14.4f, 5.2f, 6.6f);
		Parts.front(m, b, Mat.ORANGE_LENS, 6.8f, 8.6f, 5.2f, 6.6f, 0.25f);
		Parts.frontC(m, b, Mat.GRILLE, 10, 2.2f, 4.6f, 0.2f);
		Parts.rearC(m, b, Mat.GRILLE, 15.6f, 4.6f, 7.8f, 0.2f);
		Parts.tails(m, b, Mat.ROUND_TAIL, 7.8f, 15.4f, 8.2f, 11.2f);
		Parts.exhaustsRear(m, b, 1.4f, 4.2f, 0.9f, Mat.CHROME, true);
		Parts.exhaustsRear(m, b, 3.6f, 4.2f, 0.9f, Mat.CHROME, true);
		Parts.plates(m, b, 0, 0);
		// NACA door duct, rear side intake, SV wing
		Parts.sideStrip(m, b, Mat.BLACK, 7.0f, 8.6f, -2, 6, 0.1f);
		Parts.sideStrip(m, b, Mat.VENT, 5.0f, 10.8f, -18, -9, 0.1f);
		Parts.wing(m, b, Mat.PAINT, -h + 3.5f, 2.6f, 4.2f, 14);
		Parts.onTop(m, b, Mat.VENT, -6, 6, -13, -6, 0, 0.12f);
		Parts.mirrors(m, b, Mat.PAINT, b.zA - 1.2f, 10.4f);
		Parts.markers(m, b, 4.8f);
		m.rimTile = 27;
	}

	static void adder(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 2.4f;
		b.wheelR = 5.6f;
		b.wheelW = 4.8f;
		b.top = k(-h, 11.0f, -h + 1.8f, 12.8f, -20, 13.4f, -12, 13.4f, 12, 11.4f, 26, 10.0f, h - 2.5f, 8.6f, h, 7.0f);
		b.zA = 12; b.zR1 = 4; b.zR2 = -4; b.zC = -12;
		b.sideRear = -7;
		b.roofMat = Mat.BLACK;
		b.pillar = Mat.BLACK;
		b.gi = 2.4f;
		b.cornerFront = 4;
		b.cornerRear = 3;
		b.toneY = 30;
		b.toneY2 = 0;
		b.build(m);
		// horseshoe grille in chrome, round lamps, black C-shaped side sweep
		Parts.frontC(m, b, Mat.CHROME, 3.6f, 3.0f, 8.6f, 0.2f);
		Parts.frontC(m, b, Mat.CHROME_GRILLE, 2.8f, 3.6f, 8.0f, 0.4f);
		Parts.headlights(m, b, Mat.LED_HEAD, 6.8f, 13.4f, 6.8f, 8.2f);
		Parts.front(m, b, Mat.GRILLE, 7, 13.4f, 2.6f, 4.8f, 0.2f);
		Parts.sideStrip(m, b, Mat.BLACK, 3.0f, 12.6f, -6, -1.5f, 0.15f);
		Parts.sideStrip(m, b, Mat.BLACK, 11.0f, 12.6f, -1.5f, 12, 0.15f);
		Parts.rearC(m, b, Mat.BLACK, 15.6f, 4.6f, 11.4f, 0.15f);
		Parts.tails(m, b, Mat.TAIL_BAR, 2.0f, 15.4f, 10.4f, 11.2f);
		Parts.rearC(m, b, Mat.CHROME, 2.6f, 4.2f, 7.2f, 0.4f);
		Parts.rearC(m, b, Mat.BLACK, 2.0f, 4.7f, 6.7f, 0.5f);
		Parts.plates(m, b, 0, 7.6f);
		// twin roof air scoops behind the cabin
		m.pair(Mat.BLACK, 2, 13.4f, -11.5f, 5.5f, 16.0f, -6);
		m.pair(Mat.VENT, 2.2f, 14.0f, -6.05f, 5.3f, 15.6f, -5.95f);
		Parts.onTop(m, b, Mat.VENT, -6, 6, -h + 3, -14, 0, 0.12f);
		Parts.mirrors(m, b, Mat.BLACK, b.zA - 1.2f, 12.2f);
		Parts.markers(m, b, 5.4f);
		m.rimTile = 27;
	}

	static void sentinelCabrio(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 3.5f;
		b.wheelR = 5.4f;
		b.wheelW = 4;
		b.top = k(-h, 12.4f, -h + 1.2f, 13.4f, -22, 13.6f, 10, 13.2f, 32, 12.6f, h - 1, 12.2f, h, 11.6f);
		b.zA = 10; b.zR1 = 2; b.zR2 = -10; b.zC = -20;
		b.convertible = true;
		b.pillar = Mat.BLACK;
		b.windowTrim = Mat.BLACK;
		b.seatMat = Mat.TAN_LEATHER;
		b.cornerFront = 1;
		b.cornerRear = 1;
		b.build(m);
		// quad round lamps either side of the kidney grille, slim bumpers with chrome strip
		Parts.headlights(m, b, Mat.TWIN_HEAD, 3.6f, 12.8f, 8.4f, 11.2f);
		Parts.front(m, b, Mat.CHROME_GRILLE, 0.4f, 2.8f, 7.8f, 11.4f, 0.3f);
		Parts.front(m, b, Mat.ORANGE_LENS, 9.6f, 13.6f, 6.0f, 7.0f, 0.25f);
		Parts.bumperF(m, b, Mat.BLACK, 4.2f, 6.0f, 1.2f);
		Parts.frontC(m, b, Mat.CHROME, 13.6f, 5.6f, 6.0f, 1.3f);
		Parts.tails(m, b, Mat.TAIL_PLAIN, 6.5f, 13.6f, 10.0f, 12.2f);
		Parts.rear(m, b, Mat.ORANGE_LENS, 4.8f, 6.5f, 10.0f, 12.2f, 0.2f);
		Parts.bumperR(m, b, Mat.BLACK, 4.2f, 6.2f, 1.2f);
		Parts.rearC(m, b, Mat.CHROME, 13.6f, 5.8f, 6.2f, 1.3f);
		Parts.exhaustsRear(m, b, -9, 3.8f, 0.7f, Mat.CHROME, false);
		Parts.plates(m, b, 4.0f, 7.6f);
		Parts.sideStrip(m, b, Mat.BLACK, 8.6f, 9.4f, -h + 3, h - 3, 0.15f);
		Parts.mirrors(m, b, Mat.BLACK, b.zA - 0.5f, 13.6f);
		Parts.handles(m, b, Mat.BLACK, 12.2f, -6);
		Parts.seam(m, b, 9.5f, 4.5f, 13.4f);
		Parts.seam(m, b, -9, 4.5f, 13.4f);
		m.rimTile = 27;
	}

	static void elegy(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 3;
		b.wheelR = 5.6f;
		b.wheelW = 4.4f;
		b.flare = 0.4f;
		b.top = k(-h, 12.6f, -h + 1.4f, 13.8f, -24, 14.0f, 13, 13.2f, 30, 12.4f, h - 1.5f, 11.4f, h, 10.0f);
		b.zA = 13; b.zR1 = 4; b.zR2 = -12; b.zC = -21;
		b.sideRear = -14.5f;
		b.pillar = Mat.BLACK;
		b.build(m);
		Parts.headlights(m, b, Mat.HEAD_PLAIN, 8.4f, 14.0f, 8.8f, 10.4f);
		Parts.frontC(m, b, Mat.GRILLE, 6.6f, 8.8f, 10.4f, 0.2f);
		Parts.frontC(m, b, Mat.CHROME, 1, 9.2f, 10.0f, 0.35f);
		Parts.frontC(m, b, Mat.BLACK, 9.5f, 4.0f, 6.6f, 0.4f);
		Parts.front(m, b, Mat.ORANGE_LENS, 11, 14, 5.0f, 6.0f, 0.3f);
		Parts.frontC(m, b, Mat.CARBON, 13, 2.6f, 3.2f, 1.2f);
		Parts.tails(m, b, Mat.ROUND_TAIL, 4.6f, 13.8f, 10.4f, 12.8f);
		Parts.rearC(m, b, Mat.BLACK, 4.6f, 10.4f, 12.8f, 0.15f);
		Parts.bumperR(m, b, Mat.PAINT, 4.0f, 7.2f, 0.5f);
		Parts.exhaust(m, -9, 4.2f, b.rearZ(10) + 1.6f, 1.2f, Mat.CHROME);
		Parts.plates(m, b, 4.2f, 8.0f);
		// GT wing, race number roundels, canards
		Parts.wing(m, b, Mat.PAINT, -h + 3.2f, 3.0f, 3.6f, 13.6f);
		Parts.sideStrip(m, b, Mat.ROUNDEL, 5.6f, 11.6f, -6, 0, 0.2f);
		Parts.front(m, b, Mat.CARBON, 12.4f, 14.6f, 6.6f, 7.2f, 0.8f);
		Parts.mirrors(m, b, Mat.PAINT, b.zA - 0.5f, 14.2f);
		Parts.handles(m, b, Mat.BLACK, 12.6f, -7);
		Parts.seam(m, b, 12.5f, 4.5f, 13.6f);
		Parts.seam(m, b, -10, 4.5f, 13.6f);
		Parts.markers(m, b, 7.2f);
		m.rimTile = 28;
	}

	// ------------------------------------------------------------------ sedans, trucks, SUVs, vans

	static void stanier(VehicleModel m, VehicleSpec s, boolean police) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 3.8f;
		b.wheelR = 5.6f;
		b.wheelW = 4;
		b.top = k(-h, 13.2f, -h + 1.6f, 14.6f, -29, 14.8f, 14, 14.4f, 38, 13.8f, h - 1, 13.4f, h, 12.6f);
		b.zA = 14; b.zR1 = 5; b.zR2 = -18; b.zC = -27;
		b.bPillar = -6;
		b.sideRear = -21;
		b.windowTrim = Mat.CHROME;
		b.cornerFront = 1.4f;
		b.cornerRear = 1.4f;
		if (police) livery(b);
		b.build(m);
		Parts.frontC(m, b, Mat.CHROME_GRILLE, 7.4f, 9.2f, 12.4f, 0.2f);
		Parts.frontC(m, b, Mat.CHROME, 7.8f, 12.4f, 12.9f, 0.3f);
		Parts.headlights(m, b, Mat.HEAD_PLAIN, 8.0f, 13.6f, 9.8f, 12.2f);
		Parts.front(m, b, Mat.ORANGE_LENS, 13.6f, 15.4f, 9.8f, 12.2f, 0.2f);
		Parts.bumperF(m, b, police ? Mat.BLACK : Mat.TRIM, 4.4f, 8.6f, 0.8f);
		Parts.tails(m, b, Mat.TAIL_PLAIN, 8.4f, 15.4f, 11.0f, 13.8f);
		Parts.rear(m, b, Mat.REVERSE, 6.4f, 8.4f, 11.0f, 13.8f, 0.2f);
		Parts.rearC(m, b, Mat.CHROME, 6.4f, 13.2f, 13.7f, 0.2f);
		Parts.bumperR(m, b, police ? Mat.BLACK : Mat.TRIM, 4.4f, 8.6f, 0.8f);
		Parts.exhaustsRear(m, b, 10, 3.6f, 0.7f, Mat.METAL, false);
		Parts.plates(m, b, 5.4f, 9.6f);
		Parts.mirrors(m, b, Mat.BLACK, b.zA - 0.5f, 15);
		Parts.handles(m, b, Mat.CHROME, 12.6f, 6, -11);
		Parts.seam(m, b, 13.5f, 5f, 14.4f);
		Parts.seam(m, b, -6, 5f, 14.4f);
		Parts.seam(m, b, -21, 6.5f, 14.4f);
		Parts.sideStrip(m, b, Mat.RUBBER, 9.0f, 9.8f, -h + 6, h - 6, 0.25f);
		Parts.markers(m, b, 10);
		m.rimTile = 30;
		if (police) {
			policeKit(m, b, -6, true);
			Parts.sideStrip(m, b, Mat.LIVERY_DOOR, 10.0f, 14.2f, -5, 13, 0.12f);
			Parts.sideStrip(m, b, Mat.LIVERY_BAND, 10.0f, 14.2f, -20, -6.5f, 0.12f);
		}
	}

	/** Police two-tone: lower doors in the livery accent colour (VCPD blue band or sheriff white). */
	private static void livery(Body b) {
		b.toneY = 9.6f;
		b.toneY2 = 5.2f;
		b.lower2 = Mat.ACCENT;
	}

	private static void policeKit(VehicleModel m, Body b, float barZ, boolean pushBar) {
		Parts.lightBar(m, barZ, b.roofY, b.hw(barZ) - b.gi - b.ri - 1);
		if (pushBar) Parts.pushBar(m, b, b.clear + 1.5f, b.top(b.hl - 4) - 0.5f);
		Parts.spotlight(m, b);
		Parts.antenna(m, 3, b.roofY, b.zR2 + 2, 6);
		Parts.flashers(m, b, b.top(b.hl - 2) - 3.5f, 6);
		// rear window flashers
		float z = b.zC + 1.2f;
		m.box(Mat.SIREN_RED, 0.5f, b.top(z) + 0.6f, z, 5, b.top(z) + 1.4f, z + 1);
		m.box(Mat.SIREN_BLUE, -5, b.top(z) + 0.6f, z, -0.5f, b.top(z) + 1.4f, z + 1);
	}

	/** Shared full-size pickup: crew cab, bed with walls and tailgate. */
	private static Body pickupBody(VehicleSpec s, float clear, float wheelR, float flare, float belt, float hood) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = clear;
		b.wheelR = wheelR;
		b.wheelW = 5;
		b.flare = flare;
		b.archGap = 1.5f;
		float cabBack = -12.5f;
		b.top = k(-h, belt - 6.5f, cabBack - 0.5f, belt - 6.5f, cabBack, belt, 18, belt, 36, hood, h - 1.5f, hood - 0.6f, h, hood - 1.8f);
		b.bot = k(-h, clear + 2.5f, -h + 3, clear, h - 4, clear, h, clear + 3);
		b.zA = 18; b.zR1 = 9.5f; b.zR2 = cabBack + 1.5f; b.zC = cabBack + 0.4f;
		b.bPillar = 1.5f;
		b.sideRear = cabBack + 2.5f;
		b.pillar = Mat.BLACK;
		b.ri = 1.2f;
		b.cornerFront = 1.2f;
		b.cornerRear = 0.6f;
		return b;
	}

	private static void bed(VehicleModel m, Body b, float belt, Mat wallMat) {
		float h = b.hl, cabBack = -12.5f;
		float floor = belt - 6.5f;
		m.box(Mat.BLACK, -b.hw(-20) + 1.2f, floor, -h + 1.4f, b.hw(-20) - 1.2f, floor + 0.3f, cabBack - 0.6f);
		for (float z = -h; z < cabBack - 1e-3f; z += 2) {
			float za = Math.min(cabBack - 0.5f, z + 2), w = b.hw((z + za) / 2);
			float a = b.arch((z + za) / 2);
			m.pair(wallMat, w - 1.3f, Math.max(floor, a > 0 ? a : floor), z, w, belt, za);
		}
		m.box(wallMat, -b.hw(cabBack) + 1.3f, floor, cabBack - 1.5f, b.hw(cabBack) - 1.3f, belt, cabBack - 0.4f);
		// tailgate with handle
		float zt = b.rearZ(b.hw(-h)) ;
		m.box(wallMat, -b.hw(-h) + 1.3f, floor - 3, zt, b.hw(-h) - 1.3f, belt, zt + 1.4f);
		m.box(Mat.BLACK, -1.8f, belt - 2.4f, zt - 0.2f, 1.8f, belt - 1.6f, zt);
		m.pair(Mat.TRIM, b.hw(-h) - 1.3f, belt, -h + 1, b.hw(-h), belt + 0.4f, cabBack - 0.5f);
	}

	static void caracara(VehicleModel m, VehicleSpec s) {
		float belt = 19.4f, hood = 19.0f;
		Body b = pickupBody(s, 7, 7.6f, 2.2f, belt, hood);
		b.build(m);
		bed(m, b, belt, Mat.PAINT);
		// big grille with lettering bar, LED C-clamp lamps, steel bumper with skid plate and tow hooks
		Parts.frontC(m, b, Mat.GRILLE, 11.5f, 11.6f, 17.6f, 0.2f);
		Parts.frontC(m, b, Mat.PAINT_DARK, 7.5f, 14.2f, 15.4f, 0.4f);
		Parts.headlights(m, b, Mat.LED_HEAD, 11.6f, 17.2f, 15.0f, 17.4f);
		Parts.front(m, b, Mat.DRL, 11.8f, 17.0f, 14.0f, 14.6f, 0.35f);
		Parts.front(m, b, Mat.DRL, 16.4f, 17.0f, 14.0f, 17.4f, 0.35f);
		Parts.bumperF(m, b, Mat.TRIM, 7.6f, 11.6f, 1.0f);
		Parts.frontC(m, b, Mat.METAL, 9, 6.0f, 7.6f, 1.6f);
		Parts.front(m, b, Mat.ORANGE, 7.2f, 8.6f, 8.2f, 9.6f, 1.6f);
		// vertical tail lamps on the bed corners
		Parts.rear(m, b, Mat.TAIL_PLAIN, b.hw(-b.hl) - 1.4f, b.hw(-b.hl) + 0.1f, 12.5f, belt - 0.4f, 0.3f);
		Parts.bumperR(m, b, Mat.TRIM, 7.6f, 11.0f, 1.0f);
		Parts.plates(m, b, 0, 8.4f);
		// raised power-dome hood with vents, roof lamps, running boards, fender vents
		Parts.onTop(m, b, Mat.PAINT, -6, 6, 20, b.hl - 6, 0, 1.2f);
		Parts.onTop(m, b, Mat.VENT, -9, -7, 22, 30, 0, 0.2f);
		Parts.onTop(m, b, Mat.VENT, 7, 9, 22, 30, 0, 0.2f);
		for (float x = -4.5f; x <= 4.6f; x += 3) m.box(Mat.ORANGE_LENS, x - 0.6f, b.roofY, b.zR1 - 1.2f, x + 0.6f, b.roofY + 0.5f, b.zR1);
		Parts.sideStrip(m, b, Mat.RUBBER, b.clear, b.clear + 1, b.axleR + 9, b.axleF - 9, 1.8f);
		Parts.sideStrip(m, b, Mat.VENT, 14.5f, 16.5f, b.axleF + 7, b.axleF + 10, 0.15f);
		Parts.mirrors(m, b, Mat.BLACK, b.zA - 0.5f, belt + 0.6f);
		Parts.handles(m, b, Mat.BLACK, belt - 2.4f, 7, -10);
		Parts.seam(m, b, 17.5f, 8f, belt);
		Parts.seam(m, b, 1.5f, 8f, belt);
		Parts.seam(m, b, -12.5f, 8f, belt);
		Parts.markers(m, b, 14);
		m.rimTile = 28;
	}

	static void bison(VehicleModel m, VehicleSpec s, boolean police) {
		float belt = 18.6f, hood = 18.4f;
		Body b = pickupBody(s, 6.2f, 7.0f, 0.4f, belt, hood);
		if (police) { b.toneY = 13.0f; b.toneY2 = 7.4f; b.lower2 = Mat.ACCENT; }
		b.build(m);
		bed(m, b, belt, Mat.PAINT);
		// crosshair chrome grille, stacked lamps
		Parts.frontC(m, b, police ? Mat.GRILLE : Mat.CHROME_GRILLE, 11.6f, 10.6f, 17.2f, 0.2f);
		Parts.frontC(m, b, police ? Mat.BLACK : Mat.CHROME, 11.6f, 13.6f, 14.4f, 0.4f);
		Parts.frontC(m, b, police ? Mat.BLACK : Mat.CHROME, 0.6f, 10.6f, 17.2f, 0.4f);
		Parts.headlights(m, b, Mat.HEAD_PLAIN, 11.8f, 16.4f, 13.8f, 17.0f);
		Parts.front(m, b, Mat.ORANGE_LENS, 11.8f, 16.4f, 12.6f, 13.6f, 0.25f);
		Parts.bumperF(m, b, police ? Mat.BLACK : Mat.CHROME, 6.8f, 10.6f, 1.0f);
		Parts.rear(m, b, Mat.TAIL_PLAIN, b.hw(-b.hl) - 1.4f, b.hw(-b.hl) + 0.1f, 12.0f, belt - 0.4f, 0.3f);
		Parts.bumperR(m, b, police ? Mat.BLACK : Mat.CHROME, 6.8f, 10.0f, 1.0f);
		Parts.plates(m, b, 7.2f, 7.4f);
		Parts.sideStrip(m, b, Mat.CHROME, b.clear, b.clear + 0.8f, b.axleR + 9, b.axleF - 9, 1.6f);
		Parts.mirrors(m, b, Mat.CHROME, b.zA - 0.5f, belt + 0.6f);
		Parts.handles(m, b, Mat.CHROME, belt - 2.4f, 7, -10);
		Parts.seam(m, b, 17.5f, 7.5f, belt);
		Parts.seam(m, b, 1.5f, 7.5f, belt);
		Parts.seam(m, b, -12.5f, 7.5f, belt);
		Parts.markers(m, b, 13.5f);
		m.rimTile = police ? 30 : 27;
		if (police) {
			policeKit(m, b, 0, true);
			Parts.sideStrip(m, b, Mat.LIVERY_DOOR, 13.2f, belt - 0.4f, 2.5f, 17, 0.12f);
			// bed cage and equipment box
			m.box(Mat.BLACK, -b.hw(-30) + 1.4f, belt, -24, b.hw(-30) - 1.4f, belt + 4, -14);
		}
	}

	/** Large SUV body (Granger / police SUV). */
	private static Body suvBody(VehicleSpec s, float belt, float hood, float zA, float zR1, float zR2, float zC, float clear, float wheelR) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = clear;
		b.wheelR = wheelR;
		b.wheelW = 4.8f;
		b.top = k(-h, belt - 0.8f, -h + 1.2f, belt, zA, belt, zA + 14, hood, h - 1.5f, hood - 0.8f, h, hood - 2);
		b.bot = k(-h, clear + 2, -h + 3, clear, h - 4, clear, h, clear + 2.5f);
		b.zA = zA; b.zR1 = zR1; b.zR2 = zR2; b.zC = zC;
		b.pillar = Mat.BLACK;
		b.rearSideGlass = Mat.GLASS_DARK;
		b.ri = 1.2f;
		b.cornerFront = 1.4f;
		b.cornerRear = 1.0f;
		return b;
	}

	static void granger(VehicleModel m, VehicleSpec s, boolean taxi) {
		float belt = 18.6f;
		Body b = suvBody(s, belt, 18.0f, 18, 11, -41, -42.6f, 6.4f, 6.8f);
		b.bPillar = 2;
		b.cSplit = -16;
		b.sideRear = -38;
		b.build(m);
		Parts.frontC(m, b, Mat.CHROME_GRILLE, 10, 11.0f, 16.6f, 0.2f);
		Parts.frontC(m, b, Mat.CHROME, 10.4f, 13.4f, 14.2f, 0.4f);
		Parts.headlights(m, b, Mat.HEAD_PLAIN, 10.4f, 16.0f, 13.4f, 16.4f);
		Parts.front(m, b, Mat.ORANGE_LENS, 10.4f, 16.0f, 12.2f, 13.2f, 0.25f);
		Parts.bumperF(m, b, Mat.TRIM, 6.6f, 11.0f, 1.0f);
		Parts.rear(m, b, Mat.TAIL_PLAIN, b.hw(-b.hl) - 2.4f, b.hw(-b.hl) - 0.2f, 13.0f, 22.5f, 0.3f);
		Parts.bumperR(m, b, Mat.TRIM, 6.6f, 10.2f, 1.0f);
		Parts.plates(m, b, 7.6f, 14.4f);
		Parts.rearC(m, b, Mat.CHROME, 4, 17.0f, 17.6f, 0.2f);
		float gx = b.hw(0) - b.gi - b.ri;
		m.pair(Mat.TRIM, gx - 1.4f, b.roofY, -38, gx - 0.6f, b.roofY + 1.2f, 8);
		Parts.sideStrip(m, b, Mat.TRIM, b.clear + 0.4f, b.clear + 1.2f, b.axleR + 9, b.axleF - 9, 1.6f);
		Parts.mirrors(m, b, Mat.BLACK, b.zA - 0.5f, belt + 0.6f);
		Parts.handles(m, b, Mat.CHROME, belt - 2.4f, 8, -12);
		Parts.seam(m, b, 17.5f, 7f, belt);
		Parts.seam(m, b, 2, 7f, belt);
		Parts.seam(m, b, -16, 7.5f, belt);
		Parts.markers(m, b, 12.8f);
		m.rimTile = taxi ? 30 : 27;
		if (taxi) {
			float z = -14;
			m.box(Mat.TAXI_SIGN, -5, b.roofY, z - 2, 5, b.roofY + 3.4f, z + 2);
			m.box(Mat.BLACK, -5.5f, b.roofY, z - 2.5f, 5.5f, b.roofY + 0.4f, z + 2.5f);
			Parts.sideStrip(m, b, Mat.TAXI_STRIP, 12.0f, 15.0f, -40, 16, 0.12f);
		}
	}

	static void policeSuv(VehicleModel m, VehicleSpec s) {
		float belt = 17.8f;
		Body b = suvBody(s, belt, 17.2f, 14, 7, -34, -38.5f, 5.8f, 6.4f);
		b.bPillar = -4;
		b.cSplit = -21;
		b.sideRear = -32;
		livery(b);
		b.toneY = 12.0f;
		b.toneY2 = 6.8f;
		b.build(m);
		Parts.frontC(m, b, Mat.GRILLE, 9, 10.4f, 15.6f, 0.2f);
		Parts.headlights(m, b, Mat.LED_HEAD, 9.4f, 15.6f, 13.6f, 15.6f);
		Parts.bumperF(m, b, Mat.BLACK, 6.0f, 10.4f, 1.0f);
		Parts.rear(m, b, Mat.TAIL_PLAIN, 9, b.hw(-b.hl) - 0.4f, 17.0f, 19.4f, 0.3f);
		Parts.bumperR(m, b, Mat.BLACK, 6.0f, 9.6f, 1.0f);
		Parts.plates(m, b, 7, 13.6f);
		Parts.mirrors(m, b, Mat.BLACK, b.zA - 0.5f, belt + 0.6f);
		Parts.handles(m, b, Mat.BLACK, belt - 2.4f, 4, -14);
		Parts.seam(m, b, 13.5f, 7f, belt);
		Parts.seam(m, b, -4, 7f, belt);
		Parts.seam(m, b, -21, 7.5f, belt);
		Parts.sideStrip(m, b, Mat.LIVERY_DOOR, 12.4f, belt - 0.4f, -3, 13, 0.12f);
		Parts.sideStrip(m, b, Mat.LIVERY_BAND, 12.4f, belt - 0.4f, -36, -21.5f, 0.12f);
		policeKit(m, b, -10, true);
		m.rimTile = 30;
	}

	static void dubsta(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 6.4f;
		b.wheelR = 7;
		b.wheelW = 4.8f;
		b.flare = 0.6f;
		b.top = k(-h, 17.4f, -h + 0.8f, 18.0f, 10, 18.0f, 34, 17.6f, h - 0.6f, 17.2f, h, 16.4f);
		b.bot = k(-h, 8.6f, -h + 3, b.clear, h - 3, b.clear, h, 9);
		b.zA = 10; b.zR1 = 7; b.zR2 = -36.2f; b.zC = -36.8f;
		b.bPillar = -5;
		b.cSplit = -20;
		b.sideRear = -34.5f;
		b.gi = 1.0f;
		b.ri = 0.6f;
		b.crown = 0.1f;
		b.cornerFront = 0.6f;
		b.cornerRear = 0.6f;
		b.rearSideGlass = Mat.GLASS_DARK;
		b.build(m);
		// round lamps, fender-top indicators, slatted grille, bumper with tow eyes
		Parts.headlights(m, b, Mat.HEADLIGHT, 10.4f, 15.0f, 12.2f, 16.6f);
		Parts.onTop(m, b, Mat.ORANGE_LENS, -15.2f, -13.0f, h - 4, h - 2, 0, 1.2f);
		Parts.onTop(m, b, Mat.ORANGE_LENS, 13.0f, 15.2f, h - 4, h - 2, 0, 1.2f);
		Parts.frontC(m, b, Mat.BLACK, 8.6f, 11.6f, 16.8f, 0.15f);
		Parts.frontC(m, b, Mat.CHROME_GRILLE, 8.0f, 12.0f, 16.4f, 0.3f);
		Parts.bumperF(m, b, Mat.BLACK, 6.8f, 11.2f, 1.2f);
		Parts.rear(m, b, Mat.TAIL_PLAIN, 13.0f, 15.6f, 10.0f, 14.6f, 0.3f);
		Parts.bumperR(m, b, Mat.BLACK, 6.8f, 10.0f, 1.2f);
		// spare wheel on the side-hinged tailgate
		float zr = b.rearZ(8) - 0.4f;
		m.box(Mat.TIRE, -5.4f, 13.0f, zr - 3.4f, 5.4f, 23.8f, zr - 0.2f);
		m.box(Mat.PAINT, -4.6f, 13.8f, zr - 3.6f, 4.6f, 23.0f, zr - 3.2f);
		m.box(Mat.CHROME, -1.4f, 17.0f, zr - 3.8f, 1.4f, 19.8f, zr - 3.5f);
		Parts.plates(m, b, 7.6f, 11.2f);
		// external door hinges, roof rack rails, rub strip
		Parts.handles(m, b, Mat.CHROME, 15.6f, 6.5f, -10);
		for (float z : new float[] {9, -4.5f}) m.pair(Mat.BLACK, b.hw(z) - 0.1f, 11, z - 1, b.hw(z) + 0.4f, 16.5f, z);
		float gx = b.hw(0) - b.gi - b.ri;
		m.pair(Mat.BLACK, gx - 1.4f, b.roofY, -34, gx - 0.4f, b.roofY + 1.6f, 5);
		Parts.sideStrip(m, b, Mat.RUBBER, 10.4f, 11.6f, -h + 2, h - 2, 0.35f);
		Parts.mirrors(m, b, Mat.PAINT, b.zA - 0.5f, 18.6f);
		Parts.seam(m, b, 9.5f, 8f, 18);
		Parts.seam(m, b, -5, 8f, 18);
		Parts.seam(m, b, -20, 8.5f, 18);
		m.rimTile = 27;
	}

	static void speedo(VehicleModel m, VehicleSpec s) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 5.6f;
		b.wheelR = 6.4f;
		b.wheelW = 4.6f;
		b.top = k(-h, 17.6f, -h + 0.8f, 18.2f, 26, 18.2f, 36, 17.4f, h - 1.6f, 16.0f, h, 14.4f);
		b.zA = 26; b.zR1 = 16; b.zR2 = -h + 0.8f; b.zC = -h + 0.6f;
		b.bPillar = 13;
		b.sideRear = 12;
		b.glass = Mat.GLASS_DARK;
		b.crown = 0.8f;
		b.cornerFront = 2;
		b.cornerRear = 0.8f;
		b.ri = 0.8f;
		b.build(m);
		Parts.frontC(m, b, Mat.CHROME_GRILLE, 8, 10.6f, 14.6f, 0.2f);
		Parts.headlights(m, b, Mat.HEAD_PLAIN, 8.4f, 14.6f, 11.4f, 14.4f);
		Parts.front(m, b, Mat.ORANGE_LENS, 14.6f, 16.0f, 11.4f, 14.4f, 0.2f);
		Parts.bumperF(m, b, Mat.TRIM, 5.4f, 9.8f, 1.0f);
		// rear barn doors with windows, vertical tail lamps
		Parts.rearC(m, b, Mat.TRIM, 0.15f, 7.0f, 30.0f, 0.2f);
		Parts.rear(m, b, Mat.GLASS_DARK, 1.2f, 12.4f, 21.0f, 29.0f, 0.15f);
		Parts.rear(m, b, Mat.TAIL_PLAIN, 14.4f, 16.6f, 13.4f, 21.0f, 0.3f);
		Parts.bumperR(m, b, Mat.TRIM, 5.4f, 8.8f, 1.0f);
		Parts.plates(m, b, 6.4f, 9.4f);
		// sliding side door track and seams, roof marker lamps
		Parts.seam(m, b, 12.6f, 7f, 30);
		Parts.seam(m, b, -8, 7f, 30);
		float hw = b.hw(0);
		m.pair(Mat.TRIM, hw - 0.1f, 29.0f, -9, hw + 0.15f, 29.4f, 12);
		for (float x = -6; x <= 6.1f; x += 6) m.box(Mat.ORANGE_LENS, x - 0.6f, b.roofY, b.zR1 - 0.8f, x + 0.6f, b.roofY + 0.5f, b.zR1);
		Parts.mirrors(m, b, Mat.BLACK, b.zA - 0.5f, 18.8f);
		Parts.handles(m, b, Mat.BLACK, 16.0f, 22, 9);
		Parts.markers(m, b, 12);
		m.rimTile = 30;
	}

	/** Armoured cash truck (Gruppe6 Stockade) and the SWAT Police Riot share the body. */
	static void armoured(VehicleModel m, VehicleSpec s, boolean riot) {
		Body b = new Body(s);
		float h = b.hl;
		b.clear = 7;
		b.wheelR = 8.4f;
		b.wheelW = 5.4f;
		b.top = k(-h, 15.0f, 28, 15.0f, 38, 14.2f, h - 1.4f, 13.2f, h, 12.0f);
		b.bot = k(-h, b.clear + 2, -h + 4, b.clear, h - 4, b.clear, h, b.clear + 3);
		b.zA = 30; b.zR1 = 23; b.zR2 = 13.5f; b.zC = 13;
		b.sideRear = 15;
		b.glass = Mat.GLASS_DARK;
		b.cornerFront = 1.6f;
		b.cornerRear = 0.4f;
		b.ri = 0.6f;
		b.roofY = 32;
		if (riot) {
			b.lower = Mat.BLACK;
			b.upper = Mat.BLACK;
			b.roofMat = Mat.BLACK;
			b.pillar = Mat.BLACK;
		}
		b.build(m);
		Mat body = riot ? Mat.BLACK : Mat.PAINT;
		float hw = b.hw(0) - 0.4f, boxTop = s.height() * 16;
		// cargo box with rivets, gun ports and the side door
		m.box(body, -hw, 15, -h + 0.4f, hw, boxTop, 12.6f);
		m.box(Mat.METAL, -hw - 0.2f, boxTop - 0.6f, -h + 0.2f, hw + 0.2f, boxTop, 12.8f);
		for (float z = -h + 4; z < 12; z += 6) m.pair(Mat.METAL, hw, 15.6f, z, hw + 0.2f, boxTop - 1, z + 0.4f);
		m.pair(riot ? Mat.SWAT : Mat.G6, hw + 0.05f, 22, -34, hw + 0.25f, 34, -10);
		m.pair(Mat.BLACK, hw - 0.1f, 28.5f, 3, hw + 0.2f, 30.5f, 5);
		m.pair(Mat.TRIM, hw, 16, 6, hw + 0.15f, 36, 6.3f);
		m.pair(Mat.TRIM, hw, 16, 11.4f, hw + 0.15f, 36, 11.7f);
		// rear doors and steps
		float zr = -h + 0.4f;
		m.box(Mat.TRIM, -0.2f, 16, zr - 0.2f, 0.2f, boxTop - 2, zr);
		m.box(Mat.CHROME, -3, 24, zr - 0.5f, -1, 25, zr);
		m.box(Mat.CHROME, 1, 24, zr - 0.5f, 3, 25, zr);
		m.box(Mat.METAL, -hw + 2, 8, zr - 3, hw - 2, 9, zr);
		m.pair(Mat.TAIL_PLAIN, hw - 2.6f, 12, zr - 0.4f, hw - 0.6f, 16, zr);
		m.pair(Mat.ORANGE_LENS, hw - 2.4f, boxTop - 2.4f, zr - 0.4f, hw - 1.2f, boxTop - 1.4f, zr);
		// cab front: grille, lamps, heavy bumper
		Parts.frontC(m, b, riot ? Mat.GRILLE : Mat.CHROME_GRILLE, 9, 9.2f, 13.2f, 0.2f);
		Parts.headlights(m, b, Mat.HEAD_PLAIN, 10, 15.6f, 10.4f, 12.8f);
		Parts.bumperF(m, b, riot ? Mat.BLACK : Mat.METAL, 6.6f, 9.6f, 1.6f);
		Parts.plates(m, b, 7, 10);
		for (float x = -9; x <= 9.1f; x += 3) m.box(Mat.ORANGE_LENS, x - 0.6f, b.roofY, b.zR1 - 1, x + 0.6f, b.roofY + 0.5f, b.zR1);
		Parts.mirrors(m, b, Mat.BLACK, b.zA - 0.5f, 18);
		m.rimTile = 30;
		if (riot) {
			m.pair(Mat.LIVERY_BAND, hw + 0.05f, 17, -40, hw + 0.25f, 21, 12);
			m.pair(Mat.LIVERY_DOOR, b.hw(20) + 0.05f, 15.4f, 15, b.hw(20) + 0.25f, 22, 27);
			Parts.lightBar(m, 20, b.roofY, 7);
			m.box(Mat.BLACK, -4, boxTop, -20, 4, boxTop + 1.6f, -12);
			Parts.pushBar(m, b, b.clear + 1, 14);
			Parts.flashers(m, b, 13.6f, 7);
		}
	}

	static void police(VehicleModel m, VehicleSpec s) {
		switch (s.id()) {
			case "police_cruiser" -> stanier(m, s, true);
			case "police_suv" -> policeSuv(m, s);
			case "buffalo_police" -> buffalo(m, s, true);
			case "bison_police" -> bison(m, s, true);
			case "police_riot" -> armoured(m, s, true);
			default -> stanier(m, s, true);
		}
	}
}
