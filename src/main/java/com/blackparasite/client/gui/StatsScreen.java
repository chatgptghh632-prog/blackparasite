package com.blackparasite.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.blackparasite.client.ClientState;
import com.blackparasite.data.ParasiteData;
import com.blackparasite.logic.BiomassTable;
import com.blackparasite.logic.Lore;
import com.blackparasite.logic.ParasiteForm;
import com.blackparasite.logic.Progression;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;

/** Statistiques, collection biologique (espèces absorbées / inconnues, bonus) et journal. */
public class StatsScreen extends ParasiteScreen {
    private int tab = 0; // 0 stats, 1 collection, 2 journal
    private int scroll = 0;
    private int selectedLore = 0;

    public StatsScreen(Screen parent) {
        super(Component.literal("Statistiques"), parent);
    }

    @Override
    protected void init() {
        super.init();
        String[] names = {"Statistiques", "Collection", "Journal"};
        for (int i = 0; i < 3; i++) {
            int t = i;
            addRenderableWidget(new ParasiteButton(width / 2 - 170 + i * 114, 40, 110, 18, Component.literal(names[i]), b -> { tab = t; scroll = 0; }));
        }
        addRenderableWidget(new ParasiteButton(width / 2 - 50, height - 28, 100, 20, Component.literal("Retour"), b -> onClose()));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == 2) {
            int x = width / 2 - 230;
            Lore[] all = Lore.values();
            for (int i = 0; i < all.length; i++) {
                int y = 68 + i * 24;
                if (mx >= x && mx < x + 160 && my >= y && my < y + 22) { selectedLore = i; playClick(); return true; }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        drawBackdrop(gg, mx, my, pt);
        drawTitle(gg, "STATISTIQUES", 24, 14, 2f, pt);
        ParasiteData d = ClientState.DATA;
        int left = width / 2 - 230;
        Ui.panel(gg, left, 64, 460, height - 100, 0.92f);
        if (tab == 0) renderStats(gg, d, left + 14, 74, 432);
        else if (tab == 1) renderCollection(gg, d, left + 14, 74, 432, mx, my);
        else renderJournal(gg, d, left);
        super.render(gg, mx, my, pt);
    }

    private void renderStats(GuiGraphics gg, ParasiteData d, int x, int y, int w) {
        if (!d.infected) {
            gg.drawString(font, "Aucune infection : ces valeurs apparaîtront après la première infection.", x, y, Ui.TEXT_DIM, false);
            return;
        }
        ParasiteForm f = d.currentForm();
        String[][] lines = {
                {"Forme actuelle", f == null ? "?" : f.displayName},
                {"Niveau", d.level + " / 100"},
                {"Évolution", d.xpInto + " / " + d.xpNeeded},
                {"Biomasse disponible", String.valueOf(d.biomass)},
                {"Biomasse totale absorbée", String.valueOf(d.totalBiomass)},
                {"Faim", Math.round(d.hunger) + " / 100"},
                {"Énergie", Math.round(d.energy) + " / " + Math.round(d.maxEnergy())},
                {"Absorptions", String.valueOf(d.absorptions)},
                {"Espèces découvertes", d.species.size() + " / " + BiomassTable.knownTypes().size() + " référencées"},
                {"Créatures mutantes vaincues", String.valueOf(d.mutantsDefeated)},
                {"Capacités utilisées", String.valueOf(d.abilityUses)},
                {"Santé max. (bonus de niveau)", "+" + String.format("%.1f", Progression.levelHealthBonus(d.level))},
                {"Résistance (niveau)", String.format("%.0f %%", Progression.levelResistance(d.level) * 100)},
                {"Puissance des capacités", String.format("x%.2f", Progression.levelPower(d.level))},
                {"Durée des capacités", String.format("x%.2f", Progression.levelDuration(d.level))},
                {"Efficacité d'absorption", String.format("x%.2f", Progression.absorbEfficiency(d.level))},
        };
        int col = 0;
        for (int i = 0; i < lines.length; i++) {
            int cx = x + (i % 2) * (w / 2 + 6), cy = y + (i / 2) * 18;
            statLine(gg, cx, cy, w / 2 - 10, lines[i][0], lines[i][1]);
        }
        int by = y + (lines.length / 2 + 1) * 18 + 4;
        gg.drawString(font, "Santé", x, by, Ui.TEXT_DIM, false);
        Ui.bar(gg, x + 70, by + 1, 150, 6, ClientState.shownHealth / 20f, 0xFFC02030, Ui.RED_DARK, 1f);
        gg.drawString(font, "Faim", x, by + 14, Ui.TEXT_DIM, false);
        Ui.bar(gg, x + 70, by + 15, 150, 6, d.hunger / 100f, 0xFFB07A2A, 0xFF4A2A0A, 1f);
        gg.drawString(font, "Énergie", x, by + 28, Ui.TEXT_DIM, false);
        Ui.bar(gg, x + 70, by + 29, 150, 6, d.energy / Math.max(1f, d.maxEnergy()), 0xFFFFC04A, 0xFF8A4A10, 1f);
        gg.drawString(font, "Évolution", x, by + 42, Ui.TEXT_DIM, false);
        Ui.bar(gg, x + 70, by + 43, 150, 6, ClientState.shownXp, Ui.RED_BRIGHT, Ui.RED_DARK, 1f);
    }

    private void renderCollection(GuiGraphics gg, ParasiteData d, int x, int y, int w, int mx, int my) {
        List<EntityType<?>> types = BiomassTable.knownTypes();
        int known = 0;
        for (EntityType<?> t : types) if (d.species.contains(BiomassTable.idOf(t))) known++;
        gg.drawString(font, "Espèces absorbées : " + known + " / " + types.size() + "   (autres espèces : " + Math.max(0, d.species.size() - known) + ")   Absorptions : " + d.absorptions + "   Biomasse totale : " + d.totalBiomass, x, y, Ui.TEXT, false);
        int bonus = Math.min(25, d.species.size());
        gg.drawString(font, "Bonus actif : +" + bonus + " % de biomasse (1 % par espèce, max 25 %).  Absorbez de nouvelles espèces pour progresser !", x, y + 12, Ui.GOLD, false);
        int cols = 3, cw = w / cols, rows = (height - 140) / 14;
        int totalRows = (types.size() + cols - 1) / cols;
        scroll = Mth.clamp(scroll, 0, Math.max(0, totalRows - rows));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (r + scroll) * cols + c;
                if (idx >= types.size()) continue;
                EntityType<?> t = types.get(idx);
                boolean has = d.species.contains(BiomassTable.idOf(t));
                String name = has ? t.getDescription().getString() : "???";
                gg.drawString(font, (has ? "■ " : "□ ") + font.plainSubstrByWidth(name, cw - 18), x + c * cw, y + 30 + r * 14, has ? 0xFF6FD08C : 0xFF5E5A64, false);
            }
        }
        gg.drawString(font, "Molette : défiler", x, height - 52, Ui.TEXT_DIM, false);
    }

    private void renderJournal(GuiGraphics gg, ParasiteData d, int left) {
        Lore[] all = Lore.values();
        for (int i = 0; i < all.length; i++) {
            int y = 68 + i * 24;
            boolean unlocked = (d.loreMask & all[i].mask()) != 0;
            gg.fill(left + 6, y, left + 166, y + 22, i == selectedLore ? Ui.alpha(Ui.RED, 0.45f) : 0x60000000);
            gg.drawString(font, unlocked ? font.plainSubstrByWidth(all[i].title, 150) : "??? Document verrouillé", left + 12, y + 7, unlocked ? Ui.TEXT : 0xFF5E5A64, false);
        }
        Lore sel = all[Mth.clamp(selectedLore, 0, all.length - 1)];
        int tx = left + 180;
        if ((d.loreMask & sel.mask()) != 0) {
            gg.drawString(font, sel.title, tx, 72, Ui.RED_BRIGHT, false);
            List<FormattedCharSequence> lines = font.split(Component.literal(sel.text), 270);
            for (int i = 0; i < lines.size(); i++) gg.drawString(font, lines.get(i), tx, 92 + i * 11, Ui.TEXT, false);
        } else {
            gg.drawString(font, "Document non découvert.", tx, 72, Ui.TEXT_DIM, false);
            gg.drawString(font, "Progressez : absorbez, évoluez, débloquez des formes.", tx, 88, Ui.TEXT_DIM, false);
        }
    }
}
