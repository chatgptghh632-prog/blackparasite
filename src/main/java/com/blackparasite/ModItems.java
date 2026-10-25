package com.blackparasite;

import com.blackparasite.item.ParasiteSampleItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BlackParasiteMod.MODID);

    public static final RegistryObject<Item> PARASITE_SAMPLE = ITEMS.register("parasite_sample",
            () -> new ParasiteSampleItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    private ModItems() {}
}
