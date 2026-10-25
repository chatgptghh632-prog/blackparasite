package com.blackparasite.client.gui;

import com.blackparasite.ModSounds;
import com.blackparasite.client.ClientPacketHandler;
import com.blackparasite.config.ParasiteConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/** Bouton moderne : fond sombre, barre rouge qui s'étend au survol, son d'interface. */
public class ParasiteButton extends Button {
    private float glow = 0f;
    private boolean wasHovered = false;

    public ParasiteButton(int x, int y, int w, int h, Component msg, OnPress onPress) {
        super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics gg, int mx, int my, float pt) {
        boolean hover = isHoveredOrFocused() && active;
        if (hover && !wasHovered && ParasiteConfig.CLIENT.uiSounds.get()) ClientPacketHandler.play(ModSounds.UI_HOVER.get(), 0.5f, 1.0f);
        wasHovered = hover;
        glow += ((hover ? 1f : 0f) - glow) * 0.25f;
        int x = getX(), y = getY();
        gg.fill(x, y, x + width, y + height, active ? 0xD0100C12 : 0x90100C12);
        gg.fill(x, y + height - 1, x + width, y + height, Ui.STEEL_DARK);
        gg.fill(x, y, x + width, y + 1, 0x40FFFFFF);
        int bar = Math.round(width * glow);
        gg.fillGradient(x, y, x + bar, y + height, Ui.alpha(Ui.RED, 0.55f), Ui.alpha(Ui.RED_DARK, 0.55f));
        gg.fill(x, y, x + 3, y + height, active ? Ui.RED_BRIGHT : Ui.STEEL_DARK);
        int color = !active ? 0xFF5E5A64 : (hover ? 0xFFFFFFFF : Ui.TEXT);
        gg.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + width / 2 + 2, y + (height - 8) / 2, color);
    }

    @Override
    public void playDownSound(SoundManager manager) {
        if (ParasiteConfig.CLIENT.uiSounds.get()) ClientPacketHandler.play(ModSounds.UI_CLICK.get(), 0.8f, 1.0f);
    }
}
