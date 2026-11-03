package dev.leonida.look.block;

import net.minecraft.util.StringRepresentable;

/**
 * Night glow mode of a {@link WindowBlock}. AUTO: the client lights the window at night from a per-window
 * position hash (towers sparkle). ON: always lit (lobbies, shops that never close). OFF: never lit (empty units).
 */
public enum Glow implements StringRepresentable {
	AUTO("auto"), ON("on"), OFF("off");

	private final String name;

	Glow(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
