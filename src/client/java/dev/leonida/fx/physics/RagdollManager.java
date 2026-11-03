package dev.leonida.fx.physics;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.leonida.fx.FxConfig;
import dev.leonida.fx.FxModule;
import dev.leonida.fx.mixin.LivingEntityRendererInvoker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Keeps the active ragdolls, creates them from entities and hides the entities they replace. */
public final class RagdollManager {
	/** One ragdolled body. Either a humanoid {@link Ragdoll} or a single rigid {@link Piece} for other models. */
	public static final class Body {
		public final EntityModel<?> model;
		public final RenderType renderType;
		public final Ragdoll ragdoll;
		public final Piece rigid;
		public final Vector3d rigidPos0;
		public final ModelPart[] parts;
		public final Matrix4f[] restMatrices;
		public final Matrix4f baseMatrix;
		public final java.lang.ref.WeakReference<Entity> entity;
		public final int life;
		public int age;

		Body(EntityModel<?> model, RenderType renderType, Ragdoll ragdoll, Piece rigid, ModelPart[] parts, Matrix4f[] rest, Matrix4f base,
				Entity entity, int life) {
			this.model = model;
			this.renderType = renderType;
			this.ragdoll = ragdoll;
			this.rigid = rigid;
			this.rigidPos0 = rigid == null ? null : new Vector3d(rigid.pos);
			this.parts = parts;
			this.restMatrices = rest;
			this.baseMatrix = base;
			this.entity = new java.lang.ref.WeakReference<>(entity);
			this.life = life;
		}

		/** Sinks into the ground at the end of its life. */
		public float sink(float partial) {
			float left = life - (age + partial);
			return left >= 20 ? 0.0F : (20 - left) / 20.0F * 0.7F;
		}
	}

	public static final int DEAD_LIFE = 20 * 14;
	private final List<Body> bodies = new ArrayList<>();
	private final DebrisWorld rigidWorld = new DebrisWorld();
	private final Set<Entity> hidden = Collections.newSetFromMap(new WeakHashMap<>());
	private final Random random = new Random();

	public List<Body> bodies() {
		return bodies;
	}

	public boolean isHidden(Entity entity) {
		return !hidden.isEmpty() && hidden.contains(entity);
	}

	public void clear() {
		bodies.clear();
		rigidWorld.clear();
		hidden.clear();
	}

	public void tick(Level level) {
		rigidWorld.tick(level);
		for (int i = bodies.size() - 1; i >= 0; i--) {
			Body b = bodies.get(i);
			b.age++;
			if (b.ragdoll != null) b.ragdoll.tick(level);
			Entity e = b.entity.get();
			boolean alive = e != null && !e.isRemoved() && e instanceof LivingEntity le && !le.isDeadOrDying();
			if (b.age >= b.life || (b.rigid != null && !rigidWorld.pieces().contains(b.rigid))) {
				bodies.remove(i);
				if (e != null) hidden.remove(e);
				continue;
			}
			// a temporary ragdoll of a living entity ends when its time is up; respawned players show again
			if (e != null && b.life != DEAD_LIFE && b.age >= b.life - 1) hidden.remove(e);
			if (e != null && b.life == DEAD_LIFE && alive) hidden.remove(e);
		}
	}

