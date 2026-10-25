package com.blackparasite.network;

import com.blackparasite.BlackParasiteMod;
import com.blackparasite.network.packet.AbilityPacket;
import com.blackparasite.network.packet.AnimPacket;
import com.blackparasite.network.packet.NotifyPacket;
import com.blackparasite.network.packet.ResetPacket;
import com.blackparasite.network.packet.SyncDataPacket;
import com.blackparasite.network.packet.SyncVisualPacket;
import com.blackparasite.network.packet.TransformPacket;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BlackParasiteMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        // Serveur → client
        CHANNEL.registerMessage(id++, SyncDataPacket.class, SyncDataPacket::encode, SyncDataPacket::decode, SyncDataPacket::handle);
        CHANNEL.registerMessage(id++, SyncVisualPacket.class, SyncVisualPacket::encode, SyncVisualPacket::decode, SyncVisualPacket::handle);
        CHANNEL.registerMessage(id++, NotifyPacket.class, NotifyPacket::encode, NotifyPacket::decode, NotifyPacket::handle);
        CHANNEL.registerMessage(id++, AnimPacket.class, AnimPacket::encode, AnimPacket::decode, AnimPacket::handle);
        // Client → serveur (toujours revalidés côté serveur)
        CHANNEL.registerMessage(id++, AbilityPacket.class, AbilityPacket::encode, AbilityPacket::decode, AbilityPacket::handle);
        CHANNEL.registerMessage(id++, TransformPacket.class, TransformPacket::encode, TransformPacket::decode, TransformPacket::handle);
        CHANNEL.registerMessage(id++, ResetPacket.class, ResetPacket::encode, ResetPacket::decode, ResetPacket::handle);
    }
}
