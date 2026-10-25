package com.blackparasite.client.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.blackparasite.BlackParasiteMod;
import com.blackparasite.logic.ParasiteAbility;
import com.blackparasite.logic.ParasiteForm;
import com.blackparasite.network.packet.AnimPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Modèle 3D du parasite pour les formes Prédateur, Fantôme noir, Colosse et Abomination
 * (le Porteur garde le corps du joueur : voir {@link PorteurLayer}).
 * <p>
 * Chaque forme a sa propre silhouette, construite par {@link #build(Spec)} à partir d'un {@link Spec} :
 * torse, tête allongée avec mâchoire articulée et dents irrégulières, bras très longs en deux segments avec griffes,
 * jambes, chaînes de tentacules segmentées, pointes dorsales et cornes. Les yeux sont des cubes émissifs séparés,
 * dessinés avec RenderType.eyes (voir {@link #renderEyes}).
 * <p>
 * Toute l'animation (respiration, marche, course, saut, chute, attaque, coup de tentacule, absorption, transformation,
 * rugissement, dégâts) est calculée dans {@link #animate(Pose)} : aucune animation n'est un simple changement de texture.
 * La texture est un atlas 128x128 en 4 zones de 64x64 : A (0,0) torse, B (64,0) tête et membres,
 * C (0,64) tentacules, D (64,64) dents/griffes/pointes.
 */
public final class ParasiteModel {

    // ------------------------------------------------------------------ couches
    public static final ModelLayerLocation EYE_LAYER = layer("eye");
    public static final ModelLayerLocation EXTRAS_LAYER = layer("porteur_extras");
    private static final Map<ParasiteForm, ModelLayerLocation> BODY_LAYERS = new EnumMap<>(ParasiteForm.class);
    private static final Map<ParasiteForm, Spec> SPECS = new EnumMap<>(ParasiteForm.class);

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(new ResourceLocation(BlackParasiteMod.MODID, name), "main");
    }

    /** Spécification d'une forme (dimensions en pixels de modèle, 16 px = 1 bloc). */
    private record Spec(
            float bw, float bh, float bd,                 // torse (largeur, hauteur, profondeur)
            float hw, float hh, float hd,                 // tête
            float neckDrop,                               // enfoncement de la tête dans le torse
            Arm armR, Arm armL,                           // bras (asymétriques pour l'Abomination)
            float armOut,                                 // écart supplémentaire des épaules
            float legW, float legLen, float legSep,       // jambes
            float lean,                                   // inclinaison de repos du torse (rad)
            float[][] eyes,                               // yeux : {x, y, taille} dans le repère de la tête
            int teeth,                                    // dents par rangée
            int chains, int segs, float segLen, float segW, // tentacules
            int spikes, boolean horns, boolean pauldrons) {}

    private record Arm(float w, float upper, float fore, float claw) {}

    static {
        for (ParasiteForm f : new ParasiteForm[]{ParasiteForm.PREDATEUR, ParasiteForm.FANTOME, ParasiteForm.COLOSSE, ParasiteForm.ABOMINATION}) {
            BODY_LAYERS.put(f, layer("body_" + f.id));
        }
        // Prédateur : voûté, crâne allongé, bras énormes.
        SPECS.put(ParasiteForm.PREDATEUR, new Spec(
                10, 13, 6, 8, 11, 11, 1.5f,
                new Arm(3.4f, 13, 14, 5), new Arm(3.4f, 13, 14, 5), 0.5f,
                4.6f, 11, 2.7f, 0.40f,
                new float[][]{{-2.7f, -6.4f, 5.6f}, {2.7f, -6.4f, 5.6f}},
                7, 4, 4, 5.5f, 2.6f, 5, true, false));
        // Fantôme noir : fin, longs membres, six tentacules qui flottent.
        SPECS.put(ParasiteForm.FANTOME, new Spec(
                7, 14, 4, 7, 9, 9, 1f,
                new Arm(2.3f, 14, 15, 6), new Arm(2.3f, 14, 15, 6), 0.3f,
                3f, 10, 1.8f, 0.22f,
                new float[][]{{-2.1f, -5.2f, 5.0f}, {2.1f, -5.2f, 5.0f}},
                6, 6, 5, 6.2f, 1.9f, 3, false, false));
        // Colosse : torse massif, tête enfoncée, poings gigantesques.
        SPECS.put(ParasiteForm.COLOSSE, new Spec(
                16, 14, 10, 8, 7, 8, 3.5f,
                new Arm(6.8f, 13, 12, 4), new Arm(6.8f, 13, 12, 4), 0.8f,
                7f, 8, 4.2f, 0.12f,
                new float[][]{{-2.4f, -4.3f, 4.0f}, {2.4f, -4.3f, 4.0f}},
                5, 4, 3, 5.5f, 3.4f, 6, true, true));
        // Abomination : asymétrique, tête démesurée, six yeux, huit tentacules.
        SPECS.put(ParasiteForm.ABOMINATION, new Spec(
                12, 13, 8, 12, 10, 12, 1f,
                new Arm(3.2f, 17, 19, 7), new Arm(5.8f, 10, 11, 6), 1.2f,
                5.2f, 10, 3.2f, 0.30f,
                new float[][]{{-3.6f, -5.8f, 6.2f}, {3.6f, -5.8f, 6.2f},
                        {-5.2f, -8.2f, 3.0f}, {5.0f, -8.4f, 3.4f}, {0f, -9.0f, 3.6f}, {-1.2f, -2.8f, 2.6f}},
                9, 8, 5, 6f, 3.0f, 9, true, true));
    }

    public static ModelLayerLocation layerFor(ParasiteForm form) {
        return BODY_LAYERS.get(form);
    }

    public static boolean hasCustomBody(ParasiteForm form) {
        return form != null && BODY_LAYERS.containsKey(form);
    }

    /** Enregistre toutes les définitions de couches (appelé depuis l'événement du bus MOD). */
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (Map.Entry<ParasiteForm, ModelLayerLocation> e : BODY_LAYERS.entrySet()) {
            Spec spec = SPECS.get(e.getKey());
            event.registerLayerDefinition(e.getValue(), () -> build(spec));
        }
        event.registerLayerDefinition(EYE_LAYER, ParasiteModel::buildEye);
        event.registerLayerDefinition(EXTRAS_LAYER, PorteurLayer::buildExtras);
    }

    // ------------------------------------------------------------------ construction
    private static LayerDefinition buildEye() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("eye",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -4f, -1f, 8f, 8f, 2f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }

    private static LayerDefinition build(Spec s) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        float hipY = 24f - s.legLen();

        // Jambes
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -1f : 1f;
            root.addOrReplaceChild(side == 0 ? "leg_r" : "leg_l",
                    CubeListBuilder.create().texOffs(64, 0)
                            .addBox(-s.legW() / 2f, 0f, -s.legW() / 2f, s.legW(), s.legLen(), s.legW())
                            .texOffs(64, 40)
                            .addBox(-s.legW() / 2f - 0.5f, s.legLen() - 2f, -s.legW() / 2f - 2.5f, s.legW() + 1f, 2f, s.legW() + 2.5f), // pied
                    PartPose.offset(sx * s.legSep(), hipY, 0f));
        }

        // Torse (pivot à la hanche : l'inclinaison fait pivoter tout le haut du corps)
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-s.bw() / 2f, -s.bh(), -s.bd() / 2f, s.bw(), s.bh(), s.bd())
                        .texOffs(0, 32)
                        .addBox(-s.bw() * 0.32f, -s.bh() - 1.5f, -s.bd() * 0.3f, s.bw() * 0.64f, 2.5f, s.bd() * 0.8f), // bosse dorsale
                PartPose.offsetAndRotation(0f, hipY, 0f, s.lean(), 0f, 0f));

        // Tête : crâne + mâchoire articulée
        float jawH = s.hh() * 0.38f;
        float skullH = s.hh() - jawH;
        CubeListBuilder skull = CubeListBuilder.create().texOffs(64, 0)
                .addBox(-s.hw() / 2f, -s.hh(), -s.hd() / 2f, s.hw(), skullH, s.hd());
        // arcade sourcilière au-dessus des yeux
        skull.texOffs(64, 36).addBox(-s.hw() / 2f - 0.6f, -s.hh() - 0.8f, -s.hd() / 2f - 0.8f, s.hw() + 1.2f, 1.6f, 2.4f);
        // rangée de dents supérieures, longueurs irrégulières
        float[] lens = {2.2f, 3.4f, 1.6f, 3.0f, 2.0f, 3.6f, 1.8f, 2.8f, 2.4f, 3.2f};
        for (int i = 0; i < s.teeth(); i++) {
            float x = s.teeth() == 1 ? 0 : -s.hw() / 2f + 0.8f + i * (s.hw() - 2.2f) / (s.teeth() - 1);
            skull.texOffs(64, 64).addBox(x, -jawH, -s.hd() / 2f - 0.2f, 1f, lens[i % lens.length], 1f);
        }
        PartDefinition head = body.addOrReplaceChild("head", skull, PartPose.offset(0f, -s.bh() + s.neckDrop(), 0f));

        CubeListBuilder jaw = CubeListBuilder.create().texOffs(64, 18)
                .addBox(-s.hw() / 2f + 0.5f, 0f, -(s.hd() - 1.5f), s.hw() - 1f, jawH, s.hd() - 1f);
        for (int i = 0; i < s.teeth(); i++) {
            float x = s.teeth() == 1 ? 0 : -s.hw() / 2f + 1.3f + i * (s.hw() - 3.2f) / (s.teeth() - 1);
            jaw.texOffs(64, 64).addBox(x, -lens[(i + 3) % lens.length] * 0.9f, -(s.hd() - 1.5f) + 0.2f, 1f, lens[(i + 3) % lens.length] * 0.9f, 1f);
        }
        head.addOrReplaceChild("jaw", jaw, PartPose.offset(0f, -jawH, s.hd() / 2f - 1.5f));

        if (s.horns()) {
            for (int side = 0; side < 2; side++) {
                float sx = side == 0 ? -1f : 1f;
                head.addOrReplaceChild(side == 0 ? "horn_r" : "horn_l",
                        CubeListBuilder.create().texOffs(64, 64).addBox(-1f, -6f, -1f, 2f, 6f, 2f),
                        PartPose.offsetAndRotation(sx * s.hw() * 0.32f, -s.hh() + 0.5f, 0f, -0.3f, 0f, sx * -0.45f));
            }
        }

        // Épaulières du Colosse / de l'Abomination
        if (s.pauldrons()) {
            for (int side = 0; side < 2; side++) {
                float sx = side == 0 ? -1f : 1f;
                float aw = (side == 0 ? s.armR() : s.armL()).w();
                body.addOrReplaceChild(side == 0 ? "pauldron_r" : "pauldron_l",
                        CubeListBuilder.create().texOffs(0, 40).addBox(-aw * 0.8f, -3f, -aw * 0.8f, aw * 1.6f, 4f, aw * 1.6f),
                        PartPose.offsetAndRotation(sx * (s.bw() / 2f + aw / 2f + s.armOut() - 0.3f), -s.bh() + 0.5f, 0f, 0f, 0f, sx * -0.2f));
            }
        }

        // Bras : segment haut, avant-bras, trois griffes
        for (int side = 0; side < 2; side++) {
            float sx = side == 0 ? -1f : 1f;
            Arm a = side == 0 ? s.armR() : s.armL();
            PartDefinition arm = body.addOrReplaceChild(side == 0 ? "arm_r" : "arm_l",
                    CubeListBuilder.create().texOffs(64, 0).addBox(-a.w() / 2f, 0f, -a.w() / 2f, a.w(), a.upper(), a.w()),
                    PartPose.offsetAndRotation(sx * (s.bw() / 2f + a.w() / 2f - 0.5f + s.armOut()), -s.bh() + 2.5f, 0f, 0f, 0f, sx * -0.08f));
            float fw = a.w() * 0.9f;
            PartDefinition fore = arm.addOrReplaceChild("fore",
                    CubeListBuilder.create().texOffs(64, 0).addBox(-fw / 2f, 0f, -fw / 2f, fw, a.fore(), fw),
                    PartPose.offset(0f, a.upper(), 0f));
            fore.addOrReplaceChild("claws",
                    CubeListBuilder.create().texOffs(64, 64)
                            .addBox(-fw * 0.5f, 0f, -0.5f, 1f, a.claw(), 1f)
                            .addBox(-0.5f, 0f, -0.5f, 1f, a.claw() * 1.15f, 1f)
                            .addBox(fw * 0.5f - 1f, 0f, -0.5f, 1f, a.claw(), 1f),
                    PartPose.offsetAndRotation(0f, a.fore(), -fw * 0.2f, -0.4f, 0f, 0f));
        }

        // Pointes dorsales
        for (int i = 0; i < s.spikes(); i++) {
            float y = -s.bh() + 1f + i * ((s.bh() - 2f) / Math.max(1, s.spikes()));
            float len = 3f + (i % 3) * 1.4f;
            body.addOrReplaceChild("spike" + i,
                    CubeListBuilder.create().texOffs(64, 64).addBox(-0.8f, -len, -0.8f, 1.6f, len, 1.6f),
                    PartPose.offsetAndRotation(((i % 2) - 0.5f) * s.bw() * 0.25f, y, s.bd() / 2f - 0.2f, -0.9f, 0f, ((i % 2) - 0.5f) * 0.4f));
        }

        // Chaînes de tentacules (segments emboîtés, effilés)
        for (int c = 0; c < s.chains(); c++) {
            float side = (c % 2 == 0) ? -1f : 1f;
            int row = c / 2;
            int rows = (s.chains() + 1) / 2;
            float y = -s.bh() * 0.25f - row * (s.bh() * 0.6f / Math.max(1, rows));
            float x = side * (s.bw() * 0.28f + (row % 2) * 0.8f);
            PartDefinition prev = null;
            for (int j = 0; j < s.segs(); j++) {
                float w = Math.max(0.8f, s.segW() * (1f - j * 0.17f));
                CubeListBuilder cube = CubeListBuilder.create().texOffs(0, 64).addBox(-w / 2f, 0f, -w / 2f, w, s.segLen(), w);
                String name = "t" + c + "s" + j;
                if (j == 0) {
                    prev = body.addOrReplaceChild(name, cube, PartPose.offsetAndRotation(x, y, s.bd() / 2f - 0.5f, 1.15f, 0f, -side * 0.55f));
                } else {
                    prev = prev.addOrReplaceChild(name, cube, PartPose.offsetAndRotation(0f, s.segLen() - 0.4f, 0f, 0.22f, 0f, 0f));
                }
            }
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    // ------------------------------------------------------------------ pose d'animation
    /** Tout ce dont l'animation a besoin, rempli par le moteur de rendu à chaque image. */
    public static final class Pose {
        public float age;
        public float limbSwing, limbSwingAmount;
        public float headYaw, headPitch;   // degrés
        public float hurt;                 // 0..1
        public float attack;               // 0..1 (balancement vanilla du bras)
        public boolean airborne, crouching, hungry;
        public float verticalSpeed;
        public int anim = -1;
        public float animT;                // 0..1
        public int stage;                  // 0..10
        public boolean advanced = true;
    }

    // ------------------------------------------------------------------ instance
    public final ParasiteForm form;
    private final Spec spec;
    private final ModelPart root;
    private final ModelPart body, head, jaw, armR, armL, foreR, foreL, legR, legL;
    private final ModelPart[] spikes;
    private final ModelPart[][] chains;
    private final ModelPart[] pauldrons;

    public ParasiteModel(ParasiteForm form, EntityModelSet models) {
        this.form = form;
        this.spec = SPECS.get(form);
        this.root = models.bakeLayer(BODY_LAYERS.get(form));
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.jaw = head.getChild("jaw");
        this.armR = body.getChild("arm_r");
        this.armL = body.getChild("arm_l");
        this.foreR = armR.getChild("fore");
        this.foreL = armL.getChild("fore");
        this.legR = root.getChild("leg_r");
        this.legL = root.getChild("leg_l");
        this.spikes = new ModelPart[spec.spikes()];
        for (int i = 0; i < spikes.length; i++) spikes[i] = body.getChild("spike" + i);
        this.chains = new ModelPart[spec.chains()][spec.segs()];
        for (int c = 0; c < chains.length; c++) {
            ModelPart p = body;
            for (int j = 0; j < spec.segs(); j++) {
                p = p.getChild("t" + c + "s" + j);
                chains[c][j] = p;
            }
        }
        List<ModelPart> pl = new ArrayList<>();
        if (spec.pauldrons()) {
            pl.add(body.getChild("pauldron_r"));
            pl.add(body.getChild("pauldron_l"));
        }
        this.pauldrons = pl.toArray(new ModelPart[0]);
    }

    public void render(PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        root.render(ps, vc, light, overlay, r, g, b, a);
    }

    /** Dessine les yeux émissifs (couleur, taille et intensité selon le stade d'évolution et l'animation). */
    public void renderEyes(PoseStack ps, VertexConsumer eyeBuffer, ModelPart eyePart, float eyeScale, float r, float g, float b, float a) {
        ps.pushPose();
        body.translateAndRotate(ps);
        head.translateAndRotate(ps);
        for (float[] e : spec.eyes()) {
            ps.pushPose();
            ps.translate(e[0] / 16f, e[1] / 16f, (-spec.hd() / 2f - 0.15f) / 16f);
            float sc = e[2] / 8f * eyeScale;
            ps.scale(sc, sc, sc);
            eyePart.render(ps, eyeBuffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, a);
            ps.popPose();
        }
        ps.popPose();
    }

    /** Échelle des yeux : grandissent à l'apparition de la forme, battent lentement ensuite. */
    public float eyeScale(Pose s) {
        float pulse = s.advanced ? 1f + 0.05f * Mth.sin(s.age * 0.13f) : 1f;
        float appear = 1f;
        if (isTransformAnim(s.anim)) appear = Mth.clamp(smooth(s.animT * 1.7f), 0.05f, 1f);
        float vision = s.anim == A_VISION ? 1f + 0.45f * Mth.sin(s.animT * Mth.PI) : 1f;
        float stageGrow = 1f + s.stage * 0.035f;
        return pulse * appear * vision * stageGrow;
    }

    /** Pulsation globale du corps (contraction/dilatation pendant la transformation). */
    public float bodyScale(Pose s) {
        float breath = 1f + Mth.sin(s.age * 0.09f) * 0.012f;
        if (isTransformAnim(s.anim)) {
            float env = 1f - s.animT;
            return breath * (1f + 0.14f * Mth.sin(s.animT * Mth.PI * 5f) * env);
        }
        return breath;
    }

    // ------------------------------------------------------------------ animation
    private static final int A_ABSORB = ParasiteAbility.ABSORB.ordinal();
    private static final int A_CLAWS = ParasiteAbility.CLAWS.ordinal();
    private static final int A_WHIP = ParasiteAbility.WHIP.ordinal();
    private static final int A_LEAP = ParasiteAbility.LEAP.ordinal();
    private static final int A_ROAR = ParasiteAbility.ROAR.ordinal();
    private static final int A_CARAPACE = ParasiteAbility.CARAPACE.ordinal();
    private static final int A_VISION = ParasiteAbility.VISION.ordinal();
    private static final int A_REGEN = ParasiteAbility.REGEN.ordinal();
    private static final int A_CAMO = ParasiteAbility.CAMOUFLAGE.ordinal();
    private static final int A_BURST = ParasiteAbility.BURST.ordinal();
    private static final int A_ULTIMATE = ParasiteAbility.ULTIMATE.ordinal();

    private static boolean isTransformAnim(int a) {
        return a == AnimPacket.TRANSFORM || a == AnimPacket.EVOLVE || a == A_ULTIMATE;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    public void animate(Pose s) {
        root.getAllParts().forEach(ModelPart::resetPose);
        final float a = s.age;
        final float breath = Mth.sin(a * 0.09f);
        final float sw = s.limbSwing * 0.6662f;
        final float walk = Mth.clamp(s.limbSwingAmount, 0f, 1f);
        final float run = Mth.clamp((walk - 0.55f) / 0.45f, 0f, 1f);
        final float st = s.stage / 10f;
        final float amp = s.advanced ? 1f : 0.55f;
        final float t = Mth.clamp(s.animT, 0f, 1f);
        final float k = Mth.sin(t * Mth.PI); // enveloppe 0 → 1 → 0
        final boolean heavy = form == ParasiteForm.COLOSSE;

        // --- posture de base : plus voûté à mesure que le parasite évolue ---
        body.xRot += spec.lean() + st * 0.12f + walk * 0.08f + run * 0.22f + breath * 0.012f + (s.crouching ? 0.35f : 0f);
        body.y += breath * 0.35f + (s.crouching ? 1.5f : 0f);
        head.xRot = Mth.clamp(s.headPitch, -80f, 80f) * Mth.DEG_TO_RAD - body.xRot * 0.75f;
        head.yRot = Mth.clamp(s.headYaw, -75f, 75f) * Mth.DEG_TO_RAD;
        head.zRot = Mth.sin(a * 0.05f) * 0.025f + (s.hungry ? Mth.sin(a * 1.4f) * 0.035f : 0f);
        jaw.xRot = 0.10f + (breath * 0.5f + 0.5f) * 0.06f + s.attack * 0.45f;

        // --- marche / course ---
        legR.xRot = Mth.cos(sw) * 1.2f * walk;
        legL.xRot = -Mth.cos(sw) * 1.2f * walk;
        armR.xRot = -Mth.cos(sw) * 0.5f * walk + run * 0.7f + Mth.sin(a * 0.07f) * 0.03f;
        armL.xRot = Mth.cos(sw) * 0.5f * walk + run * 0.7f - Mth.sin(a * 0.07f) * 0.03f;
        armR.zRot += 0.02f + breath * 0.02f;
        armL.zRot -= 0.02f + breath * 0.02f;
        foreR.xRot = -0.3f - walk * 0.35f - run * 0.4f;
        foreL.xRot = -0.3f - walk * 0.35f - run * 0.4f;
        // balancement d'attaque vanilla (clic gauche)
        if (s.attack > 0f) {
            float sa = Mth.sin(s.attack * Mth.PI);
            armR.xRot += -1.8f * sa;
            body.yRot += -0.35f * sa;
        }

        // --- saut / chute ---
        if (s.airborne) {
            float up = s.verticalSpeed < 0 ? 1f : 0.5f;
            armR.xRot = -2.2f * up - 0.2f;
            armL.xRot = -2.2f * up - 0.2f;
            armR.zRot += 0.5f;
            armL.zRot -= 0.5f;
            legR.xRot = -0.5f;
            legL.xRot = 0.35f;
            jaw.xRot += s.verticalSpeed < -0.5f ? 0.5f : 0.15f;
        }

        // --- tentacules : mouvement organique, amplitude bornée ---
        for (int c = 0; c < chains.length; c++) {
            // Les chaînes supplémentaires n'apparaissent qu'avec l'évolution.
            boolean visible = c < Math.max(2, Math.round(chains.length * (0.5f + st * 0.5f)));
            for (int j = 0; j < chains[c].length; j++) {
                ModelPart seg = chains[c][j];
                seg.visible = visible;
                if (j == 0) {
                    seg.xRot += Mth.sin(a * 0.07f + c * 1.9f) * 0.18f * amp + walk * 0.25f;
                    seg.zRot += Mth.cos(a * 0.05f + c * 2.3f) * 0.12f * amp;
                } else {
                    seg.xRot += Mth.sin(a * 0.12f + c * 1.3f + j * 0.9f) * (0.16f + 0.04f * j) * amp;
                    seg.zRot += Mth.cos(a * 0.10f + c * 0.7f + j * 0.8f) * (0.14f + 0.03f * j) * amp;
                }
            }
        }
        // pointes et cornes : apparaissent par paliers d'évolution
        for (int i = 0; i < spikes.length; i++) spikes[i].visible = s.stage >= 1 + i * 10 / Math.max(1, spikes.length) * 0.6f;
        for (ModelPart p : pauldrons) p.visible = s.stage >= 2;

        // --- animations ponctuelles ---
        if (s.anim == A_CLAWS) {
            if (heavy) { // frappe lourde du Colosse : les deux bras s'abattent, impact au sol
                float up = smooth(t / 0.45f);
                float down = smooth((t - 0.45f) / 0.15f);
                float ang = Mth.lerp(down, -3.0f * up, 0.25f);
                armR.xRot = ang; armL.xRot = ang;
                body.xRot += 0.5f * down * (1f - smooth((t - 0.7f) / 0.3f)) - 0.2f * up * (1f - down);
                body.y += 2.5f * down * (1f - smooth((t - 0.65f) / 0.3f));
                jaw.xRot += 0.7f * down;
            } else {
                float wind = smooth(t / 0.3f);
                float slash = smooth((t - 0.3f) / 0.25f);
                float ang = Mth.lerp(slash, -2.6f * wind, 0.15f);
                if (t > 0.6f) ang = Mth.lerp(smooth((t - 0.6f) / 0.4f), 0.15f, armR.xRot);
                armR.xRot = ang;
                foreR.xRot = -0.5f * (1f - slash);
                armL.xRot += -0.6f * k;
                body.yRot += Mth.lerp(slash, 0.5f * wind, -0.45f) * (1f - smooth((t - 0.6f) / 0.4f));
                jaw.xRot += 0.5f * k;
            }
        } else if (s.anim == A_WHIP) {
            armR.xRot = -1.5f * k - 0.2f;
            body.yRot += -0.3f * k;
            for (int c = 0; c < Math.min(2, chains.length); c++) { // les deux premiers tentacules se tendent vers l'avant
                for (int j = 0; j < chains[c].length; j++) {
                    ModelPart seg = chains[c][j];
                    seg.xRot = Mth.lerp(k, seg.xRot, j == 0 ? -1.2f : -0.1f);
                    seg.zRot = Mth.lerp(k, seg.zRot, 0f);
                }
            }
            jaw.xRot += 0.4f * k;
        } else if (s.anim == A_LEAP) {
            float crouch = 1f - smooth((t - 0.25f) / 0.2f);
            float ext = smooth((t - 0.25f) / 0.25f) * (1f - smooth((t - 0.8f) / 0.2f));
            if (t < 0.45f) {
                body.y += 3f * crouch * smooth(t / 0.25f);
                legR.xRot = -0.9f * crouch; legL.xRot = -0.9f * crouch;
                armR.xRot = 0.8f * crouch; armL.xRot = 0.8f * crouch;
            }
            body.xRot += 0.45f * ext;
            armR.xRot = Mth.lerp(ext, armR.xRot, 1.1f);
            armL.xRot = Mth.lerp(ext, armL.xRot, 1.1f);
            for (ModelPart[] chain : chains) chain[0].xRot += 0.5f * ext;
        } else if (s.anim == A_ROAR) {
            float open = smooth(t / 0.2f) * (1f - smooth((t - 0.8f) / 0.2f));
            head.xRot -= 0.65f * open;
            jaw.xRot = 0.1f + 1.0f * open;
            armR.zRot += 1.2f * open; armL.zRot -= 1.2f * open;
            armR.xRot = -0.3f * open; armL.xRot = -0.3f * open;
            body.xRot -= 0.25f * open;
            body.zRot += Mth.sin(a * 2.6f) * 0.04f * open;
            for (ModelPart[] chain : chains) for (ModelPart seg : chain) seg.zRot += Mth.sin(a * 0.8f) * 0.25f * open;
        } else if (s.anim == A_CARAPACE) {
            armR.xRot = -1.1f * k; armL.xRot = -1.1f * k;
            armR.zRot -= 0.5f * k; armL.zRot += 0.5f * k;
            foreR.xRot = -1.4f * k; foreL.xRot = -1.4f * k;
            body.xRot += 0.3f * k;
            head.xRot += 0.3f * k;
        } else if (s.anim == A_VISION) {
            head.xRot -= 0.35f * k;
            jaw.xRot += 0.25f * k;
        } else if (s.anim == A_REGEN) {
            armR.xRot = -1.0f * k; armL.xRot = -1.0f * k;
            foreR.xRot = -1.7f * k; foreL.xRot = -1.7f * k;
            body.xRot += 0.2f * k + Mth.sin(a * 0.9f) * 0.03f * k;
        } else if (s.anim == A_CAMO) {
            body.y += 2.2f * k;
            legR.xRot -= 0.5f * k; legL.xRot -= 0.5f * k;
            body.xRot += 0.3f * k;
            for (ModelPart[] chain : chains) for (ModelPart seg : chain) seg.xRot += 0.3f * k;
        } else if (s.anim == A_BURST) {
            float up = smooth(t / 0.4f);
            armR.xRot = -3.0f * up * (1f - smooth((t - 0.8f) / 0.2f));
            armL.xRot = armR.xRot;
            jaw.xRot += 0.9f * k;
            head.xRot -= 0.4f * up;
            for (int c = 0; c < chains.length; c++) { // les tentacules rayonnent dans toutes les directions
                float dir = (c % 2 == 0 ? -1f : 1f);
                chains[c][0].zRot = Mth.lerp(up, chains[c][0].zRot, -dir * (0.9f + 0.1f * (c / 2)));
                chains[c][0].xRot = Mth.lerp(up, chains[c][0].xRot, 0.4f + 0.3f * (c / 2));
                for (int j = 1; j < chains[c].length; j++) chains[c][j].xRot = Mth.lerp(up, chains[c][j].xRot, 0.05f);
            }
        } else if (s.anim == A_ABSORB) {
            armR.xRot = -1.45f * k + armR.xRot * (1f - k);
            armL.xRot = -1.45f * k + armL.xRot * (1f - k);
            armR.zRot -= 0.35f * k * (0.6f + 0.4f * Mth.sin(a * 0.8f));
            armL.zRot += 0.35f * k * (0.6f + 0.4f * Mth.sin(a * 0.8f));
            head.xRot += 0.35f * k;
            jaw.xRot = 0.1f + (0.55f + 0.35f * Mth.sin(a * 0.9f)) * k;
            for (ModelPart[] chain : chains) {
                chain[0].xRot = Mth.lerp(k, chain[0].xRot, -0.7f);
                for (int j = 1; j < chain.length; j++) chain[j].xRot += -0.25f * k;
            }
        } else if (isTransformAnim(s.anim)) {
            float env = 1f - smooth(t);
            float shake = Mth.sin(a * 3.1f) * env;
            head.xRot -= 0.8f * smooth(t / 0.3f) * env;
            jaw.xRot = 0.1f + 0.9f * env * smooth(t / 0.2f);
            armR.zRot += 1.0f * env; armL.zRot -= 1.0f * env;
            armR.xRot = -0.4f * env; armL.xRot = -0.4f * env;
            body.xRot -= 0.3f * env;
            body.zRot += 0.07f * shake;
            body.y += Mth.cos(a * 2.3f) * 0.8f * env;
            legR.zRot += 0.05f * shake; legL.zRot -= 0.05f * shake;
            for (ModelPart[] chain : chains) for (int j = 0; j < chain.length; j++) chain[j].zRot += Mth.sin(a * 0.9f + j) * 0.35f * env;
        }

        // --- dégâts : sursaut ---
        if (s.hurt > 0f) {
            body.xRot -= 0.3f * s.hurt;
            head.xRot -= 0.35f * s.hurt;
            armR.zRot += 0.4f * s.hurt; armL.zRot -= 0.4f * s.hurt;
            body.zRot += Mth.sin(a * 4f) * 0.05f * s.hurt;
        }
    }

    /** Axe de rotation d'inclinaison utile au moteur de rendu (mort, etc.). */
    public static com.mojang.math.Axis deathAxis() {
        return Axis.ZP;
    }
}
