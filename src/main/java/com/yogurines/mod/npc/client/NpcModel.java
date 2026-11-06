package com.yogurines.mod.npc.client;

import com.yogurines.mod.npc.NpcEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Modelo de jugador con las poses de los NPCs (sentado, brazos cruzados, manos atrás, saludando). */
public class NpcModel extends PlayerModel<NpcEntity> {
    public NpcModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(NpcEntity e, float limbSwing, float limbAmount, float age, float headYaw, float headPitch) {
        String pose = e.look().pose;
        riding = "sit".equals(pose);
        super.setupAnim(e, limbSwing, limbAmount, age, headYaw, headPitch);
        switch (pose) {
            case "cross" -> {
                rightArm.xRot = -0.95f; rightArm.yRot = -0.78f; rightArm.zRot = 0f;
                leftArm.xRot = -0.95f; leftArm.yRot = 0.78f; leftArm.zRot = 0f;
            }
            case "behind" -> {
                rightArm.xRot = 0.55f; rightArm.yRot = 0f; rightArm.zRot = 0.12f;
                leftArm.xRot = 0.55f; leftArm.yRot = 0f; leftArm.zRot = -0.12f;
            }
            case "wave" -> {
                rightArm.xRot = 0f; rightArm.yRot = 0f;
                rightArm.zRot = 2.55f + Mth.sin(age * 0.35f) * 0.3f;
            }
            case "think" -> {
                rightArm.xRot = -2.0f; rightArm.yRot = -0.45f; rightArm.zRot = 0.1f;
                leftArm.xRot = -0.8f; leftArm.yRot = 0.7f; leftArm.zRot = 0f;
            }
            default -> {}
        }
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
        jacket.copyFrom(body);
        hat.copyFrom(head);
    }
}
