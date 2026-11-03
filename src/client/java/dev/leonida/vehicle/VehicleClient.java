package dev.leonida.vehicle;

import com.mojang.blaze3d.platform.InputConstants;
import dev.leonida.LeonidaMod;
import dev.leonida.vehicle.net.VehiclePayloads;
import dev.leonida.vehicle.render.VehicleHud;
import dev.leonida.vehicle.render.VehicleRenderer;
import dev.leonida.vehicle.sound.VehicleLoopSound;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;

/** Client-only setup for the vehicle module: renderers, driving input, keys, looping sounds, HUD prompts. */
public final class VehicleClient {
	private VehicleClient() {}

	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(LeonidaMod.id("vehicle"));
	public static KeyMapping keyHorn, keyLights, keySiren, keyExit;

	private static final Map<Long, VehicleLoopSound> SOUNDS = new HashMap<>();
	private static CameraType cameraBeforeEnter;
	private static boolean wasDriving;
	private static int lastRidingId = -1;

	public static void init() {
		for (var type : VehicleTypes.TYPES.values()) {
			EntityRendererRegistry.register(type, VehicleRenderer::new);
		}
		keyHorn = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leonida.vehicle_horn", InputConstants.KEY_H, CATEGORY));
		keyLights = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leonida.vehicle_lights", InputConstants.KEY_L, CATEGORY));
		keySiren = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leonida.vehicle_siren", InputConstants.KEY_J, CATEGORY));
		keyExit = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leonida.vehicle_exit", InputConstants.KEY_F, CATEGORY));
		// F is also vanilla's swap-hands key: handle it before vanilla's keybinds so the click does not swap hands
		ClientTickEvents.START_CLIENT_TICK.register(VehicleClient::exitKey);

		VehicleEntity.clientInput = VehicleClient::readInput;
		VehicleEntity.clientBridge = new VehicleEntity.ClientBridge() {
			@Override
			public void sendControls(VehicleEntity v, int bits) {
				ClientPlayNetworking.send(new VehiclePayloads.Controls(bits));
			}

			@Override
			public void sendCrash(VehicleEntity v, float impact) {
				ClientPlayNetworking.send(new VehiclePayloads.Action(VehiclePayloads.Action.CRASH, impact));
			}
		};

		ClientTickEvents.END_CLIENT_TICK.register(VehicleClient::tick);
		HudElementRegistry.addLast(LeonidaMod.id("vehicle_hud"), VehicleHud::extract);
	}

	private static void exitKey(Minecraft mc) {
		if (mc.player == null) return;
		boolean any = false;
		while (keyExit.consumeClick()) any = true;
		if (!any) return;
		if (keyExit.same(mc.options.keySwapOffhand)) while (mc.options.keySwapOffhand.consumeClick()) {}
		ClientPlayNetworking.send(new VehiclePayloads.Action(VehiclePayloads.Action.EXIT_OR_ENTER, 0));
	}

	private static VehicleControls readInput() {
		LocalPlayer p = Minecraft.getInstance().player;
		if (p == null) return VehicleControls.NONE;
		Input in = p.input.keyPresses;
		float t = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
		float s = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
		return new VehicleControls(t, s, in.jump());
	}

	private static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			SOUNDS.clear();
			return;
		}
		boolean driving = p.getVehicle() instanceof VehicleEntity v && v.getControllingPassenger() == p;
		VehicleEntity riding = p.getVehicle() instanceof VehicleEntity v ? v : null;
		while (keyHorn.consumeClick()) if (driving) ClientPlayNetworking.send(new VehiclePayloads.Action(VehiclePayloads.Action.HORN, 0));
		while (keyLights.consumeClick()) if (driving) ClientPlayNetworking.send(new VehiclePayloads.Action(VehiclePayloads.Action.LIGHTS, 0));
		while (keySiren.consumeClick()) if (driving) ClientPlayNetworking.send(new VehiclePayloads.Action(VehiclePayloads.Action.SIREN, 0));

		// third person while in a vehicle, restore on exit
		boolean inVehicle = riding != null;
		if (inVehicle && !wasDriving) {
			cameraBeforeEnter = mc.options.getCameraType();
			if (cameraBeforeEnter.isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			VehicleHud.onEnter(riding);
			// face the way the vehicle points
			p.setYRot(riding.getYRot());
			p.yRotO = riding.getYRot();
			p.setXRot(12);
		} else if (inVehicle && riding.getId() != lastRidingId) {
			VehicleHud.onEnter(riding);
			p.setYRot(riding.getYRot());
			p.yRotO = riding.getYRot();
		} else if (!inVehicle && wasDriving) {
			if (cameraBeforeEnter != null) mc.options.setCameraType(cameraBeforeEnter);
			cameraBeforeEnter = null;
		}
		wasDriving = inVehicle;
		lastRidingId = inVehicle ? riding.getId() : -1;
		VehicleHud.tick();

		// looping sounds for nearby vehicles
		for (VehicleEntity v : mc.level.getEntitiesOfClass(VehicleEntity.class, p.getBoundingBox().inflate(64))) {
			ensure(mc, v, 0, VehicleSounds.engine(v.spec), VehicleEntity::engineRunning,
					e -> 0.35 + Math.min(1, Math.abs(e.clientSpeed) / e.spec.maxSpeed()) * 0.5,
					e -> 0.55 + Mth.clamp(Math.abs(e.clientSpeed) / e.spec.maxSpeed(), 0, 1) * 1.15);
			if (v.isPolice()) ensure(mc, v, 1, VehicleSounds.SIREN, VehicleEntity::isSirenOn, e -> 2.5, e -> 1.0);
			ensure(mc, v, 2, VehicleSounds.ALARM, e -> e.flag(VehicleEntity.F_ALARM), e -> 1.6, e -> 1.0);
			if (!v.spec.isBoat()) ensure(mc, v, 3, VehicleSounds.SKID, e -> e.clientSkid && e.onGround(), e -> 0.6, e -> 1.0);
		}
		Iterator<Map.Entry<Long, VehicleLoopSound>> it = SOUNDS.entrySet().iterator();
		while (it.hasNext()) {
			VehicleLoopSound s = it.next().getValue();
			if (s.isStopped()) it.remove();
		}
	}

	private static void ensure(Minecraft mc, VehicleEntity v, int slot, net.minecraft.sounds.SoundEvent event,
			java.util.function.Predicate<VehicleEntity> active, java.util.function.ToDoubleFunction<VehicleEntity> vol,
			java.util.function.ToDoubleFunction<VehicleEntity> pitch) {
		long key = ((long) v.getId() << 3) | slot;
		VehicleLoopSound cur = SOUNDS.get(key);
		if (cur != null && !cur.isStopped()) return;
		if (!active.test(v)) return;
		VehicleLoopSound s = new VehicleLoopSound(event, v, active, vol, pitch);
		SOUNDS.put(key, s);
		mc.getSoundManager().play(s);
	}
}
