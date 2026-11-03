package dev.leonida.character;

import dev.leonida.character.api.CharacterApi;
import dev.leonida.character.net.CharacterNet;
import dev.leonida.core.PlayerState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Server side of GTA style character switching. A switch is two-phase: the client plays the camera
 * pull-up for {@link #UP_TICKS}, then the server stores the current character (position, inventory,
 * health, hunger), restores the other one and tells the client to drop the camera back down.
 */
public final class CharacterSwitcher {
	private CharacterSwitcher() {}

	public static final int UP_TICKS = 24;
	/** Fresh characters (never played) appear this far from the current one. */
	private static final int FRESH_DISTANCE = 28;

	private record Pending(int target, long due) {}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final Map<UUID, BlockPos> WARDROBE = new HashMap<>();

	/** Ask for a switch. instant=true skips the transition delay (commands, tests). */
	public static boolean request(ServerPlayer p, int target, boolean instant) {
		int cur = PlayerState.get(p).character();
		if (target < 0) target = Outfits.other(cur);
		if (target != PlayerState.JASON && target != PlayerState.LUCIA) return false;
		if (target == cur || PENDING.containsKey(p.getUUID())) return false;
		if (p.isDeadOrDying()) return false;
		if (PlayerState.get(p).wanted() > 0) {
			p.sendOverlayMessage(Component.translatable("character.leonida.switch.denied_wanted"));
			return false;
		}
		if (instant) {
			swap(p, target);
			return true;
		}
		PENDING.put(p.getUUID(), new Pending(target, p.level().getServer().getTickCount() + UP_TICKS));
		ServerPlayNetworking.send(p, new CharacterNet.SwitchStart(target, UP_TICKS));
		return true;
	}

	static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) return;
		long now = server.getTickCount();
		List<UUID> done = new ArrayList<>();
		for (Map.Entry<UUID, Pending> e : PENDING.entrySet()) {
			if (e.getValue().due() > now) continue;
			done.add(e.getKey());
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || p.isDeadOrDying()) continue;
			swap(p, e.getValue().target());
		}
		done.forEach(PENDING::remove);
	}

	static void forget(UUID id) {
		PENDING.remove(id);
		WARDROBE.remove(id);
	}

	static CharacterData.Slot capture(ServerPlayer p) {
		Inventory inv = p.getInventory();
		List<ItemStackWithSlot> items = new ArrayList<>();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty()) items.add(new ItemStackWithSlot(i, s.copy()));
		}
		return new CharacterData.Slot(true, p.level().dimension().identifier().toString(), p.getX(), p.getY(), p.getZ(),
				p.getYRot(), p.getXRot(), p.getHealth(), p.getFoodData().getFoodLevel(), p.getFoodData().getSaturationLevel(),
				inv.getSelectedSlot(), items);
	}

	/** Do the actual swap now. */
	public static void swap(ServerPlayer p, int target) {
		int cur = PlayerState.get(p).character();
		if (target == cur) return;
		CharacterData data = CharacterData.get(p).with(cur, capture(p));
		CharacterData.Slot dest = data.slot(target);
		if (!dest.valid()) dest = freshSlot(p);
		p.setAttached(CharacterData.TYPE, data);

		if (p.isPassenger()) p.stopRiding();
		Inventory inv = p.getInventory();
		inv.clearContent();
		for (ItemStackWithSlot s : dest.items()) {
			if (s.isValidInContainer(inv.getContainerSize())) inv.setItem(s.slot(), s.stack().copy());
		}
		inv.setSelectedSlot(Math.max(0, Math.min(Inventory.getSelectionSize() - 1, dest.selected())));
		p.setHealth(Math.max(1f, Math.min(p.getMaxHealth(), dest.health())));
		p.getFoodData().setFoodLevel(dest.food());
		p.getFoodData().setSaturation(dest.saturation());
		p.fallDistance = 0;
		p.setDeltaMovement(0, 0, 0);

		ServerLevel level = p.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dest.dimension())));
		if (level == null) level = p.level();
		p.teleportTo(level, dest.x(), dest.y(), dest.z(), Set.of(), dest.yRot(), dest.xRot(), true);
		p.containerMenu.broadcastChanges();
		p.inventoryMenu.broadcastChanges();

		PlayerState.set(p, PlayerState.get(p).withCharacter(target));
		Appearance a = Appearance.get(p).withCharacter(target);
		p.setAttached(Appearance.TYPE, a);
		ServerPlayNetworking.send(p, new CharacterNet.SwitchDone(target));
		CharacterApi.CHARACTER_SWITCHED.invoker().onSwitched(p, cur, target);
		fireOutfitChanged(p, a.outfit(), true);
	}

	/** A never-played character waits a short walk away, on the surface, with full health and empty pockets. */
	private static CharacterData.Slot freshSlot(ServerPlayer p) {
		ServerLevel level = p.level();
		double best = Double.NaN, bx = p.getX(), bz = p.getZ(), by = p.getY();
		for (int i = 0; i < 8 && Double.isNaN(best); i++) {
			double a = (p.getYRot() + 90 + i * 45) * Math.PI / 180.0;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * FRESH_DISTANCE);
			int z = (int) Math.floor(p.getZ() + Math.sin(a) * FRESH_DISTANCE);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos below = new BlockPos(x, y - 1, z);
			if (y > level.getMinY() && level.getFluidState(below).isEmpty() && !level.getBlockState(below).isAir()) {
				best = y; bx = x + 0.5; bz = z + 0.5; by = y;
			}
		}
		return new CharacterData.Slot(true, level.dimension().identifier().toString(), bx, by, bz, p.getYRot() + 180f, 0f,
				p.getMaxHealth(), 20, 5f, 0, List.of());
	}

	public static void setOutfit(ServerPlayer p, int index) {
		Appearance a = Appearance.get(p);
		int c = a.character();
		int n = Outfits.forCharacter(c).size();
		if (index < 0 || index >= n) return;
		if (a.outfitIndex(c) == index) return;
		Appearance na = a.withOutfit(c, index);
		p.setAttached(Appearance.TYPE, na);
		fireOutfitChanged(p, na.outfit(), false);
	}

	static void fireOutfitChanged(ServerPlayer p, Outfits.Outfit o, boolean switched) {
		CharacterApi.OUTFIT_CHANGED.invoker().onOutfitChanged(p, o, switched);
		dev.leonida.core.api.CharacterEvents.outfitChanged(p);
	}

	// ------------------------------------------------------------ wardrobe access
	static void openedWardrobe(ServerPlayer p, BlockPos pos) {
		WARDROBE.put(p.getUUID(), pos.immutable());
		ServerPlayNetworking.send(p, new CharacterNet.OpenWardrobe(0));
	}

	static void handleSetOutfit(ServerPlayer p, int index) {
		BlockPos pos = WARDROBE.get(p.getUUID());
		boolean allowed = p.isCreative() || (pos != null && pos.distToCenterSqr(p.position()) < 64
				&& p.level().getBlockState(pos).getBlock() instanceof WardrobeBlock);
		if (allowed) setOutfit(p, index);
	}

	/** Keep Appearance and PlayerState agreeing (PlayerState.character is what the HUD reads). */
	static void syncOnJoin(ServerPlayer p) {
		int c = PlayerState.get(p).character();
		Appearance a = Appearance.get(p);
		if (a.character() != c || p.getAttached(Appearance.TYPE) == null) p.setAttached(Appearance.TYPE, a.withCharacter(c));
	}
}
