package dev.leonida.weapon.shop;

import dev.leonida.weapon.GunType;
import dev.leonida.weapon.WeaponItems;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * GTA style weapon pickup: a gun (or ammo, melee, armor) that floats and spins until someone walks
 * through it. Guns you already carry turn into ammo. Dropped ones vanish after five minutes;
 * persistent ones (placed by builders or commands) stay.
 */
public class WeaponPickupEntity extends Entity {
	private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(WeaponPickupEntity.class, EntityDataSerializers.ITEM_STACK);
	public static final int LIFETIME = 20 * 60 * 5;
	private boolean persistent;
	private int age;

	public WeaponPickupEntity(EntityType<? extends WeaponPickupEntity> type, Level level) {
		super(type, level);
	}

	/** Spawns a pickup. Persistent ones never despawn. */
	public static WeaponPickupEntity drop(ServerLevel level, Vec3 pos, ItemStack stack, boolean persistent) {
		WeaponPickupEntity e = new WeaponPickupEntity(WeaponShop.PICKUP, level);
		e.setPos(pos);
		e.setItem(stack.copy());
		e.persistent = persistent;
		e.setDeltaMovement(0, persistent ? 0 : 0.2, 0);
		level.addFreshEntity(e);
		return e;
	}

	public ItemStack getItem() { return entityData.get(DATA_ITEM); }
	public void setItem(ItemStack s) { entityData.set(DATA_ITEM, s); }

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		b.define(DATA_ITEM, ItemStack.EMPTY);
	}

	@Override
	protected double getDefaultGravity() {
		return 0.04;
	}

	@Override
	public void tick() {
		super.tick();
		if (!onGround() || getDeltaMovement().lengthSqr() > 1e-5) {
			applyGravity();
			move(MoverType.SELF, getDeltaMovement());
			setDeltaMovement(getDeltaMovement().multiply(0.6, 0.98, 0.6));
		}
		if (!(level() instanceof ServerLevel sl)) return;
		if (getItem().isEmpty() || (!persistent && ++age > LIFETIME)) { discard(); return; }
		if (tickCount < 10) return;
		for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(0.5, 0.3, 0.5), pl -> pl.isAlive() && !pl.isSpectator())) {
			collect(sl, p);
			return;
		}
	}

	private void collect(ServerLevel sl, ServerPlayer p) {
		ItemStack s = getItem();
		GunType t = WeaponItems.gunOf(s);
		if (t != null) {
			if (!Shop.owns(p, t)) {
				ItemStack gun = s.copyWithCount(1);
				gun.set(WeaponItems.CLIP, t.magazine);
				Shop.give(p, gun);
			}
			Shop.give(p, new ItemStack(WeaponItems.AMMO.get(t), Shop.packSize(t)));
		} else {
			Shop.give(p, s.copy());
		}
		sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.9f, 1.2f);
		discard();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		if (!getItem().isEmpty()) out.store("Item", ItemStack.CODEC, getItem());
		out.putBoolean("Persistent", persistent);
		out.putInt("Age", age);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		setItem(in.read("Item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
		persistent = in.getBooleanOr("Persistent", true);
		age = in.getIntOr("Age", 0);
	}

}
