package dev.leonida.weapon;

import dev.leonida.core.api.AmmoProvider;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Server and common setup for the weapon module. Owned by the weapon agent. */
public final class WeaponModule {
	private WeaponModule() {}

	private static boolean slowMotion;

	public static void init() {
		WeaponSounds.init();
		WeaponItems.init();
		WeaponNet.init();
		CrimeHooks.init();
		dev.leonida.weapon.shop.WeaponShop.init();

		AmmoProvider.install((player, stack) -> {
			GunType t = WeaponItems.gunOf(stack);
			if (t == null) return null;
			int reserve = player.hasInfiniteMaterials() ? Math.max(Ballistics.reserve(player, t), 999) : Ballistics.reserve(player, t);
			return new AmmoProvider.Ammo(GunItem.clip(stack), reserve, t.displayName.toUpperCase(java.util.Locale.ROOT));
		});

		// right mouse aims a gun: never open doors or trade while holding one
		// (shop counters, racks and ammo boxes still work with a gun in hand)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player.getItemInHand(hand).getItem() instanceof GunItem)) return InteractionResult.PASS;
			var b = level.getBlockState(hit.getBlockPos()).getBlock();
			if (b instanceof dev.leonida.weapon.shop.ShopBlock || b instanceof dev.leonida.weapon.shop.AmmoBoxBlock) return InteractionResult.PASS;
			return InteractionResult.FAIL;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				player.getItemInHand(hand).getItem() instanceof GunItem ? InteractionResult.FAIL : InteractionResult.PASS);

		ServerPlayNetworking.registerGlobalReceiver(WeaponNet.Fire.TYPE, (p, ctx) -> {
			ServerPlayer sp = ctx.player();
			if (sp.isSpectator() || !sp.isAlive()) return;
			Vec3 dir = sp.getViewVector(1f);
			Ballistics.tryFire(sp.level(), sp, sp.getMainHandItem(), dir, p.aiming());
		});
		ServerPlayNetworking.registerGlobalReceiver(WeaponNet.Reload.TYPE, (p, ctx) ->
				Ballistics.startReload(ctx.player(), ctx.player().getMainHandItem()));
		ServerPlayNetworking.registerGlobalReceiver(WeaponNet.Wheel.TYPE, (p, ctx) -> {
			ServerPlayer sp = ctx.player();
			setSlowMotion(ctx.server(), p.open());
			if (p.slot() >= 9 && p.slot() < sp.getInventory().getNonEquipmentItems().size()) {
				// bring a weapon from the backpack into the selected hotbar slot
				var inv = sp.getInventory();
				int sel = inv.getSelectedSlot();
				ItemStack a = inv.getItem(sel), b = inv.getItem(p.slot());
				inv.setItem(sel, b);
				inv.setItem(p.slot(), a);
				inv.setChanged();
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(GunAudio::tick);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) Ballistics.tickPlayer(p);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Ballistics.forget(handler.getPlayer().getUUID());
			CrimeHooks.forget(handler.getPlayer().getUUID());
			setSlowMotion(server, false);
		});
	}

	/** Weapon wheel slow motion, only in singleplayer so nobody else is slowed down. */
	private static void setSlowMotion(MinecraftServer server, boolean on) {
		if (on && (!server.isSingleplayer() || server.getPlayerCount() > 1)) return;
		if (on == slowMotion) return;
		slowMotion = on;
		server.tickRateManager().setTickRate(on ? 6f : 20f);
	}
}
