package dev.leonida.weapon;

import com.mojang.blaze3d.platform.InputConstants;
import dev.leonida.LeonidaMod;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Client-only setup for the weapon module: input, recoil, aiming, crosshair, effects, toasts. */
public final class WeaponClient {
	private WeaponClient() {}

	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(LeonidaMod.id("weapon"));
	public static KeyMapping keyReload;
	public static KeyMapping keyWheel;

	// state read by mixins and the HUD
	public static float aim;          // 0..1 aim down sights progress (smoothed per frame)
	public static float kick;         // 0..1 visual recoil, decays per frame
	public static boolean aiming;
	private static long lastFrameNanos;
	private static int clientTicks;
	private static int lastFireTick = -1000;
	private static boolean wasAttackDown;
	private static int hitMarkerTick = -1000;
	private static boolean hitMarkerKill;
	private static boolean targetIsLiving;
	/** Set by tests to force aiming without a mouse button. */
	public static boolean forceAim;

	// pickup toast
	private static final Set<Item> knownGuns = new HashSet<>();
	private static boolean inventorySeen;
	private static ItemStack toastStack = ItemStack.EMPTY;
	private static int toastStart = -1000;

	public static void init() {
		keyReload = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leonida.weapon.reload", InputConstants.Type.KEYBOARD, InputConstants.KEY_R, CATEGORY));
		keyWheel = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leonida.weapon.wheel", InputConstants.Type.KEYBOARD, InputConstants.KEY_TAB, CATEGORY));

		EntityRendererRegistry.register(WeaponItems.ROCKET, ctx -> new ThrownItemRenderer<>(ctx, 1.6f, true));
		EntityRendererRegistry.register(dev.leonida.weapon.shop.WeaponShop.PICKUP, PickupRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(WeaponNet.OpenShop.TYPE, (p, ctx) -> ctx.client().gui.setScreen(new GunShopScreen(p.pos())));
		ClientPlayNetworking.registerGlobalReceiver(WeaponNet.ShopResult.TYPE, (p, ctx) -> {
			if (ctx.client().gui.screen() instanceof GunShopScreen s) s.result(p.index(), p.result());
		});

		// guns never punch or break blocks: left mouse fires
		ClientPreAttackCallback.EVENT.register((mc, player, clicks) -> heldGun(player) != null);

		ClientTickEvents.END_CLIENT_TICK.register(WeaponClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(WeaponNet.ShotFx.TYPE, (p, ctx) -> shotFx(ctx.client(), p));
		ClientPlayNetworking.registerGlobalReceiver(WeaponNet.HitConfirm.TYPE, (p, ctx) -> {
			hitMarkerTick = clientTicks;
			hitMarkerKill = p.kill();
		});
		ClientPlayNetworking.registerGlobalReceiver(WeaponNet.Blast.TYPE, (p, ctx) -> {
			if (ctx.client().player == null) return;
			double d = Math.sqrt(ctx.client().player.distanceToSqr(p.pos()));
			float k = Mth.clamp(1f - (float) (d / (p.power() * 14f)), 0f, 1f);
			shake = Math.max(shake, k);
			blastFlash = Math.max(blastFlash, k * k);
		});

		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, old -> (g, dt) -> {
			if (!crosshair(g, dt)) old.extractRenderState(g, dt);
		});
		HudElementRegistry.addLast(LeonidaMod.id("weapon/overlay"), WeaponClient::overlay);
	}

	public static GunType heldGun(LocalPlayer p) {
		return p == null ? null : WeaponItems.gunOf(p.getMainHandItem());
	}

	/** Target zoom factor applied to the field of view (1 = none). */
	public static float fovFactor() {
		LocalPlayer p = Minecraft.getInstance().player;
		GunType t = heldGun(p);
		if (t == null || t.scoped() && scoped()) return 1f;
		return aiming ? t.zoom : 1f;
	}

	/** True while the sniper scope is up (drives vanilla scoping: overlay, sensitivity, hidden hand). */
	public static boolean scoped() {
		Minecraft mc = Minecraft.getInstance();
		GunType t = heldGun(mc.player);
		return t != null && t.scoped() && aiming && aim > 0.6f && mc.options.getCameraType().isFirstPerson();
	}

	private static void tick(Minecraft mc) {
		clientTicks++;
		LocalPlayer p = mc.player;
		if (p == null) {
			inventorySeen = false;
			knownGuns.clear();
			return;
		}
		boolean inWorld = mc.gui.screen() == null;
		GunType t = heldGun(p);
		ItemStack held = p.getMainHandItem();
		aiming = t != null && inWorld && (mc.options.keyUse.isDown() || forceAim) && GunItem.reloadEnd(held) == 0;
		if (aiming && p.isSprinting()) p.setSprinting(false);

		boolean attackDown = inWorld && mc.options.keyAttack.isDown();
		if (t != null && attackDown && (t.automatic || !wasAttackDown) && clientTicks - lastFireTick >= t.fireDelay
				&& GunItem.reloadEnd(held) == 0) {
			fire(p, t, held);
		}
		wasAttackDown = attackDown;

		while (keyReload.consumeClick()) {
			if (t != null) ClientPlayNetworking.send(new WeaponNet.Reload());
		}
		while (keyWheel.consumeClick()) {
			if (inWorld) mc.gui.setScreen(new WeaponWheelScreen());
		}

		// blast camera shake: a zero mean jitter that is undone on the next tick
		p.setYRot(p.getYRot() - shakeYaw);
		p.setXRot(p.getXRot() - shakePitch);
		if (shake > 0.01f) {
			float amp = shake * shake * 3.5f;
			shakeYaw = (p.getRandom().nextFloat() - 0.5f) * amp;
			shakePitch = (p.getRandom().nextFloat() - 0.5f) * amp;
			shake *= 0.86f;
		} else {
			shake = 0f;
			shakeYaw = shakePitch = 0f;
		}
		p.setYRot(p.getYRot() + shakeYaw);
		p.setXRot(Mth.clamp(p.getXRot() + shakePitch, -90f, 90f));
		blastFlash = blastFlash < 0.02f ? 0f : blastFlash * 0.72f;

		trackPickups(p);
		targetIsLiving = t != null && lookingAtLiving(p, t);
	}

	/**
	 * Per gun recoil feel. vert scales the gun's pitch kick, horiz is random sideways jitter, bias a
	 * steady drift to the right, recover the share of the climb that settles back once you stop firing,
	 * kickScale the first person push, shake a little camera punch for the big guns.
	 */
	public record Recoil(float vert, float horiz, float bias, float recover, float kickScale, float shake) {}

	public static Recoil recoil(GunType t) {
		return switch (t) {
			case PISTOL -> new Recoil(1.0f, 0.35f, 0.0f, 0.75f, 0.85f, 0.0f);
			case SMG -> new Recoil(0.9f, 0.95f, 0.08f, 0.55f, 0.6f, 0.0f);
			case RIFLE -> new Recoil(1.0f, 0.45f, 0.22f, 0.5f, 0.8f, 0.0f);
			case SHOTGUN -> new Recoil(1.0f, 0.3f, 0.0f, 0.8f, 1.5f, 0.32f);
			case SNIPER -> new Recoil(1.0f, 0.15f, 0.0f, 0.8f, 1.6f, 0.36f);
			case RPG -> new Recoil(1.0f, 0.2f, 0.0f, 0.85f, 1.3f, 0.55f);
		};
	}

	private static float pendingPitch, pendingYaw, recoilDebt;
	private static int burst;

	/** Sends one shot to the server and plays the local recoil. Also used by tests. */
	public static void fire(LocalPlayer p, GunType t, ItemStack held) {
		boolean chained = clientTicks - lastFireTick <= t.fireDelay + 2;
		burst = chained ? burst + 1 : 0;
		lastFireTick = clientTicks;
		ClientPlayNetworking.send(new WeaponNet.Fire(aiming));
		if (GunItem.clip(held) <= 0) return;
		Recoil rc = recoil(t);
		float ramp = t.automatic ? 1f + Math.min(burst, 10) * 0.05f : 1f;
		float r = t.recoil * (aiming ? 0.6f : 1f) * (p.isCrouching() ? 0.75f : 1f);
		float up = r * rc.vert() * ramp;
		pendingPitch += up;
		pendingYaw += ((p.getRandom().nextFloat() - 0.5f) * rc.horiz() + rc.bias()) * r;
		recoilDebt += up * rc.recover();
		kick = rc.kickScale();
		if (rc.shake() > 0) shake = Math.max(shake, rc.shake() * (aiming ? 0.7f : 1f));
	}

	/** Applies recoil smoothly over a few frames and settles part of it back after the burst. */
	private static void applyRecoil(float dt) {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) { pendingPitch = pendingYaw = recoilDebt = 0; return; }
		if (pendingPitch != 0 || pendingYaw != 0) {
			float a = 1f - (float) Math.exp(-dt * 30f);
			float dp = pendingPitch * a, dy = pendingYaw * a;
			if (Math.abs(pendingPitch) < 0.01f && Math.abs(pendingYaw) < 0.01f) { dp = pendingPitch; dy = pendingYaw; }
			pendingPitch -= dp;
			pendingYaw -= dy;
			p.turn(dy / 0.15, -dp / 0.15);
		}
		if (recoilDebt > 0.005f && clientTicks - lastFireTick > 3 && pendingPitch < 0.05f) {
			float rec = recoilDebt * (1f - (float) Math.exp(-dt * 7f));
			recoilDebt -= rec;
			p.turn(0, rec / 0.15);
		} else if (recoilDebt <= 0.005f) {
			recoilDebt = 0;
		}
		if (heldGun(p) == null) recoilDebt = 0;
	}

