package com.blackparasite.network.packet;

import java.util.function.Supplier;

import com.blackparasite.logic.AbilityHandler;
import com.blackparasite.logic.ParasiteAbility;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Client → serveur : demande d'utilisation d'une capacité. Tout est revalidé côté serveur. */
public class AbilityPacket {
    public final int ability;

    public AbilityPacket(int ability) {
        this.ability = ability;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(ability);
    }

    public static AbilityPacket decode(FriendlyByteBuf buf) {
        return new AbilityPacket(buf.readVarInt());
    }

    public static void handle(AbilityPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            ParasiteAbility ability = ParasiteAbility.byOrdinal(msg.ability);
            if (player != null && ability != null) AbilityHandler.use(player, ability);
        });
        context.setPacketHandled(true);
    }
}
