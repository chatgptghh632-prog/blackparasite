package com.blackparasite.client.model;

import com.blackparasite.BlackParasiteMod;
import com.blackparasite.client.ClientBridge;
import com.blackparasite.logic.ParasiteForm;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Forme 1 : LE PORTEUR. Le joueur garde sa silhouette ; le parasite la recouvre :
 * calque de taches noires, yeux lumineux, griffes naissantes, courts tentacules dorsaux et pointes aux épaules.
 * Le nombre de tentacules et de pointes augmente avec le stade d'évolution.
 */
public class PorteurLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public static final ResourceLocation OVERLAY = new ResourceLocation(BlackParasiteMod.MODID, "textures/entity/porteur_overlay.png");
    public static final ResourceLocation SKIN = new ResourceLocation(BlackParasiteMod.MODID, "textures/entity/parasite_predator.png");
    public static final ResourceLocation EYE = new ResourceLocation(BlackParasiteMod.MODID, "textures/entity/eye.png");

    private final ModelPart extras;
    private final ModelPart eye;

    public PorteurLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.extras = models.bakeLayer(ParasiteModel.EXTRAS_LAYER);
        this.eye = models.bakeLayer(ParasiteModel.EYE_LAYER).getChild("eye");
    }

    /** Pièces additionnelles : griffes, pointes, trois chaînes de tentacules de 3 segments. */
    static LayerDefinition buildExtras() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -1f : 1f;
            root.addOrReplaceChild(side == 0 ? "claw_r" : "claw_l",
                    CubeListBuilder.create().texOffs(64, 64)
                            .addBox(-1.5f, 0f, -2.6f, 1f, 4f, 1f)
                            .addBox(-0.5f, 0f, -2.6f, 1f, 4.6f, 1f)
                            .addBox(0.5f, 0f, -2.6f, 1f, 4f, 1f),
                    PartPose.offsetAndRotation(sx * 1f, 9.5f, 0f, -0.3f, 0f, 0f));
        }
        root.addOrReplaceChild("spikes",
                CubeListBuilder.create().texOffs(64, 64)
                        .addBox(-6f, -3f, -0.8f, 1.6f, 4f, 1.6f)
                        .addBox(4.4f, -3f, -0.8f, 1.6f, 4f, 1.6f)
                        .addBox(-3f, -1f, 2.2f, 1.4f, 3f, 1.4f)
                        .addBox(1.6f, -1f, 2.2f, 1.4f, 3f, 1.4f),
                PartPose.ZERO);
        float[][] bases = {{-2.5f, 3f, 2.3f}, {2.5f, 3f, 2.3f}, {0f, 8f, 2.3f}};
        for (int c = 0; c < 3; c++) {
            float side = bases[c][0] == 0 ? 1f : Math.signum(bases[c][0]);
            PartDefinition prev = null;
            for (int j = 0; j < 3; j++) {
                float w = 2.2f - j * 0.5f;
                CubeListBuilder cube = CubeListBuilder.create().texOffs(0, 64).addBox(-w / 2f, 0f, -w / 2f, w, 4.5f, w);
                if (j == 0) prev = root.addOrReplaceChild("t" + c, cube, PartPose.offsetAndRotation(bases[c][0], bases[c][1], bases[c][2], 1.2f, 0f, -side * 0.5f));
                else prev = prev.addOrReplaceChild("s" + j, cube, PartPose.offsetAndRotation(0f, 4.1f, 0f, 0.25f, 0f, 0f));
            }
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limbSwing,
                       float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
        ClientBridge.Visual v = ClientBridge.visual(p.getUUID());
        if (v == null || v.form() != ParasiteForm.PORTEUR.ordinal()) return;
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(p, 0f);
        float alpha = v.camouflage() ? 0.25f : 0.93f;

        // 1. Peau infectée par-dessus le corps du joueur
        model.renderToBuffer(ps, buffers.getBuffer(RenderType.entityTranslucent(OVERLAY)), light, overlay, 1f, 1f, 1f, alpha);

        int stage = v.stage();
        ParasiteForm form = ParasiteForm.PORTEUR;
        float mix = Math.min(0.6f, stage * 0.05f);
        float r = Mth.lerp(mix, ((form.eyeColor >> 16) & 255) / 255f, 1f);
        float g = Mth.lerp(mix, ((form.eyeColor >> 8) & 255) / 255f, 1f);
        float b = Mth.lerp(mix, (form.eyeColor & 255) / 255f, 1f);

        // 2. Yeux lumineux (grandissent avec l'évolution)
        ps.pushPose();
        model.head.translateAndRotate(ps);
        float size = (3.0f + stage * 0.18f) / 8f * (1f + 0.05f * Mth.sin(age * 0.13f));
        for (int side = -1; side <= 1; side += 2) {
            ps.pushPose();
            ps.translate(side * 2.0f / 16f, -4.1f / 16f, -4.2f / 16f);
            ps.scale(size, size, size);
            eye.render(ps, buffers.getBuffer(RenderType.eyes(EYE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, alpha);
            ps.popPose();
        }
        ps.popPose();

        var skin = buffers.getBuffer(RenderType.entityCutoutNoCull(SKIN));
        // 3. Griffes naissantes aux mains
        ps.pushPose();
        model.rightArm.translateAndRotate(ps);
        extras.getChild("claw_r").render(ps, skin, light, overlay, 1f, 1f, 1f, alpha);
        ps.popPose();
        ps.pushPose();
        model.leftArm.translateAndRotate(ps);
        extras.getChild("claw_l").render(ps, skin, light, overlay, 1f, 1f, 1f, alpha);
        ps.popPose();

        // 4. Pointes et tentacules dorsaux (animés, amplitude bornée)
        ps.pushPose();
        model.body.translateAndRotate(ps);
        ModelPart spikes = extras.getChild("spikes");
        spikes.visible = stage >= 3;
        spikes.render(ps, skin, light, overlay, 1f, 1f, 1f, alpha);
        for (int c = 0; c < 3; c++) {
            ModelPart t = extras.getChild("t" + c);
            t.visible = c == 0 || stage >= (c == 1 ? 2 : 4);
            t.resetPose();
            t.xRot += Mth.sin(age * 0.08f + c * 2f) * 0.15f + limbSwingAmount * 0.3f;
            t.zRot += Mth.cos(age * 0.06f + c * 1.7f) * 0.1f;
            ModelPart s1 = t.getChild("s1");
            ModelPart s2 = s1.getChild("s2");
            s1.resetPose();
            s2.resetPose();
            s1.xRot += Mth.sin(age * 0.12f + c) * 0.2f;
            s2.xRot += Mth.sin(age * 0.14f + c + 1f) * 0.25f;
            s2.zRot += Mth.cos(age * 0.11f + c) * 0.18f;
            t.render(ps, skin, light, overlay, 1f, 1f, 1f, alpha);
        }
        ps.popPose();
    }
}
