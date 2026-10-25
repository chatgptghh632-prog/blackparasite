package com.blackparasite.network.packet;

import java.util.function.Supplier;

import com.blackparasite.logic.ParasiteService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Client → serveur : le joueur demande la réinitialisation de SA propre progression
 * (bouton de configuration, après confirmation côté client). Ne peut jamais viser un autre joueur.
 */
public class ResetPacket {
    public ResetPacket() {}

    public void encode(FriendlyByteBuf buf) {}

    public static ResetPacket decode(FriendlyByteBuf buf) {
        return new ResetPacket();
    }

    public static void handle(ResetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) ParasiteService.resetPlayer(player);
        });
        context.setPacketHandled(true);
    }
}
