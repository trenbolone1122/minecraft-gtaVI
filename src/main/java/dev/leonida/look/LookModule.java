package dev.leonida.look;

import dev.leonida.LeonidaMod;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/** Server and common setup for the look module (shared GTA VI style materials and blocks). Owned by the look agent. */
public final class LookModule {
	private LookModule() {}

	public static void init() {
		LookBlocks.init();
		ResourceKey<CreativeModeTab> tabKey = ResourceKey.create(Registries.CREATIVE_MODE_TAB, LeonidaMod.id("look/materials"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabKey, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.leonida.look"))
				.icon(() -> new ItemStack(LookBlocks.stucco(LookBlocks.Pastel.PINK)))
				.displayItems((params, out) -> LookBlocks.all().forEach(b -> out.accept(new ItemStack(b))))
				.build());
	}
}
