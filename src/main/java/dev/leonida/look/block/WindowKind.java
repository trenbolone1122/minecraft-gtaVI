package dev.leonida.look.block;

/** What sits behind a window, which decides how often and in what colour it lights up at night. */
public enum WindowKind {
	/** Homes: mostly cool white and pale blue, some TV blue, a few warm. About a quarter lit. */
	HOME(0.24f),
	/** Cool white offices, whole floors, about 30% lit. */
	OFFICE(0.30f),
	/** Shopfronts, mostly cool white, under half lit. */
	SHOP(0.45f),
	/** Warehouses and clerestories, a few lit. */
	INDUSTRIAL(0.15f);

	/** Fraction of AUTO windows that light up at full night. */
	public final float litFraction;

	WindowKind(float litFraction) {
		this.litFraction = litFraction;
	}
}
