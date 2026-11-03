package dev.leonida.world.gen;

/** Deterministic position hashes. */
public final class Hash {
	private Hash() {}

	public static long mix(long h) {
		h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33; h *= 0xc4ceb9fe1a85ec53L; h ^= h >>> 33;
		return h;
	}

	/** Uniform in [0, 1) from block x, z and a salt. */
	public static double h01(int x, int z, int salt) {
		long h = mix(((long) x * 0x9E3779B97F4A7C15L) ^ ((long) z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L));
		return (h >>> 11) * 0x1.0p-53;
	}
}
