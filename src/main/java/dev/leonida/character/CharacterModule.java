package dev.leonida.character;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.leonida.LeonidaMod;
import dev.leonida.character.net.CharacterNet;
import dev.leonida.core.PlayerState;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Server and common setup for the character module. Owned by the character agent. */
public final class CharacterModule {
	private CharacterModule() {}

	public static Block WARDROBE;
	public static Item WARDROBE_ITEM;

	public static void init() {
		Appearance.register();
		CharacterData.register();
		CharacterNet.register();
		registerBlocks();

		ServerPlayNetworking.registerGlobalReceiver(CharacterNet.SwitchRequest.TYPE,
				(payload, ctx) -> CharacterSwitcher.request(ctx.player(), payload.target(), false));
		ServerPlayNetworking.registerGlobalReceiver(CharacterNet.SetOutfit.TYPE,
				(payload, ctx) -> CharacterSwitcher.handleSetOutfit(ctx.player(), payload.index()));
		ServerTickEvents.END_SERVER_TICK.register(CharacterSwitcher::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CharacterSwitcher.syncOnJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CharacterSwitcher.forget(handler.getPlayer().getUUID()));
		registerCommands();
	}

	private static void registerBlocks() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, LeonidaMod.id("character/wardrobe"));
		WARDROBE = Registry.register(BuiltInRegistries.BLOCK, key, new WardrobeBlock(BlockBehaviour.Properties.of()
				.setId(key).mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD)));
		ResourceKey<Item> ikey = ResourceKey.create(Registries.ITEM, LeonidaMod.id("character/wardrobe"));
		WARDROBE_ITEM = Registry.register(BuiltInRegistries.ITEM, ikey,
				new BlockItem(WARDROBE, new Item.Properties().setId(ikey).useBlockDescriptionPrefix()));
		LeonidaTab.register();
		LeonidaTab.add(WARDROBE_ITEM);
	}

	private static void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> dispatcher.register(
				Commands.literal("leonida")
						.then(Commands.literal("character")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.argument("name", StringArgumentType.word())
										.suggests((c, b) -> { b.suggest("jason"); b.suggest("lucia"); return b.buildFuture(); })
										.executes(c -> {
											ServerPlayer p = c.getSource().getPlayerOrException();
											String n = StringArgumentType.getString(c, "name");
											int target = n.equalsIgnoreCase("lucia") ? PlayerState.LUCIA : PlayerState.JASON;
											boolean ok = CharacterSwitcher.request(p, target, false);
											if (!ok) c.getSource().sendFailure(Component.translatable("character.leonida.switch.failed"));
											return ok ? 1 : 0;
										})
										.then(Commands.literal("instant").executes(c -> {
											ServerPlayer p = c.getSource().getPlayerOrException();
											String n = StringArgumentType.getString(c, "name");
											return CharacterSwitcher.request(p, n.equalsIgnoreCase("lucia") ? PlayerState.LUCIA : PlayerState.JASON, true) ? 1 : 0;
										}))))
						.then(Commands.literal("outfit")
								.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
								.then(Commands.argument("id", StringArgumentType.word())
										.suggests((c, b) -> {
											for (Outfits.Outfit o : Outfits.JASON) b.suggest(o.id());
											for (Outfits.Outfit o : Outfits.LUCIA) b.suggest(o.id());
											return b.buildFuture();
										})
										.executes(c -> {
											ServerPlayer p = c.getSource().getPlayerOrException();
											int idx = Outfits.find(Appearance.get(p).character(), StringArgumentType.getString(c, "id"));
											if (idx < 0) {
												c.getSource().sendFailure(Component.translatable("character.leonida.outfit.unknown"));
												return 0;
											}
											CharacterSwitcher.setOutfit(p, idx);
											return 1;
										})))));
	}
}
