package com.blackparasite.data;

import com.blackparasite.BlackParasiteMod;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

/** Déclaration de la capability Forge qui porte {@link ParasiteData} sur les joueurs. */
public final class ParasiteCapability {

    public static final Capability<ParasiteData> PARASITE = CapabilityManager.get(new CapabilityToken<>() {});
    public static final ResourceLocation KEY = new ResourceLocation(BlackParasiteMod.MODID, "parasite");

    private ParasiteCapability() {}

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final ParasiteData data = new ParasiteData();
        private final LazyOptional<ParasiteData> optional = LazyOptional.of(() -> data);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
            return cap == PARASITE ? optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.save();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.load(nbt);
            data.recalcLevel(); // chargement depuis le disque : serveur uniquement
        }

        public void invalidate() {
            optional.invalidate();
        }
    }
}
