package dev.leonida.aircraft.client.bb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Blockbench (.bbmodel) model: cubes, bones and textures. Adapted from Immersive Aircraft
 * (immersive_aircraft.resources.bbmodel, GPL-3.0, by Luke100000). Meshes and keyframe animations are left out:
 * Leonida aircraft animate bones in code.
 */
public final class BBModel {
	public final List<Texture> textures = new ArrayList<>();
	public final List<BBObject> root = new LinkedList<>();
	public final Map<String, BBObject> objects = new HashMap<>();
	public final Map<String, BBObject> objectsByName = new HashMap<>();
	public final boolean freeFormat;
	public final float textureWidth, textureHeight;
	public final Identifier id;

	public BBModel(JsonObject model, Identifier id) {
		this.id = id;
		JsonObject meta = model.getAsJsonObject("meta");
		this.freeFormat = meta != null && meta.has("model_format") && meta.get("model_format").getAsString().equals("free");
		JsonObject resolution = model.getAsJsonObject("resolution");
		this.textureWidth = resolution.get("width").getAsFloat();
		this.textureHeight = resolution.get("height").getAsFloat();
		model.getAsJsonArray("textures").forEach(e -> textures.add(new Texture(e.getAsJsonObject(), id)));
		model.getAsJsonArray("elements").forEach(e -> {
			JsonObject o = e.getAsJsonObject();
			if ("cube".equals(o.get("type").getAsString())) {
				Cube cube = new Cube(o, this);
				objects.put(cube.uuid, cube);
				objectsByName.put(cube.name, cube);
			}
		});
		model.getAsJsonArray("outliner").forEach(e -> {
			if (e.isJsonPrimitive()) {
				BBObject o = objects.get(e.getAsString());
				if (o != null) root.add(o);
			} else {
				Bone bone = new Bone(e.getAsJsonObject(), this);
				root.add(bone);
			}
		});
	}

	float texW(Texture t) {
		return freeFormat ? t.uvWidth : textureWidth;
	}

	float texH(Texture t) {
		return freeFormat ? t.uvHeight : textureHeight;
	}

	Texture texture(int i) {
		return i >= 0 && i < textures.size() ? textures.get(i) : Texture.MISSING;
	}

	// ---- json helpers ----
	static Vector3f vec(JsonObject o, String m) {
		JsonArray a = o.getAsJsonArray(m);
		return a == null ? new Vector3f() : new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
	}

	static int intOr(JsonObject o, String m, int d) {
		JsonElement e = o.get(m);
		return e instanceof JsonPrimitive p && p.isNumber() ? p.getAsInt() : d;
	}

	static boolean boolOr(JsonObject o, String m, boolean d) {
		JsonElement e = o.get(m);
		return e instanceof JsonPrimitive p && p.isBoolean() ? p.getAsBoolean() : d;
	}

	public static Quaternionf fromXYZ(Vector3f r) {
		return new Quaternionf().rotationZYX(r.z, r.y, r.x);
	}

	public static final class Texture {
		static final Texture MISSING = new Texture();
		public final int uvWidth, uvHeight;
		public final Identifier location;

		private Texture() {
			uvWidth = 16;
			uvHeight = 16;
			location = Identifier.withDefaultNamespace("missing");
		}

		Texture(JsonObject o, Identifier model) {
			uvWidth = intOr(o, "uv_width", 16);
			uvHeight = intOr(o, "uv_height", 16);
			String name = o.get("name").getAsString();
			location = name.contains(":") ? Identifier.parse(name)
					: Identifier.fromNamespaceAndPath(model.getNamespace(), "textures/entity/" + name);
		}
	}

	public static class BBObject {
		public final String uuid, name;
		public final Vector3f origin, rotation;
		public final boolean visible;

		BBObject(JsonObject o) {
			uuid = o.get("uuid").getAsString();
			name = o.has("name") ? o.get("name").getAsString() : uuid;
			origin = vec(o, "origin").mul(1f / 16f);
			rotation = vec(o, "rotation").mul((float) (Math.PI / 180.0));
			visible = boolOr(o, "visibility", true);
		}
	}

	public static final class Bone extends BBObject {
		public final List<BBObject> children = new LinkedList<>();

