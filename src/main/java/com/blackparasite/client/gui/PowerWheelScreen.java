package com.blackparasite.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.blackparasite.client.ClientEvents;
import com.blackparasite.client.ClientState;
import com.blackparasite.config.ParasiteConfig;
import com.blackparasite.data.ParasiteData;
import com.blackparasite.logic.ParasiteAbility;
import com.blackparasite.logic.ParasiteForm;
import com.blackparasite.network.ModNetwork;
import com.blackparasite.network.packet.TransformPacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Roue de pouvoirs : un clic sur une capacité l'utilise, un clic sur une forme déclenche la transformation. */
public class PowerWheelScreen extends Screen {
    private record Slot(ParasiteAbility ability, ParasiteForm form) {}

    private final List<Slot> slots = new ArrayList<>();
    private int ticks = 0;

    public PowerWheelScreen() {
        super(Component.literal("Roue de pouvoirs"));
    }

    @Override
    protected void init() {
        super.init();
        ParasiteData d = ClientState.DATA;
        for (ParasiteAbility a : ParasiteAbility.values()) {
            if (d.level >= a.minLevel && a.allowedIn(d.currentForm()) && ParasiteConfig.COMMON.abilityEnabled.get(a).get()) slots.add(new Slot(a, null));
        }
        for (ParasiteForm f : ParasiteForm.values()) {
            if (d.hasForm(f) && d.form != f.ordinal()) slots.add(new Slot(null, f));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticks++;
    }

    private int hovered(double mx, double my) {
        int cx = width / 2, cy = height / 2;
        double dx = mx - cx, dy = my - cy;
        if (Math.sqrt(dx * dx + dy * dy) < 24 || slots.isEmpty()) return -1;
        double ang = Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / slots.size();
        ang = (ang % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        return (int) (ang / (Math.PI * 2) * slots.size()) % slots.size();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int h = hovered(mx, my);
        if (h >= 0) {
            Slot s = slots.get(h);
            if (s.ability() != null) ClientEvents.sendAbility(s.ability().ordinal());
            else ModNetwork.CHANNEL.sendToServer(new TransformPacket(s.form().ordinal()));
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        float ap = Mth.clamp((ticks + pt) / 6f, 0f, 1f);
        gg.fill(0, 0, width, height, Ui.alpha(0xFF000000, 0.55f * ap));
        int cx = width / 2, cy = height / 2;
        float radius = 78f * ap;
        int hov = hovered(mx, my);
        ParasiteData d = ClientState.DATA;
        gg.fill(cx - 22, cy - 22, cx + 22, cy + 22, Ui.alpha(Ui.PANEL, 0.9f));
        Ui.centered(gg, font, "Niv. " + d.level, cx, cy - 8, Ui.TEXT);
        Ui.centered(gg, font, Math.round(d.energy) + " E", cx, cy + 4, Ui.GOLD);
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            double a = -Math.PI / 2 + Math.PI * 2 * i / slots.size();
            int x = (int) (cx + Math.cos(a) * radius), y = (int) (cy + Math.sin(a) * radius);
            boolean hot = i == hov;
            int size = hot ? 40 : 32;
            Ui.panel(gg, x - size / 2 - 3, y - size / 2 - 3, size + 6, size + 6, hot ? 1f : 0.85f);
            if (hot) gg.fill(x - size / 2 - 3, y - size / 2 - 3, x + size / 2 + 3, y - size / 2 - 1, Ui.RED_BRIGHT);
            boolean ready = s.ability() == null || (d.cooldownRemaining[s.ability().ordinal()] <= 0
                    && d.energy >= s.ability().energyCost * ParasiteConfig.COMMON.energyCostMultiplier.get());
            gg.setColor(1f, 1f, 1f, ready ? 1f : 0.4f);
            Ui.icon(gg, s.ability() != null ? Ui.abilityIcon(s.ability()) : Ui.formIcon(s.form()), x - size / 2, y - size / 2, size);
            gg.setColor(1f, 1f, 1f, 1f);
        }
        if (hov >= 0) {
            Slot s = slots.get(hov);
            String name = s.ability() != null ? s.ability().displayName : "Forme : " + s.form().displayName;
            Ui.centered(gg, font, name, cx, cy + 108, Ui.TEXT);
            String sub = s.ability() != null ? s.ability().energyCost + " énergie" : "Transformation (10 énergie)";
            Ui.centered(gg, font, sub, cx, cy + 120, Ui.TEXT_DIM);
        } else if (slots.isEmpty()) {
            Ui.centered(gg, font, "Aucun pouvoir disponible", cx, cy + 100, Ui.TEXT_DIM);
        }
    }
}
