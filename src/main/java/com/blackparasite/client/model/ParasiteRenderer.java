package com.blackparasite.client.model;

import java.util.EnumMap;
import java.util.Map;

import com.blackparasite.BlackParasiteMod;
import com.blackparasite.client.ClientBridge;
import com.blackparasite.client.ClientState;
import com.blackparasite.config.ParasiteConfig;
import com.blackparasite.logic.ParasiteForm;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/** Rendu des formes Prédateur / Fantôme / Colosse / Abomination à la place du modèle de joueur. */
public final class ParasiteRenderer {

    private static final ResourceLocation EYE_TEX = new ResourceLocation(BlackParasiteMod.MODID, "textures/entity/eye.png");
    private static EntityModelSet lastSet;
    private static final Map<ParasiteForm, ParasiteModel> MODELS = new EnumMap<>(ParasiteForm.class);
    private static ModelPart eyePart;

    private ParasiteRenderer() {}

    private static ParasiteModel model(ParasiteForm form) {
        EntityModelSet set = Minecraft.getInstance().getEntityModels();
        if (set != lastSet) { // rechargement des ressources : on rebake
            MODELS.clear();
            lastSet = set;
            eyePart = set.bakeLayer(ParasiteModel.EYE_LAYER).getChild("eye");
        }
        return MODELS.computeIfAbsent(form, f -> new ParasiteModel(f, set));
    }

    public static ResourceLocation texture(ParasiteForm form, int stage) {
        String name = switch (form) {
            case FANTOME -> "ghost";
            case COLOSSE -> "colossus";
            case ABOMINATION -> "abomination";
            default -> "predator";
        };
        return new ResourceLocation(BlackParasiteMod.MODID, "textures/entity/parasite_" + name + (stage >= 5 ? "_veined" : "") + ".png");
    }

