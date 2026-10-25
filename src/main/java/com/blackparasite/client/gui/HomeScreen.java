package com.blackparasite.client.gui;

import com.blackparasite.ModSounds;
import com.blackparasite.client.ClientState;
import com.blackparasite.client.model.ParasiteModel;
import com.blackparasite.client.model.ParasiteRenderer;
import com.blackparasite.config.ParasiteConfig;
import com.blackparasite.data.ParasiteData;
import com.blackparasite.logic.ParasiteAbility;
import com.blackparasite.logic.ParasiteForm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Écran d'accueil : le parasite (modèle animé, yeux qui suivent le curseur), accès aux autres écrans. */
public class HomeScreen extends ParasiteScreen {
    private static final ParasiteForm[] PREVIEW = {ParasiteForm.PREDATEUR, ParasiteForm.FANTOME, ParasiteForm.COLOSSE, ParasiteForm.ABOMINATION};
    private int previewIndex = 0;
    private SimpleSoundInstance music;

    public HomeScreen(Screen parent) {
        super(Component.literal("The Black Parasite"), parent);
        ParasiteForm cur = ClientState.DATA.currentForm();
        for (int i = 0; i < PREVIEW.length; i++) if (PREVIEW[i] == cur) previewIndex = i;
    }

    @Override
    protected void init() {
        super.init();
        int bx = width - 190, by = Math.max(112, height / 2 - 40), bw = 160;
        addRenderableWidget(new ParasiteButton(bx, by, bw, 22, Component.literal("Pouvoirs"), b -> minecraft.setScreen(new PowersScreen(this))));
        addRenderableWidget(new ParasiteButton(bx, by + 28, bw, 22, Component.literal("Évolutions"), b -> minecraft.setScreen(new EvolutionScreen(this))));
        addRenderableWidget(new ParasiteButton(bx, by + 56, bw, 22, Component.literal("Statistiques"), b -> minecraft.setScreen(new StatsScreen(this))));
        addRenderableWidget(new ParasiteButton(bx, by + 84, bw, 22, Component.literal("Configuration"), b -> minecraft.setScreen(new ConfigScreen(this))));
        addRenderableWidget(new ParasiteButton(bx, by + 118, bw, 22, Component.literal("Fermer"), b -> onClose()));
        int mx = (int) (width * 0.3f);
        addRenderableWidget(new ParasiteButton(mx - 70, height - 34, 24, 20, Component.literal("<"), b -> previewIndex = (previewIndex + PREVIEW.length - 1) % PREVIEW.length));
        addRenderableWidget(new ParasiteButton(mx + 46, height - 34, 24, 20, Component.literal(">"), b -> previewIndex = (previewIndex + 1) % PREVIEW.length));
        if (music == null && ParasiteConfig.CLIENT.menuMusic.get()) {
            music = SimpleSoundInstance.forUI(ModSounds.AMBIENT_MENU.get(), 1.0f, 0.5f);
            minecraft.getSoundManager().play(music);
        }
    }

    @Override
    public void removed() {
        if (music != null) {
            Minecraft.getInstance().getSoundManager().stop(music);
            music = null;
        }
        super.removed();
    }

    @Override
    public void render(GuiGraphics gg, int mx, int my, float pt) {
        drawBackdrop(gg, mx, my, pt);
        float ap = appear(pt);
        ParasiteData d = ClientState.DATA;

        // titre
        drawTitle(gg, "THE BLACK PARASITE", 24, 18, 2.4f, pt);
        gg.drawString(font, "Laboratoire de confinement — Sujet observé", 26, 44, Ui.TEXT_DIM, false);

        // le parasite, qui regarde le curseur
        ParasiteForm shown = PREVIEW[previewIndex];
        int cx = (int) (width * 0.3f);
        float scale = height * 0.30f;
        float yaw = Mth.clamp(-(mx - cx) * 0.22f, -50f, 50f);
        float pitch = Mth.clamp((my - height * 0.35f) * 0.12f, -25f, 25f);
        int anim = -1;
        float animT = 0f;
        int cycle = ticks % 260;
        if (cycle < 36) { anim = ParasiteAbility.ROAR.ordinal(); animT = (cycle + pt) / 36f; }
        else if (cycle > 130 && cycle < 150) { anim = ParasiteAbility.ABSORB.ordinal(); animT = (cycle - 130 + pt) / 20f; }
        int stage = d.infected ? Math.min(10, d.level / 10) : 6;
        ParasiteRenderer.renderInGui(gg, shown, stage, cx, height - 52 + (1f - ap) * 50f, scale, yaw, pitch, ticks + pt, anim, animT);
        Ui.centered(gg, font, shown.displayName, cx, height - 28 - 0, Ui.TEXT);
        gg.drawString(font, d.infected && d.hasForm(shown) ? "Forme débloquée" : (d.infected ? "Forme non débloquée (aperçu)" : "Aperçu"), cx - 60, height - 18,
                d.infected && d.hasForm(shown) ? Ui.GOLD : Ui.TEXT_DIM, false);

        drawWatchingEye(gg, width - 110, 62, mx, my, pt);

        // état du sujet
        int px = width - 190, py = Math.max(112, height / 2 - 40) + 150;
        Ui.panel(gg, px, py, 160, 62, 0.9f);
        if (d.infected) {
            ParasiteForm f = d.currentForm();
            gg.drawString(font, (f == null ? "?" : f.displayName) + "  Niv. " + d.level, px + 8, py + 8, Ui.TEXT, false);
            gg.drawString(font, "Biomasse : " + d.biomass, px + 8, py + 22, Ui.TEXT_DIM, false);
            Ui.bar(gg, px + 8, py + 38, 144, 6, d.xpNeeded <= 0 ? 1f : d.xpInto / (float) d.xpNeeded, Ui.RED_BRIGHT, Ui.RED_DARK, 1f);
            gg.drawString(font, "Évolution " + d.xpInto + "/" + d.xpNeeded, px + 8, py + 48, Ui.TEXT_DIM, false);
        } else {
            gg.drawString(font, "Aucune infection détectée", px + 8, py + 8, Ui.RED_BRIGHT, false);
            gg.drawString(font, "Fabriquez un échantillon de", px + 8, py + 24, Ui.TEXT_DIM, false);
            gg.drawString(font, "parasite et utilisez-le.", px + 8, py + 36, Ui.TEXT_DIM, false);
        }
        super.render(gg, mx, my, pt);
    }
}
