package dev.leonida.fx.physics;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** One debris rigid body: an oriented box with a block texture region. */
public final class Piece {
	// shape
	public final float hx, hy, hz;
	public final float invMass;
	public final Vector3f invInertiaBody = new Vector3f();
	// state
	public final Vector3d pos = new Vector3d();
	public final Vector3d prevPos = new Vector3d();
	public final Vector3d vel = new Vector3d();
	public final Quaternionf rot = new Quaternionf();
	public final Quaternionf prevRot = new Quaternionf();
	public final Vector3f angVel = new Vector3f();
	public int age;
	public int life;
	public int restTicks;
	public boolean sleeping;
	public int contacts;
	// look
	public final TextureAtlasSprite sprite;
	public final float u0, v0, u1, v1;
	public final int color;
	public final boolean translucent;
	public final float restitution;
	public final float friction;

	// scratch
	final Matrix3f rotM = new Matrix3f();

	public Piece(float hx, float hy, float hz, TextureAtlasSprite sprite, float u0, float v0, float u1, float v1,
			int color, boolean translucent, float restitution, float friction, int life) {
		this.hx = hx;
		this.hy = hy;
		this.hz = hz;
		float mass = 8.0F * hx * hy * hz * 4.0F + 0.02F;
		this.invMass = 1.0F / mass;
		float ix = mass / 3.0F * (hy * hy + hz * hz);
		float iy = mass / 3.0F * (hx * hx + hz * hz);
		float iz = mass / 3.0F * (hx * hx + hy * hy);
		this.invInertiaBody.set(1.0F / ix, 1.0F / iy, 1.0F / iz);
		this.sprite = sprite;
		this.u0 = u0;
		this.v0 = v0;
		this.u1 = u1;
		this.v1 = v1;
		this.color = color;
		this.translucent = translucent;
		this.restitution = restitution;
		this.friction = friction;
		this.life = life;
	}

	/** Radius of the sphere used for piece-piece contacts. */
	public float contactRadius() {
		return Math.max(0.03F, Math.min(hx, Math.min(hy, hz)) * 0.5F + (hx + hy + hz) / 3.0F * 0.5F) * 0.9F;
	}

	/** Size multiplier used when fading out at the end of life. */
	public float scale(float partial) {
		float left = life - (age + partial);
		return left >= 20 ? 1.0F : Math.max(0.0F, left / 20.0F);
	}
}
