package dev.leonida.look;

import net.minecraft.world.level.material.MapColor;

/** Colour helpers: LOOK.md palettes are linear RGB; blocks, textures and map colours want sRGB. */
public final class LookColor {
	private LookColor() {}

	/** Linear 0..1 to sRGB 0..1. */
	public static double toSrgb(double c) {
		c = Math.max(0, Math.min(1, c));
		return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
	}

	/** Linear RGB triple to packed 0xRRGGBB sRGB. */
	public static int srgb(double r, double g, double b) {
		return (int) Math.round(toSrgb(r) * 255) << 16 | (int) Math.round(toSrgb(g) * 255) << 8 | (int) Math.round(toSrgb(b) * 255);
	}

	private static final MapColor[] MAP = {MapColor.SAND, MapColor.WOOL, MapColor.METAL, MapColor.SNOW, MapColor.CLAY,
			MapColor.DIRT, MapColor.STONE, MapColor.WOOD, MapColor.QUARTZ, MapColor.COLOR_ORANGE, MapColor.COLOR_MAGENTA,
			MapColor.COLOR_LIGHT_BLUE, MapColor.COLOR_YELLOW, MapColor.COLOR_LIGHT_GREEN, MapColor.COLOR_PINK, MapColor.COLOR_GRAY,
			MapColor.COLOR_LIGHT_GRAY, MapColor.COLOR_CYAN, MapColor.COLOR_PURPLE, MapColor.COLOR_BLUE, MapColor.COLOR_BROWN,
			MapColor.COLOR_GREEN, MapColor.COLOR_RED, MapColor.COLOR_BLACK, MapColor.GOLD, MapColor.DIAMOND, MapColor.LAPIS,
			MapColor.EMERALD, MapColor.TERRACOTTA_WHITE, MapColor.TERRACOTTA_ORANGE, MapColor.TERRACOTTA_PINK,
			MapColor.TERRACOTTA_LIGHT_GRAY, MapColor.TERRACOTTA_CYAN, MapColor.TERRACOTTA_BROWN, MapColor.TERRACOTTA_YELLOW,
			MapColor.TERRACOTTA_LIGHT_BLUE, MapColor.TERRACOTTA_GRAY, MapColor.DEEPSLATE, MapColor.WARPED_WART_BLOCK};

	/** Nearest vanilla map colour to a packed sRGB colour (so the minimap shows pink hotels pink). */
	public static MapColor map(int rgb) {
		MapColor best = MapColor.STONE;
		long bestD = Long.MAX_VALUE;
		for (MapColor m : MAP) {
			int c = m.col;
			long dr = ((c >> 16) & 255) - ((rgb >> 16) & 255), dg = ((c >> 8) & 255) - ((rgb >> 8) & 255), db = (c & 255) - (rgb & 255);
			long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}
}
