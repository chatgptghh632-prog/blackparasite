package com.blackparasite.client.gui;

import com.blackparasite.BlackParasiteMod;
import com.blackparasite.logic.ParasiteAbility;
import com.blackparasite.logic.ParasiteForm;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Outils de dessin et palette communs (noir profond, rouge sombre, gris métallique). */
public final class Ui {
    public static final int BLACK = 0xFF07060A;
    public static final int PANEL = 0xE0100C12;
    public static final int RED = 0xFFB01428;
    public static final int RED_DARK = 0xFF5A0A16;
    public static final int RED_BRIGHT = 0xFFFF3B3B;
    public static final int STEEL = 0xFF8A8F9C;
    public static final int STEEL_DARK = 0xFF2A2C34;
    public static final int TEXT = 0xFFD8D4DA;
    public static final int TEXT_DIM = 0xFF8C8690;
    public static final int GOLD = 0xFFFFC04A;

    private Ui() {}

    public static ResourceLocation tex(String path) {
        return new ResourceLocation(BlackParasiteMod.MODID, "textures/gui/" + path + ".png");
    }

    public static ResourceLocation abilityIcon(ParasiteAbility a) {
        return tex("icon_" + a.id);
    }

    public static ResourceLocation formIcon(ParasiteForm f) {
        return tex("icon_form_" + f.id);
    }

    /** Icône 64x64 réduite à size x size. */
    public static void icon(GuiGraphics gg, ResourceLocation t, int x, int y, int size) {
        gg.blit(t, x, y, size, size, 0f, 0f, 64, 64, 64, 64);
    }

    public static int alpha(int argb, float a) {
        int al = (int) (((argb >>> 24) & 255) * Mth.clamp(a, 0f, 1f));
        return (al << 24) | (argb & 0xFFFFFF);
    }

    public static int lerpColor(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Cadre métallique sombre avec liseré. */
    public static void panel(GuiGraphics gg, int x, int y, int w, int h, float opacity) {
        gg.fill(x, y, x + w, y + h, alpha(PANEL, opacity));
        gg.fill(x, y, x + w, y + 1, alpha(STEEL, 0.55f * opacity));
        gg.fill(x, y + h - 1, x + w, y + h, alpha(STEEL_DARK, opacity));
        gg.fill(x, y, x + 1, y + h, alpha(STEEL, 0.35f * opacity));
        gg.fill(x + w - 1, y, x + w, y + h, alpha(STEEL_DARK, opacity));
    }

    /** Barre organique : fond sombre, dégradé, reflet. {@code frac} 0..1. */
    public static void bar(GuiGraphics gg, int x, int y, int w, int h, float frac, int colorA, int colorB, float opacity) {
        frac = Mth.clamp(frac, 0f, 1f);
        gg.fill(x - 1, y - 1, x + w + 1, y + h + 1, alpha(0xFF000000, opacity));
        gg.fill(x, y, x + w, y + h, alpha(0xFF1A1418, opacity));
        int fw = Math.round(w * frac);
        if (fw > 0) {
            gg.fillGradient(x, y, x + fw, y + h, alpha(colorA, opacity), alpha(colorB, opacity));
            gg.fill(x, y, x + fw, y + 1, alpha(0x55FFFFFF, opacity));
        }
    }

    public static void centered(GuiGraphics gg, net.minecraft.client.gui.Font font, String s, int cx, int y, int color) {
        gg.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }

    public static String ticksToSeconds(int ticks) {
        return String.format("%.1f s", ticks / 20.0);
    }
}
