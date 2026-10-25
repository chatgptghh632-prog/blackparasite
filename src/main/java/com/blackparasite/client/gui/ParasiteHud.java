package com.blackparasite.client.gui;

import com.blackparasite.client.ClientState;
import com.blackparasite.config.ParasiteConfig;
import com.blackparasite.data.ParasiteData;
import com.blackparasite.logic.ParasiteAbility;
import com.blackparasite.logic.ParasiteForm;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD du parasite : icône de forme, santé, évolution (animée), faim, énergie, stade, biomasse, indicateur de
 * transformation, barre de capacités avec recharge, alertes (« ÉVOLUTION DÉTECTÉE ») et flux de gains.
 * Position, taille, opacité et chaque élément sont configurables (menu Configuration).
 */
public class ParasiteHud implements IGuiOverlay {

    @Override
    public void render(ForgeGui gui, GuiGraphics gg, float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        ParasiteData d = ClientState.DATA;
        if (!d.infected || mc.player == null || mc.options.hideGui) return;
        var cfg = ParasiteConfig.CLIENT;
        if (!cfg.hudEnabled.get()) return;
        Font font = mc.font;
        float op = cfg.hudOpacity.get().floatValue();
        float scale = cfg.hudScale.get().floatValue();
        ClientState.shownHealth = Mth.lerp(0.3f, ClientState.shownHealth, mc.player.getHealth());
        ParasiteForm form = d.currentForm();
        if (form == null) return;

        // ----- panneau principal, ancré en bas à gauche -----
        int pw = 158;
        int rows = 1 + (cfg.showHealth.get() ? 1 : 0) + (cfg.showEvolution.get() ? 1 : 0) + (cfg.showHunger.get() ? 1 : 0)
                + (cfg.showEnergy.get() ? 1 : 0) + (cfg.showBiomass.get() ? 1 : 0);
        int ph = 30 + (rows - 1) * 12 + (cfg.showAbility.get() ? 30 : 0) + 4;
        var pose = gg.pose();
        pose.pushPose();
        pose.translate(cfg.hudOffsetX.get(), sh - cfg.hudOffsetY.get() - ph * scale, 0);
        pose.scale(scale, scale, 1f);
        RenderSystem.enableBlend();

        Ui.panel(gg, 0, 0, pw, ph, 0.85f * op);
        // icône de forme + nom + stade
        gg.setColor(1f, 1f, 1f, op);
        Ui.icon(gg, Ui.formIcon(form), 4, 4, 22);
        gg.setColor(1f, 1f, 1f, 1f);
        gg.drawString(font, form.displayName, 30, 5, Ui.alpha(Ui.TEXT, op), false);
        gg.drawString(font, "Niv. " + d.level + "  ·  Stade " + Math.min(10, d.level / 10), 30, 16, Ui.alpha(Ui.TEXT_DIM, op), false);

        int y = 28;
        int bx = 22, bw = pw - 28;
        if (cfg.showHealth.get()) {
            row(gg, "icon_health", y);
            Ui.bar(gg, bx, y + 2, bw, 6, ClientState.shownHealth / Math.max(1f, mc.player.getMaxHealth()), 0xFFC02030, 0xFF5A0A16, op);
            y += 12;
        }
        if (cfg.showEvolution.get()) {
            float fill = ClientState.shownXp;
            int flash = ClientState.progressFlash;
            int a = flash > 0 ? Ui.lerpColor(Ui.RED_BRIGHT, 0xFFFFFFFF, flash / 24f) : Ui.RED_BRIGHT;
            gg.drawString(font, "EVO", 3, y + 1, Ui.alpha(Ui.TEXT_DIM, op), false);
            Ui.bar(gg, bx, y + 2, bw, 6, fill, a, Ui.RED_DARK, op);
            if (flash > 0) gg.fill(bx, y + 2, bx + Math.round(bw * fill), y + 8, Ui.alpha(0xFFFFFFFF, 0.25f * flash / 24f * op));
            y += 12;
        }
        if (cfg.showHunger.get()) {
            row(gg, "icon_hunger", y);
            boolean low = d.hunger < 25f;
            float pulse = low ? 0.6f + 0.4f * Mth.sin(ClientState.clientTick * 0.4f) : 1f;
            Ui.bar(gg, bx, y + 2, bw, 6, ClientState.shownHunger / 100f, low ? Ui.RED_BRIGHT : 0xFFB07A2A, 0xFF4A2A0A, op * pulse);
            y += 12;
        }
        if (cfg.showEnergy.get()) {
            row(gg, "icon_energy", y);
            Ui.bar(gg, bx, y + 2, bw, 6, ClientState.shownEnergy / Math.max(1f, d.maxEnergy()), 0xFFFFC04A, 0xFF8A4A10, op);
            y += 12;
        }
        if (cfg.showBiomass.get()) {
            row(gg, "icon_biomass", y);
            gg.drawString(font, "Biomasse " + d.biomass + "   (total " + d.totalBiomass + ")", bx, y + 1, Ui.alpha(0xFFD9A8FF, op), false);
            y += 12;
        }
        if (cfg.showAbility.get()) {
            int ax = 4;
            ParasiteAbility[] all = ParasiteAbility.values();
            for (ParasiteAbility a : all) {
                if (d.level < a.minLevel || !a.allowedIn(form) || !ParasiteConfig.COMMON.abilityEnabled.get(a).get()) continue;
                int cd = d.cooldownRemaining[a.ordinal()];
                boolean sel = a.ordinal() == ClientState.selectedAbility;
                boolean afford = d.energy >= a.energyCost * ParasiteConfig.COMMON.energyCostMultiplier.get();
                gg.fill(ax - 1, y + 2, ax + 17, y + 20, sel ? Ui.alpha(Ui.GOLD, op) : Ui.alpha(Ui.STEEL_DARK, op));
                gg.setColor(1f, 1f, 1f, (cd > 0 || !afford ? 0.4f : 1f) * op);
                Ui.icon(gg, Ui.abilityIcon(a), ax, y + 3, 16);
                gg.setColor(1f, 1f, 1f, 1f);
                if (cd > 0) { // jauge de récupération
                    int h = Math.round(16 * (cd / (float) a.cooldownTicks));
                    gg.fill(ax, y + 3, ax + 16, y + 3 + h, Ui.alpha(0xCC000000, op));
                }
                ax += 19;
                if (ax > pw - 18) break;
            }
        }
        // indicateur de transformation disponible
        boolean canTransform = false;
        for (ParasiteForm f : ParasiteForm.values()) if (d.hasForm(f) && d.form != f.ordinal()) canTransform = true;
        if (canTransform && d.energy >= 10f) {
            float p = 0.6f + 0.4f * Mth.sin(ClientState.clientTick * 0.25f);
            gg.drawString(font, "▲ TRANSFORMATION (G)", 4, ph + 2, Ui.alpha(Ui.GOLD, p * op), false);
        }
        pose.popPose();

        // ----- effets d'état -----
        if (d.hunger < 10f) {
            float p = 0.15f + 0.1f * Mth.sin(ClientState.clientTick * 0.3f);
            gg.fillGradient(0, 0, sw, 30, Ui.alpha(0xFF700010, p), 0);
            gg.fillGradient(0, sh - 30, sw, sh, 0, Ui.alpha(0xFF700010, p));
        }
        if (d.camoTicks > 0) gg.drawString(font, "CAMOUFLAGE " + Ui.ticksToSeconds(d.camoTicks), sw / 2 - 40, sh - 62, Ui.alpha(0xFF9AE0FF, 0.9f), false);
        if (d.carapaceTicks > 0) gg.drawString(font, "CARAPACE " + Ui.ticksToSeconds(d.carapaceTicks), sw / 2 - 36, sh - 72, Ui.alpha(Ui.STEEL, 0.9f), false);

        if (!cfg.showAlerts.get()) return;
        // ----- gros message d'évolution -----
        ClientState.Alert big = ClientState.bigAlert;
        if (big != null) {
            float t = (ClientState.clientTick + pt - big.start()) / (float) big.duration();
            float in = Mth.clamp(t / 0.12f, 0f, 1f), out = Mth.clamp((1f - t) / 0.2f, 0f, 1f);
            float a = Math.min(in, out);
            float s = 1.5f + 2.2f * (1f - in) * 0 + 1.2f * in;
            pose.pushPose();
            pose.translate(sw / 2f, sh * 0.2f, 0);
            pose.scale(s, s, 1f);
            gg.fill(-font.width(big.title()) / 2 - 8, -6, font.width(big.title()) / 2 + 8, 11, Ui.alpha(0xFF000000, 0.55f * a));
            gg.drawString(font, big.title(), -font.width(big.title()) / 2, 0, Ui.alpha(Ui.RED_BRIGHT, a), false);
            pose.popPose();
            pose.pushPose();
            pose.translate(sw / 2f, sh * 0.2f + 26, 0);
            pose.scale(2f, 2f, 1f);
            gg.drawString(font, big.subtitle(), -font.width(big.subtitle()) / 2, 0, Ui.alpha(0xFFFFFFFF, a), false);
            pose.popPose();
        }
        // ----- flux de notifications -----
        int ty = sh / 2 - 40;
        for (ClientState.Alert al : ClientState.TOASTS) {
            float age = ClientState.clientTick + pt - al.start();
            float a = Mth.clamp(Math.min(age / 6f, (al.duration() - age) / 15f), 0f, 1f);
            int color = switch (al.kind()) {
                case com.blackparasite.network.packet.NotifyPacket.GAIN -> 0xFFD9A8FF;
                case com.blackparasite.network.packet.NotifyPacket.WARNING -> Ui.RED_BRIGHT;
                case com.blackparasite.network.packet.NotifyPacket.MISSION -> Ui.GOLD;
                default -> Ui.TEXT;
            };
            int w = Math.max(font.width(al.title()), font.width(al.subtitle())) + 12;
            int x = sw - w - 8 + Math.round((1f - Mth.clamp(age / 6f, 0f, 1f)) * 40f);
            gg.fill(x, ty, x + w, ty + 24, Ui.alpha(0xFF0A070C, 0.8f * a));
            gg.fill(x, ty, x + 2, ty + 24, Ui.alpha(color, a));
            gg.drawString(font, al.title(), x + 6, ty + 3, Ui.alpha(color, a), false);
            gg.drawString(font, al.subtitle(), x + 6, ty + 14, Ui.alpha(Ui.TEXT_DIM, a), false);
            ty += 28;
        }
    }

    private static void row(GuiGraphics gg, String icon, int y) {
        Ui.icon(gg, Ui.tex(icon), 3, y, 10);
    }
}
