package dev.leonida.aircraft;

import com.mojang.blaze3d.platform.InputConstants;
import dev.leonida.LeonidaMod;
import dev.leonida.aircraft.client.EngineSound;
import dev.leonida.aircraft.client.bb.BBModelLoader;
import dev.leonida.aircraft.client.render.AircraftRenderer;
import dev.leonida.aircraft.client.render.ParachuteRenderer;
import dev.leonida.aircraft.entity.AircraftEntity;
import dev.leonida.aircraft.entity.ParachuteEntity;
import dev.leonida.aircraft.net.ParachuteDeployPayload;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Client-only setup for the aircraft module. Owned by the aircraft agent. */
public final class AircraftClient {
	private AircraftClient() {}

	public static KeyMapping equipmentKey;
	private static final Map<Entity, EngineSound> SOUNDS = new WeakHashMap<>();
	/** Local skydiving state. Public so tests can check it. */
	public static boolean freefall;
	private static boolean jumpWasDown;
	private static int hintTicks;

	public static void init() {
		for (var type : AircraftModule.TYPES.values()) {
			EntityRendererRegistry.register(type, AircraftRenderer::new);
		}
		EntityRendererRegistry.register(AircraftModule.PARACHUTE, ParachuteRenderer::new);
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(LeonidaMod.id("aircraft/bbmodels"), new BBModelLoader());

		equipmentKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leonida.aircraft.equipment",
				InputConstants.Type.KEYBOARD, InputConstants.KEY_G, new KeyMapping.Category(LeonidaMod.id("aircraft"))));

		AircraftHooks.pilotKeys = () -> {
			var o = Minecraft.getInstance().options;
			return new AircraftHooks.PilotKeys(o.keyUp.isDown(), o.keyDown.isDown(), o.keyLeft.isDown(), o.keyRight.isDown(),
					o.keyJump.isDown(), o.keySprint.isDown());
		};
		AircraftHooks.freefalling = p -> freefall && p == Minecraft.getInstance().player;

		ClientTickEvents.END_CLIENT_TICK.register(AircraftClient::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			freefall = false;
			return;
		}
		// engine sounds for aircraft near the player
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof AircraftEntity a && a.getEnginePower() > 0.02f && e.distanceToSqr(p) < 160 * 160) {
				EngineSound s = SOUNDS.get(a);
				if (s == null || s.isStopped()) {
					s = new EngineSound(a, a.spec.isHelicopter() ? AircraftModule.SOUND_ROTOR : AircraftModule.SOUND_PROPELLER);
					SOUNDS.put(a, s);
					mc.getSoundManager().play(s);
				}
			}
		}

		// pilot equipment toggle (smoke for the crop duster, searchlight for the police helicopter)
		while (equipmentKey.consumeClick()) {
			if (p.getVehicle() instanceof AircraftEntity a && a.getControllingPassenger() == p) {
				if (a.spec.smokeTrail) a.setSmoke(!a.isSmokeOn());
				if (a.spec.searchlight) a.setSearchlight(!a.isSearchlightOn());
				a.sendControls();
			}
		}

		boolean jumpDown = mc.options.keyJump.isDown();
		boolean jumpClicked = false;
		while (mc.options.keyJump.consumeClick()) jumpClicked = true;
		boolean jumpPressed = (jumpDown && !jumpWasDown) || (jumpClicked && freefall);
		jumpWasDown = jumpDown;

		if (p.getVehicle() instanceof ParachuteEntity) {
			freefall = false;
			if (hintTicks++ % 60 == 0) p.sendOverlayMessage(Component.translatable("message.leonida.aircraft.parachute"));
			return;
		}

		boolean grounded = p.onGround() || p.isPassenger() || p.isInWater() || p.isInLava() || p.getAbilities().flying || p.isFallFlying() || p.isSpectator();
		if (freefall && grounded) {
			freefall = false;
		} else if (!freefall && !grounded && p.getDeltaMovement().y < -0.5 && heightAboveGround(p, 12) >= 12) {
			freefall = true;
			hintTicks = 0;
		}
		if (!freefall) return;

		// skydive steering: look direction plus W glides you forward, terminal velocity is lower belly down
		Vec3 v = p.getDeltaMovement();
		Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
		double accel = mc.options.keyUp.isDown() ? 0.035 : 0.0;
		double hx = v.x + look.x * accel, hz = v.z + look.z * accel;
		double h = Math.sqrt(hx * hx + hz * hz);
		double maxH = 0.9;
		if (h > maxH) {
			hx *= maxH / h;
			hz *= maxH / h;
		}
		if (mc.options.keyDown.isDown()) {
			hx *= 0.95;
			hz *= 0.95;
		}
		p.setDeltaMovement(hx, Math.max(v.y, -1.6), hz);
		if (hintTicks++ % 40 == 0) p.sendOverlayMessage(Component.translatable("message.leonida.aircraft.freefall"));
		if (jumpPressed) {
			ClientPlayNetworking.send(new ParachuteDeployPayload());
		}
	}

	private static double heightAboveGround(Player p, int max) {
		Vec3 from = p.position();
		BlockHitResult hit = p.level().clip(new ClipContext(from, from.add(0, -max, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
		return hit.getType() == HitResult.Type.MISS ? max : from.y - hit.getLocation().y;
	}
}