	private static boolean lookingAtLiving(LocalPlayer p, GunType t) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(t.range));
		HitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bh.getType() != HitResult.Type.MISS) end = bh.getLocation();
		EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity && !e.isSpectator() && e.isPickable(), eye.distanceToSqr(end));
		return eh != null;
	}

	private static final java.util.Map<Item, Integer> ammoSeen = new java.util.HashMap<>();

	private static void trackPickups(LocalPlayer p) {
		Set<Item> now = new HashSet<>();
		java.util.Map<Item, Integer> ammo = new java.util.HashMap<>();
		ItemStack fresh = ItemStack.EMPTY;
		for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
			if (s.getItem() instanceof GunItem || s.is(WeaponItems.BAT) || s.is(WeaponItems.KNIFE)) {
				now.add(s.getItem());
				if (!knownGuns.contains(s.getItem())) fresh = s;
			} else if (WeaponItems.AMMO.containsValue(s.getItem())) {
				ammo.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		if (inventorySeen && !fresh.isEmpty()) showToast(fresh);
		else if (inventorySeen) {
			// ammo picked up or bought: "+24 PISTOL AMMO"
			for (var e : ammo.entrySet()) {
				int gained = e.getValue() - ammoSeen.getOrDefault(e.getKey(), 0);
				if (gained > 0) { showToast(new ItemStack(e.getKey(), gained)); toastPrefix = "+" + gained + " "; break; }
			}
		}
		knownGuns.clear();
		knownGuns.addAll(now);
		ammoSeen.clear();
		ammoSeen.putAll(ammo);
		inventorySeen = true;
	}

	private static String toastPrefix = "";

	public static void showToast(ItemStack s) {
		toastStack = s.copy();
		toastPrefix = "";
		toastStart = clientTicks;
	}

	// ---- per-frame state ----

	public static void frame() {
		long t = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (t - lastFrameNanos) / 1e9f);
		lastFrameNanos = t;
		float target = aiming ? 1f : 0f;
		aim += (target - aim) * Math.min(1f, dt * 12f);
		if (Math.abs(target - aim) < 0.002f) aim = target;
		kick = Math.max(0f, kick - dt * 7f * Math.max(1f, kick));
		if (dt > 0) applyRecoil(dt);
	}

	/** Called from the first person renderer after the vanilla item arm transform. */
	public static void applyGunPose(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.world.entity.HumanoidArm arm) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		GunType t = heldGun(p);
		if (t == null || arm != p.getMainArm()) return;
		frame();
		int invert = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1 : -1;
		float a = aim * aim * (3 - 2 * aim);
		pose.translate(-invert * adsX(t) * a, adsY(t) * a, ADS_Z * a);
		if (kick > 0) {
			float k = Math.min(kick, 1f) * Math.min(kick, 1f) * Math.max(1f, kick);
			pose.translate(0f, 0.02f * k, 0.09f * k);
			pose.rotateDegrees(com.mojang.math.Axis.XP, 6f * k);
		}
		long end = GunItem.reloadEnd(p.getMainHandItem());
		if (end > 0 && mc.level != null) {
			float left = (end - mc.level.getGameTime() - mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / t.reloadTicks;
			float prog = Mth.clamp(1f - left, 0f, 1f);
			float dip = Mth.sin(prog * Mth.PI);
			pose.translate(0f, -0.12f * dip, 0.03f * dip);
			pose.rotateDegrees(com.mojang.math.Axis.XP, -22f * dip);
			pose.rotateDegrees(com.mojang.math.Axis.ZP, invert * 20f * dip);
		}
	}

	public static float ADS_X = 0.56f, ADS_Y = 0.13f, ADS_Z = 0.10f;

	/** Per gun sight alignment so the front sight sits on the crosshair when aiming. */
	static float adsX(GunType t) {
		return switch (t) {
			case PISTOL -> ADS_X - 0.045f;
			default -> ADS_X - 0.085f;
		};
	}

	static float adsY(GunType t) {
		return switch (t) {
			case PISTOL -> ADS_Y + 0.07f;
			default -> ADS_Y;
		};
	}

	// ---- HUD ----

	private static boolean crosshair(GuiGraphicsExtractor g, DeltaTracker dt) {
		frame();
		Minecraft mc = Minecraft.getInstance();
		GunType t = heldGun(mc.player);
		if (t == null || !mc.options.getCameraType().isFirstPerson() || mc.gui.screen() instanceof WeaponWheelScreen) return t != null;
		int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
		if (clientTicks - flashTick < 2 && !scoped()) drawFlash(g, mc, cx, cy);
		boolean marker = clientTicks - hitMarkerTick < 6;
		if (scoped()) {
			// fine reticle over the vanilla scope overlay
			g.fill(cx - 60, cy, cx - 3, cy + 1, 0xFF000000);
			g.fill(cx + 3, cy, cx + 61, cy + 1, 0xFF000000);
			g.fill(cx, cy + 3, cx + 1, cy + 61, 0xFF000000);
			g.fill(cx, cy - 40, cx + 1, cy - 3, 0xFF000000);
			g.fill(cx, cy, cx + 1, cy + 1, targetIsLiving ? 0xFFE03030 : 0xFF000000);
		} else {
			int col = targetIsLiving ? 0xFFE8343A : 0xFFFFFFFF;
			g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xB0000000);
			g.fill(cx - 1, cy - 1, cx + 1, cy + 1, col);
		}
		if (marker) {
			int col = hitMarkerKill ? 0xFFE8343A : 0xFFFFFFFF;
			for (int i = 3; i <= 6; i++) {
				g.fill(cx - i - 1, cy - i - 1, cx - i, cy - i, col);
				g.fill(cx + i, cy - i - 1, cx + i + 1, cy - i, col);
				g.fill(cx - i - 1, cy + i, cx - i, cy + i + 1, col);
				g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, col);
			}
		}
		// reload progress under the crosshair
		ItemStack held = mc.player.getMainHandItem();
		long end = GunItem.reloadEnd(held);
		if (end > 0 && mc.level != null) {
			float left = (end - mc.level.getGameTime()) / (float) t.reloadTicks;
			float prog = Mth.clamp(1f - left, 0f, 1f);
			g.fill(cx - 16, cy + 10, cx + 16, cy + 13, 0x90000000);
			g.fill(cx - 15, cy + 11, cx - 15 + (int) (30 * prog), cy + 12, 0xFFFFFFFF);
		}
		return true;
	}

	private static int flashTick = -100;
	private static boolean flashBig;

	/** Draws the muzzle flash where the hip or aimed gun muzzle projects onto the screen. */
	private static void drawFlash(GuiGraphicsExtractor g, Minecraft mc, int cx, int cy) {
		float hip = 1f - aim;
		double fov = Math.toRadians(mc.options.fov().get() * fovFactor());
		double f = (g.guiHeight() / 2.0) / Math.tan(fov / 2);
		int x = cx + (int) (0.30 * hip / 1.1 * f);
		int y = cy + (int) ((0.22 * hip + 0.08 * aim) / 1.1 * f);
		int r = (flashBig ? 9 : 6) + (clientTicks % 2) * 2;
		int a = clientTicks - flashTick == 0 ? 0xE0 : 0x80;
		g.fill(x - r, y - 1, x + r, y + 2, (a << 24) | 0xFF9A2E);
		g.fill(x - 1, y - r, x + 2, y + r, (a << 24) | 0xFF9A2E);
		int d = r * 2 / 3;
		for (int i = 1; i < d; i++) {
			g.fill(x - i, y - i, x - i + 1, y - i + 1, (a << 24) | 0xFFC04A);
			g.fill(x + i, y - i, x + i + 1, y - i + 1, (a << 24) | 0xFFC04A);
			g.fill(x - i, y + i, x - i + 1, y + i + 1, (a << 24) | 0xFFC04A);
			g.fill(x + i, y + i, x + i + 1, y + i + 1, (a << 24) | 0xFFC04A);
		}
		int c = r / 2;
		g.fill(x - c, y - c, x + c + 1, y + c + 1, (a << 24) | 0xFFD45E);
		g.fill(x - c / 2, y - c / 2, x + c / 2 + 1, y + c / 2 + 1, (a << 24) | 0xFFFFF0);
	}

	private static float shake, shakeYaw, shakePitch, blastFlash;

	private static void overlay(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (blastFlash > 0f) {
			int a = Mth.clamp((int) (blastFlash * 150), 0, 150);
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0xFFC070);
		}
		if (mc.player == null || toastStack.isEmpty()) return;
		int age = clientTicks - toastStart;
		if (age > 70) return;
		float a = age < 5 ? age / 5f : age > 55 ? (70 - age) / 15f : 1f;
		int alpha = Mth.clamp((int) (a * 255), 8, 255);
		String name = toastPrefix + toastStack.getHoverName().getString().toUpperCase(Locale.ROOT);
		// Extended Look style: icon plus bold white capitals, centred low on the screen, no panel
		int w = g.guiWidth(), h = g.guiHeight();
		int textW = mc.font.width(name) + 1;
		int total = 22 + textW;
		int x = w / 2 - total / 2, y = (int) (h * 0.72f);
		g.fill(x - 10, y - 3, x + total + 10, y + 19, (int) (a * 0x48) << 24);
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.item(toastStack, 0, 0);
		g.pose().popMatrix();
		int col = (alpha << 24) | 0xFFFFFF;
		Component c = Component.literal(name);
		g.text(mc.font, c, x + 22, y + 5, col, true);
		g.text(mc.font, c, x + 23, y + 5, col, false);
	}

	// ---- shot effects ----

	private static void shotFx(Minecraft mc, WeaponNet.ShotFx fx) {
		if (mc.level == null || mc.player == null) return;
		GunType t = GunType.values()[Mth.clamp(fx.gun(), 0, GunType.values().length - 1)];
		Vec3 muzzle = fx.muzzle();
		boolean local = fx.shooterId() == mc.player.getId();
		if (local && mc.options.getCameraType().isFirstPerson()) {
			Vec3 cam = mc.gameRenderer.mainCamera().position();
			Vec3 fwd = new Vec3(mc.gameRenderer.mainCamera().forwardVector());
			Vec3 up = new Vec3(mc.gameRenderer.mainCamera().upVector());
			Vec3 left = new Vec3(mc.gameRenderer.mainCamera().leftVector());
			float hip = 1f - aim;
			muzzle = cam.add(fwd.scale(1.1)).add(left.scale(-0.30 * hip)).add(up.scale(-0.22 * hip - 0.08 * aim));
		}
		var level = mc.level;
		var rnd = level.getRandom();
		// muzzle flash: a short screen-space burst for our own first person shots (world particles linger too long
		// and drift away from the gun when the camera recoils), world particles for everyone else
		boolean fpLocal = local && mc.options.getCameraType().isFirstPerson();
		if (fpLocal) {
			flashTick = clientTicks;
			flashBig = t == GunType.SHOTGUN || t == GunType.RPG;
		}
		for (int i = 0; i < (fpLocal ? 0 : 4); i++) {
			level.addParticle(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z,
					(rnd.nextFloat() - 0.5f) * 0.02, (rnd.nextFloat() - 0.5f) * 0.02, (rnd.nextFloat() - 0.5f) * 0.02);
		}
		if (!fpLocal) level.addParticle(new DustParticleOptions(0xFFD27A, t == GunType.SHOTGUN || t == GunType.RPG ? 1.6f : 0.9f), muzzle.x, muzzle.y, muzzle.z, 0, 0, 0);
		if (!fpLocal) level.addParticle(ParticleTypes.SMOKE, muzzle.x, muzzle.y + 0.02, muzzle.z, 0, 0.02, 0);
		if (t == GunType.RPG && !fpLocal) {
			for (int i = 0; i < 8; i++) level.addParticle(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, (rnd.nextFloat() - 0.5f) * 0.1, 0.02, (rnd.nextFloat() - 0.5f) * 0.1);
		}
		for (int i = 0; i < fx.ends().size(); i++) {
			Vec3 end = fx.ends().get(i);
			int kind = fx.kinds()[i];
			if (t.pellets == 1 || i % 3 == 0) tracer(level, muzzle, end);
			Vec3 dir = end.subtract(muzzle).normalize();
			if (kind == 1) {
				BlockPos bp = BlockPos.containing(end.add(dir.scale(0.05)));
				BlockState st = level.getBlockState(bp);
				if (!st.isAir()) {
					for (int k = 0; k < 6; k++) {
						level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), end.x, end.y, end.z,
								-dir.x * 0.1 + (rnd.nextFloat() - 0.5f) * 0.15, 0.1 + rnd.nextFloat() * 0.1, -dir.z * 0.1 + (rnd.nextFloat() - 0.5f) * 0.15);
					}
				}
				Vec3 back = end.subtract(dir.scale(0.08));
				level.addParticle(ParticleTypes.SMOKE, back.x, back.y, back.z, 0, 0.01, 0);
				level.addParticle(ParticleTypes.CRIT, back.x, back.y, back.z, -dir.x * 0.2, 0.05, -dir.z * 0.2);
			} else if (kind == 2) {
				for (int k = 0; k < 7; k++) {
					level.addParticle(new DustParticleOptions(0x8C0A0A, 1.1f), end.x + (rnd.nextFloat() - 0.5f) * 0.2,
							end.y + (rnd.nextFloat() - 0.5f) * 0.2, end.z + (rnd.nextFloat() - 0.5f) * 0.2, 0, 0, 0);
				}
			}
		}
	}

	private static void tracer(net.minecraft.client.multiplayer.ClientLevel level, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 1.5) return;
		Vec3 n = d.scale(1 / len);
		DustParticleOptions dust = new DustParticleOptions(0xFFE7A8, 0.35f);
		for (double s = 1.2; s < len && s < 80; s += 0.6) {
			level.addParticle(dust, from.x + n.x * s, from.y + n.y * s, from.z + n.z * s, 0, 0, 0);
		}
	}

	public static Entity localPlayer() {
		return Minecraft.getInstance().player;
	}
}
