package dev.leonida.gameplay.npc;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Shared base for every Leonida human NPC (pedestrians, clerks, police). Holds the synced skin index
 * and a small synced "pose" the renderer reads (phone up, hands up, aiming).
 */
public abstract class LeonidaNpc extends PathfinderMob {
	public static final int POSE_NONE = 0, POSE_PHONE_CALL = 1, POSE_FILMING = 2, POSE_HANDS_UP = 3, POSE_AIMING = 4, POSE_COWER = 5;

	private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(LeonidaNpc.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> NPC_POSE = SynchedEntityData.defineId(LeonidaNpc.class, EntityDataSerializers.INT);

	protected LeonidaNpc(EntityType<? extends LeonidaNpc> type, Level level) {
		super(type, level);
		if (!level.isClientSide()) setSkin(getRandom().nextInt(1 << 16));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(SKIN, 0);
		b.define(NPC_POSE, POSE_NONE);
	}

	public int skin() { return entityData.get(SKIN); }
	public void setSkin(int s) { entityData.set(SKIN, s); }
	public int npcPose() { return entityData.get(NPC_POSE); }
	public void setNpcPose(int p) { if (npcPose() != p) entityData.set(NPC_POSE, p); }

	/** Which skin family the client renderer should use. */
	public abstract SkinFamily skinFamily();

	public enum SkinFamily { PEDESTRIAN, CLERK, POLICE, SWAT }

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
		super.addAdditionalSaveData(out);
		out.putInt("leonida_skin", skin());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		super.readAdditionalSaveData(in);
		setSkin(in.getIntOr("leonida_skin", skin()));
	}
}
