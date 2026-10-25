package com.blackparasite.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;

/** Touches configurables (menu Options > Contrôles > The Black Parasite). */
public final class Keys {
    public static final String CATEGORY = "key.categories.blackparasite";

    public static final KeyMapping OPEN_MENU = make("key.blackparasite.menu", GLFW.GLFW_KEY_B);
    public static final KeyMapping WHEEL = make("key.blackparasite.wheel", GLFW.GLFW_KEY_G);
    public static final KeyMapping USE_ABILITY = make("key.blackparasite.use", GLFW.GLFW_KEY_V);
    public static final KeyMapping ABSORB = make("key.blackparasite.absorb", GLFW.GLFW_KEY_X);
    public static final KeyMapping CYCLE = make("key.blackparasite.cycle", GLFW.GLFW_KEY_C);

    private Keys() {}

    private static KeyMapping make(String name, int key) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CATEGORY);
    }
}
