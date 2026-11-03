package dev.leonida.gameplay.client;

import dev.leonida.gameplay.npc.LeonidaNpc;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/** Player-shaped model (built from the player model layers, outer layer included) plus the hands-up pose. */
public class NpcModel extends HumanoidModel<NpcRenderState> {
	public NpcModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
	}

	@Override
	public void setupAnim(NpcRenderState state) {
		super.setupAnim(state);
		if (state.npcPose == LeonidaNpc.POSE_HANDS_UP) {
			float wobble = Mth.sin(state.ageInTicks * 0.3f) * 0.05f;
			rightArm.xRot = (float) Math.PI + 0.25f + wobble;
			leftArm.xRot = (float) Math.PI + 0.25f - wobble;
			rightArm.yRot = 0;
			leftArm.yRot = 0;
			rightArm.zRot = -0.35f;
			leftArm.zRot = 0.35f;
		}
	}
}
