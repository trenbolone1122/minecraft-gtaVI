package dev.leonida.vicecity.site;

import dev.leonida.look.LookBlocks.Accent;
import dev.leonida.look.LookBlocks.Awning;
import dev.leonida.look.LookBlocks.Neon;
import dev.leonida.look.LookBlocks.Pastel;
import dev.leonida.vicecity.ViceLandmarks;
import dev.leonida.vicecity.site.DecoHotel.Sign;
import dev.leonida.vicecity.site.DecoHotel.Spec;
import java.util.List;

/**
 * The Ocean Drive (Shore Dr) hotel row, south to north. Positions are the GTADB landmark points; lot widths come from
 * the spacing to the neighbouring landmarks; storeys and colours follow the game photos (gamePhoto per id) and the real
 * buildings (realAddress). Game names are used where GTADB has them; real-only names get no sign.
 */
public final class OceanDrive {
	private OceanDrive() {}

	static final List<Spec> ROW = List.of(
		new Spec("L51", "Cordelia", "CORDELIA", 1918, 354, 20, 30, 7, Pastel.CREAM, Pastel.SKY, Accent.BLUE, Neon.BLUE, Awning.BLUE, Sign.TOWER, 6, false, false, true),
		new Spec("L50", "Seahorse", "SEAHORSE", 1920, 371, 15, 28, 4, Pastel.CREAM, Pastel.LAVENDER, Accent.TEAL, Neon.VIOLET, Awning.TEAL, Sign.PORCH, 4, false, false, true),
		new Spec("L49", "Atlantis Hotel", "ATLANTIS", 1922, 386, 12, 28, 4, Pastel.MINT, Pastel.CREAM, Accent.WHITE, Neon.CYAN, Awning.TEAL, Sign.BLADE, 5, false, true, true),
		new Spec("L48", "Bobby Hurricane's", "BOBBY HURRICANES", 1924, 398, 11, 26, 2, Pastel.CREAM, Pastel.PINK, Accent.CORAL, Neon.PINK, Awning.CORAL, Sign.NONE, 0, false, false, true),
		new Spec("L47", "Cortez Hotel", "CORTEZ", 1928, 412, 14, 28, 4, Pastel.CREAM, Pastel.CREAM, Accent.BLUE, Neon.BLUE, Awning.BLUE, Sign.BLADE, 8, false, false, true),
		new Spec("L46", "Boulevard Hotel", "", 1928, 428, 16, 28, 4, Pastel.BUTTER, Pastel.CREAM, Accent.TEAL, Neon.CYAN, Awning.TEAL, Sign.NONE, 3, true, false, true),
		new Spec("L45", "Seashell Hotel", "SEASHELL", 1931, 446, 17, 28, 4, Pastel.CREAM, Pastel.MINT, Accent.CORAL, Neon.PINK, Awning.PINK, Sign.TOWER, 9, false, true, true),
		new Spec("L43", "760 Ocean Dr", "", 1928, 495, 15, 26, 3, Pastel.CREAM, Pastel.PEACH, Accent.CORAL, Neon.PINK, Awning.CORAL, Sign.NONE, 2, false, false, true),
		new Spec("L44", "News Cafe", "", 1930, 510, 15, 26, 2, Pastel.CREAM, Pastel.CREAM, Accent.GOLD, Neon.GOLD, Awning.GOLD, Sign.NONE, 0, false, false, true),
		new Spec("L42", "Parsons Hotel", "PARSONS", 1932, 526, 13, 28, 4, Pastel.CREAM, Pastel.PINK, Accent.CORAL, Neon.PINK, Awning.PINK, Sign.BLADE, 5, false, false, true),
		new Spec("L41", "Hotel Neptune", "NEPTUNE", 1934, 540, 14, 28, 4, Pastel.CREAM, Pastel.SKY, Accent.TEAL, Neon.VIOLET, Awning.TEAL, Sign.TOWER, 9, true, true, true),
		new Spec("L40", "Boardwalk", "BOARDWALK", 1939, 564, 23, 30, 5, Pastel.CREAM, Pastel.CREAM, Accent.GOLD, Neon.GOLD, Awning.BLUE, Sign.BLADE, 12, true, false, true),
		new Spec("L39", "Seven Seas Seafood", "SEVEN SEAS", 1941, 589, 23, 30, 4, Pastel.BUTTER, Pastel.BUTTER, Accent.WHITE, Neon.CYAN, Awning.GOLD, Sign.PORCH, 3, false, false, true),
		new Spec("L36", "Ocean View Hotel", "OCEAN VIEW", 1960, 639, 36, 34, 4, Pastel.CREAM, Pastel.LAVENDER, Accent.WHITE, Neon.PINK, Awning.PINK, Sign.PORCH, 6, true, false, true),
		new Spec("L35", "Paramour Hotel", "PARAMOUR", 1963, 678, 16, 28, 3, Pastel.CREAM, Pastel.CREAM, Accent.CORAL, Neon.PINK, Awning.CORAL, Sign.BLADE, 9, false, false, true),
		new Spec("L34", "Cavalier Hotel", "", 1966, 694, 16, 28, 3, Pastel.CREAM, Pastel.MINT, Accent.TEAL, Neon.CYAN, Awning.TEAL, Sign.NONE, 4, false, false, true),
		new Spec("L33", "Leslie Hotel", "", 1971, 714, 19, 28, 4, Pastel.BUTTER, Pastel.CREAM, Accent.WHITE, Neon.GOLD, Awning.GOLD, Sign.NONE, 3, false, false, true),
		new Spec("L32", "Hotel Dixon", "HOTEL DIXON", 1974, 737, 22, 30, 9, Pastel.CREAM, Pastel.CREAM, Accent.WHITE, Neon.WHITE, Awning.PINK, Sign.BLADE, 4, false, false, true),
		new Spec("L28", "Winter Haven", "", 1996, 828, 18, 28, 4, Pastel.CREAM, Pastel.CREAM, Accent.TEAL, Neon.CYAN, Awning.TEAL, Sign.NONE, 6, false, true, true),
		new Spec("L27", "McAlpin Ocean Plaza", "", 2000, 847, 18, 28, 3, Pastel.CREAM, Pastel.MINT, Accent.CORAL, Neon.PINK, Awning.CORAL, Sign.NONE, 8, false, true, true),
		new Spec("L26", "The Tides South Beach", "", 2003, 874, 25, 30, 11, Pastel.CREAM, Pastel.CREAM, Accent.WHITE, Neon.WHITE, Awning.TEAL, Sign.NONE, 5, false, false, true),
		new Spec("L25", "Dominion Hotel", "DOMINION", 2008, 912, 36, 30, 4, Pastel.CREAM, Pastel.CREAM, Accent.TEAL, Neon.CYAN, Awning.TEAL, Sign.PORCH, 3, true, false, true)
	);

	public static void registerAll(ViceLandmarks reg) {
		for (Spec s : ROW) reg.add(new DecoHotel(s));
	}
}
