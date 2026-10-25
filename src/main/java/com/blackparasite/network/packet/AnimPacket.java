package com.blackparasite.network.packet;

import java.util.UUID;
import java.util.function.Supplier;

import com.blackparasite.client.ClientPacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Serveur → clients : déclenche une animation ponctuelle sur le modèle d'un joueur.
 * Valeurs de {@code anim} : ordinal d'une {@link com.blackparasite.logic.ParasiteAbility}, ou
 * {@link #TRANSFORM} / {@link #EVOLVE}.
 */
public class AnimPacket {
    public static final int TRANSFORM = 100;
    public static final int EVOLVE = 101;

    public final UUID player;
    public final int anim;

    public AnimPacket(UUID player, int anim) {
        this.player = player;
        this.anim = anim;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(player);
        buf.writeVarInt(anim);
    }

    public static AnimPacket decode(FriendlyByteBuf buf) {
        return new AnimPacket(buf.readUUID(), buf.readVarInt());
    }

    public static void handle(AnimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleAnim(msg)));
        ctx.get().setPacketHandled(true);
    }
}