		Bone(JsonObject o, BBModel model) {
			super(o);
			model.objects.put(uuid, this);
			model.objectsByName.put(name, this);
			o.getAsJsonArray("children").forEach(c -> {
				if (c.isJsonObject()) {
					children.add(new Bone(c.getAsJsonObject(), model));
				} else {
					BBObject child = model.objects.get(c.getAsString());
					if (child != null) children.add(child);
				}
			});
		}
	}

	public static final class Vertex {
		public float x, y, z, nx, ny, nz, u, v;
	}

	public static final class Face {
		public final Vertex[] vertices;
		public Texture texture;

		Face(Vertex[] v) {
			vertices = v;
		}
	}

	public static final class Cube extends BBObject {
		private static final String[] SIDES = {"north", "east", "south", "west", "up", "down"};
		private static final float[][] NORMALS = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
		private static final int[][] ORDER = {{1, 4, 6, 3}, {0, 1, 3, 2}, {5, 0, 2, 7}, {4, 5, 7, 6}, {4, 1, 0, 5}, {7, 2, 3, 6}};
		public final List<Face> faces = new ArrayList<>();
		public final boolean cull;
		/** Leonida convention: cubes named "glass..." draw translucent, cubes named "lamp..." glow at full brightness. */
		public final boolean glass, lamp;

		Cube(JsonObject o, BBModel model) {
			super(o);
			glass = name.startsWith("glass");
			lamp = name.startsWith("lamp");
			float inflate = intOr(o, "inflate", 0);
			Vector3f from = vec(o, "from").sub(inflate, inflate, inflate).mul(1f / 16f).sub(origin);
			Vector3f to = vec(o, "to").add(inflate, inflate, inflate).mul(1f / 16f).sub(origin);
			Vector3f[] p = {
					new Vector3f(to.x, to.y, to.z), new Vector3f(to.x, to.y, from.z), new Vector3f(to.x, from.y, to.z),
					new Vector3f(to.x, from.y, from.z), new Vector3f(from.x, to.y, from.z), new Vector3f(from.x, to.y, to.z),
					new Vector3f(from.x, from.y, from.z), new Vector3f(from.x, from.y, to.z)};
			JsonObject facesJson = o.getAsJsonObject("faces");
			for (int i = 0; i < 6; i++) {
				JsonObject fj = facesJson == null ? null : facesJson.getAsJsonObject(SIDES[i]);
				if (fj == null || !fj.has("texture") || fj.get("texture").isJsonNull()) continue;
				Vertex[] vs = new Vertex[4];
				for (int j = 0; j < 4; j++) {
					Vertex v = new Vertex();
					Vector3f pos = p[ORDER[i][3 - j]];
					v.x = pos.x;
					v.y = pos.y;
					v.z = pos.z;
					v.nx = NORMALS[i][0];
					v.ny = NORMALS[i][1];
					v.nz = NORMALS[i][2];
					vs[j] = v;
				}
				Face f = new Face(vs);
				f.texture = model.texture(intOr(fj, "texture", 0));
				float[] uv = new float[4];
				Iterator<JsonElement> it = fj.getAsJsonArray("uv").iterator();
				for (int j = 0; j < 4; j++) uv[j] = it.next().getAsFloat();
				float tw = model.texW(f.texture), th = model.texH(f.texture);
				vs[0].u = uv[0] / tw;
				vs[0].v = uv[3] / th;
				vs[1].u = uv[2] / tw;
				vs[1].v = uv[3] / th;
				vs[2].u = uv[2] / tw;
				vs[2].v = uv[1] / th;
				vs[3].u = uv[0] / tw;
				vs[3].v = uv[1] / th;
				float ax = vs[1].x - vs[0].x, ay = vs[1].y - vs[0].y, az = vs[1].z - vs[0].z;
				float bx = vs[2].x - vs[0].x, by = vs[2].y - vs[0].y, bz = vs[2].z - vs[0].z;
				boolean degenerate = ax * by - ay * bx == 0 && ax * bz - az * bx == 0 && ay * bz - az * by == 0;
				if (!degenerate) faces.add(f);
			}
			float vol = Math.abs((to.x - from.x) * (to.y - from.y) * (to.z - from.z));
			cull = vol < 0.01f && faces.size() > 1;
		}
	}
}
