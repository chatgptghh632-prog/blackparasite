package com.blackparasite.client.gui;

import com.blackparasite.client.ClientState;
import com.blackparasite.data.ParasiteData;
import com.blackparasite.logic.Mission;
import com.blackparasite.logic.ParasiteForm;
import com.blackparasite.network.ModNetwork;
import com.blackparasite.network.packet.TransformPacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Formes (avec conditions de déblocage et bouton de transformation) et missions avec suivi. */
public class EvolutionScreen extends ParasiteScreen {

    public EvolutionScreen(Screen parent) {
        super(Component.literal("Évolutions"), parent);
    }

    @Override
    protected void init() {
        super.init();
        int left = width / 2 - 250;
        ParasiteForm[] forms = ParasiteForm.values();
        for (int i = 0; i < forms.length; i++) {
            ParasiteForm f = forms[i];
            addRenderableWidget(new ParasiteButton(left + 140, 70 + i * 52 + 24, 92, 18, Component.literal("Se transformer"),
                    b -> {
                        ModNetwork.CHANNEL.sendToServer(new TransformPacket(f.ordinal()));
                        onClose();
                    }));
        }
        addRenderableWidget(new ParasiteButton(width / 2 - 50, height - 28, 100, 20, Component.literal("Retour"), b -> onClose()));
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        drawBackdrop(gg, mx, my, pt);
        drawTitle(gg, "ÉVOLUTIONS", 24, 18, 2f, pt);
        ParasiteData d = ClientState.DATA;
        int left = width / 2 - 250;
        // barre d'évolution
        gg.drawString(font, d.infected ? "Niveau " + d.level + " / 100   —   Biomasse " + d.biomass + "   —   Total absorbé " + d.totalBiomass : "Aucune infection", 26, 42, Ui.TEXT_DIM, false);
        Ui.bar(gg, 26, 54, width - 52, 4, ClientState.shownXp, Ui.RED_BRIGHT, Ui.RED_DARK, 1f);

        ParasiteForm[] forms = ParasiteForm.values();
        for (int i = 0; i < forms.length; i++) {
            ParasiteForm f = forms[i];
            int y = 70 + i * 52;
            boolean unlocked = d.infected && d.hasForm(f);
            boolean current = d.infected && d.form == f.ordinal();
            Ui.panel(gg, left, y, 240, 48, 0.95f);
            if (current) gg.fill(left, y, left + 3, y + 48, Ui.GOLD);
            gg.setColor(1f, 1f, 1f, unlocked ? 1f : 0.35f);
            Ui.icon(gg, Ui.formIcon(f), left + 6, y + 6, 36);
            gg.setColor(1f, 1f, 1f, 1f);
            gg.drawString(font, f.displayName, left + 48, y + 5, unlocked ? Ui.TEXT : Ui.TEXT_DIM, false);
            String req = f == ParasiteForm.ABOMINATION ? "Transformation ultime (Colosse, niv. 80)" : "Niveau " + f.unlockLevel;
            gg.drawString(font, current ? "Forme actuelle" : unlocked ? "Débloquée" : req, left + 48, y + 16, current ? Ui.GOLD : unlocked ? 0xFF6FD08C : Ui.RED_BRIGHT, false);
            gg.drawString(font, "PV +" + (int) f.healthBonus + "  Atq x" + f.damageMultiplier + "  Rés. " + (int) (f.resistance * 100) + "%", left + 48, y + 28, Ui.TEXT_DIM, false);
            gg.drawString(font, "Vitesse " + (f.speedBonus >= 0 ? "+" : "") + (int) (f.speedBonus * 100) + "%  Énergie " + f.energyRegen + "/s", left + 48, y + 38, Ui.TEXT_DIM, false);
        }
        // boutons : seulement utilisables sur les formes débloquées et différentes de l'actuelle
        int bi = 0;
        for (var w : children()) {
            if (w instanceof ParasiteButton pb && pb.getMessage().getString().equals("Se transformer")) {
                ParasiteForm f = forms[bi++];
                pb.active = d.infected && d.hasForm(f) && d.form != f.ordinal();
                pb.visible = false; // placé dans la carte : on le repositionne ci-dessous
                pb.visible = true;
            }
        }

        // missions
        int mx0 = left + 256;
        gg.drawString(font, "MISSIONS (facultatives)", mx0, 70 - 10, Ui.RED_BRIGHT, false);
        Mission[] ms = Mission.values();
        for (int i = 0; i < ms.length; i++) {
            Mission m = ms[i];
            int y = 70 + i * 34;
            boolean done = (d.missionMask & m.mask()) != 0;
            Ui.panel(gg, mx0, y, 244, 30, 0.95f);
            gg.drawString(font, (done ? "✔ " : "") + (i + 1) + ". " + m.title, mx0 + 6, y + 4, done ? 0xFF6FD08C : Ui.TEXT, false);
            String desc = font.plainSubstrByWidth(m.description, 232);
            gg.drawString(font, desc, mx0 + 6, y + 14, Ui.TEXT_DIM, false);
            int prog = d.infected ? m.progress(d) : 0;
            Ui.bar(gg, mx0 + 6, y + 25, 150, 3, prog / (float) m.target, done ? 0xFF6FD08C : Ui.RED_BRIGHT, Ui.RED_DARK, 1f);
            String r = prog + "/" + m.target + "  +" + m.reward;
            gg.drawString(font, r, mx0 + 238 - font.width(r), y + 20, Ui.GOLD, false);
        }
        super.render(gg, mx, my, pt);
    }
}
