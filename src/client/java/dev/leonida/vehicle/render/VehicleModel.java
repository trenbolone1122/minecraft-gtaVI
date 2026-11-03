package dev.leonida.vehicle.render;

import java.util.ArrayList;
import java.util.List;

/**
 * A box model in pixels (16 px = 1 block). Origin at ground centre, x to the vehicle's right, y up, z forward.
 * Built procedurally by {@link VehicleModels}; drawn by {@link VehicleRenderer}.
 */
public final class VehicleModel {
	public static final int G_BODY = 0, G_DRIVER_WINDOW = 1, G_STEER = 2, G_PROP = 3, G_INTERIOR = 4;

	public static final class Box {
		public final Mat mat;
		public final float x0, y0, z0, x1, y1, z1;
		public int group = G_BODY;
		/** Optional rotation about the x axis (degrees) around the box's own centre, for slanted panels. */
		public float pitch;
		/** Optional rotation about the z axis (degrees) around the box's own centre. */
		public float roll;
		/** Faces to draw, bit f set for face f (0 -x, 1 +x, 2 -y, 3 +y, 4 -z, 5 +z). */
		public int faces = 63;
		/** Front door this box belongs to: -1 left, +1 right (model x), 0 none. Swings about the door hinge. */
		public int door;

		Box(Mat mat, float x0, float y0, float z0, float x1, float y1, float z1) {
			this.mat = mat;
			this.x0 = Math.min(x0, x1);
			this.y0 = Math.min(y0, y1);
			this.z0 = Math.min(z0, z1);
			this.x1 = Math.max(x0, x1);
			this.y1 = Math.max(y0, y1);
			this.z1 = Math.max(z0, z1);
		}

		public Box group(int g) { group = g; return this; }
		public Box pitch(float deg) { pitch = deg; return this; }
		public Box roll(float deg) { roll = deg; return this; }
		public Box faces(int mask) { faces = mask; return this; }
		public Box door(int side) { door = side; return this; }
	}

	/** A wheel: cylinder along x. */
	public record Wheel(float x, float y, float z, float radius, float width, boolean front, boolean steers) {}

	public final List<Box> boxes = new ArrayList<>();
	public final List<Wheel> wheels = new ArrayList<>();
	/** Steering pivot (z, px) for the G_STEER group (bike forks, handlebars). */
	public float steerPivotZ;
	/** Fork rake for bikes in degrees (steer axis tilted back). */
	public float steerRake;
	/** Airboat propeller hub (px). */
	public float propY, propZ;
	/** Where the NPC driver figure sits (px), and whether it is a bike-style rider. */
	public boolean bikeRider;
	/** Rim tile of the atlas for the wheel side faces, and whether tyres are whitewall (unused) . */
	public int rimTile = 2;
	/** Headlight beam origins (px): x, y, z triples. Empty means every HEADLIGHT box. */
	public final List<float[]> beams = new ArrayList<>();
	/** Hide the NPC driver figure (for vehicles whose rider would clip, e.g. kayak handled separately). */
	public boolean kayak;

	/** Front doors (px): z range (hinge at doorZ1, the front edge), hinge x (outer skin), and the region used to
	 * attach side trim to the door. doorZ1 <= doorZ0 means no opening doors. */
	public float doorZ0, doorZ1, doorHingeX, doorInnerX, doorY0, doorY1;

	public boolean hasDoors() { return doorZ1 > doorZ0; }

	/** Attaches side trim (handles, seams, livery, mirrors on the door) inside the door region to the door. */
	public void attachDoorTrim() {
		if (!hasDoors()) return;
		for (Box b : boxes) {
			if (b.door != 0 || b.group != G_BODY) continue;
			float cx = (b.x0 + b.x1) / 2;
			if (Math.abs(cx) >= doorInnerX && b.z0 >= doorZ0 - 0.01f && b.z1 <= doorZ1 + 0.01f && b.y0 >= doorY0 - 0.01f && b.y1 <= doorY1)
				b.door = cx < 0 ? -1 : 1;
		}
	}

	public void beam(float x, float y, float z) {
		beams.add(new float[] {x, y, z});
		beams.add(new float[] {-x, y, z});
	}

	public Box box(Mat m, float x0, float y0, float z0, float x1, float y1, float z1) {
		Box b = new Box(m, x0, y0, z0, x1, y1, z1);
		boxes.add(b);
		return b;
	}

	/** Box mirrored on both sides of the centre line: from x0 to x1 on the right and -x1 to -x0 on the left. */
	public void pair(Mat m, float x0, float y0, float z0, float x1, float y1, float z1) {
		box(m, x0, y0, z0, x1, y1, z1);
		box(m, -x1, y0, z0, -x0, y1, z1);
	}

	public Box centered(Mat m, float w, float y0, float z0, float y1, float z1) {
		return box(m, -w / 2, y0, z0, w / 2, y1, z1);
	}

	public void wheelPair(float halfTrack, float y, float z, float r, float w, boolean front, boolean steers) {
		wheels.add(new Wheel(halfTrack, y, z, r, w, front, steers));
		wheels.add(new Wheel(-halfTrack, y, z, r, w, front, steers));
	}
}