    public static ParasiteModel.Pose poseFor(AbstractClientPlayer p, float pt, int stage, float animTOverride, int animOverride) {
        ParasiteModel.Pose s = new ParasiteModel.Pose();
        s.age = p.tickCount + pt;
        s.limbSwing = p.walkAnimation.position(pt);
        s.limbSwingAmount = p.walkAnimation.speed(pt);
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        s.headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot) - bodyYaw;
        s.headPitch = Mth.lerp(pt, p.xRotO, p.getXRot());
        s.hurt = p.hurtTime > 0 ? Mth.clamp(p.hurtTime - pt, 0f, 10f) / 10f : 0f;
        s.attack = p.getAttackAnim(pt);
        s.airborne = !p.onGround() && !p.isInWater() && !p.getAbilities().flying;
        s.verticalSpeed = (float) p.getDeltaMovement().y;
        s.crouching = p.isCrouching();
        s.stage = stage;
        s.advanced = ParasiteConfig.CLIENT.advancedAnimations.get();
        s.hungry = p.getUUID().equals(Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID())
                && ClientState.DATA.hunger < 25f;
        if (animOverride >= 0) {
            s.anim = animOverride;
            s.animT = animTOverride;
        } else {
            ClientState.Anim an = ClientState.ANIMS.get(p.getUUID());
            if (an != null) {
                s.anim = an.id();
                s.animT = (ClientState.clientTick + pt - an.start()) / (float) ClientState.animDuration(an.id());
                if (s.animT > 1f) s.anim = -1;
            }
        }
        return s;
    }

    /** @return vrai si ce rendu remplace celui du joueur (l'appelant annule alors le rendu vanilla). */
    public static boolean renderPlayer(AbstractClientPlayer p, ClientBridge.Visual v, float pt, PoseStack ps,
                                       MultiBufferSource buffers, int light) {
        ParasiteForm form = v.parasiteForm();
        if (!ParasiteModel.hasCustomBody(form)) return false;
        ParasiteModel model = model(form);
        ParasiteModel.Pose pose = poseFor(p, pt, v.stage(), 0f, -1);
        model.animate(pose);

        ps.pushPose();
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        if (p.deathTime > 0) {
            float f = Mth.sqrt((p.deathTime + pt - 1f) / 20f * 1.6f);
            ps.mulPose(Axis.ZP.rotationDegrees(Math.min(f, 1f) * 90f));
        }
        ps.scale(-1f, -1f, 1f);
        float sc = form.visualScale * model.bodyScale(pose);
        ps.scale(sc, sc, sc);
        ps.translate(0f, -1.501f, 0f);

        boolean camo = v.camouflage();
        float alpha = camo ? 0.12f + 0.05f * Mth.sin(pose.age * 0.2f) : (form == ParasiteForm.FANTOME ? 0.92f : 1f);
        float tint = v.carapace() ? 0.72f : 1f;
        RenderType rt = (camo || form == ParasiteForm.FANTOME)
                ? RenderType.entityTranslucent(texture(form, v.stage()))
                : RenderType.entityCutoutNoCull(texture(form, v.stage()));
        int overlay = LivingEntityRenderer.getOverlayCoords(p, 0f);
        model.render(ps, buffers.getBuffer(rt), light, overlay, tint, tint * (v.carapace() ? 0.85f : 1f), tint, alpha);

        // Yeux : couleur de la forme qui blanchit avec l'évolution, jamais atténuée par la lumière
        float mix = Math.min(0.7f, v.stage() * 0.06f);
        float r = Mth.lerp(mix, ((form.eyeColor >> 16) & 255) / 255f, 1f);
        float g = Mth.lerp(mix, ((form.eyeColor >> 8) & 255) / 255f, 1f);
        float b = Mth.lerp(mix, (form.eyeColor & 255) / 255f, 1f);
        model.renderEyes(ps, buffers.getBuffer(RenderType.eyes(EYE_TEX)), eyePart, model.eyeScale(pose), r, g, b, camo ? 0.3f : 1f);
        ps.popPose();

        renderNameTag(p, ps, buffers, light);
        return true;
    }

    /** Le rendu vanilla étant annulé, on redessine la plaque de nom (comme EntityRenderer.renderNameTag). */
    private static void renderNameTag(AbstractClientPlayer p, PoseStack ps, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.getCameraEntity() || !Minecraft.renderNames() || p.isDiscrete()) return;
        if (mc.getEntityRenderDispatcher().distanceToSqr(p) > 4096.0) return;
        Component name = p.getDisplayName();
        Font font = mc.font;
        ps.pushPose();
        ps.translate(0f, p.getBbHeight() + 0.7f, 0f);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f m = ps.last().pose();
        int bg = (int) (mc.options.getBackgroundOpacity(0.25f) * 255f) << 24;
        float x = -font.width(name) / 2f;
        font.drawInBatch(name, x, 0f, 0x20FFFFFF, false, m, buffers, Font.DisplayMode.SEE_THROUGH, bg, light);
        font.drawInBatch(name, x, 0f, 0xFFFFFFFF, false, m, buffers, Font.DisplayMode.NORMAL, 0, light);
        ps.popPose();
    }

    /** Ambiance : particules sombres autour des porteurs (limitées, selon la densité du client). */
    public static void tickParticles(AbstractClientPlayer p, ClientBridge.Visual v) {
        if (!p.level().isClientSide || p.tickCount % 4 != 0) return;
        if (Minecraft.getInstance().isPaused()) return;
        int stage = v.stage();
        ParasiteForm form = v.parasiteForm();
        if (form == null) return;
        var rnd = p.getRandom();
        double h = p.getBbHeight();
        if (rnd.nextFloat() < 0.25f + stage * 0.05f + form.ordinal() * 0.08f) {
            p.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    p.getX() + (rnd.nextDouble() - 0.5) * 0.9, p.getY() + rnd.nextDouble() * h, p.getZ() + (rnd.nextDouble() - 0.5) * 0.9,
                    0, 0.03, 0);
        }
        if (stage >= 4 && rnd.nextFloat() < 0.15f) {
            p.level().addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.55f, 0.02f, 0.05f), 1.0f),
                    p.getX() + (rnd.nextDouble() - 0.5), p.getY() + rnd.nextDouble() * h, p.getZ() + (rnd.nextDouble() - 0.5), 0, 0.02, 0);
        }
        if (form == ParasiteForm.ABOMINATION && rnd.nextFloat() < 0.2f) {
            p.level().addParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    p.getX() + (rnd.nextDouble() - 0.5), p.getY() + rnd.nextDouble() * h, p.getZ() + (rnd.nextDouble() - 0.5), 0, 0.02, 0);
        }
    }

    /** Rendu du modèle dans un écran (menu d'accueil). {@code x,y} = point au sol, {@code scale} = pixels par bloc. */
    public static void renderInGui(net.minecraft.client.gui.GuiGraphics gg, ParasiteForm form, int stage, float x, float y,
                                   float scale, float lookYaw, float lookPitch, float age, int anim, float animT) {
        if (!ParasiteModel.hasCustomBody(form)) form = ParasiteForm.PREDATEUR;
        ParasiteModel model = model(form);
        ParasiteModel.Pose s = new ParasiteModel.Pose();
        s.age = age;
        s.stage = stage;
        s.headYaw = lookYaw;
        s.headPitch = lookPitch;
        s.advanced = ParasiteConfig.CLIENT.advancedAnimations.get();
        s.anim = anim;
        s.animT = animT;
        model.animate(s);

        PoseStack ps = gg.pose();
        ps.pushPose();
        ps.translate(x, y, 150f);
        ps.scale(scale, scale, -scale);
        ps.mulPose(Axis.YP.rotationDegrees(-18f + 10f * Mth.sin(age * 0.03f)));
        float sc = form.visualScale * model.bodyScale(s);
        ps.scale(sc, sc, sc);
        ps.translate(0f, -1.5f, 0f);
        net.minecraft.client.renderer.Lighting.setupForEntityInInventory();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        MultiBufferSource.BufferSource buf = gg.bufferSource();
        int full = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        model.render(ps, buf.getBuffer(RenderType.entityTranslucent(texture(form, stage))), full,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        float mix = Math.min(0.7f, stage * 0.06f);
        float r = Mth.lerp(mix, ((form.eyeColor >> 16) & 255) / 255f, 1f);
        float g = Mth.lerp(mix, ((form.eyeColor >> 8) & 255) / 255f, 1f);
        float b = Mth.lerp(mix, (form.eyeColor & 255) / 255f, 1f);
        model.renderEyes(ps, buf.getBuffer(RenderType.eyes(EYE_TEX)), eyePart, model.eyeScale(s), r, g, b, 1f);
        buf.endBatch();
        ps.popPose();
        net.minecraft.client.renderer.Lighting.setupFor3DItems();
    }
}
