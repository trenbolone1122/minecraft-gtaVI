package dev.leonida.build;

import net.minecraft.world.level.block.state.BlockState;

/** Materials of one building, picked deterministically from its id. */
final class Style {
	BlockState wall, accent, trim, glass, roof, floor, inWall, neon, base;
	String pastel = "white";
	String wood = "oak";
	String sofa = "white";
	String bed = "white";
	String cabinet = "white_concrete";
	String rug = "light_gray";
	String doorName = "oak_door";
	boolean panes = true;

	static final String[] WOODS = {"oak", "spruce", "birch", "dark_oak", "jungle", "acacia", "mangrove", "cherry", "pale_oak"};
	static final String[] FABRIC = {"white", "light_gray", "gray", "cyan", "light_blue", "blue", "pink", "lime", "orange", "brown", "black", "green", "red", "yellow", "magenta", "purple"};

	static Style pastel(Site s) {
		Style st = new Style();
		st.pastel = s.pick(B.PASTELS);
		st.wall = B.c(st.pastel, "concrete");
		st.accent = B.c(s.pick(new String[] {"white", "cyan", "pink", "light_blue", "yellow", "lime", "magenta"}), "concrete");
		if (st.accent == st.wall) st.accent = B.of("white_concrete");
		st.trim = B.of(s.pick(new String[] {"smooth_quartz", "white_concrete", "calcite", "quartz_bricks"}));
		st.glass = B.of(s.chance(0.5) ? "light_blue_stained_glass_pane" : "glass_pane");
		st.roof = B.of("smooth_stone");
		st.neon = B.of(s.pick(B.NEONS));
		st.base = B.of("smooth_stone");
		common(s, st);
		return st;
	}

	static void common(Site s, Style st) {
		st.wood = s.pick(WOODS);
		st.floor = B.of(s.pick(new String[] {st.wood + "_planks", "smooth_quartz", "polished_diorite", "white_concrete", "birch_planks", "stripped_" + (st.wood.equals("bamboo") ? "oak" : st.wood) + "_wood", "light_gray_concrete", "smooth_sandstone"}));
		st.inWall = B.of(s.pick(new String[] {"white_concrete", "smooth_quartz", "calcite", "white_terracotta", "light_gray_concrete", "smooth_sandstone", "quartz_bricks"}));
		st.sofa = s.pick(FABRIC);
		st.bed = s.pick(FABRIC);
		st.rug = s.pick(FABRIC);
		st.cabinet = s.pick(new String[] {"white_concrete", "smooth_quartz", st.wood + "_planks", "green_terracotta", "light_blue_terracotta", "gray_concrete", "black_concrete", "cyan_terracotta", "polished_deepslate"});
		st.doorName = st.wood.equals("pale_oak") ? "pale_oak_door" : st.wood + "_door";
	}
}
