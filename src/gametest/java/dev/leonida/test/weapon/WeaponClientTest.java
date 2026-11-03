package dev.leonida.test.weapon;

import dev.leonida.test.TestFilter;
import com.mojang.blaze3d.platform.InputConstants;
import dev.leonida.weapon.GunShopScreen;
import dev.leonida.weapon.WeaponClient;
import dev.leonida.weapon.WeaponItems;
import dev.leonida.weapon.WeaponNet;
import dev.leonida.weapon.api.WeaponApi;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import dev.leonida.weapon.WeaponWheelScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/** Client game tests for the weapon module. Owned by the weapon agent. Run: tools/pod-test.sh weapon */
public class WeaponClientTest implements FabricClientGameTest {
	private static final String[] GUNS = {"pistol", "smg", "rifle", "shotgun", "sniper", "rpg", "bat", "knife"};

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("weapon")) return;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			cmd(sp, "time set noon");
			cmd(sp, "weather clear");
			cmd(sp, "gamerule doDaylightCycle false");
			cmd(sp, "gamerule doMobSpawning false");
			cmd(sp, "fill -10 150 -4 10 150 30 smooth_stone");
			cmd(sp, "fill -10 151 22 10 158 22 bricks");
			cmd(sp, "fill -3 151 21 3 153 21 oak_planks");
			cmd(sp, "fill -10 151 -4 10 160 20 air");
			cmd(sp, "fill 4 151 21 6 153 21 glass");
			cmd(sp, "tp @p 0.5 151 0.5 0 0");
			cmd(sp, "gamemode survival @p");
			cmd(sp, "effect give @p resistance infinite 255 true");
			cmd(sp, "effect give @p saturation infinite 255 true");
			cmd(sp, "clear @p");
			for (int i = 0; i < GUNS.length; i++) {
				if (GUNS[i].equals("shotgun")) continue;
				cmd(sp, "item replace entity @p hotbar." + i + " with leonida:weapon/" + GUNS[i]);
			}
			for (String a : new String[] {"pistol", "smg", "rifle", "shotgun", "sniper"}) {
				cmd(sp, "give @p leonida:weapon/ammo_" + a + " 64");
			}
			cmd(sp, "give @p leonida:weapon/ammo_rocket 8");
			cmd(sp, "summon husk 2.5 151 9.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
			cmd(sp, "summon villager -2.5 151 11.5 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}");
			context.getInput().lookAt(0f, 0f);
			context.waitTicks(40);
			sp.getConnection().waitForChunksRender();

			// Pickup toast: the shotgun arrives after the inventory has been seen.
			cmd(sp, "item replace entity @p hotbar.3 with leonida:weapon/shotgun");
			context.waitTicks(12);
			context.takeScreenshot("weapon_pickup_toast");
			context.waitTicks(80);

			// Each weapon in first person, hip fire pose.
			for (int i = 0; i < GUNS.length; i++) {
				select(context, i);
				context.waitTicks(15);
				context.takeScreenshot("weapon_fp_" + GUNS[i]);
			}

			// Aiming down sights.
			for (int i : new int[] {0, 2, 4}) {
				select(context, i);
				context.waitTicks(10);
				WeaponClient.forceAim = true;
				context.waitTicks(15);
				context.takeScreenshot("weapon_aim_" + GUNS[i]);
				WeaponClient.forceAim = false;
				context.waitTicks(10);
			}

			// Firing: muzzle flash, tracers, impacts on the wall and on the husk.
			select(context, 1);
			context.waitTicks(10);
			context.getInput().lookAt(8f, 2f);
			context.getInput().holdKey(opts -> opts.keyAttack);
			context.waitTicks(5);
			context.takeScreenshot("weapon_fire_smg");
			context.waitTicks(4);
			context.getInput().releaseKey(opts -> opts.keyAttack);
			context.getInput().lookAt(0f, 0f);
			context.waitTicks(10);

			select(context, 3);
			context.waitTicks(10);
			context.getInput().lookAt(-6f, 0f);
			context.getInput().holdKeyFor(opts -> opts.keyAttack, 2);
			context.waitTicks(1);
			context.takeScreenshot("weapon_fire_shotgun");
			context.waitTicks(30);
			context.getInput().lookAt(0f, 0f);

			// Reload with the reload key.
			select(context, 2);
			context.waitTicks(10);
			context.getInput().holdKey(opts -> opts.keyAttack);
			context.waitTicks(6);
			context.getInput().releaseKey(opts -> opts.keyAttack);
			context.getInput().lookAt(0f, 0f);
			context.waitTicks(10);
			context.getInput().pressKey(WeaponClient.keyReload);
			context.waitTicks(14);
			context.takeScreenshot("weapon_reload_rifle");
			context.waitTicks(60);
			context.takeScreenshot("weapon_reload_done");

			// Weapon wheel and sniper scope, centred at the default size and after real window resizes.
			int[][] sizes = {{0, 0}, {1600, 900}, {1280, 720}, {1000, 800}};
			for (int[] sz : sizes) {
				String tag = sz[0] == 0 ? "default" : sz[0] + "x" + sz[1];
				if (sz[0] != 0) {
					context.getInput().resizeWindow(sz[0], sz[1]);
					context.waitTicks(5);
				}
				WeaponWheelScreen.forcedSelection = 5;
				context.setScreen(WeaponWheelScreen::new);
				context.waitTicks(8);
				context.takeScreenshot(sz[0] == 0 ? "weapon_wheel" : "weapon_wheel_" + tag);
				context.setScreen(() -> null);
				WeaponWheelScreen.forcedSelection = -1;
				context.waitTicks(5);
				select(context, 4);
				context.waitTicks(8);
				WeaponClient.forceAim = true;
				context.waitTicks(12);
				context.takeScreenshot("weapon_scope_" + tag);
				WeaponClient.forceAim = false;
				context.waitTicks(8);
			}
			context.getInput().resizeWindow(854, 480);
			context.waitTicks(5);

			// Rocket launcher and explosion against the brick wall.
			select(context, 5);
			context.waitTicks(15);
			context.getInput().lookAt(0f, -2f);
			context.getInput().holdKeyFor(opts -> opts.keyAttack, 2);
			context.waitTicks(3);
			context.takeScreenshot("weapon_rpg_launch");
			context.waitTicks(6);
			context.takeScreenshot("weapon_rpg_explosion_a");
			context.waitTicks(3);
			context.takeScreenshot("weapon_rpg_explosion_b");
			context.waitTicks(8);
			context.takeScreenshot("weapon_rpg_explosion_c");
			context.waitTicks(40);
			// a closer blast on the floor: shake, flash and debris up close
			context.getInput().lookAt(-25f, 14f);
			context.waitTicks(5);
			context.getInput().holdKeyFor(opts -> opts.keyAttack, 2);
			context.waitTicks(4);
			context.takeScreenshot("weapon_rpg_close_a");
			context.waitTicks(4);
			context.takeScreenshot("weapon_rpg_close_b");
			context.waitTicks(40);

			// Third person pose.
			select(context, 2);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(15);
			context.takeScreenshot("weapon_third_person_rifle");
			WeaponClient.forceAim = true;
			context.waitTicks(15);
			context.takeScreenshot("weapon_third_person_aim");
			WeaponClient.forceAim = false;
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(5);

			// Ammu-Nation: counter row, racks on a back wall, ammo box and spinning pickups.
			cmd(sp, "kill @e[type=!player]");
			cmd(sp, "fill -3 151 6 3 154 6 bricks");
			cmd(sp, "fill -1 151 3 1 151 3 leonida:weapon/gun_counter[facing=north]");
			cmd(sp, "fill -1 151 5 1 152 5 leonida:weapon/gun_rack[facing=north]");
			cmd(sp, "setblock 2 151 3 leonida:weapon/ammo_box[facing=north]");
			cmd(sp, "item replace entity @p hotbar.7 with air");
			sp.getServer().runOnServer(server -> {
				var level = server.overworld();
				WeaponApi.spawnPickup(level, new Vec3(-2.5, 151.0, 2.5), WeaponApi.gun("rifle"), true);
				WeaponApi.spawnPickup(level, new Vec3(-2.5, 151.0, 0.5), new ItemStack(WeaponItems.KNIFE), true);
			});
			cmd(sp, "tp @p 0.5 151 -1.5 0 0");
			select(context, 0);
			context.getInput().lookAt(-10f, 22f);
			context.waitTicks(30);
			context.takeScreenshot("weapon_shop_blocks");

			// Open the counter menu (what a right click does) and buy things.
			sp.getServer().runOnServer(server -> {
				ServerPlayer p = server.getPlayerList().getPlayers().get(0);
				ServerPlayNetworking.send(p, new WeaponNet.OpenShop(new BlockPos(0, 151, 3)));
			});
			context.waitTicks(10);
			context.takeScreenshot("weapon_shop_menu");
			shop(context, "rifle");
			context.waitTicks(6);
			context.takeScreenshot("weapon_shop_owned");
			shop(context, "ammo_pistol");
			context.waitTicks(6);
			context.takeScreenshot("weapon_shop_bought");
			shop(context, "armor");
			context.waitTicks(6);
			context.takeScreenshot("weapon_shop_no_cash");
			context.setScreen(() -> null);
			context.waitTicks(20);

			// Walk into the knife pickup: it is collected with a pickup prompt.
			cmd(sp, "tp @p -2.5 151 0.5 0 0");
			context.waitTicks(8);
			context.takeScreenshot("weapon_pickup_collect");
			context.waitTicks(70);

			// Ammo box: use it with a gun in hand, it hands out ammo and shows empty.
			cmd(sp, "tp @p 2.5 151 1.2 0 27");
			context.getInput().lookAt(0f, 27f);
			select(context, 2);
			context.waitTicks(10);
			context.getInput().pressKey(opts -> opts.keyUse);
			context.waitTicks(8);
			context.takeScreenshot("weapon_ammo_box");

			// GTA keyboard layout with real key input: right mouse on the counter opens the shop even with a gun,
			// arrows and Escape drive it; Tab held opens the wheel, mouse wheel steps it, release equips;
			// right mouse aims; held left mouse fires and recoil settles back afterwards.
			cmd(sp, "tp @p 0.5 151 1.4 0 25");
			context.getInput().lookAt(0f, 25f);
			context.waitTicks(10);
			context.getInput().pressKey(opts -> opts.keyUse);
			context.waitTicks(8);
			boolean shopOpen = context.computeOnClient(mc -> mc.gui.screen() instanceof GunShopScreen);
			System.out.println("[leonida-test] keys: right click counter opens shop = " + shopOpen);
			if (!shopOpen) throw new AssertionError("right click on gun counter did not open the shop");
			context.getInput().pressKey(InputConstants.KEY_DOWN);
			context.getInput().pressKey(InputConstants.KEY_DOWN);
			context.waitTicks(3);
			context.takeScreenshot("weapon_keys_shop");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);
			boolean shopClosed = context.computeOnClient(mc -> mc.gui.screen() == null);
			System.out.println("[leonida-test] keys: escape closes shop = " + shopClosed);
			if (!shopClosed) throw new AssertionError("escape did not close the shop");

			cmd(sp, "tp @p 0.5 151 0.5 180 0");
			select(context, 2);
			context.getInput().lookAt(180f, -5f);
			context.waitTicks(10);
			context.getInput().holdKey(WeaponClient.keyWheel);
			context.waitTicks(6);
			boolean wheelOpen = context.computeOnClient(mc -> mc.gui.screen() instanceof WeaponWheelScreen);
			System.out.println("[leonida-test] keys: holding Tab opens wheel = " + wheelOpen);
			if (!wheelOpen) throw new AssertionError("holding Tab did not open the wheel");
			context.getInput().scroll(-1);
			context.waitTicks(2);
			context.getInput().scroll(-1);
			context.waitTicks(4);
			context.takeScreenshot("weapon_keys_wheel");
			context.getInput().releaseKey(WeaponClient.keyWheel);
			context.waitTicks(6);
			String heldAfter = context.computeOnClient(mc -> mc.gui.screen() == null
					? String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem())) : "wheel still open");
			System.out.println("[leonida-test] keys: released Tab, now holding " + heldAfter);
			if (heldAfter.equals("wheel still open")) throw new AssertionError("releasing Tab did not close the wheel");

			select(context, 2);
			context.waitTicks(6);
			context.getInput().holdKey(opts -> opts.keyUse);
			context.waitTicks(12);
			boolean aimed = context.computeOnClient(mc -> WeaponClient.aiming);
			System.out.println("[leonida-test] keys: right mouse aims = " + aimed);
			context.takeScreenshot("weapon_keys_aim");
			context.getInput().releaseKey(opts -> opts.keyUse);
			if (!aimed) throw new AssertionError("right mouse did not aim");
			context.waitTicks(10);

			float pitch0 = context.computeOnClient(mc -> mc.player.getXRot());
			context.getInput().holdKey(opts -> opts.keyAttack);
			context.waitTicks(12);
			context.getInput().releaseKey(opts -> opts.keyAttack);
			context.waitTicks(1);
			float pitchPeak = context.computeOnClient(mc -> mc.player.getXRot());
			context.takeScreenshot("weapon_keys_burst");
			context.waitTicks(30);
			float pitchSettled = context.computeOnClient(mc -> mc.player.getXRot());
			System.out.printf("[leonida-test] recoil: pitch before %.2f, after burst %.2f, settled %.2f%n", pitch0, pitchPeak, pitchSettled);
			if (!(pitchPeak < pitch0 - 1f)) throw new AssertionError("rifle burst did not climb");
			if (!(pitchSettled > pitchPeak + 0.5f)) throw new AssertionError("recoil did not settle back");
		}
	}

	private static void shop(ClientGameTestContext context, String key) {
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof GunShopScreen s) { s.selectKey(key); s.buy(); }
		});
	}

	private static void select(ClientGameTestContext context, int slot) {
		context.getInput().pressKey(opts -> opts.keyHotbarSlots[slot]);
	}

	private static void cmd(TestSingleplayerContext sp, String c) {
		sp.getServer().runCommand(c);
	}
}
