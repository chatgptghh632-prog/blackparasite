package com.blackparasite.network.packet;

import java.util.function.Supplier;

import com.blackparasite.client.ClientPacketHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Serveur → client : alerte à afficher dans le HUD. */
public class NotifyPacket {
    public static final int INFO = 0;
    public static final int GAIN = 1;
    public static final int EVOLUTION = 2;
    public static final int WARNING = 3;
    public static final int MISSION = 4;

    public final int kind;
    public final String title;
    public final String subtitle;

    public NotifyPacket(int kind, String title, String subtitle) {
        this.kind = kind;
        this.title = title;
        this.subtitle = subtitle;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(kind);
        buf.writeUtf(title, 128);
        buf.writeUtf(subtitle, 256);
    }

    public static NotifyPacket decode(FriendlyByteBuf buf) {
        return new NotifyPacket(buf.readVarInt(), buf.readUtf(128), buf.readUtf(256));
    }

    public static void handle(NotifyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleNotify(msg)));
        ctx.get().setPacketHandled(true);
    }
}
