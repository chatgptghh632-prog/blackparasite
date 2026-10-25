package com.blackparasite.client.gui;

import java.util.function.Consumer;

import com.blackparasite.client.ClientState;
import com.blackparasite.config.ParasiteConfig;
import com.blackparasite.network.ModNetwork;
import com.blackparasite.network.packet.ResetPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/** Réglages client (HUD, animations, sons) + réinitialisation de la progression (avec confirmation). */
public class ConfigScreen extends ParasiteScreen {

    /** Curseur numérique qui écrit directement dans la configuration. */
    private static final class Slider extends AbstractSliderButton {
        private final String label;
        private final double min, max;
        private final Consumer<Double> setter;
        private final boolean integer;

        Slider(int x, int y, int w, String label, double min, double max, double current, boolean integer, Consumer<Double> setter) {
            super(x, y, w, 18, Component.empty(), (current - min) / (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.setter = setter;
            updateMessage();
        }

        private double real() {
            double v = min + (max - min) * value;
            return integer ? Math.round(v) : Math.round(v * 100.0) / 100.0;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + " : " + (integer ? String.valueOf((int) real()) : String.valueOf(real()))));
        }

        @Override
        protected void applyValue() {
            setter.accept(real());
        }
    }

    public ConfigScreen(Screen parent) {
        super(Component.literal("Configuration"), parent);
    }

    private void toggle(int x, int y, int w, String label, ForgeConfigSpec.BooleanValue v) {
        addRenderableWidget(CycleButton.onOffBuilder(v.get()).create(x, y, w, 18, Component.literal(label), (b, val) -> v.set(val)));
    }

    @Override
    protected void init() {
        super.init();
        var c = ParasiteConfig.CLIENT;
        int col1 = width / 2 - 230, col2 = width / 2 + 6, w = 224, y = 62;
        toggle(col1, y, w, "HUD activé", c.hudEnabled);
        addRenderableWidget(new Slider(col1, y + 22, w, "Taille", 0.5, 2.5, c.hudScale.get(), false, v -> c.hudScale.set(v)));
        addRenderableWidget(new Slider(col1, y + 44, w, "Opacité", 0.1, 1.0, c.hudOpacity.get(), false, v -> c.hudOpacity.set(v)));
        addRenderableWidget(new Slider(col1, y + 66, w, "Position X (px)", 0, 600, c.hudOffsetX.get(), true, v -> c.hudOffsetX.set((int) Math.round(v))));
        addRenderableWidget(new Slider(col1, y + 88, w, "Position Y (px)", 0, 600, c.hudOffsetY.get(), true, v -> c.hudOffsetY.set((int) Math.round(v))));
        toggle(col2, y, w, "Barre de santé", c.showHealth);
        toggle(col2, y + 22, w, "Barre d'évolution", c.showEvolution);
        toggle(col2, y + 44, w, "Jauge de faim", c.showHunger);
        toggle(col2, y + 66, w, "Jauge d'énergie", c.showEnergy);
        toggle(col2, y + 88, w, "Biomasse", c.showBiomass);
        toggle(col1, y + 120, w, "Barre de capacités", c.showAbility);
        toggle(col2, y + 120, w, "Alertes à l'écran", c.showAlerts);
        toggle(col1, y + 142, w, "Animations avancées", c.advancedAnimations);
        toggle(col2, y + 142, w, "Sons d'interface", c.uiSounds);
        toggle(col1, y + 164, w, "Musique du menu", c.menuMusic);
        addRenderableWidget(new ParasiteButton(col2, y + 164, w, 18, Component.literal("Réinitialiser la progression…"), b ->
                minecraft.setScreen(new ConfirmScreen(yes -> {
                    if (yes && minecraft.level != null) ModNetwork.CHANNEL.sendToServer(new ResetPacket());
                    minecraft.setScreen(this);
                }, Component.literal("Réinitialiser le parasite ?"),
                        Component.literal("Niveau, biomasse, formes, missions et collection seront effacés. Cette action est définitive.")))));
        addRenderableWidget(new ParasiteButton(width / 2 - 50, height - 28, 100, 20, Component.literal("Retour"), b -> onClose()));
    }

    @Override
    public void removed() {
        ParasiteConfig.CLIENT_SPEC.save();
        super.removed();
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        drawBackdrop(gg, mx, my, pt);
        drawTitle(gg, "CONFIGURATION", 24, 18, 2f, pt);
        gg.drawString(font, "HUD en jeu", width / 2 - 230, 50, Ui.RED_BRIGHT, false);
        var s = ParasiteConfig.COMMON;
        int y = height - 74;
        gg.drawString(font, "Règles du serveur (lecture seule — fichier config/blackparasite-common.toml) :", width / 2 - 230, y, Ui.TEXT_DIM, false);
        gg.drawString(font, "Difficulté x" + s.progressionDifficulty.get() + "  Biomasse x" + s.biomassMultiplier.get() + "  Faim " + s.hungerDrainPerSecond.get()
                + "/s  Coût énergie x" + s.energyCostMultiplier.get() + "  Événements x" + s.eventFrequency.get(), width / 2 - 230, y + 12, Ui.TEXT_DIM, false);
        super.render(gg, mx, my, pt);
    }
}