	/** Builds a ragdoll for the entity as it looks now. Returns false if its renderer is not a living model. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public boolean spawn(LivingEntity entity, Vec3 impulse, int ticks) {
		Minecraft mc = Minecraft.getInstance();
		EntityRenderer<?, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
		if (!(renderer instanceof LivingEntityRenderer lr)) return false;
		// replace an existing ragdoll of the same entity
		for (int i = bodies.size() - 1; i >= 0; i--) {
			if (bodies.get(i).entity.get() == entity) bodies.remove(i);
		}
		int max = FxConfig.get().quality.maxRagdolls;
		while (bodies.size() >= max) {
			Body old = bodies.remove(0);
			Entity oe = old.entity.get();
			if (oe != null) hidden.remove(oe);
		}
		float partial = 1.0F;
		LivingEntityRenderState state;
		try {
			state = (LivingEntityRenderState) lr.createRenderState(entity, partial);
		} catch (RuntimeException e) {
			FxModule.LOG.debug("No render state for ragdoll of {}", entity, e);
			return false;
		}
		EntityModel model = lr.getModel();
		Identifier texture = lr.getTextureLocation(state);
		RenderType renderType = model.renderType(texture);

		// same base transform LivingEntityRenderer uses, without the death roll
		PoseStack ps = new PoseStack();
		Vec3 p = entity.position();
		ps.translate(p.x, p.y, p.z);
		float scale = state.scale;
		ps.scale(scale, scale, scale);
		ps.rotateDegrees(com.mojang.math.Axis.YP, 180.0F - state.bodyRot);
		ps.scale(-1.0F, -1.0F, 1.0F);
		try {
			((LivingEntityRendererInvoker) lr).leonida$fx$scale(state, ps);
		} catch (RuntimeException ignored) {
			// some renderers need state we do not have; their extra scale is cosmetic
		}
		ps.translate(0.0F, -1.501F, 0.0F);
		Matrix4f base = new Matrix4f(ps.last().pose());

		Vec3 v = entity.getDeltaMovement().scale(20.0).add(impulse);
		int life = ticks > 0 ? ticks : DEAD_LIFE;
		Body body;
		if (model instanceof HumanoidModel<?> hm) {
			ModelPart[] parts = {hm.head, hm.body, hm.rightArm, hm.leftArm, hm.rightLeg, hm.leftLeg};
			Matrix4f[] rest = new Matrix4f[6];
			for (int i = 0; i < 6; i++) {
				PartPose ip = parts[i].getInitialPose();
				rest[i] = new Matrix4f(base).translate(ip.x() / 16.0F, ip.y() / 16.0F, ip.z() / 16.0F).rotateZYX(ip.zRot(), ip.yRot(), ip.xRot());
			}
			Vector3d[] pts = new Vector3d[Ragdoll.N];
			pts[Ragdoll.SR] = at(rest[2], 0, 0, 0);
			pts[Ragdoll.SL] = at(rest[3], 0, 0, 0);
			pts[Ragdoll.HR] = at(rest[4], 0, 0, 0);
			pts[Ragdoll.HL] = at(rest[5], 0, 0, 0);
			pts[Ragdoll.HEAD] = at(rest[0], 0, -8, 0);
			pts[Ragdoll.HAND_R] = at(rest[2], 0, 10, 0);
			pts[Ragdoll.HAND_L] = at(rest[3], 0, 10, 0);
			pts[Ragdoll.FOOT_R] = at(rest[4], 0, 12, 0);
			pts[Ragdoll.FOOT_L] = at(rest[5], 0, 12, 0);
			Vector3d neck = at(rest[0], 0, 0, 0);
			// lift slightly so feet do not start inside the ground
			for (Vector3d q : pts) q.y += 0.02;
			neck.y += 0.02;
			Ragdoll rd = new Ragdoll(pts, neck, scale * entity.getBbHeight() / 1.8F, life);
			double bias = 0.35 + random.nextDouble() * 0.3;
			rd.addVelocity(v.x, v.y, v.z, bias, random);
			// fall over: push the chest along the facing direction or the impulse
			Vec3 tip = impulse.lengthSqr() > 0.01 ? new Vec3(impulse.x, 0, impulse.z) : Vec3.directionFromRotation(0, state.bodyRot).scale(-1);
			if (tip.lengthSqr() < 1.0E-4) tip = new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
			tip = tip.normalize().scale(1.2);
			rd.addVelocity(tip.x, 0, tip.z, 1.0, random);
			body = new Body(model, renderType, rd, null, parts, rest, base, entity, life);
		} else {
			float hw = Math.max(0.15F, entity.getBbWidth() * 0.45F), hh = Math.max(0.15F, entity.getBbHeight() * 0.48F);
			Piece rigid = new Piece(hw, hh, hw, null, 0, 0, 0, 0, -1, false, 0.1F, 0.7F, life);
			rigid.pos.set(p.x, p.y + hh + 0.01, p.z);
			rigid.prevPos.set(rigid.pos);
			rigid.vel.set(v.x, v.y, v.z);
			Vec3 tip = impulse.lengthSqr() > 0.01 ? impulse : new Vec3(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
			Vector3f axis = new Vector3f((float) tip.z, 0, (float) -tip.x);
			if (axis.lengthSquared() < 1.0E-6F) axis.set(1, 0, 0);
			axis.normalize(2.5F + random.nextFloat() * 2.0F);
			rigid.angVel.set(axis);
			rigidWorld.add(rigid, 64);
			body = new Body(model, renderType, null, rigid, null, null, base, entity, life);
		}
		bodies.add(body);
		hidden.add(entity);
		return true;
	}

	private static Vector3d at(Matrix4f m, float px, float py, float pz) {
		Vector3f v = m.transformPosition(px / 16.0F, py / 16.0F, pz / 16.0F, new Vector3f());
		return new Vector3d(v.x, v.y, v.z);
	}
}
