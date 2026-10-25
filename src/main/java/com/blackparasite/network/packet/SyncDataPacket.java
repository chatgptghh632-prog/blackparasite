package com.blackparasite.network.packet;

import java.util.function.Supplier;

import com.blackparasite.client.ClientPacketHandler;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Serveur → client : état complet du parasite du joueur local. */
public class SyncDataPacket {
    public final CompoundTag tag;

    public SyncDataPacket(CompoundTag tag) {
        this.tag = tag;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(tag);
    }

    public static SyncDataPacket decode(FriendlyByteBuf buf) {
        CompoundTag t = buf.readNbt();
        return new SyncDataPacket(t == null ? new CompoundTag() : t);
    }

    public static void handle(SyncDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSync(msg.tag)));
        ctx.get().setPacketHandled(true);
    }
}
