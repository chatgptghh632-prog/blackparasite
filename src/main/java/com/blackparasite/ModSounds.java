package com.blackparasite;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons du mod. Les fichiers .ogg sont dans assets/blackparasite/sounds/ (générés par tools/generate_assets.py)
 * et déclarés dans assets/blackparasite/sounds.json. Chaque nom ci-dessous correspond à une entrée de ce fichier.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BlackParasiteMod.MODID);

    public static final RegistryObject<SoundEvent> UI_HOVER = reg("ui_hover");
    public static final RegistryObject<SoundEvent> UI_CLICK = reg("ui_click");
    public static final RegistryObject<SoundEvent> ROAR = reg("roar");
    public static final RegistryObject<SoundEvent> TENTACLE = reg("tentacle");
    public static final RegistryObject<SoundEvent> ABSORB = reg("absorb");
    public static final RegistryObject<SoundEvent> EVOLVE = reg("evolve");
    public static final RegistryObject<SoundEvent> WHISPER = reg("whisper");
    public static final RegistryObject<SoundEvent> HEARTBEAT = reg("heartbeat");
    public static final RegistryObject<SoundEvent> AMBIENT_MENU = reg("ambient_menu");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(BlackParasiteMod.MODID, name)));
    }
}
