package com.blackparasite;

import org.slf4j.Logger;

import com.blackparasite.config.ParasiteConfig;
import com.blackparasite.data.ParasiteData;
import com.blackparasite.network.ModNetwork;
import com.mojang.logging.LogUtils;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BlackParasiteMod.MODID)
public class BlackParasiteMod {
    public static final String MODID = "blackparasite";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlackParasiteMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::buildCreativeTabs);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ParasiteConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ParasiteConfig.CLIENT_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
        LOGGER.info("The Black Parasite : réseau enregistré.");
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(ParasiteData.class);
    }

    private void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.PARASITE_SAMPLE);
        }
    }
}
