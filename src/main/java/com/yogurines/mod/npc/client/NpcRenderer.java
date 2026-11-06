package com.yogurines.mod.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yogurines.mod.npc.NpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/** Dibuja el NPC con el modelo de jugador (normal o de brazos finos), su skin y su nombre personalizado. */
public class NpcRenderer extends HumanoidMobRenderer<NpcEntity, NpcModel> {
    private final NpcModel wide;
    private final NpcModel slim;

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new NpcModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        wide = model;
        slim = new NpcModel(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public void render(NpcEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        NpcEntity.Look l = e.look();
        model = NpcSkins.slim(l) ? slim : wide;
        boolean sit = "sit".equals(l.pose);
        ps.pushPose();
        if (sit) ps.translate(0, -0.55 * l.scale, 0);
        super.render(e, yaw, pt, ps, buf, light);
        ps.popPose();
        if (l.showName && !l.name.isEmpty() && entityRenderDispatcher.distanceToSqr(e) < 32 * 32 && !e.isInvisible()) {
            float top = e.getBbHeight() + (sit ? -0.55f * l.scale : 0f) + 0.32f;
            Nameplate.render(e, l, top, ps, buf, entityRenderDispatcher.cameraOrientation(), getFont(), pt);
        }
    }

    @Override
    protected boolean shouldShowName(NpcEntity e) {
        return false;
    }

    @Override
    protected void scale(NpcEntity e, PoseStack ps, float pt) {
        float s = 0.9375f * e.look().scale;
        ps.scale(s, s, s);
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity e) {
        return NpcSkins.texture(e.look());
    }
}
