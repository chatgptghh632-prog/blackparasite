package com.blackparasite.client.gui;

import com.blackparasite.ModSounds;
import com.blackparasite.client.ClientPacketHandler;
import com.blackparasite.client.ClientState;
import com.blackparasite.config.ParasiteConfig;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Base de tous les écrans : fond de laboratoire extraterrestre (panneaux métalliques, fumée animée, vignette rouge),
 * titre glitché, et un œil géant dont la pupille suit le curseur : le parasite observe le joueur.
 */
public abstract class ParasiteScreen extends Screen {
    protected final Screen parent;
    protected int ticks = 0;
    private float[][] fog;

    protected ParasiteScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (fog == null) {
            var r = new java.util.Random(42);
            fog = new float[36][5];
            for (float[] f : fog) { f[0] = r.nextFloat(); f[1] = r.nextFloat(); f[2] = 0.05f + r.nextFloat() * 0.15f; f[3] = 8 + r.nextFloat() * 22; f[4] = r.nextFloat() * 6.28f; }
        }
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /** Opacité d'apparition (0..1) pour les transitions progressives. */
    protected float appear(float pt) {
        return Mth.clamp((ticks + pt) / 12f, 0f, 1f);
    }

    protected void drawBackdrop(GuiGraphics gg, int mx, int my, float pt) {
        float ap = appear(pt);
        gg.fillGradient(0, 0, width, height, 0xFF040306, 0xFF150A0F);
        // panneaux métalliques en filigrane
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.22f * ap);
        var panel = Ui.tex("lab_panel");
        for (int x = 0; x < width; x += 64) for (int y = 0; y < height; y += 64) gg.blit(panel, x, y, 0f, 0f, 64, 64, 64, 64);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        // fumée
        if (ParasiteConfig.CLIENT.advancedAnimations.get()) {
            float t = (ticks + pt) * 0.02f;
            for (float[] f : fog) {
                float x = ((f[0] + t * f[2] * 0.4f) % 1f) * width;
                float y = height - ((f[1] + t * f[2]) % 1f) * height;
                int s = (int) f[3];
                float a = 0.10f + 0.07f * Mth.sin(t * 3f + f[4]);
                gg.fill((int) x, (int) y, (int) x + s, (int) y + s, Ui.alpha(0xFF3A1018, a * ap));
            }
        }
        // vignette rouge sombre
        gg.fillGradient(0, 0, width, 40, Ui.alpha(0xFF3A0610, 0.55f), 0x00000000);
        gg.fillGradient(0, height - 50, width, height, 0x00000000, Ui.alpha(0xFF3A0610, 0.65f));
        // lueur qui bat comme un cœur
        float beat = 0.5f + 0.5f * Mth.sin((ticks + pt) * 0.12f);
        gg.fillGradient(0, 0, 10, height, Ui.alpha(0xFF7A0A18, 0.25f * beat), 0x00000000);
    }

    /** Œil géant en haut à droite : la pupille se déplace vers le curseur. */
    protected void drawWatchingEye(GuiGraphics gg, int cx, int cy, int mx, int my, float pt) {
        float ap = appear(pt);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, ap * 0.9f);
        gg.blit(Ui.tex("menu_eye"), cx - 48, cy - 32, 0f, 0f, 96, 64, 96, 64);
        float dx = mx - cx, dy = my - cy;
        float len = Math.max(1f, Mth.sqrt(dx * dx + dy * dy));
        float k = Math.min(1f, len / 260f);
        int px = Math.round(cx + dx / len * 20f * k) - 13;
        int py = Math.round(cy + dy / len * 11f * k) - 13;
        gg.blit(Ui.tex("menu_pupil"), px, py, 26, 26, 0f, 0f, 32, 32, 32, 32);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Titre avec léger effet de glitch (désactivé si les animations avancées le sont). */
    protected void drawTitle(GuiGraphics gg, String text, int x, int y, float scale, float pt) {
        var pose = gg.pose();
        boolean glitch = ParasiteConfig.CLIENT.advancedAnimations.get() && (ticks % 90) < 4;
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);
        if (glitch) {
            gg.drawString(font, text, -2, 0, 0xAA00FFFF, false);
            gg.drawString(font, text, 2, 1, 0xAAFF0020, false);
        }
        gg.drawString(font, text, 1, 1, 0xFF000000, false);
        gg.drawString(font, text, 0, 0, Ui.lerpColor(Ui.RED, Ui.RED_BRIGHT, 0.5f + 0.5f * Mth.sin((ticks + pt) * 0.1f)), false);
        pose.popPose();
    }

    protected void playClick() {
        if (ParasiteConfig.CLIENT.uiSounds.get()) ClientPacketHandler.play(ModSounds.UI_CLICK.get(), 0.8f, 1f);
    }

    /** Ligne de statistique « libellé ... valeur ». */
    protected void statLine(GuiGraphics gg, int x, int y, int w, String label, String value) {
        gg.drawString(font, label, x, y, Ui.TEXT_DIM, false);
        gg.drawString(font, value, x + w - font.width(value), y, Ui.TEXT, false);
    }

    protected static boolean infected() {
        return ClientState.DATA.infected;
    }
}
