package dev.leonida.test.character;

import dev.leonida.character.Appearance;
import dev.leonida.character.CharacterClient;
import dev.leonida.character.CharacterSkins;
import dev.leonida.character.Outfits;
import dev.leonida.character.SwitchTransition;
import dev.leonida.character.WardrobeScreen;
import dev.leonida.character.api.CharacterApi;
import dev.leonida.core.PlayerState;
import dev.leonida.core.api.CharacterEvents;
import dev.leonida.test.TestFilter;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

/** Client game tests for the character module. Owned by the character agent. Run: tools/pod-test.sh character */
public class CharacterClientTest implements FabricClientGameTest {
	private static final AtomicInteger OUTFIT_EVENTS = new AtomicInteger();
	private static final AtomicInteger CORE_EVENTS = new AtomicInteger();
	private static boolean listening;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!TestFilter.enabled("character")) return;
		if (!listening) {
			listening = true;
			CharacterApi.OUTFIT_CHANGED.register((p, o, s) -> OUTFIT_EVENTS.incrementAndGet());
			CharacterEvents.OUTFIT_CHANGED.register(p -> CORE_EVENTS.incrementAndGet());
		}
		// skin library sanity
		check(CharacterSkins.PEDESTRIANS.size() >= 40, "pedestrian library too small");
		check(CharacterSkins.randomPedestrian(42L).equals(CharacterSkins.randomPedestrian(42L)), "ped skin not deterministic");
		check(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(dev.leonida.character.LeonidaTab.KEY), "Leonida creative tab missing");
		for (CharacterSkins.District d : CharacterSkins.District.values()) {
			check(!CharacterSkins.pedestrians(d).isEmpty(), "no skins for " + d);
			check(CharacterSkins.pedestrian(d, 99L).equals(CharacterSkins.pedestrian(d, 99L)), "district skin not deterministic");
		}
		check(CharacterSkins.District.fromPlaceName("Vice Beach") == CharacterSkins.District.VICE_BEACH, "place name lookup");
		check(CharacterSkins.District.fromPlaceName("Port Gellhorn") == CharacterSkins.District.PORT_GELLHORN, "place name lookup");
		check(CharacterSkins.randomPedestrian(7L, CharacterSkins.Kind.BEACH).kind() == CharacterSkins.Kind.BEACH, "kind filter");

		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("weather clear");
			check(context.computeOnClient(mc -> dev.leonida.character.model.HdModelLayer.active(
					CharacterClient.skin(Outfits.JASON.getFirst()).body().texturePath()) != null), "HD model not active");
			recreation(context, sp);
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("gamemode survival @a");
			context.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
			stage(sp, 0);
			sp.getConnection().waitForChunksRender();
			context.waitTicks(20);

			// Jason, every outfit
			for (Outfits.Outfit o : Outfits.JASON) shootOutfit(context, sp, o);

			// separate inventories: Jason holds a sword, Lucia arrives empty handed
			sp.getServer().runCommand("give @a minecraft:diamond_sword");
			sp.getServer().runCommand("execute as @a run leonida character lucia instant");
			context.waitFor(mc -> mc.player != null && Appearance.get(mc.player).character() == PlayerState.LUCIA);
			boolean luciaEmpty = sp.getServer().computeOnServer(s -> player(s).getInventory().countItem(Items.DIAMOND_SWORD) == 0);
			check(luciaEmpty, "Lucia should not carry Jason's sword");
			check(sp.getServer().computeOnServer(s -> PlayerState.get(player(s)).character() == PlayerState.LUCIA), "PlayerState.character not LUCIA");
			stage(sp, 0);
			context.waitTicks(20);
			sp.getConnection().waitForChunksRender();
			for (Outfits.Outfit o : Outfits.LUCIA) shootOutfit(context, sp, o);
			// back to her everyday look for the switch
			sp.getServer().runCommand("execute as @a run leonida outfit everyday");

			// wardrobe: open it, pick the bikini, wear it
			sp.getServer().runCommand("gamemode creative @a");
			sp.getServer().runCommand("setblock 2 100 -2 leonida:character/wardrobe[facing=west]");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			int before = OUTFIT_EVENTS.get();
			int coreBefore = CORE_EVENTS.get();
			context.setScreen(WardrobeScreen::new);
			context.waitTicks(5);
			context.clickScreenButton("character.leonida.outfit.lucia.swim");
			context.waitTicks(5);
			context.takeScreenshot("character-wardrobe-lucia");
			context.clickScreenButton("character.leonida.wardrobe.wear");
			context.waitFor(mc -> mc.player != null && Appearance.get(mc.player).outfit().id().equals("swim"));
			check(OUTFIT_EVENTS.get() > before, "OUTFIT_CHANGED did not fire on wardrobe change");
			check(CORE_EVENTS.get() > coreBefore, "core CharacterEvents.outfitChanged not called");
			sp.getServer().runCommand("gamemode survival @a");
			sp.getServer().runCommand("execute as @a run leonida outfit everyday");

			// GTA switch with the key and camera transition: Lucia -> Jason
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(10);
			context.takeScreenshot("character-switch-0-before");
			context.getInput().pressKey(CharacterClient.switchKey);
			context.waitFor(mc -> SwitchTransition.active(), 60);
			context.waitTicks(8);
			context.takeScreenshot("character-switch-1-lifting");
			context.waitFor(mc -> SwitchTransition.phaseName().equals("HOLD") || SwitchTransition.phaseName().equals("DOWN"), 100);
			context.takeScreenshot("character-switch-2-sky");
			context.waitFor(mc -> SwitchTransition.phaseName().equals("DOWN"), 100);
			context.waitTicks(8);
			context.takeScreenshot("character-switch-3-dropping");
			context.waitFor(mc -> !SwitchTransition.active(), 200);
			context.waitTicks(5);
			context.takeScreenshot("character-switch-4-jason");
			check(sp.getServer().computeOnServer(s -> PlayerState.get(player(s)).character() == PlayerState.JASON), "switch back to Jason failed");
			boolean swordBack = sp.getServer().computeOnServer(s -> player(s).getInventory().countItem(Items.DIAMOND_SWORD) == 1);
			check(swordBack, "Jason's sword did not come back");

			// wanted players cannot switch
			sp.getServer().runOnServer(s -> PlayerState.set(player(s), PlayerState.get(player(s)).withWanted(2)));
			context.waitTicks(2);
			context.getInput().pressKey(CharacterClient.switchKey);
			context.waitTicks(40);
			check(sp.getServer().computeOnServer(s -> PlayerState.get(player(s)).character() == PlayerState.JASON), "switched while wanted");
			sp.getServer().runOnServer(s -> PlayerState.set(player(s), PlayerState.get(player(s)).withWanted(0)));
			System.out.println("[leonida-test] character: all checks passed");
		}
	}

	private static ServerPlayer player(MinecraftServer s) {
		return s.getPlayerList().getPlayers().getFirst();
	}

	private static void check(boolean ok, String msg) {
		if (!ok) throw new AssertionError("[character] " + msg);
	}

	/**
	 * Recreation of the key art: a sunset street, Jason standing with a pistol at his side, Lucia
	 * sitting on the hood of a red car, one pistol up. The camera is a flying creative player.
	 */
	private static void recreation(ClientGameTestContext context, TestSingleplayerContext sp) {
		String[] c = {
				// ground: asphalt road along x with a centre line, raised sidewalks, then shop fronts
				"fill 70 98 -20 130 98 20 minecraft:stone",
				"fill 70 99 -20 130 125 20 minecraft:air",
				"fill 70 99 -5 130 99 5 minecraft:gray_concrete",
				"fill 70 99 0 130 99 0 minecraft:yellow_concrete",
				"fill 70 99 6 130 99 9 minecraft:smooth_stone",
				"fill 70 99 -9 130 99 -6 minecraft:smooth_stone",
				"fill 70 100 6 130 100 6 minecraft:smooth_stone_slab",
				"fill 70 100 -6 130 100 -6 minecraft:smooth_stone_slab",
				"fill 70 99 10 84 112 16 minecraft:pink_terracotta",
				"fill 85 99 10 96 108 16 minecraft:white_terracotta",
				"fill 97 99 10 110 115 16 minecraft:light_blue_terracotta",
				"fill 111 99 10 130 109 16 minecraft:yellow_terracotta",
				"fill 70 99 -16 82 110 -10 minecraft:light_blue_terracotta",
				"fill 83 99 -16 98 113 -10 minecraft:pink_terracotta",
				"fill 99 99 -16 112 107 -10 minecraft:white_terracotta",
				"fill 113 99 -16 130 111 -10 minecraft:orange_terracotta",
				"fill 72 102 10 128 102 10 minecraft:light_blue_stained_glass",
				"fill 72 105 10 128 105 10 minecraft:light_blue_stained_glass",
				"fill 72 102 -10 128 102 -10 minecraft:light_blue_stained_glass",
				"fill 72 105 -10 128 105 -10 minecraft:light_blue_stained_glass",
				"fill 74 100 10 76 101 10 minecraft:magenta_stained_glass",
				"fill 100 100 -10 103 101 -10 minecraft:cyan_stained_glass",
				// palms
				"fill 90 100 8 90 107 8 minecraft:stripped_jungle_log", "fill 89 108 7 91 108 9 minecraft:jungle_leaves[persistent=true]",
				"fill 87 108 8 93 108 8 minecraft:jungle_leaves[persistent=true]", "fill 90 108 5 90 108 11 minecraft:jungle_leaves[persistent=true]",
				"fill 90 109 8 90 109 8 minecraft:jungle_leaves[persistent=true]",
				"fill 84 100 -8 84 108 -8 minecraft:stripped_jungle_log", "fill 83 109 -9 85 109 -7 minecraft:jungle_leaves[persistent=true]",
				"fill 81 109 -8 87 109 -8 minecraft:jungle_leaves[persistent=true]", "fill 84 109 -11 84 109 -5 minecraft:jungle_leaves[persistent=true]",
				"fill 84 110 -8 84 110 -8 minecraft:jungle_leaves[persistent=true]",
				"fill 76 100 7 76 106 7 minecraft:stripped_jungle_log", "fill 75 107 6 77 107 8 minecraft:jungle_leaves[persistent=true]",
				"fill 73 107 7 79 107 7 minecraft:jungle_leaves[persistent=true]",
				// street lights
				"fill 96 101 -7 96 104 -7 minecraft:iron_bars", "setblock 96 105 -7 minecraft:sea_lantern",
				"fill 80 101 7 80 104 7 minecraft:iron_bars", "setblock 80 105 7 minecraft:sea_lantern",
				// red car parked along the far kerb, nose towards the camera: low body, glass cabin, roof, hood
				"fill 96 100 -4 102 100 -2 minecraft:red_concrete",
				"fill 97 101 -4 99 101 -2 minecraft:black_stained_glass",
				"fill 97 102 -4 99 102 -2 minecraft:red_concrete",
				"fill 96 101 -4 96 101 -2 minecraft:red_concrete",
				"setblock 103 100 -4 minecraft:end_rod[facing=east]", "setblock 103 100 -2 minecraft:end_rod[facing=east]",
				"fill 97 99 -5 97 99 -5 minecraft:black_concrete", "fill 101 99 -5 101 99 -5 minecraft:black_concrete",
				"kill @e[type=!player]",
				"time set 12550", "weather clear",
		};
		// camera: a flying creative player (spectators would see the invisible seat)
		sp.getServer().runCommand("gamemode creative @a");
		sp.getServer().runOnServer(s -> { player(s).getAbilities().flying = true; player(s).onUpdateAbilities(); });
		sp.getServer().runCommand("tp @a 110.5 101.2 -0.8 95 4");
		sp.getConnection().waitForChunksRender();
		context.waitTicks(20);
		for (String cmd : c) sp.getServer().runCommand(cmd);
		// on the GPU machine (Iris present) shoot the scene through our own shader pack
		boolean pack = context.computeOnClient(mc -> irisPack("Leonida Shaders"));
		System.out.println("[leonida-test] character recreation with shader pack: " + pack);
		context.waitTicks(40);
		// Lucia sits on the hood (an invisible seat), Jason stands in front of the car
		sp.getServer().runCommand("summon armor_stand 102.85 101.0 -3.0 {Invisible:1b,Marker:1b,NoGravity:1b,Tags:[\"seat\"],Rotation:[-40f,0f]}");
		sp.getServer().runCommand("summon mannequin 102.85 101 -3.0 {Tags:[\"lucia\"],Rotation:[-40f,0f],hide_description:true,"
				+ "profile:{texture:\"leonida:entity/character/lucia/everyday\",model:\"slim\"},"
				+ "equipment:{mainhand:{id:\"leonida:weapon/pistol\",count:1},offhand:{id:\"leonida:weapon/pistol\",count:1}}}");
		sp.getServer().runCommand("ride @e[type=mannequin,tag=lucia,limit=1] mount @e[type=armor_stand,tag=seat,limit=1]");
		sp.getServer().runCommand("summon mannequin 102.9 100 0.9 {Tags:[\"jason\"],Rotation:[-110f,0f],hide_description:true,immovable:true,"
				+ "profile:{texture:\"leonida:entity/character/jason/everyday\"},"
				+ "equipment:{offhand:{id:\"leonida:weapon/pistol\",count:1}}}");
		sp.getServer().runCommand("tp @a 107.6 100.2 -1.0 84 4");
		context.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
		context.runOnClient(mc -> mc.options.fov().set(50));
		sp.getConnection().waitForChunksRender();
		context.waitTicks(40);
		check(sp.getServer().computeOnServer(s -> !s.overworld().getEntities(net.minecraft.world.entity.EntityTypes.MANNEQUIN,
				e -> e.entityTags().contains("lucia") && e.isPassenger()).isEmpty()), "Lucia is not seated on the hood");
		context.takeScreenshot("character-recreation-sunset");
		// face close-ups: front, three-quarter and side
		context.runOnClient(mc -> mc.options.fov().set(30));
		portrait(context, sp, "jason", 0, 2.6, "character-recreation-jason-front");
		portrait(context, sp, "jason", 40, 2.6, "character-recreation-jason-34");
		// Hair close-ups: lower the pistols so her arms do not cover the face.
		sp.getServer().runCommand("item replace entity @e[type=mannequin,tag=lucia] weapon.mainhand with air");
		sp.getServer().runCommand("item replace entity @e[type=mannequin,tag=lucia] weapon.offhand with air");
		context.waitTicks(5);
		portrait(context, sp, "lucia", 0, 2.4, "character-recreation-lucia-front");
		portrait(context, sp, "lucia", -40, 2.4, "character-recreation-lucia-34");
		portrait(context, sp, "lucia", 80, 2.4, "character-recreation-lucia-side");
		context.runOnClient(mc -> mc.options.fov().set(70));
		// vanilla skin fallback still works when the detailed model is switched off
		context.runOnClient(mc -> dev.leonida.character.model.HdModelLayer.enabled = false);
		context.runOnClient(mc -> mc.options.fov().set(50));
		sp.getServer().runCommand("tp @a 107.6 100.2 -1.0 84 4");
		context.waitTicks(5);
		context.takeScreenshot("character-recreation-fallback");
		context.runOnClient(mc -> mc.options.fov().set(70));
		context.runOnClient(mc -> dev.leonida.character.model.HdModelLayer.enabled = true);
		if (pack) context.computeOnClient(mc -> irisPack(null));
		sp.getServer().runCommand("kill @e[type=!player]");
	}

	/** Moves the camera to look a mannequin in the face from offDeg around it, at dist blocks. */
	private static void portrait(ClientGameTestContext context, TestSingleplayerContext sp, String tag, double offDeg, double dist, String shot) {
		double[] e = sp.getServer().computeOnServer(s -> {
			var list = s.overworld().getEntities(net.minecraft.world.entity.EntityTypes.MANNEQUIN, m -> m.entityTags().contains(tag));
			var m = list.getFirst();
			var eye = m.getEyePosition();
			return new double[] {eye.x, eye.y, eye.z, m.getYRot()};
		});
		double yaw = Math.toRadians(e[3] + offDeg);
		double cx = e[0] - Math.sin(yaw) * dist, cz = e[2] + Math.cos(yaw) * dist;
		float camYaw = (float) (e[3] + offDeg + 180.0);
		sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.3f %.3f %.3f %.1f 2", cx, e[1] - 1.62 - 0.12, cz, camYaw));
		context.waitTicks(10);
		context.takeScreenshot(shot);
	}

	/** Selects a shader pack in Iris (null turns shaders off). False when Iris is not installed. */
	private static boolean irisPack(String name) {
		try {
			Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
			Object cfg = iris.getMethod("getIrisConfig").invoke(null);
			cfg.getClass().getMethod("setShaderPackName", String.class).invoke(cfg, name);
			cfg.getClass().getMethod("setShadersEnabled", boolean.class).invoke(cfg, name != null);
			cfg.getClass().getMethod("save").invoke(cfg);
			iris.getMethod("reload").invoke(null);
			return name != null;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return false;
		}
	}

	/** A small sunny photo stage at y=100 with a backdrop wall, the player facing north. */
	private static void stage(TestSingleplayerContext sp, int x) {
		sp.getServer().runCommand("fill -6 99 -10 6 99 6 minecraft:smooth_sandstone");
		sp.getServer().runCommand("fill -6 100 -10 6 110 6 minecraft:air");
		sp.getServer().runCommand("fill -6 100 6 6 104 6 minecraft:light_blue_concrete");
		sp.getServer().runCommand("fill -6 100 -10 6 104 -10 minecraft:pink_concrete");
		sp.getServer().runCommand("setblock -3 100 -9 minecraft:potted_dead_bush");
		sp.getServer().runCommand("tp @a " + x + ".5 100 0.5 180 10");
	}

	private static void shootOutfit(ClientGameTestContext context, TestSingleplayerContext sp, Outfits.Outfit o) {
		String name = Outfits.characterName(o.character()) + "-" + o.id();
		sp.getServer().runCommand("execute as @a run leonida outfit " + o.id());
		sp.getServer().runCommand("tp @a 0.5 100 0.5 180 10");
		context.waitFor(mc -> mc.player != null && Appearance.get(mc.player).outfit().equals(o));
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		sp.getConnection().waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("character-" + name + "-front");
		// HD face close-ups: narrow field of view, front and three-quarter
		boolean hd = context.computeOnClient(mc -> CharacterClient.skin(o).body().texturePath().getPath().contains("/hd/"));
		check(hd, "HD skin not in use for " + name);
		context.runOnClient(mc -> mc.options.fov().set(30));
		sp.getServer().runCommand("tp @a 0.5 100 0.5 180 0");
		context.waitTicks(6);
		context.takeScreenshot("character-" + name + "-face-front");
		sp.getServer().runCommand("tp @a 0.5 100 0.5 140 0");
		context.waitTicks(6);
		context.takeScreenshot("character-" + name + "-face-34");
		context.runOnClient(mc -> mc.options.fov().set(70));
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		sp.getServer().runCommand("tp @a 0.5 100 0.5 150 25");
		context.waitTicks(6);
		context.takeScreenshot("character-" + name + "-back");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		sp.getServer().runCommand("tp @a 0.5 100 0.5 180 20");
		context.waitTicks(6);
		context.takeScreenshot("character-" + name + "-firstperson");
	}
}
