package com.blackparasite.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.blackparasite.client.ClientEvents;
import com.blackparasite.client.ClientState;
import com.blackparasite.config.ParasiteConfig;
import com.blackparasite.data.ParasiteData;
import com.blackparasite.logic.ParasiteAbility;
import com.blackparasite.logic.ParasiteForm;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Liste des capacités : état, coût, recharge ; choisir la capacité rapide (touche V) ou l'utiliser. */
public class PowersScreen extends ParasiteScreen {
    private static final int CW = 236, CH = 44;
    private final List<int[]> cells = new ArrayList<>(); // x, y, ordinal

    public PowersScreen(Screen parent) {
        super(Component.literal("Pouvoirs"), parent);
    }

    @Override
    protected void init() {
        super.init();
        cells.clear();
        ParasiteAbility[] all = ParasiteAbility.values();
        int left = width / 2 - CW - 6;
        int top = 62;
        for (int i = 0; i < all.length; i++) {
            int col = i % 2, row = i / 2;
            int x = left + col * (CW + 12), y = top + row * (CH + 6);
            cells.add(new int[]{x, y, i});
            ParasiteAbility a = all[i];
            addRenderableWidget(new ParasiteButton(x + CW - 104, y + CH - 20, 50, 16, Component.literal("Choisir"), b -> {
                if (a != ParasiteAbility.ABSORB) ClientState.selectedAbility = a.ordinal();
            })).active = a != ParasiteAbility.ABSORB;
            addRenderableWidget(new ParasiteButton(x + CW - 52, y + CH - 20, 48, 16, Component.literal("Utiliser"), b -> {
                ClientEvents.sendAbility(a.ordinal());
                onClose();
            }));
        }
        addRenderableWidget(new ParasiteButton(width / 2 - 50, height - 28, 100, 20, Component.literal("Retour"), b -> onClose()));
    }

    private static String status(ParasiteData d, ParasiteAbility a) {
        if (!d.infected) return "Non infecté";
        if (!ParasiteConfig.COMMON.abilityEnabled.get(a).get()) return "Désactivée (serveur)";
        if (d.level < a.minLevel) return "Niveau " + a.minLevel + " requis";
        if (!a.allowedIn(d.currentForm())) return "Autre forme requise";
        int cd = d.cooldownRemaining[a.ordinal()];
        if (cd > 0) return "Recharge " + Ui.ticksToSeconds(cd);
        if (d.energy < a.energyCost * ParasiteConfig.COMMON.energyCostMultiplier.get()) return "Énergie insuffisante";
        return "Prêt";
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        drawBackdrop(gg, mx, my, pt);
        drawTitle(gg, "POUVOIRS", 24, 18, 2f, pt);
        ParasiteData d = ClientState.DATA;
        gg.drawString(font, "Énergie " + Math.round(d.energy) + "/" + Math.round(d.maxEnergy()) + "   Touche V : capacité choisie   Touche X : absorber   Touche G : roue",
                26, 42, Ui.TEXT_DIM, false);
        ParasiteAbility[] all = ParasiteAbility.values();
        List<Component> tooltip = null;
        for (int[] c : cells) {
            ParasiteAbility a = all[c[2]];
            String st = status(d, a);
            boolean ready = st.equals("Prêt");
            boolean selected = a.ordinal() == ClientState.selectedAbility;
            Ui.panel(gg, c[0], c[1], CW, CH, 0.95f);
            if (selected) gg.fill(c[0], c[1], c[0] + 3, c[1] + CH, Ui.GOLD);
            gg.setColor(1f, 1f, 1f, ready ? 1f : 0.45f);
            Ui.icon(gg, Ui.abilityIcon(a), c[0] + 6, c[1] + 6, 32);
            gg.setColor(1f, 1f, 1f, 1f);
            gg.drawString(font, a.displayName, c[0] + 44, c[1] + 6, ready ? Ui.TEXT : Ui.TEXT_DIM, false);
            gg.drawString(font, st, c[0] + 44, c[1] + 18, ready ? Ui.GOLD : Ui.RED_BRIGHT, false);
            gg.drawString(font, a.energyCost + " énergie · " + (a.cooldownTicks / 20) + " s", c[0] + 44, c[1] + 30, Ui.TEXT_DIM, false);
            int cd = d.cooldownRemaining[a.ordinal()];
            if (cd > 0) gg.fill(c[0] + 1, c[1] + CH - 3, c[0] + 1 + (int) ((CW - 2) * (cd / (float) a.cooldownTicks)), c[1] + CH - 1, Ui.RED);
            if (mx >= c[0] && mx < c[0] + CW - 108 && my >= c[1] && my < c[1] + CH) {
                tooltip = List.of(Component.literal(a.displayName), Component.literal(a.description),
                        Component.literal("Niveau min. " + a.minLevel + " — " + formsOf(a)));
            }
        }
        super.render(gg, mx, my, pt);
        if (tooltip != null) gg.renderComponentTooltip(font, tooltip, mx, my);
    }

    private static String formsOf(ParasiteAbility a) {
        StringBuilder sb = new StringBuilder();
        for (ParasiteForm f : ParasiteForm.values()) if (a.allowedIn(f)) sb.append(sb.length() > 0 ? ", " : "").append(f.displayName);
        return sb.toString();
    }
}
