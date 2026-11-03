package dev.leonida.character;

import dev.leonida.LeonidaMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * The shared "Leonida" creative tab (id leonida:leonida). Any module can add its items:
 * <pre>CreativeModeTabEvents.modifyOutputEvent(LeonidaTab.KEY).register(out -&gt; out.accept(MY_ITEM));</pre>
 * or simply call {@link #add(ItemLike...)} during mod init.
 */
public final class LeonidaTab {
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, LeonidaMod.id("leonida"));
	private static boolean registered;

	private LeonidaTab() {}

	/** Registers the tab once. Called by CharacterModule; safe to call again. */
	public static void register() {
		if (registered) return;
		registered = true;
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.leonida.leonida"))
				.icon(() -> new ItemStack(CharacterModule.WARDROBE_ITEM))
				.build());
	}

	/** Convenience: append items to the Leonida tab. */
	public static void add(ItemLike... items) {
		CreativeModeTabEvents.modifyOutputEvent(KEY).register(out -> {
			for (ItemLike i : items) out.accept(i);
		});
	}
}
